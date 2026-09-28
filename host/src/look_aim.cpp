// Gyro and mouse aiming in the look mode (hold R, look around with the stick), and a choice of
// smooth or direct response for each of the stick, the mouse and gyro.
//
// func_15120158 runs the look mode once a frame. The stick moves a target yaw and pitch, in
// degrees ($t0 + 0x34 and + 0x38; yaw grows to the left and isn't wrapped), then the pitch target
// is clamped to limits that depend on what Conker is doing (0x15120B44 to 0x15120D28), and then a
// spring (func_15049688) eases the current angles ($s0 + 0x37C yaw, $t0 + 0x3C pitch) toward the
// targets, keeping their velocities in $t0 + 0x18 and + 0x1C. Everything the camera is built from
// is copied from the current angles after that.
//
// Hooks (conker.toml):
//   conker_look_stick_yaw_a/_b and conker_look_stick_pitch, where the stick's turn is stored into
//     the targets (0x15120A14, 0x15120A44, 0x15120A98): each can turn its axis around. The game's
//     stick has X normal and Y inverted (up looks down).
//   conker_look_targets, before the clamp: the mouse and gyro turn the targets, so the game's own
//     limits apply to them just as they do to the stick.
//   conker_look_currents, after the springs (L_15120E8C, where both of their branches meet): for
//     an input set to Direct, the current angles take its movement in the same frame instead of
//     being eased toward it.

#include <cstdint>
#include <cstring>
#include <deque>
#include <mutex>

#include "recomp.h"

#include "conker.hpp"

#if defined(CONKER_RT64)
#include "recompinput/input_state.h"
#include "recompui/config.h"
#endif

#if defined(__ANDROID__) && defined(CONKER_RT64)
bool conker_android_take_gyro(float* x, float* y); // android/touch_controls.cpp
#endif

namespace {
    // Field offsets (see above).
    constexpr int32_t target_yaw = 0x34, target_pitch = 0x38;      // $t0
    constexpr int32_t yaw_velocity = 0x18, pitch_velocity = 0x1C;  // $t0
    constexpr int32_t current_yaw = 0x37C;                         // $s0
    constexpr int32_t current_pitch = 0x3C;                        // $t0

    // Degrees per pixel of mouse movement at 100% mouse sensitivity.
    constexpr float mouse_degrees_per_pixel = 0.1f;
    // recompinput's gyro delta isn't an angle: it adds up the controller's angular velocity
    // (degrees per second) once per sensor event, without the time between events, so a frame's
    // worth depends on the controller's report rate. Tuned by feel, not derived.
    constexpr float gyro_scale = 1.0f / 60.0f;

    float read_float(uint8_t* rdram, gpr base, int32_t offset) {
        uint32_t word = (uint32_t)MEM_W(offset, base);
        float value;
        std::memcpy(&value, &word, sizeof(value));
        return value;
    }

    void write_float(uint8_t* rdram, gpr base, int32_t offset, float value) {
        uint32_t word;
        std::memcpy(&word, &value, sizeof(word));
        MEM_W(offset, base) = (int32_t)word;
    }

    // Each input poll's mouse and gyro movement, queued so the look mode takes one poll's worth a
    // frame. Measured in the look mode: 30 polls and 30 look updates a second, but on different
    // threads (the game's controller thread polls), so two polls sometimes land between two
    // updates and none before the next. recompinput keeps only the latest poll's movement, and
    // taking that once per update left some frames still and lost others: the view hitched.
    // Queued, nothing is lost, and a second poll waits a frame instead of doubling one. Outside
    // the look mode nothing takes from the queue, so the oldest is dropped past two, rather than
    // saved up and turned into a jump when R is pressed.
    struct Movement {
        float mouse_x, mouse_y, gyro_x, gyro_y;
    };
    std::mutex queue_mutex;
    std::deque<Movement> queue;

    // What conker_look_targets did this frame, for conker_look_currents.
    struct Frame {
        bool targets_moved = false;
        float pitch_target_set = 0.0f;  // the pitch target as conker_look_targets left it
        float direct_yaw = 0.0f;        // the part of the turn from inputs set to Direct
        float direct_pitch = 0.0f;
    } frame;

#if defined(CONKER_RT64)
    namespace options {
        const std::string stick_response = "look_stick_response";
        const std::string stick_invert = "look_stick_invert";
        const std::string mouse_response = "look_mouse_response";
        const std::string gyro_response = "look_gyro_response";
        const std::string mouse_invert = "look_mouse_invert";
        const std::string gyro_invert = "look_gyro_invert";
    }

    enum class Response : uint32_t { Smooth, Direct };
    enum class Invert : uint32_t { None, X, Y, Both };

    template <typename T>
    T option(const std::string& id) {
        return static_cast<T>(std::get<uint32_t>(recompui::config::get_general_config().get_option_value(id)));
    }

    void apply_invert(Invert invert, float& x, float& y) {
        if (invert == Invert::X || invert == Invert::Both) x = -x;
        if (invert == Invert::Y || invert == Invert::Both) y = -y;
    }
#endif
}

#if defined(CONKER_RT64)
// Smooth is every input's default. Known: with Direct (or the stick's Direct, which makes every
// input direct), gyro now and then hitches turning left and right, never up and down; with Smooth
// throughout it doesn't. Not yet traced: input noise the spring hides, or something the game does
// to the yaw alone.
void conker::look_aim::add_options(recomp::config::Config& config) {
    using EnumOptions = const std::vector<recomp::config::ConfigOptionEnumOption>;
    static EnumOptions response = {
        {Response::Smooth, "Smooth", "Smooth"},
        {Response::Direct, "Direct", "Direct"},
    };
    static EnumOptions invert = {
        {Invert::None, "None", "None"},
        {Invert::X, "InvertX", "Invert X"},
        {Invert::Y, "InvertY", "Invert Y"},
        {Invert::Both, "InvertBoth", "Invert Both"},
    };
    const std::string about =
        "<br /><recomp-color primary>Smooth</recomp-color>: the view eases toward where you aim, as in the original game."
        "<br /><recomp-color primary>Direct</recomp-color>: the view follows it exactly, with no easing.";
    config.add_enum_option(options::stick_response, "R-Look: Stick Response",
        "How the view follows the stick in R-Look (hold R and look around)." + about +
        " This also applies to the mouse and gyro, as the view catches up with the stick at once.",
        response, Response::Smooth);
    config.add_enum_option(options::stick_invert, "R-Look: Invert Stick",
        "Inverts the stick in R-Look (hold R and look around), separately from the mouse and gyro. <recomp-color primary>Invert Y</recomp-color> is the default and matches the original game: pushing the stick up looks down.",
        invert, Invert::Y);
    // A phone has no mouse: the mouse's options are kept (hidden) for the look mode to read.
#if defined(__ANDROID__)
    constexpr bool no_mouse = true;
#else
    constexpr bool no_mouse = false;
#endif
    config.add_enum_option(options::mouse_response, "R-Look: Mouse Response",
        "How the view follows the mouse in R-Look (hold R and look around). Needs Mouse Sensitivity above zero." + about,
        response, Response::Smooth, no_mouse);
    config.add_enum_option(options::gyro_response, "R-Look: Gyro Response",
        "How the view follows gyro in R-Look (hold R and look around). Needs Gyro Sensitivity above zero." + about,
        response, Response::Smooth);
    config.add_enum_option(options::mouse_invert, "R-Look: Invert Mouse",
        "Inverts the mouse in R-Look (hold R and look around), separately from the stick and gyro. With <recomp-color primary>None</recomp-color>, moving the mouse up looks up; <recomp-color primary>Invert Y</recomp-color> matches the game's stick, where up looks down.",
        invert, Invert::None, no_mouse);
    config.add_enum_option(options::gyro_invert, "R-Look: Invert Gyro",
        "Inverts gyro in R-Look (hold R and look around), separately from the stick and the mouse. With <recomp-color primary>None</recomp-color>, the view turns the way the controller is turned.",
        invert, Invert::None);
}
#endif

#if defined(CONKER_RT64)
void conker::look_aim::on_input_poll() {
    Movement m;
    recompinput::get_mouse_deltas(&m.mouse_x, &m.mouse_y);
    recompinput::get_gyro_deltas(0, &m.gyro_x, &m.gyro_y);
#if defined(__ANDROID__)
    // The phone's own gyro, in degrees turned: at the default 25% Gyro Sensitivity, the view
    // turns as far as the phone does.
    float phone_x, phone_y;
    if (conker_android_take_gyro(&phone_x, &phone_y)) {
        const float scale = (float)recompui::config::general::get_gyro_sensitivity() / 25.0f / gyro_scale;
        m.gyro_x += phone_x * scale;
        m.gyro_y += phone_y * scale;
    }
#endif
    std::lock_guard lock{queue_mutex};
    queue.push_back(m);
    while (queue.size() > 2) {
        queue.pop_front();
    }
}
#endif

#if defined(CONKER_RT64)
namespace {
    // The game's own stick is Invert Y, so X turns around when the setting has X, and Y when the
    // setting lacks it.
    bool turn_stick_x() {
        const Invert invert = option<Invert>(options::stick_invert);
        return invert == Invert::X || invert == Invert::Both;
    }
    bool turn_stick_y() {
        const Invert invert = option<Invert>(options::stick_invert);
        return invert == Invert::None || invert == Invert::X;
    }
}
#endif

// The stick's yaw: target - stick x * scale, stored at 0x15120A14 (one state, which then goes on
// through the next one too) and at 0x15120A44 (every state). Turned around, it's a +.
extern "C" void conker_look_stick_yaw_a(uint8_t* rdram, recomp_context* ctx) {
#if defined(CONKER_RT64)
    if (turn_stick_x()) {
        ctx->f6.fl = ctx->f8.fl + ctx->f18.fl;  // 0x15120A10: sub.s $f6, $f8, $f18
    }
#endif
}

extern "C" void conker_look_stick_yaw_b(uint8_t* rdram, recomp_context* ctx) {
#if defined(CONKER_RT64)
    if (turn_stick_x()) {
        ctx->f18.fl = ctx->f4.fl + ctx->f10.fl;  // 0x15120A40: sub.s $f18, $f4, $f10
    }
#endif
}

// The stick's pitch: target + stick y * scale, stored at 0x15120A98. Turned around, it's a -.
extern "C" void conker_look_stick_pitch(uint8_t* rdram, recomp_context* ctx) {
#if defined(CONKER_RT64)
    if (turn_stick_y()) {
        ctx->f10.fl = ctx->f6.fl - ctx->f8.fl;  // 0x15120A94: add.s $f10, $f6, $f8
    }
#endif
}

// Before the pitch clamp: $s0 is the look state, $t0 the targets (reloaded at 0x15120B40).
extern "C" void conker_look_targets(uint8_t* rdram, recomp_context* ctx) {
    frame = Frame{};
#if defined(CONKER_RT64)
    Movement m;
    {
        std::lock_guard lock{queue_mutex};
        if (queue.empty()) {
            return;
        }
        m = queue.front();
        queue.pop_front();
    }

    // Directions, all measured by playing: the yaw grows to the left and the pitch downward (the
    // game's stick is inverted: up looks down). The mouse's x grows to the right and its y
    // downward. recompinput's gyro gives the controller's turn on y (positive turning left) and
    // its tilt on x (positive tilting it back, toward you). Without inverting, the mouse and gyro
    // look the way they move: mouse or controller up looks up.
    float mouse_yaw = -m.mouse_x * mouse_degrees_per_pixel, mouse_pitch = m.mouse_y * mouse_degrees_per_pixel;
    float gyro_yaw = m.gyro_y * gyro_scale, gyro_pitch = -m.gyro_x * gyro_scale;
    apply_invert(option<Invert>(options::mouse_invert), mouse_yaw, mouse_pitch);
    apply_invert(option<Invert>(options::gyro_invert), gyro_yaw, gyro_pitch);

    const float yaw = mouse_yaw + gyro_yaw, pitch = mouse_pitch + gyro_pitch;
    if (yaw == 0.0f && pitch == 0.0f) {
        return;
    }
    const gpr targets = ctx->r8;
    write_float(rdram, targets, target_yaw, read_float(rdram, targets, target_yaw) + yaw);
    frame.pitch_target_set = read_float(rdram, targets, target_pitch) + pitch;
    write_float(rdram, targets, target_pitch, frame.pitch_target_set);
    frame.targets_moved = true;
    if (option<Response>(options::mouse_response) == Response::Direct) {
        frame.direct_yaw += mouse_yaw;
        frame.direct_pitch += mouse_pitch;
    }
    if (option<Response>(options::gyro_response) == Response::Direct) {
        frame.direct_yaw += gyro_yaw;
        frame.direct_pitch += gyro_pitch;
    }
#endif
}

// After the springs: $s0 is the look state, $t0 the targets (reloaded at 0x15120E28 or 0x15120E88).
extern "C" void conker_look_currents(uint8_t* rdram, recomp_context* ctx) {
#if defined(CONKER_RT64)
    const gpr state = ctx->r16, targets = ctx->r8;
    if (option<Response>(options::stick_response) == Response::Direct) {
        // The view is where the targets are, and the spring keeps no speed to overshoot with.
        write_float(rdram, state, current_yaw, read_float(rdram, targets, target_yaw));
        write_float(rdram, targets, current_pitch, read_float(rdram, targets, target_pitch));
        write_float(rdram, targets, yaw_velocity, 0.0f);
        write_float(rdram, targets, pitch_velocity, 0.0f);
        return;
    }
    if (!frame.targets_moved) {
        return;
    }
    write_float(rdram, state, current_yaw, read_float(rdram, state, current_yaw) + frame.direct_yaw);
    // The clamp may have held the pitch target back; the current pitch mustn't pass it either.
    const float target = read_float(rdram, targets, target_pitch);
    const float clamped = target - frame.pitch_target_set;
    float pitch = read_float(rdram, targets, current_pitch) + frame.direct_pitch;
    if ((clamped < 0.0f && pitch > target) || (clamped > 0.0f && pitch < target)) {
        pitch = target;
    }
    write_float(rdram, targets, current_pitch, pitch);
#endif
}

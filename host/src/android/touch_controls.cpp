// Android: the on-screen controls, the phone's gyro and vibration, and their settings.
//
// The overlay in the app (TouchControlsView.java) presses a virtual SDL game controller through
// JNI, so the game sees it like any other controller, through RecompFrontend's input mapping
// (remappable in Controls). The game's rumble on it vibrates the phone, and the phone's gyro
// aims in R-Look (look_aim.cpp).

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdio>
#include <mutex>
#include <string>
#include <vector>

#include <SDL.h>
#include <jni.h>

#include "recompui/config.h"
#include "recompui/recompui.h"

namespace {
    SDL_Joystick* touch_joystick = nullptr;

    // TouchControlsView, for calling back into it from the game's threads (FindClass there
    // can't see the app's classes).
    jclass view_class = nullptr;
    jmethodID rumble_method = nullptr;

    // The phone's turn since the game last took it, in degrees (x: tilt, y: turn).
    std::mutex gyro_mutex;
    float gyro_x = 0.0f, gyro_y = 0.0f;

    namespace options {
        const std::string touch_opacity = "android_touch_opacity";
        const std::string touch_visibility = "android_touch_visibility";
        const std::string touch_haptics = "android_touch_haptics";
        const std::string phone_rumble = "android_phone_rumble";
        const std::string phone_gyro = "android_phone_gyro";
    }
    enum class Visibility : uint32_t { Auto, Always, Hidden };
    enum class Toggle : uint32_t { Off, On };

    template <typename T>
    T enum_option(const std::string& id) {
        return static_cast<T>(std::get<uint32_t>(recompui::config::get_general_config().get_option_value(id)));
    }

    double number_option(const std::string& id) {
        return std::get<double>(recompui::config::get_general_config().get_option_value(id));
    }

    // SDL's virtual controller rumble: the phone vibrates, as strong as the stronger motor.
    int SDLCALL rumble(void*, Uint16 low, Uint16 high) {
        if (view_class == nullptr || rumble_method == nullptr) {
            return -1;
        }
        float strength = std::max(low, high) / 65535.0f;
        try {
            if (enum_option<Toggle>(options::phone_rumble) == Toggle::Off) {
                strength = 0.0f;
            }
        } catch (...) {
        }
        auto* env = (JNIEnv*)SDL_AndroidGetJNIEnv();
        if (env == nullptr) {
            return -1;
        }
        env->CallStaticVoidMethod(view_class, rumble_method, (jfloat)strength);
        return 0;
    }
}

// The Android settings, on the General tab (conker_config.cpp).
void conker_android_add_options(recomp::config::Config& config) {
    using EnumOptions = const std::vector<recomp::config::ConfigOptionEnumOption>;
    static EnumOptions visibility = {
        {Visibility::Auto, "Auto", "Auto"},
        {Visibility::Always, "Always", "Always"},
        {Visibility::Hidden, "Hidden", "Hidden"},
    };
    static EnumOptions toggle = {
        {Toggle::Off, "Off", "Off"},
        {Toggle::On, "On", "On"},
    };
    config.add_enum_option(options::touch_visibility, "Touch Controls",
        "When the on-screen controls show. <recomp-color primary>Auto</recomp-color> hides them while a controller is connected."
        "<br />Move and resize them with the pencil button at the top of the screen.",
        visibility, Visibility::Auto);
    config.add_percent_number_option(options::touch_opacity, "Touch Controls: Opacity",
        "How visible the on-screen controls are.", 60.0);
    config.add_enum_option(options::touch_haptics, "Touch Controls: Vibration",
        "A short vibration when you press an on-screen control.", toggle, Toggle::On);
    config.add_enum_option(options::phone_rumble, "Phone Rumble",
        "The phone vibrates with the game's rumble while you play with the on-screen controls. "
        "Its strength follows Rumble Strength.", toggle, Toggle::On);
    config.add_enum_option(options::phone_gyro, "R-Look: Phone Gyro",
        "Aim in R-Look (hold R and look around) by turning and tilting the phone. "
        "Its speed follows Gyro Sensitivity.", toggle, Toggle::On);
}

// look_aim.cpp, on each input poll: the phone's turn since the last one, in degrees, or none if
// the option is off.
bool conker_android_take_gyro(float* x, float* y) {
    {
        std::lock_guard lock{gyro_mutex};
        *x = gyro_x;
        *y = gyro_y;
        gyro_x = gyro_y = 0.0f;
    }
    try {
        return enum_option<Toggle>(options::phone_gyro) == Toggle::On;
    } catch (...) {
        return false;
    }
}

// The pause menu's settings (PauseMenu.java). recompui's settings belong to the main thread, so
// the changes wait for it (conker_android_apply_pending_options, from frontend.cpp).
namespace {
    struct PendingOption {
        std::string config, option;
        int kind; // 0: enum (its index), 1: number, 2: bool
        double value;
    };
    std::mutex pending_mutex;
    std::vector<PendingOption> pending_options;

    std::string jstring_text(JNIEnv* env, jstring text) {
        const char* chars = env->GetStringUTFChars(text, nullptr);
        std::string result = chars;
        env->ReleaseStringUTFChars(text, chars);
        return result;
    }
}

void conker_android_apply_pending_options() {
    std::vector<PendingOption> options;
    {
        std::lock_guard lock{pending_mutex};
        options.swap(pending_options);
    }
    if (options.empty()) {
        return;
    }
    std::vector<std::string> changed_configs;
    for (const PendingOption& p : options) {
        try {
            recomp::config::Config& config = recompui::config::get_config(p.config);
            recomp::config::ConfigValueVariant value;
            switch (p.kind) {
                case 0: value = (uint32_t)p.value; break;
                case 1: value = p.value; break;
                default: value = p.value != 0.0; break;
            }
            config.set_option_value(p.option, value);
            config.apply_option_value(p.option);
            if (std::find(changed_configs.begin(), changed_configs.end(), p.config) == changed_configs.end()) {
                changed_configs.push_back(p.config);
            }
        } catch (const std::exception& e) {
            std::fprintf(stderr, "[android] couldn't set %s.%s: %s\n", p.config.c_str(), p.option.c_str(), e.what());
        }
    }
    // Saving also applies them (graphics: the renderer takes its new configuration).
    for (const std::string& id : changed_configs) {
        try {
            recompui::config::get_config(id).save_config();
        } catch (const std::exception& e) {
            std::fprintf(stderr, "[android] couldn't save %s: %s\n", id.c_str(), e.what());
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_codepdbh_cbfdrecomp_PauseMenu_nativeSetOption(JNIEnv* env, jclass, jstring config, jstring option, jint kind, jdouble value) {
    std::lock_guard lock{pending_mutex};
    pending_options.push_back({jstring_text(env, config), jstring_text(env, option), kind, value});
}

// An option's value: an enum's index, a number, or a bool as 0 or 1 (-1 if there's none).
extern "C" JNIEXPORT jdouble JNICALL
Java_com_codepdbh_cbfdrecomp_PauseMenu_nativeGetOption(JNIEnv* env, jclass, jstring config, jstring option) {
    try {
        recomp::config::ConfigValueVariant value =
            recompui::config::get_config(jstring_text(env, config)).get_option_value(jstring_text(env, option));
        if (auto* index = std::get_if<uint32_t>(&value)) {
            return *index;
        }
        if (auto* number = std::get_if<double>(&value)) {
            return *number;
        }
        if (auto* flag = std::get_if<bool>(&value)) {
            return *flag ? 1.0 : 0.0;
        }
    } catch (...) {
    }
    return -1.0;
}

// frontend.cpp, once SDL's joystick subsystem is up.
void conker_android_attach_touch_controller() {
    if (touch_joystick != nullptr) {
        return;
    }
    SDL_VirtualJoystickDesc desc{};
    desc.version = SDL_VIRTUAL_JOYSTICK_DESC_VERSION;
    desc.type = SDL_JOYSTICK_TYPE_GAMECONTROLLER;
    desc.naxes = SDL_CONTROLLER_AXIS_MAX;
    desc.nbuttons = SDL_CONTROLLER_BUTTON_MAX;
    // Every standard axis and button, so SDL maps them to the game controller's own.
    desc.axis_mask = (1u << SDL_CONTROLLER_AXIS_MAX) - 1;
    desc.button_mask = (1u << SDL_CONTROLLER_BUTTON_MAX) - 1;
    desc.name = "Touch Controls";
    desc.Rumble = rumble;
    int device_index = SDL_JoystickAttachVirtualEx(&desc);
    if (device_index < 0) {
        std::fprintf(stderr, "[touch] SDL_JoystickAttachVirtualEx failed: %s\n", SDL_GetError());
        return;
    }
    touch_joystick = SDL_JoystickOpen(device_index);
    if (touch_joystick == nullptr) {
        std::fprintf(stderr, "[touch] SDL_JoystickOpen failed: %s\n", SDL_GetError());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_codepdbh_cbfdrecomp_TouchControlsView_nativeSetButton(JNIEnv*, jclass, jint button, jboolean pressed) {
    if (touch_joystick != nullptr) {
        SDL_JoystickSetVirtualButton(touch_joystick, button, pressed ? SDL_PRESSED : SDL_RELEASED);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_codepdbh_cbfdrecomp_TouchControlsView_nativeSetAxis(JNIEnv*, jclass, jint axis, jint value) {
    if (touch_joystick != nullptr) {
        SDL_JoystickSetVirtualAxis(touch_joystick, axis, (Sint16)value);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_codepdbh_cbfdrecomp_TouchControlsView_nativeAddGyro(JNIEnv*, jclass, jfloat x, jfloat y) {
    std::lock_guard lock{gyro_mutex};
    gyro_x += x;
    gyro_y += y;
}

// What the overlay shows, polled a few times a second: { visible, opacity (0 to 1), haptics }.
// Hidden while a menu has the input (the launcher, settings...), which takes touches as clicks.
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_codepdbh_cbfdrecomp_TouchControlsView_nativeGetState(JNIEnv* env, jclass cls) {
    if (view_class == nullptr) {
        view_class = (jclass)env->NewGlobalRef(cls);
        rumble_method = env->GetStaticMethodID(view_class, "rumble", "(F)V");
    }
    float visible = recompui::is_context_capturing_input() ? 0.0f : 1.0f;
    float opacity = 0.6f, haptics = 1.0f;
    try {
        opacity = (float)(number_option(options::touch_opacity) / 100.0);
        haptics = enum_option<Toggle>(options::touch_haptics) == Toggle::On ? 1.0f : 0.0f;
        switch (enum_option<Visibility>(options::touch_visibility)) {
            case Visibility::Hidden:
                visible = 0.0f;
                break;
            case Visibility::Auto:
                // A game controller besides the touch one.
                if (SDL_WasInit(SDL_INIT_JOYSTICK)) {
                    for (int i = 0; i < SDL_NumJoysticks(); i++) {
                        if (SDL_IsGameController(i) && !SDL_JoystickIsVirtual(i)) {
                            visible = 0.0f;
                            break;
                        }
                    }
                }
                break;
            case Visibility::Always:
                break;
        }
    } catch (...) {
        // The settings aren't there yet.
    }
    float state[3] = { visible, opacity, haptics };
    jfloatArray result = env->NewFloatArray(3);
    env->SetFloatArrayRegion(result, 0, 3, state);
    return result;
}

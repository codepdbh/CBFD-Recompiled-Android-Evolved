// On-screen touch controls for Android: a virtual SDL game controller, which the overlay in
// the app (TouchControlsView.java) presses through JNI. The game sees it like any other
// controller, so it goes through RecompFrontend's input mapping (remappable in Controls).

#include <cstdio>

#include <SDL.h>
#include <jni.h>

#include "recompui/recompui.h"

namespace {
    SDL_Joystick* touch_joystick = nullptr;
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

// Whether a menu (the launcher, settings, mods...) has the input: the overlay hides then,
// and the menu takes touches as clicks.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_codepdbh_cbfdrecomp_TouchControlsView_nativeMenuOpen(JNIEnv*, jclass) {
    return recompui::is_context_capturing_input() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_codepdbh_cbfdrecomp_TouchControlsView_nativeSetAxis(JNIEnv*, jclass, jint axis, jint value) {
    if (touch_joystick != nullptr) {
        SDL_JoystickSetVirtualAxis(touch_joystick, axis, (Sint16)value);
    }
}

// Skip Intro: starts the game at the main menu.
//
// The game boots through three scenes: 0x25, the "For Mature Audiences Only" notice, the Dolby
// and Rare notices and the Nintendo logo; 0x21, the chainsaw opening; and 0x1D, the bar, where
// Conker walks in before the menu appears (one button press skips that). A phase (D_800E0B94)
// picks what runs each frame (func_151E51EC, conker/conker/src/game/game_20AE20.c), and each
// part asks for the next scene itself, through func_1501C730:
//   - In phase 0, func_151DE6D4 counts the logos' frames and, at 0x349 (about 14 seconds), moves
//     to phase 1 and asks for 0x21.
//   - At the end of the chainsaw opening, func_151DE85C asks for 0x1D and moves to phase 3.
// This does the logos' part as soon as they've run for ARM_FRAMES, asking for the bar instead of
// the opening, and once the bar has loaded, leaves behind what the opening's end would have.
//
// Measured, not derived: asked for on the logos' first or second frame, the bar never loads (a
// black screen, the request pending); from the third frame on it does. ARM_FRAMES leaves room
// above that, and the bar is up 0.3 seconds after Start Game. Doing the opening's end inside the
// logos (phase 3 there) also leaves a black screen, so that waits for the bar.
// Known: a soft reset later on plays the whole boot again, as each step here happens once per
// start of the game.

#include "modding.h"
#include "PR/ultratypes.h"

#define SCENE_BAR 0x1D
// The logos' frame count at which the bar is asked for (the game asks for the opening at 0x349).
#ifndef ARM_FRAMES
#define ARM_FRAMES 10
#endif

#define logo_frames   (*(volatile s32*)0x800E0A90)

#define scene_in_play (*(volatile s32*)0x800BE9F0)
#define boot_phase    (*(volatile u8*)0x800E0B94)
#define D_800D2E40    (*(volatile u8*)0x800D2E40)
#define D_800E0B96    (*(volatile u8*)0x800E0B96)
#define D_8008FD80    (*(volatile u8*)0x8008FD80)
#define D_8008FDA4    (*(volatile u8*)0x8008FDA4)
#define D_8008FE28    (*(volatile u8*)0x8008FE28)
#define gGameState    (*(u8* volatile*)0x8008FDD4)

void func_1501C730(s32 arg0, s32 arg1, u32 arg2, s32 arg3, s32 arg4);

static int bar_requested = 0;
static int bar_set_up = 0;

// func_151DE6D4 at the end of the logos, but ARM_FRAMES in, and asking for the bar.
RECOMP_HOOK_RETURN("func_151DE6D4") void skip_intro_after_logos_frame(void) {
    if (bar_requested || logo_frames < ARM_FRAMES) {
        return;
    }
    bar_requested = 1;
    D_8008FE28 = 2;
    boot_phase = 1;
    D_800D2E40 = 0;
    func_1501C730(6, SCENE_BAR, 0, 0, 1);
    D_800E0B96 = 0xFF;
}

// Once the bar has loaded: what func_151DE85C leaves behind after its request (byte stores, as
// its instructions have them).
RECOMP_HOOK_RETURN("func_15007A70") void skip_intro_after_scene_load(void) {
    if (!bar_requested || bar_set_up || scene_in_play != SCENE_BAR) {
        return;
    }
    bar_set_up = 1;
    D_800D2E40 = 0;
    boot_phase = 3;
    D_8008FD80 = 1;
    D_8008FE28 = 2;
    D_8008FDA4 = 0;
    if (gGameState != 0) {
        gGameState[0x3E] = 0;
        gGameState[0x2B] = 5;
        gGameState[0x2C] = gGameState[0x2B];
    }
}

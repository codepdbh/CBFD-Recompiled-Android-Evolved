# Handoff: mouse orbit camera, frame clear, widescreen door clipping

Status as of 2026-09-27. This lives only on the `mouse-orbit-wip` branch of the test clone
(`D:\Retro Emulation\Conkers Recomp Test`; renamed 2026-09-28 without the apostrophe, so the old `build-win` folders had to be deleted), not on main. Experiments stay here until the
user has tried them in game; then they are ported to the original repo
(`D:\Retro Emulation\Conker's Recomp`, public as sciaschi/CBFD-Recompiled).

## Where things stand

### Released / on main
- v0.1.3 is PR #33:
  - backdrop buffer overflow crash fix (ultrawide, looking at the sky);
  - backdrop sectors widened;
  - Linux file dialogs through xdg-desktop-portal instead of GTK.
- The user merges it and tags V0.1.3.
- PR #35 (R-Look: mouse/gyro aiming, by another contributor) is recommended to merge after
  #33. It fixed B-pad aiming (the slingshot in the hub world), which previously only allowed
  left and right.

### On this branch
Commits:
- `94e23e6`: WIP orbit camera.
- `9951bf6`: merge of v0.1.3 and PR #35; zoom limited to the controller's distance range.
- `cf28983`: look mode (hold R, B-pad aiming) hands the camera back to the aim code. This fixed
  the camera turning faster than the aim.

What the user has confirmed working:
- **Mouse orbit:** yaw and pitch.
- **Scroll wheel zoom:** within the controller's nearest and farthest presets.
- **Aiming:** no conflict in story mode.
- **Handing back to the game:** C-left/right, cutscenes and look mode.

Multiplayer mouse is deferred on purpose; the user wants story mode stable first.

## Start here: open work, in priority order (2026-09-27, end of session)

### 1. Widescreen clipping of doors and objects: FIXED in RT64. The user confirmed it looks much better in several spots (2026-09-28), and at 32:9 too

**Still to do:** remove the log and the switches, port to main (and check the camera's wall check, the sky, glows, pause and iris before that).

**The real cause (from `CONKER_RT64_FBLOG`, 2026-09-28; the depth theory below was wrong).**
- **What the log ruled out:** RT64 never reloads, clears or resizes the depth target, and
  the pair after the copy is widened like the one before.
- **What it found:** the pair after the copy (pair 3, the objects, 7 calls) got a target only
  **292×51** native (1920×255). A pair's target height is `drawColorRect.bottom`.
- **How that rect is built:** `RSP` (rt64_rsp.cpp, draw rect per triangle) intersects each
  triangle's box with the **4:3** scissor and viewport, and drops it if that's empty.
- **Why widescreen cuts the objects:** triangles beside the 4:3 area, visible only because
  the picture is widened, don't count. A pair drawn mostly at the side (the doors at the right
  edge: drawColorRect x 249..290) is sized from its small 4:3 part, and everything below
  that row is cut off, by an amount that depends on the camera angle.
- **Why only with the copy:** the copy splits the frame, so these objects get a pair of
  their own. Without it they're in pair 1, which is full height because the level fills
  it. At 4:3 nothing drawn is outside the area.
- **First fix, not enough (user's screenshots, 2026-09-28):** clamping triangles into the 4:3
  area horizontally. Doors at the right edge still lost pieces, and doors at the top left
  vanished entirely.
- **Why:**
  - A pair whose triangles are all beside the 4:3 area had a zero-width draw area.
    `isEmpty()` then skipped the whole pair on the widened render, so the door vanished.
  - Triangles crossing the camera plane project with a flipped y (the divide by w
    isn't guarded), so they either still don't count or are culled as back-facing.
- **Fix now (both in recomp/rt64.patch):**
  - `rt64_rsp.cpp`: clamp the triangle's box into scissor∩viewport horizontally, at least one pixel
    wide, so a pair drawn only at the side still has a draw area and is drawn.
  - `rt64_workload_queue.cpp` (`getTargetsFromPair`): the widened render uses the
    framebuffer's full height (`max(drawColorRect.bottom, fb->height)` when the widths
    match), not the rows the 4:3 measure saw.
- **To check:**
  - the doors at 16:9 and 32:9 with no switches set;
  - the camera still turns for walls;
  - no regressions in sky, glows, pause, iris;
  - later, whether the camera row copy (`tileCopy` left 0 in the widened target) needs
    centring for the camera's wall check.

**The earlier (wrong) theory, kept for reference:**

**Symptom.** In widescreen only (fine at 4:3), doors lose pieces depending on the camera
angle, and so do most objects drawn in the same pass. Examples: the doors on either side of
the Feral Reserve building, and the "CLOSED" door in the Windy caves.

**Cause.** `func_1510B9D0` is the per-camera level pass. At 0x1510BE20 it calls
`func_1512E5F0`, camera code, when the camera struct (`$a1` = sp+0x94) has +0x8B8 set and
bit 0x8 of +0x84 on.
- **What the copy does:** it switches the colour image to a 1-pixel-wide image (0xFF100000 at
  0x1512E7AC), sets a scissor, and copies depth-buffer pixels into it.
- **Who reads it:** `func_1512B1B8` decodes the samples with the N64 depth table `D_80089630`.
- **What breaks:** RT64 then starts a new framebuffer pair for the main screen ("The color image
  was changed"). Everything drawn after it (objects in the later passes) is depth-tested
  wrongly. The probable reason: RT64 syncs depth to RDRAM (292 wide) and reloads it, so it no
  longer lines up with the widened picture.

**Confirmed by the user at 1080p:**
- `CONKER_SKIP_DEPTH_CAMERA=1` (skips that copy): nothing clips.
- `CONKER_SKIP_DEPTH_PROBES=1` (the other copy, `func_151742EC` → `func_15173D00`, 4 pixels
  wide): still clips. So it's the camera copy only.

**What the copy is for.** `func_1512E5F0` builds an RDP copy of one full row of the depth
buffer into the buffer at camera +0x8BC:
- the row is camera +0x8BA, the full width `D_800BE620`, 292;
- the texture is the depth buffer (`D_800BE9C4`), loaded as a 16-bit texture with LoadBlock;
- the colour image is set to +0x8BC at width 292, with a 1-row scissor, and a copy-mode texture
  rectangle does the copy;
- afterwards `func_1501A680` and `func_1501A490` restore the main colour image and scissor.

Next frame, `func_1512B1B8(camera, x, a2, depth)` scans that row left and right from
Conker's column (called from 0x1512AF6C). It decodes each sample, compares it with Conker's
depth, and calls `func_1512B630` to record occluders in the per-camera tables `D_800DC0C0`.
This is most likely the controller camera noticing a wall hiding Conker and swinging around
it (not yet confirmed in game). So skipping the copy would change camera behaviour.

**Decision (user, 2026-09-27): fix it in RT64 (option b); keep the game's copy as it is.**
Option (a), skipping the copy in widescreen and filling +0x8BC with 0xFFFC ("nothing in
front"), was turned down because the camera would lose that behaviour. Keep it only as a
fallback.

**Next steps (RT64 side):**
1. **Reproduce.** Use the inspector (`CONKER_DEV_MODE=1`, F1) in widescreen at the Feral
   Reserve doors. Framebuffer pair #2 should be the 292×1 image at camera+0x8BC, and #3 the
   main screen resumed ("The color image was changed").
2. **Find what RT64 does to the depth target around pair #2.** Suspects, most likely first:
   - **Depth target resized or converted:** the tiny pair's scissor isn't 4:3, so
     `adjustRatio` is false (`rt64_framebuffer_renderer.cpp`, about line 1435, scissor ratio
     vs `aspectRatioSource`). That pair may be rendered without widening. Pair #2 still has
     the main depth image attached, so RT64 may resize or convert the depth target to the
     narrower resolution, and #3 then gets it back misaligned.
   - **Depth reloaded from RDRAM:** the depth buffer is read as a texture (SETTIMG = the
     depth address). RT64 may sync depth to RDRAM (292 wide) for that, then mark the target
     stale and reload it from RDRAM for pair #3, which drops the widened area and shifts the
     rest.
   - **Where to look:** `rt64_framebuffer_manager.cpp` / `rt64_framebuffer_changes.cpp`
     (fb-as-texture, RDRAM sync), `rt64_render_target_manager` (target sizes per address),
     and the `checkFramebufferPair` / flush path in `rt64_state.cpp` / `rt64_rdp.cpp`.
3. **Fix.** When a pair only reads the depth image as a texture and doesn't draw with depth
   (copy mode, no Z), leave the widened depth target alone: no resize, no reload, and no
   RDRAM write-back that later overrides it. The game still needs its row copy to hold real
   depth, 292 samples at the N64 resolution, taken from the middle 4:3 area of the widened
   target. So the copy has to sample the widened target at the right place, or RT64's
   existing fb-to-RDRAM path has to produce that without touching the GPU target.
4. **Patch location:** the change goes in `recomp/rt64.patch` (build.cmd/build.sh apply it to
   tools/rt64). Regenerate the patch from tools/rt64 after editing.
5. **Verify.** The doors stay whole at 16:9 and 32:9 (`host/multi_aspect.ps1`) without
   `CONKER_SKIP_DEPTH_CAMERA`. The camera still swings when Conker walks behind a wall (compare
   with 4:3). No regressions in the sky, the glows or the frame copies (pause and iris).
6. **Then:** remove the TEMP-DEBUG switches, name and document `func_1512E5F0` and
   `func_1512B1B8` (project rules), and port to main once the user confirms.

**Code reading, 2026-09-28 (cloud session, no ROM, nothing run):**
- The copy's display list (`func_1512E5F0`): othermode 0xEF202CFF / L=0, so copy mode with
  no Z compare or update. Pair #2 therefore has no depth image attached in RT64, and RT64
  never resizes or redraws the depth target for it.
- Order in RT64: the LoadBlock of the depth row comes before SETCIMG. So `checkImageOverlap`
  ends pair #1 ("SamplingFromDepthImage"). The tile copy of the depth row lands in pair
  #2's start operations (`createTileCopyRecord`: depth → colour copy, then
  `discardLastWrite` on the depth framebuffer). None of this writes to the hi-res depth
  target.
- Only two paths could overwrite the widened depth target in pair #3:
  (A) the depth framebuffer flagged `rdramChanged`/`formatChanged` in
      `State::submitFramebufferPair`. Then `WorkloadQueue` clears it and reloads it from the
      RDRAM snapshot (292 wide, 4:3): correct at 4:3, misaligned when widened. That matches
      the symptom.
  (B) pair #3's projections not widened the way pair #1's are. `ProjectionProcessor` and
      `FramebufferRenderer` decide widening per pair from `fbPair.scissorRect`. Then the
      doors' depth wouldn't line up with the level's.
- The tile copy samples the widened row from x=0 (`tileCopy.left = fbTile.left * scale.x`),
  not from the centred 4:3 area. So the game's camera row is shifted in widescreen. Fix it
  together with this bug.
- The CPU also writes 0xFFFC into the +0x8BC buffer (game_157840.c, around line 545). +0x8BC is a
  pointer, and where that buffer is allocated is unknown.
- **Next:** add an env-gated RT64 log. Per pair: addresses, flush reason, scissor,
  depthRead/Write, colour/depth formatChanged, syncRequired, start ops. In the hi-res
  record: depth clears and reloads. Per projection: adjustAspectRatio. Run it once at the
  Feral Reserve doors at 16:9 to tell (A) from (B). This needs the user's machine (ROM,
  build, game).

**The RT64 log (TEMP-DEBUG, 2026-09-28), to tell (A) from (B):**
- **Turning it on:** it's in `recomp/rt64.patch` (new `src/common/rt64_fblog.h`). Off unless
  `CONKER_RT64_FBLOG` is set.
- **What it writes:** 2 frames out of every 150 (about every 5 s), 40 times, to
  `rt64_fb_log.txt` in the working directory. Rects are 10.2 fixed point, so 4 = one pixel.
- **Line prefixes:**
  - `[hle N]`: the game's side, per pair as it's submitted: addresses, flush reason,
    rects, depthRead/Write, and the colour and depth framebuffers' widthChanged,
    sizChanged, rdramChanged and formatChanged. Also pending ops (tile copies), `changeRAM`
    (who marks whom rdramChanged), and `checkRDRAM` (framebuffers the CPU changed).
  - `[hires N]`: the widened render, per pair: targets and sizes, resizes, and every time the
    depth target is CLEARED, reloaded from RDRAM, or overwritten from its colour-type
    target. Also tile copy setup/record and writeChanges (RDRAM uploads).
  - `renderer pair` lines: the pair's adjustRatio, and useWideViewport per projection.
  - `[proj N]`: the projection processor's adjustAspectRatio per projection.
    Transforms shared by several pairs are adjusted by whichever of them comes last.
- **Reading it:** find the frame's pair with colour width 292 whose drawColorRect is one
  row (the camera copy). Then look at the pair after it (the main screen resumed):
  - (A) it shows `DEPTH target CLEARED` or `rows ... reloaded from RDRAM`, and the `[hle]` depthFb line
    says which flag caused it;
  - (B) its `adjustRatio`, `useWideViewport` or `adjustAspectRatio` differ from the pair
    before the copy.
- **To run** (from the repo root, as the rt64 patch changed): `.\build.cmd`, then in
  `host\build-win`:
  `$env:CONKER_RT64_FBLOG=1; .\ConkerRecomp.exe; Remove-Item Env:CONKER_RT64_FBLOG`.
  Stand at the Feral Reserve doors in 16:9 with a door visibly clipped for ~30 s.
  A second run with `CONKER_SKIP_DEPTH_CAMERA=1` as well gives the comparison.

**Tools that found it:**
- `CONKER_DEV_MODE=1` turns on RT64's developer tools. Set Mouse Sensitivity to 0, press F1,
  right-click the scene, and pick draw calls.
- A framebuffer pair whose flush reason is "color image was changed" was the clue.

### 2. Light glows: the depth fix works, but they clip at the screen edges (next small fix)
**Status:** the user confirmed the glows stay lit now. But they disappear toward the left and
right edges in widescreen.

**Cause:** `func_151408A4` is the glow. It keeps a glow only while the light's projected point
(from `func_15144CEC`) is inside the camera's 4:3 screen bounds:
- left +0x2C and right +0x30, compared at 0x15140980–0x151409A8;
- top +0x24 and bottom +0x28 after that.

There's no margin. The glow itself is drawn as a 3D billboard (matrices through
`func_151D5D60`), so widescreen draws it fine anywhere; only this check is 4:3.

**Fix (written 2026-09-28, cloud session, not built or tried in game):** hooks
`conker_widen_light_glow_left/right` in `widescreen.cpp` (beside the sprite cull, same
margin), wired in conker.toml at 0x15140988 and 0x151409A0. Branch
`claude/project-thread-rbl6nv`.

**Edge fix confirmed by the log (2026-09-28):** $f8 is the light's x, $f10 = 2 and $f4 = 290
(the 4:3 bounds). The user confirmed that glows now show past the 4:3 edges.

**Still open: in widescreen the door glows vanish inside the 4:3 area (straight on).** Read
from the user's RecompiledFuncs (funcs_81.c): after the bounds, func_151408A4 only checks
facing (light dir · camera offset), distance, and, only when flag 0x10 of +0x58 is set, the
depth sample. The door lights don't have 0x10 (the log never reached 0x15140DB4). Nothing
after the bounds depends on screen x, so the hiding is RT64's (most likely the glow failing
depth against the level inside 4:3, since the light sits 3 to 11 units in front of the wall).
Asked the user to test with the aspect ratio set to Original, and with `CONKER_SKIP_DEPTH_CAMERA=1`.

**Fix plan (as done):**
- Add hooks before the two x compares: 0x15140988 (`c.lt.s $f8, $f10`, where `$f10` = left) and
  0x151409A0 (`c.lt.s $f4, $f8`, where `$f4` = right).
- Widen `$f10` by −margin and `$f4` by +margin. Use the same margin as the sprite cull in
  `widescreen.cpp`: half_frame × (ratio − 1) + 8, where ratio = window aspect / (4/3).
- Put the code in `render_fixes.cpp`, which can call a shared margin helper exported from
  `widescreen.cpp`.
- The depth-sample x written for that light may then be off the 4:3 buffer. That's harmless,
  since the depth test is bypassed.

**Earlier details:**
- **What was wrong:** `func_151408A4` hides a glow when a depth sample says something is in front
  of the light. On RT64 the sample isn't real depth, so glows vanished with distance (the Feral
  Reserve sign lights).
- **The fix:** a hook at 0x15140DB4 (`conker_light_glow_depth` in `render_fixes.cpp`) forces
  the test to pass. It still writes a TEMP-DEBUG `light_glow_log.txt`.
- **To check:** the user hasn't confirmed it in game; the log only appears once those lights are on
  screen.
- **The other system:** a second glow system (`func_1517E1AC` reads depth from RDRAM with the
  CPU; `func_1517E28C` fades it) may need the same treatment.

### 3. Frame clear: confirmed good
`D_800BE635` is forced on through `conker_frame_clear` (hook in `func_1510FEA0` at
0x1510FFA4), so each frame starts black instead of repeating the last one in the void. It
has a TEMP-DEBUG `CONKER_NO_FRAME_CLEAR` switch. It is a candidate to port to main on its
own; it helps the controller camera too.

### TEMP-DEBUG to remove before porting
- `CONKER_CULL_EXTRA` and `CONKER_NO_CULL_WIDEN` in `widescreen.cpp`.
- `CONKER_DEV_MODE` in `frontend.cpp`. Could stay as a developer feature if the user wants.
- `CONKER_RT64_FBLOG` in `recomp/rt64.patch`: all of `src/common/rt64_fblog.h` and every block marked
  TEMP-DEBUG (Conker) in rt64_state.cpp, rt64_framebuffer_manager.cpp, rt64_workload_queue.cpp,
  rt64_framebuffer_renderer.cpp and rt64_projection_processor.cpp.
- `CONKER_NO_FRAME_CLEAR`, `CONKER_SKIP_DEPTH_CAMERA`, `CONKER_SKIP_DEPTH_PROBES` and the glow log
  in `render_fixes.cpp`, plus their two toml hooks (`func_1510B9D0`, `func_151742EC`).

## Orbit camera walls, attempt 4 (2026-09-28): let the game's own collision do it

- **Why it's worth trying:** the user confirmed the C-button camera stops at walls and
  slides along them, so `func_1512BB10` works for the game's own camera.
- **Why the orbit went through walls:** it wrote all three eye copies in `func_151284C4`,
  after that collision had run, which threw away its result.
- **The new hook:** `conker_mouse_camera_collide` at `func_1512BB10` +4.
  - It places the orbit's wanted eye in +0x2F8, the same field the C-button turning sets
    earlier in `func_15122C5C`.
  - The collision sweeps from last frame's drawn eye (+0x304, saved at the start of
    `func_15122C5C` from +0x2EC) to that eye and leaves the result in +0x2F8.
  - `func_1512C490` copies it to +0x2EC.
- **The view hook** (`conker_mouse_camera`) now only resets the per-frame flags.
- **Status:** the user confirmed it no longer clips (2026-09-28). It was jumpy when caught
  on a wall, compared with the C-buttons.
- **Jumpiness fix (built, not yet tried):**
  - The orbit keeps turning while a wall holds the camera, so the collision made one long
    sweep each frame from the held spot, and it landed somewhere different each time.
  - Now, when the camera is further from the wanted eye than the orbit itself moved since
    last frame (+ 8 units), +0x2F8 gets only `catch_up` (0.3) of the way from +0x304 each
    frame. Otherwise it gets the whole way, so free orbiting and zoom are unchanged.
- **Watch for:** getting stuck behind pillars while orbiting (the sweep follows a straight
  line from the old eye), and flicks through thin walls.

## Change of plan (user's idea): let it clip, clear the frame

The orbit camera no longer tries to collide. The collision code and the compare hooks were
removed (the notes below are kept for reference). They are still in commit 1f31ddf.

What was really wrong on screen was the "hall of mirrors" in the void.
- **What the game does:** it never clears the colour buffer. `func_1510FEA0` starts each
  frame's display list and clears the depth buffer (`func_151106A8`). It clears the colour
  buffer to black (`func_15110544(dl, 0, 0, width, height, 0, 0, 0)`) only while
  `D_800BE635` is set.
- **Why the flag is off:** level setup (`func_1501A220`) clears it, so the game relies on the
  level and sky covering every pixel.
- **The fix:** a toml hook in `func_1510FEA0` at 0x1510FFA4 forces `$t8` (the flag, just read) to 1,
  so every frame starts black.
- **Status:** built; waiting for the user to try it in game.
- **Watch for:** any effect that depended on the old picture staying (none known), and whether
  RT64's widescreen covers the full width with that fill (it fills the whole
  framebuffer).

Also related: `func_151103C8` (in a function table at 0x800891D8) clears one camera's viewport
to the colour `D_800DBEA8` (RGB), probably the fog colour. That colour could be used instead
of black.

## Earlier problem (shelved): the orbit camera goes through walls

### Goal
When a wall is between Conker and the orbit eye, pull the camera in toward Conker. When the
way is clear, ease it back out.

### What didn't work
1. **`func_150AC9C0`:** a level ray the game casts in `func_15123A54`. It hardly ever hits;
   even the game's own call didn't hit when the user pushed the camera into walls with the
   C-buttons.
2. **One long move with `func_15044380`:** move-with-collision from the look-at point to the
   eye. It returned 0 hits every frame, and the collider ended exactly at its target.
3. **The same move in short steps** (0.75 × collider radius each). Still 0 hits on every
   frame. So the move length isn't the problem: our stand-in collider (or some game state at
   the time we call) makes the collision code ignore everything.

### What the game does (`func_1512BB10`)
The game's own camera collision runs each frame, called from `func_15122C5C` before the
view is built by `func_151284C4`.

- **Collider radii by mode:** camera +0x94C/+0x950, eased into +0x95C/+0x960 by `func_150495B0`.
- **A stand-in object on its stack, at sp+0x74, 0x32C bytes** (gObject size):
  - +0x00 = 0x2D (type: camera; `func_15044660` uses the type to size the collider);
  - +0x14/+0x18/+0x1C = target position (the desired eye, camera +0x2F8);
  - +0x20 = 0;
  - +0x28 = y − camera+0x354;
  - +0x40 = camera+0x37C (yaw);
  - +0x180 = camera+0x354;
  - +0x188 = camera+0x644;
  - +0x318 = camera pointer.

  The rest is uninitialised stack.
- **Collision switches set around the call:**
  - D_800CBDD2 = 1, and back to 0 after;
  - D_800CBDD3 = (camera+0x3D0)->+0x102 ? 1 : 0, restored after;
  - D_800CBDD4 is set from camera+0x23C / +0x2C, restored after;
  - the layer enables D_80089120[2] and [1] are cleared in some modes, restored after.
- **The call:** `func_15044380(f12, f14, a2 = previous eye (+0x304..+0x30C), a3 = object, sp10 = 0, sp14 = 0)`.
- **Afterwards:** object +0x14.. is the corrected position, written back to camera +0x2F8..,
  and v0 is the hit count.

`func_15044380` does this:
- `func_15044660(obj, x, y, z)` sets the collider size globals D_800CBDD8/DDC.
- For layers 3..0 that are enabled in D_80089120: `func_1510F800(layer)`, then, if
  D_800DBE62 is set, `func_150AB1F0(x0, y0, z0, obj, flags)`, adding up the hits.
- `func_1510F800(0)` at the end.

`func_150AB1F0` is hand-written assembly (computed `jr` through addresses kept on its stack):
- It branches on D_800DBE50 (2 = level mesh via `func_150A64C8`/`func_150A44F0`, 3 = objects).
- It walks collision data from D_800DBE48, box-tested against the move.
- It reads the object's +0x14..+0x1C, +0x20, +0x28 and +0x40.

### The current experiment (built, not yet run)
- **Hooks:** `func_1512BB10` is hooked at 0x1512BEBC (before its own call) and 0x1512BEC4
  (after it). The hook functions are `conker_wall_compare_before`/`_after`, at the end of
  `host/src/mouse_camera.cpp`; the hooks are at the end of `conker.toml`.
- **What it compares:** before the game's call, `collide_camera` (our stand-in) runs with the
  same start and target. Both results go to `host/build-win/wall_compare_log.txt`, with the
  hit counts, end positions, target, D_800DBE62 and D_800DBE50.
- **The user's test:** push the camera into walls with only the C-buttons, so the game's call
  gets hits.
- **Reading the log:**
  - If the game hits and ours doesn't with identical inputs, the difference is in the
    stand-in object. Next step: copy the game's whole object (sp+0x74, 0x32C bytes) instead of
    zeros, then narrow down the field that matters.
  - If neither hits, the collision call isn't what stops the game's camera, and the next
    place to look is `func_15122C5C`'s other camera code.

`collide_camera()` in `mouse_camera.cpp` builds the stand-in and does the call. It now saves
and restores D_800CBDD2 as well, so it's safe inside the game's own call.

The orbit hook `conker_mouse_camera` currently marches the collider out in short steps and
logs to `mouse_camera_log.txt` (TEMP-DEBUG). Once walls work, remove all TEMP-DEBUG code and
hooks.

## Orbit camera map (`host/src/mouse_camera.cpp`)

Hooks, all in `conker.toml`:
- **`conker_mouse_camera_follow`**: `func_1512D390` @0x1512D54C (C-button turning). It marks
  that the follow camera ran; C-left/right disengage the orbit.
- **`conker_mouse_camera_look_mode`**: `func_15120158` @0x1512015C (look mode). It disables the
  orbit for that frame.
- **`conker_mouse_camera_collide`**: `func_1512BB10` @0x1512BB14 (the camera's collision; $a0 =
  camera). It reads the mouse and wheel, then places the eye it wants from our yaw, pitch and distance
  around the look-at point (+0x2BC) in +0x2F8, for the collision to move the camera toward.
- **`conker_mouse_camera`**: `func_151284C4` @0x151284C8 (builds the view). It ends the frame:
  resets the flags.
- **Scroll wheel:** an SDL event watch (`conker_mouse_camera_init`, called from frontend.cpp).

Camera (struct108), the follow camera:
- **Positions:**
  - pivot at Conker's feet: +0x2A4;
  - look-at point (pivot + 67.5): +0x2BC.
- **Distance:**
  - height: +0x344;
  - horizontal distance: +0x374.
- **Angles:**
  - yaw in degrees, recomputed from the eye each frame by `func_15125330`: +0x37C;
  - radians: +0x39C/+0x3A0.
- **Buttons:**
  - +0x36A: button bits;
  - +0x36C: pointer to the buttons held;
  - +0x6B0: C-turn direction.
- **Controller distance presets:** D_800A34B0, 4 × {horizontal, height}: {267,100}, {247,100},
  {370,185}, {530,400}.

## Building and testing
- **Recompile after a `conker.toml` change:** `py -3 recomp\recompile.py` from the repo root.
- **Build:** `host\build_windows.cmd` → `host\build-win\ConkerRecomp.exe`. The user tests
  from there, with the game closed while building.
- **The user's terminal is PowerShell:** env vars are `$env:X=1; ...; Remove-Item Env:X`.
- **Look out for:** a `\n` inside C strings written through heredocs/python can turn into a real
  newline and break the build.

## Project rules (from the user)
- **When proposing a fix, ship the matching C and the name improvements with it.** `func_`
  names are temporary: rename them once they're matched and understood (descriptive name +
  address suffix). Document every function. Ugly functions get a two-pass match.
- **Once walls work:**
  - name and document `func_1512BB10` (camera collision), `func_15044380`
    (move a collider through the level), `func_15044660` (collider size),
    `func_150AB1F0` and `func_15122C5C`;
  - keep mod-facing `func_` names where mods/hooks use them.
- **Nothing reaches the original repo until the user confirms it in game.**

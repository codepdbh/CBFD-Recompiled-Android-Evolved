package com.codepdbh.cbfdrecomp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * On-screen N64 controller over the game. It presses a virtual SDL game controller
 * (host/src/android/touch_controls.cpp), so the game takes it like any controller, through
 * the Controls mapping: A = south, B = west, Z = left trigger, R = right trigger,
 * L = left shoulder, C buttons = right stick, Start = start, the menu button = back.
 *
 * A touch that starts away from every control goes on to the game's window, and while a
 * menu has the input (the launcher, settings...) the overlay hides and the menu takes
 * touches as clicks.
 *
 * The pencil button edits the layout: drag a control to move it, and resize the selected
 * one with - and +. The layout is kept in ConkerRecompiled/touch_layout.json.
 */
public class TouchControlsView extends View {
    private static final String TAG = "ConkerRecomp";

    // SDL_GameControllerButton and SDL_GameControllerAxis.
    private static final int BUTTON_SOUTH = 0, BUTTON_WEST = 2, BUTTON_BACK = 4, BUTTON_START = 6,
        BUTTON_LEFTSHOULDER = 9;
    private static final int AXIS_LEFTX = 0, AXIS_LEFTY = 1, AXIS_RIGHTX = 2, AXIS_RIGHTY = 3,
        AXIS_TRIGGERLEFT = 4, AXIS_TRIGGERRIGHT = 5;
    private static final int AXIS_MAX = 32767;

    static native void nativeSetButton(int button, boolean pressed);
    static native void nativeSetAxis(int axis, int value);
    static native void nativeAddGyro(float x, float y);
    /** { visible, opacity (0 to 1), haptics }, from the game's settings and menus. */
    static native float[] nativeGetState();

    private static Vibrator vibrator;

    /** The game's rumble on the touch controller (touch_controls.cpp): 0 stops it. */
    static void rumble(float strength) {
        Vibrator v = vibrator;
        if (v == null) {
            return;
        }
        if (strength <= 0.01f) {
            v.cancel();
        } else {
            int amplitude = Math.max(1, Math.min(255, Math.round(strength * 255)));
            v.vibrate(VibrationEffect.createOneShot(1000, amplitude));
        }
    }

    private enum Kind { BUTTON, TRIGGER, C_BUTTON, STICK }

    /** A control, placed by its center as a fraction of the view's size, sized in dp. */
    private static final class Control {
        final String key; // in touch_layout.json
        final Kind kind;
        final String label;
        final int id; // button, trigger axis, or C direction (0 left, 1 right, 2 up, 3 down)
        final float defaultX, defaultY, radiusDp;
        final int color;
        final boolean wide;
        float fx, fy, scale = 1;
        float cx, cy, radius;
        boolean pressed;

        Control(String key, Kind kind, String label, int id, float fx, float fy, float radiusDp, int color, boolean wide) {
            this.key = key;
            this.kind = kind;
            this.label = label;
            this.id = id;
            this.defaultX = this.fx = fx;
            this.defaultY = this.fy = fy;
            this.radiusDp = radiusDp;
            this.color = color;
            this.wide = wide;
        }

        boolean hit(float x, float y, float slop) {
            float r = radius * slop;
            if (wide) {
                return Math.abs(x - cx) <= r * 1.6f && Math.abs(y - cy) <= r;
            }
            float dx = x - cx, dy = y - cy;
            return dx * dx + dy * dy <= r * r;
        }
    }

    private static final int BLUE = 0xFF3A6FF0, GREEN = 0xFF2FA84F, YELLOW = 0xFFF2C230,
        GREY = 0xFF9A9A9A, RED = 0xFFD8403A, ORANGE = 0xFFF08A24;
    private static final float MIN_SCALE = 0.5f, MAX_SCALE = 2.5f, SCALE_STEP = 0.1f;

    private final List<Control> controls = new ArrayList<>();
    private final Control stick;
    private final Map<Integer, Control> pointers = new HashMap<>();
    private float stickX, stickY; // -1..1, the knob's offset
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    // Layout editing.
    private boolean editing = false;
    private Control selected = null;
    private int dragPointer = -1;
    private float dragOffsetX, dragOffsetY;
    private final RectF editButton = new RectF();
    private final String[] toolLabels = { "−", "+", "Reset", "Done" };
    private final RectF[] toolRects = { new RectF(), new RectF(), new RectF(), new RectF() };

    /**
     * Hidden while a menu has the input (touches go to it then), or as the Touch Controls
     * setting says; opacity and haptics come from the settings too.
     */
    private boolean hidden = true;
    private float opacity = 0.6f;
    private boolean haptics = true;
    private final Runnable checkState = new Runnable() {
        @Override
        public void run() {
            float[] state = null;
            try {
                state = nativeGetState();
            } catch (UnsatisfiedLinkError e) {
                // The game's library isn't there.
            }
            if (state != null) {
                boolean newHidden = state[0] < 0.5f;
                boolean changed = newHidden != hidden || Math.abs(state[1] - opacity) > 0.01f;
                haptics = state[2] > 0.5f;
                opacity = state[1];
                if (newHidden != hidden) {
                    hidden = newHidden;
                    if (hidden) {
                        releaseAll();
                        if (editing) {
                            finishEditing();
                        }
                    }
                }
                if (changed) {
                    invalidate();
                }
            }
            postDelayed(this, 150);
        }
    };

    // The phone's gyro, in degrees turned around the screen's axes (x: tilting toward you,
    // y: turning left), for aiming in R-Look (look_aim.cpp).
    private final SensorManager sensors;
    private long lastGyroTime = 0;
    private final SensorEventListener gyroListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (lastGyroTime != 0) {
                float dt = (event.timestamp - lastGyroTime) * 1e-9f;
                if (dt > 0 && dt < 0.1f) {
                    // The sensor's axes are the phone's natural (portrait) ones: turn them to the
                    // landscape screen's.
                    float wx = event.values[0], wy = event.values[1];
                    float sx, sy;
                    if (getDisplay() != null && getDisplay().getRotation() == Surface.ROTATION_270) {
                        sx = -wy;
                        sy = wx;
                    } else {
                        sx = wy;
                        sy = -wx;
                    }
                    float toDegrees = (float) (180.0 / Math.PI) * dt;
                    try {
                        nativeAddGyro(sx * toDegrees, sy * toDegrees);
                    } catch (UnsatisfiedLinkError e) {
                        // The game's library isn't there.
                    }
                }
            }
            lastGyroTime = event.timestamp;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    public TouchControlsView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        sensors = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        stick = new Control("stick", Kind.STICK, "", 0, 0.15f, 0.68f, 64, GREY, false);
        controls.add(stick);
        controls.add(new Control("z", Kind.TRIGGER, "Z", AXIS_TRIGGERLEFT, 0.07f, 0.36f, 30, GREY, false));
        controls.add(new Control("l", Kind.BUTTON, "L", BUTTON_LEFTSHOULDER, 0.08f, 0.10f, 24, GREY, true));
        controls.add(new Control("r", Kind.TRIGGER, "R", AXIS_TRIGGERRIGHT, 0.92f, 0.10f, 24, GREY, true));
        controls.add(new Control("a", Kind.BUTTON, "A", BUTTON_SOUTH, 0.80f, 0.78f, 36, BLUE, false));
        controls.add(new Control("b", Kind.BUTTON, "B", BUTTON_WEST, 0.70f, 0.64f, 30, GREEN, false));
        float cx = 0.89f, cy = 0.44f, step = 0.075f;
        controls.add(new Control("c_left", Kind.C_BUTTON, "◀", 0, cx - step * 0.55f, cy, 21, YELLOW, false));
        controls.add(new Control("c_right", Kind.C_BUTTON, "▶", 1, cx + step * 0.55f, cy, 21, YELLOW, false));
        controls.add(new Control("c_up", Kind.C_BUTTON, "▲", 2, cx, cy - step * 1.2f, 21, YELLOW, false));
        controls.add(new Control("c_down", Kind.C_BUTTON, "▼", 3, cx, cy + step * 1.2f, 21, YELLOW, false));
        controls.add(new Control("start", Kind.BUTTON, "START", BUTTON_START, 0.44f, 0.90f, 22, RED, true));
        controls.add(new Control("menu", Kind.BUTTON, "☰", BUTTON_BACK, 0.56f, 0.90f, 18, GREY, true));
        loadLayout();

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2 * density);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        setHapticFeedbackEnabled(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(checkState);
        resumeSensors();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(checkState);
        pauseSensors();
        super.onDetachedFromWindow();
    }

    /** GameActivity, when the game shows and goes away. */
    void resumeSensors() {
        Sensor gyro = sensors != null ? sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE) : null;
        if (gyro != null) {
            lastGyroTime = 0;
            sensors.registerListener(gyroListener, gyro, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    void pauseSensors() {
        if (sensors != null) {
            sensors.unregisterListener(gyroListener);
        }
        rumble(0);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        place();
    }

    /** Pixel positions and sizes from the layout, and the edit button and toolbar. */
    private void place() {
        int w = getWidth(), h = getHeight();
        for (Control c : controls) {
            c.cx = c.fx * w;
            c.cy = c.fy * h;
            c.radius = c.radiusDp * density * c.scale;
        }
        float size = 34 * density;
        editButton.set(w / 2f - size / 2, 8 * density, w / 2f + size / 2, 8 * density + size);
        float toolWidth = 72 * density, toolHeight = 40 * density, gap = 10 * density;
        float left = w / 2f - (toolRects.length * toolWidth + (toolRects.length - 1) * gap) / 2;
        float top = h * 0.22f;
        for (int i = 0; i < toolRects.length; i++) {
            float x = left + i * (toolWidth + gap);
            toolRects[i].set(x, top, x + toolWidth, top + toolHeight);
        }
    }

    // Drawing.

    @Override
    protected void onDraw(Canvas canvas) {
        if (hidden) {
            return;
        }
        if (editing) {
            canvas.drawColor(0x66000000);
        }
        // The Opacity setting fades the controls (not while editing them).
        int layer = canvas.saveLayerAlpha(null, editing ? 255 : Math.round(255 * clamp(opacity, 0.05f, 1)));
        for (Control c : controls) {
            drawControl(canvas, c);
        }
        canvas.restoreToCount(layer);
        drawEditButton(canvas);
        if (editing) {
            drawToolbar(canvas);
        }
    }

    private void drawControl(Canvas canvas, Control c) {
        boolean lit = c.pressed || (editing && c == selected);
        fill.setColor((c.color & 0x00FFFFFF) | ((lit ? 0xE0 : 0x90) << 24));
        stroke.setColor(editing && c == selected ? ORANGE : (0x00FFFFFF | ((lit ? 0xE0 : 0x90) << 24)));
        if (c.kind == Kind.STICK) {
            canvas.drawCircle(c.cx, c.cy, c.radius, fill);
            canvas.drawCircle(c.cx, c.cy, c.radius, stroke);
            fill.setColor(0x90FFFFFF);
            canvas.drawCircle(c.cx + stickX * c.radius, c.cy + stickY * c.radius, c.radius * 0.45f, fill);
            return;
        }
        if (c.wide) {
            RectF rect = new RectF(c.cx - c.radius * 1.6f, c.cy - c.radius * 0.8f,
                c.cx + c.radius * 1.6f, c.cy + c.radius * 0.8f);
            canvas.drawRoundRect(rect, c.radius * 0.6f, c.radius * 0.6f, fill);
            canvas.drawRoundRect(rect, c.radius * 0.6f, c.radius * 0.6f, stroke);
        } else {
            canvas.drawCircle(c.cx, c.cy, c.radius, fill);
            canvas.drawCircle(c.cx, c.cy, c.radius, stroke);
        }
        text.setTextSize(c.label.length() > 1 && c.kind != Kind.C_BUTTON ? c.radius * 0.6f : c.radius * 0.9f);
        drawCentered(canvas, c.label, c.cx, c.cy);
    }

    private void drawEditButton(Canvas canvas) {
        fill.setColor(editing ? 0xC0F08A24 : 0x559A9A9A);
        stroke.setColor(0x90FFFFFF);
        canvas.drawRoundRect(editButton, 10 * density, 10 * density, fill);
        canvas.drawRoundRect(editButton, 10 * density, 10 * density, stroke);
        text.setTextSize(editButton.height() * 0.55f);
        drawCentered(canvas, "✎", editButton.centerX(), editButton.centerY());
    }

    private void drawToolbar(Canvas canvas) {
        text.setTextSize(15 * density);
        String hint = selected == null
            ? "Drag a control to move it. Tap one to resize it."
            : "Size: " + Math.round(selected.scale * 100) + "%";
        drawCentered(canvas, hint, getWidth() / 2f, toolRects[0].top - 16 * density);
        for (int i = 0; i < toolRects.length; i++) {
            boolean enabled = i >= 2 || selected != null;
            fill.setColor(i == 3 ? 0xD0F08A24 : (enabled ? 0xB0303030 : 0x60303030));
            stroke.setColor(0xA0FFFFFF);
            canvas.drawRoundRect(toolRects[i], 10 * density, 10 * density, fill);
            canvas.drawRoundRect(toolRects[i], 10 * density, 10 * density, stroke);
            text.setTextSize((i < 2 ? 22 : 15) * density);
            drawCentered(canvas, toolLabels[i], toolRects[i].centerX(), toolRects[i].centerY());
        }
    }

    private void drawCentered(Canvas canvas, String label, float x, float y) {
        canvas.drawText(label, x, y - (text.descent() + text.ascent()) / 2, text);
    }

    // Touch.

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (hidden && pointers.isEmpty()) {
            return false;
        }
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN && editButton.contains(event.getX(), event.getY())) {
            if (editing) {
                finishEditing();
            } else {
                startEditing();
            }
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            invalidate();
            return true;
        }
        if (editing) {
            onEditTouch(event, action, index);
            invalidate();
            return true;
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                Control c = find(event.getX(index), event.getY(index));
                if (c == null) {
                    // Nothing here: the first touch goes to the game's window instead.
                    return action != MotionEvent.ACTION_DOWN;
                }
                pointers.put(event.getPointerId(index), c);
                press(c, true);
                if (c == stick) {
                    moveStick(event.getX(index), event.getY(index));
                }
                if (haptics) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    if (pointers.get(event.getPointerId(i)) == stick) {
                        moveStick(event.getX(i), event.getY(i));
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                Control c = pointers.remove(event.getPointerId(index));
                if (c != null) {
                    release(c);
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                break;
        }
        invalidate();
        return true;
    }

    private void onEditTouch(MotionEvent event, int action, int index) {
        float x = event.getX(index), y = event.getY(index);
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                for (int i = 0; i < toolRects.length; i++) {
                    if (toolRects[i].contains(x, y)) {
                        onTool(i);
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        return;
                    }
                }
                Control c = find(x, y);
                selected = c;
                if (c != null) {
                    dragPointer = event.getPointerId(index);
                    dragOffsetX = c.cx - x;
                    dragOffsetY = c.cy - y;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (selected == null || dragPointer < 0) {
                    break;
                }
                int i = event.findPointerIndex(dragPointer);
                if (i < 0) {
                    break;
                }
                float w = getWidth(), h = getHeight();
                selected.fx = clamp((event.getX(i) + dragOffsetX) / w, 0.02f, 0.98f);
                selected.fy = clamp((event.getY(i) + dragOffsetY) / h, 0.02f, 0.98f);
                place();
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragPointer = -1;
                break;
        }
    }

    private void onTool(int tool) {
        switch (tool) {
            case 0:
            case 1:
                if (selected != null) {
                    float delta = tool == 0 ? -SCALE_STEP : SCALE_STEP;
                    selected.scale = clamp(Math.round((selected.scale + delta) * 10) / 10f, MIN_SCALE, MAX_SCALE);
                    place();
                }
                break;
            case 2:
                for (Control c : controls) {
                    c.fx = c.defaultX;
                    c.fy = c.defaultY;
                    c.scale = 1;
                }
                place();
                break;
            case 3:
                finishEditing();
                break;
        }
    }

    private void startEditing() {
        releaseAll();
        editing = true;
        selected = null;
    }

    private void finishEditing() {
        editing = false;
        selected = null;
        dragPointer = -1;
        saveLayout();
    }

    private Control find(float x, float y) {
        // The stick takes touches a little beyond its ring, the buttons a little beyond theirs.
        for (Control c : controls) {
            if (c != stick && c.hit(x, y, 1.25f)) {
                return c;
            }
        }
        return stick.hit(x, y, 1.5f) ? stick : null;
    }

    // The virtual controller.

    private void releaseAll() {
        for (Control c : pointers.values()) {
            release(c);
        }
        pointers.clear();
    }

    private void release(Control c) {
        press(c, false);
        if (c == stick) {
            stickX = stickY = 0;
            nativeSetAxis(AXIS_LEFTX, 0);
            nativeSetAxis(AXIS_LEFTY, 0);
        }
    }

    private void press(Control c, boolean pressed) {
        c.pressed = pressed;
        switch (c.kind) {
            case BUTTON:
                nativeSetButton(c.id, pressed);
                break;
            case TRIGGER:
                nativeSetAxis(c.id, pressed ? AXIS_MAX : 0);
                break;
            case C_BUTTON:
                updateCButtons();
                break;
            case STICK:
                break;
        }
    }

    /** The C buttons are the right stick's directions. */
    private void updateCButtons() {
        int x = 0, y = 0;
        for (Control c : controls) {
            if (c.kind == Kind.C_BUTTON && c.pressed) {
                switch (c.id) {
                    case 0: x -= AXIS_MAX; break;
                    case 1: x += AXIS_MAX; break;
                    case 2: y -= AXIS_MAX; break;
                    case 3: y += AXIS_MAX; break;
                }
            }
        }
        nativeSetAxis(AXIS_RIGHTX, (int) clamp(x, -AXIS_MAX, AXIS_MAX));
        nativeSetAxis(AXIS_RIGHTY, (int) clamp(y, -AXIS_MAX, AXIS_MAX));
    }

    private void moveStick(float x, float y) {
        float dx = (x - stick.cx) / stick.radius, dy = (y - stick.cy) / stick.radius;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length > 1) {
            dx /= length;
            dy /= length;
        }
        stickX = dx;
        stickY = dy;
        nativeSetAxis(AXIS_LEFTX, Math.round(dx * AXIS_MAX));
        nativeSetAxis(AXIS_LEFTY, Math.round(dy * AXIS_MAX));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    // touch_layout.json: { "a": { "x": 0.8, "y": 0.78, "scale": 1.0 }, ... }

    private static File layoutFile() {
        return new File(MainActivity.gameFolder(), "touch_layout.json");
    }

    private void loadLayout() {
        File file = layoutFile();
        if (!file.isFile()) {
            return;
        }
        try (InputStream in = new FileInputStream(file)) {
            JSONObject json = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            for (Control c : controls) {
                JSONObject entry = json.optJSONObject(c.key);
                if (entry != null) {
                    c.fx = clamp((float) entry.optDouble("x", c.defaultX), 0.02f, 0.98f);
                    c.fy = clamp((float) entry.optDouble("y", c.defaultY), 0.02f, 0.98f);
                    c.scale = clamp((float) entry.optDouble("scale", 1), MIN_SCALE, MAX_SCALE);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Couldn't read " + file, e);
        }
    }

    private void saveLayout() {
        File file = layoutFile();
        try {
            JSONObject json = new JSONObject();
            for (Control c : controls) {
                JSONObject entry = new JSONObject();
                entry.put("x", c.fx);
                entry.put("y", c.fy);
                entry.put("scale", c.scale);
                json.put(c.key, entry);
            }
            try (OutputStream out = new FileOutputStream(file)) {
                out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w(TAG, "Couldn't write " + file, e);
        }
    }
}

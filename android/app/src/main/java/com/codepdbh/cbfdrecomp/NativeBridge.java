package com.codepdbh.cbfdrecomp;

/** The game library (host/, built headless for now). */
final class NativeBridge {
    static {
        System.loadLibrary("ConkerRecomp");
    }

    private NativeBridge() {}

    /** Runs the game with main.cpp's command line; returns when it quits. */
    static native int run(String[] args);
}

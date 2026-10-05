package com.hoshinoriji.miuihomelandscape.core;

/** Compile-time switches for device debugging. */
public final class Diagnostics {
    /**
     * Per-touch hit tests and the 24-line slot dump on every bind. These go to the persistent
     * LSPosed module log, so they stay off in normal builds; flip to true for input debugging.
     */
    public static final boolean VERBOSE_INPUT_LOGS = false;

    private Diagnostics() {
    }
}

package com.hoshinoriji.miuihomelandscape.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import com.hoshinoriji.miuihomelandscape.LandscapeBridge;

import java.util.Locale;

/** Compatibility gate for the Xiaomi 11T Pro firmware family used by this build. */
public final class DeviceProfile {
    public static final String EXPECTED_DEVICE = "vili";
    public static final String EXPECTED_MODEL = "2107113SG";
    public static final String EXPECTED_FINGERPRINT =
            "Xiaomi/vili/vili:13/RKQ1.211001.001/V14.0.5.0.TKDMIXM:user/release-keys";
    public static final int EXPECTED_SDK = 33;
    public static final long EXPECTED_MIUI_HOME_VERSION = 439126764L;

    private final String device;
    private final String product;
    private final String model;
    private final String fingerprint;
    private final int sdk;
    private final long miuiHomeVersion;
    private final boolean supported;

    private DeviceProfile(
            String device,
            String product,
            String model,
            String fingerprint,
            int sdk,
            long miuiHomeVersion,
            boolean supported) {
        this.device = device;
        this.product = product;
        this.model = model;
        this.fingerprint = fingerprint;
        this.sdk = sdk;
        this.miuiHomeVersion = miuiHomeVersion;
        this.supported = supported;
    }

    public static DeviceProfile inspect(Context context) {
        String device = safe(Build.DEVICE);
        String product = safe(Build.PRODUCT);
        String model = safe(Build.MODEL);
        String fingerprint = safe(Build.FINGERPRINT);
        long homeVersion = -1L;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    LandscapeBridge.HOME_PKG, 0);
            homeVersion = info.getLongVersionCode();
        } catch (Throwable ignored) {
            // A missing or hidden target package deliberately fails the compatibility gate.
        }

        boolean codenameMatches = EXPECTED_DEVICE.equalsIgnoreCase(device)
                || EXPECTED_DEVICE.equalsIgnoreCase(product);
        // xiaomi.eu may rewrite Build.MODEL and Build.FINGERPRINT while keeping the same vili
        // framework and MIUI Home binary. Treat those two values as diagnostics, not hard gates.
        boolean supported = codenameMatches
                && Build.VERSION.SDK_INT == EXPECTED_SDK
                && homeVersion == EXPECTED_MIUI_HOME_VERSION;
        return new DeviceProfile(
                device, product, model, fingerprint,
                Build.VERSION.SDK_INT, homeVersion, supported);
    }

    public boolean isSupported() {
        return supported;
    }

    public boolean isEnabled(ModuleSettings settings) {
        return supported || (settings != null && settings.allowUnsupported());
    }

    public String device() {
        return device;
    }

    public String product() {
        return product;
    }

    public String model() {
        return model;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public int sdk() {
        return sdk;
    }

    public long miuiHomeVersion() {
        return miuiHomeVersion;
    }

    public String summary() {
        return String.format(Locale.ROOT,
                "device=%s product=%s model=%s sdk=%d miuiHome=%d fingerprint=%s supported=%s",
                device, product, model, sdk, miuiHomeVersion, fingerprint, supported);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

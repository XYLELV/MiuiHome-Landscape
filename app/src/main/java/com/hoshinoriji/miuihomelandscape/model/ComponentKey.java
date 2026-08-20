package com.hoshinoriji.miuihomelandscape.model;

import android.content.ComponentName;

/** 不可变的 (包名, 类名, userSerial) 三元组。Store / Picker / View 统一用这个。 */
public final class ComponentKey {
    public final String packageName;
    public final String className;
    public final long userSerial;

    public ComponentKey(String pkg, String cls, long serial) {
        this.packageName = pkg;
        this.className = cls;
        this.userSerial = serial;
    }

    public ComponentKey(ComponentName cn, long serial) {
        this(cn.getPackageName(), cn.getClassName(), serial);
    }

    public ComponentName toComponentName() {
        return new ComponentName(packageName, className);
    }

    /** Compact, intent-safe representation. Android component names cannot contain newlines. */
    public String encode() {
        return toComponentName().flattenToString() + "\n" + userSerial;
    }

    public static ComponentKey decode(String encoded) {
        if (encoded == null || encoded.length() > 1024) return null;
        int split = encoded.lastIndexOf('\n');
        if (split <= 0 || split >= encoded.length() - 1) return null;
        ComponentName component = ComponentName.unflattenFromString(encoded.substring(0, split));
        if (component == null) return null;
        try {
            long serial = Long.parseLong(encoded.substring(split + 1));
            if (serial < 0L) return null;
            return new ComponentKey(component, serial);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof ComponentKey)) return false;
        ComponentKey k = (ComponentKey) o;
        return userSerial == k.userSerial
                && packageName.equals(k.packageName)
                && className.equals(k.className);
    }

    @Override public int hashCode() {
        int h = packageName.hashCode();
        h = 31 * h + className.hashCode();
        h = 31 * h + (int) (userSerial ^ (userSerial >>> 32));
        return h;
    }

    @Override public String toString() {
        return packageName + "/" + className + "#" + userSerial;
    }
}

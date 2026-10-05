package com.hoshinoriji.miuihomelandscape.model;

import android.content.ComponentName;

public final class ComponentKey {
    public final String className;
    public final String packageName;
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
        return new ComponentName(this.packageName, this.className);
    }

    public boolean equals(Object o) {
        if (!(o instanceof ComponentKey)) {
            return false;
        }
        ComponentKey k = (ComponentKey) o;
        return this.userSerial == k.userSerial && this.packageName.equals(k.packageName) && this.className.equals(k.className);
    }

    public int hashCode() {
        int h = this.packageName.hashCode();
        return (((h * 31) + this.className.hashCode()) * 31) + ((int) (this.userSerial ^ (this.userSerial >>> 32)));
    }

    public String toString() {
        return this.packageName + "/" + this.className + "#" + this.userSerial;
    }
}

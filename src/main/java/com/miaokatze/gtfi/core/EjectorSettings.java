package com.miaokatze.gtfi.core;

import net.minecraft.nbt.NBTTagCompound;

/** The same bounds are applied on loading, editing and every server-side offer. */
public final class EjectorSettings {

    public boolean enabled = true;
    public int threshold = 26;
    public double range = 50;
    public double power = 1;
    public double damageFactor = 1;
    public int countdownSeconds = 31;
    public boolean portable;

    public static EjectorSettings blockDefaults() {
        return new EjectorSettings();
    }

    public static EjectorSettings portableDefaults() {
        EjectorSettings result = new EjectorSettings();
        result.portable = true;
        result.range = 10;
        result.countdownSeconds = 5;
        return result;
    }

    public static EjectorSettings read(NBTTagCompound tag, boolean portable, boolean safeMode) {
        EjectorSettings result = portable ? portableDefaults() : blockDefaults();
        if (tag.hasKey("enabled")) result.enabled = tag.getBoolean("enabled");
        if (tag.hasKey("threshold")) result.threshold = tag.getInteger("threshold");
        if (tag.hasKey("range")) result.range = tag.getDouble("range");
        if (tag.hasKey("power")) result.power = tag.getDouble("power");
        if (tag.hasKey("damageFactor")) result.damageFactor = tag.getDouble("damageFactor");
        if (tag.hasKey("countdownSeconds")) result.countdownSeconds = tag.getInteger("countdownSeconds");
        return result.sanitize(portable, safeMode);
    }

    public EjectorSettings sanitize(boolean portable, boolean safeMode) {
        this.portable = portable;
        threshold = Math.max(26, threshold);
        range = finiteClamp(range, portable ? 5 : 10, portable ? 50 : 200);
        power = finiteClamp(power, portable ? 0.1 : 1, portable ? 1 : 1000);
        damageFactor = finiteClamp(damageFactor, 0, 1);
        countdownSeconds = Math.max(portable || safeMode ? 5 : 31, Math.min(86400, countdownSeconds));
        return this;
    }

    public void write(NBTTagCompound tag) {
        tag.setBoolean("enabled", enabled);
        tag.setInteger("threshold", threshold);
        tag.setDouble("range", range);
        tag.setDouble("power", power);
        tag.setDouble("damageFactor", damageFactor);
        tag.setInteger("countdownSeconds", countdownSeconds);
    }

    public EjectorSettings copy() {
        EjectorSettings copy = new EjectorSettings();
        copy.enabled = enabled;
        copy.threshold = threshold;
        copy.range = range;
        copy.power = power;
        copy.damageFactor = damageFactor;
        copy.countdownSeconds = countdownSeconds;
        copy.portable = portable;
        return copy;
    }

    static double finiteClamp(double value, double min, double max) {
        return Double.isNaN(value) || Double.isInfinite(value) ? min : Math.max(min, Math.min(max, value));
    }
}

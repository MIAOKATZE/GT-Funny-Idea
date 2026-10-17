package com.miaokatze.gtfi.core;

/** Per-level prices, not cumulative lifetime thresholds. */
public final class TransferProgression {

    private static final int[] PRICES = { 50, 100, 200, 300, 500, 1000, 2000, 3000, 5000 };

    private TransferProgression() {}

    public static int required(int level) {
        return level >= 10 ? 0 : PRICES[Math.max(1, level) - 1];
    }

    public static boolean canSelect(int level, WarpType type) {
        return type == WarpType.TEMP || type == WarpType.NORMAL && level >= 3 || type == WarpType.PERM && level >= 6;
    }

    /** Returns {level, experience remaining in that level}. */
    public static int[] add(int level, int experience, int amount) {
        level = Math.max(1, Math.min(10, level));
        long total = Math.max(0L, experience) + Math.max(0L, amount);
        while (level < 10 && total >= required(level)) total -= required(level++);
        return new int[] { level, level == 10 ? 0 : (int) total };
    }
}

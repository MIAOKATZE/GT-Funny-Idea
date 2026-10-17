package com.miaokatze.gtfi.core;

/** Run with java; these tests intentionally require no Minecraft bootstrap or mocking. */
public final class CoreRulesRegression {

    private CoreRulesRegression() {}

    public static void main(String[] args) {
        close(50000, FlightMath.launchSpeed(1000, 0, 200), "maximum launch speed");
        close(5, FlightMath.launchSpeed(0.1, 0, 10), "minimum portable power");
        close(25, FlightMath.launchSpeed(1, 5, 10), "linear distance attenuation");
        close(0, FlightMath.launchSpeed(1000, 200, 200), "range edge");
        close(50, FlightMath.speedMetersPerSecond(1.5, 2, 0), "blocks/tick to metres/second");
        // A 2,500-block tick must still hit a one-block wall rather than test only its endpoint.
        close(0.004, FlightMath.sweep(0, 1, 0, 2500, 0, 0, 10, 0, -1, 11, 2, 1), "high speed wall");
        close(0.4, FlightMath.sweep(0, 0, 0, 10, 10, 0, 4, 4, -1, 5, 5, 1), "diagonal corner");
        close(0, FlightMath.sweep(4, 4.5, 0, 1, 0, 0, 4, 4, -1, 5, 5, 1), "inward touching wall");
        infinite(FlightMath.sweep(4, 4.5, 0, -1, 0, 0, 4, 4, -1, 5, 5, 1), "outward touching wall");
        infinite(FlightMath.sweep(0, 2, 0, 20, 0, 0, 4, 0, -1, 5, 2, 1), "parallel touching floor");
        infinite(FlightMath.sweep(0, 3, 0, 2500, 0, 0, 10, 0, -1, 11, 2, 1), "wall outside trajectory");
        int[] before = TransferProgression.add(1, 49, 0);
        equal(1, before[0], "before level boundary");
        equal(49, before[1], "retained experience");
        int[] after = TransferProgression.add(1, 49, 1);
        equal(2, after[0], "exact level boundary");
        equal(0, after[1], "spent experience");
        int[] overflow = TransferProgression.add(1, 0, 160);
        equal(3, overflow[0], "multiple level gain");
        equal(10, overflow[1], "carry experience");
        equal(5000, TransferProgression.required(9), "confirmed last level price");
        int[] finalBefore = TransferProgression.add(9, 4999, 0);
        equal(9, finalBefore[0], "level ten needs all 5000");
        int[] finalAfter = TransferProgression.add(9, 4999, 1);
        equal(10, finalAfter[0], "level ten boundary");
        equal(0, finalAfter[1], "max level experience");
        int[] saturated = TransferProgression.add(1, Integer.MAX_VALUE, Integer.MAX_VALUE);
        equal(10, saturated[0], "large XP cannot overflow");
        check(!TransferProgression.canSelect(2, WarpType.NORMAL), "normal locked at two");
        check(TransferProgression.canSelect(3, WarpType.NORMAL), "normal unlocked at three");
        check(!TransferProgression.canSelect(5, WarpType.PERM), "permanent locked at five");
        check(TransferProgression.canSelect(6, WarpType.PERM), "permanent unlocked at six");
        check(WarpType.TEMP.targetType() == WarpType.NORMAL, "temporary becomes normal");
        check(WarpType.NORMAL.targetType() == WarpType.PERM, "normal becomes permanent");
        check(WarpType.PERM.targetType() == WarpType.PERM, "permanent stays permanent");
        System.out.println("CoreRulesRegression: all checks passed");
    }

    private static void close(double expected, double actual, String label) {
        check(Math.abs(expected - actual) < 1.0E-8, label + ": " + actual);
    }

    private static void equal(int expected, int actual, String label) {
        check(expected == actual, label + ": " + actual);
    }

    private static void infinite(double actual, String label) {
        check(Double.isInfinite(actual), label + ": " + actual);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}

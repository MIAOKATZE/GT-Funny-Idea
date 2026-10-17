package com.miaokatze.gtfi.core;

/** Unit-free continuous collision maths, also usable without a running Minecraft world. */
public final class FlightMath {

    private FlightMath() {}

    public static double speedMetersPerSecond(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z) * 20;
    }

    public static double launchSpeed(double power, double distance, double range) {
        return Math.min(100000, 100 * power) * Math.max(0.5, 1 - Math.max(0, distance) / range);
    }

    /** Entry fraction for a moving point against the obstacle expanded by the moving box. */
    public static double sweep(double x, double y, double z, double dx, double dy, double dz, double minX, double minY,
        double minZ, double maxX, double maxY, double maxZ) {
        double entry = 0;
        double exit = 1;
        double[] position = { x, y, z };
        double[] delta = { dx, dy, dz };
        double[] min = { minX, minY, minZ };
        double[] max = { maxX, maxY, maxZ };
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(delta[axis]) < 1.0E-12) {
                if (position[axis] <= min[axis] || position[axis] >= max[axis]) return Double.POSITIVE_INFINITY;
            } else {
                double near = (min[axis] - position[axis]) / delta[axis];
                double far = (max[axis] - position[axis]) / delta[axis];
                if (near > far) {
                    double swap = near;
                    near = far;
                    far = swap;
                }
                entry = Math.max(entry, near);
                exit = Math.min(exit, far);
                if (entry > exit) return Double.POSITIVE_INFINITY;
            }
        }
        // A touching surface behind the direction of travel must not stop an escaping launch.
        return exit <= 1.0E-9 || entry > 1 ? Double.POSITIVE_INFINITY : entry;
    }
}

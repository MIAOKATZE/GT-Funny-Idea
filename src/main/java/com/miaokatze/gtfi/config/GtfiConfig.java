package com.miaokatze.gtfi.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** Server-authoritative settings; changes take effect after a restart. */
public final class GtfiConfig {

    public static boolean safeMode = false;
    public static boolean testDimensionEnabled = true;
    public static int testDimensionId = 22970;

    private GtfiConfig() {}

    public static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();
        safeMode = config.getBoolean(
            "safeMode",
            Configuration.CATEGORY_GENERAL,
            false,
            "Restrict ejector edits to owners/operators, allow a 5-second block countdown, "
                + "and disable the warp transferer. Requires restart.");
        testDimensionEnabled = config.getBoolean(
            "enabled",
            "testDimension",
            true,
            "Register the isolated test dimension and its operator commands. Requires restart.");
        testDimensionId = config.getInt(
            "dimensionId",
            "testDimension",
            22970,
            2,
            Integer.MAX_VALUE,
            "Dimension/provider ID. Choose an unused positive ID before loading a world. Requires restart.");
        if (config.hasChanged()) {
            config.save();
        }
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtfi.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** Server-authoritative settings; changes take effect after a restart. */
public final class GtfiConfig {

    public static boolean safeMode = false;
    public static boolean testDimensionEnabled = false;
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
            false,
            "Opt in to the isolated test dimension. Requires the complete testlab replacement JAR on both sides and a restart.");
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

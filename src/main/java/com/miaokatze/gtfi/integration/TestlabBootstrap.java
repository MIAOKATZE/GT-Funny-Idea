package com.miaokatze.gtfi.integration;

import net.minecraft.entity.player.EntityPlayer;

import com.miaokatze.gtfi.GTFunnyIdea;
import com.miaokatze.gtfi.config.GtfiConfig;

import cpw.mods.fml.common.event.FMLServerStartingEvent;

/** Optional lifecycle boundary. Ordinary jars contain no fixture implementation. */
public final class TestlabBootstrap {

    public interface Driver {

        void preInit();

        void serverStarting(FMLServerStartingEvent event);

        void serverStopped();

        void serverStopping();

        void installClient();

        boolean ownsActor(EntityPlayer player);
    }

    private static Driver driver;

    private TestlabBootstrap() {}

    public static void preInit() {
        if (!GtfiConfig.testDimensionEnabled) return;
        try {
            driver = (Driver) Class.forName("com.miaokatze.gtfi.testlab.TestlabDriver")
                .getDeclaredConstructor()
                .newInstance();
        } catch (ClassNotFoundException absent) {
            GTFunnyIdea.LOG.info("Test dimension disabled: install the complete testlab replacement JAR to opt in.");
            return;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot initialize GTFI testlab driver", failure);
        }
        driver.preInit();
    }

    public static void serverStarting(FMLServerStartingEvent event) {
        if (driver != null) driver.serverStarting(event);
    }

    public static void serverStopped() {
        if (driver != null) driver.serverStopped();
    }

    public static void serverStopping() {
        if (driver != null) driver.serverStopping();
    }

    public static void installClient() {
        if (driver != null && Boolean.getBoolean("gtfi.clientSmoke")) driver.installClient();
    }

    public static boolean isSyntheticActor(EntityPlayer player) {
        return driver != null && driver.ownsActor(player);
    }
}

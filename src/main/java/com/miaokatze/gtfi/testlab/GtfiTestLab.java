package com.miaokatze.gtfi.testlab;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;

import com.miaokatze.gtfi.GTFunnyIdea;
import com.miaokatze.gtfi.config.GtfiConfig;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

/** Optional fixture lifecycle. Registration never changes a conflicting or persisted dimension ID. */
public final class GtfiTestLab {

    private static int dimension;
    private static boolean providerReady;
    private static String problem = "not initialized";
    private static TestRuntime runtime;

    private GtfiTestLab() {}

    public static void preInit() {
        if (!GtfiConfig.testDimensionEnabled) {
            problem = "disabled by testDimension.enabled";
            return;
        }
        dimension = GtfiConfig.testDimensionId;
        if (DimensionManager.isDimensionRegistered(dimension)
            || !DimensionManager.registerProviderType(dimension, TestWorldProvider.class, false)) {
            problem = "dimension/provider ID conflict: " + dimension + "; choose an unused ID before first use";
            return;
        }
        providerReady = true;
        ForgeChunkManager.setForcedChunkLoadingCallback(GTFunnyIdea.INSTANCE, new ForgeChunkManager.LoadingCallback() {

            @Override
            public void ticketsLoaded(java.util.List<ForgeChunkManager.Ticket> tickets,
                net.minecraft.world.World world) {
                // A server restart interrupts the old run. Its four fixture leases must not remain permanent.
                for (ForgeChunkManager.Ticket ticket : tickets) if (ticket.getModData()
                    .getBoolean("GTFITestRun")) ForgeChunkManager.releaseTicket(ticket);
            }
        });
        // Clients need the same provider mapping to enter an enabled dedicated server lab.
        if (FMLCommonHandler.instance()
            .getSide()
            .isClient()) DimensionManager.registerDimension(dimension, dimension);
        problem = "awaiting save identity validation";
    }

    public static void serverStarting(FMLServerStartingEvent event) {
        if (providerReady) {
            try {
                File save = MinecraftServer.getServer()
                    .worldServerForDimension(0)
                    .getSaveHandler()
                    .getWorldDirectory();
                java.nio.file.Path identity = new File(save, "gtfi-testlab-dimension.txt").toPath();
                String expected = "GTFI-TEST\n" + dimension + "\n";
                if (Files.exists(identity)) {
                    if (!expected.equals(new String(Files.readAllBytes(identity), StandardCharsets.UTF_8)))
                        throw new IOException(
                            "Persisted test dimension ID differs; restore configured ID. Do not remap saves.");
                } else Files.write(identity, expected.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
                if (!DimensionManager.isDimensionRegistered(dimension))
                    DimensionManager.registerDimension(dimension, dimension);
                else if (DimensionManager.getProviderType(dimension) != dimension)
                    throw new IOException("Dimension became occupied before save validation");
                runtime = new TestRuntime(save, dimension);
                FMLCommonHandler.instance()
                    .bus()
                    .register(runtime);
                problem = null;
            } catch (Exception failure) {
                problem = failure.toString();
            }
        }
        // Retain an explanatory disabled command without enabling any fixture behavior.
        event.registerServerCommand(new TestCommand());
    }

    public static void serverStopped() {
        if (runtime != null) {
            runtime.close();
            FMLCommonHandler.instance()
                .bus()
                .unregister(runtime);
            runtime = null;
        }
        // A physical client keeps its mapping when an integrated server stops, so it can then
        // join an enabled dedicated server without restarting the client process.
        if (!FMLCommonHandler.instance()
            .getSide()
            .isClient() && providerReady
            && DimensionManager.isDimensionRegistered(dimension)
            && DimensionManager.getProviderType(dimension) == dimension)
            DimensionManager.unregisterDimension(dimension);
        problem = "server stopped";
    }

    public static boolean isSyntheticActor(EntityPlayer player) {
        return runtime != null && problem == null && runtime.ownsActor(player);
    }

    static TestRuntime runtime() {
        if (runtime == null || problem != null) throw new IllegalStateException("GTFI testlab " + problem);
        return runtime;
    }

    static WorldServer world() {
        runtime();
        if (DimensionManager.getWorld(dimension) == null) DimensionManager.initDimension(dimension);
        WorldServer world = DimensionManager.getWorld(dimension);
        if (world == null || !(world.provider instanceof TestWorldProvider))
            throw new IllegalStateException("Test world provider does not match registered owner");
        return world;
    }
}

package com.miaokatze.gtfi.testlab;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraftforge.common.ForgeChunkManager;

import com.google.gson.GsonBuilder;
import com.miaokatze.gtfi.GTFunnyIdea;
import com.miaokatze.gtfi.core.ThaumcraftWarp;
import com.miaokatze.gtfi.core.TransferService;
import com.miaokatze.gtfi.core.WarpService;
import com.miaokatze.gtfi.core.WarpType;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Natural server-tick runner. Reset touches only x/z [-16,15], y [64,95] of GTFI-TEST. */
public final class TestRuntime {

    private final File reportDirectory;
    private final int dimension;
    private final Map<UUID, ReturnPosition> returns = new HashMap<UUID, ReturnPosition>();
    private String phase = "IDLE";
    private int resetCursor;
    private boolean built;
    private List<TestScenarios.Scenario> queue;
    private TestScenarios.Scenario current;
    private int index, elapsed;
    private long naturalTicks;
    private TestActor[] actors;
    private Map<String, Object> run;
    private List<Map<String, Object>> results;
    private String reportPath = "none", writeStatus = "NONE";
    private UUID runId;
    private TestScenarios.Context context;
    private ForgeChunkManager.Ticket ticket;
    private final List<Map<String, Object>> fixtureDiagnostics = new ArrayList<Map<String, Object>>();

    TestRuntime(File save, int dimension) {
        this.reportDirectory = new File(save, "gtfi-test-reports");
        this.dimension = dimension;
    }

    boolean ownsActor(EntityPlayer player) {
        if (actors == null || player == null
            || player.dimension != dimension
            || !(player.worldObj.provider instanceof TestWorldProvider)) return false;
        for (TestActor actor : actors) if (actor == player) return true;
        return false;
    }

    private void idleRequired() {
        if (!"IDLE".equals(phase)) throw new IllegalStateException("Busy: " + status());
    }

    private void noVisitors() {
        WorldServer world = GtfiTestLab.world();
        for (Object object : world.playerEntities) {
            EntityPlayer player = (EntityPlayer) object;
            if (!ownsActor(player))
                throw new IllegalStateException("Test dimension has a visitor; leave before rebuilding/running");
        }
    }

    String build() {
        idleRequired();
        noVisitors();
        queue = null;
        beginReset();
        return status();
    }

    String run(String selector) {
        idleRequired();
        noVisitors();
        queue = TestScenarios.select(selector);
        if (queue.isEmpty()) throw new IllegalArgumentException("Unknown scenario " + selector);
        runId = UUID.randomUUID();
        run = new LinkedHashMap<String, Object>();
        results = new ArrayList<Map<String, Object>>();
        run.put("runId", runId.toString());
        run.put("scope", "Actual dedicated/integrated Forge server; natural ticks; synthetic FakePlayers");
        run.put("dimensionId", dimension);
        run.put("selector", selector);
        run.put("startedUtc", new java.util.Date().toString());
        run.put("results", results);
        fixtureDiagnostics.clear();
        run.put("fixtureDiagnostics", fixtureDiagnostics);
        naturalTicks = 0;
        run.put("status", "RUNNING");
        reportPath = new File(reportDirectory, runId + ".json").getAbsolutePath();
        writeStatus = "PENDING";
        index = 0;
        beginReset();
        return status();
    }

    private void beginReset() {
        cleanupActors();
        current = null;
        context = null;
        resetCursor = 0;
        built = false;
        phase = "BUILDING";
        WorldServer world = GtfiTestLab.world();
        if (ticket == null) {
            ticket = ForgeChunkManager.requestTicket(GTFunnyIdea.INSTANCE, world, ForgeChunkManager.Type.NORMAL);
            if (ticket == null) throw new IllegalStateException("Forge refused the test fixture chunk ticket");
            ticket.getModData()
                .setBoolean("GTFITestRun", true);
            if (ticket.getChunkListDepth() > 0 && ticket.getChunkListDepth() < 4)
                throw new IllegalStateException("Test fixture requires a four chunk ticket depth");
        }
        for (int x = -1; x <= 0; x++) for (int z = -1; z <= 0; z++) {
            ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(x, z));
            // Some pack optimizations disable implicit provideChunk loading in an empty dimension.
            Chunk chunk = world.theChunkProviderServer.loadChunk(x, z);
            if (chunk instanceof EmptyChunk || !world.theChunkProviderServer.chunkExists(x, z))
                throw new IllegalStateException("Fixture failed to load owned chunk " + x + "," + z);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || "IDLE".equals(phase)) return;
        naturalTicks++;
        try {
            if ("VISUAL".equals(phase)) {
                elapsed++;
                context.using(false);
                WarpService.INSTANCE.offerSource(TestScenarios.SOURCE_IDS[0], context.world, 8, 65, 2, context.first);
                if (elapsed >= 160) {
                    cleanupActors();
                    context = null;
                    phase = "IDLE";
                }
            } else if ("BUILDING".equals(phase)) {
                WorldServer world = GtfiTestLab.world();
                int end = Math.min(32768, resetCursor + 4096);
                for (; resetCursor < end; resetCursor++) {
                    int x = (resetCursor & 31) - 16;
                    int z = ((resetCursor >> 5) & 31) - 16;
                    int y = 64 + (resetCursor >> 10);
                    if (y == 64) world.setBlock(x, y, z, Blocks.stone, 0, 2);
                    else world.setBlockToAir(x, y, z);
                }
                if (resetCursor == 32768) {
                    built = true;
                    boolean placed = world
                        .setBlock(-4, 65, 0, com.miaokatze.gtfi.register.ModContent.WARP_EJECTOR, 0, 3);
                    com.miaokatze.gtfi.block.TileWarpEjector display = (com.miaokatze.gtfi.block.TileWarpEjector) world
                        .getTileEntity(-4, 65, 0);
                    Map<String, Object> diagnostic = new LinkedHashMap<String, Object>();
                    diagnostic.put("resetIndex", index);
                    diagnostic.put("setBlockChanged", placed);
                    diagnostic.put(
                        "blockRegistryName",
                        net.minecraft.block.Block.blockRegistry.getNameForObject(world.getBlock(-4, 65, 0)));
                    diagnostic.put(
                        "hasTileEntity",
                        world.getBlock(-4, 65, 0)
                            .hasTileEntity(world.getBlockMetadata(-4, 65, 0)));
                    diagnostic.put(
                        "actualTile",
                        display == null ? null
                            : display.getClass()
                                .getName());
                    diagnostic.put("chunkExists", world.theChunkProviderServer.chunkExists(-1, 0));
                    diagnostic.put("implicitLoadEnabled", world.theChunkProviderServer.loadChunkOnProvideRequest);
                    diagnostic.put(
                        "forcedChunks",
                        ticket == null ? 0
                            : ticket.getChunkList()
                                .size());
                    diagnostic.put("remote", world.isRemote);
                    fixtureDiagnostics.add(diagnostic);
                    if (display == null)
                        throw new IllegalStateException("Registered display tile missing: " + diagnostic);
                    com.miaokatze.gtfi.core.EjectorSettings settings = display.getSettings();
                    settings.enabled = false;
                    display.setSettings(settings);
                    if (queue == null) {
                        releaseTicket();
                        phase = "IDLE";
                    } else startScenario();
                }
            } else if ("RUNNING".equals(phase)) {
                if (++elapsed > current.timeout()) throw new AssertionError("Natural server tick timeout: " + elapsed);
                if (current.tick(context, elapsed)) finishScenario(null);
            }
        } catch (Throwable failure) {
            if (current != null) finishScenario(failure);
            else {
                releaseTicket();
                phase = "IDLE";
                if (run != null) {
                    run.put("status", "ERROR");
                    run.put("error", failure.toString());
                    writeReport();
                }
            }
        }
    }

    private void startScenario() throws Exception {
        if (index >= queue.size()) {
            complete();
            return;
        }
        WorldServer world = GtfiTestLab.world();
        actors = new TestActor[] { new TestActor(world, "owner"), new TestActor(world, "target"),
            new TestActor(world, "observer") };
        current = queue.get(index);
        elapsed = 0;
        context = new TestScenarios.Context(world, actors);
        phase = "RUNNING";
        current.start(context);
    }

    private void finishScenario(Throwable failure) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", current.id());
        result.put("status", failure == null ? "PASS" : "FAIL");
        result.put("naturalTicks", elapsed);
        result.put("assertions", context == null ? null : context.assertions);
        if (failure != null) {
            result.put("error", failure.toString());
            List<String> stack = new ArrayList<String>();
            for (StackTraceElement frame : failure.getStackTrace()) {
                if (stack.size() == 12) break;
                stack.add(frame.toString());
            }
            result.put("stack", stack);
        }
        results.add(result);
        System.out.println(
            "[GTFI-TEST] " + current.id() + " " + result.get("status") + (failure == null ? "" : " " + failure));
        index++;
        beginReset();
    }

    private void complete() {
        boolean passed = true;
        for (Map<String, Object> result : results) if (!"PASS".equals(result.get("status"))) passed = false;
        run.put("status", passed ? "PASS" : "FAIL");
        run.put("naturalTicks", naturalTicks);
        run.put("completedUtc", new java.util.Date().toString());
        run.put(
            "impactObservation",
            "FakePlayer refuses damage: reports actual production damage requests, not client health/death");
        writeReport();
        releaseTicket();
        queue = null;
        phase = "IDLE";
        System.out.println("[GTFI-TEST] COMPLETE " + run.get("status") + " " + reportPath);
    }

    private void writeReport() {
        try {
            Files.createDirectories(reportDirectory.toPath());
            Files.write(
                new File(reportPath).toPath(),
                new GsonBuilder().setPrettyPrinting()
                    .create()
                    .toJson(run)
                    .getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE_NEW);
            writeStatus = "WRITTEN";
        } catch (Exception failure) {
            writeStatus = "FAILED: " + failure;
        }
    }

    private void cleanupActors() {
        for (String id : TestScenarios.SOURCE_IDS) WarpService.INSTANCE.removeSource(id);
        if (context != null) context.restoreConfig();
        if (actors == null) return;
        for (TestActor actor : actors) {
            WarpService.INSTANCE.forgetPlayer(actor);
            TransferService.INSTANCE.stopUsing(actor);
            for (WarpType type : WarpType.values()) ThaumcraftWarp.set(actor, type, 0);
            actor.worldObj.playerEntities.remove(actor);
            actor.worldObj.removeEntity(actor);
        }
        actors = null;
    }

    String stop() {
        cleanupActors();
        releaseTicket();
        if (run != null && !"IDLE".equals(phase)) {
            run.put("status", "STOPPED");
            writeReport();
        }
        queue = null;
        current = null;
        phase = "IDLE";
        return status();
    }

    void close() {
        stop();
    }

    private void releaseTicket() {
        if (ticket != null) {
            ForgeChunkManager.releaseTicket(ticket);
            ticket = null;
        }
    }

    String status() {
        return "phase=" + phase
            + " built="
            + built
            + " dimension="
            + dimension
            + " scenario="
            + (current == null ? "none" : current.id())
            + " ticks="
            + elapsed
            + " results="
            + (results == null ? 0 : results.size())
            + " run="
            + runId;
    }

    String report() {
        return "status=" + (run == null ? "NONE" : run.get("status")) + " path=" + reportPath + " write=" + writeStatus;
    }

    String actors() {
        if (actors == null)
            return "No active synthetic actors. Roles: owner,target,observer; stable namespace gtfi-testlab:<role>";
        StringBuilder out = new StringBuilder();
        for (TestActor actor : actors) out.append(actor.getCommandSenderName())
            .append(" UUID=")
            .append(actor.getUniqueID())
            .append(" (server FakePlayer) ");
        return out.toString();
    }

    String enter(EntityPlayerMP player) {
        idleRequired();
        if (!built) throw new IllegalStateException("Build the test area first");
        if (player.dimension == dimension) throw new IllegalStateException("Already in test dimension");
        returns.put(player.getUniqueID(), new ReturnPosition(player));
        transfer(player, GtfiTestLab.world(), 0.5, 65, 0.5, player.rotationYaw, player.rotationPitch);
        return "Entered dedicated test area";
    }

    String leave(EntityPlayerMP player) {
        ReturnPosition position = returns.get(player.getUniqueID());
        if (position == null) throw new IllegalStateException("No return position recorded for this player");
        restorePreview(player);
        WorldServer target = MinecraftServer.getServer()
            .worldServerForDimension(position.dimension);
        transfer(player, target, position.x, position.y, position.z, position.yaw, position.pitch);
        returns.remove(player.getUniqueID());
        return "Returned to original dimension and position";
    }

    String camera(EntityPlayerMP player, String pose) {
        if (!player.canCommandSenderUseCommand(4, "gtfi")) throw new IllegalStateException("Camera requires OP4");
        if (player.dimension != dimension || !(player.worldObj.provider instanceof TestWorldProvider))
            throw new IllegalStateException("Camera requires the dedicated test dimension");
        if (player.playerNetServerHandler == null)
            throw new IllegalStateException("Camera requires a connected player");
        if ("node".equals(pose)) {
            if (player.worldObj.getBlock(-4, 65, 0) != com.miaokatze.gtfi.register.ModContent.WARP_EJECTOR
                || !(player.worldObj.getTileEntity(-4, 65, 0) instanceof com.miaokatze.gtfi.block.TileWarpEjector))
                throw new IllegalStateException("Actual placed ejector/tile missing at (-4,65,0); build first");
            player.playerNetServerHandler.setPlayerLocation(-1.5, 65, 2.5, 135, 21.6F);
            return "camera=node actual placed gtfi:warp_ejector at (-4,65,0); feet=(-1.5,65,2.5),yaw=135,pitch=21.6";
        }
        if ("effects".equals(pose)) {
            player.playerNetServerHandler.setPlayerLocation(2, 65, 3, -90, 4.4F);
            return "camera=effects feet=(2,65,3),yaw=-90,pitch=4.4; natural actor focus=(10,66,3)";
        }
        throw new IllegalArgumentException("Camera poses: node|effects");
    }

    String visual(EntityPlayerMP player) {
        idleRequired();
        if (player.dimension != dimension) throw new IllegalStateException("Enter test dimension first");
        WorldServer world = GtfiTestLab.world();
        actors = new TestActor[] { new TestActor(world, "owner"), new TestActor(world, "target"),
            new TestActor(world, "observer") };
        context = new TestScenarios.Context(world, actors);
        context.tool();
        context.owner.setPosition(10, 65, 2);
        context.target.setPosition(10, 65, 4);
        context.warp(context.owner, WarpType.TEMP, 100);
        context.first = com.miaokatze.gtfi.core.EjectorSettings.portableDefaults();
        context.first.range = 5;
        context.first.damageFactor = 0;
        elapsed = 0;
        phase = "VISUAL";
        return "Natural production transfer at 3s and synthetic flight at 5s near (10,65,2); ends after 8s";
    }

    String preview(EntityPlayerMP player) {
        idleRequired();
        if (player.dimension != dimension || !(player.worldObj.provider instanceof TestWorldProvider))
            throw new IllegalStateException("Enter the test dimension first");
        NBTTagCompound entity = player.getEntityData();
        if (!entity.hasKey("GtfiPreviewBackup")) {
            NBTTagCompound backup = new NBTTagCompound();
            backup.setTag("inventory", player.inventory.writeToNBT(new NBTTagList()));
            backup.setInteger("slot", player.inventory.currentItem);
            for (WarpType type : WarpType.values()) backup.setInteger(type.name(), ThaumcraftWarp.get(player, type));
            entity.setTag("GtfiPreviewBackup", backup);
        }
        player.inventory.clearInventory(null, -1);
        player.inventory.currentItem = 0;
        com.miaokatze.gtfi.core.EjectorSettings portable = com.miaokatze.gtfi.core.EjectorSettings.portableDefaults();
        portable.enabled = false;
        ItemStack item = new ItemStack(com.miaokatze.gtfi.register.ModContent.PORTABLE_EJECTOR);
        com.miaokatze.gtfi.item.ItemPortableEjector.setSettings(item, portable);
        player.inventory.setInventorySlotContents(0, item);
        player.inventory
            .setInventorySlotContents(1, new ItemStack(com.miaokatze.gtfi.register.ModContent.WARP_TRANSFERER));
        player.inventory
            .setInventorySlotContents(2, new ItemStack(com.miaokatze.gtfi.register.ModContent.WARP_EJECTOR));
        ThaumcraftWarp.set(player, WarpType.TEMP, 100);
        ThaumcraftWarp.set(player, WarpType.PERM, 50);
        com.miaokatze.gtfi.block.TileWarpEjector display = (com.miaokatze.gtfi.block.TileWarpEjector) player.worldObj
            .getTileEntity(-4, 65, 0);
        com.miaokatze.gtfi.core.EjectorSettings settings = com.miaokatze.gtfi.core.EjectorSettings.blockDefaults();
        settings.countdownSeconds = 300;
        settings.damageFactor = 0;
        display.setOwner(player);
        display.setSettings(settings);
        player.inventoryContainer.detectAndSendChanges();
        return "Preview kit ready; actual 300s countdown at (-4,65,0). Use leave to restore inventory and warp.";
    }

    private void restorePreview(EntityPlayerMP player) {
        NBTTagCompound entity = player.getEntityData();
        if (!entity.hasKey("GtfiPreviewBackup")) return;
        NBTTagCompound backup = entity.getCompoundTag("GtfiPreviewBackup");
        player.inventory.readFromNBT(backup.getTagList("inventory", 10));
        player.inventory.currentItem = backup.getInteger("slot");
        for (WarpType type : WarpType.values()) ThaumcraftWarp.set(player, type, backup.getInteger(type.name()));
        entity.removeTag("GtfiPreviewBackup");
        com.miaokatze.gtfi.block.TileWarpEjector display = (com.miaokatze.gtfi.block.TileWarpEjector) GtfiTestLab
            .world()
            .getTileEntity(-4, 65, 0);
        if (display != null) {
            display.getSettings().enabled = false;
            display.setSettings(display.getSettings());
        }
        WarpService.INSTANCE.forgetPlayer(player);
        player.inventoryContainer.detectAndSendChanges();
    }

    private void transfer(EntityPlayerMP player, final WorldServer target, final double x, final double y,
        final double z, final float yaw, final float pitch) {
        MinecraftServer.getServer()
            .getConfigurationManager()
            .transferPlayerToDimension(player, target.provider.dimensionId, new Teleporter(target) {

                @Override
                public void placeInPortal(net.minecraft.entity.Entity entity, double ox, double oy, double oz,
                    float oldYaw) {
                    entity.setLocationAndAngles(x, y, z, yaw, pitch);
                }
            });
        player.playerNetServerHandler.setPlayerLocation(x, y, z, yaw, pitch);
    }

    private static final class ReturnPosition {

        final int dimension;
        final double x, y, z;
        final float yaw, pitch;

        ReturnPosition(EntityPlayerMP player) {
            dimension = player.dimension;
            x = player.posX;
            y = player.posY;
            z = player.posZ;
            yaw = player.rotationYaw;
            pitch = player.rotationPitch;
        }
    }
}

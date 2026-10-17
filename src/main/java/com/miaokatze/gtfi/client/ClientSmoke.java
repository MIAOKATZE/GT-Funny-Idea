package com.miaokatze.gtfi.client;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.Vec3;

import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.miaokatze.gtfi.GTFunnyIdea;
import com.miaokatze.gtfi.block.TileWarpEjector;
import com.miaokatze.gtfi.core.EjectorSettings;
import com.miaokatze.gtfi.core.TransferService;
import com.miaokatze.gtfi.core.WarpType;
import com.miaokatze.gtfi.item.ItemPortableEjector;
import com.miaokatze.gtfi.network.GtfiNetwork;
import com.miaokatze.gtfi.register.ModContent;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Explicit opt-in fixture: ordinary client packets, real server windows and framebuffer captures. */
public final class ClientSmoke {

    private final File output = new File(System.getProperty("gtfi.clientSmoke.output", "gtfi-client-smoke"));
    private final long installedAt = System.nanoTime();
    private final long startupBudget = Math.max(60L, Long.getLong("gtfi.clientSmoke.startupSeconds", 600L))
        * 1000000000L;
    private boolean connected;
    private boolean finished;
    private int ticks;
    private String capture;
    private boolean requireMui;
    private int chargingUntil;

    static void install() {
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientSmoke());
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            mc.gameSettings.pauseOnLostFocus = false;
            if (mc.currentScreen instanceof GuiIngameMenu) mc.displayGuiScreen(null);
            if (mc.thePlayer == null && System.nanoTime() - installedAt > startupBudget) {
                throw new IllegalStateException(
                    "Client smoke connection timed out: " + (mc.currentScreen == null ? "no screen"
                        : mc.currentScreen.getClass()
                            .getName()));
            }
            if (!connected && (mc.currentScreen instanceof GuiMainMenu
                || (mc.currentScreen != null && "lumien.custommainmenu.gui.GuiCustom".equals(
                    mc.currentScreen.getClass()
                        .getName())))) {
                connected = true;
                FMLClientHandler handler = FMLClientHandler.instance();
                handler.setupServerList();
                handler.connectToServer(
                    new GuiMainMenu(),
                    new ServerData(
                        "GTFI isolated smoke",
                        System.getProperty("gtfi.clientSmoke.host", "127.0.0.1") + ":"
                            + Integer.getInteger("gtfi.clientSmoke.port", 25676)));
            }
            if (mc.thePlayer == null || mc.theWorld == null) return;
            ticks++;
            if (ticks == 60) chat("/gtfi test build");
            if (ticks == 200) chat("/gtfi test enter");
            if (ticks == 250) {
                chat("/gamemode 1");
                chat("/give " + mc.thePlayer.getCommandSenderName() + " gtfi:warp_ejector 1");
                chat("/give " + mc.thePlayer.getCommandSenderName() + " gtfi:portable_ejector 1");
                chat("/give " + mc.thePlayer.getCommandSenderName() + " gtfi:warp_transferer 1");
                chat("/gtfi test camera node");
            }
            if (ticks == 280) {
                select(Item.getItemFromBlock(ModContent.WARP_EJECTOR));
                // Avoid the vanilla DDA zero-step loop at an exactly diagonal negative-coordinate ray.
                mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = 133;
            }
            if (ticks == 300) request("01-gold-quantum-node", false);
            if (ticks == 320) {
                mc.playerController.onPlayerRightClick(
                    mc.thePlayer,
                    mc.theWorld,
                    mc.thePlayer.getHeldItem(),
                    -4,
                    65,
                    0,
                    1,
                    Vec3.createVectorHelper(-3.5, 65.5, .5));
            }
            if (ticks == 360) request("02-ejector-mui", true);
            if (ticks == 390) {
                mc.thePlayer.closeScreen();
                select(ModContent.PORTABLE_EJECTOR);
            }
            if (ticks == 410) use(false);
            if (ticks == 430) writePortableThroughMui();
            if (ticks == 450) mc.thePlayer.closeScreen();
            if (ticks == 460) use(false);
            if (ticks == 470) {
                EjectorSettings stored = ItemPortableEjector.getSettings(mc.thePlayer.getHeldItem());
                if (stored.range != 14 || stored.enabled) throw new IllegalStateException(
                    "Native MUI portable write did not persist after reopening: range=" + stored.range
                        + " enabled="
                        + stored.enabled);
                request("03-pendant-mui", true);
                evidence(
                    "portable-roundtrip.txt",
                    "Native MUI C2S StringSyncValue(range=14), IntSyncValue(enabled=0); "
                        + "server inventory update and reopened panel confirm persisted range=14, enabled=false. "
                        + "This is native MUI network API validation, not physical mouse input.");
            }
            if (ticks == 480) {
                mc.thePlayer.closeScreen();
                sneak(false);
                select(ModContent.WARP_TRANSFERER);
            }
            if (ticks == 500) use(true);
            if (ticks == 520) ((IntSyncValue) nativeSync().findSyncHandlerNullable("type", 0))
                .setIntValue(WarpType.NORMAL.ordinal(), true, true);
            if (ticks == 535) mc.thePlayer.closeScreen();
            if (ticks == 540) use(true);
            if (ticks == 560) {
                if (TransferService.getType(mc.thePlayer.getHeldItem()) != WarpType.TEMP
                    || ((IntSyncValue) nativeSync().findSyncHandlerNullable("type", 0)).getIntValue()
                        != WarpType.TEMP.ordinal())
                    throw new IllegalStateException("Locked NORMAL type was not rejected");
                request("04-transferer-mui", true);
                evidence(
                    "transferer-locked-type.txt",
                    "Native MUI IntSyncValue(type=NORMAL) C2S request at level 1; "
                        + "server rejected locked type, reopened server panel and inventory remain TEMP.");
            }
            if (ticks == 570) {
                mc.thePlayer.closeScreen();
                sneak(false);
            }
            if (ticks == 590) mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
            if (ticks == 620) request("05-three-item-meshes", false);
            if (ticks == 650) {
                mc.thePlayer.closeScreen();
                chat("/gtfi test preview");
            }
            if (ticks == 680) request("06-authoritative-countdown", false);
            if (ticks == 700) {
                select(ModContent.WARP_TRANSFERER);
                use(false);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                chargingUntil = 780;
            }
            if (ticks < chargingUntil) GtfiNetwork.sendAlt(true);
            if (ticks == 750) request("07-authoritative-charge", false);
            if (ticks == 780) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                mc.playerController.onStoppedUsingItem(mc.thePlayer);
                GtfiNetwork.sendAlt(false);
            }
            if (ticks == 800) {
                chat("/gtfi test camera effects");
                chat("/gtfi test visual");
            }
            if (ticks == 870) request("08-authoritative-transfer-particles", false);
            if (ticks == 910) request("09-authoritative-flight-trail", false);
            if (ticks == 960) {
                chat("/gtfi test leave");
                output.mkdirs();
                Files.write(
                    new File(output, "complete.txt").toPath(),
                    ("Completed real client interaction smoke at " + new java.util.Date()
                        + "\nHeap max="
                        + Runtime.getRuntime()
                            .maxMemory()
                        + "\nWorld="
                        + mc.theWorld.provider.dimensionId
                        + "\n").getBytes(StandardCharsets.UTF_8));
                GTFunnyIdea.LOG.info("[GTFI-CLIENT-SMOKE] COMPLETE {}", output);
                finished = true;
            }
        } catch (Exception error) {
            fail(error);
        }
    }

    private void request(String name, boolean mui) {
        capture = name;
        requireMui = mui;
    }

    @SubscribeEvent
    public void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || capture == null || finished) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            String screen = mc.currentScreen == null ? "world"
                : mc.currentScreen.getClass()
                    .getName();
            if (requireMui && !screen.contains("modularui")) {
                throw new IllegalStateException("Expected server MUI screen for " + capture + ", got " + screen);
            }
            output.mkdirs();
            String filename = "gtfi-" + capture + ".png";
            ScreenShotHelper
                .saveScreenshot(mc.mcDataDir, filename, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            Files.copy(
                new File(new File(mc.mcDataDir, "screenshots"), filename).toPath(),
                new File(output, filename).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
            Files.write(
                new File(output, capture + ".txt").toPath(),
                ("screen=" + screen
                    + "\nworld="
                    + mc.theWorld.provider.dimensionId
                    + "\nposition="
                    + mc.thePlayer.posX
                    + ","
                    + mc.thePlayer.posY
                    + ","
                    + mc.thePlayer.posZ
                    + "\nfeetY="
                    + mc.thePlayer.boundingBox.minY
                    + "\nyaw="
                    + mc.thePlayer.rotationYaw
                    + "\npitch="
                    + mc.thePlayer.rotationPitch
                    + "\nheld="
                    + mc.thePlayer.getHeldItem()
                    + "\n"
                    + placedBlockDiagnostics()
                    + "\n").getBytes(StandardCharsets.UTF_8));
            if ("01-gold-quantum-node".equals(capture)) verifyPlacedBlock();
            GTFunnyIdea.LOG.info("[GTFI-CLIENT-SMOKE] CAPTURE {} screen={}", capture, screen);
            capture = null;
        } catch (Exception error) {
            fail(error);
        }
    }

    private void verifyPlacedBlock() {
        Minecraft mc = Minecraft.getMinecraft();
        MovingObjectPosition hit = mc.thePlayer.rayTrace(6, 1);
        if (mc.theWorld.getBlock(-4, 65, 0) != ModContent.WARP_EJECTOR
            || !(mc.theWorld.getTileEntity(-4, 65, 0) instanceof TileWarpEjector)
            || hit == null
            || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
            || hit.blockX != -4
            || hit.blockY != 65
            || hit.blockZ != 0
            || mc.thePlayer.getHeldItem() == null
            || mc.thePlayer.getHeldItem()
                .getItem() != Item.getItemFromBlock(ModContent.WARP_EJECTOR)) {
            throw new IllegalStateException(
                "Expected placed ejector Block/Tile at (-4,65,0), fresh native camera ray and selected block item: "
                    + placedBlockDiagnostics());
        }
    }

    private String placedBlockDiagnostics() {
        Minecraft mc = Minecraft.getMinecraft();
        Object tile = mc.theWorld.getTileEntity(-4, 65, 0);
        return "block=" + mc.theWorld.getBlock(-4, 65, 0)
            .getClass()
            .getName()
            + "\nblockIsExpected="
            + (mc.theWorld.getBlock(-4, 65, 0) == ModContent.WARP_EJECTOR)
            + "\ntile="
            + (tile == null ? "null"
                : tile.getClass()
                    .getName())
            + "\ntileIsExpected="
            + (tile instanceof TileWarpEjector)
            + "\nheldIsBlock="
            + (mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem()
                .getItem() == Item.getItemFromBlock(ModContent.WARP_EJECTOR))
            + "\ncachedHit="
            + describeHit(mc.objectMouseOver)
            + "\nfreshNativeHit="
            + describeHit(mc.thePlayer.rayTrace(6, 1));
    }

    private String describeHit(MovingObjectPosition hit) {
        return hit == null ? "null" : hit.typeOfHit + "," + hit.blockX + "," + hit.blockY + "," + hit.blockZ;
    }

    private PanelSyncManager nativeSync() {
        ModularScreen screen = ModularScreen.getCurrent();
        if (screen == null) throw new IllegalStateException("Expected native MUI window");
        return screen.getSyncManager()
            .getPanelSyncManager(
                screen.getMainPanel()
                    .getName());
    }

    private void writePortableThroughMui() {
        PanelSyncManager sync = nativeSync();
        if (!((BooleanSyncValue) sync.findSyncHandlerNullable("editable", 0)).getBoolValue())
            throw new IllegalStateException("Held portable device is incorrectly read-only");
        ((StringSyncValue) sync.findSyncHandlerNullable("range", 0)).setStringValue("14", true, true);
        ((IntSyncValue) sync.findSyncHandlerNullable("enabled", 0)).setIntValue(0, true, true);
    }

    private void evidence(String filename, String text) throws java.io.IOException {
        output.mkdirs();
        Files.write(new File(output, filename).toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private void select(Item item) {
        Minecraft mc = Minecraft.getMinecraft();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack == null || stack.getItem() != item) continue;
            if (slot >= 9) {
                mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, slot, 0, 2, mc.thePlayer);
                slot = 0;
            }
            mc.thePlayer.inventory.currentItem = slot;
            mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(slot));
            return;
        }
        throw new IllegalStateException("Missing fixture item " + item.getUnlocalizedName());
    }

    private void use(boolean sneaking) {
        Minecraft mc = Minecraft.getMinecraft();
        sneak(sneaking);
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
    }

    private void sneak(boolean sneaking) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.thePlayer.setSneaking(sneaking);
        mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, sneaking ? 1 : 2));
    }

    private void chat(String command) {
        Minecraft.getMinecraft().thePlayer.sendChatMessage(command);
    }

    private void fail(Exception error) {
        finished = true;
        GTFunnyIdea.LOG.error("[GTFI-CLIENT-SMOKE] FAILED", error);
        try {
            output.mkdirs();
            Files.write(
                new File(output, "failed.txt").toPath(),
                error.toString()
                    .getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }
}

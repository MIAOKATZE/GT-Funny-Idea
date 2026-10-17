package com.miaokatze.gtfi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.Item;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.miaokatze.gtfi.block.TileWarpEjector;
import com.miaokatze.gtfi.network.GtfiNetwork;
import com.miaokatze.gtfi.network.VisualMessage;
import com.miaokatze.gtfi.register.ModContent;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Loaded exclusively by ClientProxy. All HUD state is a short-lived server snapshot. */
public final class ClientRuntime {

    private static final ClientRuntime INSTANCE = new ClientRuntime();
    private final WarpParticles particles = new WarpParticles();
    private World world;
    private int tick;
    private int countdown = -1;
    private int countdownAt;
    private VisualMessage progress;
    private int progressAt;
    private VisualMessage level;
    private int levelAt;
    private boolean sentAlt;
    private int inputAt;
    private static boolean initialized;

    private ClientRuntime() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        MinecraftForge.EVENT_BUS.register(INSTANCE.particles);
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
        ClientRegistry.bindTileEntitySpecialRenderer(TileWarpEjector.class, new WarpEjectorRenderer());
        MinecraftForgeClient.registerItemRenderer(ModContent.PORTABLE_EJECTOR, new WarpItemRenderer(false));
        MinecraftForgeClient.registerItemRenderer(ModContent.WARP_TRANSFERER, new WarpItemRenderer(true));
        MinecraftForgeClient
            .registerItemRenderer(Item.getItemFromBlock(ModContent.WARP_EJECTOR), new WarpBlockItemRenderer());
        if (Boolean.getBoolean("gtfi.clientSmoke")) ClientSmoke.install();
    }

    private void clear() {
        countdown = -1;
        progress = null;
        level = null;
        particles.clear();
        GtfiNetwork.clearVisuals();
        sentAlt = false;
        inputAt = -100;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != world) {
            clear();
            world = mc.theWorld;
        }
        if (world == null || mc.thePlayer == null) return;
        tick++;
        boolean alt = mc.currentScreen == null && mc.inGameHasFocus
            && (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU));
        boolean holding = mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem()
            .getItem() == ModContent.WARP_TRANSFERER;
        alt &= holding;
        // Periodic refresh covers starting use while Alt is already held and server use validation.
        if (alt != sentAlt || (holding && tick - inputAt >= 10)) {
            GtfiNetwork.sendAlt(alt);
            sentAlt = alt;
            inputAt = tick;
        }
        for (int i = 0; i < 512; i++) {
            VisualMessage message = GtfiNetwork.pollVisual();
            if (message == null) break;
            if (message.dimension != world.provider.dimensionId) continue;
            switch (message.kind) {
                case VisualMessage.COUNTDOWN:
                    countdown = message.value;
                    countdownAt = tick;
                    break;
                case VisualMessage.PROGRESS:
                    progress = message.value < 0 ? null : message;
                    progressAt = tick;
                    break;
                case VisualMessage.LEVEL:
                    level = message;
                    levelAt = tick;
                    break;
                case VisualMessage.FLIGHT:
                    particles.trail(message);
                    break;
                case VisualMessage.TRANSFER:
                    particles.transfer(message);
                    break;
                default:
                    break;
            }
        }
        if (!mc.isGamePaused()) particles.tick();
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (event.world == world) clear();
    }

    @SubscribeEvent
    public void hud(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings.hideGUI) return;
        int width = event.resolution.getScaledWidth();
        int height = event.resolution.getScaledHeight();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (countdown >= 0 && tick - countdownAt < 40) {
                String warning = StatCollector.translateToLocal("gtfi.hud.ejector_warning");
                mc.fontRenderer.drawStringWithShadow(
                    warning,
                    width / 2 - mc.fontRenderer.getStringWidth(warning) / 2,
                    height / 2 - 51,
                    0xFFE6A0);
                String text = String.format(java.util.Locale.ROOT, "%.1f", countdown / 20D);
                GL11.glPushMatrix();
                GL11.glTranslatef(width / 2F, height / 2F - 34, 0);
                GL11.glScalef(2.8F, 2.8F, 1);
                mc.fontRenderer.drawStringWithShadow(
                    text,
                    -mc.fontRenderer.getStringWidth(text) / 2,
                    0,
                    countdown < 60 ? 0xFF4242 : 0xFFE6A0);
                GL11.glPopMatrix();
            }
            if (progress != null && tick - progressAt < 40 && progress.total > 0) {
                int x = width / 2 - 61;
                int y = height / 2 + 27;
                double fraction = Math.max(0, Math.min(1, progress.value / (double) progress.total));
                Gui.drawRect(x - 2, y - 2, x + 124, y + 9, 0xB5100C1D);
                Gui.drawRect(x, y, x + 122, y + 7, 0xFF30233D);
                Gui.drawRect(x, y, x + (int) (122 * fraction), y + 7, progress.active ? 0xFFD8A6FF : 0xFFB974E8);
                String label = StatCollector.translateToLocal(progress.active ? "gtfi.hud.charge" : "gtfi.hud.transfer")
                    + " "
                    + (int) (100 * fraction)
                    + "%";
                mc.fontRenderer.drawStringWithShadow(
                    label,
                    width / 2 - mc.fontRenderer.getStringWidth(label) / 2,
                    y + 13,
                    0xE8D5FF);
            }
            if (level != null && tick - levelAt < 100) {
                String label = StatCollector.translateToLocal(
                    "gtfi.hud.level") + " " + level.value + "  " + level.extra + "/" + level.total;
                mc.fontRenderer.drawStringWithShadow(
                    label,
                    width / 2 - mc.fontRenderer.getStringWidth(label) / 2,
                    height / 2 + 58,
                    0xE5C0FF);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

}

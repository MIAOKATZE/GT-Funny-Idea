package com.miaokatze.gtfi.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import com.miaokatze.gtfi.config.GtfiConfig;
import com.miaokatze.gtfi.core.TransferService;
import com.miaokatze.gtfi.core.WarpService;
import com.miaokatze.gtfi.core.WarpType;
import com.miaokatze.gtfi.register.ModContent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/** Common-side protocol and bounded thread handoff. No Minecraft client classes may enter this file. */
public final class GtfiNetwork {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("gtfi");
    private static final ArrayBlockingQueue<VisualMessage> VISUALS = new ArrayBlockingQueue<VisualMessage>(512);
    private static final Map<EntityPlayerMP, Boolean> PENDING_ALT = new ConcurrentHashMap<EntityPlayerMP, Boolean>();
    private static final Map<UUID, Boolean> ALT = new ConcurrentHashMap<UUID, Boolean>();
    private static final Map<EntityPlayerMP, Boolean> ALT_PLAYERS = new ConcurrentHashMap<EntityPlayerMP, Boolean>();
    private static final Map<UUID, VisualMessage> FLIGHT_SAMPLES = new ConcurrentHashMap<UUID, VisualMessage>();
    private static boolean initialized;

    private GtfiNetwork() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        CHANNEL.registerMessage(VisualHandler.class, VisualMessage.class, 0, Side.CLIENT);
        CHANNEL.registerMessage(AltHandler.class, AltKeyMessage.class, 1, Side.SERVER);
        FMLCommonHandler.instance()
            .bus()
            .register(new GtfiNetwork());
        WarpService.INSTANCE.setHooks(new WarpService.Hooks() {

            @Override
            public void countdown(EntityPlayerMP player, int ticks) {
                VisualMessage message = message(VisualMessage.COUNTDOWN, player);
                message.value = ticks;
                CHANNEL.sendTo(message, player);
            }

            @Override
            public void flight(EntityPlayerMP player, double speedMetersPerSecond, boolean active) {
                VisualMessage message = message(VisualMessage.FLIGHT, player);
                message.active = active;
                message.value = (int) Math.min(100000, Math.max(0, speedMetersPerSecond * 100));
                VisualMessage previous = active ? FLIGHT_SAMPLES.put(player.getUniqueID(), message)
                    : FLIGHT_SAMPLES.remove(player.getUniqueID());
                message.targetX = previous == null ? message.x : previous.x;
                message.targetY = previous == null ? message.y : previous.y;
                message.targetZ = previous == null ? message.z : previous.z;
                sendTrail(message);
            }
        });
        TransferService.INSTANCE.setHooks(new TransferService.Hooks() {

            @Override
            public void progress(EntityPlayerMP player, int ticks, int total, boolean charging) {
                VisualMessage message = message(VisualMessage.PROGRESS, player);
                message.value = ticks;
                message.total = total;
                message.active = charging;
                CHANNEL.sendTo(message, player);
            }

            @Override
            public void transferred(EntityPlayerMP from, EntityPlayerMP to, WarpType type, int amount) {
                VisualMessage message = message(VisualMessage.TRANSFER, from);
                message.targetX = to.posX;
                message.targetY = to.posY + 1;
                message.targetZ = to.posZ;
                message.value = amount;
                message.extra = type.ordinal();
                nearby(message, from);
                if (from.getDistanceSqToEntity(to) > 48 * 48) {
                    VisualMessage target = message(VisualMessage.TRANSFER, to);
                    target.targetX = target.x;
                    target.targetY = target.y;
                    target.targetZ = target.z;
                    target.value = amount;
                    target.extra = type.ordinal();
                    nearby(target, to);
                }
            }

            @Override
            public void levelChanged(EntityPlayerMP player, int level, int xp, int requiredXp) {
                VisualMessage message = message(VisualMessage.LEVEL, player);
                message.value = level;
                message.total = requiredXp;
                message.extra = xp;
                CHANNEL.sendTo(message, player);
            }
        });
    }

    private static VisualMessage message(int kind, EntityPlayerMP player) {
        VisualMessage message = new VisualMessage(kind, player.dimension, player.getEntityId());
        message.x = player.posX;
        message.y = player.posY + 1;
        message.z = player.posZ;
        return message;
    }

    private static void nearby(VisualMessage message, EntityPlayerMP player) {
        CHANNEL.sendToAllAround(
            message,
            new NetworkRegistry.TargetPoint(player.dimension, player.posX, player.posY, player.posZ, 64));
    }

    private static void sendTrail(VisualMessage message) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        double dx = message.x - message.targetX;
        double dy = message.y - message.targetY;
        double dz = message.z - message.targetZ;
        double lengthSq = dx * dx + dy * dy + dz * dz;
        for (Object object : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP viewer = (EntityPlayerMP) object;
            if (viewer.dimension != message.dimension) continue;
            double t = lengthSq == 0 ? 0
                : Math.max(
                    0,
                    Math.min(
                        1,
                        ((viewer.posX - message.targetX) * dx + (viewer.posY + 1 - message.targetY) * dy
                            + (viewer.posZ - message.targetZ) * dz) / lengthSq));
            double x = message.targetX + t * dx - viewer.posX;
            double y = message.targetY + t * dy - viewer.posY - 1;
            double z = message.targetZ + t * dz - viewer.posZ;
            if (x * x + y * y + z * z <= 64 * 64) CHANNEL.sendTo(message, viewer);
        }
    }

    public static boolean isAltPressed(EntityPlayer player) {
        return player != null && Boolean.TRUE.equals(ALT.get(player.getUniqueID()));
    }

    public static void sendAlt(boolean pressed) {
        CHANNEL.sendToServer(new AltKeyMessage(pressed));
    }

    public static VisualMessage pollVisual() {
        return VISUALS.poll();
    }

    public static void clearVisuals() {
        VISUALS.clear();
    }

    public static void clearServerState() {
        PENDING_ALT.clear();
        ALT.clear();
        ALT_PLAYERS.clear();
        FLIGHT_SAMPLES.clear();
    }

    public static final class VisualHandler implements IMessageHandler<VisualMessage, IMessage> {

        @Override
        public IMessage onMessage(VisualMessage message, MessageContext context) {
            if (!VISUALS.offer(message)) {
                VISUALS.poll();
                VISUALS.offer(message);
            }
            return null;
        }
    }

    public static final class AltHandler implements IMessageHandler<AltKeyMessage, IMessage> {

        @Override
        public IMessage onMessage(AltKeyMessage message, MessageContext context) {
            // Coalesce to one pending state per authenticated connection, irrespective of packet rate.
            PENDING_ALT.put(context.getServerHandler().playerEntity, message.pressed);
            return null;
        }
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        for (Map.Entry<EntityPlayerMP, Boolean> entry : PENDING_ALT.entrySet()) {
            EntityPlayerMP player = entry.getKey();
            Boolean state = entry.getValue();
            if (!PENDING_ALT.remove(player, state)) continue;
            boolean accepted = state.booleanValue() && !GtfiConfig.safeMode && validTransferer(player);
            if (accepted) {
                ALT.put(player.getUniqueID(), Boolean.TRUE);
                ALT_PLAYERS.put(player, Boolean.TRUE);
            } else {
                ALT.remove(player.getUniqueID());
                ALT_PLAYERS.remove(player);
            }
            TransferService.INSTANCE.setAltCharging(player, accepted);
        }
        for (EntityPlayerMP player : ALT_PLAYERS.keySet()) {
            if (GtfiConfig.safeMode || !validTransferer(player)) {
                ALT_PLAYERS.remove(player);
                ALT.remove(player.getUniqueID());
                TransferService.INSTANCE.setAltCharging(player, false);
            }
        }
    }

    private static boolean validTransferer(EntityPlayerMP player) {
        if (player.isDead || player.playerNetServerHandler == null) return false;
        ItemStack held = player.getHeldItem();
        ItemStack using = player.getItemInUse();
        return (held != null && held.getItem() == ModContent.WARP_TRANSFERER)
            || (player.isUsingItem() && using != null && using.getItem() == ModContent.WARP_TRANSFERER);
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ALT.remove(event.player.getUniqueID());
        ALT_PLAYERS.remove(event.player);
        PENDING_ALT.remove(event.player);
        FLIGHT_SAMPLES.remove(event.player.getUniqueID());
    }
}

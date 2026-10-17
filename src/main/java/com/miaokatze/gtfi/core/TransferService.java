package com.miaokatze.gtfi.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;

import com.miaokatze.gtfi.config.GtfiConfig;
import com.miaokatze.gtfi.register.ModContent;
import com.miaokatze.gtfi.testlab.GtfiTestLab;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Item callbacks supply use intent; targets, rates, balances and XP are computed here. */
public final class TransferService {

    public static final TransferService INSTANCE = new TransferService();

    public interface Hooks {

        void progress(EntityPlayerMP player, int ticks, int total, boolean charging);

        void transferred(EntityPlayerMP from, EntityPlayerMP to, WarpType type, int amount);

        void levelChanged(EntityPlayerMP player, int level, int xp, int requiredXp);
    }

    /** Fired after the milestone chat has been submitted to the server player manager. */
    public static final class MilestoneEvent extends Event {

        public final EntityPlayerMP player;
        public final int level;

        public MilestoneEvent(EntityPlayerMP player, int level) {
            this.player = player;
            this.level = level;
        }
    }

    private static final Hooks NO_HOOKS = new Hooks() {

        public void progress(EntityPlayerMP player, int ticks, int total, boolean charging) {}

        public void transferred(EntityPlayerMP from, EntityPlayerMP to, WarpType type, int amount) {}

        public void levelChanged(EntityPlayerMP player, int level, int xp, int requiredXp) {}
    };
    private final Map<UUID, UseState> uses = new HashMap<UUID, UseState>();
    private final Map<UUID, Boolean> altCharging = new HashMap<UUID, Boolean>();
    private Hooks hooks = NO_HOOKS;
    private boolean registered;

    private TransferService() {}

    public void setHooks(Hooks hooks) {
        this.hooks = hooks == null ? NO_HOOKS : hooks;
    }

    public void register() {
        if (!registered) {
            FMLCommonHandler.instance()
                .bus()
                .register(this);
            registered = true;
        }
    }

    public void setAltCharging(EntityPlayerMP player, boolean pressed) {
        if (!pressed) {
            altCharging.remove(player.getUniqueID());
            return;
        }
        ItemStack held = player.getCurrentEquippedItem();
        if (held != null && held.getItem() == ModContent.WARP_TRANSFERER
            && player.isUsingItem()
            && !GtfiConfig.safeMode) altCharging.put(player.getUniqueID(), true);
    }

    public boolean isAltCharging(EntityPlayer player) {
        return Boolean.TRUE.equals(altCharging.get(player.getUniqueID()));
    }

    public void onUsingTick(EntityPlayer user, ItemStack stack, boolean charging) {
        if (!(user instanceof EntityPlayerMP) || user.worldObj.isRemote) return;
        EntityPlayerMP player = (EntityPlayerMP) user;
        if (GtfiConfig.safeMode || player.isDead
            || !player.isEntityAlive()
            || stack == null
            || stack != player.getCurrentEquippedItem()
            || !player.isUsingItem()
            || stack.getItem() != ModContent.WARP_TRANSFERER) {
            stopUsing(player);
            return;
        }
        // Bind the entire warmup to a currently visible, aimed-at player. Alt self-charging is target-free.
        EntityPlayerMP target = charging ? null : findTarget(player);
        if (!charging && target == null) {
            // stopUsingItem re-enters ItemWarpTransferer.onPlayerStoppedUsing, whose cleanup is idempotent.
            player.stopUsingItem();
            stopUsing(player);
            return;
        }
        long now = player.worldObj.getTotalWorldTime();
        UseState state = uses.get(player.getUniqueID());
        WarpType type = getType(stack);
        if (!canSelect(player, type)) type = WarpType.TEMP;
        if (state == null || state.stack != stack
            || state.charging != charging
            || state.type != type
            || !charging && !target.getUniqueID()
                .equals(state.targetId)
            || state.dimension != player.dimension
            || now > state.lastTick + 1) {
            state = new UseState(stack, charging, type, player.dimension, now, target);
            state.player = player;
            uses.put(player.getUniqueID(), state);
        }
        if (state.lastTick == now && state.started != now) return;
        if (state.processedTick == now) return;
        state.lastTick = now;
        state.processedTick = now;
        if (charging) {
            charge(player);
            hooks.progress(player, (int) (progressTag(player).getDouble("ChargeFraction") * 1000), 1000, true);
            return;
        }
        long elapsed = now - state.started;
        if (elapsed < 60) {
            hooks.progress(player, (int) elapsed, 60, false);
            return;
        }
        hooks.progress(player, (int) ((elapsed - 60) % 20), 20, false);
        if ((elapsed - 60) % 20 != 0) return;
        int amount = ThaumcraftWarp.transfer(player, target, type, getLevel(player));
        if (amount <= 0) return;
        awardExperience(player, amount * type.experience);
        player.addChatMessage(
            new ChatComponentTranslation(
                "gtfi.transfer.sent",
                target.getCommandSenderName(),
                amount,
                new ChatComponentTranslation(
                    "gtfi.warp." + type.name()
                        .toLowerCase(java.util.Locale.ROOT))));
        target.addChatMessage(
            new ChatComponentTranslation(
                "gtfi.transfer.received",
                player.getCommandSenderName(),
                amount,
                new ChatComponentTranslation(
                    "gtfi.warp." + type.targetType()
                        .name()
                        .toLowerCase(java.util.Locale.ROOT))));
        hooks.transferred(player, target, type, amount);
    }

    private void charge(EntityPlayerMP player) {
        int permanent = ThaumcraftWarp.get(player, WarpType.PERM);
        if (permanent <= 0) return;
        NBTTagCompound progress = progressTag(player);
        double saved = progress.getDouble("ChargeFraction");
        if (Double.isNaN(saved) || Double.isInfinite(saved) || saved < 0 || saved >= 1) saved = 0;
        double fraction = saved + permanent / 2000.0;
        int requested = (int) Math.floor(fraction + 1.0E-10);
        if (requested > 0) ThaumcraftWarp.addTemporary(player, requested);
        progress.setDouble("ChargeFraction", Math.max(0, fraction - requested));
    }

    public EntityPlayerMP findTarget(EntityPlayerMP player) {
        Vec3 eye = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLookVec();
        Vec3 end = eye.addVector(look.xCoord * 3, look.yCoord * 3, look.zCoord * 3);
        // World ray tracing advances its first Vec3 in place. Keep the real eye for entity hits/range.
        MovingObjectPosition wall = player.worldObj.func_147447_a(
            Vec3.createVectorHelper(eye.xCoord, eye.yCoord, eye.zCoord),
            Vec3.createVectorHelper(end.xCoord, end.yCoord, end.zCoord),
            false,
            true,
            false);
        double bestDistance = wall == null ? 3.000001 : eye.distanceTo(wall.hitVec);
        EntityPlayerMP nearest = null;
        for (Object object : player.worldObj.playerEntities) {
            if (!(object instanceof EntityPlayerMP) || object == player) continue;
            EntityPlayerMP other = (EntityPlayerMP) object;
            if (!other.isEntityAlive() || other.isDead
                || other.dimension != player.dimension
                || other instanceof FakePlayer && !GtfiTestLab.isSyntheticActor(other)) continue;
            AxisAlignedBB box = other.boundingBox.expand(0.1, 0.1, 0.1);
            MovingObjectPosition hit = box.calculateIntercept(eye, end);
            double distance = box.isVecInside(eye) ? 0
                : hit == null ? Double.POSITIVE_INFINITY : eye.distanceTo(hit.hitVec);
            if (distance < bestDistance && distance <= 3) {
                nearest = other;
                bestDistance = distance;
            }
        }
        return nearest;
    }

    public static int getLevel(EntityPlayer player) {
        return Math.max(1, Math.min(10, progressTag(player).getInteger("Level")));
    }

    public static int getExperience(EntityPlayer player) {
        int level = getLevel(player);
        return level == 10 ? 0
            : Math.max(
                0,
                Math.min(TransferProgression.required(level) - 1, progressTag(player).getInteger("Experience")));
    }

    public static int getNextLevelExperience(EntityPlayer player) {
        return TransferProgression.required(getLevel(player));
    }

    public static boolean canSelect(EntityPlayer player, WarpType type) {
        return type != null && TransferProgression.canSelect(getLevel(player), type);
    }

    public static WarpType getType(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return WarpType.TEMP;
        int ordinal = stack.getTagCompound()
            .getInteger("GtfiWarpType");
        return ordinal >= 0 && ordinal < WarpType.values().length ? WarpType.values()[ordinal] : WarpType.TEMP;
    }

    public static boolean setType(EntityPlayer player, ItemStack stack, WarpType type) {
        if (player.worldObj.isRemote || GtfiConfig.safeMode
            || stack == null
            || stack != player.getCurrentEquippedItem()
            || stack.getItem() != ModContent.WARP_TRANSFERER
            || !canSelect(player, type)) return false;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setInteger("GtfiWarpType", type.ordinal());
        return true;
    }

    private void awardExperience(EntityPlayerMP player, int amount) {
        int previous = getLevel(player);
        int[] next = TransferProgression.add(previous, getExperience(player), amount);
        NBTTagCompound tag = progressTag(player);
        tag.setInteger("Level", next[0]);
        tag.setInteger("Experience", next[1]);
        hooks.levelChanged(player, next[0], next[1], TransferProgression.required(next[0]));
        for (int milestone : new int[] { 3, 6, 10 }) {
            if (previous < milestone && next[0] >= milestone && MinecraftServer.getServer() != null) {
                MinecraftServer.getServer()
                    .getConfigurationManager()
                    .sendChatMsg(
                        new ChatComponentTranslation(
                            "gtfi.transfer.milestone",
                            player.getCommandSenderName(),
                            milestone));
                MinecraftForge.EVENT_BUS.post(new MilestoneEvent(player, milestone));
            }
        }
    }

    private static NBTTagCompound progressTag(EntityPlayer player) {
        NBTTagCompound entity = player.getEntityData();
        if (!entity.hasKey(EntityPlayer.PERSISTED_NBT_TAG))
            entity.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        NBTTagCompound persistent = entity.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persistent.hasKey("GtfiTransfer")) persistent.setTag("GtfiTransfer", new NBTTagCompound());
        return persistent.getCompoundTag("GtfiTransfer");
    }

    public void stopUsing(EntityPlayer player) {
        uses.remove(player.getUniqueID());
        altCharging.remove(player.getUniqueID());
        if (player instanceof EntityPlayerMP) hooks.progress((EntityPlayerMP) player, -1, 0, false);
    }

    public void clear() {
        uses.clear();
        altCharging.clear();
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Iterator<Map.Entry<UUID, UseState>> iterator = uses.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UseState> entry = iterator.next();
            UseState state = entry.getValue();
            EntityPlayer player = state.player;
            if (player == null) continue;
            if (player.isDead || player.dimension != state.dimension
                || player.worldObj.getTotalWorldTime() > state.lastTick + 1
                || !player.isUsingItem()
                || player.getCurrentEquippedItem() != state.stack
                || GtfiConfig.safeMode) {
                iterator.remove();
                altCharging.remove(entry.getKey());
                hooks.progress((EntityPlayerMP) player, -1, 0, false);
            }
        }
    }

    private static final class UseState {

        final ItemStack stack;
        final boolean charging;
        final WarpType type;
        final int dimension;
        final long started;
        final UUID targetId;
        EntityPlayer player;
        long lastTick, processedTick = Long.MIN_VALUE;

        UseState(ItemStack stack, boolean charging, WarpType type, int dimension, long tick, EntityPlayerMP target) {
            this.stack = stack;
            this.charging = charging;
            this.type = type;
            this.dimension = dimension;
            started = tick;
            targetId = target == null ? null : target.getUniqueID();
            lastTick = tick;
        }
    }
}

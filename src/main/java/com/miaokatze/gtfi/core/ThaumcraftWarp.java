package com.miaokatze.gtfi.core;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import thaumcraft.common.Thaumcraft;
import thaumcraft.common.config.Config;
import thaumcraft.common.lib.network.PacketHandler;
import thaumcraft.common.lib.network.playerdata.PacketSyncWarp;
import thaumcraft.common.lib.research.PlayerKnowledge;

/** Server-only adapter. The public helper cannot remove permanent warp in TC 4. */
public final class ThaumcraftWarp {

    private ThaumcraftWarp() {}

    public static int get(EntityPlayer player, WarpType type) {
        PlayerKnowledge knowledge = Thaumcraft.proxy.getPlayerKnowledge();
        String name = player.getCommandSenderName();
        switch (type) {
            case TEMP:
                return knowledge.getWarpTemp(name);
            case NORMAL:
                return knowledge.getWarpSticky(name);
            default:
                return knowledge.getWarpPerm(name);
        }
    }

    public static long total(EntityPlayer player) {
        return (long) get(player, WarpType.TEMP) + get(player, WarpType.NORMAL) + get(player, WarpType.PERM);
    }

    public static boolean isWarded(EntityPlayer player) {
        return player.isPotionActive(Config.potionWarpWardID);
    }

    public static void set(EntityPlayerMP player, WarpType type, int value) {
        if (player.worldObj.isRemote) throw new IllegalStateException("Warp writes require the server");
        write(player, type, Math.max(0, value));
        sync(player, type);
    }

    public static int addTemporary(EntityPlayerMP player, int amount) {
        if (amount <= 0 || player.worldObj.isRemote) return 0;
        int old = get(player, WarpType.TEMP);
        int actual = (int) Math.min((long) amount, Integer.MAX_VALUE - (long) old);
        if (actual > 0) set(player, WarpType.TEMP, old + actual);
        return actual;
    }

    /** Both values are committed before either client sync; never credits more than was debited. */
    public static int transfer(EntityPlayerMP from, EntityPlayerMP to, WarpType type, int requested) {
        if (requested <= 0 || from == to || from.worldObj.isRemote || to.worldObj.isRemote) return 0;
        WarpType destination = type.targetType();
        int sourceValue = get(from, type);
        int targetValue = get(to, destination);
        int amount = (int) Math.min(Math.min((long) requested, sourceValue), Integer.MAX_VALUE - (long) targetValue);
        if (amount <= 0) return 0;
        write(from, type, sourceValue - amount);
        write(to, destination, targetValue + amount);
        sync(from, type);
        sync(to, destination);
        return amount;
    }

    private static void write(EntityPlayerMP player, WarpType type, int value) {
        PlayerKnowledge knowledge = Thaumcraft.proxy.getPlayerKnowledge();
        String name = player.getCommandSenderName();
        switch (type) {
            case TEMP:
                knowledge.setWarpTemp(name, value);
                break;
            case NORMAL:
                knowledge.setWarpSticky(name, value);
                break;
            default:
                knowledge.setWarpPerm(name, value);
                break;
        }
        knowledge.setWarpCounter(name, (int) Math.min(Integer.MAX_VALUE, total(player)));
    }

    private static void sync(EntityPlayerMP player, WarpType type) {
        PacketHandler.INSTANCE.sendTo(new PacketSyncWarp(player, type.syncId), player);
    }
}

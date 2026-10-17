package com.miaokatze.gtfi.ui;

import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;

/** A GUI follows one device in one authoritative slot, including vanilla's post-use stack copy. */
final class InventoryUiBinding {

    private static final String DEVICE_ID = "GtfiDeviceId";
    private final PlayerInventoryGuiData data;
    private final Item item;
    private final String deviceId;
    private final boolean client;

    InventoryUiBinding(PlayerInventoryGuiData data, Item item) {
        this.data = data;
        this.item = item;
        this.client = data.getWorld().isRemote;
        ItemStack stack = data.getUsedItemStack();
        if (stack == null || stack.getItem() != item) throw new IllegalArgumentException("Wrong GUI device");
        if (!client && id(stack).isEmpty()) {
            if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
            stack.getTagCompound()
                .setString(
                    DEVICE_ID,
                    UUID.randomUUID()
                        .toString());
            data.getPlayer().inventory.markDirty();
        }
        this.deviceId = id(stack);
    }

    private static String id(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound()
            .getString(DEVICE_ID) : "";
    }

    ItemStack current() {
        ItemStack stack = data.getUsedItemStack();
        if (stack == null || stack.stackSize != 1 || stack.getItem() != item) return null;
        // The initial client inventory packet may precede the server's device-ID assignment.
        // Client getters only populate caches; every mutation validates the server's nonempty ID.
        return (client && deviceId.isEmpty()) || (!deviceId.isEmpty() && deviceId.equals(id(stack))) ? stack : null;
    }

    boolean isValid() {
        return current() != null;
    }

    void markDirty(ItemStack stack) {
        if (!client && stack != null && stack == current()) {
            data.setUsedItemStack(stack);
            data.getPlayer().inventory.markDirty();
        }
    }
}

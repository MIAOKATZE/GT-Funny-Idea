package com.miaokatze.gtfi.testlab;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.RecipeViewerSettings;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.inventory.InventoryTypes;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.value.sync.ValueSyncHandler;
import com.miaokatze.gtfi.config.GtfiConfig;
import com.miaokatze.gtfi.core.TransferService;
import com.miaokatze.gtfi.core.WarpType;
import com.miaokatze.gtfi.item.ItemPortableEjector;
import com.miaokatze.gtfi.register.ModContent;
import com.miaokatze.gtfi.testlab.TestScenarios.Context;

import io.netty.buffer.Unpooled;

/** Writes through the native MUI handlers registered by the real item holders. */
final class UiScenarios {

    private UiScenarios() {}

    static List<TestScenarios.Scenario> all() {
        return Arrays.<TestScenarios.Scenario>asList(new TestScenarios.Case("ui.portable_slot_copy_writeback") {

            public boolean tick(Context c, int t) throws Exception {
                boolean safeMode = GtfiConfig.safeMode;
                try {
                    GtfiConfig.safeMode = false;
                    c.owner.inventory.currentItem = 0;
                    ItemStack original = new ItemStack(ModContent.PORTABLE_EJECTOR);
                    c.owner.inventory.setInventorySlotContents(0, original);
                    PlayerInventoryGuiData data = new PlayerInventoryGuiData(c.owner, InventoryTypes.PLAYER, 0);
                    PanelSyncManager sync = serverSync();
                    ModContent.PORTABLE_EJECTOR.buildUI(data, sync, new UISettings(RecipeViewerSettings.DUMMY));
                    BooleanSyncValue editable = sync.findSyncHandler("editable", BooleanSyncValue.class);
                    c.check(editable.getBoolValue(), "native pendant UI initially editable by holder");
                    ItemStack current = ItemStack.copyItemStack(original);
                    c.owner.inventory.setInventorySlotContents(0, current);
                    editable.updateCacheFromSource(false);
                    c.check(editable.getBoolValue(), "vanilla post-right-click ItemStack copy keeps pendant editable");

                    StringSyncValue range = sync.findSyncHandler("range", StringSyncValue.class);
                    IntSyncValue enabled = sync.findSyncHandler("enabled", IntSyncValue.class);
                    c.check(range.isAllowC2S() && enabled.isAllowC2S(), "native numeric and toggle handlers allow C2S");
                    write(range, "14");
                    write(enabled, 0);
                    c.near(
                        ItemPortableEjector.getSettings(current).range,
                        14,
                        0,
                        "native MUI numeric packet writes current inventory stack");
                    c.check(
                        !ItemPortableEjector.getSettings(current).enabled,
                        "native MUI toggle packet persists to current inventory stack");
                    c.near(
                        ItemPortableEjector.getSettings(original).range,
                        10,
                        0,
                        "detached original stack is never mutated by numeric packet");
                    c.check(
                        ItemPortableEjector.getSettings(original).enabled,
                        "detached original stack is never mutated by toggle packet");
                    write(range, "NaN");
                    c.near(
                        ItemPortableEjector.getSettings(current).range,
                        14,
                        0,
                        "native numeric packet rejects nonfinite input");

                    ItemStack replacement = new ItemStack(ModContent.PORTABLE_EJECTOR);
                    c.owner.inventory.setInventorySlotContents(0, replacement);
                    ModContent.PORTABLE_EJECTOR.buildUI(data, serverSync(), new UISettings(RecipeViewerSettings.DUMMY));
                    c.check(
                        !original.getTagCompound()
                            .getString("GtfiDeviceId")
                            .equals(
                                replacement.getTagCompound()
                                    .getString("GtfiDeviceId")),
                        "separately opened same-type device receives a distinct server UUID");
                    editable.updateCacheFromSource(false);
                    c.check(!editable.getBoolValue(), "different same-type device invalidates old UI session");
                    write(range, "25");
                    write(enabled, 0);
                    c.check(
                        !replacement.getTagCompound()
                            .hasKey("gtfiSettings"),
                        "old UI packets cannot modify replacement device with different UUID");
                    ItemStack foreign = new ItemStack(Items.diamond);
                    c.owner.inventory.setInventorySlotContents(0, foreign);
                    write(range, "30");
                    write(enabled, 0);
                    c.check(!foreign.hasTagCompound(), "old UI packets cannot modify unrelated item in bound slot");
                    c.owner.inventory.setInventorySlotContents(0, null);
                    write(range, "30");
                    write(enabled, 0);
                    c.check(
                        c.owner.inventory.getStackInSlot(0) == null,
                        "empty bound slot safely rejects native UI writes");
                    return true;
                } finally {
                    GtfiConfig.safeMode = safeMode;
                }
            }
        }, new TestScenarios.Case("ui.transferer_slot_copy_writeback") {

            public boolean tick(Context c, int t) throws Exception {
                boolean safeMode = GtfiConfig.safeMode;
                try {
                    GtfiConfig.safeMode = false;
                    c.owner.inventory.currentItem = 0;
                    c.progress(6, 0);
                    ItemStack original = new ItemStack(ModContent.WARP_TRANSFERER);
                    c.owner.inventory.setInventorySlotContents(0, original);
                    PlayerInventoryGuiData data = new PlayerInventoryGuiData(c.owner, InventoryTypes.PLAYER, 0);
                    PanelSyncManager sync = serverSync();
                    ModContent.WARP_TRANSFERER.buildUI(data, sync, new UISettings(RecipeViewerSettings.DUMMY));
                    IntSyncValue type = sync.findSyncHandler("type", IntSyncValue.class);
                    ItemStack current = original.copy();
                    c.owner.inventory.setInventorySlotContents(0, current);
                    write(type, WarpType.NORMAL.ordinal());
                    c.check(
                        TransferService.getType(current) == WarpType.NORMAL,
                        "native type button writes copied current transferer stack");
                    c.check(
                        TransferService.getType(original) == WarpType.TEMP,
                        "native type button never writes detached original stack");
                    write(type, WarpType.PERM.ordinal());
                    c.check(
                        TransferService.getType(current) == WarpType.PERM,
                        "native type button accepts unlocked permanent mode");
                    GtfiConfig.safeMode = true;
                    write(type, WarpType.TEMP.ordinal());
                    c.check(
                        TransferService.getType(current) == WarpType.PERM,
                        "safeMode rejects native type button writes");
                    GtfiConfig.safeMode = false;
                    ItemStack replacement = new ItemStack(ModContent.WARP_TRANSFERER);
                    c.owner.inventory.setInventorySlotContents(0, replacement);
                    ModContent.WARP_TRANSFERER.buildUI(data, serverSync(), new UISettings(RecipeViewerSettings.DUMMY));
                    c.check(
                        !original.getTagCompound()
                            .getString("GtfiDeviceId")
                            .equals(
                                replacement.getTagCompound()
                                    .getString("GtfiDeviceId")),
                        "replacement transferer has distinct server-assigned device UUID");
                    write(type, WarpType.NORMAL.ordinal());
                    c.check(
                        TransferService.getType(replacement) == WarpType.TEMP,
                        "old type button cannot modify replacement transferer UUID");
                    write(type, -1);
                    write(type, 999);
                    c.check(
                        TransferService.getType(replacement) == WarpType.TEMP,
                        "out-of-range type packets cannot modify inventory");
                    return true;
                } finally {
                    GtfiConfig.safeMode = safeMode;
                }
            }
        });
    }

    private static PanelSyncManager serverSync() {
        return new PanelSyncManager(new ModularSyncManager(false), true);
    }

    private static void write(StringSyncValue target, String value) throws IOException {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            new StringSyncValue(() -> value).write(packet);
            target.readOnServer(ValueSyncHandler.SYNC_VALUE, packet);
        } finally {
            packet.release();
        }
    }

    private static void write(IntSyncValue target, int value) throws IOException {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            new IntSyncValue(() -> value).write(packet);
            target.readOnServer(ValueSyncHandler.SYNC_VALUE, packet);
        } finally {
            packet.release();
        }
    }
}

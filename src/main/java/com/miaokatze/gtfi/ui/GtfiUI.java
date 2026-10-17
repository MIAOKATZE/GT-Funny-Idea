package com.miaokatze.gtfi.ui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtfi.block.TileWarpEjector;
import com.miaokatze.gtfi.config.GtfiConfig;
import com.miaokatze.gtfi.core.EjectorSettings;
import com.miaokatze.gtfi.core.TransferService;
import com.miaokatze.gtfi.core.WarpType;
import com.miaokatze.gtfi.item.ItemPortableEjector;
import com.miaokatze.gtfi.register.ModContent;

/** Every mutable handler validates server-side identity, bounds and permissions before applying. */
public final class GtfiUI {

    private GtfiUI() {}

    /** Uses MUI's built-in factories; no custom Container or GUI bridge is needed. */
    public static void init() {}

    static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    public static ModularPanel ejector(EntityPlayer player, PanelSyncManager sync, TileWarpEjector tile) {
        BooleanSupplier valid = () -> !tile.isInvalid() && tile.getWorldObj() == player.worldObj
            && player.getDistanceSq(tile.xCoord + 0.5, tile.yCoord + 0.5, tile.zCoord + 0.5) <= 64
            && tile.canEdit(player);
        return settingsPanel("ejector", false, sync, tile::getSettings, tile::setSettings, valid);
    }

    public static ModularPanel portable(PlayerInventoryGuiData data, PanelSyncManager sync) {
        InventoryUiBinding binding = new InventoryUiBinding(data, ModContent.PORTABLE_EJECTOR);
        return settingsPanel("portable", true, sync, () -> {
            ItemStack current = binding.current();
            return current == null ? EjectorSettings.portableDefaults() : ItemPortableEjector.getSettings(current);
        }, value -> {
            ItemStack current = binding.current();
            if (current != null) {
                ItemPortableEjector.setSettings(current, value);
                binding.markDirty(current);
            }
        }, binding::isValid);
    }

    private static ModularPanel settingsPanel(String name, boolean portable, PanelSyncManager sync,
        Supplier<EjectorSettings> getter, Consumer<EjectorSettings> setter, BooleanSupplier canEdit) {
        ModularPanel panel = GtfiMuiTheme.panel(name, 240);
        GtfiMuiTheme.header(panel, "gtfi.ui." + name);
        BooleanSyncValue editable = new BooleanSyncValue(canEdit);
        sync.syncValue("editable", editable);
        panel.child(
            GtfiMuiTheme.label(
                () -> tr(
                    editable.getBoolValue() ? "gtfi.ui.settings_hint"
                        : portable ? "gtfi.ui.device_unavailable" : "gtfi.ui.read_only"),
                14,
                39,
                292,
                22,
                GtfiMuiTheme.MUTED));

        numeric(panel, sync, "threshold", 64, () -> getter.get().threshold, v -> {
            EjectorSettings s = getter.get()
                .copy();
            s.threshold = (int) v;
            setter.accept(s);
        }, canEdit, 25, Integer.MAX_VALUE);
        numeric(panel, sync, "power", 91, () -> getter.get().power, v -> {
            EjectorSettings s = getter.get()
                .copy();
            s.power = v;
            setter.accept(s);
        }, canEdit, portable ? 0.1 : 1, portable ? 1 : 1000);
        numeric(panel, sync, "damage", 118, () -> getter.get().damageFactor * 100, v -> {
            EjectorSettings s = getter.get()
                .copy();
            s.damageFactor = v / 100;
            setter.accept(s);
        }, canEdit, 0, 100);
        IntSyncValue minCountdown = new IntSyncValue(() -> portable || GtfiConfig.safeMode ? 5 : 15);
        sync.syncValue("min_countdown", minCountdown);
        numeric(panel, sync, "countdown", 145, () -> getter.get().countdownSeconds, v -> {
            EjectorSettings s = getter.get()
                .copy();
            s.countdownSeconds = (int) v;
            setter.accept(s);
        }, canEdit, () -> minCountdown.getIntValue(), 86400);
        numeric(panel, sync, "range", 172, () -> getter.get().range, v -> {
            EjectorSettings s = getter.get()
                .copy();
            s.range = v;
            setter.accept(s);
        }, canEdit, portable ? 5 : 10, portable ? 50 : 200);

        IntSyncValue enabled = new IntSyncValue(() -> getter.get().enabled ? 1 : 0, v -> {
            if (!sync.isClient() && canEdit.getAsBoolean() && (v == 0 || v == 1)) {
                EjectorSettings s = getter.get()
                    .copy();
                s.enabled = v == 1;
                setter.accept(s);
            }
        }).allowC2S();
        sync.syncValue("enabled", enabled);
        panel.child(
            new CycleButtonWidget().stateCount(2)
                .value(enabled)
                .pos(14, 205)
                .size(292, 24)
                .background(GtfiMuiTheme.BUTTON)
                .hoverBackground(GtfiMuiTheme.PRESSED)
                .stateOverlay(
                    0,
                    IKey.lang("gtfi.ui.disabled")
                        .color(GtfiMuiTheme.MUTED)
                        .shadow(false))
                .stateOverlay(
                    1,
                    IKey.lang("gtfi.ui.enabled")
                        .color(GtfiMuiTheme.TITLE)
                        .shadow(false)));
        return panel;
    }

    private static void numeric(ModularPanel panel, PanelSyncManager sync, String name, int y, DoubleSupplier getter,
        DoubleConsumer setter, BooleanSupplier allowed, double min, double max) {
        numeric(panel, sync, name, y, getter, setter, allowed, () -> min, max);
    }

    private static void numeric(ModularPanel panel, PanelSyncManager sync, String name, int y, DoubleSupplier getter,
        DoubleConsumer setter, BooleanSupplier allowed, DoubleSupplier min, double max) {
        StringSyncValue value = new StringSyncValue(() -> format(getter.getAsDouble()), text -> {
            if (sync.isClient() || !allowed.getAsBoolean() || text.length() > 32) return;
            try {
                double requested = Double.parseDouble(text);
                if (Double.isNaN(requested) || Double.isInfinite(requested)) return;
                setter.accept(Math.max(min.getAsDouble(), Math.min(max, requested)));
            } catch (NumberFormatException ignored) {}
        }).allowC2S();
        sync.syncValue(name, value);
        panel.child(GtfiMuiTheme.label(() -> tr("gtfi.ui." + name), 14, y, 186, 22, GtfiMuiTheme.TEXT));
        panel.child(
            new TextFieldWidget().pos(204, y)
                .size(102, 22)
                .background(GtfiMuiTheme.PANEL)
                .value(value)
                .setMaxLength(16)
                .setTextColor(GtfiMuiTheme.TEXT)
                .setMarkedColor(0xffc4a36b)
                .setNumbersDouble(v -> Math.max(min.getAsDouble(), Math.min(max, v)))
                .setDefaultNumber(min.getAsDouble())
                .addTooltipLine(IKey.dynamic(() -> format(min.getAsDouble()) + " - " + format(max))));
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    public static ModularPanel transferer(PlayerInventoryGuiData data, PanelSyncManager sync) {
        EntityPlayer player = data.getPlayer();
        InventoryUiBinding binding = new InventoryUiBinding(data, ModContent.WARP_TRANSFERER);
        BooleanSupplier valid = () -> !GtfiConfig.safeMode && binding.isValid();
        ModularPanel panel = GtfiMuiTheme.panel("transferer", 234);
        GtfiMuiTheme.header(panel, "gtfi.ui.transferer");
        IntSyncValue level = new IntSyncValue(() -> TransferService.getLevel(player));
        IntSyncValue experience = new IntSyncValue(() -> TransferService.getExperience(player));
        IntSyncValue next = new IntSyncValue(() -> TransferService.getNextLevelExperience(player));
        sync.syncValue("level", level);
        sync.syncValue("experience", experience);
        sync.syncValue("next", next);
        panel.child(
            GtfiMuiTheme.label(
                () -> tr("gtfi.ui.level") + ": " + level.getIntValue() + " / 10",
                14,
                46,
                292,
                22,
                GtfiMuiTheme.TITLE));
        panel
            .child(
                GtfiMuiTheme
                    .label(
                        () -> tr("gtfi.ui.experience") + ": "
                            + experience.getIntValue()
                            + (next.getIntValue() > 0 ? " / " + next.getIntValue()
                                : " (" + tr("gtfi.ui.max_level") + ")"),
                        14,
                        72,
                        292,
                        22,
                        GtfiMuiTheme.TEXT));
        panel.child(GtfiMuiTheme.label(() -> tr("gtfi.ui.type"), 14, 102, 292, 18, GtfiMuiTheme.MUTED));
        IntSyncValue type = new IntSyncValue(
            () -> TransferService.getType(binding.current())
                .ordinal(),
            v -> {
                if (!sync.isClient() && valid.getAsBoolean()
                    && v >= 0
                    && v < WarpType.values().length
                    && TransferService.canSelect(player, WarpType.values()[v])) {
                    if (TransferService.setType(player, binding.current(), WarpType.values()[v]))
                        binding.markDirty(binding.current());
                }
            }).allowC2S();
        sync.syncValue("type", type);
        panel.child(
            new CycleButtonWidget().stateCount(3)
                .value(type)
                .pos(14, 125)
                .size(292, 26)
                .background(GtfiMuiTheme.BUTTON)
                .hoverBackground(GtfiMuiTheme.PRESSED)
                .stateOverlay(
                    0,
                    IKey.lang("gtfi.ui.type.temp")
                        .color(GtfiMuiTheme.TITLE)
                        .shadow(false))
                .stateOverlay(
                    1,
                    IKey.lang("gtfi.ui.type.normal")
                        .color(GtfiMuiTheme.TITLE)
                        .shadow(false))
                .stateOverlay(
                    2,
                    IKey.lang("gtfi.ui.type.perm")
                        .color(GtfiMuiTheme.TITLE)
                        .shadow(false)));
        panel.child(GtfiMuiTheme.label(() -> tr("gtfi.ui.unlock"), 14, 160, 292, 20, GtfiMuiTheme.MUTED));
        panel.child(GtfiMuiTheme.label(() -> tr("gtfi.ui.transfer_hint"), 14, 184, 292, 34, GtfiMuiTheme.TEXT));
        return panel;
    }
}

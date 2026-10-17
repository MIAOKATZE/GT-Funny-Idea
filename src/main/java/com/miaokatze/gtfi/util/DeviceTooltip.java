package com.miaokatze.gtfi.util;

import java.math.BigDecimal;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtfi.core.EjectorSettings;
import com.miaokatze.gtfi.core.TransferService;
import com.miaokatze.gtfi.item.ItemPortableEjector;

/** Common-side-safe device sections and the segmented GT-Funny-Idea brand footer. */
public final class DeviceTooltip {

    private DeviceTooltip() {}

    public static void block(List<String> lines) {
        state(lines);
        add(lines, "gtfi.tooltip.block.defaults");
        settings(lines, EjectorSettings.blockDefaults());
        description(
            lines,
            "gtfi.tooltip.ejector.description",
            "gtfi.tooltip.ejector.threshold",
            "gtfi.tooltip.ejector.attenuation",
            "gtfi.tooltip.block.countdown",
            "gtfi.tooltip.block.power",
            "gtfi.tooltip.block.orbit");
        controls(lines, "gtfi.tooltip.block.configure");
        finish(lines);
    }

    public static void portable(ItemStack stack, List<String> lines) {
        state(lines);
        settings(lines, ItemPortableEjector.getSettings(stack));
        description(
            lines,
            "gtfi.tooltip.ejector.description",
            "gtfi.tooltip.ejector.threshold",
            "gtfi.tooltip.ejector.attenuation",
            "gtfi.tooltip.portable.countdown",
            "gtfi.tooltip.portable.power",
            "gtfi.tooltip.portable.equipment");
        controls(lines, "gtfi.tooltip.portable.configure", "gtfi.tooltip.toggle");
        finish(lines);
    }

    public static void transferer(ItemStack stack, List<String> lines) {
        state(lines);
        String type = TransferService.getType(stack)
            .name()
            .toLowerCase(java.util.Locale.ROOT);
        lines.add(
            StatCollector.translateToLocalFormatted(
                "gtfi.tooltip.transfer.selected",
                StatCollector.translateToLocal("gtfi.warp." + type)));
        description(lines, "gtfi.tooltip.transfer.description", "gtfi.tooltip.transfer.obtain", "gtfi.ui.unlock");
        controls(
            lines,
            "gtfi.tooltip.transfer",
            "gtfi.tooltip.transfer.target",
            "gtfi.tooltip.transfer.cancel",
            "gtfi.tooltip.alt",
            "gtfi.tooltip.transfer_ui");
        finish(lines);
    }

    private static void settings(List<String> lines, EjectorSettings settings) {
        lines.add(
            (settings.enabled ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                + StatCollector.translateToLocal(settings.enabled ? "gtfi.ui.enabled" : "gtfi.ui.disabled")
                + EnumChatFormatting.RESET);
        lines.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                "gtfi.tooltip.ejector.settings_range",
                settings.threshold,
                number(settings.range)));
        lines.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                "gtfi.tooltip.ejector.settings_power",
                number(settings.power),
                settings.countdownSeconds));
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value)
            .stripTrailingZeros()
            .toPlainString();
    }

    public static void state(List<String> lines) {
        lines.add(sectionTitle("state"));
    }

    public static void description(List<String> lines, String... keys) {
        section(lines, "description", keys);
    }

    public static void controls(List<String> lines, String... keys) {
        section(lines, "controls", keys);
    }

    private static void section(List<String> lines, String name, String... keys) {
        lines.add("");
        lines.add(sectionTitle(name));
        add(lines, keys);
    }

    public static String sectionTitle(String name) {
        return ("controls".equals(name) ? EnumChatFormatting.YELLOW : EnumChatFormatting.AQUA) + ""
            + EnumChatFormatting.BOLD
            + StatCollector.translateToLocal("gtfi.tooltip.section." + name)
            + EnumChatFormatting.RESET;
    }

    public static void add(List<String> lines, String... keys) {
        for (String key : keys)
            lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(key) + EnumChatFormatting.RESET);
    }

    public static void finish(List<String> lines) {
        lines.add("");
        lines.add(addedByLine());
    }

    public static String addedByLine() {
        return EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtfi.tooltip.added_by")
            + " "
            + EnumChatFormatting.AQUA
            + "GT"
            + EnumChatFormatting.GREEN
            + "-"
            + EnumChatFormatting.GOLD
            + "Funny"
            + EnumChatFormatting.RED
            + "-"
            + EnumChatFormatting.LIGHT_PURPLE
            + "Idea"
            + EnumChatFormatting.RESET;
    }
}

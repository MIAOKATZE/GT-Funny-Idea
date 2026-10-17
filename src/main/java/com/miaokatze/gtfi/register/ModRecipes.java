package com.miaokatze.gtfi.register;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import thaumcraft.common.config.ConfigItems;

public final class ModRecipes {

    private ModRecipes() {}

    public static void register() {
        // GT205's Basic circuit tier is LV; ore matching accepts every circuit of that tier.
        GameRegistry.addRecipe(
            new ShapedOreRecipe(
                new ItemStack(ModContent.WARP_EJECTOR),
                "ECE",
                "CTC",
                "ECE",
                'E',
                ItemList.Emitter_LV.get(1),
                'C',
                "circuitBasic",
                'T',
                "blockThaumium"));
        // Thaumcraft 4's Mundane Amulet is ItemBaubleBlanks metadata zero.
        GameRegistry.addRecipe(
            new ShapelessOreRecipe(
                new ItemStack(ModContent.PORTABLE_EJECTOR),
                new ItemStack(ConfigItems.itemBaubleBlanks, 1, 0),
                new ItemStack(ModContent.WARP_EJECTOR)));
    }
}

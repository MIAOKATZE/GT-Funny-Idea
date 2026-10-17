package com.miaokatze.gtfi.testlab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.WorldServer;
import net.minecraftforge.oredict.OreDictionary;

import com.miaokatze.gtfi.block.TileWarpEjector;
import com.miaokatze.gtfi.config.GtfiConfig;
import com.miaokatze.gtfi.register.ModContent;
import com.miaokatze.gtfi.testlab.TestScenarios.Context;

import gregtech.api.enums.ItemList;
import thaumcraft.common.config.ConfigItems;
import thaumcraft.common.entities.EntitySpecialItem;

/** Native registered crafting and TC4 wand behavior inside the bounded fixture. */
final class NativeScenarios {

    static List<TestScenarios.Scenario> all() {
        return Arrays.<TestScenarios.Scenario>asList(new TestScenarios.Case("recipes.native_crafting") {

            public boolean tick(Context c, int t) {
                List<ItemStack> circuits = OreDictionary.getOres("circuitBasic");
                c.check(circuits.size() >= 2, "runtime LV ore dictionary offers at least two circuit variants");
                ItemStack thaumium = OreDictionary.getOres("blockThaumium")
                    .get(0);
                for (int variant = 0; variant < 2; variant++) {
                    InventoryCrafting grid = grid();
                    for (int slot : new int[] { 0, 2, 6, 8 })
                        grid.setInventorySlotContents(slot, ItemList.Emitter_LV.get(1));
                    for (int slot : new int[] { 1, 3, 5, 7 }) grid.setInventorySlotContents(
                        slot,
                        circuits.get(variant)
                            .copy());
                    grid.setInventorySlotContents(4, thaumium.copy());
                    c.check(
                        matches(grid, Item.getItemFromBlock(ModContent.WARP_EJECTOR), c.world),
                        "registered native recipe accepts LV circuit variant " + variant);
                    if (variant == 0) {
                        List<ItemStack> mv = OreDictionary.getOres("circuitGood");
                        c.check(!mv.isEmpty(), "runtime MV ore dictionary exists");
                        grid.setInventorySlotContents(
                            1,
                            mv.get(0)
                                .copy());
                        c.check(
                            !matches(grid, Item.getItemFromBlock(ModContent.WARP_EJECTOR), c.world),
                            "registered recipe rejects MV replacement");
                    }
                }
                InventoryCrafting portable = grid();
                portable.setInventorySlotContents(2, new ItemStack(ModContent.WARP_EJECTOR));
                portable.setInventorySlotContents(6, new ItemStack(ConfigItems.itemBaubleBlanks, 1, 0));
                c.check(
                    matches(portable, ModContent.PORTABLE_EJECTOR, c.world),
                    "native portable shapeless recipe accepts mundane amulet meta zero");
                portable.setInventorySlotContents(6, null);
                portable.setInventorySlotContents(0, new ItemStack(ConfigItems.itemBaubleBlanks, 1, 0));
                c.check(
                    matches(portable, ModContent.PORTABLE_EJECTOR, c.world),
                    "portable recipe accepts reordered ingredients");
                portable.setInventorySlotContents(0, new ItemStack(ConfigItems.itemBaubleBlanks, 1, 1));
                c.check(
                    !matches(portable, ModContent.PORTABLE_EJECTOR, c.world),
                    "portable recipe rejects other amulet metadata");
                return true;
            }
        }, new TestScenarios.Case("wand.native_special_item") {

            public boolean tick(Context c, int t) {
                c.world.setBlock(4, 65, 0, ModContent.WARP_EJECTOR, 0, 3);
                TileWarpEjector tile = (TileWarpEjector) c.world.getTileEntity(4, 65, 0);
                tile.getSettings().enabled = false;
                ItemStack wand = new ItemStack(ConfigItems.itemWandCasting);
                boolean original = GtfiConfig.safeMode;
                try {
                    GtfiConfig.safeMode = true;
                    c.eq(
                        ModContent.WARP_EJECTOR.onWandRightClick(c.world, wand, c.owner, 4, 65, 0, 1, 0),
                        0,
                        "safeMode rejects actual IWandable callback");
                    c.check(c.world.getBlock(4, 65, 0) == ModContent.WARP_EJECTOR, "safeMode preserves source block");
                    GtfiConfig.safeMode = false;
                    c.eq(
                        ModContent.WARP_EJECTOR.onWandRightClick(c.world, wand, c.owner, 4, 65, 0, 1, 0),
                        1,
                        "actual Thaumcraft wand callback performs conversion");
                    c.check(c.world.isAirBlock(4, 65, 0), "successful conversion consumes source block");
                    int count = 0;
                    for (Object entity : new ArrayList<Object>(c.world.loadedEntityList)) {
                        if (entity instanceof EntitySpecialItem) {
                            EntitySpecialItem special = (EntitySpecialItem) entity;
                            if (special.getEntityItem()
                                .getItem() == ModContent.WARP_TRANSFERER) {
                                count++;
                                special.setDead();
                                c.world.removeEntity(special);
                            }
                        }
                    }
                    c.eq(count, 1, "exactly one native Thaumcraft EntitySpecialItem carries converter");
                } finally {
                    GtfiConfig.safeMode = original;
                }
                return true;
            }
        });
    }

    private static InventoryCrafting grid() {
        return new InventoryCrafting(new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return false;
            }
        }, 3, 3);
    }

    private static boolean matches(InventoryCrafting grid, Item output, WorldServer world) {
        for (Object object : CraftingManager.getInstance()
            .getRecipeList()) {
            IRecipe recipe = (IRecipe) object;
            ItemStack result = recipe.getRecipeOutput();
            if (result != null && result.getItem() == output && recipe.matches(grid, world)) return true;
        }
        return false;
    }
}

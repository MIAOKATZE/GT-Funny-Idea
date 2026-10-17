package com.miaokatze.gtfi.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtfi.util.DeviceTooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Retains the normal ItemBlock identity and placement behavior while adding device information. */
public final class ItemBlockWarpEjector extends ItemBlock {

    public ItemBlockWarpEjector(Block block) {
        super(block);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        DeviceTooltip.block(list);
    }
}

package com.miaokatze.gtfi.client;

import net.minecraft.item.Item;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.miaokatze.gtfi.register.ModContent;
import com.miaokatze.gtfi.util.DeviceTooltipOrder;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Normalize only our three devices after other item tooltip providers have added their details. */
@SideOnly(Side.CLIENT)
public final class DeviceTooltipHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTooltip(ItemTooltipEvent event) {
        if (event.itemStack == null) return;
        Item item = event.itemStack.getItem();
        if (item == ModContent.PORTABLE_EJECTOR || item == ModContent.WARP_TRANSFERER
            || item == Item.getItemFromBlock(ModContent.WARP_EJECTOR)) DeviceTooltipOrder.normalize(event.toolTip);
    }
}

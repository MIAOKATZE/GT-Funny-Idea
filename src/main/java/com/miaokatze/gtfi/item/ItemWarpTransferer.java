package com.miaokatze.gtfi.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtfi.config.GtfiConfig;
import com.miaokatze.gtfi.core.TransferService;
import com.miaokatze.gtfi.network.GtfiNetwork;
import com.miaokatze.gtfi.ui.GtfiUI;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemWarpTransferer extends Item implements IGuiHolder<PlayerInventoryGuiData> {

    public ItemWarpTransferer() {
        setUnlocalizedName("gtfi.warp_transferer");
        setTextureName("gtfi:warp_transferer");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabMisc);
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.bow;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && GtfiConfig.safeMode) {
            player.addChatMessage(new ChatComponentTranslation("gtfi.message.transfer_disabled"));
            return stack;
        }
        if (player.isSneaking()) {
            if (!world.isRemote) PlayerInventoryGuiFactory.INSTANCE.openFromMainHand(player);
        } else player.setItemInUse(stack, getMaxItemUseDuration(stack));
        return stack;
    }

    @Override
    public void onUsingTick(ItemStack stack, EntityPlayer player, int count) {
        if (!player.worldObj.isRemote) {
            if (GtfiConfig.safeMode) {
                player.stopUsingItem();
                TransferService.INSTANCE.stopUsing(player);
            } else TransferService.INSTANCE.onUsingTick(player, stack, GtfiNetwork.isAltPressed(player));
        }
    }

    @Override
    public void onPlayerStoppedUsing(ItemStack stack, World world, EntityPlayer player, int remaining) {
        if (!world.isRemote) TransferService.INSTANCE.stopUsing(player);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(StatCollector.translateToLocal("gtfi.tooltip.transfer"));
        list.add(StatCollector.translateToLocal("gtfi.tooltip.alt"));
        list.add(StatCollector.translateToLocal("gtfi.tooltip.transfer_ui"));
    }

    @Override
    public ModularPanel buildUI(PlayerInventoryGuiData data, PanelSyncManager sync, UISettings settings) {
        return GtfiUI.transferer(data, sync);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(PlayerInventoryGuiData data, ModularPanel panel) {
        return new ModularScreen("gtfi", panel);
    }
}

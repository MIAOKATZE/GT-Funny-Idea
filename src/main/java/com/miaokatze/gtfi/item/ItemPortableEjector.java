package com.miaokatze.gtfi.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtfi.config.GtfiConfig;
import com.miaokatze.gtfi.core.EjectorSettings;
import com.miaokatze.gtfi.core.WarpService;
import com.miaokatze.gtfi.ui.GtfiUI;
import com.miaokatze.gtfi.util.DeviceTooltip;

import baubles.api.BaubleType;
import baubles.api.expanded.BaubleExpandedSlots;
import baubles.api.expanded.IBaubleExpanded;
import baubles.common.lib.PlayerHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemPortableEjector extends Item implements IBaubleExpanded, IGuiHolder<PlayerInventoryGuiData> {

    public ItemPortableEjector() {
        setUnlocalizedName("gtfi.portable_ejector");
        setTextureName("gtfi:portable_ejector");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabMisc);
    }

    public static EjectorSettings getSettings(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? EjectorSettings.portableDefaults()
            : EjectorSettings.read(tag.getCompoundTag("gtfiSettings"), true, GtfiConfig.safeMode);
    }

    public static void setSettings(ItemStack stack, EjectorSettings settings) {
        settings.sanitize(true, GtfiConfig.safeMode);
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound values = new NBTTagCompound();
        settings.write(values);
        stack.getTagCompound()
            .setTag("gtfiSettings", values);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            if (player.isSneaking()) {
                EjectorSettings settings = getSettings(stack);
                settings.enabled = !settings.enabled;
                setSettings(stack, settings);
                player.inventory.markDirty();
            } else PlayerInventoryGuiFactory.INSTANCE.openFromMainHand(player);
        }
        return stack;
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        if (!world.isRemote && entity instanceof EntityPlayer) {
            updateSource(stack, (EntityPlayer) entity, "inventory:" + slot);
        }
    }

    private void updateSource(ItemStack stack, EntityPlayer player, String slot) {
        String id = "portable:" + player.getUniqueID() + ":" + slot;
        EjectorSettings settings = getSettings(stack);
        if (settings.enabled)
            WarpService.INSTANCE.offerSource(id, player.worldObj, player.posX, player.posY, player.posZ, settings);
        else WarpService.INSTANCE.removeSource(id);
    }

    @Override
    public BaubleType getBaubleType(ItemStack stack) {
        return BaubleType.AMULET;
    }

    @Override
    public String[] getBaubleTypes(ItemStack stack) {
        return new String[] { BaubleExpandedSlots.amuletType };
    }

    @Override
    public boolean canEquip(ItemStack stack, EntityLivingBase player) {
        return true;
    }

    @Override
    public boolean canUnequip(ItemStack stack, EntityLivingBase player) {
        return true;
    }

    @Override
    public void onEquipped(ItemStack stack, EntityLivingBase player) {
        onWornTick(stack, player);
    }

    @Override
    public void onUnequipped(ItemStack stack, EntityLivingBase player) {
        if (player.worldObj.isRemote || !(player instanceof EntityPlayer)) return;
        IInventory baubles = PlayerHandler.getPlayerBaubles((EntityPlayer) player);
        for (int slot = 0; slot < baubles.getSizeInventory(); slot++) {
            ItemStack current = baubles.getStackInSlot(slot);
            // setInventorySlotContents calls before replacement; decrStackSize calls after removal.
            if (current == stack || current == null || current.getItem() != this)
                WarpService.INSTANCE.removeSource("portable:" + player.getUniqueID() + ":bauble:" + slot);
        }
    }

    @Override
    public void onWornTick(ItemStack stack, EntityLivingBase player) {
        if (player.worldObj.isRemote || !(player instanceof EntityPlayer)) return;
        EntityPlayer wearer = (EntityPlayer) player;
        IInventory baubles = PlayerHandler.getPlayerBaubles(wearer);
        for (int slot = 0; slot < baubles.getSizeInventory(); slot++) {
            if (baubles.getStackInSlot(slot) == stack) {
                updateSource(stack, wearer, "bauble:" + slot);
                return;
            }
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        DeviceTooltip.portable(stack, list);
    }

    @Override
    public ModularPanel buildUI(PlayerInventoryGuiData data, PanelSyncManager sync, UISettings settings) {
        return GtfiUI.portable(data, sync);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(PlayerInventoryGuiData data, ModularPanel panel) {
        return new ModularScreen("gtfi", panel);
    }
}

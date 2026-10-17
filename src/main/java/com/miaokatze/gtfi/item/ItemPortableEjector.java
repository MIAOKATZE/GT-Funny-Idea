package com.miaokatze.gtfi.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
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
import com.miaokatze.gtfi.core.EjectorSettings;
import com.miaokatze.gtfi.core.WarpService;
import com.miaokatze.gtfi.ui.GtfiUI;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemPortableEjector extends Item implements IBauble, IGuiHolder<PlayerInventoryGuiData> {

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
    public boolean canEquip(ItemStack stack, EntityLivingBase player) {
        return true;
    }

    @Override
    public boolean canUnequip(ItemStack stack, EntityLivingBase player) {
        return true;
    }

    @Override
    public void onEquipped(ItemStack stack, EntityLivingBase player) {}

    @Override
    public void onUnequipped(ItemStack stack, EntityLivingBase player) {
        if (!player.worldObj.isRemote && player instanceof EntityPlayer)
            WarpService.INSTANCE.removeSource("portable:" + player.getUniqueID() + ":amulet");
    }

    @Override
    public void onWornTick(ItemStack stack, EntityLivingBase player) {
        if (!player.worldObj.isRemote && player instanceof EntityPlayer)
            updateSource(stack, (EntityPlayer) player, "amulet");
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(StatCollector.translateToLocal("gtfi.tooltip.portable"));
        list.add(StatCollector.translateToLocal("gtfi.tooltip.toggle"));
        list.add(StatCollector.translateToLocal(getSettings(stack).enabled ? "gtfi.ui.enabled" : "gtfi.ui.disabled"));
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

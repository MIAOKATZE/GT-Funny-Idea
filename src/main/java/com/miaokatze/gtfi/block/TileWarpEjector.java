package com.miaokatze.gtfi.block;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtfi.config.GtfiConfig;
import com.miaokatze.gtfi.core.EjectorSettings;
import com.miaokatze.gtfi.core.WarpService;
import com.miaokatze.gtfi.ui.GtfiUI;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class TileWarpEjector extends TileEntity implements IGuiHolder<PosGuiData> {

    private EjectorSettings settings = EjectorSettings.blockDefaults();
    private UUID owner;

    public EjectorSettings getSettings() {
        return settings;
    }

    public void setSettings(EjectorSettings value) {
        value.sanitize(false, GtfiConfig.safeMode);
        settings = value;
        markDirty();
    }

    public void setOwner(EntityPlayer player) {
        owner = player.getUniqueID();
        markDirty();
    }

    public UUID getOwner() {
        return owner;
    }

    public boolean canEdit(EntityPlayer player) {
        return !GtfiConfig.safeMode || player.getUniqueID()
            .equals(owner) || player.canCommandSenderUseCommand(2, "gtfi");
    }

    public String sourceId() {
        return "block:" + worldObj.provider.dimensionId + ":" + xCoord + ":" + yCoord + ":" + zCoord;
    }

    @Override
    public void updateEntity() {
        if (!worldObj.isRemote) {
            settings.sanitize(false, GtfiConfig.safeMode);
            if (settings.enabled) WarpService.INSTANCE
                .offerSource(sourceId(), worldObj, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, settings);
            else WarpService.INSTANCE.removeSource(sourceId());
        }
    }

    @Override
    public void invalidate() {
        if (worldObj != null && !worldObj.isRemote) WarpService.INSTANCE.removeSource(sourceId());
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        if (worldObj != null && !worldObj.isRemote) WarpService.INSTANCE.removeSource(sourceId());
        super.onChunkUnload();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagCompound values = new NBTTagCompound();
        settings.write(values);
        tag.setTag("gtfiSettings", values);
        if (owner != null) tag.setString("gtfiOwner", owner.toString());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        settings = EjectorSettings.read(tag.getCompoundTag("gtfiSettings"), false, GtfiConfig.safeMode);
        try {
            owner = UUID.fromString(tag.getString("gtfiOwner"));
        } catch (IllegalArgumentException ignored) {
            owner = null;
        }
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager sync, UISettings uiSettings) {
        return GtfiUI.ejector(data.getPlayer(), sync, this);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel panel) {
        return new ModularScreen("gtfi", panel);
    }
}

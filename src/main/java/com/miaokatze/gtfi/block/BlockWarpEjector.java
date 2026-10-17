package com.miaokatze.gtfi.block;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.TileEntityGuiFactory;
import com.miaokatze.gtfi.config.GtfiConfig;
import com.miaokatze.gtfi.register.ModContent;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import thaumcraft.api.wands.IWandable;
import thaumcraft.common.entities.EntitySpecialItem;
import thaumcraft.common.items.wands.ItemWandCasting;
import thaumcraft.common.lib.network.PacketHandler;
import thaumcraft.common.lib.network.fx.PacketFXBlockSparkle;

public class BlockWarpEjector extends BlockContainer implements IWandable {

    public BlockWarpEjector() {
        super(Material.iron);
        setBlockName("gtfi.warp_ejector");
        setBlockTextureName("gtfi:warp_ejector");
        setHardness(50.0F);
        setResistance(2000.0F);
        setHarvestLevel("pickaxe", 2);
        setCreativeTab(CreativeTabs.tabMisc);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileWarpEjector();
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote && placer instanceof EntityPlayer
            && world.getTileEntity(x, y, z) instanceof TileWarpEjector) {
            ((TileWarpEjector) world.getTileEntity(x, y, z)).setOwner((EntityPlayer) placer);
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof ItemWandCasting) return false;
        if (!world.isRemote && world.getTileEntity(x, y, z) instanceof TileWarpEjector) {
            TileEntityGuiFactory.INSTANCE.open(player, (TileWarpEjector) world.getTileEntity(x, y, z));
        }
        return true;
    }

    /** Reuses Thaumcraft's original special-item entity and its book-creation FX packet. */
    public boolean grantTransferer(World world, int x, int y, int z, EntityPlayer player, ItemStack wandStack) {
        if (world.isRemote || GtfiConfig.safeMode
            || wandStack == null
            || !(wandStack.getItem() instanceof ItemWandCasting)) return false;
        TileEntity raw = world.getTileEntity(x, y, z);
        if (!(raw instanceof TileWarpEjector)) return false;
        EntitySpecialItem item = new EntitySpecialItem(
            world,
            x + 0.5,
            y + 0.3,
            z + 0.5,
            new ItemStack(ModContent.WARP_TRANSFERER));
        item.motionX = item.motionY = item.motionZ = 0;
        if (!world.spawnEntityInWorld(item)) return false;
        if (!world.setBlockToAir(x, y, z)) {
            item.setDead();
            return false;
        }
        PacketHandler.INSTANCE.sendToAllAround(
            new PacketFXBlockSparkle(x, y, z, -9999),
            new TargetPoint(world.provider.dimensionId, x, y, z, 32));
        world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "thaumcraft:wand", 1, 1);
        return true;
    }

    @Override
    public int onWandRightClick(World world, ItemStack wand, EntityPlayer player, int x, int y, int z, int side,
        int metadata) {
        return grantTransferer(world, x, y, z, player, wand) ? 1 : 0;
    }

    @Override
    public ItemStack onWandRightClick(World world, ItemStack stack, EntityPlayer player) {
        return stack;
    }

    @Override
    public void onUsingWandTick(ItemStack stack, EntityPlayer player, int count) {}

    @Override
    public void onWandStoppedUsing(ItemStack stack, World world, EntityPlayer player, int count) {}
}

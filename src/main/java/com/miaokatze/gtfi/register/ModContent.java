package com.miaokatze.gtfi.register;

import com.miaokatze.gtfi.GTFunnyIdea;
import com.miaokatze.gtfi.block.BlockWarpEjector;
import com.miaokatze.gtfi.block.TileWarpEjector;
import com.miaokatze.gtfi.item.ItemPortableEjector;
import com.miaokatze.gtfi.item.ItemWarpTransferer;

import cpw.mods.fml.common.registry.GameRegistry;

/** The single registration owner for GT-Funny-Idea blocks, items and tile entities. */
public final class ModContent {

    public static final BlockWarpEjector WARP_EJECTOR = new BlockWarpEjector();
    public static final ItemPortableEjector PORTABLE_EJECTOR = new ItemPortableEjector();
    public static final ItemWarpTransferer WARP_TRANSFERER = new ItemWarpTransferer();

    private ModContent() {}

    public static void register() {
        GameRegistry.registerBlock(WARP_EJECTOR, "warp_ejector");
        GameRegistry.registerItem(PORTABLE_EJECTOR, "portable_ejector");
        GameRegistry.registerItem(WARP_TRANSFERER, "warp_transferer");
        GameRegistry.registerTileEntity(TileWarpEjector.class, GTFunnyIdea.MOD_ID + ":warp_ejector");
    }
}

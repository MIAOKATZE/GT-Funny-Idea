package com.miaokatze.gtfi.proxy;

import com.miaokatze.gtfi.core.WarpService;
import com.miaokatze.gtfi.network.GtfiNetwork;
import com.miaokatze.gtfi.register.ModContent;
import com.miaokatze.gtfi.register.ModRecipes;
import com.miaokatze.gtfi.testlab.GtfiTestLab;
import com.miaokatze.gtfi.ui.GtfiUI;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;

/** Common lifecycle. This class must never link Minecraft client classes. */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        ModContent.register();
        GtfiTestLab.preInit();
    }

    public void init(FMLInitializationEvent event) {
        WarpService.INSTANCE.register();
        GtfiNetwork.init();
        GtfiUI.init();
        ModRecipes.register();
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        GtfiTestLab.serverStarting(event);
    }

    public void serverStopped(FMLServerStoppedEvent event) {
        GtfiTestLab.serverStopped();
        WarpService.INSTANCE.clear();
        GtfiNetwork.clearServerState();
    }
}

package com.miaokatze.gtfi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtfi.config.GtfiConfig;
import com.miaokatze.gtfi.proxy.CommonProxy;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;

/** GT-Funny-Idea lifecycle entry point. */
@Mod(
    modid = GTFunnyIdea.MOD_ID,
    name = GTFunnyIdea.MOD_NAME,
    version = GTFunnyIdea.VERSION,
    dependencies = "required-after:miaokatzecore@[0.1.3,0.2.0);required-after:gregtech;required-after:modularui2@[2.3.92-1.7.10,);required-after:Thaumcraft@[4.2.3.5,);required-after:Baubles")
public final class GTFunnyIdea {

    public static final String MOD_ID = "gtfi";
    public static final String MOD_NAME = "GT-Funny-Idea";
    public static final String VERSION = "v" + Tags.RELEASE_VERSION;
    public static final Logger LOG = LogManager.getLogger(MOD_ID);

    @Mod.Instance(MOD_ID)
    public static GTFunnyIdea INSTANCE;

    @SidedProxy(
        clientSide = "com.miaokatze.gtfi.proxy.ClientProxy",
        serverSide = "com.miaokatze.gtfi.proxy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        GtfiConfig.load(event.getSuggestedConfigurationFile());
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        proxy.serverStopped(event);
    }
}

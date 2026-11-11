// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtfi.proxy;

import com.miaokatze.gtfi.core.WarpService;
import com.miaokatze.gtfi.integration.TestlabBootstrap;
import com.miaokatze.gtfi.network.GtfiNetwork;
import com.miaokatze.gtfi.register.ModContent;
import com.miaokatze.gtfi.register.ModRecipes;
import com.miaokatze.gtfi.ui.GtfiUI;
import com.miaokatze.miaokatzecore.api.recipe.RecipeRegistration;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

/** Common lifecycle. This class must never link Minecraft client classes. */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        ModContent.register();
        TestlabBootstrap.preInit();
    }

    public void init(FMLInitializationEvent event) {
        WarpService.INSTANCE.register();
        GtfiNetwork.init();
        GtfiUI.init();
        RecipeRegistration.register("gtfi", ModRecipes::register);
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        TestlabBootstrap.serverStarting(event);
    }

    public void serverStopped(FMLServerStoppedEvent event) {
        try {
            TestlabBootstrap.serverStopped();
        } finally {
            WarpService.INSTANCE.clear();
            GtfiNetwork.clearServerState();
        }
    }

    public void serverStopping(FMLServerStoppingEvent event) {
        TestlabBootstrap.serverStopping();
    }
}

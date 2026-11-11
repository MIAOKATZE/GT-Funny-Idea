// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtfi.testlab;

import net.minecraft.entity.player.EntityPlayer;

import com.miaokatze.gtfi.client.ClientSmoke;
import com.miaokatze.gtfi.integration.TestlabBootstrap;

import cpw.mods.fml.common.event.FMLServerStartingEvent;

/** Loaded only after an explicit opt-in and only in the complete replacement jar. */
public final class TestlabDriver implements TestlabBootstrap.Driver {

    public void preInit() {
        GtfiTestLab.preInit();
    }

    public void serverStarting(FMLServerStartingEvent event) {
        GtfiTestLab.serverStarting(event);
    }

    public void serverStopped() {
        GtfiTestLab.serverStopped();
    }

    public void serverStopping() {
        GtfiTestLab.serverStopping();
    }

    public void installClient() {
        ClientSmoke.install();
    }

    public boolean ownsActor(EntityPlayer player) {
        return GtfiTestLab.isSyntheticActor(player);
    }
}

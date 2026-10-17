package com.miaokatze.gtfi.proxy;

import com.miaokatze.gtfi.client.ClientRuntime;

import cpw.mods.fml.common.event.FMLInitializationEvent;

/** Client lifecycle extension. Client-only initialization belongs here. */
public final class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ClientRuntime.init();
    }
}

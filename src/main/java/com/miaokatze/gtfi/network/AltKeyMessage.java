package com.miaokatze.gtfi.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/** The only C2S input is the physical modifier state; no gameplay values or target IDs. */
public final class AltKeyMessage implements IMessage {

    public boolean pressed;

    public AltKeyMessage() {}

    public AltKeyMessage(boolean pressed) {
        this.pressed = pressed;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pressed = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(pressed);
    }
}

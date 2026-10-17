package com.miaokatze.gtfi.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/** Server-authored presentation data. This class deliberately has no client references. */
public final class VisualMessage implements IMessage {

    public static final int COUNTDOWN = 0;
    public static final int PROGRESS = 1;
    public static final int FLIGHT = 2;
    public static final int TRANSFER = 3;
    public static final int LEVEL = 4;

    public int kind, dimension, entity, value, total, extra;
    public double x, y, z, targetX, targetY, targetZ;
    public boolean active;

    public VisualMessage() {}

    public VisualMessage(int kind, int dimension, int entity) {
        this.kind = kind;
        this.dimension = dimension;
        this.entity = entity;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        kind = buf.readUnsignedByte();
        dimension = buf.readInt();
        entity = buf.readInt();
        value = buf.readInt();
        total = buf.readInt();
        extra = buf.readInt();
        active = buf.readBoolean();
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        targetX = buf.readDouble();
        targetY = buf.readDouble();
        targetZ = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(kind);
        buf.writeInt(dimension);
        buf.writeInt(entity);
        buf.writeInt(value);
        buf.writeInt(total);
        buf.writeInt(extra);
        buf.writeBoolean(active);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(targetX);
        buf.writeDouble(targetY);
        buf.writeDouble(targetZ);
    }
}

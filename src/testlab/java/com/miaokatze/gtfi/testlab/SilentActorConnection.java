package com.miaokatze.gtfi.testlab;

import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

/** Memory-only outbound sink for synthetic actors; no socket, login or network registration. */
final class SilentActorConnection extends NetHandlerPlayServer {

    SilentActorConnection(FakePlayer actor) {
        super(MinecraftServer.getServer(), new ActorNetwork(), actor);
    }

    @Override
    public void sendPacket(Packet packet) {
        // A FakePlayer has no client. Keep normal server interactions and discard only their outbound packets.
    }

    /** FML's PLAYER target inspects channel attributes even for a FakePlayer. An absent dispatcher drops it. */
    private static final class ActorNetwork extends NetworkManager {

        private final EmbeddedChannel sink = new EmbeddedChannel(new ChannelInboundHandlerAdapter());

        ActorNetwork() {
            super(false);
        }

        @Override
        public Channel channel() {
            return sink;
        }
    }
}

package com.miaokatze.gtfi.testlab;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import com.mojang.authlib.GameProfile;

/** Stable synthetic identities. Captures outbound business observations without any socket or login. */
final class TestActor extends FakePlayer {

    final List<String> messages = new ArrayList<String>();
    float lastImpact;
    int impacts;

    TestActor(WorldServer world, String role) {
        super(
            world,
            new GameProfile(
                UUID.nameUUIDFromBytes(("gtfi-testlab:" + role).getBytes(StandardCharsets.UTF_8)),
                "GTFI_" + role));
        dimension = world.provider.dimensionId;
        new SilentActorConnection(this);
        setPosition(0.5, 65, 0.5);
        world.playerEntities.add(this);
    }

    @Override
    public void addChatMessage(IChatComponent message) {
        messages.add(message.getUnformattedText());
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        // Forge FakePlayer normally refuses all damage. Observe the real production impact request;
        // this is intentionally not a health/death assertion against an invulnerable synthetic actor.
        if ("gtfiImpact".equals(source.damageType)) {
            lastImpact = amount;
            impacts++;
        }
        return super.attackEntityFrom(source, amount);
    }
}

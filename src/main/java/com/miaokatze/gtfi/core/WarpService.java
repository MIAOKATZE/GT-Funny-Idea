package com.miaokatze.gtfi.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.miaokatze.gtfi.config.GtfiConfig;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** All schedules, cooldowns and flight vectors are owned by the server tick thread. */
public final class WarpService {

    public static final WarpService INSTANCE = new WarpService();

    public interface Hooks {

        void countdown(EntityPlayerMP player, int ticks);

        void flight(EntityPlayerMP player, double speedMetersPerSecond, boolean active);
    }

    private static final Hooks NO_HOOKS = new Hooks() {

        public void countdown(EntityPlayerMP player, int ticks) {}

        public void flight(EntityPlayerMP player, double speed, boolean active) {}
    };
    private Hooks hooks = NO_HOOKS;
    private boolean registered;
    private long tick;
    private final Map<String, Source> sources = new HashMap<String, Source>();
    private final Map<UUID, Map<String, Long>> deadlines = new HashMap<UUID, Map<String, Long>>();
    private final Map<UUID, Long> cooldowns = new HashMap<UUID, Long>();
    private final Map<UUID, Flight> flights = new HashMap<UUID, Flight>();
    private final Map<UUID, Integer> shownCountdowns = new HashMap<UUID, Integer>();
    private static final DamageSource IMPACT = new DamageSource("gtfiImpact").setDamageBypassesArmor()
        .setDamageIsAbsolute()
        .setDamageAllowedInCreativeMode();

    private WarpService() {}

    public void setHooks(Hooks hooks) {
        this.hooks = hooks == null ? NO_HOOKS : hooks;
    }

    public void register() {
        if (!registered) {
            FMLCommonHandler.instance()
                .bus()
                .register(this);
            MinecraftForge.EVENT_BUS.register(this);
            TransferService.INSTANCE.register();
            registered = true;
        }
    }

    /** Sources are leases: callers must refresh them every server tick. */
    public void offerSource(String id, World world, double x, double y, double z, EjectorSettings settings) {
        if (world == null || world.isRemote || id == null || settings == null) return;
        EjectorSettings safe = settings.copy()
            .sanitize(settings.portable, GtfiConfig.safeMode);
        if (!safe.enabled) {
            removeSource(id);
            return;
        }
        Source old = sources.get(id);
        Source source = new Source(world, x, y, z, safe, tick);
        sources.put(id, source);
        // Configuration changes restart a countdown; moving portable sources do not.
        if (old != null && (old.world != world || old.settings.countdownSeconds != safe.countdownSeconds
            || old.settings.threshold != safe.threshold)) cancelSource(id);
    }

    public void removeSource(String id) {
        sources.remove(id);
        cancelSource(id);
    }

    private void cancelSource(String id) {
        for (Map<String, Long> pending : deadlines.values()) pending.remove(id);
    }

    public int getCountdownTicks(EntityPlayer player) {
        Integer ticks = shownCountdowns.get(player.getUniqueID());
        return ticks == null ? -1 : ticks;
    }

    public boolean isFlying(EntityPlayer player) {
        return flights.containsKey(player.getUniqueID());
    }

    public int getCooldownTicks(EntityPlayer player) {
        Long deadline = cooldowns.get(player.getUniqueID());
        return deadline == null ? 0 : (int) Math.max(0, deadline - tick);
    }

    public double getSpeedMetersPerSecond(EntityPlayer player) {
        Flight flight = flights.get(player.getUniqueID());
        return flight == null ? 0 : FlightMath.speedMetersPerSecond(flight.x, flight.y, flight.z);
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick++;
        Iterator<Map.Entry<String, Source>> sourceIterator = sources.entrySet()
            .iterator();
        while (sourceIterator.hasNext()) {
            Map.Entry<String, Source> entry = sourceIterator.next();
            if (entry.getValue().lastSeen < tick - 1) {
                cancelSource(entry.getKey());
                sourceIterator.remove();
            }
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        Set<UUID> online = new HashSet<UUID>();
        List<Object> players = new ArrayList<Object>();
        for (World world : DimensionManager.getWorlds()) players.addAll(new ArrayList<Object>(world.playerEntities));
        for (Object entry : players) {
            if (!(entry instanceof EntityPlayerMP)) continue;
            EntityPlayerMP player = (EntityPlayerMP) entry;
            online.add(player.getUniqueID());
            Flight flight = flights.get(player.getUniqueID());
            if (flight != null) advanceFlight(player, flight);
            updateCountdown(player);
        }
        deadlines.keySet()
            .retainAll(online);
        shownCountdowns.keySet()
            .retainAll(online);
        flights.keySet()
            .retainAll(online);
        Iterator<Long> cooldownIterator = cooldowns.values()
            .iterator();
        while (cooldownIterator.hasNext()) if (cooldownIterator.next() <= tick) cooldownIterator.remove();
    }

    private void updateCountdown(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        if (player.isDead || !player.isEntityAlive()
            || ThaumcraftWarp.isWarded(player)
            || getCooldownTicks(player) > 0) {
            deadlines.remove(id);
            showCountdown(player, -1);
            return;
        }
        Map<String, Long> pending = deadlines.get(id);
        if (pending == null) {
            pending = new HashMap<String, Long>();
            deadlines.put(id, pending);
        }
        Set<String> active = new HashSet<String>();
        long total = ThaumcraftWarp.total(player);
        Source first = null;
        long earliest = Long.MAX_VALUE;
        for (Map.Entry<String, Source> entry : sources.entrySet()) {
            Source source = entry.getValue();
            if (source.world != player.worldObj || total <= source.settings.threshold
                || player.getDistanceSq(source.x, source.y, source.z) > source.settings.range * source.settings.range)
                continue;
            active.add(entry.getKey());
            Long deadline = pending.get(entry.getKey());
            if (deadline == null) {
                deadline = tick + source.settings.countdownSeconds * 20L;
                pending.put(entry.getKey(), deadline);
            }
            if (deadline < earliest) {
                earliest = deadline;
                first = source;
            }
        }
        pending.keySet()
            .retainAll(active);
        if (first == null) {
            deadlines.remove(id);
            showCountdown(player, -1);
            return;
        }
        if (earliest <= tick) {
            launch(player, first);
            deadlines.remove(id);
            cooldowns.put(id, tick + 100);
            showCountdown(player, -1);
        } else showCountdown(player, (int) (earliest - tick));
    }

    private void showCountdown(EntityPlayerMP player, int remaining) {
        Integer old = shownCountdowns.get(player.getUniqueID());
        if (remaining < 0) shownCountdowns.remove(player.getUniqueID());
        else shownCountdowns.put(player.getUniqueID(), remaining);
        if (old != null || remaining >= 0) hooks.countdown(player, remaining);
    }

    private void launch(EntityPlayerMP player, Source source) {
        if (player.ridingEntity != null) player.mountEntity(null);
        Flight previous = flights.remove(player.getUniqueID());
        if (previous != null) restoreFlight(player, previous);
        AxisAlignedBB body = player.boundingBox;
        double dx = (body.minX + body.maxX) * 0.5 - source.x;
        double dy = (body.minY + body.maxY) * 0.5 - source.y;
        double dz = (body.minZ + body.maxZ) * 0.5 - source.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double length = distance;
        if (length < 1.0E-6) {
            dx = 0;
            dy = 1;
            dz = 0;
            length = 1;
        }
        double speed = FlightMath.launchSpeed(source.settings.power, distance, source.settings.range) / 20;
        Flight flight = new Flight(
            player,
            dx / length * speed,
            dy / length * speed,
            dz / length * speed,
            source.settings.damageFactor);
        flights.put(player.getUniqueID(), flight);
        player.capabilities.isFlying = false;
        player.sendPlayerAbilities();
        player.fallDistance = 0;
        player.motionX = player.motionY = player.motionZ = 0;
        hooks.flight(player, speed * 20, true);
    }

    private void advanceFlight(EntityPlayerMP player, Flight flight) {
        if (player.isDead || !player.isEntityAlive() || player.dimension != flight.dimension || ++flight.age > 2400) {
            finishFlight(player, flight);
            return;
        }
        player.capabilities.isFlying = false;
        // Ignore position/motion proposed by C03 while this server-owned trajectory is active.
        player.setPosition(flight.positionX, flight.positionY, flight.positionZ);
        double speed = FlightMath.speedMetersPerSecond(flight.x, flight.y, flight.z);
        int steps = Math.max(
            1,
            (int) Math.ceil(Math.max(Math.max(Math.abs(flight.x), Math.abs(flight.y)), Math.abs(flight.z)) / 4));
        double dx = flight.x / steps, dy = flight.y / steps, dz = flight.z / steps;
        boolean collision = false;
        boolean unloaded = false;
        for (int i = 0; i < steps; i++) {
            AxisAlignedBB box = player.boundingBox;
            AxisAlignedBB swept = box.addCoord(dx, dy, dz);
            if (!loaded(player.worldObj, swept)) {
                unloaded = true;
                break;
            }
            double fraction = 1;
            List<?> obstacles = player.worldObj.getCollidingBoundingBoxes(player, swept);
            double centerX = (box.minX + box.maxX) / 2;
            double centerY = (box.minY + box.maxY) / 2;
            double centerZ = (box.minZ + box.maxZ) / 2;
            double halfX = (box.maxX - box.minX) / 2;
            double halfY = (box.maxY - box.minY) / 2;
            double halfZ = (box.maxZ - box.minZ) / 2;
            for (Object obstacle : obstacles) {
                AxisAlignedBB other = (AxisAlignedBB) obstacle;
                double hit = FlightMath.sweep(
                    centerX,
                    centerY,
                    centerZ,
                    dx,
                    dy,
                    dz,
                    other.minX - halfX,
                    other.minY - halfY,
                    other.minZ - halfZ,
                    other.maxX + halfX,
                    other.maxY + halfY,
                    other.maxZ + halfZ);
                if (hit <= fraction) {
                    fraction = hit;
                    collision = true;
                }
            }
            double safeFraction = collision ? Math.max(0, fraction - 1.0E-5) : 1;
            player.setPosition(
                player.posX + dx * safeFraction,
                player.posY + dy * safeFraction,
                player.posZ + dz * safeFraction);
            if (collision) break;
        }
        player.motionX = player.motionY = player.motionZ = 0;
        flight.positionX = player.posX;
        flight.positionY = player.posY;
        flight.positionZ = player.posZ;
        player.fallDistance = 0;
        player.playerNetServerHandler
            .setPlayerLocation(player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch);
        if (collision || unloaded) {
            finishFlight(player, flight);
            if (collision && flight.damageFactor > 0)
                player.attackEntityFrom(IMPACT, (float) (speed * flight.damageFactor));
            return;
        }
        double drag = player.isInWater() ? 0.8 : 0.98;
        flight.x *= player.isInWater() ? 0.8 : 0.91;
        flight.z *= player.isInWater() ? 0.8 : 0.91;
        flight.y = (flight.y - (player.isInWater() ? 0.02 : 0.08)) * drag;
        hooks.flight(player, speed, true);
    }

    private boolean loaded(World world, AxisAlignedBB box) {
        int minX = (int) Math.floor(box.minX), maxX = (int) Math.floor(box.maxX);
        int minZ = (int) Math.floor(box.minZ), maxZ = (int) Math.floor(box.maxZ);
        return world.blockExists(minX, 64, minZ) && world.blockExists(minX, 64, maxZ)
            && world.blockExists(maxX, 64, minZ)
            && world.blockExists(maxX, 64, maxZ);
    }

    private void finishFlight(EntityPlayerMP player, Flight flight) {
        flights.remove(player.getUniqueID());
        restoreFlight(player, flight);
        hooks.flight(player, 0, false);
    }

    private void restoreFlight(EntityPlayerMP player, Flight flight) {
        if (player.capabilities.allowFlying) player.capabilities.isFlying = flight.wasFlying;
        player.sendPlayerAbilities();
    }

    private void cleanup(EntityPlayerMP player) {
        Flight flight = flights.get(player.getUniqueID());
        if (flight != null) finishFlight(player, flight);
        deadlines.remove(player.getUniqueID());
        showCountdown(player, -1);
        TransferService.INSTANCE.stopUsing(player);
    }

    public void forgetPlayer(EntityPlayerMP player) {
        cleanup(player);
        cooldowns.remove(player.getUniqueID());
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) cleanup((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP) cleanup((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void death(LivingDeathEvent event) {
        if (event.entityLiving instanceof EntityPlayerMP) cleanup((EntityPlayerMP) event.entityLiving);
    }

    @SubscribeEvent
    public void worldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote) return;
        for (Flight flight : new ArrayList<Flight>(flights.values())) {
            if (flight.player.worldObj == event.world) cleanup(flight.player);
        }
        Iterator<Map.Entry<String, Source>> iterator = sources.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Source> entry = iterator.next();
            if (entry.getValue().world == event.world) {
                cancelSource(entry.getKey());
                iterator.remove();
            }
        }
    }

    public void clear() {
        for (Flight flight : flights.values()) restoreFlight(flight.player, flight);
        flights.clear();
        sources.clear();
        deadlines.clear();
        cooldowns.clear();
        shownCountdowns.clear();
        TransferService.INSTANCE.clear();
        tick = 0;
    }

    private static final class Source {

        final World world;
        final double x, y, z;
        final EjectorSettings settings;
        final long lastSeen;

        Source(World world, double x, double y, double z, EjectorSettings settings, long tick) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.settings = settings;
            this.lastSeen = tick;
        }
    }

    private static final class Flight {

        final EntityPlayerMP player;
        final int dimension;
        final boolean wasFlying;
        final double damageFactor;
        double x, y, z;
        double positionX, positionY, positionZ;
        int age;

        Flight(EntityPlayerMP player, double x, double y, double z, double damageFactor) {
            this.player = player;
            this.dimension = player.dimension;
            this.wasFlying = player.capabilities.isFlying;
            this.x = x;
            this.y = y;
            this.z = z;
            this.damageFactor = damageFactor;
            this.positionX = player.posX;
            this.positionY = player.posY;
            this.positionZ = player.posZ;
        }
    }
}

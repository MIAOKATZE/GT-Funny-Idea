package com.miaokatze.gtfi.testlab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;

import com.miaokatze.gtfi.block.TileWarpEjector;
import com.miaokatze.gtfi.config.GtfiConfig;
import com.miaokatze.gtfi.core.EjectorSettings;
import com.miaokatze.gtfi.core.ThaumcraftWarp;
import com.miaokatze.gtfi.core.TransferService;
import com.miaokatze.gtfi.core.WarpService;
import com.miaokatze.gtfi.core.WarpType;
import com.miaokatze.gtfi.item.ItemPortableEjector;
import com.miaokatze.gtfi.register.ModContent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Assertions invoke the registered production items, tiles and services; no alternative business implementation. */
public final class TestScenarios {

    static final String[] SOURCE_IDS = { "gtfi-test:slow", "gtfi-test:fast", "gtfi-test:impact" };

    interface Scenario {

        String id();

        int timeout();

        void start(Context context) throws Exception;

        boolean tick(Context context, int elapsed) throws Exception;
    }

    abstract static class Case implements Scenario {

        private final String name;

        Case(String name) {
            this.name = name;
        }

        public String id() {
            return name;
        }

        public int timeout() {
            return 1000;
        }

        public void start(Context context) throws Exception {}
    }

    public static final class Context {

        final WorldServer world;
        final TestActor owner, target, observer;
        final List<String> assertions = new ArrayList<String>();
        final boolean safeMode;
        ItemStack tool;
        EjectorSettings first, second;
        int mark;
        double measured;
        final List<Integer> milestones = new ArrayList<Integer>();

        Context(WorldServer world, TestActor[] actors) {
            this.world = world;
            owner = actors[0];
            target = actors[1];
            observer = actors[2];
            safeMode = GtfiConfig.safeMode;
            target.setPosition(12, 65, 12);
            observer.setPosition(-12, 65, 12);
            MinecraftForge.EVENT_BUS.register(this);
        }

        void check(boolean condition, String message) {
            if (!condition) throw new AssertionError(message);
            assertions.add(message);
        }

        void eq(long actual, long expected, String message) {
            check(actual == expected, message + " expected=" + expected + " actual=" + actual);
        }

        void near(double actual, double expected, double tolerance, String message) {
            check(Math.abs(actual - expected) <= tolerance, message + " expected=" + expected + " actual=" + actual);
        }

        void restoreConfig() {
            GtfiConfig.safeMode = safeMode;
            MinecraftForge.EVENT_BUS.unregister(this);
        }

        @SubscribeEvent
        public void milestone(TransferService.MilestoneEvent event) {
            if (event.player == owner) milestones.add(event.level);
        }

        void warp(EntityPlayer player, WarpType type, int amount) {
            ThaumcraftWarp.set((net.minecraft.entity.player.EntityPlayerMP) player, type, amount);
        }

        void tool() {
            tool = new ItemStack(ModContent.WARP_TRANSFERER);
            owner.inventory.currentItem = 0;
            owner.inventory.setInventorySlotContents(0, tool);
            owner.setItemInUse(tool, 72000);
            owner.setPosition(0, 65, 0);
            owner.rotationYaw = 0;
            owner.rotationPitch = 0;
            target.setPosition(0, 65, 2);
        }

        void using(boolean charge) {
            TransferService.INSTANCE.onUsingTick(owner, tool, charge);
        }

        void progress(int level, int xp) {
            NBTTagCompound persisted = new NBTTagCompound();
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Level", level);
            tag.setInteger("Experience", xp);
            persisted.setTag("GtfiTransfer", tag);
            owner.getEntityData()
                .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        }

        void offer(String id, EjectorSettings settings) {
            WarpService.INSTANCE.offerSource(id, world, -2, 65, 0.5, settings);
        }
    }

    static List<Scenario> select(String selector) {
        List<Scenario> all = Arrays.<Scenario>asList(new Case("warp.read_write_conservation") {

            public boolean tick(Context c, int t) {
                for (WarpType type : WarpType.values()) {
                    c.warp(c.owner, type, 17);
                    c.eq(ThaumcraftWarp.get(c.owner, type), 17, type + " uses actual TC4 player knowledge");
                    c.warp(c.owner, type, -5);
                    c.eq(ThaumcraftWarp.get(c.owner, type), 0, type + " negative writes clamp");
                    c.warp(c.owner, type, 9);
                    c.warp(c.target, type.targetType(), 0);
                    c.eq(ThaumcraftWarp.transfer(c.owner, c.target, type, 99), 9, type + " cannot overdraw");
                    c.eq(ThaumcraftWarp.get(c.owner, type), 0, type + " debited at source");
                    c.eq(ThaumcraftWarp.get(c.target, type.targetType()), 9, type + " destination conversion");
                }
                c.warp(c.target, WarpType.NORMAL, Integer.MAX_VALUE);
                c.warp(c.owner, WarpType.TEMP, 3);
                c.eq(
                    ThaumcraftWarp.transfer(c.owner, c.target, WarpType.TEMP, 3),
                    0,
                    "destination saturation conserves source");
                c.eq(ThaumcraftWarp.get(c.owner, WarpType.TEMP), 3, "no debit when destination full");
                c.eq(ThaumcraftWarp.transfer(c.owner, c.owner, WarpType.TEMP, 2), 0, "self transfer rejected");
                return true;
            }
        }, new Case("settings.bounds_nbt") {

            public boolean tick(Context c, int t) {
                NBTTagCompound bad = new NBTTagCompound();
                bad.setInteger("threshold", -1);
                bad.setDouble("range", -100);
                bad.setDouble("power", Double.NaN);
                bad.setDouble("damageFactor", 2);
                bad.setInteger("countdownSeconds", 0);
                EjectorSettings block = EjectorSettings.read(bad, false, false);
                c.eq(block.threshold, 26, "block threshold lower bound greater than 25");
                c.near(block.range, 10, 0, "block range lower bound");
                c.near(EjectorSettings.blockDefaults().damageFactor, 1, 0, "default damage factor is one");
                c.eq(EjectorSettings.blockDefaults().threshold, 26, "default threshold is 26");
                c.near(block.power, 1, 0, "nonfinite block power rejected");
                c.near(block.damageFactor, 1, 0, "damage factor upper bound");
                c.eq(block.countdownSeconds, 31, "normal block countdown greater than 30 seconds");
                c.eq(
                    EjectorSettings.read(bad, false, true).countdownSeconds,
                    5,
                    "safeMode block countdown lower bound");
                EjectorSettings portable = EjectorSettings.read(bad, true, false);
                c.near(portable.range, 5, 0, "portable range lower bound");
                c.near(portable.power, 0.1, 0, "portable power lower bound");
                c.eq(portable.countdownSeconds, 5, "portable countdown lower bound");
                bad.setDouble("range", 9999);
                bad.setDouble("power", 99999);
                bad.setDouble("damageFactor", -1);
                block = EjectorSettings.read(bad, false, false);
                portable = EjectorSettings.read(bad, true, false);
                c.near(block.range, 200, 0, "block range upper bound");
                c.near(block.power, 1000, 0, "block power upper bound");
                c.near(portable.range, 50, 0, "portable range upper bound");
                c.near(portable.power, 1, 0, "portable power upper bound");
                c.near(block.damageFactor, 0, 0, "damage factor lower bound");
                c.world.setBlock(-4, 65, 0, ModContent.WARP_EJECTOR, 0, 3);
                TileWarpEjector tile = (TileWarpEjector) c.world.getTileEntity(-4, 65, 0);
                block.enabled = false;
                tile.setSettings(block);
                tile.setOwner(c.owner);
                NBTTagCompound saved = new NBTTagCompound();
                tile.writeToNBT(saved);
                TileWarpEjector loaded = new TileWarpEjector();
                loaded.readFromNBT(saved);
                c.check(
                    c.owner.getUniqueID()
                        .equals(loaded.getOwner()),
                    "block owner survives actual TileEntity NBT");
                c.near(loaded.getSettings().power, 1000, 0, "block settings survive TileEntity NBT");
                ItemStack item = new ItemStack(ModContent.PORTABLE_EJECTOR);
                portable.enabled = false;
                ItemPortableEjector.setSettings(item, portable);
                ItemStack loadedItem = ItemStack.loadItemStackFromNBT(item.writeToNBT(new NBTTagCompound()));
                c.near(
                    ItemPortableEjector.getSettings(loadedItem).range,
                    50,
                    0,
                    "portable settings survive actual ItemStack NBT");
                c.check(!ItemPortableEjector.getSettings(loadedItem).enabled, "portable disabled flag persists");
                return true;
            }
        }, new Case("countdown.immunity") {

            public void start(Context c) {
                c.warp(c.owner, WarpType.PERM, 100);
                c.first = EjectorSettings.portableDefaults();
                c.owner.addPotionEffect(new PotionEffect(thaumcraft.common.config.Config.potionWarpWardID, 1000, 0));
            }

            public boolean tick(Context c, int t) {
                c.offer(SOURCE_IDS[0], c.first);
                if (t == 5) c.eq(
                    WarpService.INSTANCE.getCountdownTicks(c.owner),
                    -1,
                    "warp ward blocks detection despite warp threshold");
                if (t == 6) c.owner.removePotionEffect(thaumcraft.common.config.Config.potionWarpWardID);
                if (t == 9) {
                    c.check(WarpService.INSTANCE.getCountdownTicks(c.owner) > 0, "removing ward begins countdown");
                    c.owner
                        .addPotionEffect(new PotionEffect(thaumcraft.common.config.Config.potionWarpWardID, 1000, 0));
                }
                if (t == 12) {
                    c.eq(WarpService.INSTANCE.getCountdownTicks(c.owner), -1, "ward cancels an active countdown");
                    c.check(!WarpService.INSTANCE.isFlying(c.owner), "ward never launches actor");
                    return true;
                }
                return false;
            }
        }, new Case("countdown.threshold_boundary") {

            public void start(Context c) {
                c.first = EjectorSettings.portableDefaults();
                c.warp(c.owner, WarpType.TEMP, 26);
            }

            public boolean tick(Context c, int t) {
                c.offer(SOURCE_IDS[0], c.first);
                if (t == 4) c.eq(
                    WarpService.INSTANCE.getCountdownTicks(c.owner),
                    -1,
                    "warp equal to configured threshold does not trigger");
                if (t == 5) c.warp(c.owner, WarpType.TEMP, 27);
                if (t == 8) {
                    c.check(
                        WarpService.INSTANCE.getCountdownTicks(c.owner) > 0,
                        "warp strictly greater than threshold triggers");
                    return true;
                }
                return false;
            }
        }, new Case("countdown.fastest_reentry_cancel") {

            public void start(Context c) {
                c.warp(c.owner, WarpType.TEMP, 100);
                c.first = EjectorSettings.portableDefaults();
                c.first.countdownSeconds = 8;
                c.second = EjectorSettings.portableDefaults();
                c.second.countdownSeconds = 5;
            }

            public boolean tick(Context c, int t) {
                if (t < 10) c.offer(SOURCE_IDS[0], c.first);
                if (t < 10 || t >= 13 && t < 19) c.offer(SOURCE_IDS[1], c.second);
                if (t == 5) c.check(
                    WarpService.INSTANCE.getCountdownTicks(c.owner) <= 100,
                    "overlapping sources show earliest countdown");
                if (t == 6) c.owner.setPosition(14, 65, 14);
                if (t == 8)
                    c.eq(WarpService.INSTANCE.getCountdownTicks(c.owner), -1, "leaving range cancels all countdowns");
                if (t == 12) c.owner.setPosition(0.5, 65, 0.5);
                if (t == 16) c.check(
                    WarpService.INSTANCE.getCountdownTicks(c.owner) >= 97,
                    "reentry restarts full countdown rather than resuming stale deadline");
                if (t == 17) {
                    c.second.enabled = false;
                    c.offer(SOURCE_IDS[1], c.second);
                }
                if (t == 20)
                    c.eq(WarpService.INSTANCE.getCountdownTicks(c.owner), -1, "disabled source cancels countdown");
                if (t >= 21 && t <= 26) {
                    c.second.enabled = true;
                    c.offer(SOURCE_IDS[1], c.second);
                }
                if (t == 25) c.warp(c.owner, WarpType.TEMP, 24);
                if (t == 28) c.eq(
                    WarpService.INSTANCE.getCountdownTicks(c.owner),
                    -1,
                    "dropping below warp threshold cancels countdown");
                if (t == 30) return true;
                return false;
            }
        }, new Case("flight.creative_cooldown_damage") {

            public void start(Context c) {
                c.warp(c.owner, WarpType.TEMP, 100);
                c.owner.capabilities.isCreativeMode = true;
                c.owner.capabilities.allowFlying = true;
                c.owner.capabilities.isFlying = true;
                c.first = EjectorSettings.portableDefaults();
                c.first.power = 1;
                c.first.damageFactor = 0.25;
                for (int y = 65; y < 75; y++)
                    for (int z = -2; z <= 2; z++) c.world.setBlock(2, y, z, Blocks.stone, 0, 3);
            }

            public boolean tick(Context c, int t) {
                c.offer(SOURCE_IDS[0], c.first);
                if (WarpService.INSTANCE.isFlying(c.owner) && c.mark == 0) {
                    c.mark = t;
                    c.measured = WarpService.INSTANCE.getSpeedMetersPerSecond(c.owner);
                    c.check(!c.owner.capabilities.isFlying, "creative flight is suspended during forced ejection");
                    c.eq(
                        WarpService.INSTANCE.getCooldownTicks(c.owner),
                        100,
                        "launch starts exact five second cooldown");
                }
                if (c.mark > 0 && t == c.mark + 2) {
                    c.check(c.owner.impacts == 1, "production collision requests creative impact damage");
                    c.near(
                        c.owner.lastImpact,
                        c.measured * 0.25,
                        0.001,
                        "impact request scales by configured damage factor");
                    c.check(c.owner.posX < 2, "sweep stops actor before stone wall");
                    c.check(c.owner.capabilities.isFlying, "creative flight preference restored after collision");
                }
                if (c.mark > 0 && t == c.mark + 99) {
                    c.eq(WarpService.INSTANCE.getCooldownTicks(c.owner), 1, "cooldown remains until tick 100");
                    c.eq(WarpService.INSTANCE.getCountdownTicks(c.owner), -1, "no countdown while cooldown active");
                }
                if (c.mark > 0 && t == c.mark + 101) {
                    c.eq(WarpService.INSTANCE.getCooldownTicks(c.owner), 0, "cooldown expires after 100 natural ticks");
                    c.check(WarpService.INSTANCE.getCountdownTicks(c.owner) >= 99, "countdown restarts after cooldown");
                    return true;
                }
                return false;
            }
        }, new Case("flight.high_speed_zero_damage") {

            public void start(Context c) {
                c.warp(c.owner, WarpType.TEMP, 100);
                c.first = EjectorSettings.blockDefaults();
                c.first.power = 1000;
                c.first.damageFactor = 0;
                for (int y = 65; y < 85; y++)
                    for (int z = -2; z <= 2; z++) c.world.setBlock(2, y, z, Blocks.stone, 0, 3);
            }

            public boolean tick(Context c, int t) {
                c.offer(SOURCE_IDS[0], c.first);
                if (WarpService.INSTANCE.isFlying(c.owner) && c.mark == 0) {
                    c.mark = t;
                    c.check(
                        WarpService.INSTANCE.getSpeedMetersPerSecond(c.owner) > 10000,
                        "high power exercises sweep over thousands of metres per tick");
                }
                if (c.mark > 0 && t == c.mark + 2) {
                    c.check(!WarpService.INSTANCE.isFlying(c.owner), "thin wall intercepts high speed flight");
                    c.check(c.owner.posX < 2, "high speed cannot tunnel through wall");
                    c.eq(c.owner.impacts, 0, "zero damage factor issues no damage request");
                    return true;
                }
                return false;
            }
        }, new Case("transfer.target_geometry") {

            public boolean tick(Context c, int t) {
                c.tool();
                c.check(
                    TransferService.INSTANCE.findTarget(c.owner) == c.target,
                    "aim selects nearby real server FakePlayer target");
                c.owner.rotationYaw = 90;
                c.check(
                    TransferService.INSTANCE.findTarget(c.owner) == null,
                    "wrong aim direction rejects nearby target");
                c.owner.rotationYaw = 0;
                c.target.setPosition(0, 65, 3.5);
                c.check(
                    TransferService.INSTANCE.findTarget(c.owner) == null,
                    "target outside three metre reach rejected");
                c.target.setPosition(0, 65, 2);
                c.world.setBlock(0, 66, 1, Blocks.stone, 0, 3);
                c.check(TransferService.INSTANCE.findTarget(c.owner) == null, "solid wall blocks transfer ray");
                return true;
            }
        },
            transferCase("transfer.temp_warmup_xp", WarpType.TEMP, 1),
            transferCase("transfer.normal_conversion", WarpType.NORMAL, 3),
            transferCase("transfer.permanent_conversion", WarpType.PERM, 6),
            milestoneCase("transfer.milestone_three", 2, WarpType.TEMP, 3),
            milestoneCase("transfer.milestone_six", 5, WarpType.NORMAL, 6),
            milestoneCase("transfer.milestone_ten", 9, WarpType.PERM, 10),
            new Case("transfer.unlock_charge_cancel") {

                public void start(Context c) {
                    c.tool();
                    c.warp(c.owner, WarpType.PERM, 200);
                }

                public boolean tick(Context c, int t) {
                    if (t == 1) {
                        c.check(
                            !TransferService.setType(c.owner, c.tool, WarpType.NORMAL),
                            "normal warp locked below level three");
                        c.check(
                            !TransferService.setType(c.owner, c.tool, WarpType.PERM),
                            "permanent warp locked below level six");
                    }
                    c.using(t <= 20);
                    if (t == 20) c.eq(
                        ThaumcraftWarp.get(c.owner, WarpType.TEMP),
                        2,
                        "200 permanent warp produces two temporary warp in twenty natural ticks");
                    if (t == 21) {
                        c.owner.stopUsingItem();
                        TransferService.INSTANCE.stopUsing(c.owner);
                    }
                    if (t >= 22) c.using(true);
                    if (t == 25) {
                        c.eq(ThaumcraftWarp.get(c.owner, WarpType.TEMP), 2, "released item cannot continue charging");
                        c.eq(
                            ThaumcraftWarp.get(c.owner, WarpType.PERM),
                            200,
                            "charging does not consume permanent warp");
                        return true;
                    }
                    return false;
                }
            },
            new Case("safe_mode.gates") {

                public boolean tick(Context c, int t) {
                    c.tool();
                    c.warp(c.owner, WarpType.PERM, 200);
                    c.warp(c.owner, WarpType.TEMP, 100);
                    c.world.setBlock(-4, 65, 0, ModContent.WARP_EJECTOR, 0, 3);
                    TileWarpEjector tile = (TileWarpEjector) c.world.getTileEntity(-4, 65, 0);
                    tile.setOwner(c.owner);
                    // The complete override occurs within this one server callback; always restored before another
                    // tick.
                    boolean original = GtfiConfig.safeMode;
                    try {
                        GtfiConfig.safeMode = true;
                        c.check(tile.canEdit(c.owner), "safeMode block owner can edit");
                        c.check(!tile.canEdit(c.target), "safeMode unrelated non-operator cannot edit block");
                        EjectorSettings settings = EjectorSettings.blockDefaults();
                        settings.countdownSeconds = 1;
                        tile.setSettings(settings);
                        c.eq(tile.getSettings().countdownSeconds, 5, "safeMode block accepts five second countdown");
                        c.check(
                            !TransferService.setType(c.owner, c.tool, WarpType.TEMP),
                            "safeMode denies transfer type edits");
                        for (int i = 0; i < 100; i++) c.using(true);
                        c.eq(
                            ThaumcraftWarp.get(c.owner, WarpType.TEMP),
                            100,
                            "safeMode denies all charging warp writes");
                        c.eq(ThaumcraftWarp.get(c.target, WarpType.NORMAL), 0, "safeMode denies transfer writes");
                        c.owner.stopUsingItem();
                        ModContent.WARP_TRANSFERER.onItemRightClick(c.tool, c.world, c.owner);
                        c.check(!c.owner.isUsingItem(), "safeMode item right click cannot begin use");
                    } finally {
                        GtfiConfig.safeMode = original;
                    }
                    return true;
                }
            });
        List<Scenario> selected = new ArrayList<Scenario>();
        for (Scenario scenario : all) if ("all".equals(selector) || scenario.id()
            .equals(selector)) selected.add(scenario);
        for (Scenario scenario : NativeScenarios.all()) if ("all".equals(selector) || scenario.id()
            .equals(selector)) selected.add(scenario);
        return selected;
    }

    private static Scenario transferCase(String id, final WarpType type, final int level) {
        return new Case(id) {

            public void start(Context c) {
                c.tool();
                c.progress(level, 0);
                c.warp(c.owner, type, 30);
                c.check(TransferService.setType(c.owner, c.tool, type), "unlocked type accepted for level " + level);
            }

            public boolean tick(Context c, int t) {
                c.using(false);
                if (t == 59) c.eq(
                    ThaumcraftWarp.get(c.target, type.targetType()),
                    0,
                    "three second warmup has no early transfer");
                if (t == 62) {
                    c.eq(
                        ThaumcraftWarp.get(c.target, type.targetType()),
                        level,
                        "first production transfer converts selected warp");
                    c.eq(ThaumcraftWarp.get(c.owner, type), 30 - level, "transfer debits source in correct direction");
                    c.eq(
                        TransferService.getExperience(c.owner),
                        (long) level * type.experience,
                        "actual transfer grants weighted experience");
                    c.check(
                        !c.owner.messages.isEmpty() && !c.target.messages.isEmpty(),
                        "both participants receive transfer chat");
                    return true;
                }
                return false;
            }
        };
    }

    private static Scenario milestoneCase(String id, final int startLevel, final WarpType type, final int nextLevel) {
        return new Case(id) {

            public void start(Context c) {
                c.tool();
                c.progress(startLevel, com.miaokatze.gtfi.core.TransferProgression.required(startLevel) - 1);
                c.warp(c.owner, type, 50);
                c.check(TransferService.setType(c.owner, c.tool, type), "milestone fixture selects unlocked type");
            }

            public boolean tick(Context c, int t) {
                c.using(false);
                if (t == 62) {
                    c.eq(
                        TransferService.getLevel(c.owner),
                        nextLevel,
                        "actual production XP promotes to milestone " + nextLevel);
                    c.check(
                        c.milestones.contains(nextLevel),
                        "real server broadcast path emitted milestone " + nextLevel);
                    c.eq(c.milestones.size(), 1, "milestone broadcast occurs once");
                    return true;
                }
                return false;
            }
        };
    }
}

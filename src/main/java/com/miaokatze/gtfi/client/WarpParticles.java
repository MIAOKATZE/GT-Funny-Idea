package com.miaokatze.gtfi.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtfi.network.VisualMessage;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Bounded, texture-free voxel sparks. One mesh batch per pass, independent of optional mods. */
final class WarpParticles {

    private static final int LIMIT = 768;
    private final List<Spark> sparks = new ArrayList<Spark>();
    private final Random random = new Random();
    private int budget = 96;

    void clear() {
        sparks.clear();
        budget = 96;
    }

    void tick() {
        budget = Minecraft.getMinecraft().gameSettings.particleSetting == 1 ? 40 : 96;
        Iterator<Spark> iterator = sparks.iterator();
        while (iterator.hasNext()) {
            Spark spark = iterator.next();
            if (++spark.age >= spark.life) iterator.remove();
            else {
                spark.x += spark.vx;
                spark.y += spark.vy;
                spark.z += spark.vz;
                spark.vx *= .97;
                spark.vy *= .97;
                spark.vz *= .97;
            }
        }
    }

    private void add(double x, double y, double z, double vx, double vy, double vz, int rgb, int life, double size) {
        Minecraft mc = Minecraft.getMinecraft();
        if (budget <= 0 || sparks.size() >= LIMIT
            || mc.gameSettings.particleSetting >= 2
            || mc.thePlayer == null
            || mc.thePlayer.getDistanceSq(x, y, z) > 96 * 96) return;
        budget--;
        sparks.add(new Spark(x, y, z, vx, vy, vz, rgb, life, size));
    }

    void trail(VisualMessage message) {
        Entity viewer = Minecraft.getMinecraft().renderViewEntity;
        if (viewer == null) return;
        double dx = message.x - message.targetX;
        double dy = message.y - message.targetY;
        double dz = message.z - message.targetZ;
        double lengthSq = dx * dx + dy * dy + dz * dz;
        double lo = 0, hi = 1;
        if (lengthSq > .000001) {
            double rx = message.targetX - viewer.posX;
            double ry = message.targetY - viewer.posY;
            double rz = message.targetZ - viewer.posZ;
            double dot = rx * dx + ry * dy + rz * dz;
            // Clip the authoritative segment against the camera's visible sphere before sampling.
            double discriminant = dot * dot - lengthSq * (rx * rx + ry * ry + rz * rz - 80 * 80);
            if (discriminant < 0) return;
            double root = Math.sqrt(discriminant);
            lo = Math.max(0, (-dot - root) / lengthSq);
            hi = Math.min(1, (-dot + root) / lengthSq);
            if (lo > hi) return;
        }
        int steps = Math.max(2, Math.min(48, (int) Math.ceil(Math.sqrt(lengthSq) * (hi - lo) * 2)));
        for (int i = 0; i < steps; i++) {
            double t = lo + (hi - lo) * (i + .5) / steps;
            add(
                message.targetX + dx * t + jitter(.22),
                message.targetY + dy * t + jitter(.4),
                message.targetZ + dz * t + jitter(.22),
                jitter(.012),
                .006 + random.nextDouble() * .025,
                jitter(.012),
                random.nextBoolean() ? 0xFFD05A : 0xFFF2B7,
                24 + random.nextInt(24),
                .04 + random.nextDouble() * .06);
        }
    }

    void transfer(VisualMessage message) {
        burst(message.x, message.y, message.z, 0xC878FF);
        burst(message.targetX, message.targetY, message.targetZ, 0xE3B9FF);
        double dx = message.targetX - message.x;
        double dy = message.targetY - message.y;
        double dz = message.targetZ - message.z;
        if (dx * dx + dy * dy + dz * dz > 64 * 64) return;
        for (int i = 0; i < 28; i++) {
            double t = i / 27D;
            double angle = t * Math.PI * 6;
            double radius = Math.sin(t * Math.PI) * .35;
            add(
                message.x + dx * t + Math.cos(angle) * radius,
                message.y + dy * t + Math.sin(t * Math.PI) * .5,
                message.z + dz * t + Math.sin(angle) * radius,
                dx * .008,
                dy * .008,
                dz * .008,
                0xD498FF,
                30,
                .04);
        }
    }

    private void burst(double x, double y, double z, int rgb) {
        for (int i = 0; i < 28; i++) {
            double angle = i * Math.PI * 2 / 28;
            double dy = jitter(.7);
            add(
                x + Math.cos(angle) * .35,
                y + dy,
                z + Math.sin(angle) * .35,
                Math.cos(angle) * .065,
                dy * .06,
                Math.sin(angle) * .065,
                rgb,
                22 + random.nextInt(18),
                .025 + random.nextDouble() * .055);
        }
    }

    private double jitter(double range) {
        return (random.nextDouble() * 2 - 1) * range;
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity camera = mc.renderViewEntity;
        if (camera == null || sparks.isEmpty() || mc.gameSettings.particleSetting >= 2) return;
        double x = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.partialTicks;
        double y = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.partialTicks;
        double z = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.partialTicks;
        float lightX = OpenGlHelper.lastBrightnessX;
        float lightY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-x, -y, -z);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDepthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            batch(event.partialTicks, false);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            batch(event.partialTicks, true);
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void batch(float partialTicks, boolean glow) {
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setBrightness(0xF000F0);
        for (Spark spark : sparks) {
            float alpha = (float) Math.max(0, 1 - (spark.age + partialTicks) / spark.life);
            tess.setColorRGBA_F(
                ((spark.rgb >> 16) & 255) / 255F,
                ((spark.rgb >> 8) & 255) / 255F,
                (spark.rgb & 255) / 255F,
                alpha * (glow ? .14F : .8F));
            double s = spark.size * (.6 + alpha * .4) * (glow ? 2.5 : 1);
            double x = spark.x + spark.vx * partialTicks;
            double y = spark.y + spark.vy * partialTicks;
            double z = spark.z + spark.vz * partialTicks;
            WarpMesh.cubeVertices(tess, x - s, y - s, z - s, x + s, y + s, z + s);
        }
        tess.draw();
    }

    private static final class Spark {

        private double x, y, z, vx, vy, vz;
        private final int rgb, life;
        private final double size;
        private int age;

        private Spark(double x, double y, double z, double vx, double vy, double vz, int rgb, int life, double size) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.rgb = rgb;
            this.life = life;
            this.size = size;
        }
    }
}

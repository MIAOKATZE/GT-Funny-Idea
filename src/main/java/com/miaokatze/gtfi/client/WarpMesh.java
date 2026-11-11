package com.miaokatze.gtfi.client;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

/** Small original meshes: gold quantum core, pendant setting and a segmented violet disc. */
final class WarpMesh {

    private static final int[] GOLD = { 0xA9650A, 0xE9B73F, 0xF3CB5E, 0xFFDF75, 0xFFD65D, 0xD99D22, 0xFFCF4C };
    private static final int[] VIOLET = { 0x140C1E, 0x69418B, 0x9862C9, 0xCE9EF3, 0xDEB3FF, 0x75489D, 0xBB70F1 };

    private WarpMesh() {}

    static void begin() {
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    static void end() {
        GL11.glPopAttrib();
    }

    static void core(double time) {
        core(time, GOLD);
    }

    private static void core(double time, int[] palette) {
        GL11.glPushMatrix();
        GL11.glRotated(time * .7, 0, 1, 0);
        com.miaokatze.miaokatzecore.client.render.SolidMesh.box(-.185, -.185, -.185, .185, .185, .185, palette[0], 1);
        com.miaokatze.miaokatzecore.client.render.SolidMesh.box(-.192, -.128, -.128, .192, .128, .128, palette[1], 1);
        com.miaokatze.miaokatzecore.client.render.SolidMesh.box(-.128, -.192, -.128, .128, .192, .128, palette[2], 1);
        com.miaokatze.miaokatzecore.client.render.SolidMesh.box(-.128, -.128, -.192, .128, .128, .192, palette[3], 1);
        GL11.glPushMatrix();
        GL11.glRotated(45, 0, 0, 1);
        GL11.glRotated(time * 1.2, 1, 0, 0);
        com.miaokatze.miaokatzecore.client.render.SolidMesh.squareRing(.28, .305, .016, palette[4], .8F);
        GL11.glRotated(90, 1, 0, 0);
        com.miaokatze.miaokatzecore.client.render.SolidMesh.squareRing(.28, .305, .016, palette[5], .8F);
        GL11.glPopMatrix();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
        com.miaokatze.miaokatzecore.client.render.SolidMesh.box(-.206, -.206, -.206, .206, .206, .206, palette[6], .1F);
        GL11.glDepthMask(true);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glPopMatrix();
    }

    static void pendant(double time) {
        GL11.glPushMatrix();
        GL11.glScaled(.82, .82, .82);
        core(time);
        GL11.glTranslated(0, .33, 0);
        com.miaokatze.miaokatzecore.client.render.SolidMesh.squareRing(.07, .105, .025, 0xFFDA72, 1);
        GL11.glPopMatrix();
    }

    static void disc(double time, boolean active) {
        com.miaokatze.miaokatzecore.client.render.SolidMesh.ring(.13, .4, -.055, .055, 0x17111F, 1);
        com.miaokatze.miaokatzecore.client.render.SolidMesh.ring(.355, .405, -.018, .018, 0x6F3D91, 1);
        com.miaokatze.miaokatzecore.client.render.SolidMesh.ring(.115, .145, -.065, .065, 0x08070B, 1);
        GL11.glPushMatrix();
        GL11.glScaled(.44, .44, .44);
        GL11.glRotated(20, 1, 0, 0);
        core(time, VIOLET);
        GL11.glPopMatrix();
        GL11.glPushMatrix();
        GL11.glRotated(time * (active ? 2.4 : .5), 0, 0, 1);
        com.miaokatze.miaokatzecore.client.render.SolidMesh.ring(.235, .25, -.062, -.057, 0xB77CE6, 1);
        com.miaokatze.miaokatzecore.client.render.SolidMesh.ring(.235, .25, .057, .062, 0xB77CE6, 1);
        for (int i = 0; i < 8; i++) {
            GL11.glRotated(45, 0, 0, 1);
            com.miaokatze.miaokatzecore.client.render.SolidMesh
                .box(-.018, .285, -.066, .018, .35, .066, i % 2 == 0 ? 0xDEADFF : 0x754599, 1);
        }
        GL11.glPopMatrix();
        if (active) {
            GL11.glPushMatrix();
            GL11.glRotated(-time * 1.6, 0, 0, 1);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            com.miaokatze.miaokatzecore.client.render.SolidMesh.ring(.42, .435, -.005, .005, 0xD798FF, .6F);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glPopMatrix();
        }
    }

    static void cubeVertices(Tessellator t, double a, double b, double c, double d, double e, double f) {
        com.miaokatze.miaokatzecore.client.render.SolidMesh.cubeVertices(t, a, b, c, d, e, f);
    }
}

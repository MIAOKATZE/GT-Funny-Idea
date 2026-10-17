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
        box(-.185, -.185, -.185, .185, .185, .185, palette[0], 1);
        box(-.192, -.128, -.128, .192, .128, .128, palette[1], 1);
        box(-.128, -.192, -.128, .128, .192, .128, palette[2], 1);
        box(-.128, -.128, -.192, .128, .128, .192, palette[3], 1);
        GL11.glPushMatrix();
        GL11.glRotated(45, 0, 0, 1);
        GL11.glRotated(time * 1.2, 1, 0, 0);
        squareRing(.28, .305, .016, palette[4], .8F);
        GL11.glRotated(90, 1, 0, 0);
        squareRing(.28, .305, .016, palette[5], .8F);
        GL11.glPopMatrix();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
        box(-.206, -.206, -.206, .206, .206, .206, palette[6], .1F);
        GL11.glDepthMask(true);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glPopMatrix();
    }

    static void pendant(double time) {
        GL11.glPushMatrix();
        GL11.glScaled(.82, .82, .82);
        core(time);
        GL11.glTranslated(0, .33, 0);
        squareRing(.07, .105, .025, 0xFFDA72, 1);
        GL11.glPopMatrix();
    }

    static void disc(double time, boolean active) {
        ring(.13, .4, -.055, .055, 0x17111F, 1);
        ring(.355, .405, -.018, .018, 0x6F3D91, 1);
        ring(.115, .145, -.065, .065, 0x08070B, 1);
        GL11.glPushMatrix();
        GL11.glScaled(.44, .44, .44);
        GL11.glRotated(20, 1, 0, 0);
        core(time, VIOLET);
        GL11.glPopMatrix();
        GL11.glPushMatrix();
        GL11.glRotated(time * (active ? 2.4 : .5), 0, 0, 1);
        ring(.235, .25, -.062, -.057, 0xB77CE6, 1);
        ring(.235, .25, .057, .062, 0xB77CE6, 1);
        for (int i = 0; i < 8; i++) {
            GL11.glRotated(45, 0, 0, 1);
            box(-.018, .285, -.066, .018, .35, .066, i % 2 == 0 ? 0xDEADFF : 0x754599, 1);
        }
        GL11.glPopMatrix();
        if (active) {
            GL11.glPushMatrix();
            GL11.glRotated(-time * 1.6, 0, 0, 1);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            ring(.42, .435, -.005, .005, 0xD798FF, .6F);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glPopMatrix();
        }
    }

    private static void squareRing(double inner, double outer, double thick, int rgb, float alpha) {
        box(-outer, -outer, -thick, -inner, outer, thick, rgb, alpha);
        box(inner, -outer, -thick, outer, outer, thick, rgb, alpha);
        box(-inner, -outer, -thick, inner, -inner, thick, rgb, alpha);
        box(-inner, inner, -thick, inner, outer, thick, rgb, alpha);
    }

    private static void ring(double inner, double outer, double front, double back, int rgb, float alpha) {
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setBrightness(0xF000F0);
        color(tess, rgb, alpha);
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI / 24;
            double b = (i + 1) * Math.PI / 24;
            double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
            quad(
                tess,
                ca * inner,
                sa * inner,
                front,
                ca * outer,
                sa * outer,
                front,
                cb * outer,
                sb * outer,
                front,
                cb * inner,
                sb * inner,
                front);
            quad(
                tess,
                cb * inner,
                sb * inner,
                back,
                cb * outer,
                sb * outer,
                back,
                ca * outer,
                sa * outer,
                back,
                ca * inner,
                sa * inner,
                back);
            quad(
                tess,
                ca * outer,
                sa * outer,
                front,
                ca * outer,
                sa * outer,
                back,
                cb * outer,
                sb * outer,
                back,
                cb * outer,
                sb * outer,
                front);
            if (inner > 0) quad(
                tess,
                ca * inner,
                sa * inner,
                back,
                ca * inner,
                sa * inner,
                front,
                cb * inner,
                sb * inner,
                front,
                cb * inner,
                sb * inner,
                back);
        }
        tess.draw();
    }

    private static void color(Tessellator tess, int rgb, float alpha) {
        tess.setColorRGBA_F(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, alpha);
    }

    private static void box(double x0, double y0, double z0, double x1, double y1, double z1, int rgb, float alpha) {
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setBrightness(0xF000F0);
        color(tess, rgb, alpha);
        cubeVertices(tess, x0, y0, z0, x1, y1, z1);
        tess.draw();
    }

    static void cubeVertices(Tessellator t, double a, double b, double c, double d, double e, double f) {
        quad(t, a, b, c, a, e, c, d, e, c, d, b, c);
        quad(t, d, b, f, d, e, f, a, e, f, a, b, f);
        quad(t, a, b, f, a, e, f, a, e, c, a, b, c);
        quad(t, d, b, c, d, e, c, d, e, f, d, b, f);
        quad(t, a, e, c, a, e, f, d, e, f, d, e, c);
        quad(t, a, b, f, a, b, c, d, b, c, d, b, f);
    }

    private static void quad(Tessellator t, double x0, double y0, double z0, double x1, double y1, double z1, double x2,
        double y2, double z2, double x3, double y3, double z3) {
        t.addVertex(x0, y0, z0);
        t.addVertex(x1, y1, z1);
        t.addVertex(x2, y2, z2);
        t.addVertex(x3, y3, z3);
    }
}

package com.miaokatze.gtfi.client;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtfi.block.TileWarpEjector;

/** A gold quantum-node variant without an AE2 or GTSWN runtime dependency. */
final class WarpEjectorRenderer extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (tile instanceof TileWarpEjector) ClientRuntime.emitEjector((TileWarpEjector) tile);
        double time = (tile.getWorldObj() == null ? 0
            : tile.getWorldObj()
                .getTotalWorldTime())
            + partialTicks;
        float lightX = OpenGlHelper.lastBrightnessX;
        float lightY = OpenGlHelper.lastBrightnessY;
        GL11.glPushMatrix();
        WarpMesh.begin();
        try {
            GL11.glTranslated(x + .5, y + .5 + Math.sin(time * .035) * .025, z + .5);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            WarpMesh.core(time);
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            WarpMesh.end();
            GL11.glPopMatrix();
        }
    }
}

package com.miaokatze.gtfi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

final class WarpBlockItemRenderer implements IItemRenderer {

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type != ItemRenderType.FIRST_PERSON_MAP;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return helper == ItemRendererHelper.ENTITY_BOBBING || helper == ItemRendererHelper.ENTITY_ROTATION;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        Minecraft mc = Minecraft.getMinecraft();
        double time = mc.theWorld == null ? 0 : mc.theWorld.getTotalWorldTime();
        float lightX = OpenGlHelper.lastBrightnessX;
        float lightY = OpenGlHelper.lastBrightnessY;
        GL11.glPushMatrix();
        WarpMesh.begin();
        try {
            if (type == ItemRenderType.INVENTORY) {
                GL11.glTranslatef(8, 8, 0);
                GL11.glScalef(17, -17, 17);
                GL11.glRotatef(25, 1, 0, 0);
            } else if (type != ItemRenderType.ENTITY) GL11.glTranslatef(.5F, .5F, .5F);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            WarpMesh.core(time);
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            WarpMesh.end();
            GL11.glPopMatrix();
        }
    }
}

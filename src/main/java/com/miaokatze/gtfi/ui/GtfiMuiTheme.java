package com.miaokatze.gtfi.ui;

import java.util.function.Supplier;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.TextWidget;

/** The independently bundled GTMB pocket skin, with its quiet cloth and brass trim. */
final class GtfiMuiTheme {

    static final int TEXT = 0xffedf1eb, TITLE = 0xffefcb8a, MUTED = 0xff9bacb3;
    static final IDrawable PANEL = surface("panel", 64, 64, 5, 5, 2, 0xff121c22, 0xff18242b);
    static final IDrawable CLOTH = surface("cloth", 32, 32, 3, 3, 3, 0xff263238, 0xff192329);
    static final IDrawable BUTTON = surface("btn", 88, 18, 4, 3, 2, 0xff35434b, 0xff29363e);
    static final IDrawable PRESSED = surface("btn_pressed", 88, 18, 4, 3, 2, 0xff4c5e63, 0xff35484f);

    private GtfiMuiTheme() {}

    private static IDrawable surface(String name, int w, int h, int bx, int by, int inset, int top, int bottom) {
        UITexture trim = UITexture.builder()
            .location("gtfi", "textures/gui/pocket/POCKET_C2_" + name + ".png")
            .imageSize(w, h)
            .adaptable(bx, by)
            .build()
            .withColorOverride(0xffffffff);
        return (context, x, y, width, height, theme) -> {
            trim.draw(context, x, y, width, height, theme);
            GuiDraw.drawVerticalGradientRect(x + inset, y + inset, width - inset * 2, height - inset * 2, top, bottom);
        };
    }

    static ModularPanel panel(String name, int height) {
        return new ModularPanel("gtfi_" + name).size(320, height)
            .background(CLOTH);
    }

    static TextWidget<?> label(Supplier<String> text, int x, int y, int width, int height, int color) {
        return IKey.dynamic(text)
            .color(color)
            .shadow(false)
            .asWidget()
            .textAlign(Alignment.CenterLeft)
            .pos(x, y)
            .size(width, height);
    }

    static void header(ModularPanel panel, String key) {
        panel.child(label(() -> GtfiUI.tr(key), 14, 9, 278, 20, TITLE));
        panel.child(
            new com.cleanroommc.modularui.drawable.Rectangle().color(0xff596269)
                .asWidget()
                .pos(12, 34)
                .size(296, 1));
        panel.child(
            com.cleanroommc.modularui.widgets.ButtonWidget.panelCloseButton()
                .right(7)
                .top(8));
    }
}

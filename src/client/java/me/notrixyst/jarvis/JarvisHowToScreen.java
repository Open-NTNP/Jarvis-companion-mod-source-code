package me.notrixyst.jarvis;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class JarvisHowToScreen extends Screen {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 252;

    private final Screen parent;

    public JarvisHowToScreen(Screen parent) {
        super(Text.literal("JARVIS How To"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = 120;
        int x = this.width / 2 - buttonWidth / 2;
        int panelY = Math.max(6, this.height / 2 - PANEL_H / 2);
        int y = panelY + PANEL_H - 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close()).dimensions(x, y, buttonWidth, 20).build());
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, argb(170, 2, 8, 16));

        int panelX = this.width / 2 - PANEL_W / 2;
        int panelY = Math.max(6, this.height / 2 - PANEL_H / 2);

        context.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, argb(215, 4, 16, 29));
        context.fill(panelX, panelY, panelX + PANEL_W, panelY + 2, argb(220, 53, 205, 255));
        context.fill(panelX, panelY + PANEL_H - 2, panelX + PANEL_W, panelY + PANEL_H, argb(170, 28, 133, 183));

        drawCenteredWithOutline(context, "J.A.R.V.I.S", this.width / 2, panelY + 14, 0xFFF0FDFF, 0xFF2EAEDB);
        drawCenteredWithOutline(context, "How To Use - Command Reference", this.width / 2, panelY + 30, 0xFFA3EBFF, 0xFF275F79);

        List<String> lines = List.of(
                "Wake: 'jarvis' then send your request.",
                "Recipes: 'recipe for observer', 'recipe for potion of strength'.",
                "Commands: 'how to teleport with commands'.",
                "Command blocks: 'command block types'.",
                "Browse web: 'browse <keywords>' opens your default browser.",
                "Screenshot: 'clip that' saves to Minecraft screenshots folder.",
                "Whisper: 'whisper to Alex meet at base'.",
                "Waypoints: 'save waypoint as home', 'where waypoint home is?'.",
                "Modes: 'assistance mode' / 'switch to assistance mode' / 'switch to local mode'.",
                "Status: 'how do you work?'.",
                "Config keybind: JarvisMenuKeybind in Controls.",
                "Config toggles: Jarvis ON/OFF, Learn ON/OFF, Sorting UI ON/OFF, Durability HUD ON/OFF."
        );

        super.render(context, mouseX, mouseY, delta);

        int contentTop = panelY + 52;
        int contentBottom = panelY + PANEL_H - 34;
        int lineCount = Math.max(1, lines.size());
        int available = Math.max(1, contentBottom - contentTop);
        int step = Math.max(12, Math.min(17, available / lineCount));

        int y = contentTop;
        int maxWidth = PANEL_W - 28;
        for (String line : lines) {
            if (y > contentBottom) {
                break;
            }
            String clipped = this.textRenderer.trimToWidth(line, maxWidth);
            drawLine(context, clipped, panelX + 14, y);
            y += step;
        }
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | (blue & 255);
    }

    private void drawLine(DrawContext context, String text, int x, int y) {
        context.drawText(this.textRenderer, Text.literal(text), x - 1, y, 0xFF09202C, false);
        context.drawText(this.textRenderer, Text.literal(text), x + 1, y, 0xFF09202C, false);
        context.drawText(this.textRenderer, Text.literal(text), x, y, 0xFFE8FBFF, false);
    }

    private void drawCenteredWithOutline(DrawContext context, String text, int centerX, int y, int color, int outline) {
        int x = centerX - (this.textRenderer.getWidth(text) / 2);
        context.drawText(this.textRenderer, Text.literal(text), x - 1, y, outline, true);
        context.drawText(this.textRenderer, Text.literal(text), x + 1, y, outline, true);
        context.drawText(this.textRenderer, Text.literal(text), x, y - 1, outline, true);
        context.drawText(this.textRenderer, Text.literal(text), x, y + 1, outline, true);
        context.drawText(this.textRenderer, Text.literal(text), x, y, color, true);
    }
}

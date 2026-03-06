package me.notrixyst.jarvis;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class JarvisConfigScreen extends Screen {

    private final Screen parent;
    private final JarvisConfig config = JarvisConfig.getInstance();

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 248;

    private TextFieldWidget apiKeyField;
    private final List<ButtonWidget> styledButtons = new ArrayList<>();
    private boolean resetConfirmArmed = false;

    public JarvisConfigScreen(Screen parent) {
        super(Text.literal("JARVIS Configuration"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        styledButtons.clear();

        int panelX = getPanelX();
        int panelY = getPanelY();

        int fieldX = panelX + 20;
        int fieldY = panelY + 60;
        int fieldW = PANEL_W - 40;

        apiKeyField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, fieldW, 20, Text.literal("Gemini API Key"));
        apiKeyField.setMaxLength(256);
        apiKeyField.setText(config.getApiKey());
        addSelectableChild(apiKeyField);

        int topButtonWidth = 156;
        int topGap = 10;
        int topRowStartX = panelX + (PANEL_W - ((topButtonWidth * 2) + topGap)) / 2;

        ButtonWidget learnButton = ButtonWidget.builder(
                learnLabel(),
                button -> {
                    config.setLearnFromPlayer(!config.isLearnFromPlayer());
                    button.setMessage(learnLabel());
                }
        ).dimensions(topRowStartX, fieldY + 28, topButtonWidth, 20).build();

        ButtonWidget howToButton = ButtonWidget.builder(
                Text.literal("How To Use It"),
                button -> {
                    if (client != null) {
                        client.setScreen(new JarvisHowToScreen(this));
                    }
                }
        ).dimensions(topRowStartX + topButtonWidth + topGap, fieldY + 28, topButtonWidth, 20).build();

        ButtonWidget enabledButton = ButtonWidget.builder(
                enabledLabel(),
                button -> {
                    config.setJarvisEnabled(!config.isJarvisEnabled());
                    button.setMessage(enabledLabel());
                }
        ).dimensions(fieldX, fieldY + 50, 320, 20).build();

        ButtonWidget sortingButton = ButtonWidget.builder(
                sortingLabel(),
                button -> {
                    config.setInventorySortingEnabled(!config.isInventorySortingEnabled());
                    button.setMessage(sortingLabel());
                }
        ).dimensions(fieldX, fieldY + 72, 320, 20).build();

        ButtonWidget durabilityHudButton = ButtonWidget.builder(
                durabilityHudLabel(),
                button -> {
                    config.setDurabilityHudEnabled(!config.isDurabilityHudEnabled());
                    button.setMessage(durabilityHudLabel());
                }
        ).dimensions(fieldX, fieldY + 94, 320, 20).build();

        ButtonWidget resetDataButton = ButtonWidget.builder(
                resetDataLabel(),
                button -> {
                    if (!resetConfirmArmed) {
                        resetConfirmArmed = true;
                        button.setMessage(resetDataLabel());
                        return;
                    }
                    resetJarvisDataFiles();
                    resetConfirmArmed = false;
                    button.setMessage(resetDataLabel());
                }
        ).dimensions(fieldX, fieldY + 116, 320, 20).build();

        int bottomY = panelY + 206;
        int buttonWidth = 112;
        int buttonGap = 18;
        int rowStartX = panelX + (PANEL_W - ((buttonWidth * 2) + buttonGap)) / 2;

        ButtonWidget saveButton = ButtonWidget.builder(
                Text.literal("Save"),
                button -> saveAndClose()
        ).dimensions(rowStartX, bottomY, buttonWidth, 20).build();

        ButtonWidget cancelButton = ButtonWidget.builder(
                Text.literal("Cancel"),
                button -> close()
        ).dimensions(rowStartX + buttonWidth + buttonGap, bottomY, buttonWidth, 20).build();

        addStyledButton(learnButton);
        addStyledButton(howToButton);
        addStyledButton(enabledButton);
        addStyledButton(sortingButton);
        addStyledButton(durabilityHudButton);
        addStyledButton(resetDataButton);
        addStyledButton(saveButton);
        addStyledButton(cancelButton);
        addDrawableChild(apiKeyField);
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

        int panelX = getPanelX();
        int panelY = getPanelY();

        context.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, argb(215, 3, 14, 25));
        context.fill(panelX, panelY, panelX + PANEL_W, panelY + 2, argb(225, 55, 204, 255));
        context.fill(panelX, panelY + PANEL_H - 2, panelX + PANEL_W, panelY + PANEL_H, argb(170, 25, 130, 180));
        context.fill(panelX + 12, panelY + 52, panelX + PANEL_W - 12, panelY + 54, argb(130, 27, 114, 160));

        drawCenteredWithOutline(context, "J.A.R.V.I.S", this.width / 2, panelY + 16, 0xFFF2FDFF, 0xFF2EAEDB);
        drawCenteredWithOutline(context, "configuration menu", this.width / 2, panelY + 32, 0xFFA4EBFF, 0xFF275F79);

        context.drawText(this.textRenderer, Text.literal("Gemini API Key"), panelX + 20, panelY + 56, 0xFFBDEFFF, false);
        context.drawText(this.textRenderer, Text.literal("Open this menu with your configured keybind in Controls."), panelX + 20, panelY + 228, 0xFF8DCDE4, false);

        for (ButtonWidget button : styledButtons) {
            drawButtonFrame(context, button);
        }

        super.render(context, mouseX, mouseY, delta);

        // Draw header again after widgets so it is never hidden.
        drawCenteredWithOutline(context, "J.A.R.V.I.S", this.width / 2, panelY + 16, 0xFFF2FDFF, 0xFF2EAEDB);
        drawCenteredWithOutline(context, "configuration menu", this.width / 2, panelY + 32, 0xFFA4EBFF, 0xFF275F79);
    }

    private Text learnLabel() {
        return Text.literal("Learn From Player: " + (config.isLearnFromPlayer() ? "TRUE" : "FALSE"));
    }

    private Text enabledLabel() {
        return Text.literal("Jarvis to be: " + (config.isJarvisEnabled() ? "ON" : "OFF"));
    }

    private Text sortingLabel() {
        return Text.literal("Inventory Sorting UI: " + (config.isInventorySortingEnabled() ? "ON" : "OFF"));
    }

    private Text durabilityHudLabel() {
        return Text.literal("Durability HUD: " + (config.isDurabilityHudEnabled() ? "ON" : "OFF"));
    }

    private Text resetDataLabel() {
        return Text.literal(resetConfirmArmed ? "Reset Learned Data: CLICK AGAIN" : "Reset Learned Data");
    }

    private void saveAndClose() {
        if (apiKeyField != null) {
            config.setApiKey(apiKeyField.getText());
        }
        config.save();
        close();
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | (blue & 255);
    }

    private int getPanelX() {
        return this.width / 2 - PANEL_W / 2;
    }

    private int getPanelY() {
        return Math.max(6, this.height / 2 - PANEL_H / 2);
    }

    private void drawCenteredWithOutline(DrawContext context, String text, int centerX, int y, int color, int outline) {
        int x = centerX - (this.textRenderer.getWidth(text) / 2);
        context.drawText(this.textRenderer, Text.literal(text), x - 1, y, outline, true);
        context.drawText(this.textRenderer, Text.literal(text), x + 1, y, outline, true);
        context.drawText(this.textRenderer, Text.literal(text), x, y - 1, outline, true);
        context.drawText(this.textRenderer, Text.literal(text), x, y + 1, outline, true);
        context.drawText(this.textRenderer, Text.literal(text), x, y, color, true);
    }

    private void addStyledButton(ButtonWidget button) {
        styledButtons.add(button);
        addDrawableChild(button);
    }

    private void drawButtonFrame(DrawContext context, ButtonWidget button) {
        int x1 = button.getX() - 1;
        int y1 = button.getY() - 1;
        int x2 = button.getX() + button.getWidth() + 1;
        int y2 = button.getY() + button.getHeight() + 1;

        context.fill(x1, y1, x2, y2, argb(120, 3, 20, 34));
        context.fill(x1, y1, x2, y1 + 1, argb(200, 49, 196, 246));
        context.fill(x1, y2 - 1, x2, y2, argb(165, 24, 130, 176));
        context.fill(x1, y1, x1 + 1, y2, argb(165, 30, 160, 210));
        context.fill(x2 - 1, y1, x2, y2, argb(165, 30, 160, 210));
    }

    private void resetJarvisDataFiles() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        deleteIfExists(configDir.resolve("jarvis-habits.properties"));
        deleteIfExists(configDir.resolve("jarvis-waypoints.properties"));
        deleteIfExists(configDir.resolve("jarvis-welcomes.properties"));
    }

    private void deleteIfExists(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Ignore deletion errors to avoid breaking UI flow.
        }
    }
}

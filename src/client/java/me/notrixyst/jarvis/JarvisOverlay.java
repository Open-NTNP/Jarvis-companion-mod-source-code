package me.notrixyst.jarvis;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;

public final class JarvisOverlay {

    private static final int MAX_MESSAGES = 4;
    private static final int HANDLE_WIDTH = 62;
    private static final int HANDLE_HEIGHT = 10;

    private final Deque<String> messages = new ArrayDeque<>();
    private final KeyBinding toggleKey;

    private boolean expanded = true;
    private float openProgress = 1.0f;
    private boolean previousLeftClick = false;

    private float responsePulse = 0.0f;
    private float speechEnergy = 0.0f;
    private float spinDegrees = 0.0f;
    private float spinDegreesAlt = 0.0f;
    private int ticks = 0;
    private int renderRecoveryTicks = 0;

    public JarvisOverlay() {
        this.toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.jarvis.overlay_toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_GRAVE_ACCENT,
                KeyBinding.Category.MISC
        ));
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(MinecraftClient.getInstance(), drawContext));
    }

    public void addMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        if (messages.size() >= MAX_MESSAGES) {
            messages.removeFirst();
        }
        messages.addLast(message);

        responsePulse = 1.0f;
        speechEnergy = 1.0f;
    }

    private void onTick(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }

        while (toggleKey.wasPressed()) {
            expanded = !expanded;
        }

        boolean leftDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;
        if (leftDown && !previousLeftClick && client.currentScreen != null) {
            int scaledWidth = client.getWindow().getScaledWidth();
            int scaledHeight = client.getWindow().getScaledHeight();
            double mouseX = client.mouse.getX() * (double) scaledWidth / client.getWindow().getWidth();
            double mouseY = client.mouse.getY() * (double) scaledHeight / client.getWindow().getHeight();
            if (inHandleBounds((int) mouseX, (int) mouseY, scaledHeight)) {
                expanded = !expanded;
            }
        }
        previousLeftClick = leftDown;

        float target = expanded ? 1.0f : 0.0f;
        openProgress += (target - openProgress) * 0.17f;
        openProgress = MathHelper.clamp(openProgress, 0.0f, 1.0f);

        responsePulse *= 0.915f;
        responsePulse = MathHelper.clamp(responsePulse, 0.0f, 1.0f);
        speechEnergy *= 0.955f;
        speechEnergy = MathHelper.clamp(speechEnergy, 0.0f, 1.0f);
        spinDegrees = (spinDegrees + 2.35f + speechEnergy * 1.3f) % 360.0f;
        spinDegreesAlt = (spinDegreesAlt - (1.05f + speechEnergy * 0.8f)) % 360.0f;
        ticks++;
        if (renderRecoveryTicks > 0) {
            renderRecoveryTicks--;
        }
    }

    private void render(MinecraftClient client, DrawContext context) {
        if (client == null || client.player == null || client.options.hudHidden) {
            return;
        }
        if (!JarvisConfig.getInstance().isJarvisEnabled()) {
            return;
        }

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int anchorX = MathHelper.clamp(92, 52, Math.max(52, width - 52));
        int anchorY = MathHelper.clamp(height - 116, 62, Math.max(62, height - 34));

        int handleX = anchorX - 6;
        int handleY = anchorY - 32;

        int coreX = anchorX;
        int coreY = anchorY - (int) ((1.0f - openProgress) * 24.0f);

        try {
            if (renderRecoveryTicks > 0) {
                renderFallback(context, client, coreX, coreY, handleX, handleY);
                return;
            }

            drawHandle(context, client, handleX, handleY);
            if (openProgress < 0.04f) {
                return;
            }

            float pulse = responsePulse * 5.2f;

            int animatedX = coreX;
            int animatedY = coreY;

            int auraAlpha = (int) ((45 + pulse * 20) * openProgress);

            drawRing(context, animatedX, animatedY, (int) (40 + pulse), 195, 0, 238, 255, 1.2f, 4.2f);
            drawRing(context, animatedX, animatedY, 35, auraAlpha, 0, 182, 238, 1.0f, 2.2f);
            drawRing(context, animatedX, animatedY, 28, (int) (170 * openProgress), 94, 230, 255, 1.0f, 2.0f);
            drawRing(context, animatedX, animatedY, 20, (int) (135 * openProgress), 0, 131, 190, 0.9f, 1.9f);

            drawSegmentBand(context, animatedX, animatedY, 41, 24, spinDegrees * 0.32f, (int) (150 * openProgress), 58, 220, 255);
            drawTickBand(context, animatedX, animatedY, 43, 3.0f, (int) (122 * openProgress), 70, 212, 255);
            drawTickBand(context, animatedX, animatedY, 31, 6.0f, (int) (95 * openProgress), 31, 150, 205);

            drawArc(context, animatedX, animatedY, 39, spinDegrees, 122, (int) (188 * openProgress), 25, 196, 238, 2);
            drawArc(context, animatedX, animatedY, 33, spinDegreesAlt + 180.0f, 94, (int) (205 * openProgress), 255, 196, 46, 2);
            drawArc(context, animatedX, animatedY, 27, spinDegrees * 1.31f + 32.0f, 88, (int) (170 * openProgress), 58, 220, 255, 1);

            drawOrbitDots(context, animatedX, animatedY, 33, spinDegrees * 1.15f, (int) (210 * openProgress));

            if (JarvisConfig.getInstance().isDurabilityHudEnabled()) {
                int telemetryX = Math.max(6, animatedX - 98);
                renderDurabilityTelemetry(context, client, telemetryX, animatedY - 54);
            }

            String line = messages.isEmpty() ? "Awaiting input..." : truncate(messages.getLast(), 30);
            int textColor = blendTextColor(0xFF88DFFF, 0xFFE9FCFF, speechEnergy);
            context.drawText(client.textRenderer, Text.literal(line), animatedX + 52, animatedY - 4, textColor, false);

            // Keep title draw last so no other HUD layer can cover it.
            drawCoreText(context, client, animatedX, animatedY);
        } catch (RuntimeException ignored) {
            // Prevent one bad draw frame from permanently breaking HUD rendering.
            renderRecoveryTicks = 40;
            renderFallback(context, client, coreX, coreY, handleX, handleY);
        }
    }

    private static void drawHandle(DrawContext context, MinecraftClient client, int x, int y) {
        context.drawText(client.textRenderer, Text.literal("JARVIS"), x + 12, y + 1, 0x9BE9FF, false);
    }

    private static void drawCoreText(DrawContext context, MinecraftClient client, int centerX, int centerY) {
        String title = "J.A.R.V.I.S.";
        int titleY = centerY - 4;
        int titleWidth = client.textRenderer.getWidth(title);
        int titleX = centerX - (titleWidth / 2);

        // Draw on top of the HUD core with an explicit outline.
        context.drawText(client.textRenderer, Text.literal(title), titleX - 1, titleY, 0xFF052231, true);
        context.drawText(client.textRenderer, Text.literal(title), titleX + 1, titleY, 0xFF052231, true);
        context.drawText(client.textRenderer, Text.literal(title), titleX, titleY - 1, 0xFF052231, true);
        context.drawText(client.textRenderer, Text.literal(title), titleX, titleY + 1, 0xFF052231, true);
        context.drawText(client.textRenderer, Text.literal(title), titleX, titleY, 0xFFFFFFFF, false);

    }

    private static void renderDurabilityTelemetry(DrawContext context, MinecraftClient client, int baseX, int baseY) {
        if (client.player == null) {
            return;
        }

        ItemStack[] telemetry = {
                client.player.getEquippedStack(EquipmentSlot.HEAD),
                client.player.getEquippedStack(EquipmentSlot.CHEST),
                client.player.getEquippedStack(EquipmentSlot.LEGS),
                client.player.getEquippedStack(EquipmentSlot.FEET),
                client.player.getMainHandStack(),
                client.player.getOffHandStack()
        };

        for (int i = 0; i < telemetry.length; i++) {
            ItemStack stack = telemetry[i];
            int y = baseY + (i * 20);
            drawTechFrame(context, baseX, y, 18, 18);
            if (stack.isEmpty()) {
                context.fill(baseX + 4, y + 4, baseX + 14, y + 14, argb(90, 20, 60, 82));
                continue;
            }

            context.drawItem(stack, baseX + 1, y + 1);
            drawDurabilityText(context, client, stack, baseX + 22, y + 6);
        }
    }

    private static void drawDurabilityText(DrawContext context, MinecraftClient client, ItemStack stack, int x, int y) {
        String value;
        int color;
        if (!stack.isDamageable()) {
            value = "INF";
            color = 0xFF86E3FF;
        } else {
            int max = stack.getMaxDamage();
            int left = Math.max(0, max - stack.getDamage());
            value = Integer.toString(left);
            float pct = max <= 0 ? 0.0f : (left / (float) max);
            int red = (int) (255 * (1.0f - pct));
            int green = (int) (255 * pct);
            color = (0xFF << 24) | (red << 16) | (green << 8) | 0x6A;
        }

        String clipped = value.length() > 5 ? value.substring(0, 5) : value;
        drawOutlinedText(context, client, clipped, x, y, color);
    }

    private static void drawTechFrame(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, argb(70, 7, 26, 38));
        context.fill(x, y, x + width, y + 1, argb(190, 51, 194, 244));
        context.fill(x, y + height - 1, x + width, y + height, argb(150, 30, 138, 173));
        context.fill(x, y, x + 1, y + height, argb(170, 43, 170, 215));
        context.fill(x + width - 1, y, x + width, y + height, argb(170, 43, 170, 215));
    }

    private static void drawRing(DrawContext context, int cx, int cy, int radius, int alpha, int red, int green, int blue, float thickness, float stepDegrees) {
        for (float r = radius; r < radius + thickness; r += 0.9f) {
            for (float deg = 0.0f; deg < 360.0f; deg += stepDegrees) {
                double rad = Math.toRadians(deg);
                int x = (int) (cx + Math.cos(rad) * r);
                int y = (int) (cy + Math.sin(rad) * r);
                context.fill(x, y, x + 1, y + 1, argb(alpha, red, green, blue));
            }
        }
    }

    private static void drawTickBand(DrawContext context, int cx, int cy, int radius, float stepDeg, int alpha, int red, int green, int blue) {
        for (float deg = 0.0f; deg < 360.0f; deg += stepDeg) {
            double rad = Math.toRadians(deg);
            int x = (int) (cx + Math.cos(rad) * radius);
            int y = (int) (cy + Math.sin(rad) * radius);
            context.fill(x, y, x + 1, y + 1, argb(alpha, red, green, blue));
        }
    }

    private static void drawSegmentBand(DrawContext context, int cx, int cy, int radius, int segments, float shiftDeg, int alpha, int red, int green, int blue) {
        float arcSize = 7.0f;
        float gap = 360.0f / segments;
        for (int i = 0; i < segments; i++) {
            float start = i * gap + shiftDeg;
            drawArc(context, cx, cy, radius, start, arcSize, alpha, red, green, blue, 1);
        }
    }

    private static void drawArc(DrawContext context, int cx, int cy, int radius, float startDegrees, float sweepDegrees, int alpha, int red, int green, int blue, int pointSize) {
        float step = 2.0f;
        for (float deg = startDegrees; deg < startDegrees + sweepDegrees; deg += step) {
            double rad = Math.toRadians(deg);
            int x = (int) (cx + Math.cos(rad) * radius);
            int y = (int) (cy + Math.sin(rad) * radius);
            context.fill(x, y, x + pointSize, y + pointSize, argb(alpha, red, green, blue));
        }
    }

    private static void drawOrbitDots(DrawContext context, int cx, int cy, int radius, float phaseDeg, int alpha) {
        for (int i = 0; i < 6; i++) {
            float deg = phaseDeg + i * 60.0f;
            double rad = Math.toRadians(deg);
            int x = (int) (cx + Math.cos(rad) * radius);
            int y = (int) (cy + Math.sin(rad) * radius);
            context.fill(x, y, x + 2, y + 2, argb(alpha, 255, 214, 64));
        }
    }

    private static void drawOutlinedText(DrawContext context, MinecraftClient client, String text, int x, int y, int color) {
        context.drawText(client.textRenderer, Text.literal(text), x - 1, y, 0xFF081620, false);
        context.drawText(client.textRenderer, Text.literal(text), x + 1, y, 0xFF081620, false);
        context.drawText(client.textRenderer, Text.literal(text), x, y - 1, 0xFF081620, false);
        context.drawText(client.textRenderer, Text.literal(text), x, y + 1, 0xFF081620, false);
        context.drawText(client.textRenderer, Text.literal(text), x, y, color, false);
    }

    private static int blendTextColor(int base, int bright, float t) {
        float clamped = MathHelper.clamp(t, 0.0f, 1.0f);
        int ba = (base >> 24) & 255;
        int br = (base >> 16) & 255;
        int bg = (base >> 8) & 255;
        int bb = base & 255;

        int ta = (bright >> 24) & 255;
        int tr = (bright >> 16) & 255;
        int tg = (bright >> 8) & 255;
        int tb = bright & 255;

        int ra = (int) (ba + (ta - ba) * clamped);
        int rr = (int) (br + (tr - br) * clamped);
        int rg = (int) (bg + (tg - bg) * clamped);
        int rb = (int) (bb + (tb - bb) * clamped);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | (blue & 255);
    }

    private static boolean inHandleBounds(int mouseX, int mouseY, int scaledHeight) {
        int handleX = 78 - 6;
        int handleY = scaledHeight - 148;
        return mouseX >= handleX && mouseX <= handleX + HANDLE_WIDTH && mouseY >= handleY && mouseY <= handleY + HANDLE_HEIGHT;
    }

    private static String truncate(String value, int maxLen) {
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLen - 3)) + "...";
    }

    private static void renderFallback(DrawContext context, MinecraftClient client, int coreX, int coreY, int handleX, int handleY) {
        drawHandle(context, client, handleX, handleY);
        drawRing(context, coreX, coreY, 26, 155, 42, 197, 245, 1.0f, 6.0f);
        drawRing(context, coreX, coreY, 18, 130, 27, 141, 188, 1.0f, 7.5f);
        drawCoreText(context, client, coreX, coreY);
    }
}

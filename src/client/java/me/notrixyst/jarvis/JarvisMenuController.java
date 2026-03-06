package me.notrixyst.jarvis;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class JarvisMenuController {

    private KeyBinding openMenuKey;

    public void register() {
        KeyBinding.Category jarvisCategory = KeyBinding.Category.create(Identifier.of("jarvis", "controls"));
        this.openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.jarvis.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                jarvisCategory
        ));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        if (openMenuKey == null) {
            return;
        }
        while (openMenuKey.wasPressed()) {
            client.setScreen(new JarvisConfigScreen(client.currentScreen));
        }
    }
}

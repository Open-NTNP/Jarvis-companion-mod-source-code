package me.notrixyst.jarvis;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class JarvisInventorySorter {

    private static SortMode activeMode = SortMode.TOOLS;

    public void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof InventoryScreen)) {
                return;
            }
            if (!JarvisConfig.getInstance().isInventorySortingEnabled()) {
                return;
            }

            int left = (scaledWidth - 176) / 2;
            int top = (scaledHeight - 166) / 2;
            int x = left + 178;
            int y = top + 8;

            ButtonWidget modeButton = ButtonWidget.builder(
                    Text.literal("Mode: " + activeMode.label),
                    button -> {
                        activeMode = activeMode.next();
                        button.setMessage(Text.literal("Mode: " + activeMode.label));
                    }
            ).dimensions(x, y, 92, 20).build();

            ButtonWidget sortButton = ButtonWidget.builder(
                    Text.literal("JARVIS Sort"),
                    button -> sortOpenInventory(client)
            ).dimensions(x, y + 22, 92, 20).build();

            Screens.getButtons(screen).add(modeButton);
            Screens.getButtons(screen).add(sortButton);
        });
    }

    private static void sortOpenInventory(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) {
            return;
        }

        // Player inventory slots in PlayerScreenHandler:
        // 9-35 main inventory, 36-44 hotbar.
        List<Integer> slots = new ArrayList<>();
        for (int slot = 9; slot <= 44; slot++) {
            slots.add(slot);
        }

        List<ItemStack> current = new ArrayList<>(slots.size());
        for (int slotId : slots) {
            current.add(client.player.playerScreenHandler.getSlot(slotId).getStack().copy());
        }

        List<ItemStack> target = new ArrayList<>(current);
        target.sort(stackComparator(activeMode));

        int syncId = client.player.playerScreenHandler.syncId;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack desired = target.get(i);
            ItemStack have = current.get(i);
            if (sameStack(have, desired)) {
                continue;
            }

            int swapIndex = -1;
            for (int j = i + 1; j < slots.size(); j++) {
                if (sameStack(current.get(j), desired)) {
                    swapIndex = j;
                    break;
                }
            }

            if (swapIndex < 0) {
                continue;
            }

            int slotA = slots.get(i);
            int slotB = slots.get(swapIndex);

            client.interactionManager.clickSlot(syncId, slotA, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(syncId, slotB, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(syncId, slotA, 0, SlotActionType.PICKUP, client.player);

            ItemStack temp = current.get(i);
            current.set(i, current.get(swapIndex));
            current.set(swapIndex, temp);
        }
    }

    private static Comparator<ItemStack> stackComparator(SortMode mode) {
        if (mode == SortMode.ALPHABETICAL) {
            return Comparator
                    .comparing((ItemStack stack) -> stack.isEmpty())
                    .thenComparing(stack -> stack.isEmpty() ? "zzzzzz" : stack.getName().getString().toLowerCase(Locale.ROOT))
                    .thenComparing(ItemStack::getCount, Comparator.reverseOrder());
        }

        return Comparator
                .comparingInt((ItemStack stack) -> categoryRank(mode, classify(stack)))
                .reversed()
                .thenComparing(stack -> stack.isEmpty() ? "zzzzzz" : stack.getName().getString().toLowerCase(Locale.ROOT))
                .thenComparing(ItemStack::getCount, Comparator.reverseOrder());
    }

    private static int categoryRank(SortMode mode, ItemCategory category) {
        if (category == ItemCategory.EMPTY) {
            return -1;
        }

        return switch (mode) {
            case TOOLS -> switch (category) {
                case TOOLS -> 4;
                case MATERIALS -> 3;
                case BLOCKS -> 2;
                case OTHER -> 1;
                case EMPTY -> 0;
            };
            case MATERIALS -> switch (category) {
                case MATERIALS -> 4;
                case TOOLS -> 3;
                case BLOCKS -> 2;
                case OTHER -> 1;
                case EMPTY -> 0;
            };
            case BLOCKS -> switch (category) {
                case BLOCKS -> 4;
                case MATERIALS -> 3;
                case TOOLS -> 2;
                case OTHER -> 1;
                case EMPTY -> 0;
            };
            case ALPHABETICAL -> switch (category) {
                case EMPTY -> 0;
                default -> 1;
            };
        };
    }

    private static ItemCategory classify(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemCategory.EMPTY;
        }

        String idPath = Registries.ITEM.getId(stack.getItem()).getPath().toLowerCase(Locale.ROOT);

        if (stack.isDamageable()
                || idPath.contains("sword")
                || idPath.contains("pickaxe")
                || idPath.contains("axe")
                || idPath.contains("shovel")
                || idPath.contains("hoe")
                || idPath.contains("bow")
                || idPath.contains("crossbow")
                || idPath.contains("trident")
                || idPath.contains("shield")
                || idPath.contains("helmet")
                || idPath.contains("chestplate")
                || idPath.contains("leggings")
                || idPath.contains("boots")) {
            return ItemCategory.TOOLS;
        }

        if (stack.getItem() instanceof BlockItem) {
            return ItemCategory.BLOCKS;
        }

        if (idPath.contains("ingot")
                || idPath.contains("nugget")
                || idPath.contains("gem")
                || idPath.contains("dust")
                || idPath.contains("shard")
                || idPath.contains("scrap")
                || idPath.contains("raw_")
                || idPath.contains("coal")
                || idPath.contains("quartz")
                || idPath.contains("amethyst")
                || idPath.contains("string")
                || idPath.contains("leather")
                || idPath.contains("bone")
                || idPath.contains("feather")) {
            return ItemCategory.MATERIALS;
        }

        return ItemCategory.OTHER;
    }

    private static boolean sameStack(ItemStack a, ItemStack b) {
        return a.getCount() == b.getCount() && ItemStack.areItemsAndComponentsEqual(a, b);
    }

    private enum ItemCategory {
        TOOLS,
        MATERIALS,
        BLOCKS,
        OTHER,
        EMPTY
    }

    private enum SortMode {
        TOOLS("Tools"),
        MATERIALS("Materials"),
        BLOCKS("Blocks"),
        ALPHABETICAL("A-Z");

        private final String label;

        SortMode(String label) {
            this.label = label;
        }

        private SortMode next() {
            int idx = ordinal() + 1;
            if (idx >= values().length) {
                idx = 0;
            }
            return values()[idx];
        }
    }
}

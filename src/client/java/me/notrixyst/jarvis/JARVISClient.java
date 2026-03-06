package me.notrixyst.jarvis;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.Registries;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public class JARVISClient implements ClientModInitializer {

    private static final Map<String, PotionRecipe> POTION_RECIPES = createPotionRecipes();
    private static final Map<String, CommandPlaybook> COMMAND_PLAYBOOKS = createCommandPlaybooks();

    private final Map<String, Integer> habits = new HashMap<>();
    private final Path habitsFile = FabricLoader.getInstance().getConfigDir().resolve("jarvis-habits.properties");
    private final Map<String, Waypoint> waypoints = new HashMap<>();
    private final Path waypointsFile = FabricLoader.getInstance().getConfigDir().resolve("jarvis-waypoints.properties");
    private final Path welcomesFile = FabricLoader.getInstance().getConfigDir().resolve("jarvis-welcomes.properties");
    private final JarvisOverlay overlay = new JarvisOverlay();
    private final JarvisInventorySorter inventorySorter = new JarvisInventorySorter();
    private final JarvisMenuController menuController = new JarvisMenuController();
    private final JarvisConfig config = JarvisConfig.getInstance();
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final Map<String, Boolean> welcomedWorlds = new HashMap<>();

    private boolean assistanceMode = false;

    private boolean listening = false;

    @Override
    public void onInitializeClient() {
        config.load();
        loadHabits();
        loadWaypoints();
        loadWelcomes();
        overlay.register();
        inventorySorter.register();
        menuController.register();
        overlay.addMessage("Systems initialized.");
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onWorldJoin(client));
        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
            String input = message.trim();
            String inputLower = input.toLowerCase(Locale.ROOT);

            if (!config.isJarvisEnabled()) {
                listening = false;
                assistanceMode = false;
                return true;
            }

            if (inputLower.equals("jarvis")) {
                listening = true;
                reply(buildWakeReply());
                return false;
            }

            if (listening) {
                handleInput(inputLower, input);
                listening = false;
                return false;
            }
            return true;
        });
    }

    private void onWorldJoin(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }
        if (!config.isJarvisEnabled()) {
            return;
        }

        String worldKey = buildWorldKey(client);
        if (worldKey == null || worldKey.isBlank()) {
            return;
        }
        if (welcomedWorlds.getOrDefault(worldKey, false)) {
            return;
        }

        welcomedWorlds.put(worldKey, true);
        saveWelcomes();

        String playerName = client.player.getName().getString();
        reply("Hi " + playerName + ". J.A.R.V.I.S online,Sir.");
        reply("How to start: type 'jarvis' in chat, then send your request,Sir.");
        reply("Try: recipe for torch, how do you work?, or assistance mode,Sir.");
        reply("Open configuration with JarvisMenuKeybind in Controls,Sir.");
    }

    private String buildWorldKey(MinecraftClient client) {
        if (client.getCurrentServerEntry() != null) {
            String address = client.getCurrentServerEntry().address;
            if (address != null && !address.isBlank()) {
                return "mp:" + address.trim().toLowerCase(Locale.ROOT);
            }
        }

        if (client.getServer() != null) {
            try {
                Path root = client.getServer().getSavePath(WorldSavePath.ROOT);
                if (root != null) {
                    return "sp:" + root.toAbsolutePath().normalize();
                }
            } catch (Exception ignored) {
                // Fallback below.
            }
        }

        if (client.world != null) {
            return "fallback:" + client.world.getRegistryKey().getValue();
        }
        return null;
    }

    private void handleInput(String requestLower, String requestRaw) {
        if (requestLower.equals("how do you work?") || requestLower.equals("how do you work")) {
            explainCapabilities();
            return;
        }

        if (requestLower.equals("switch to assistance mode")
                || requestLower.equals("assistance mode")
                || requestLower.equals("switch to gemini")
                || requestLower.equals("gemini mode")) {
            assistanceMode = true;
            reply("Assistance mode enabled. Ask me anything. For live answers, set GEMINI_API_KEY first,Sir.");
            return;
        }

        if (requestLower.equals("switch to local mode")) {
            assistanceMode = false;
            reply("Local mode enabled,Sir.");
            return;
        }

        if (requestLower.equals("clip that") || requestLower.equals("clip this")) {
            handleClipRequest();
            return;
        }

        if (requestLower.startsWith("whisper to ")) {
            handleWhisperRequest(requestRaw);
            return;
        }

        if (isWaypointSaveRequest(requestLower)) {
            handleWaypointSave(requestRaw);
            return;
        }

        if (isWaypointLookupRequest(requestLower)) {
            handleWaypointLookup(requestRaw);
            return;
        }

        if (requestLower.startsWith("browse")) {
            handleBrowseRequest(requestRaw);
            return;
        }

        if (containsAdvancementIntent(requestLower)) {
            handleAdvancementRequest(requestRaw);
            return;
        }

        if (requestLower.contains("youtube")) {
            String query = requestLower.replaceAll(".*youtube for |.*youtube ", "").trim();
            reply("Searching YouTube...");
            Util.getOperatingSystem().open("https://www.youtube.com/results?search_query=" + query.replace(" ", "+"));
            return;
        }

        if (containsCommandBlockIntent(requestLower)) {
            handleCommandBlockRequest(requestLower);
            return;
        }

        if (containsCommandIntent(requestLower)) {
            handleCommandRequest(requestLower);
            return;
        }

        if (containsRecipeIntent(requestLower)) {
            String target = extractRecipeTarget(requestLower);
            handleRecipeRequest(target);
            return;
        }

        if (assistanceMode) {
            handleAssistancePrompt(requestRaw);
            return;
        }

        reply("Ask me: recipe for <item>, whisper to <user> <message>, save waypoint as <name>, or where waypoint <name> is.");
    }

    private void explainCapabilities() {
        reply("I can do: recipes (items/potions), command help, command-block guides, whisper mode, and waypoints,Sir.");
        reply("Commands: whisper to <username> <message>, save waypoint as <name>, where waypoint <name> is?,Sir.");
        reply("Modes: switch to assistance mode, switch to local mode. Open config from your JARVIS keybind in Controls,Sir.");
    }

    private void handleAssistancePrompt(String prompt) {
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("GEMINI_API_KEY");
        }
        apiKey = normalizeApiKey(apiKey);
        if (apiKey == null || apiKey.isBlank()) {
            reply("Assistance mode needs an API key. Set it in the JARVIS configuration menu or GEMINI_API_KEY,Sir.");
            return;
        }

        String contextualPrompt = buildContextualAssistancePrompt(prompt);
        String body = "{\"contents\":[{\"parts\":[{\"text\":" + toJsonString(contextualPrompt) + "}]}]}";
        // Discover model paths dynamically, then fall back to known common model names.
        List<String> modelPaths = buildGeminiModelPaths(apiKey);

        String lastError = null;
        for (String modelPath : modelPaths) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://generativelanguage.googleapis.com/" + modelPath + "?key=" + apiKey))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    String errorMessage = extractGeminiErrorMessage(response.body());
                    lastError = "status " + response.statusCode() + (errorMessage == null ? "" : " - " + errorMessage);
                    continue;
                }

                String text = extractGeminiText(response.body());
                if (text == null || text.isBlank()) {
                    lastError = "response had no text";
                    continue;
                }

                reply(text.trim());
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                reply("Gemini request interrupted,Sir.");
                return;
            } catch (IOException e) {
                lastError = "connection error: " + e.getClass().getSimpleName();
            }
        }

        if (lastError == null) {
            reply("Gemini request failed for an unknown reason,Sir.");
        } else {
            reply("Gemini request failed: " + lastError + ",Sir.");
        }
    }

    private List<String> buildGeminiModelPaths(String apiKey) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();

        // Prefer discovery from listModels so we use currently supported model names.
        paths.addAll(discoverGeminiModelPaths(apiKey, "v1beta"));
        paths.addAll(discoverGeminiModelPaths(apiKey, "v1"));

        // Static fallbacks in case listModels is blocked.
        paths.add("v1beta/models/gemini-2.5-flash:generateContent");
        paths.add("v1beta/models/gemini-2.5-flash-lite:generateContent");
        paths.add("v1beta/models/gemini-2.0-flash:generateContent");
        paths.add("v1beta/models/gemini-2.0-flash-lite:generateContent");
        paths.add("v1beta/models/gemini-1.5-flash:generateContent");
        paths.add("v1/models/gemini-2.5-flash:generateContent");
        paths.add("v1/models/gemini-2.5-flash-lite:generateContent");
        paths.add("v1/models/gemini-2.0-flash:generateContent");
        paths.add("v1/models/gemini-2.0-flash-lite:generateContent");
        paths.add("v1/models/gemini-1.5-flash:generateContent");

        return new ArrayList<>(paths);
    }

    private List<String> discoverGeminiModelPaths(String apiKey, String apiVersion) {
        List<String> discovered = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/" + apiVersion + "/models?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return discovered;
            }

            // Lightweight parser: pull model names whose supportedGenerationMethods include generateContent.
            String json = response.body();
            int index = 0;
            while (true) {
                int nameKey = json.indexOf("\"name\":\"models/", index);
                if (nameKey < 0) {
                    break;
                }
                int nameStart = nameKey + "\"name\":\"".length();
                int nameEnd = json.indexOf('"', nameStart);
                if (nameEnd < 0) {
                    break;
                }
                String modelName = json.substring(nameStart, nameEnd);

                int nextName = json.indexOf("\"name\":\"models/", nameEnd);
                int sectionEnd = nextName < 0 ? json.length() : nextName;
                String modelSection = json.substring(nameEnd, sectionEnd);
                if (modelSection.contains("generateContent")) {
                    discovered.add(apiVersion + "/" + modelName + ":generateContent");
                }
                index = nameEnd + 1;
            }
        } catch (Exception ignored) {
            // Ignore discovery failures and rely on fallback list.
        }
        return discovered;
    }

    private String buildContextualAssistancePrompt(String userPrompt) {
        ZonedDateTime now = ZonedDateTime.now();
        String formattedNow = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy HH:mm:ss z"));

        MinecraftClient client = MinecraftClient.getInstance();
        String playerName = "unknown";
        String position = "unknown";
        String dimension = "unknown";
        if (client != null && client.player != null) {
            playerName = client.player.getName().getString();
            BlockPos pos = client.player.getBlockPos();
            position = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            if (client.world != null) {
                dimension = client.world.getRegistryKey().getValue().toString();
            }
        }

        return """
                You are J.A.R.V.I.S in a Minecraft client assistant.
                Use this live context as authoritative for time-sensitive facts:
                - Current local date/time: %s
                - Current year: %d
                - Time zone: %s
                - Player: %s
                - Player position (X,Y,Z): %s
                - Dimension: %s

                When the user asks about "today", "now", current date, year, or time, use the live context above.
                User request:
                %s
                """.formatted(
                formattedNow,
                now.getYear(),
                now.getZone().toString(),
                playerName,
                position,
                dimension,
                userPrompt
        );
    }

    private static String normalizeApiKey(String apiKey) {
        if (apiKey == null) {
            return null;
        }
        String normalized = apiKey.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private static String extractGeminiText(String json) {
        String marker = "\"text\":";
        int idx = json.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        int start = json.indexOf('"', idx + marker.length());
        if (start < 0) {
            return null;
        }
        int i = start + 1;
        boolean escaped = false;
        StringBuilder builder = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> builder.append('\n');
                    case 't' -> builder.append('\t');
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    default -> builder.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                builder.append(c);
            }
            i++;
        }
        return builder.toString();
    }

    private static String extractGeminiErrorMessage(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        String marker = "\"message\":";
        int idx = json.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        int start = json.indexOf('"', idx + marker.length());
        if (start < 0) {
            return null;
        }
        int i = start + 1;
        boolean escaped = false;
        StringBuilder builder = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> builder.append(' ');
                    case 't' -> builder.append(' ');
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    default -> builder.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                builder.append(c);
            }
            i++;
        }
        String msg = builder.toString().trim();
        return msg.isEmpty() ? null : msg;
    }

    private static String toJsonString(String raw) {
        String escaped = raw
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        return "\"" + escaped + "\"";
    }

    private void handleCommandRequest(String request) {
        if (isCommandCatalogRequest(request)) {
            recordHabit("command:catalog");
            reply("Core commands: give, tp, effect, summon, fill, clone, execute, scoreboard, data.");
            String favoriteCommands = summarizeTopHabits("command:", 2);
            if (!favoriteCommands.isEmpty()) {
                reply("You usually ask about: " + favoriteCommands + ".");
            }
            sendSuggestion("Help", "/help");
            sendSuggestion("Teleport", "/tp @s 0 100 0");
            sendSuggestion("Give Item", "/give @s minecraft:diamond 1");
            sendSuggestion("Summon", "/summon minecraft:zombie ~ ~ ~");
            sendSuggestion("Set Time", "/time set day");
            return;
        }

        String target = extractCommandTarget(request);
        Optional<CommandPlaybook> playbook = matchCommandPlaybook(target);

        if (playbook.isEmpty()) {
            recordHabit("command:unknown");
            reply("I can help with teleport, kits, healing, mob control, building, scoreboard, and automation.");
            String likely = topHabitLabel("command:");
            if (likely != null) {
                reply("Based on your history, you may want: " + likely + ".");
            }
            sendSuggestion("Command List", "/help");
            Util.getOperatingSystem().open("https://minecraft.wiki/w/Commands");
            return;
        }

        CommandPlaybook plan = playbook.get();
        recordHabit("command:" + normalize(plan.name));
        reply("Task: " + plan.name);
        reply(plan.description);
        for (CommandExample example : plan.examples) {
            sendSuggestion(example.label, example.command);
        }
    }

    private void handleCommandBlockRequest(String request) {
        if (request.contains("types") || request.contains("difference") || request.contains("impulse") || request.contains("repeat") || request.contains("chain")) {
            recordHabit("commandblock:types");
            reply("Impulse runs once. Repeat loops each tick. Chain runs after previous block succeeds.");
            reply("Use: Repeat + Always Active for scanners, then Chain + Always Active for follow-up actions.");
            sendSuggestion("Get Block", "/give @s command_block");
            sendSuggestion("Get Chain", "/give @s chain_command_block");
            sendSuggestion("Get Repeat", "/give @s repeating_command_block");
            return;
        }

        String target = request.replace("command block", "").replace("with command blocks", "").trim();
        Optional<CommandPlaybook> playbook = matchCommandPlaybook(target);

        if (playbook.isEmpty()) {
            recordHabit("commandblock:unknown");
            reply("Command block workflow: place Impulse/Repeat, set Always Active or Redstone, then chain extra actions.");
            reply("Ask: command block setup for starter kit, mob cleanup, teleport hub, or area effects.");
            return;
        }

        CommandPlaybook plan = playbook.get();
        recordHabit("commandblock:" + normalize(plan.name));
        reply("Command block setup for: " + plan.name);
        reply("Step 1: Place a Repeat block and set Always Active for continuous checks.");
        reply("Step 2: Add Chain blocks (Always Active, Conditional if needed) for extra actions.");
        reply("Step 3: Use Impulse blocks with buttons for one-time actions.");
        for (CommandExample example : plan.examples) {
            sendSuggestion(example.label, example.command);
        }
    }

    private void handleRecipeRequest(String rawTarget) {
        String target = rawTarget.trim();
        if (target.isEmpty()) {
            recordHabit("recipe:empty");
            reply("Tell me the item, for example: recipe for observer.");
            return;
        }

        if (looksLikePotion(target)) {
            recordHabit("recipe:potion:" + normalizePotionEffect(target));
            List<String> steps = buildPotionSteps(target);
            for (String step : steps) {
                reply(step);
            }
            return;
        }

        Optional<ItemMatch> match = matchItem(target);
        if (match.isEmpty()) {
            recordHabit("recipe:unknown:" + normalize(target));
            reply("I could not identify that item. Try the Minecraft id, e.g. recipe for redstone_lamp.");
            return;
        }

        ItemMatch item = match.get();
        recordHabit("recipe:item:" + item.id.getPath());
        reply("Recipe target: " + item.displayName + " (" + item.id + ")");
        reply("Use recipe book unlock, then open your inventory recipe book.");
        sendSuggestion("Unlock Recipe", "/recipe give @s " + item.id);
        sendSuggestion("Get Item", "/give @s " + item.id);
        Util.getOperatingSystem().open("https://minecraft.wiki/w/" + item.id.getPath());
    }

    private static boolean containsRecipeIntent(String request) {
        return request.contains("recipe")
                || request.contains("how to craft")
                || request.contains("how to make")
                || request.contains("how do i make")
                || request.contains("brew")
                || request.contains("potion");
    }

    private static boolean containsCommandIntent(String request) {
        return request.contains("with commands")
                || request.contains("with command")
                || request.contains("what commands can i use")
                || request.contains("which commands can i use")
                || request.contains("command for")
                || request.contains("commands for")
                || request.contains("how to") && request.contains("command")
                || request.startsWith("/");
    }

    private static boolean containsCommandBlockIntent(String request) {
        return request.contains("command block")
                || request.contains("chain block")
                || request.contains("repeating block")
                || request.contains("impulse block")
                || request.contains("with command blocks");
    }

    private static boolean isCommandCatalogRequest(String request) {
        return request.contains("what commands can i use")
                || request.contains("which commands can i use")
                || request.contains("command list")
                || request.equals("commands")
                || request.equals("command");
    }

    private static String extractRecipeTarget(String request) {
        String[] markers = {
                "recipe for ",
                "how to craft ",
                "how to make ",
                "how do i make ",
                "brew ",
                "recipe ",
                "craft ",
                "make "
        };

        for (String marker : markers) {
            int index = request.indexOf(marker);
            if (index >= 0) {
                return request.substring(index + marker.length()).trim();
            }
        }

        return request;
    }

    private static String extractCommandTarget(String request) {
        String[] markers = {
                "how to ",
                "command for ",
                "commands for ",
                "with commands",
                "with command",
                "using commands",
                "use commands for "
        };

        String value = request;
        for (String marker : markers) {
            int idx = value.indexOf(marker);
            if (idx >= 0 && marker.endsWith(" ")) {
                value = value.substring(idx + marker.length()).trim();
                break;
            }
        }

        return value
                .replace("with commands", "")
                .replace("with command", "")
                .replace("using commands", "")
                .replace("using command", "")
                .replace("command", "")
                .trim();
    }

    private static Optional<CommandPlaybook> matchCommandPlaybook(String target) {
        String normalized = normalize(target);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        CommandPlaybook best = null;
        int bestScore = -1;

        for (CommandPlaybook candidate : COMMAND_PLAYBOOKS.values()) {
            int score = 0;
            for (String keyword : candidate.keywords) {
                String nKeyword = normalize(keyword);
                if (normalized.equals(nKeyword)) {
                    score += 100;
                } else if (normalized.contains(nKeyword)) {
                    score += 30;
                } else if (nKeyword.contains(normalized)) {
                    score += 20;
                }
            }

            for (String token : normalized.split(" ")) {
                if (token.length() < 3) {
                    continue;
                }
                for (String keyword : candidate.keywords) {
                    if (normalize(keyword).contains(token)) {
                        score += 8;
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (bestScore < 28) {
            return Optional.empty();
        }

        return Optional.ofNullable(best);
    }

    private static boolean looksLikePotion(String target) {
        String normalized = normalize(target);
        if (normalized.contains("potion")) {
            return true;
        }

        String effect = normalizePotionEffect(target);
        return POTION_RECIPES.containsKey(effect);
    }

    private static List<String> buildPotionSteps(String target) {
        String normalizedTarget = normalize(target);
        String effect = normalizePotionEffect(target);

        boolean splash = normalizedTarget.contains("splash");
        boolean lingering = normalizedTarget.contains("lingering");
        boolean strong = normalizedTarget.contains(" ii") || normalizedTarget.contains("strong");
        boolean extended = normalizedTarget.contains("long") || normalizedTarget.contains("extended");

        List<String> steps = new ArrayList<>();
        PotionRecipe recipe = POTION_RECIPES.get(effect);

        if (recipe == null) {
            steps.add("I could not map that potion exactly. Try: potion of swiftness, healing, strength, poison, or weakness.");
            return steps;
        }

        if (recipe.effectIngredient == null) {
            steps.add("Base brew: Water Bottle + " + recipe.baseIngredient + " -> " + recipe.displayName + ".");
        } else {
            steps.add("Step 1: Water Bottle + " + recipe.baseIngredient + " -> " + recipe.baseName + ".");
            steps.add("Step 2: " + recipe.baseName + " + " + recipe.effectIngredient + " -> " + recipe.displayName + ".");
        }

        if (strong) {
            if (recipe.supportsStrong) {
                steps.add("Step 3: Add Glowstone Dust for the stronger variant (Potion II).");
            } else {
                steps.add("This potion has no stronger (II) variant.");
            }
        }

        if (extended) {
            if (recipe.supportsExtended) {
                steps.add("Step 3: Add Redstone Dust for the longer-duration variant.");
            } else {
                steps.add("This potion has no extended-duration variant.");
            }
        }

        if (splash || lingering) {
            steps.add("Final: Add Gunpowder to convert it into a Splash Potion.");
        }

        if (lingering) {
            steps.add("Final+: Add Dragon's Breath to the splash version for a Lingering Potion.");
        }

        return steps;
    }

    private static String normalizePotionEffect(String target) {
        String normalized = normalize(target)
                .replace("recipe for", "")
                .replace("how to craft", "")
                .replace("how to make", "")
                .replace("how do i make", "")
                .replace("brew", "")
                .replace("splash", "")
                .replace("lingering", "")
                .replace("potion", "")
                .replace("of", "")
                .replace("ii", "")
                .replace("strong", "")
                .replace("long", "")
                .replace("extended", "")
                .trim();

        return normalized.replaceAll("\\s+", " ");
    }

    private static Optional<ItemMatch> matchItem(String target) {
        String normalizedTarget = normalize(target).replace(" ", "_");
        String looseTarget = normalize(target);

        ItemMatch best = null;
        int bestScore = -1;

        for (Identifier id : Registries.ITEM.getIds()) {
            if (!"minecraft".equals(id.getNamespace())) {
                continue;
            }

            Item item = Registries.ITEM.get(id);
            String path = id.getPath();
            String displayName = item.getName().getString();
            String display = normalize(displayName);

            int score = scoreMatch(normalizedTarget, looseTarget, path, display);
            if (score > bestScore) {
                bestScore = score;
                best = new ItemMatch(id, displayName);
            }
        }

        if (bestScore < 45) {
            return Optional.empty();
        }

        return Optional.of(best);
    }

    private static int scoreMatch(String normalizedTarget, String looseTarget, String path, String display) {
        if (path.equals(normalizedTarget) || display.equals(looseTarget)) {
            return 100;
        }

        int score = 0;
        if (path.contains(normalizedTarget)) {
            score += 65;
        }
        if (normalizedTarget.contains(path)) {
            score += 45;
        }
        if (display.contains(looseTarget)) {
            score += 55;
        }
        if (looseTarget.contains(display)) {
            score += 35;
        }

        String[] tokens = looseTarget.split(" ");
        for (String token : tokens) {
            if (token.length() < 2) {
                continue;
            }
            if (path.contains(token)) {
                score += 8;
            }
            if (display.contains(token)) {
                score += 8;
            }
        }

        return score;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void handleWhisperRequest(String rawInput) {
        String trimmed = rawInput.trim();
        String prefix = "whisper to ";
        if (trimmed.length() <= prefix.length()) {
            reply("Use: whisper to <username> <message>");
            return;
        }

        String payload = trimmed.substring(prefix.length()).trim();
        int firstSpace = payload.indexOf(' ');
        if (firstSpace <= 0 || firstSpace >= payload.length() - 1) {
            reply("Use: whisper to <username> <message>");
            return;
        }

        String username = payload.substring(0, firstSpace).trim();
        String message = payload.substring(firstSpace + 1).trim();
        if (username.isEmpty() || message.isEmpty()) {
            reply("Use: whisper to <username> <message>");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            reply("Cannot whisper right now. You are not connected to a world.");
            return;
        }

        client.getNetworkHandler().sendChatCommand("msg " + username + " " + message);
        recordHabit("command:whisper");
        reply("Whisper sent to " + username + ",Sir.");
    }

    private boolean isWaypointSaveRequest(String requestLower) {
        return requestLower.contains("save waypoint as ") || requestLower.contains("save landmark as ");
    }

    private boolean isWaypointLookupRequest(String requestLower) {
        return requestLower.contains("where waypoint ") || requestLower.contains("where is waypoint ");
    }

    private void handleWaypointSave(String rawInput) {
        String lower = rawInput.toLowerCase(Locale.ROOT);
        String marker = lower.contains("save waypoint as ") ? "save waypoint as " : "save landmark as ";
        int start = lower.indexOf(marker);
        if (start < 0) {
            reply("Use: save waypoint as <name>");
            return;
        }

        String name = rawInput.substring(start + marker.length()).trim();
        name = sanitizeWaypointName(name);
        if (name.isEmpty()) {
            reply("Waypoint name is empty. Use: save waypoint as <name>");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            reply("Cannot save waypoint right now.");
            return;
        }

        BlockPos pos = client.player.getBlockPos();
        RegistryKey<World> dimension = client.world.getRegistryKey();
        Waypoint waypoint = new Waypoint(pos.getX(), pos.getY(), pos.getZ(), dimension.getValue().toString());

        waypoints.put(name.toLowerCase(Locale.ROOT), waypoint);
        saveWaypoints();
        recordHabit("waypoint:save");
        reply("Waypoint " + name + " saved at " + waypoint.x + ", " + waypoint.y + ", " + waypoint.z + ",Sir.");
    }

    private void handleWaypointLookup(String rawInput) {
        String lower = rawInput.toLowerCase(Locale.ROOT).trim();
        String name;
        if (lower.contains("where is waypoint ")) {
            int idx = lower.indexOf("where is waypoint ");
            name = rawInput.substring(idx + "where is waypoint ".length()).trim();
        } else {
            int idx = lower.indexOf("where waypoint ");
            name = rawInput.substring(idx + "where waypoint ".length()).trim();
            if (name.toLowerCase(Locale.ROOT).endsWith(" is")) {
                name = name.substring(0, name.length() - 3).trim();
            }
        }

        name = sanitizeWaypointName(name);
        if (name.isEmpty()) {
            reply("Use: where waypoint <name> is?");
            return;
        }

        Waypoint waypoint = waypoints.get(name.toLowerCase(Locale.ROOT));
        if (waypoint == null) {
            reply("I could not find waypoint " + name + ",Sir.");
            return;
        }

        recordHabit("waypoint:lookup");
        reply("Waypoint " + name + " is at X " + waypoint.x + ", Y " + waypoint.y + ", Z " + waypoint.z
                + " in " + waypoint.dimension + ",Sir.");
    }

    private void handleBrowseRequest(String rawInput) {
        String trimmed = rawInput.trim();
        String query = trimmed.length() > "browse".length()
                ? trimmed.substring("browse".length()).trim()
                : "";

        if (query.isEmpty()) {
            reply("Use: browse <keywords>,Sir.");
            return;
        }

        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        Util.getOperatingSystem().open("https://www.google.com/search?q=" + encoded);
        recordHabit("command:browse");
        reply("Browsing for: " + query + ",Sir.");
    }

    private void handleClipRequest() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getFramebuffer() == null) {
            reply("Cannot take screenshot right now,Sir.");
            return;
        }

        ScreenshotRecorder.saveScreenshot(client.runDirectory, client.getFramebuffer(), message ->
                client.execute(() -> reply(message.getString() + ",Sir."))
        );
        recordHabit("command:clip");
    }

    private static boolean containsAdvancementIntent(String requestLower) {
        return requestLower.startsWith("how to get advancement ")
                || requestLower.startsWith("how do i get advancement ")
                || requestLower.startsWith("advancement help ")
                || requestLower.startsWith("how to get the advancement ");
    }

    private void handleAdvancementRequest(String rawInput) {
        String lower = rawInput.toLowerCase(Locale.ROOT).trim();
        String[] prefixes = {
                "how to get advancement ",
                "how do i get advancement ",
                "advancement help ",
                "how to get the advancement "
        };

        String name = "";
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                name = rawInput.substring(prefix.length()).trim();
                break;
            }
        }

        if (name.isBlank()) {
            reply("Use: how to get advancement <name>,Sir.");
            return;
        }

        String query = "minecraft advancement " + name;
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        Util.getOperatingSystem().open("https://minecraft.wiki/w/Special:Search?search=" + encoded);
        recordHabit("advancement:help");
        reply("Opening the Minecraft Wiki guide for advancement " + name + ",Sir.");
        reply("Tip: you can also use /advancement grant @s only <namespace:path> for testing,Sir.");
    }

    private static String sanitizeWaypointName(String value) {
        return value.replace("?", "")
                .replace(".", "")
                .replace(",", "")
                .trim();
    }

    private String buildWakeReply() {
        String topRecipe = topHabitLabel("recipe:item:");
        String topCommand = topHabitLabel("command:");
        if (topRecipe != null && topCommand != null) {
            return "Yes, sir? Last favorites: recipe " + topRecipe + ", commands " + topCommand + ".";
        }
        if (topRecipe != null) {
            return "Yes, sir? You often ask recipes for " + topRecipe + ".";
        }
        if (topCommand != null) {
            return "Yes, sir? You often use " + topCommand + " commands.";
        }
        return "Yes, sir?";
    }

    private void recordHabit(String key) {
        if (!config.isLearnFromPlayer()) {
            return;
        }
        habits.merge(key, 1, Integer::sum);
        saveHabits();
    }

    private String summarizeTopHabits(String prefix, int limit) {
        List<String> labels = habits.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue()).reversed())
                .limit(limit)
                .map(entry -> stripHabitPrefix(prefix, entry.getKey()))
                .toList();
        if (labels.isEmpty()) {
            return "";
        }
        return String.join(", ", labels);
    }

    private String topHabitLabel(String prefix) {
        return habits.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(entry -> stripHabitPrefix(prefix, entry.getKey()))
                .orElse(null);
    }

    private static String stripHabitPrefix(String prefix, String key) {
        String label = key.substring(prefix.length());
        return label.replace('_', ' ').trim();
    }

    private void loadHabits() {
        if (!Files.exists(habitsFile)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(habitsFile)) {
            properties.load(in);
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key, "0");
                try {
                    habits.put(key, Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed entries and keep loading.
                }
            }
        } catch (IOException ignored) {
            // Ignore IO issues and continue without persisted habits.
        }
    }

    private void saveHabits() {
        Properties properties = new Properties();
        for (Map.Entry<String, Integer> entry : habits.entrySet()) {
            properties.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
        try {
            Files.createDirectories(habitsFile.getParent());
            try (OutputStream out = Files.newOutputStream(habitsFile)) {
                properties.store(out, "JARVIS user habits");
            }
        } catch (IOException ignored) {
            // Ignore IO issues to avoid interrupting gameplay features.
        }
    }

    private void loadWaypoints() {
        if (!Files.exists(waypointsFile)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(waypointsFile)) {
            properties.load(in);
            for (String key : properties.stringPropertyNames()) {
                String[] parts = properties.getProperty(key, "").split(",", 4);
                if (parts.length != 4) {
                    continue;
                }
                try {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    String dimension = parts[3];
                    waypoints.put(key.toLowerCase(Locale.ROOT), new Waypoint(x, y, z, dimension));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed waypoint entry.
                }
            }
        } catch (IOException ignored) {
            // Ignore IO issues and continue without persisted waypoints.
        }
    }

    private void saveWaypoints() {
        Properties properties = new Properties();
        for (Map.Entry<String, Waypoint> entry : waypoints.entrySet()) {
            Waypoint wp = entry.getValue();
            properties.setProperty(entry.getKey(), wp.x + "," + wp.y + "," + wp.z + "," + wp.dimension);
        }
        try {
            Files.createDirectories(waypointsFile.getParent());
            try (OutputStream out = Files.newOutputStream(waypointsFile)) {
                properties.store(out, "JARVIS waypoints");
            }
        } catch (IOException ignored) {
            // Ignore IO issues to avoid interrupting gameplay features.
        }
    }

    private void loadWelcomes() {
        if (!Files.exists(welcomesFile)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(welcomesFile)) {
            properties.load(in);
            for (String key : properties.stringPropertyNames()) {
                welcomedWorlds.put(key, Boolean.parseBoolean(properties.getProperty(key, "false")));
            }
        } catch (IOException ignored) {
            // Ignore IO issues and continue without persisted welcomes.
        }
    }

    private void saveWelcomes() {
        Properties properties = new Properties();
        for (Map.Entry<String, Boolean> entry : welcomedWorlds.entrySet()) {
            properties.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
        try {
            Files.createDirectories(welcomesFile.getParent());
            try (OutputStream out = Files.newOutputStream(welcomesFile)) {
                properties.store(out, "JARVIS one-time world/server welcomes");
            }
        } catch (IOException ignored) {
            // Ignore IO issues to avoid interrupting gameplay features.
        }
    }

    private void reply(String text) {
        overlay.addMessage(text);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                    Text.literal("[J.A.R.V.I.S.] ").formatted(Formatting.AQUA)
                            .append(Text.literal(text).formatted(Formatting.WHITE)),
                    false
            );
        }
    }

    private static void sendSuggestion(String label, String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                    Text.literal("[J.A.R.V.I.S.] ").formatted(Formatting.AQUA)
                            .append(Text.literal(label + ": ").formatted(Formatting.GREEN))
                            .append(Text.literal("[LOAD]").styled(style ->
                                    style.withColor(Formatting.YELLOW)
                                            .withClickEvent(new ClickEvent.SuggestCommand(command)))),
                    false
            );
        }
    }

    private static Map<String, PotionRecipe> createPotionRecipes() {
        Map<String, PotionRecipe> recipes = new LinkedHashMap<>();
        recipes.put("awkward", new PotionRecipe("Nether Wart", "Awkward Potion", "Awkward Potion", null, false, false));
        recipes.put("thick", new PotionRecipe("Glowstone Dust", "Thick Potion", "Thick Potion", null, false, false));
        recipes.put("mundane", new PotionRecipe("Redstone Dust", "Mundane Potion", "Mundane Potion", null, false, false));

        recipes.put("night vision", new PotionRecipe("Nether Wart", "Awkward Potion", "Potion of Night Vision", "Golden Carrot", true, false));
        recipes.put("invisibility", new PotionRecipe("Nether Wart", "Awkward Potion", "Potion of Invisibility", "Golden Carrot, then Fermented Spider Eye", true, false));
        recipes.put("leaping", new PotionRecipe("Nether Wart", "Awkward Potion", "Potion of Leaping", "Rabbit's Foot", true, true));
        recipes.put("swiftness", new PotionRecipe("Nether Wart", "Awkward Potion", "Potion of Swiftness", "Sugar", true, true));
        recipes.put("slowness", new PotionRecipe("Nether Wart", "Awkward Potion", "Potion of Slowness", "Sugar then Fermented Spider Eye", true, false));
        recipes.put("water breathing", new PotionRecipe("Nether Wart", "Awkward Potion", "Potion of Water Breathing", "Pufferfish", true, false));
        recipes.put("healing", new PotionRecipe("Nether Wart", "Awkward Potion", "Potion of Healing", "Glistering Melon Slice", false, true));
        recipes.put("harming", new PotionRecipe("Nether Wart", "Awkward Potion", "Potion of Harming", "Glistering Melon Slice then Fermented Spider Eye", false, true));
        recipes.put("poison", new PotionRecipe("Nether Wart", "Awkward Potion", "Potion of Poison", "Spider Eye", true, true));
        recipes.put("regeneration", new PotionRecipe("Nether Wart", "Awkward Potion", "Potion of Regeneration", "Ghast Tear", true, true));
        recipes.put("strength", new PotionRecipe("Nether Wart", "Awkward Potion", "Potion of Strength", "Blaze Powder", true, true));
        recipes.put("weakness", new PotionRecipe("Fermented Spider Eye", "Water Bottle", "Potion of Weakness", null, true, false));
        recipes.put("slow falling", new PotionRecipe("Nether Wart", "Awkward Potion", "Potion of Slow Falling", "Phantom Membrane", true, false));
        recipes.put("turtle master", new PotionRecipe("Nether Wart", "Awkward Potion", "Potion of the Turtle Master", "Turtle Helmet", false, true));
        return recipes;
    }

    private static Map<String, CommandPlaybook> createCommandPlaybooks() {
        Map<String, CommandPlaybook> playbooks = new LinkedHashMap<>();

        playbooks.put("teleport", new CommandPlaybook(
                "Teleport Players",
                "Move yourself or players to coordinates, entities, or each other.",
                List.of("teleport", "tp", "warp", "spawn", "move player"),
                List.of(
                        new CommandExample("TP To Coords", "/tp @s 100 64 100"),
                        new CommandExample("TP To Player", "/tp @s @p"),
                        new CommandExample("Set Spawn", "/spawnpoint @s ~ ~ ~")
                )
        ));

        playbooks.put("kit", new CommandPlaybook(
                "Starter Kits",
                "Give players gear and food quickly.",
                List.of("kit", "starter", "loadout", "gear", "give items"),
                List.of(
                        new CommandExample("Give Sword", "/give @p minecraft:iron_sword"),
                        new CommandExample("Give Food", "/give @p minecraft:cooked_beef 16"),
                        new CommandExample("Give Armor", "/give @p minecraft:iron_chestplate")
                )
        ));

        playbooks.put("heal", new CommandPlaybook(
                "Healing and Effects",
                "Heal players, clear effects, or apply buffs.",
                List.of("heal", "health", "regeneration", "buff", "effect"),
                List.of(
                        new CommandExample("Instant Heal", "/effect give @p minecraft:instant_health 1 1 true"),
                        new CommandExample("Regen", "/effect give @p minecraft:regeneration 10 1 true"),
                        new CommandExample("Clear Effects", "/effect clear @p")
                )
        ));

        playbooks.put("mobcontrol", new CommandPlaybook(
                "Mob Control",
                "Summon, remove, or manage entities in an area.",
                List.of("mob", "entities", "clear mobs", "kill mobs", "summon"),
                List.of(
                        new CommandExample("Summon Zombie", "/summon minecraft:zombie ~ ~ ~"),
                        new CommandExample("Kill Hostile Mobs", "/kill @e[type=#minecraft:raiders,distance=..80]"),
                        new CommandExample("Kill Nearby Monsters", "/kill @e[type=minecraft:zombie,distance=..64]")
                )
        ));

        playbooks.put("build", new CommandPlaybook(
                "Fast Building",
                "Create or copy structures with fill and clone.",
                List.of("build", "fill", "clone", "replace blocks", "structure"),
                List.of(
                        new CommandExample("Fill Area", "/fill ~ ~ ~ ~10 ~5 ~10 minecraft:stone"),
                        new CommandExample("Clear Area", "/fill ~ ~ ~ ~10 ~5 ~10 minecraft:air"),
                        new CommandExample("Clone Area", "/clone ~ ~ ~ ~5 ~5 ~5 ~20 ~ ~")
                )
        ));

        playbooks.put("scoreboard", new CommandPlaybook(
                "Scoreboards",
                "Track stats, points, and custom objectives.",
                List.of("scoreboard", "points", "stats", "leaderboard", "kills"),
                List.of(
                        new CommandExample("Create Objective", "/scoreboard objectives add kills playerKillCount"),
                        new CommandExample("Show Sidebar", "/scoreboard objectives setdisplay sidebar kills"),
                        new CommandExample("Add Points", "/scoreboard players add @p kills 1")
                )
        ));

        playbooks.put("timeweather", new CommandPlaybook(
                "Time and Weather",
                "Control day cycle and weather instantly.",
                List.of("time", "day", "night", "weather", "rain", "clear"),
                List.of(
                        new CommandExample("Set Day", "/time set day"),
                        new CommandExample("Set Night", "/time set night"),
                        new CommandExample("Clear Weather", "/weather clear")
                )
        ));

        return playbooks;
    }

    private record ItemMatch(Identifier id, String displayName) {
    }

    private record PotionRecipe(
            String baseIngredient,
            String baseName,
            String displayName,
            String effectIngredient,
            boolean supportsExtended,
            boolean supportsStrong
    ) {
    }

    private record CommandPlaybook(
            String name,
            String description,
            List<String> keywords,
            List<CommandExample> examples
    ) {
    }

    private record CommandExample(String label, String command) {
    }

    private record Waypoint(int x, int y, int z, String dimension) {
    }
}

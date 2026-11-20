package com.agentdid127.resourcepack.forwards.impl.other;

import com.agentdid127.resourcepack.forwards.ForwardsPackConverter;
import com.agentdid127.resourcepack.library.Converter;
import com.agentdid127.resourcepack.library.PackConverter;
import com.agentdid127.resourcepack.library.pack.Pack;
import com.agentdid127.resourcepack.library.utilities.JsonUtil;
import com.agentdid127.resourcepack.library.utilities.Logger;
import com.agentdid127.resourcepack.library.utilities.Util;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class ModelConverter extends Converter {
    private final int from;
    private final int to;
    protected String light = "none";

    private Pack pack;

    public ModelConverter(PackConverter packConverter, String light, int from, int to) {
        super(packConverter);
        this.light = light;
        this.from = from;
        this.to = to;
        pack = null;
    }

    @Override
    public boolean shouldConvert(Gson gson, int from, int to) {
        return true;
    }

    /**
     * Runs findfiles with the directory Models
     *
     * @param pack
     * @throws IOException
     */
    @Override
    public void convert(Pack pack) throws IOException {
        this.pack = pack;
        Path models = pack.getWorkingPath().resolve("assets/minecraft/models".replace("/", File.separator));
        if (models.toFile().exists()) {
            Logger.addTab();
            findFiles(models);
            Logger.subTab();
        }
    }

    /**
     * Recursively finds {@code .json} models under {@code rootPath} and remaps each.
     *
     * <p>Must walk nested folders (e.g. {@code item/presentation/}). A prior one-level
     * {@code remapModelJsons} left adventure Blockbench UVs unclamped and caused 26.x
     * {@code NativeImage} translucency OOB on TSF presentation boards.
     *
     * @param rootPath models root or subdirectory
     * @throws IOException on IO failure
     */
    protected void findFiles(Path rootPath) throws IOException {
        File directory = rootPath.toFile();
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File file : children) {
            Path filePath = file.toPath();
            if (file.isDirectory()) {
                findFiles(filePath);
            } else if (file.getName().endsWith(".json")) {
                remapModelJson(filePath.getParent(), filePath);
            }
        }
    }

    /**
     * Remaps every {@code .json} model directly under {@code rootPath} (non-recursive).
     * Prefer {@link #findFiles(Path)} when nested model folders must be converted.
     *
     * @param rootPath directory of model JSON files
     * @throws IOException on IO failure
     */
    protected void remapModelJsons(Path rootPath) throws IOException {
        if (rootPath.toFile().exists()) {
            try (Stream<Path> pathStream = Files.list(rootPath).filter(path1 -> path1.toString().endsWith(".json"))) {
                pathStream.forEach(modelPath -> {
                    try {
                        this.remapModelJson(rootPath, modelPath);
                    } catch (IOException e) {
                        Util.propagate(e);
                    }
                });
            }
        }
    }

    protected void remapModelJson(Path rootPath, Path model) throws IOException {
        if (!model.toFile().exists()) {
            return;
        }

        try {
            JsonObject jsonObject;
            if (JsonUtil.readJson(packConverter.getGson(), model) != null && JsonUtil.readJson(packConverter.getGson(), model).isJsonObject()) {
                jsonObject = JsonUtil.readJson(packConverter.getGson(), model);
            } else {
                Logger.debug("Could not convert model: " + model.getFileName());
                Logger.addTab();
                if (JsonUtil.readJson(packConverter.getGson(), model) == null) {
                    Logger.debug("Check for Syntax Errors in file.");
                } else {
                    Logger.debug("File is not JSON Object.");
                }
                Logger.subTab();
                return;
            }

            // GUI light system for 1.15.2
            if (!light.equals("none") && (light.equals("front") || light.equals("side"))) {
                Logger.log("Model did not have light, adding light '" + light + "'");
                jsonObject.addProperty("gui_light", light);
            }

            if (jsonObject.has("textures") && jsonObject.get("textures").isJsonObject()) {
                NameConverter nameConverter = packConverter.getConverter(NameConverter.class);

                JsonObject initialTextureObject = jsonObject.getAsJsonObject("textures");
                JsonObject textureObject = initialTextureObject.deepCopy();
                for (Map.Entry<String, JsonElement> entry : initialTextureObject.entrySet()) {
                    String value = entry.getValue().getAsString();
                    textureObject.remove(entry.getKey());

                    // 1.8→1.9: bare layer0 names (e.g. "diamond_sword") need the models
                    // folder prefix ("item/"). Do not rewrite paths that already include a
                    // directory (e.g. "models/armor/noxcrew") — that wrongly produced
                    // missing "item/models/armor/…" refs.
                    if (to >= Util.getVersionProtocol(packConverter.getGson(), "1.9")) {
                        if (entry.getKey().equals("layer0")) {
                            String folder = rootPath.getFileName().toString();
                            boolean bareName = !value.contains("/")
                                && !value.contains(":")
                                && !value.startsWith("#");
                            boolean missingFolderPrefix = !value.startsWith(folder + "/")
                                && !value.startsWith("minecraft:" + folder + "/");
                            if (bareName && missingFolderPrefix) {
                                value = folder + "/" + value;
                            }
                        }
                    }

                    // 1.13 Mappings
                    if (from <= Util.getVersionProtocol(packConverter.getGson(), "1.12.2") &&
                            to >= Util.getVersionProtocol(packConverter.getGson(), "1.13")) {
                        if (value.startsWith("blocks/")) {
                            value = "block/" + nameConverter.getBlockMapping_1_13().remap(value.substring("blocks/".length())).toLowerCase().replaceAll("[()]", "");
                        } else if (value.startsWith("items/")) {
                            value = "item/" + nameConverter.getItemMapping_1_13().remap(value.substring("items/".length())).toLowerCase().replaceAll("[()]", "");
                        } else {
                            value = value.toLowerCase().replaceAll("[()]", "");
                        }
                    }

                    // 1.14 Mappings
                    if (from <= Util.getVersionProtocol(packConverter.getGson(), "1.13.2") &&
                            to >= Util.getVersionProtocol(packConverter.getGson(), "1.14")) {
                        if (value.startsWith("block/")) {
                            value = "block/" + nameConverter.getBlockMapping_1_14().remap(value.substring("block/".length()));
                        }

                        // Dyes
                        if (value.startsWith("item/") && value.contains("dye")) {
                            value = "item/" + nameConverter.getItemMapping_1_14().remap(value.substring("item/".length()));
                        }
                    }

                    // 1.17 Mappings
                    if (from <= Util.getVersionProtocol(packConverter.getGson(), "1.16.5") &&
                            to >= Util.getVersionProtocol(packConverter.getGson(), "1.17")) {
                        if (value.startsWith("block/")) {
                            value = "block/" + nameConverter.getBlockMapping_1_17().remap(value.substring("block/".length())).toLowerCase().replaceAll("[()]", "");
                        } else if (value.startsWith("item/")) {
                            value = "item/" + nameConverter.getItemMapping_1_17().remap(value.substring("item/".length())).toLowerCase().replaceAll("[()]", "");
                        }

                        value = value.toLowerCase().replaceAll("[()]", "");
                    }

                    // 1.19 Mappings
                    if (from <= Util.getVersionProtocol(packConverter.getGson(), "1.18.2") &&
                            to >= Util.getVersionProtocol(packConverter.getGson(), "1.19")) {
                        if (value.startsWith("block/")) {
                            value = "block/" + nameConverter.getBlockMapping_1_19().remap(value.substring("block/".length())).toLowerCase().replaceAll("[()]", "");
                        }

                        value = value.toLowerCase().replaceAll("[()]", "");
                    }

                    // 1.19.3 Mappings — prefix bare paths only. Already-namespaced refs
                    // (e.g. tsf:npc/barney/…) must stay intact; "minecraft:" + "tsf:…"
                    // produces illegal minecraft:tsf:… resource locations.
                    if (from <= Util.getVersionProtocol(packConverter.getGson(), "1.19.2") &&
                            to >= Util.getVersionProtocol(packConverter.getGson(), "1.19.3")) {
                        if (!value.startsWith("#") && !value.contains(":")) {
                            value = "minecraft:" + value;
                        }
                    }

                    // 1.20.5 Mappings (scute -> turtle_scute)
                    if (from < Util.getVersionProtocol(packConverter.getGson(), "1.20.5") &&
                            to >= Util.getVersionProtocol(packConverter.getGson(), "1.20.5")) {
                        if (value.startsWith("minecraft:item/")) {
                            value = "minecraft:item/" + nameConverter.getItemMapping_1_20_5()
                                    .remap(value.substring("minecraft:item/".length()));
                        } else if (value.startsWith("item/")) {
                            value = "item/" + nameConverter.getItemMapping_1_20_5()
                                    .remap(value.substring("item/".length()));
                        }
                    }

                    // 1.21.9 Mappings (chain -> iron_chain)
                    if (from < Util.getVersionProtocol(packConverter.getGson(), "1.21.9") &&
                            to >= Util.getVersionProtocol(packConverter.getGson(), "1.21.9")) {
                        if (value.startsWith("minecraft:item/")) {
                            value = "minecraft:item/" + nameConverter.getItemMapping_1_21_9()
                                    .remap(value.substring("minecraft:item/".length()));
                        } else if (value.startsWith("item/")) {
                            value = "item/" + nameConverter.getItemMapping_1_21_9()
                                    .remap(value.substring("item/".length()));
                        } else if (value.startsWith("minecraft:block/")) {
                            value = "minecraft:block/" + nameConverter.getBlockMapping_1_21_9()
                                    .remap(value.substring("minecraft:block/".length()));
                        } else if (value.startsWith("block/")) {
                            value = "block/" + nameConverter.getBlockMapping_1_21_9()
                                    .remap(value.substring("block/".length()));
                        }
                    }

                    if (!textureObject.has(entry.getKey())) {
                        textureObject.addProperty(entry.getKey(), value);
                    }
                }

                jsonObject.remove("textures");
                jsonObject.add("textures", textureObject);
            }

            // Fix Display Model For Packs (<= 1.8.9)
            if (jsonObject.has("display") &&
                    from <= Util.getVersionProtocol(packConverter.getGson(), "1.8") &&
                    to >= Util.getVersionProtocol(packConverter.getGson(), "1.9")) {
                JsonObject display = updateDisplay(packConverter.getGson(), jsonObject.remove("display").getAsJsonObject());
                jsonObject.add("display", display);
            }

            if (jsonObject.has("overrides")) {
                for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                    if (entry.getKey().equals("overrides")) {
                        JsonArray overrides = jsonObject.get("overrides").getAsJsonArray();
                        JsonArray overridesArray = new JsonArray();
                        for (int i = 0; i < overrides.size(); i++) {
                            JsonObject object = overrides.get(i).getAsJsonObject();
                            for (Map.Entry<String, JsonElement> json : object.entrySet()) {
                                if (json.getKey().equals("model")) {
                                    object.addProperty(json.getKey(), json.getValue().getAsString().replaceAll("[()]", ""));
                                } else {
                                    object.add(json.getKey(), json.getValue());
                                }
                            }
                            overridesArray.add(object);
                        }
                        jsonObject.add(entry.getKey(), overridesArray);
                    }
                }
            }

            // Parent Stuff
            if (jsonObject.has("parent")) {
                // Change parent to lowercase
                for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                    if (entry.getKey().equals("parent")) {
                        String parent = entry.getValue().getAsString().toLowerCase().replace(" ", "_");

                        // Get block/item parents renamed
                        if (from < Util.getVersionProtocol(packConverter.getGson(), "1.13")
                                && to >= Util.getVersionProtocol(packConverter.getGson(), "1.13")) {
                            if (parent.startsWith("block/")) {
                                parent = setParent("block/", "/forwards/blocks.json", parent, "1_13");
                            } else if (parent.startsWith("item/")) {
                                parent = setParent("item/", "/forwards/items.json", parent, "1_13");
                            }
                        }

                        if (from < Util.getVersionProtocol(packConverter.getGson(), "1.14")
                                && to >= Util.getVersionProtocol(packConverter.getGson(), "1.14")) {
                            if (parent.startsWith("block/")) {
                                parent = setParent("block/", "/forwards/blocks.json", parent, "1_14");
                            } else if (parent.startsWith("item/")) {
                                parent = setParent("item/", "/forwards/items.json", parent, "1_14");
                            }
                        }

                        if (from < Util.getVersionProtocol(packConverter.getGson(), "1.17")
                                && to >= Util.getVersionProtocol(packConverter.getGson(), "1.17")) {
                            if (parent.startsWith("block/")) {
                                parent = setParent("block/", "/forwards/blocks.json", parent, "1_17");
                            } else if (parent.startsWith("item/")) {
                                parent = setParent("item/", "/forwards/items.json", parent, "1_17");
                            }
                        }

                        if (from < Util.getVersionProtocol(packConverter.getGson(), "1.19")
                                && to >= Util.getVersionProtocol(packConverter.getGson(), "1.19")) {
                            if (parent.startsWith("block/")) {
                                parent = setParent("block/", "/forwards/blocks.json", parent, "1_19");
                            }
                        }

                        if (to >= Util.getVersionProtocol(packConverter.getGson(), "1.19.3")
                                && from < Util.getVersionProtocol(packConverter.getGson(), "1.19.3")) {
                            // Preserve custom namespaces (tsf:…); only prefix bare parents.
                            if (!parent.startsWith("#") && !parent.contains(":")) {
                                parent = "minecraft:" + parent;
                            }
                        }

                        if (from < Util.getVersionProtocol(packConverter.getGson(), "1.20.5")
                                && to >= Util.getVersionProtocol(packConverter.getGson(), "1.20.5")) {
                            if (parent.startsWith("item/")) {
                                parent = setParent("item/", "/forwards/items.json", parent, "1_20_5");
                            } else if (parent.startsWith("minecraft:item/")) {
                                String remapped = setParent("item/", "/forwards/items.json",
                                        parent.substring("minecraft:".length()), "1_20_5");
                                parent = "minecraft:" + remapped;
                            }
                        }

                        if (from < Util.getVersionProtocol(packConverter.getGson(), "1.21.9")
                                && to >= Util.getVersionProtocol(packConverter.getGson(), "1.21.9")) {
                            if (parent.startsWith("item/")) {
                                parent = setParent("item/", "/forwards/items.json", parent, "1_21_9");
                            } else if (parent.startsWith("minecraft:item/")) {
                                String remapped = setParent("item/", "/forwards/items.json",
                                        parent.substring("minecraft:".length()), "1_21_9");
                                parent = "minecraft:" + remapped;
                            } else if (parent.startsWith("block/")) {
                                parent = setParent("block/", "/forwards/blocks.json", parent, "1_21_9");
                            } else if (parent.startsWith("minecraft:block/")) {
                                String remapped = setParent("block/", "/forwards/blocks.json",
                                        parent.substring("minecraft:".length()), "1_21_9");
                                parent = "minecraft:" + remapped;
                            }
                        }

                        jsonObject.addProperty(entry.getKey(), parent);
                    }
                }
            }


            // New Override System for 1.21.4+
            if (to >= Util.getVersionProtocol(packConverter.getGson(), "1.21.4")
                    && from < Util.getVersionProtocol(packConverter.getGson(), "1.21.4")) {

                // Check for new overrides
                if (jsonObject.has("overrides")) {
                    JsonArray overridesArray = jsonObject.get("overrides").getAsJsonArray();

                    // Convert damages, and model data
                    if (isDamageModel(overridesArray)) {
                        createDamageModel(jsonObject, model);
                    } else if (isCustomModelData(overridesArray)) {
                        createCustomModelData(jsonObject, model);
                    }

                    // remove old damages / CMD overrides (now in assets/minecraft/items/)
                    jsonObject.remove("overrides");

                    // Adventure hosts sometimes are textures+overrides only (no parent/elements).
                    // After stripping overrides the leftover is an invalid bake target while
                    // items/<host>.json fallback still points at minecraft:item/<host> — purple/black.
                    String hostStem = model.getFileName().toString();
                    if (hostStem.endsWith(".json")) {
                        hostStem = hostStem.substring(0, hostStem.length() - ".json".length());
                    }
                    ensureValidHostModel(jsonObject, hostStem);
                }
            }

            // 26.x FaceBakery/NativeImage.computeTransparency rejects UVs that round past the
            // sprite (e.g. Blockbench 16.03125 → pixel 513 on a 512² atlas). Clamp to [0, 16].
            if (to >= Util.getVersionProtocol(packConverter.getGson(), "26.1")
                    && clampElementFaceUvs(jsonObject)) {
                Logger.debug("Clamped out-of-range face UVs in " + model.getFileName());
            }

            if (!JsonUtil.readJson(packConverter.getGson(), model).equals(jsonObject)) {
                Logger.debug("Updating Model: " + model.getFileName());
                JsonUtil.writeJson(packConverter.getGson(), model, jsonObject);
            } else {
                Logger.debug("Skipping Model: " + model.getFileName());
            }
        } catch (IOException e) {
            throw Util.propagate(e);
        }
    }

    /**
     * Clamp face {@code uv} components into Minecraft's 0–16 model UV space.
     *
     * <p>Blockbench often emits values like {@code 16.03125}. With {@code texture_size}
     * 512 that becomes pixel 513; 26.x {@code NativeImage.computeTransparency} throws
     * and the item bakes as missing (purple/black) — TSF presentation board.
     *
     * @return true if any value was changed
     */
    private static boolean clampElementFaceUvs(JsonObject jsonObject) {
        if (jsonObject == null || !jsonObject.has("elements") || !jsonObject.get("elements").isJsonArray()) {
            return false;
        }
        boolean changed = false;
        for (JsonElement el : jsonObject.getAsJsonArray("elements")) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject element = el.getAsJsonObject();
            if (!element.has("faces") || !element.get("faces").isJsonObject()) {
                continue;
            }
            JsonObject faces = element.getAsJsonObject("faces");
            for (Map.Entry<String, JsonElement> faceEntry : faces.entrySet()) {
                if (!faceEntry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject face = faceEntry.getValue().getAsJsonObject();
                if (!face.has("uv") || !face.get("uv").isJsonArray()) {
                    continue;
                }
                JsonArray uv = face.getAsJsonArray("uv");
                JsonArray clamped = new JsonArray();
                boolean faceChanged = false;
                for (int i = 0; i < uv.size(); i++) {
                    if (!uv.get(i).isJsonPrimitive() || !uv.get(i).getAsJsonPrimitive().isNumber()) {
                        clamped.add(uv.get(i));
                        continue;
                    }
                    double v = uv.get(i).getAsDouble();
                    double c = Math.max(0.0, Math.min(16.0, v));
                    if (c != v) {
                        faceChanged = true;
                    }
                    // Prefer ints when the value is integral after clamp (keeps JSON tidy).
                    if (c == (long) c) {
                        clamped.add((long) c);
                    } else {
                        clamped.add(c);
                    }
                }
                if (faceChanged) {
                    face.add("uv", clamped);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * After CMD/damage overrides are migrated to {@code items/}, ensure the host
     * {@code models/item/<host>.json} is still a bakeable model.
     *
     * <p>TSF {@code white_stained_glass} is textures+overrides only; stripping overrides
     * leaves a stub. Replace with a vanilla block-item parent (client jar resolves it).
     * Do not reuse adventure {@code textures.layer0} — that was the wrong fallback.
     */
    private static void ensureValidHostModel(JsonObject jsonObject, String hostStem) {
        if (jsonObject == null || hostStem == null || hostStem.isEmpty()) {
            return;
        }
        boolean hasParent = jsonObject.has("parent") && jsonObject.get("parent").isJsonPrimitive();
        boolean hasElements = jsonObject.has("elements") && jsonObject.get("elements").isJsonArray()
                && jsonObject.getAsJsonArray("elements").size() > 0;
        if (hasParent || hasElements) {
            return;
        }
        // Clear invalid leftover (credit/textures-only) and install vanilla block-item stub.
        for (String key : new ArrayList<>(jsonObject.keySet())) {
            jsonObject.remove(key);
        }
        jsonObject.addProperty("parent", "minecraft:block/" + hostStem);
        Logger.debug("Restored vanilla block parent for override-stripped host model: " + hostStem);
    }

    /**
     * Creates a Damage-based item definition under assets/minecraft/items for 1.21.4+.
     * Schema matches vanilla item defs (minecraft:range_dispatch + minecraft:damage).
     */
    private void createDamageModel(JsonObject oldModel, Path original) throws IOException {
        writeRangeDispatchItemDef(oldModel, original, "damage", "damage");
    }

    /**
     * Creates a Custom Model Data item definition under assets/minecraft/items for 1.21.4+.
     * Schema matches wiki/vanilla: minecraft:range_dispatch + minecraft:custom_model_data,
     * fallback = minecraft:item/&lt;host&gt;, entry models namespaced.
     */
    private void createCustomModelData(JsonObject oldModel, Path original) throws IOException {
        writeRangeDispatchItemDef(oldModel, original, "custom_model_data", "custom_model_data");
    }

    /**
     * Emit assets/minecraft/items/&lt;host&gt;.json from legacy model overrides.
     *
     * @param predicateKey override predicate key (damage or custom_model_data)
     * @param propertyName un-namespaced numeric property id for range_dispatch
     */
    private void writeRangeDispatchItemDef(JsonObject oldModel, Path original, String predicateKey, String propertyName)
            throws IOException {
        if (pack == null) {
            return;
        }
        if (!oldModel.has("overrides") || !oldModel.get("overrides").isJsonArray()) {
            return;
        }

        String hostStem = original.getFileName().toString();
        if (hostStem.endsWith(".json")) {
            hostStem = hostStem.substring(0, hostStem.length() - ".json".length());
        }

        Path itemsPath = pack.getWorkingPath().resolve("assets/minecraft/items");
        if (!itemsPath.toFile().exists()) {
            itemsPath.toFile().mkdirs();
        }

        // Collect + sort by threshold so range_dispatch "last <= value" semantics match CMD order.
        List<JsonObject> sortedEntries = new ArrayList<>();
        for (JsonElement override : oldModel.get("overrides").getAsJsonArray()) {
            if (!override.isJsonObject()) {
                continue;
            }
            JsonObject overrideObj = override.getAsJsonObject();
            if (!overrideObj.has("predicate") || !overrideObj.get("predicate").isJsonObject()) {
                continue;
            }
            JsonObject predicate = overrideObj.get("predicate").getAsJsonObject();
            if (!predicate.has(predicateKey) || !overrideObj.has("model")) {
                continue;
            }

            JsonObject entry = new JsonObject();
            if (predicate.get(predicateKey).getAsJsonPrimitive().isNumber()) {
                // Prefer integer thresholds when the predicate was an int (CMD); keep doubles for damage.
                if ("custom_model_data".equals(predicateKey)
                        || predicate.get(predicateKey).getAsDouble() == predicate.get(predicateKey).getAsLong()) {
                    entry.addProperty("threshold", predicate.get(predicateKey).getAsLong());
                } else {
                    entry.addProperty("threshold", predicate.get(predicateKey).getAsDouble());
                }
            } else {
                continue;
            }

            JsonObject modelEntry = new JsonObject();
            modelEntry.addProperty("type", "minecraft:model");
            modelEntry.addProperty("model", normalizeItemModelId(overrideObj.get("model").getAsString()));
            entry.add("model", modelEntry);
            sortedEntries.add(entry);
        }

        sortedEntries.sort(Comparator.comparingDouble(a -> a.get("threshold").getAsDouble()));

        JsonArray entries = new JsonArray();
        for (JsonObject entry : sortedEntries) {
            entries.add(entry);
        }

        JsonObject fallback = new JsonObject();
        fallback.addProperty("type", "minecraft:model");
        // Host stem matches vanilla items/<id>.json → model minecraft:item/<id> (do not use custom layer0).
        fallback.addProperty("model", "minecraft:item/" + hostStem);

        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:range_dispatch");
        model.addProperty("property", "minecraft:" + propertyName);
        model.add("fallback", fallback);
        model.add("entries", entries);

        JsonObject out = new JsonObject();
        out.add("model", model);

        JsonUtil.writeJson(packConverter.getGson(), itemsPath.resolve(original.getFileName().toString()), out);
    }

    /**
     * Ensure model ids used in item defs are namespaced like vanilla (minecraft:item/…).
     * Leaves non-minecraft namespaces untouched.
     */
    private static String normalizeItemModelId(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return modelId;
        }
        String cleaned = modelId.replaceAll("[()]", "").trim();
        if (cleaned.contains(":")) {
            return cleaned;
        }
        return "minecraft:" + cleaned;
    }

    /**
     * Checks if overrides is damage-based
     *
     * @param overrides old overrides model
     * @return {@code true} if it is a damage-based model
     */
    private boolean isDamageModel(JsonArray overrides) {
        for (JsonElement override : overrides) {
            if (override instanceof JsonObject && override.getAsJsonObject().has("predicate")) {
                JsonObject predicate = override.getAsJsonObject().get("predicate").getAsJsonObject();
                if (predicate.has("damage") && !predicate.has("custom_model_data")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if overrides is CustomModelData-based
     *
     * @param overrides old overrides model
     * @return {@code true} if it is a CustomModelData-based model
     */
    private boolean isCustomModelData(JsonArray overrides) {
        for (JsonElement override : overrides) {
            if (override instanceof JsonObject && override.getAsJsonObject().has("predicate")) {
                JsonObject predicate = override.getAsJsonObject().get("predicate").getAsJsonObject();
                if (!predicate.has("damage") && predicate.has("custom_model_data")) {
                    return true;
                }
            }
        }
        return false;
    }
    /**
     * Gets parent object and sets a new one
     *
     * @param prefix prefix of file path
     * @param path   File path of json control
     * @param parent Parent String
     * @return New string with changed parent.
     */
    protected String setParent(String prefix, String path, String parent, String item) {
        String parentWithoutPrefix = parent.replace(prefix, "");
        JsonObject file = JsonUtil.readJsonResource(packConverter.getGson(), path).getAsJsonObject(item);
        if (file == null) {
            Logger.debug("Prefix Failed on: " + parent);
            return parent;
        } else {
            return file.has(parentWithoutPrefix) ? prefix + file.get(parentWithoutPrefix).getAsString() : parent;
        }
    }

    protected static JsonObject updateDisplay(Gson gson, JsonObject display) {
        JsonObject defaults = JsonUtil.readJsonResource(gson, "/forwards/display.json");
        if (display == null) {
            return defaults.deepCopy();
        }

        // First Person
        if (display.has("firstperson")) {
            JsonObject firstPerson = display.remove("firstperson").getAsJsonObject();
            display.add("firstperson_righthand", updateDisplayFirstPerson(gson, firstPerson));
        } else if (!display.has("firstperson_righthand")) {
            JsonObject rightHand = defaults.get("firstperson_righthand").getAsJsonObject().deepCopy();
            display.add("firstperson_righthand", rightHand);
        }

        if (!display.has("firstperson_lefthand")) {
            display.add("firstperson_lefthand", getLeftHand(display.get("firstperson_righthand").getAsJsonObject().deepCopy()));
        }

        // Third Person
        if (display.has("thirdperson")) {
            JsonObject thirdPerson = display.remove("thirdperson").getAsJsonObject();
            display.add("thirdperson_righthand", updateDisplayThirdPerson(gson, thirdPerson));
        } else if (!display.has("thirdperson_righthand")) {
            JsonObject rightHand = defaults.get("thirdperson_righthand").getAsJsonObject().deepCopy();
            display.add("thirdperson_righthand", rightHand);
        }

        if (!display.has("thirdperson_lefthand")) {
            display.add("thirdperson_lefthand", getLeftHand(display.get("thirdperson_righthand").getAsJsonObject().deepCopy()));
        }

        if (!display.has("ground")) {
            display.add("ground", defaults.get("ground").getAsJsonObject().deepCopy());
        }

        if (!display.has("head")) {
            display.add("head", defaults.get("head").getAsJsonObject().deepCopy());
        }

        return display;
    }

    private static JsonObject getLeftHand(JsonObject old) {
        JsonObject newObject = old.deepCopy();
        if (old.has("rotation")) {
            JsonArray oldRotation = newObject.remove("rotation").getAsJsonArray();
            JsonArray rotation = new JsonArray();
            rotation.add(oldRotation.get(0).getAsNumber());
            rotation.add(0 - oldRotation.get(1).getAsDouble());
            rotation.add(0 - oldRotation.get(2).getAsDouble());
            newObject.add("rotation", rotation);
        }

        return newObject;
    }

    private static JsonObject updateDisplayFirstPerson(Gson gson, JsonObject old) {
        JsonObject newObject = old.deepCopy();
        if (old.has("rotation")) {
            JsonArray rotation = newObject.remove("rotation").getAsJsonArray();
            newObject.add("rotation", JsonUtil.add(rotation, JsonUtil.asArray(gson, "[0, 45, 0]")));
        }

        if (old.has("translation")) {
            JsonArray translation = newObject.remove("translation").getAsJsonArray();
            newObject.add("translation",
                    JsonUtil.add(
                            JsonUtil.multiply(
                                    JsonUtil.subtract(
                                            translation,
                                            JsonUtil.asArray(gson, "[0, 4, 2]")),
                                    JsonUtil.asArray(gson, "[0.4, 0.4, 0.4]")),
                            JsonUtil.asArray(gson, "[1.13, 3.2, 1.13]")));
        }

        if (old.has("scale")) {
            JsonArray scale = newObject.remove("scale").getAsJsonArray();
            newObject.add("scale", JsonUtil.multiply(scale, JsonUtil.asArray(gson, "[0.4, 0.4, 0.4]")));
        }

        return newObject;
    }

    private static JsonObject updateDisplayThirdPerson(Gson gson, JsonObject old) {
        JsonObject newObject = old.deepCopy();
        if (old.has("rotation")) {
            JsonArray rotation = newObject.remove("rotation").getAsJsonArray();
            newObject.add("rotation",
                    JsonUtil.add(
                            JsonUtil.multiply(
                                    rotation,
                                    JsonUtil.asArray(gson, "[1, -1, -1]")),
                            JsonUtil.asArray(gson, "[0, 0, 20]")));
        }

        if (old.has("translation")) {
            JsonArray translation = newObject.remove("translation").getAsJsonArray();
            newObject.add("translation",
                    JsonUtil.add(
                            JsonUtil.multiply(
                                    translation,
                                    JsonUtil.asArray(gson, "[1, 1, -1]")),
                            JsonUtil.asArray(gson, "[0, 2.75, -3]")));
        }

        // For keeping order
        if (old.has("scale")) {
            JsonArray scale = newObject.remove("scale").getAsJsonArray();
            newObject.add("scale", scale);
        }

        return newObject;
    }
}

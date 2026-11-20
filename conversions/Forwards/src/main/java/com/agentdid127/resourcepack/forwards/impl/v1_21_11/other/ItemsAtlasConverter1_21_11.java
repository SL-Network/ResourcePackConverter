package com.agentdid127.resourcepack.forwards.impl.v1_21_11.other;

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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1.21.11 / pack_format 75.0 (snapshot 25w45a / format 73.0): item textures were split
 * out of the blocks atlas into a separate items atlas (no mipmaps).
 *
 * <p>Evidence: vanilla {@code 1.21.10} {@code atlases/blocks.json} includes {@code item/}
 * + trim item overlays; {@code 1.21.11} moves those to {@code atlases/items.json}.
 * Wiki: all textures in an item model must come from the same atlas (items or blocks);
 * block models must use the blocks atlas.
 *
 * <p>This converter peels item-related sources from a pack-local {@code blocks.json}
 * (typically emitted by {@code AtlasConverter1_19_3}) into {@code items.json}.
 *
 * <p>Adventure packs that place custom props via crop/plant blockstates often reference
 * {@code item/...} textures from {@code models/block/**}. Dual-stitching those ids onto
 * both atlases fails in-game: the client resolves the sprite to the items atlas and
 * rejects the block model. Instead, copy each needed PNG under {@code textures/block/item/}
 * and rewrite block-model texture refs to {@code block/item/...} so they stay on the
 * blocks atlas while originals remain available to item models via the items atlas.
 *
 * <p>Item / CMD models (and {@code items/*.json} range_dispatch trees) that mix
 * {@code item/} and {@code block/} must share one atlas. Same-id dual-stitch of
 * {@code block/...} onto {@code items.json} poisons terrain: the client resolves
 * the sprite to {@code items.png} and rejects block models. Instead, add
 * {@code single} sources with a distinct sprite id ({@code item/block/...}) and
 * rewrite item-facing model refs to that sprite. Generated
 * {@code paletted_permutations} sprites ({@code pixel/pixel_*}) are mirrored onto
 * items under the same names (no fake {@code single} resources).
 */
public class ItemsAtlasConverter1_21_11 extends Converter {
	public ItemsAtlasConverter1_21_11(PackConverter packConverter) {
		super(packConverter);
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		return from <= Util.getVersionProtocol(gson, "1.21.10")
			&& to >= Util.getVersionProtocol(gson, "1.21.11");
	}

	@Override
	public void convert(Pack pack) throws IOException {
		Gson gson = packConverter.getGson();
		Path atlasesPath = pack.getWorkingPath().resolve("assets/minecraft/atlases".replace("/", File.separator));
		Path blocksPath = atlasesPath.resolve("blocks.json");

		if (Files.exists(blocksPath)) {
			JsonObject blocksJson = JsonUtil.readJson(gson, blocksPath);
			if (blocksJson != null && blocksJson.has("sources") && blocksJson.get("sources").isJsonArray()) {
				JsonArray remaining = new JsonArray();
				JsonArray moved = new JsonArray();
				for (JsonElement element : blocksJson.getAsJsonArray("sources")) {
					if (!element.isJsonObject()) {
						remaining.add(element);
						continue;
					}
					JsonObject source = element.getAsJsonObject();
					if (isItemsAtlasSource(source)) {
						moved.add(source);
					} else {
						remaining.add(source);
					}
				}

				if (moved.size() > 0) {
					if (!Files.exists(atlasesPath)) {
						Files.createDirectories(atlasesPath);
					}

					Path itemsPath = atlasesPath.resolve("items.json");
					JsonObject itemsJson;
					JsonArray itemsSources;
					if (Files.exists(itemsPath)) {
						itemsJson = JsonUtil.readJson(gson, itemsPath);
						if (itemsJson == null) {
							itemsJson = new JsonObject();
						}
						if (itemsJson.has("sources") && itemsJson.get("sources").isJsonArray()) {
							itemsSources = itemsJson.getAsJsonArray("sources");
						} else {
							itemsSources = new JsonArray();
							itemsJson.add("sources", itemsSources);
						}
					} else {
						itemsJson = new JsonObject();
						itemsSources = new JsonArray();
						itemsJson.add("sources", itemsSources);
					}

					for (JsonElement element : moved) {
						if (!containsEquivalentSource(itemsSources, element.getAsJsonObject())) {
							itemsSources.add(element);
						}
					}
					JsonUtil.writeJson(gson, itemsPath, itemsJson);

					blocksJson.add("sources", remaining);
					JsonUtil.writeJson(gson, blocksPath, blocksJson);
				}
			}
		}

		// Copy+rewrite block models only (item/ → block/item/) so terrain stays on blocks.
		rewriteBlockModelsItemTextures(pack.getWorkingPath(), gson);

		// Item/CMD models: alias non-item textures onto items atlas under item/<path>
		// sprites (never same-id block/ dual-stitch) and rewrite item-facing refs.
		dualStitchItemModelBlockTexturesOntoItems(pack.getWorkingPath(), gson);

		// Dual-stitch models/armor onto items (keep on blocks too). Item hand models
		// (TSF colored_arm_model) sample models/armor; blocks atlas mipmaps muddy dyes.
		dualStitchModelsArmorOntoItems(pack.getWorkingPath(), gson);
	}

	/**
	 * Ensure {@code atlases/items.json} includes directory sources for
	 * {@code models/armor} (and parent {@code models} if already on blocks) when the
	 * pack ships those textures. Does not remove them from blocks.json.
	 */
	static void dualStitchModelsArmorOntoItems(Path packRoot, Gson gson) throws IOException {
		Path texturesArmor = packRoot.resolve(
			"assets/minecraft/textures/models/armor".replace("/", File.separator));
		if (!Files.isDirectory(texturesArmor)) {
			return;
		}
		boolean anyPng;
		try (java.util.stream.Stream<Path> stream = Files.list(texturesArmor)) {
			anyPng = stream.anyMatch(p -> p.getFileName().toString().endsWith(".png"));
		}
		if (!anyPng) {
			return;
		}

		Path atlasesPath = packRoot.resolve("assets/minecraft/atlases".replace("/", File.separator));
		Files.createDirectories(atlasesPath);
		Path itemsPath = atlasesPath.resolve("items.json");
		JsonObject itemsJson;
		JsonArray itemsSources;
		if (Files.exists(itemsPath)) {
			itemsJson = JsonUtil.readJson(gson, itemsPath);
			if (itemsJson == null) {
				itemsJson = new JsonObject();
			}
			if (itemsJson.has("sources") && itemsJson.get("sources").isJsonArray()) {
				itemsSources = itemsJson.getAsJsonArray("sources");
			} else {
				itemsSources = new JsonArray();
				itemsJson.add("sources", itemsSources);
			}
		} else {
			itemsJson = new JsonObject();
			itemsSources = new JsonArray();
			itemsJson.add("sources", itemsSources);
		}

		// Prefer mirroring whatever directory prefixes AtlasConverter put on blocks.
		Path blocksPath = atlasesPath.resolve("blocks.json");
		boolean addedFromBlocks = false;
		if (Files.exists(blocksPath)) {
			JsonObject blocksJson = JsonUtil.readJson(gson, blocksPath);
			if (blocksJson != null && blocksJson.has("sources") && blocksJson.get("sources").isJsonArray()) {
				for (JsonElement element : blocksJson.getAsJsonArray("sources")) {
					if (!element.isJsonObject()) {
						continue;
					}
					JsonObject source = element.getAsJsonObject();
					if (isModelsArmorDirectorySource(source)
						&& !containsEquivalentSource(itemsSources, source)) {
						itemsSources.add(source.deepCopy());
						addedFromBlocks = true;
					}
				}
			}
		}

		if (!addedFromBlocks && !hasModelsArmorDirectory(itemsSources)) {
			JsonObject source = new JsonObject();
			source.addProperty("type", "directory");
			source.addProperty("source", "models/armor");
			source.addProperty("prefix", "models/armor/");
			itemsSources.add(source);
		}

		JsonUtil.writeJson(gson, itemsPath, itemsJson);
		Logger.debug("Dual-stitched models/armor directory source(s) onto items atlas");
	}

	static boolean isModelsArmorDirectorySource(JsonObject source) {
		String type = source.has("type") && source.get("type").isJsonPrimitive()
			? source.get("type").getAsString()
			: "";
		String normalized = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
		if (!"directory".equals(normalized)) {
			return false;
		}
		String dir = source.has("source") && source.get("source").isJsonPrimitive()
			? source.get("source").getAsString().replace("\\", "/").replaceAll("^/+|/+$", "")
			: "";
		// Whole "models" tree or just "models/armor"
		return dir.equals("models") || dir.equals("models/armor") || dir.startsWith("models/armor/");
	}

	private static boolean hasModelsArmorDirectory(JsonArray sources) {
		for (JsonElement element : sources) {
			if (element.isJsonObject() && isModelsArmorDirectorySource(element.getAsJsonObject())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Sources that vanilla moved from blocks → items in 1.21.11:
	 * directory {@code item} / {@code item/**}, singles under {@code item/},
	 * and paletted_permutations whose textures are trim item overlays.
	 */
	static boolean isItemsAtlasSource(JsonObject source) {
		String type = source.has("type") && source.get("type").isJsonPrimitive()
			? source.get("type").getAsString()
			: "";
		String normalized = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;

		if ("directory".equals(normalized)) {
			String dir = source.has("source") && source.get("source").isJsonPrimitive()
				? source.get("source").getAsString().replace("\\", "/").replaceAll("^/+|/+$", "")
				: "";
			return dir.equals("item") || dir.startsWith("item/");
		}

		if ("single".equals(normalized)) {
			String resource = source.has("resource") && source.get("resource").isJsonPrimitive()
				? source.get("resource").getAsString().replace("\\", "/")
				: "";
			if (resource.contains(":")) {
				resource = resource.substring(resource.indexOf(':') + 1);
			}
			return resource.equals("item") || resource.startsWith("item/");
		}

		if ("paletted_permutations".equals(normalized)) {
			if (!source.has("textures") || !source.get("textures").isJsonArray()) {
				return false;
			}
			for (JsonElement texture : source.getAsJsonArray("textures")) {
				if (!texture.isJsonPrimitive()) {
					continue;
				}
				String id = texture.getAsString().replace("\\", "/");
				if (id.contains(":")) {
					id = id.substring(id.indexOf(':') + 1);
				}
				// Vanilla moved trims/items/*_trim overlays to the items atlas.
				if (id.startsWith("trims/items/")) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * For every block model texture under {@code item/}, copy the PNG (+ mcmeta) to
	 * {@code textures/block/item/...} and rewrite the ref to {@code block/item/...}.
	 */
	static void rewriteBlockModelsItemTextures(Path packRoot, Gson gson) throws IOException {
		Path assets = packRoot.resolve("assets");
		if (!Files.isDirectory(assets)) {
			return;
		}

		File[] namespaces = assets.toFile().listFiles();
		if (namespaces == null) {
			return;
		}

		for (File nsDir : namespaces) {
			if (!nsDir.isDirectory()) {
				continue;
			}
			Path blockModels = nsDir.toPath().resolve("models").resolve("block");
			if (!Files.isDirectory(blockModels)) {
				continue;
			}
			Files.walkFileTree(blockModels, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (!file.getFileName().toString().endsWith(".json")) {
						return FileVisitResult.CONTINUE;
					}
					try {
						JsonObject model = JsonUtil.readJson(gson, file);
						if (model == null || !model.has("textures") || !model.get("textures").isJsonObject()) {
							return FileVisitResult.CONTINUE;
						}
						JsonObject textures = model.getAsJsonObject("textures");
						boolean changed = false;
						for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
							if (!entry.getValue().isJsonPrimitive()) {
								continue;
							}
							String raw = entry.getValue().getAsString();
							String rewrittenRef = rewriteItemTextureRef(packRoot, raw);
							if (rewrittenRef != null && !rewrittenRef.equals(raw)) {
								textures.addProperty(entry.getKey(), rewrittenRef);
								changed = true;
							}
						}
						if (changed) {
							JsonUtil.writeJson(gson, file, model);
						}
					} catch (IOException ignored) {
						// Best-effort per model; skip unreadable/unwritable files.
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
		Logger.debug("Rewrote block-model item/ texture refs onto block/item/ where pack textures exist");
	}

	/**
	 * For non-{@code item/} texture ids used by item-facing models, register distinct
	 * sprite aliases on {@code atlases/items.json} ({@code resource}=original,
	 * {@code sprite}={@code item/&lt;path&gt;}) and rewrite model refs. Never same-id
	 * dual-stitch {@code block/...} onto items (that rejects terrain models in-game).
	 *
	 * <p>Generated {@code paletted_permutations} sprites (e.g. {@code pixel/pixel_*})
	 * are covered by mirroring those sources onto items; no fake {@code single}s.
	 * When mirrored and unused by {@code models/block/**}, the source is <strong>removed</strong>
	 * from {@code blocks.json} so the same sprite id is not on both atlases (client warns
	 * duplicates will be rejected in a future version).
	 */
	static void dualStitchItemModelBlockTexturesOntoItems(Path packRoot, Gson gson) throws IOException {
		List<Path> itemFacingModels = listItemFacingModelFiles(packRoot);
		if (itemFacingModels.isEmpty()) {
			return;
		}

		Path atlasesPath = packRoot.resolve("assets/minecraft/atlases".replace("/", File.separator));
		Path blocksPath = atlasesPath.resolve("blocks.json");
		List<JsonObject> palettedSources = loadPalettedPermutationSources(gson, blocksPath);
		Set<String> generatedSprites = collectGeneratedPalettedSpritePaths(palettedSources);

		Set<String> resourcesToAlias = new LinkedHashSet<>();
		Set<JsonObject> palettedToMirror = new LinkedHashSet<>();
		boolean anyModelNeedsPaletted = false;

		for (Path file : itemFacingModels) {
			JsonObject model;
			try {
				model = JsonUtil.readJson(gson, file);
			} catch (IOException ignored) {
				continue;
			}
			if (model == null || !model.has("textures") || !model.get("textures").isJsonObject()) {
				continue;
			}
			JsonObject textures = model.getAsJsonObject("textures");
			boolean changed = false;
			for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
				if (!entry.getValue().isJsonPrimitive()) {
					continue;
				}
				String raw = entry.getValue().getAsString();
				String resource = toAtlasResourceId(raw);
				if (resource == null) {
					continue;
				}
				String path = resource.substring(resource.indexOf(':') + 1);
				// Reject already-broken RLs (e.g. minecraft:tsf:npc/… from a bad
				// minecraft: prefix on a custom-ns ref) — aliasing would emit
				// minecraft:item/tsf:… and poison items.json.
				if (path.contains(":")) {
					continue;
				}
				if (path.equals("item") || path.startsWith("item/")) {
					continue;
				}
				// models/armor is dual-stitched by directory under the same sprite ids
				// (intentional; terrain does not sample those sprites).
				if (path.equals("models") || path.startsWith("models/")) {
					continue;
				}
				if (generatedSprites.contains(path)) {
					anyModelNeedsPaletted = true;
					continue;
				}
				resourcesToAlias.add(resource);
				String rewritten = rewriteNonItemTextureRefToItemAlias(raw);
				if (rewritten != null && !rewritten.equals(raw)) {
					textures.addProperty(entry.getKey(), rewritten);
					changed = true;
				}
			}
			if (changed) {
				JsonUtil.writeJson(gson, file, model);
			}
		}

		if (anyModelNeedsPaletted) {
			palettedToMirror.addAll(palettedSources);
		}

		if (resourcesToAlias.isEmpty() && palettedToMirror.isEmpty()) {
			return;
		}

		Files.createDirectories(atlasesPath);
		Path itemsPath = atlasesPath.resolve("items.json");
		JsonObject itemsJson;
		JsonArray itemsSources;
		if (Files.exists(itemsPath)) {
			itemsJson = JsonUtil.readJson(gson, itemsPath);
			if (itemsJson == null) {
				itemsJson = new JsonObject();
			}
			if (itemsJson.has("sources") && itemsJson.get("sources").isJsonArray()) {
				itemsSources = itemsJson.getAsJsonArray("sources");
			} else {
				itemsSources = new JsonArray();
				itemsJson.add("sources", itemsSources);
			}
		} else {
			itemsJson = new JsonObject();
			itemsSources = new JsonArray();
			itemsJson.add("sources", itemsSources);
		}

		int added = 0;
		for (String resource : resourcesToAlias) {
			String path = resource.substring(resource.indexOf(':') + 1);
			String namespace = resource.substring(0, resource.indexOf(':'));
			String sprite = namespace + ":item/" + path;
			JsonObject single = new JsonObject();
			single.addProperty("type", "single");
			single.addProperty("resource", resource);
			single.addProperty("sprite", sprite);
			if (!containsEquivalentSource(itemsSources, single)
				&& !containsSingleWithSprite(itemsSources, sprite)) {
				itemsSources.add(single);
				added++;
			}
		}
		for (JsonObject paletted : palettedToMirror) {
			JsonObject copy = paletted.deepCopy();
			if (!containsEquivalentSource(itemsSources, copy)) {
				itemsSources.add(copy);
				added++;
			}
		}
		if (added > 0) {
			JsonUtil.writeJson(gson, itemsPath, itemsJson);
			Logger.log(
				"Aliased " + resourcesToAlias.size()
					+ " non-item texture(s) onto items atlas as item/<path> sprites"
					+ (palettedToMirror.isEmpty() ? "" : ("; mirrored "
					+ palettedToMirror.size() + " paletted_permutations source(s)")));
		}

		// Drop mirrored PPP from blocks when block models do not sample generated sprites.
		if (!palettedToMirror.isEmpty()) {
			int removed = removeUnusedMirroredPalettedFromBlocks(
				packRoot, gson, blocksPath, palettedToMirror);
			if (removed > 0) {
				Logger.log(
					"Removed " + removed
						+ " paletted_permutations source(s) from blocks atlas "
						+ "(sprites only used by item-facing models; avoids duplicate sprite ids)");
			}
		}
	}

	/**
	 * Remove pack {@code paletted_permutations} sources from {@code blocks.json} that were
	 * mirrored onto items and whose generated sprites are not referenced by any block model.
	 * Skips sources that look like vanilla trim-item overlays (already peeled by
	 * {@link #isItemsAtlasSource}).
	 *
	 * @return number of sources removed
	 */
	static int removeUnusedMirroredPalettedFromBlocks(
		Path packRoot,
		Gson gson,
		Path blocksPath,
		Set<JsonObject> mirroredSources
	) throws IOException {
		if (!Files.exists(blocksPath) || mirroredSources.isEmpty()) {
			return 0;
		}
		JsonObject blocksJson = JsonUtil.readJson(gson, blocksPath);
		if (blocksJson == null || !blocksJson.has("sources") || !blocksJson.get("sources").isJsonArray()) {
			return 0;
		}

		Set<String> blockModelTextures = collectBlockModelTexturePaths(packRoot, gson);
		JsonArray sources = blocksJson.getAsJsonArray("sources");
		JsonArray remaining = new JsonArray();
		int removed = 0;
		for (JsonElement element : sources) {
			if (!element.isJsonObject()) {
				remaining.add(element);
				continue;
			}
			JsonObject source = element.getAsJsonObject();
			if (!isPalettedPermutationsSource(source)) {
				remaining.add(source);
				continue;
			}
			// Never strip trim-item overlay PPP here (peel owns those).
			if (isItemsAtlasSource(source)) {
				remaining.add(source);
				continue;
			}
			boolean wasMirrored = false;
			for (JsonObject mirrored : mirroredSources) {
				if (sourcesEquivalent(mirrored, source)) {
					wasMirrored = true;
					break;
				}
			}
			if (!wasMirrored) {
				remaining.add(source);
				continue;
			}
			Set<String> generated = collectGeneratedPalettedSpritePaths(
				java.util.Collections.singletonList(source));
			boolean usedByBlock = false;
			for (String sprite : generated) {
				if (blockModelTextures.contains(sprite)) {
					usedByBlock = true;
					break;
				}
			}
			if (usedByBlock) {
				remaining.add(source);
			} else {
				removed++;
			}
		}
		if (removed > 0) {
			blocksJson.add("sources", remaining);
			JsonUtil.writeJson(gson, blocksPath, blocksJson);
		}
		return removed;
	}

	static boolean isPalettedPermutationsSource(JsonObject source) {
		String type = source.has("type") && source.get("type").isJsonPrimitive()
			? source.get("type").getAsString()
			: "";
		String normalized = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
		return "paletted_permutations".equals(normalized);
	}

	/** Texture paths (no namespace) referenced by any {@code models/block/**} textures map. */
	static Set<String> collectBlockModelTexturePaths(Path packRoot, Gson gson) throws IOException {
		Set<String> paths = new LinkedHashSet<>();
		Path assets = packRoot.resolve("assets");
		if (!Files.isDirectory(assets)) {
			return paths;
		}
		File[] namespaces = assets.toFile().listFiles();
		if (namespaces == null) {
			return paths;
		}
		for (File nsDir : namespaces) {
			if (!nsDir.isDirectory()) {
				continue;
			}
			Path blockModels = nsDir.toPath().resolve("models").resolve("block");
			if (!Files.isDirectory(blockModels)) {
				continue;
			}
			Files.walkFileTree(blockModels, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (!file.getFileName().toString().endsWith(".json")) {
						return FileVisitResult.CONTINUE;
					}
					try {
						JsonObject model = JsonUtil.readJson(gson, file);
						if (model == null || !model.has("textures") || !model.get("textures").isJsonObject()) {
							return FileVisitResult.CONTINUE;
						}
						for (Map.Entry<String, JsonElement> entry
							: model.getAsJsonObject("textures").entrySet()) {
							if (!entry.getValue().isJsonPrimitive()) {
								continue;
							}
							String path = texturePathWithoutNamespace(entry.getValue().getAsString());
							if (path != null) {
								paths.add(path);
							}
						}
					} catch (IOException ignored) {
						// skip
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
		return paths;
	}

	/** Item-facing model JSON files: everything under {@code models/**} except {@code models/block/**}. */
	static List<Path> listItemFacingModelFiles(Path packRoot) throws IOException {
		List<Path> files = new ArrayList<>();
		Path assets = packRoot.resolve("assets");
		if (!Files.isDirectory(assets)) {
			return files;
		}
		File[] namespaces = assets.toFile().listFiles();
		if (namespaces == null) {
			return files;
		}
		for (File nsDir : namespaces) {
			if (!nsDir.isDirectory()) {
				continue;
			}
			Path modelsRoot = nsDir.toPath().resolve("models");
			if (!Files.isDirectory(modelsRoot)) {
				continue;
			}
			Files.walkFileTree(modelsRoot, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					if (dir.equals(modelsRoot.resolve("block"))) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (file.getFileName().toString().endsWith(".json")) {
						files.add(file);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
		return files;
	}

	/**
	 * Collect namespaced texture resources (e.g. {@code minecraft:block/stone}) referenced
	 * by any model outside {@code models/block/**} that is not an {@code item/} path.
	 */
	static Set<String> collectNonItemTextureResourcesForItemModels(Path packRoot, Gson gson)
		throws IOException {
		Set<String> resources = new LinkedHashSet<>();
		for (Path file : listItemFacingModelFiles(packRoot)) {
			try {
				JsonObject model = JsonUtil.readJson(gson, file);
				if (model == null || !model.has("textures") || !model.get("textures").isJsonObject()) {
					continue;
				}
				collectNonItemResources(model.getAsJsonObject("textures"), resources);
			} catch (IOException ignored) {
				// skip
			}
		}
		return resources;
	}

	static void collectNonItemResources(JsonObject textures, Set<String> out) {
		for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
			if (!entry.getValue().isJsonPrimitive()) {
				continue;
			}
			String resource = toAtlasResourceId(entry.getValue().getAsString());
			if (resource == null) {
				continue;
			}
			String path = resource.substring(resource.indexOf(':') + 1);
			if (path.equals("item") || path.startsWith("item/")) {
				continue;
			}
			out.add(resource);
		}
	}

	static List<JsonObject> loadPalettedPermutationSources(Gson gson, Path blocksPath)
		throws IOException {
		List<JsonObject> out = new ArrayList<>();
		if (!Files.exists(blocksPath)) {
			return out;
		}
		JsonObject blocksJson = JsonUtil.readJson(gson, blocksPath);
		if (blocksJson == null || !blocksJson.has("sources") || !blocksJson.get("sources").isJsonArray()) {
			return out;
		}
		for (JsonElement element : blocksJson.getAsJsonArray("sources")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject source = element.getAsJsonObject();
			String type = source.has("type") && source.get("type").isJsonPrimitive()
				? source.get("type").getAsString()
				: "";
			String normalized = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
			if ("paletted_permutations".equals(normalized)) {
				out.add(source);
			}
		}
		return out;
	}

	/**
	 * Sprite paths produced by {@code paletted_permutations}: {@code <texture>_<key>}
	 * (e.g. {@code pixel/pixel} + {@code brown} → {@code pixel/pixel_brown}).
	 */
	static Set<String> collectGeneratedPalettedSpritePaths(List<JsonObject> palettedSources) {
		Set<String> out = new LinkedHashSet<>();
		for (JsonObject source : palettedSources) {
			if (!source.has("textures") || !source.get("textures").isJsonArray()) {
				continue;
			}
			if (!source.has("permutations") || !source.get("permutations").isJsonObject()) {
				continue;
			}
			JsonObject permutations = source.getAsJsonObject("permutations");
			for (JsonElement textureEl : source.getAsJsonArray("textures")) {
				if (!textureEl.isJsonPrimitive()) {
					continue;
				}
				String texture = textureEl.getAsString().replace("\\", "/");
				if (texture.contains(":")) {
					texture = texture.substring(texture.indexOf(':') + 1);
				}
				texture = texture.replaceAll("^/+", "");
				if (texture.isEmpty()) {
					continue;
				}
				for (Map.Entry<String, JsonElement> perm : permutations.entrySet()) {
					out.add(texture + "_" + perm.getKey());
				}
			}
		}
		return out;
	}

	/**
	 * Rewrite {@code block/foo} → {@code item/block/foo} (preserve namespace prefix).
	 * Returns original for {@code #} refs / empty; null unused.
	 */
	static String rewriteNonItemTextureRefToItemAlias(String raw) {
		if (raw == null) {
			return null;
		}
		String trimmed = raw.replace("\\", "/").trim();
		if (trimmed.isEmpty() || trimmed.startsWith("#")) {
			return raw;
		}
		String namespace = null;
		String path = trimmed;
		int colon = trimmed.indexOf(':');
		if (colon >= 0) {
			namespace = trimmed.substring(0, colon);
			path = trimmed.substring(colon + 1);
		}
		path = path.replaceAll("^/+", "");
		// Path must be a single RL path segment tree — no embedded namespaces.
		if (path.isEmpty() || path.contains(":")
			|| path.equals("item") || path.startsWith("item/")) {
			return raw;
		}
		String aliased = "item/" + path;
		if (namespace != null) {
			return namespace + ":" + aliased;
		}
		return aliased;
	}

	private static boolean containsSingleWithSprite(JsonArray existing, String spriteId) {
		for (JsonElement element : existing) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject src = element.getAsJsonObject();
			String type = src.has("type") && src.get("type").isJsonPrimitive()
				? src.get("type").getAsString()
				: "";
			String normalized = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
			if (!"single".equals(normalized)) {
				continue;
			}
			if (!src.has("sprite") || !src.get("sprite").isJsonPrimitive()) {
				continue;
			}
			String sprite = src.get("sprite").getAsString().replace("\\", "/");
			if (sprite.equals(spriteId)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return {@code namespace:path} suitable for atlas {@code single.resource}, or null
	 */
	static String toAtlasResourceId(String raw) {
		if (raw == null) {
			return null;
		}
		String trimmed = raw.replace("\\", "/").trim();
		if (trimmed.isEmpty() || trimmed.startsWith("#")) {
			return null;
		}
		String namespace = "minecraft";
		String path = trimmed;
		int colon = trimmed.indexOf(':');
		if (colon >= 0) {
			namespace = trimmed.substring(0, colon);
			path = trimmed.substring(colon + 1);
		}
		path = path.replaceAll("^/+", "");
		// Illegal RLs like minecraft:tsf:npc/… (colon in path) must not become atlas singles.
		if (path.isEmpty() || path.contains(":") || namespace.isEmpty() || namespace.contains("/")) {
			return null;
		}
		return namespace + ":" + path;
	}

	/** @return path without namespace, or null if empty / {@code #} ref */
	static String texturePathWithoutNamespace(String raw) {
		if (raw == null) {
			return null;
		}
		String path = raw.replace("\\", "/").trim();
		if (path.isEmpty() || path.startsWith("#")) {
			return null;
		}
		if (path.contains(":")) {
			path = path.substring(path.indexOf(':') + 1);
		}
		path = path.replaceAll("^/+", "");
		return path.isEmpty() ? null : path;
	}

	/**
	 * @return rewritten texture ref, original if not an item/ ref or copy impossible, null only unused
	 */
	static String rewriteItemTextureRef(Path packRoot, String raw) throws IOException {
		if (raw == null) {
			return null;
		}
		String trimmed = raw.replace("\\", "/").trim();
		if (trimmed.isEmpty() || trimmed.startsWith("#")) {
			return raw;
		}

		String namespace = "minecraft";
		String path = trimmed;
		int colon = trimmed.indexOf(':');
		if (colon >= 0) {
			namespace = trimmed.substring(0, colon);
			path = trimmed.substring(colon + 1);
		}
		path = path.replaceAll("^/+", "");
		if (!path.startsWith("item/")) {
			return raw;
		}

		String blockPath = "block/" + path; // item/foo -> block/item/foo
		Path texturesRoot = packRoot.resolve("assets").resolve(namespace).resolve("textures");
		Path sourcePng = texturesRoot.resolve(path.replace("/", File.separator) + ".png");
		Path destPng = texturesRoot.resolve(blockPath.replace("/", File.separator) + ".png");

		if (!Files.exists(sourcePng) && !Files.exists(destPng)) {
			// Mixed models often sample vanilla item/ sprites (not in the pack). Copy from
			// knowledge/vanilla when present so block/item/ exists on the blocks atlas.
			Path vanillaPng = findVanillaTexturePng(packRoot, namespace, path + ".png");
			if (vanillaPng == null) {
				return raw;
			}
			Files.createDirectories(destPng.getParent());
			Files.copy(vanillaPng, destPng, StandardCopyOption.REPLACE_EXISTING);
			Path vanillaMeta = vanillaPng.resolveSibling(vanillaPng.getFileName().toString() + ".mcmeta");
			Path destMeta = destPng.resolveSibling(destPng.getFileName().toString() + ".mcmeta");
			if (Files.exists(vanillaMeta)) {
				Files.copy(vanillaMeta, destMeta, StandardCopyOption.REPLACE_EXISTING);
			}
		} else if (Files.exists(sourcePng)) {
			Files.createDirectories(destPng.getParent());
			Files.copy(sourcePng, destPng, StandardCopyOption.REPLACE_EXISTING);
			Path sourceMeta = sourcePng.resolveSibling(sourcePng.getFileName().toString() + ".mcmeta");
			Path destMeta = destPng.resolveSibling(destPng.getFileName().toString() + ".mcmeta");
			if (Files.exists(sourceMeta)) {
				Files.copy(sourceMeta, destMeta, StandardCopyOption.REPLACE_EXISTING);
			}
		}

		if (colon >= 0) {
			return namespace + ":" + blockPath;
		}
		return blockPath;
	}

	/**
	 * Locate {@code assets/&lt;ns&gt;/textures/&lt;relPng&gt;} under {@code knowledge/vanilla/*}
	 * near the pack or cwd (harness / repo layout). Returns null when unavailable.
	 */
	static Path findVanillaTexturePng(Path packRoot, String namespace, String relPng) {
		String rel = ("assets/" + namespace + "/textures/" + relPng).replace("/", File.separator);
		List<Path> roots = new ArrayList<>();
		String env = System.getenv("RPC_VANILLA");
		if (env != null && !env.isBlank()) {
			roots.add(Path.of(env));
		}
		String prop = System.getProperty("rpc.vanilla");
		if (prop != null && !prop.isBlank()) {
			roots.add(Path.of(prop));
		}
		Path cwd = Path.of("").toAbsolutePath().normalize();
		roots.add(cwd.resolve("knowledge").resolve("vanilla"));
		if (packRoot != null) {
			Path abs = packRoot.toAbsolutePath().normalize();
			for (Path p = abs; p != null; p = p.getParent()) {
				roots.add(p.resolve("knowledge").resolve("vanilla"));
				if (p.getParent() == null) {
					break;
				}
			}
		}

		for (Path vanillaRoot : roots) {
			if (vanillaRoot == null || !Files.isDirectory(vanillaRoot)) {
				continue;
			}
			// Prefer newer trees; any hit is fine (item textures rarely change path).
			try (java.util.stream.Stream<Path> versions = Files.list(vanillaRoot)) {
				List<Path> dirs = versions.filter(Files::isDirectory)
					.sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
					.toList();
				for (Path versionDir : dirs) {
					Path candidate = versionDir.resolve(rel);
					if (Files.isRegularFile(candidate)) {
						return candidate;
					}
				}
			} catch (IOException ignored) {
				// try next root
			}
		}
		return null;
	}

	/** @return path like {@code item/chair_computer}, or null if not an item texture ref */
	static String normalizeTexturePath(String raw) {
		if (raw == null) {
			return null;
		}
		String path = raw.replace("\\", "/").trim();
		if (path.isEmpty() || path.startsWith("#")) {
			return null;
		}
		if (path.contains(":")) {
			path = path.substring(path.indexOf(':') + 1);
		}
		path = path.replaceAll("^/+", "");
		if (path.equals("item") || path.startsWith("item/")) {
			return path;
		}
		return null;
	}

	private static boolean containsEquivalentSource(JsonArray existing, JsonObject candidate) {
		for (JsonElement element : existing) {
			if (element.isJsonObject() && sourcesEquivalent(element.getAsJsonObject(), candidate)) {
				return true;
			}
		}
		return false;
	}

	private static boolean sourcesEquivalent(JsonObject a, JsonObject b) {
		return a.equals(b);
	}
}

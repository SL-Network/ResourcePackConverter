package com.agentdid127.resourcepack.forwards.impl.v1_21.other;

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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 1.21.2+: emit {@code assets/minecraft/equipment/&lt;id&gt;.json} for non-vanilla
 * theatrical materials present under {@code textures/entity/equipment/humanoid*}.
 *
 * <p>Shape follows vanilla {@code leather.json}: base layer may be {@code dyeable}
 * (so {@code dyed_color} on the item tints sleeves/body) plus an untinted overlay
 * layer. {@code color_when_undyed} is white ({@code 16777215}) so undyed theatrical
 * bases keep authored colors (unlike brown vanilla leather).
 *
 * <p>Does not invent item components or trim_overrides. Skips vanilla equipment ids.
 * Re-opens pack-local custom JSON only to add missing {@code dyeable} on leather-style
 * base+overlay stacks.
 */
public class CustomEquipmentJsonConverter1_21_2 extends Converter {
	/** White — preserve authored base when undyed; dye multiply when dyed_color set. */
	private static final int COLOR_WHEN_UNDYED_WHITE = 16777215;

	/** Vanilla equipment asset ids — never overwrite / invent extras for these. */
	private static final Set<String> VANILLA_EQUIPMENT_IDS = Set.of(
		"leather", "chainmail", "iron", "gold", "diamond", "netherite", "copper",
		"turtle_scute", "armadillo_scute", "elytra", "saddle",
		"trader_llama", "trader_llama_baby",
		"white_harness", "orange_harness", "magenta_harness", "light_blue_harness",
		"yellow_harness", "lime_harness", "pink_harness", "gray_harness",
		"light_gray_harness", "cyan_harness", "purple_harness", "blue_harness",
		"brown_harness", "green_harness", "red_harness", "black_harness",
		"white_carpet", "orange_carpet", "magenta_carpet", "light_blue_carpet",
		"yellow_carpet", "lime_carpet", "pink_carpet", "gray_carpet",
		"light_gray_carpet", "cyan_carpet", "purple_carpet", "blue_carpet",
		"brown_carpet", "green_carpet", "red_carpet", "black_carpet"
	);

	public CustomEquipmentJsonConverter1_21_2(PackConverter packConverter) {
		super(packConverter);
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		return from <= Util.getVersionProtocol(gson, "1.21.1")
			&& to >= Util.getVersionProtocol(gson, "1.21.2");
	}

	@Override
	public void convert(Pack pack) throws IOException {
		Path minecraft = pack.getWorkingPath().resolve("assets/minecraft".replace("/", File.separator));
		if (!Files.isDirectory(minecraft)) {
			return;
		}

		Path humanoid = minecraft.resolve("textures/entity/equipment/humanoid".replace("/", File.separator));
		Path leggings = minecraft.resolve("textures/entity/equipment/humanoid_leggings".replace("/", File.separator));
		if (!Files.isDirectory(humanoid) && !Files.isDirectory(leggings)) {
			return;
		}

		Path equipmentDir = minecraft.resolve("equipment");
		Files.createDirectories(equipmentDir);

		Set<String> materials = new HashSet<>();
		materials.addAll(listMaterialStems(humanoid));
		materials.addAll(listMaterialStems(leggings));

		Gson gson = packConverter.getGson();
		for (String material : materials.stream().sorted().collect(Collectors.toList())) {
			if (VANILLA_EQUIPMENT_IDS.contains(material)) {
				continue;
			}
			Path out = equipmentDir.resolve(material + ".json");

			boolean hasHumanoid = Files.exists(humanoid.resolve(material + ".png"));
			boolean hasLeggings = Files.exists(leggings.resolve(material + ".png"));
			boolean hasHumanoidOverlay = Files.exists(humanoid.resolve(material + "_overlay.png"));
			boolean hasLeggingsOverlay = Files.exists(leggings.resolve(material + "_overlay.png"));

			if (!hasHumanoid && !hasLeggings) {
				continue;
			}

			if (Files.exists(out)) {
				if (patchMissingDyeable(gson, out, hasHumanoidOverlay || hasLeggingsOverlay)) {
					Logger.log("Patched dyeable onto custom equipment JSON for material " + material);
				}
				continue;
			}

			JsonObject root = new JsonObject();
			JsonObject layers = new JsonObject();
			if (hasHumanoid) {
				JsonArray humanoidLayers = layerArray("minecraft:" + material, hasHumanoidOverlay);
				layers.add("humanoid", humanoidLayers);
				layers.add("humanoid_baby", humanoidLayers.deepCopy());
			}
			if (hasLeggings) {
				layers.add("humanoid_leggings",
					layerArray("minecraft:" + material, hasLeggingsOverlay));
			}
			root.add("layers", layers);
			JsonUtil.writeJson(gson, out, root);
			Logger.log("Wrote custom equipment JSON for material " + material);
		}
	}

	/**
	 * Leather-style stack: dyeable base + untinted overlay when an overlay PNG exists.
	 * Base-only materials stay untinted (e.g. pre-colored rainbow sheets).
	 */
	private static JsonArray layerArray(String textureId, boolean withOverlay) {
		JsonArray arr = new JsonArray();
		JsonObject base = new JsonObject();
		base.addProperty("texture", textureId);
		if (withOverlay) {
			JsonObject dyeable = new JsonObject();
			dyeable.addProperty("color_when_undyed", COLOR_WHEN_UNDYED_WHITE);
			base.add("dyeable", dyeable);
		}
		arr.add(base);
		if (withOverlay) {
			JsonObject overlay = new JsonObject();
			overlay.addProperty("texture", textureId + "_overlay");
			arr.add(overlay);
		}
		return arr;
	}

	/** @return true if file was modified */
	private static boolean patchMissingDyeable(Gson gson, Path out, boolean expectLeatherStyle)
		throws IOException {
		if (!expectLeatherStyle) {
			return false;
		}
		JsonObject root = JsonUtil.readJson(gson, out);
		if (root == null || !root.has("layers") || !root.get("layers").isJsonObject()) {
			return false;
		}
		boolean changed = false;
		JsonObject layers = root.getAsJsonObject("layers");
		for (Map.Entry<String, JsonElement> entry : layers.entrySet()) {
			if (!entry.getValue().isJsonArray()) {
				continue;
			}
			JsonArray arr = entry.getValue().getAsJsonArray();
			if (arr.size() < 2 || !arr.get(0).isJsonObject()) {
				continue;
			}
			JsonObject base = arr.get(0).getAsJsonObject();
			if (!base.has("dyeable")) {
				JsonObject dyeable = new JsonObject();
				dyeable.addProperty("color_when_undyed", COLOR_WHEN_UNDYED_WHITE);
				base.add("dyeable", dyeable);
				changed = true;
			}
		}
		if (changed) {
			JsonUtil.writeJson(gson, out, root);
		}
		return changed;
	}

	/**
	 * Material stems from {@code foo.png} / {@code foo_overlay.png} — overlay files
	 * alone do not define a material; they attach to {@code foo}.
	 */
	private static Set<String> listMaterialStems(Path dir) throws IOException {
		Set<String> stems = new HashSet<>();
		if (!Files.isDirectory(dir)) {
			return stems;
		}
		try (Stream<Path> stream = Files.list(dir)) {
			for (Path p : stream.collect(Collectors.toList())) {
				String name = p.getFileName().toString();
				if (!name.endsWith(".png") || name.endsWith(".png.mcmeta")) {
					continue;
				}
				String stem = name.substring(0, name.length() - ".png".length());
				if (stem.endsWith("_overlay")) {
					continue;
				}
				stems.add(stem);
			}
		}
		return stems;
	}
}

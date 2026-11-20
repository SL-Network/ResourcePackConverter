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

/**
 * 1.21.4+: item dye tints must be declared on {@code assets/.../items/*.json}.
 * Packs that override {@code models/item/leather_*.json} with {@code tintindex}
 * (TSF flight-suit arms) but omit a pack-local items def rely on vanilla's items
 * file — which usually works — but if a pack already emitted a host items JSON
 * without {@code tints}, sleeves lose dye. This converter:
 * <ul>
 *   <li>Writes a minimal dye-tint items def when the pack customizes the leather
 *       armor model and has no items file yet.</li>
 *   <li>Injects {@code minecraft:dye} into existing pack items defs that reference
 *       those models but lack tints.</li>
 * </ul>
 */
public class LeatherArmorItemsTintConverter1_21_4 extends Converter {
	private static final String[] LEATHER_PIECES = {
		"leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots"
	};
	/** Same default as vanilla items/leather_*.json */
	private static final int DEFAULT_LEATHER_DYE = -6265536;

	public LeatherArmorItemsTintConverter1_21_4(PackConverter packConverter) {
		super(packConverter);
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		return from < Util.getVersionProtocol(gson, "1.21.4")
			&& to >= Util.getVersionProtocol(gson, "1.21.4");
	}

	@Override
	public void convert(Pack pack) throws IOException {
		Path minecraft = pack.getWorkingPath().resolve("assets/minecraft".replace("/", File.separator));
		Path modelsItem = minecraft.resolve("models/item".replace("/", File.separator));
		Path itemsDir = minecraft.resolve("items");
		if (!Files.isDirectory(modelsItem)) {
			return;
		}
		Gson gson = packConverter.getGson();
		Files.createDirectories(itemsDir);

		for (String piece : LEATHER_PIECES) {
			Path modelPath = modelsItem.resolve(piece + ".json");
			if (!Files.exists(modelPath)) {
				continue;
			}
			// Any pack-local leather_* model override (often parent-only like TSF
			// leather_chestplate → colored_arm_model) needs items dye tints on 1.21.4+.
			Path itemsPath = itemsDir.resolve(piece + ".json");
			if (!Files.exists(itemsPath)) {
				JsonObject items = minimalDyeItemsDef("minecraft:item/" + piece);
				JsonUtil.writeJson(gson, itemsPath, items);
				Logger.log("Wrote items/" + piece + ".json with dye tint for custom leather model");
				continue;
			}

			JsonObject items = JsonUtil.readJson(gson, itemsPath);
			if (items == null) {
				continue;
			}
			if (ensureDyeTints(items)) {
				JsonUtil.writeJson(gson, itemsPath, items);
				Logger.log("Injected dye tints into items/" + piece + ".json");
			}
		}
	}

	private static JsonObject minimalDyeItemsDef(String modelId) {
		JsonObject root = new JsonObject();
		JsonObject model = new JsonObject();
		model.addProperty("type", "minecraft:model");
		model.addProperty("model", modelId);
		model.add("tints", dyeTintsArray());
		root.add("model", model);
		return root;
	}

	private static JsonArray dyeTintsArray() {
		JsonArray tints = new JsonArray();
		JsonObject dye = new JsonObject();
		dye.addProperty("type", "minecraft:dye");
		dye.addProperty("default", DEFAULT_LEATHER_DYE);
		tints.add(dye);
		return tints;
	}

	/** @return true if modified */
	private static boolean ensureDyeTints(JsonObject items) {
		if (!items.has("model")) {
			return false;
		}
		JsonElement modelEl = items.get("model");
		if (modelEl.isJsonObject()) {
			return ensureDyeTintsOnModelNode(modelEl.getAsJsonObject());
		}
		return false;
	}

	private static boolean ensureDyeTintsOnModelNode(JsonObject node) {
		boolean changed = false;
		String type = node.has("type") && node.get("type").isJsonPrimitive()
			? node.get("type").getAsString()
			: "";
		String normalized = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;

		if ("model".equals(normalized)) {
			if (!node.has("tints")) {
				node.add("tints", dyeTintsArray());
				changed = true;
			}
			return changed;
		}

		// Walk common wrappers: select / range_dispatch / condition / composite
		for (String key : new String[]{"fallback", "model"}) {
			if (node.has(key) && node.get(key).isJsonObject()) {
				changed |= ensureDyeTintsOnModelNode(node.getAsJsonObject(key));
			}
		}
		if (node.has("cases") && node.get("cases").isJsonArray()) {
			for (JsonElement c : node.getAsJsonArray("cases")) {
				if (c.isJsonObject() && c.getAsJsonObject().has("model")
					&& c.getAsJsonObject().get("model").isJsonObject()) {
					changed |= ensureDyeTintsOnModelNode(c.getAsJsonObject().getAsJsonObject("model"));
				}
			}
		}
		if (node.has("entries") && node.get("entries").isJsonArray()) {
			for (JsonElement e : node.getAsJsonArray("entries")) {
				if (e.isJsonObject() && e.getAsJsonObject().has("model")
					&& e.getAsJsonObject().get("model").isJsonObject()) {
					changed |= ensureDyeTintsOnModelNode(e.getAsJsonObject().getAsJsonObject("model"));
				}
			}
		}
		if (node.has("on_true") && node.get("on_true").isJsonObject()) {
			changed |= ensureDyeTintsOnModelNode(node.getAsJsonObject("on_true"));
		}
		if (node.has("on_false") && node.get("on_false").isJsonObject()) {
			changed |= ensureDyeTintsOnModelNode(node.getAsJsonObject("on_false"));
		}
		if (node.has("models") && node.get("models").isJsonArray()) {
			for (JsonElement m : node.getAsJsonArray("models")) {
				if (m.isJsonObject()) {
					changed |= ensureDyeTintsOnModelNode(m.getAsJsonObject());
				}
			}
		}
		return changed;
	}
}

package com.agentdid127.resourcepack.forwards.impl.v26_3.other;

import com.agentdid127.resourcepack.forwards.impl.v26_2.other.BedsSignsConverter26_2;
import com.agentdid127.resourcepack.library.Converter;
import com.agentdid127.resourcepack.library.PackConverter;
import com.agentdid127.resourcepack.library.pack.Pack;
import com.agentdid127.resourcepack.library.utilities.FileUtil;
import com.agentdid127.resourcepack.library.utilities.JsonUtil;
import com.agentdid127.resourcepack.library.utilities.Logger;
import com.agentdid127.resourcepack.library.utilities.Util;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 26.3 (pack_format 97.1): migrate trim material override_armor_assets into
 * equipment trim_overrides (final jar field; snap1 briefly used
 * trim_palette_replacements).
 *
 * <p>Evidence: wiki Armor trim definition / Equipment history (26.3 snap1–snap6);
 * vanilla equipment/iron.json uses trim_overrides with palette + when.material.
 * Datapack trim materials live under data/&lt;ns&gt;/trim_material/
 * (may ship inside combined packs).
 */
public class TrimEquipmentOverrideConverter26_3 extends Converter {
	public TrimEquipmentOverrideConverter26_3(PackConverter packConverter) {
		super(packConverter);
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		return from <= Util.getVersionProtocol(gson, "26.2")
			&& to >= Util.getVersionProtocol(gson, "26.3");
	}

	@Override
	public void convert(Pack pack) throws IOException {
		Path root = pack.getWorkingPath();
		Gson gson = packConverter.getGson();
		List<JsonObject> findings = new ArrayList<>();

		migrateTrimMaterials(root, gson, findings);
		upgradeEquipmentReplacements(root, gson, findings);

		BedsSignsConverter26_2.appendReviewFindings(root, gson, findings);
	}

	private static void migrateTrimMaterials(Path packRoot, Gson gson, List<JsonObject> findings)
		throws IOException {
		Path data = packRoot.resolve("data");
		if (!Files.isDirectory(data)) {
			return;
		}
		File[] namespaces = data.toFile().listFiles();
		if (namespaces == null) {
			return;
		}
		for (File nsDir : namespaces) {
			if (!nsDir.isDirectory()) {
				continue;
			}
			String ns = nsDir.getName();
			Path materials = nsDir.toPath().resolve("trim_material");
			if (!Files.isDirectory(materials)) {
				continue;
			}
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(materials, "*.json")) {
				for (Path file : stream) {
					migrateOneMaterial(packRoot, ns, file, gson, findings);
				}
			}
		}
	}

	private static void migrateOneMaterial(Path packRoot, String ns, Path file, Gson gson,
		List<JsonObject> findings) throws IOException {
		JsonObject root = JsonUtil.readJson(gson, file);
		if (root == null) {
			return;
		}
		boolean changed = false;
		String materialId = ns + ":" + stripJson(file.getFileName().toString());

		String assetName = null;
		if (root.has("asset_name") && root.get("asset_name").isJsonPrimitive()) {
			assetName = root.get("asset_name").getAsString();
			String paletteId = toPaletteId(ns, assetName);
			root.remove("asset_name");
			if (!root.has("palette_id")) {
				root.addProperty("palette_id", paletteId);
			}
			changed = true;
		}

		if (root.has("override_armor_assets") && root.get("override_armor_assets").isJsonObject()) {
			JsonObject overrides = root.getAsJsonObject("override_armor_assets");
			for (Map.Entry<String, JsonElement> e : overrides.entrySet()) {
				if (!e.getValue().isJsonPrimitive()) {
					continue;
				}
				String armorAsset = e.getKey();
				String darkerName = e.getValue().getAsString();
				String palette = toPaletteId(nsOf(armorAsset, ns), darkerName);
				// Prefer material's own namespace for palette leaf when value is bare.
				if (!darkerName.contains(":")) {
					palette = toPaletteId(ns, darkerName);
				}
				appendTrimOverride(packRoot, armorAsset, materialId, palette, gson, findings);
			}
			root.remove("override_armor_assets");
			changed = true;
			Logger.log("Migrated override_armor_assets from " + materialId + " → equipment trim_overrides");
		}

		if (changed) {
			JsonUtil.writeJson(gson, file, root);
			if (assetName != null) {
				JsonObject info = new JsonObject();
				info.addProperty("level", "info");
				info.addProperty("id", "trim_material_palette_id_26_3");
				info.addProperty("msg", "Replaced asset_name with palette_id on " + materialId);
				findings.add(info);
			}
		}
	}

	private static void appendTrimOverride(Path packRoot, String armorAsset, String materialId,
		String paletteId, Gson gson, List<JsonObject> findings) throws IOException {
		String armorNs = nsOf(armorAsset, "minecraft");
		String armorPath = pathOf(armorAsset);
		Path equipFile = packRoot.resolve(
			("assets/" + armorNs + "/equipment/" + armorPath + ".json").replace("/", File.separator));

		JsonObject root;
		if (Files.exists(equipFile)) {
			root = JsonUtil.readJson(gson, equipFile);
			if (root == null) {
				root = new JsonObject();
			}
		} else {
			root = new JsonObject();
			root.add("layers", new JsonObject());
		}

		JsonArray overrides;
		if (root.has("trim_overrides") && root.get("trim_overrides").isJsonArray()) {
			overrides = root.getAsJsonArray("trim_overrides");
		} else {
			overrides = new JsonArray();
			root.add("trim_overrides", overrides);
		}

		// Skip duplicate material match.
		for (JsonElement el : overrides) {
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject o = el.getAsJsonObject();
			if (o.has("when") && o.get("when").isJsonObject()) {
				JsonObject when = o.getAsJsonObject("when");
				if (when.has("material") && materialId.equals(when.get("material").getAsString())) {
					return;
				}
			}
		}

		JsonObject entry = new JsonObject();
		JsonObject when = new JsonObject();
		when.addProperty("material", materialId);
		entry.add("when", when);
		entry.addProperty("palette", paletteId);
		overrides.add(entry);

		FileUtil.ensureParentExists(equipFile);
		JsonUtil.writeJson(gson, equipFile, root);

		JsonObject info = new JsonObject();
		info.addProperty("level", "info");
		info.addProperty("id", "trim_overrides_migrated_26_3");
		info.addProperty("msg", "equipment/" + armorPath + ".json ← trim_overrides when.material="
			+ materialId + " palette=" + paletteId);
		findings.add(info);
	}

	/**
	 * Snap1 field {@code trim_palette_replacements} (map original→replacement palette id)
	 * → final {@code trim_overrides}. Material id inferred as {@code ns:<leaf>} from
	 * {@code ns:trim/<leaf>} keys only — otherwise emit review.
	 */
	private static void upgradeEquipmentReplacements(Path packRoot, Gson gson, List<JsonObject> findings)
		throws IOException {
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
			Path equipment = nsDir.toPath().resolve("equipment");
			if (!Files.isDirectory(equipment)) {
				continue;
			}
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(equipment, "*.json")) {
				for (Path file : stream) {
					upgradeOneEquipment(file, gson, findings);
				}
			}
		}
	}

	private static void upgradeOneEquipment(Path file, Gson gson, List<JsonObject> findings)
		throws IOException {
		JsonObject root = JsonUtil.readJson(gson, file);
		if (root == null || !root.has("trim_palette_replacements")) {
			return;
		}
		if (!root.get("trim_palette_replacements").isJsonObject()) {
			root.remove("trim_palette_replacements");
			JsonUtil.writeJson(gson, file, root);
			return;
		}
		JsonObject map = root.getAsJsonObject("trim_palette_replacements");
		JsonArray overrides;
		if (root.has("trim_overrides") && root.get("trim_overrides").isJsonArray()) {
			overrides = root.getAsJsonArray("trim_overrides");
		} else {
			overrides = new JsonArray();
			root.add("trim_overrides", overrides);
		}

		for (Map.Entry<String, JsonElement> e : map.entrySet()) {
			if (!e.getValue().isJsonPrimitive()) {
				continue;
			}
			String original = e.getKey();
			String replacement = e.getValue().getAsString();
			String material = materialFromPaletteId(original);
			if (material == null) {
				JsonObject review = new JsonObject();
				review.addProperty("level", "review");
				review.addProperty("id", "trim_palette_replacements_ambiguous_26_3");
				review.addProperty("msg", "Cannot infer trim material from palette key '" + original
					+ "' in " + file.getFileName() + " — review trim_overrides manually");
				findings.add(review);
				continue;
			}
			boolean exists = false;
			for (JsonElement el : overrides) {
				if (el.isJsonObject() && el.getAsJsonObject().has("when")) {
					JsonObject when = el.getAsJsonObject().getAsJsonObject("when");
					if (when.has("material") && material.equals(when.get("material").getAsString())) {
						exists = true;
						break;
					}
				}
			}
			if (exists) {
				continue;
			}
			JsonObject entry = new JsonObject();
			JsonObject when = new JsonObject();
			when.addProperty("material", material);
			entry.add("when", when);
			entry.addProperty("palette", rewriteLegacyPalette(replacement));
			overrides.add(entry);
		}
		root.remove("trim_palette_replacements");
		JsonUtil.writeJson(gson, file, root);
		Logger.debug("Upgraded trim_palette_replacements → trim_overrides in " + file.getFileName());
	}

	/** {@code ns:trim/foo} → {@code ns:foo}; else null. */
	static String materialFromPaletteId(String paletteId) {
		if (paletteId == null) {
			return null;
		}
		String id = paletteId.replace("\\", "/").trim();
		String ns = "minecraft";
		String path = id;
		int colon = id.indexOf(':');
		if (colon >= 0) {
			ns = id.substring(0, colon);
			path = id.substring(colon + 1);
		}
		if (path.startsWith("trim/") && path.indexOf('/', 5) < 0) {
			return ns + ":" + path.substring("trim/".length());
		}
		// Pre-26.3 texture-style id
		if (path.startsWith("trims/color_palettes/") && !path.endsWith("trim_palette")) {
			String leaf = path.substring("trims/color_palettes/".length());
			if (!leaf.isEmpty() && !leaf.contains("/")) {
				return ns + ":" + leaf;
			}
		}
		return null;
	}

	static String rewriteLegacyPalette(String raw) {
		if (raw == null) {
			return raw;
		}
		String id = raw.replace("\\", "/").trim();
		String ns = "minecraft";
		String path = id;
		int colon = id.indexOf(':');
		if (colon >= 0) {
			ns = id.substring(0, colon);
			path = id.substring(colon + 1);
		}
		if (path.equals("trims/color_palettes/trim_palette")) {
			return ns + ":trim_base";
		}
		if (path.startsWith("trims/color_palettes/")) {
			String leaf = path.substring("trims/color_palettes/".length());
			if (!leaf.isEmpty() && !leaf.contains("/")) {
				return ns + ":trim/" + leaf;
			}
		}
		if (path.startsWith("trim/") || path.equals("trim_base")) {
			return ns + ":" + path;
		}
		// bare asset name
		if (!path.contains("/")) {
			return ns + ":trim/" + path;
		}
		return raw;
	}

	static String toPaletteId(String ns, String assetOrId) {
		if (assetOrId == null) {
			return ns + ":trim/unknown";
		}
		String v = assetOrId.replace("\\", "/").trim();
		if (v.contains(":")) {
			return rewriteLegacyPalette(v);
		}
		if (v.startsWith("trim/") || v.equals("trim_base")) {
			return ns + ":" + v;
		}
		return ns + ":trim/" + v;
	}

	private static String nsOf(String id, String fallback) {
		int colon = id.indexOf(':');
		return colon >= 0 ? id.substring(0, colon) : fallback;
	}

	private static String pathOf(String id) {
		int colon = id.indexOf(':');
		return colon >= 0 ? id.substring(colon + 1) : id;
	}

	private static String stripJson(String name) {
		return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
	}
}

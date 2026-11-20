package com.agentdid127.resourcepack.forwards.impl.v26_3.textures;

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
 * 26.3 (pack_format 97.1): trim color palettes move under {@code textures/palettes/}.
 *
 * <p>Evidence ({@code knowledge/vanilla/26.2} → {@code 26.3},
 * {@code knowledge/renames/26.2-26.3.json}):
 * <ul>
 *   <li>{@code textures/trims/color_palettes/*.png} → {@code textures/palettes/trim/*.png}</li>
 *   <li>{@code textures/trims/color_palettes/trim_palette.png} → {@code textures/palettes/trim_base.png}</li>
 *   <li>{@code atlases/armor_trims.json} removed (entity trims generated on-demand)</li>
 *   <li>Atlas {@code palette_key}/{@code permutations} resolve under {@code textures/palettes/}
 *       ({@code minecraft:trims/color_palettes/X} → {@code minecraft:trim/X},
 *       {@code …/trim_palette} → {@code minecraft:trim_base})</li>
 *   <li>Wiki: palette {@code .mcmeta} {@code palette.base_palette} required for remapping palettes</li>
 * </ul>
 */
public class TrimPaletteConverter26_3 extends Converter {
	private static final String OLD_DIR = "textures/trims/color_palettes";
	private static final String NEW_TRIM_DIR = "textures/palettes/trim";
	private static final String NEW_BASE = "textures/palettes/trim_base.png";
	private static final String OLD_BASE_NAME = "trim_palette.png";

	public TrimPaletteConverter26_3(PackConverter packConverter) {
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
		Path assets = root.resolve("assets");
		if (!Files.isDirectory(assets)) {
			return;
		}

		List<JsonObject> findings = new ArrayList<>();
		Gson gson = packConverter.getGson();

		File[] namespaces = assets.toFile().listFiles();
		if (namespaces == null) {
			return;
		}
		for (File nsDir : namespaces) {
			if (!nsDir.isDirectory()) {
				continue;
			}
			String ns = nsDir.getName();
			Path nsRoot = nsDir.toPath();
			movePalettes(nsRoot, ns, gson, findings);
			rewriteAtlases(nsRoot, gson);
			removeArmorTrimsAtlas(nsRoot, findings);
		}

		BedsSignsConverter26_2.appendReviewFindings(root, gson, findings);
	}

	private static void movePalettes(Path nsRoot, String ns, Gson gson, List<JsonObject> findings)
		throws IOException {
		Path oldDir = nsRoot.resolve(OLD_DIR.replace("/", File.separator));
		if (!Files.isDirectory(oldDir)) {
			return;
		}

		Path baseFrom = oldDir.resolve(OLD_BASE_NAME);
		Path baseTo = nsRoot.resolve(NEW_BASE.replace("/", File.separator));
		boolean movedBase = false;
		if (Files.exists(baseFrom)) {
			FileUtil.moveIfExists(baseFrom, baseTo);
			FileUtil.moveIfExists(
				baseFrom.resolveSibling(OLD_BASE_NAME + ".mcmeta"),
				baseTo.resolveSibling(baseTo.getFileName().toString() + ".mcmeta"));
			movedBase = true;
			Logger.debug("Moved trim base palette → " + NEW_BASE + " (" + ns + ")");
		}

		Path newTrimDir = nsRoot.resolve(NEW_TRIM_DIR.replace("/", File.separator));
		List<Path> pngs = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(oldDir, "*.png")) {
			for (Path file : stream) {
				pngs.add(file);
			}
		}
		boolean nsHasBase = movedBase || Files.exists(baseTo);
		for (Path file : pngs) {
			String name = file.getFileName().toString();
			if (name.equals(OLD_BASE_NAME)) {
				continue;
			}
			Path dest = newTrimDir.resolve(name);
			FileUtil.moveIfExists(file, dest);
			Path metaFrom = file.resolveSibling(name + ".mcmeta");
			Path metaTo = dest.resolveSibling(name + ".mcmeta");
			FileUtil.moveIfExists(metaFrom, metaTo);
			if (Files.exists(dest)) {
				ensureBasePaletteMcmeta(metaTo, ns, nsHasBase, gson);
			}
			Logger.debug("Moved trim palette " + name + " → " + NEW_TRIM_DIR + " (" + ns + ")");
		}

		// Empty color_palettes leftovers — best-effort cleanup.
		if (Files.isDirectory(oldDir)) {
			boolean empty = true;
			try (DirectoryStream<Path> leftover = Files.newDirectoryStream(oldDir)) {
				if (leftover.iterator().hasNext()) {
					empty = false;
				}
			}
			if (empty) {
				Files.deleteIfExists(oldDir);
			}
		}

		JsonObject info = new JsonObject();
		info.addProperty("level", "info");
		info.addProperty("id", "trim_palettes_moved_26_3");
		info.addProperty("msg", "Moved " + ns + "/" + OLD_DIR + " → palettes/trim (+ trim_base)");
		findings.add(info);
	}

	/**
	 * Wiki pack format 97.1 / 89.0: palette textures that remap need
	 * {@code palette.base_palette}. Vanilla jar ships no mcmeta for built-ins;
	 * custom palettes in packs need the field. Base id is {@code <ns>:trim_base}
	 * when that file exists in-pack, else {@code minecraft:trim_base}.
	 */
	static void ensureBasePaletteMcmeta(Path metaPath, String ns, boolean nsHasBase, Gson gson)
		throws IOException {
		String baseId = nsHasBase ? (ns + ":trim_base") : "minecraft:trim_base";
		JsonObject root;
		if (Files.exists(metaPath)) {
			root = JsonUtil.readJson(gson, metaPath);
			if (root == null) {
				root = new JsonObject();
			}
		} else {
			root = new JsonObject();
		}
		JsonObject palette;
		if (root.has("palette") && root.get("palette").isJsonObject()) {
			palette = root.getAsJsonObject("palette");
			if (palette.has("base_palette") && palette.get("base_palette").isJsonPrimitive()) {
				return; // already set — do not invent over author intent
			}
		} else {
			palette = new JsonObject();
			root.add("palette", palette);
		}
		palette.addProperty("base_palette", baseId);
		FileUtil.ensureParentExists(metaPath);
		JsonUtil.writeJson(gson, metaPath, root);
		Logger.debug("Wrote palette.base_palette=" + baseId + " → " + metaPath.getFileName());
	}

	static void rewriteAtlases(Path nsRoot, Gson gson) throws IOException {
		Path atlases = nsRoot.resolve("atlases");
		if (!Files.isDirectory(atlases)) {
			return;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(atlases, "*.json")) {
			for (Path atlas : stream) {
				JsonObject root = JsonUtil.readJson(gson, atlas);
				if (root == null || !root.has("sources") || !root.get("sources").isJsonArray()) {
					continue;
				}
				boolean changed = false;
				JsonArray sources = root.getAsJsonArray("sources");
				for (JsonElement el : sources) {
					if (!el.isJsonObject()) {
						continue;
					}
					JsonObject src = el.getAsJsonObject();
					String type = src.has("type") && src.get("type").isJsonPrimitive()
						? src.get("type").getAsString() : "";
					if (!type.endsWith("paletted_permutations")) {
						continue;
					}
					if (src.has("palette_key") && src.get("palette_key").isJsonPrimitive()) {
						String rewritten = rewritePaletteId(src.get("palette_key").getAsString());
						if (!rewritten.equals(src.get("palette_key").getAsString())) {
							src.addProperty("palette_key", rewritten);
							changed = true;
						}
					}
					if (src.has("permutations") && src.get("permutations").isJsonObject()) {
						JsonObject perms = src.getAsJsonObject("permutations");
						for (Map.Entry<String, JsonElement> e : new ArrayList<>(perms.entrySet())) {
							if (!e.getValue().isJsonPrimitive()) {
								continue;
							}
							String raw = e.getValue().getAsString();
							String rewritten = rewritePaletteId(raw);
							if (!rewritten.equals(raw)) {
								perms.addProperty(e.getKey(), rewritten);
								changed = true;
							}
						}
					}
				}
				if (changed) {
					JsonUtil.writeJson(gson, atlas, root);
					Logger.debug("Rewrote trim palette atlas refs in " + atlas.getFileName());
				}
			}
		}
	}

	/**
	 * Exact legacy palette texture ids → 26.3 Palette Texture IDs under {@code textures/palettes/}.
	 */
	static String rewritePaletteId(String raw) {
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
		String prefix = "trims/color_palettes/";
		if (path.startsWith(prefix)) {
			String leaf = path.substring(prefix.length());
			if (!leaf.isEmpty() && !leaf.contains("/")) {
				return ns + ":trim/" + leaf;
			}
		}
		return raw;
	}

	private static void removeArmorTrimsAtlas(Path nsRoot, List<JsonObject> findings) throws IOException {
		Path atlas = nsRoot.resolve("atlases/armor_trims.json".replace("/", File.separator));
		if (!Files.exists(atlas)) {
			return;
		}
		Files.delete(atlas);
		Logger.log("Removed obsolete atlas armor_trims.json (26.3 / pack_format 97.1)");
		JsonObject f = new JsonObject();
		f.addProperty("level", "info");
		f.addProperty("id", "armor_trims_atlas_removed_26_3");
		f.addProperty("msg", "Removed assets/.../atlases/armor_trims.json — entity trim palettes are on-demand in 26.3");
		findings.add(f);
	}
}

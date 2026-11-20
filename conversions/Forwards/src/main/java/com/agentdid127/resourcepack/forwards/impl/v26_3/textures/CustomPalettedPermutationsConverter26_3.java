package com.agentdid127.resourcepack.forwards.impl.v26_3.textures;

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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 26.3 (pack_format 97.1): custom {@code paletted_permutations} palette images resolve under
 * {@code textures/palettes/} instead of {@code textures/}.
 *
 * <p>Sibling to {@link TrimPaletteConverter26_3} (trim-only). Leaves atlas JSON ids unchanged
 * ({@code pixel/white} still names the file under {@code textures/palettes/pixel/white.png}).
 * Base images listed in {@code textures:} stay under {@code textures/}.
 */
public class CustomPalettedPermutationsConverter26_3 extends Converter {
	public CustomPalettedPermutationsConverter26_3(PackConverter packConverter) {
		super(packConverter);
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		return from <= Util.getVersionProtocol(gson, "26.2")
			&& to >= Util.getVersionProtocol(gson, "26.3");
	}

	@Override
	public void convert(Pack pack) throws IOException {
		Path assets = pack.getWorkingPath().resolve("assets");
		if (!Files.isDirectory(assets)) {
			return;
		}

		Gson gson = packConverter.getGson();
		int moved = 0;

		File[] namespaces = assets.toFile().listFiles();
		if (namespaces == null) {
			return;
		}
		for (File nsDir : namespaces) {
			if (!nsDir.isDirectory()) {
				continue;
			}
			moved += processNamespaceAtlases(assets, nsDir.toPath(), nsDir.getName(), gson);
		}

		if (moved > 0) {
			Logger.log("Moved " + moved + " custom paletted_permutations palette(s) → textures/palettes/");
		}
	}

	private static int processNamespaceAtlases(Path assets, Path nsRoot, String defaultNs, Gson gson)
		throws IOException {
		Path atlases = nsRoot.resolve("atlases");
		if (!Files.isDirectory(atlases)) {
			return 0;
		}

		int moved = 0;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(atlases, "*.json")) {
			for (Path atlas : stream) {
				JsonObject root = JsonUtil.readJson(gson, atlas);
				if (root == null || !root.has("sources") || !root.get("sources").isJsonArray()) {
					continue;
				}
				JsonArray sources = root.getAsJsonArray("sources");
				for (JsonElement el : sources) {
					if (!el.isJsonObject()) {
						continue;
					}
					moved += processSource(assets, defaultNs, el.getAsJsonObject(), gson);
				}
			}
		}
		return moved;
	}

	private static int processSource(Path assets, String defaultNs, JsonObject src, Gson gson)
		throws IOException {
		String type = src.has("type") && src.get("type").isJsonPrimitive()
			? src.get("type").getAsString() : "";
		if (!type.endsWith("paletted_permutations")) {
			return 0;
		}

		String paletteKeyRaw = null;
		if (src.has("palette_key") && src.get("palette_key").isJsonPrimitive()) {
			paletteKeyRaw = src.get("palette_key").getAsString();
		}
		if (paletteKeyRaw == null || paletteKeyRaw.isBlank()) {
			return 0;
		}

		ParsedId keyId = parseId(paletteKeyRaw, defaultNs);
		if (keyId == null || shouldSkip(keyId.path)) {
			return 0;
		}

		// full key "ns:path" → write base_palette? (false for key itself)
		Map<String, Boolean> asPermutation = new LinkedHashMap<>();
		asPermutation.put(keyId.key(), false);

		if (src.has("permutations") && src.get("permutations").isJsonObject()) {
			JsonObject perms = src.getAsJsonObject("permutations");
			for (Map.Entry<String, JsonElement> e : perms.entrySet()) {
				if (!e.getValue().isJsonPrimitive()) {
					continue;
				}
				ParsedId permId = parseId(e.getValue().getAsString(), defaultNs);
				if (permId == null || shouldSkip(permId.path)) {
					continue;
				}
				String k = permId.key();
				if (k.equals(keyId.key())) {
					continue; // key palette — no base_palette
				}
				asPermutation.put(k, true);
			}
		}

		int moved = 0;
		Set<String> movedKeys = new LinkedHashSet<>();
		for (Map.Entry<String, Boolean> e : asPermutation.entrySet()) {
			ParsedId id = parseId(e.getKey(), defaultNs);
			if (id == null) {
				continue;
			}
			String basePaletteId = e.getValue() ? paletteKeyRaw.trim() : null;
			if (movePaletteFile(assets, id, gson, basePaletteId)) {
				moved++;
				movedKeys.add(id.key());
			}
		}

		if (!movedKeys.isEmpty()) {
			Logger.debug("Custom palette move: " + movedKeys);
		}
		return moved;
	}

	/**
	 * Move {@code assets/<ns>/textures/<id>.png} → {@code …/textures/palettes/<id>.png} (+ {@code .mcmeta}).
	 * When {@code basePaletteId} is non-null (permutation, not key), ensure
	 * {@code palette.base_palette} if missing.
	 *
	 * @return true if a PNG was moved
	 */
	private static boolean movePaletteFile(Path assets, ParsedId id, Gson gson, String basePaletteId)
		throws IOException {
		Path nsRoot = assets.resolve(id.ns);
		Path from = nsRoot.resolve("textures").resolve(id.path.replace("/", File.separator) + ".png");
		Path to = nsRoot.resolve("textures").resolve("palettes")
			.resolve(id.path.replace("/", File.separator) + ".png");
		if (!Files.exists(from) || Files.exists(to)) {
			if (basePaletteId != null && Files.exists(to)) {
				ensureBasePaletteMcmeta(to.resolveSibling(to.getFileName().toString() + ".mcmeta"),
					basePaletteId, gson);
			}
			return false;
		}

		FileUtil.moveIfExists(from, to);
		Path metaFrom = from.resolveSibling(from.getFileName().toString() + ".mcmeta");
		Path metaTo = to.resolveSibling(to.getFileName().toString() + ".mcmeta");
		FileUtil.moveIfExists(metaFrom, metaTo);

		if (basePaletteId != null && Files.exists(to)) {
			ensureBasePaletteMcmeta(metaTo, basePaletteId, gson);
		}
		Logger.debug("Moved custom palette " + id.key() + " → textures/palettes/");
		return Files.exists(to);
	}

	/** Same pattern as trim: do not overwrite an existing {@code base_palette}. */
	static void ensureBasePaletteMcmeta(Path metaPath, String basePaletteId, Gson gson)
		throws IOException {
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
				return;
			}
		} else {
			palette = new JsonObject();
			root.add("palette", palette);
		}
		palette.addProperty("base_palette", basePaletteId);
		FileUtil.ensureParentExists(metaPath);
		JsonUtil.writeJson(gson, metaPath, root);
		Logger.debug("Wrote palette.base_palette=" + basePaletteId + " → " + metaPath.getFileName());
	}

	static boolean shouldSkip(String path) {
		if (path == null || path.isEmpty()) {
			return true;
		}
		String p = path.replace("\\", "/");
		if (p.startsWith("palettes/")) {
			return true;
		}
		if (p.equals("trim_base") || p.startsWith("trim/")) {
			return true;
		}
		// Leftover legacy trim ids — TrimPaletteConverter owns these
		if (p.startsWith("trims/color_palettes/")) {
			return true;
		}
		return false;
	}

	static ParsedId parseId(String raw, String defaultNs) {
		if (raw == null) {
			return null;
		}
		String id = raw.replace("\\", "/").trim();
		if (id.isEmpty()) {
			return null;
		}
		String ns = defaultNs != null ? defaultNs : "minecraft";
		String path = id;
		int colon = id.indexOf(':');
		if (colon >= 0) {
			ns = id.substring(0, colon);
			path = id.substring(colon + 1);
		}
		if (path.isEmpty()) {
			return null;
		}
		return new ParsedId(ns, path);
	}

	static final class ParsedId {
		final String ns;
		final String path;

		ParsedId(String ns, String path) {
			this.ns = ns;
			this.path = path;
		}

		String key() {
			return ns + ":" + path;
		}
	}
}

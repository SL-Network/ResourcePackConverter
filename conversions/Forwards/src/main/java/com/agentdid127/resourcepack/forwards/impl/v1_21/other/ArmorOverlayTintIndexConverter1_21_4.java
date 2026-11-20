package com.agentdid127.resourcepack.forwards.impl.v1_21.other;

import com.agentdid127.resourcepack.library.Converter;
import com.agentdid127.resourcepack.library.PackConverter;
import com.agentdid127.resourcepack.library.pack.Pack;
import com.agentdid127.resourcepack.library.utilities.JsonUtil;
import com.agentdid127.resourcepack.library.utilities.Logger;
import com.agentdid127.resourcepack.library.utilities.Util;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 1.21.4+: item tints come from {@code items/*.json}. Leather-style suit arm models
 * (e.g. TSF {@code colored_arm_model}) often put {@code tintindex} on overlay faces
 * as well as the dyeable base. Overlay layers must stay untinted (stitches / shade);
 * dyeing them muddies sleeves on modern clients.
 *
 * <p>Removes {@code tintindex} from faces whose texture resolves to an {@code *_overlay}
 * path (including {@code #layer1} / {@code #1} when that slot maps to an overlay).
 */
public class ArmorOverlayTintIndexConverter1_21_4 extends Converter {
	public ArmorOverlayTintIndexConverter1_21_4(PackConverter packConverter) {
		super(packConverter);
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		return from < Util.getVersionProtocol(gson, "1.21.4")
			&& to >= Util.getVersionProtocol(gson, "1.21.4");
	}

	@Override
	public void convert(Pack pack) throws IOException {
		Path assets = pack.getWorkingPath().resolve("assets");
		if (!Files.isDirectory(assets)) {
			return;
		}
		Gson gson = packConverter.getGson();
		File[] namespaces = assets.toFile().listFiles();
		if (namespaces == null) {
			return;
		}
		for (File ns : namespaces) {
			if (!ns.isDirectory()) {
				continue;
			}
			Path itemModels = ns.toPath().resolve("models").resolve("item");
			if (!Files.isDirectory(itemModels)) {
				continue;
			}
			Files.walkFileTree(itemModels, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (!file.getFileName().toString().endsWith(".json")) {
						return FileVisitResult.CONTINUE;
					}
					try {
						JsonObject model = JsonUtil.readJson(gson, file);
						if (model == null) {
							return FileVisitResult.CONTINUE;
						}
						Set<String> overlayKeys = overlayTextureKeys(model);
						if (overlayKeys.isEmpty() && !hasOverlayTextureMap(model)) {
							return FileVisitResult.CONTINUE;
						}
						if (stripOverlayTints(model, overlayKeys)) {
							JsonUtil.writeJson(gson, file, model);
							Logger.debug("Stripped overlay tintindex in " + file.getFileName());
						}
					} catch (IOException ignored) {
						// best-effort per model
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}

	private static boolean hasOverlayTextureMap(JsonObject model) {
		if (!model.has("textures") || !model.get("textures").isJsonObject()) {
			return false;
		}
		for (Map.Entry<String, JsonElement> e : model.getAsJsonObject("textures").entrySet()) {
			if (e.getValue().isJsonPrimitive() && isOverlayPath(e.getValue().getAsString())) {
				return true;
			}
		}
		return false;
	}

	/** Texture map keys whose values point at overlay PNGs (layer1, 1, …). */
	private static Set<String> overlayTextureKeys(JsonObject model) {
		Set<String> keys = new HashSet<>();
		if (!model.has("textures") || !model.get("textures").isJsonObject()) {
			return keys;
		}
		for (Map.Entry<String, JsonElement> e : model.getAsJsonObject("textures").entrySet()) {
			if (!e.getValue().isJsonPrimitive()) {
				continue;
			}
			if (isOverlayPath(e.getValue().getAsString())) {
				keys.add(e.getKey());
			}
		}
		return keys;
	}

	private static boolean isOverlayPath(String raw) {
		if (raw == null) {
			return false;
		}
		String path = raw.replace("\\", "/");
		if (path.contains(":")) {
			path = path.substring(path.indexOf(':') + 1);
		}
		path = path.replaceAll("^/+", "");
		return path.endsWith("_overlay") || path.contains("_overlay/") || path.endsWith("_overlay.png");
	}

	private static boolean stripOverlayTints(JsonObject model, Set<String> overlayKeys) {
		if (!model.has("elements") || !model.get("elements").isJsonArray()) {
			return false;
		}
		boolean changed = false;
		for (JsonElement el : model.getAsJsonArray("elements")) {
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
				if (!face.has("tintindex") || !face.has("texture") || !face.get("texture").isJsonPrimitive()) {
					continue;
				}
				String tex = face.get("texture").getAsString();
				if (isOverlayTextureRef(tex, overlayKeys)) {
					face.remove("tintindex");
					changed = true;
				}
			}
		}
		return changed;
	}

	private static boolean isOverlayTextureRef(String tex, Set<String> overlayKeys) {
		if (tex == null) {
			return false;
		}
		String t = tex.trim();
		if (t.startsWith("#")) {
			String key = t.substring(1);
			return overlayKeys.contains(key);
		}
		return isOverlayPath(t);
	}
}

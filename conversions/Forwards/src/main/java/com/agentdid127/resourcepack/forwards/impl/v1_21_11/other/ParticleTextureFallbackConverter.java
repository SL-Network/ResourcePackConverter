package com.agentdid127.resourcepack.forwards.impl.v1_21_11.other;

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
import java.util.Map;

/**
 * Modern clients warn when a model has {@code textures} but no {@code particle} slot
 * ({@code Missing texture references in model … particle}). Adventure packs often omit it.
 *
 * <p>When {@code textures} is non-empty and {@code particle} is absent, set
 * {@code "particle": "#&lt;key&gt;"} referencing an existing texture key ({@code layer0},
 * {@code 0}, or the first key). Does not invent texture paths.
 */
public class ParticleTextureFallbackConverter extends Converter {
	public ParticleTextureFallbackConverter(PackConverter packConverter) {
		super(packConverter);
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		return to >= Util.getVersionProtocol(gson, "1.21.11");
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
		int[] filled = {0};
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
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (!file.getFileName().toString().endsWith(".json")) {
						return FileVisitResult.CONTINUE;
					}
					try {
						if (fillParticleIfMissing(gson, file)) {
							filled[0]++;
						}
					} catch (IOException ignored) {
						// best-effort
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
		if (filled[0] > 0) {
			Logger.log("Filled missing particle texture slot on " + filled[0] + " model(s)");
		}
	}

	/**
	 * @return true if the model was written with a new particle ref
	 */
	static boolean fillParticleIfMissing(Gson gson, Path file) throws IOException {
		JsonObject model = JsonUtil.readJson(gson, file);
		if (model == null || !model.has("textures") || !model.get("textures").isJsonObject()) {
			return false;
		}
		JsonObject textures = model.getAsJsonObject("textures");
		if (textures.size() == 0 || textures.has("particle")) {
			return false;
		}
		String key = pickParticleSourceKey(textures);
		if (key == null) {
			return false;
		}
		textures.addProperty("particle", "#" + key);
		JsonUtil.writeJson(gson, file, model);
		return true;
	}

	/**
	 * Prefer {@code layer0}, then {@code 0}, else first key whose value is not {@code #particle}.
	 */
	static String pickParticleSourceKey(JsonObject textures) {
		if (textures.has("layer0") && isUsableParticleSource(textures, "layer0")) {
			return "layer0";
		}
		if (textures.has("0") && isUsableParticleSource(textures, "0")) {
			return "0";
		}
		for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
			if (isUsableParticleSource(textures, entry.getKey())) {
				return entry.getKey();
			}
		}
		return null;
	}

	private static boolean isUsableParticleSource(JsonObject textures, String key) {
		JsonElement value = textures.get(key);
		if (value == null || !value.isJsonPrimitive()) {
			return false;
		}
		String raw = value.getAsString().trim();
		// Avoid "#particle" → "#particle" cycles.
		return !raw.equals("#particle");
	}
}

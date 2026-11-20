package com.agentdid127.resourcepack.forwards.impl.v26_2.textures;

import com.agentdid127.resourcepack.library.Converter;
import com.agentdid127.resourcepack.library.PackConverter;
import com.agentdid127.resourcepack.library.pack.Pack;
import com.agentdid127.resourcepack.library.utilities.FileUtil;
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
 * 26.2 (pack_format 88.0): pillar side textures renamed.
 *
 * <p>Evidence: {@code knowledge/vanilla/26.1} {@code block/purpur_pillar.png} /
 * {@code quartz_pillar.png} → {@code 26.2} {@code *_pillar_side.png} (same SHA-256);
 * models switch {@code side} refs. Rename JSON removed_sample lists the old paths.
 */
public class PillarSideConverter26_2 extends Converter {
	private static final String[][] MOVES = {
		{"block/purpur_pillar.png", "block/purpur_pillar_side.png"},
		{"block/quartz_pillar.png", "block/quartz_pillar_side.png"},
	};

	private static final String[][] TEXTURE_REFS = {
		{"block/purpur_pillar", "block/purpur_pillar_side"},
		{"minecraft:block/purpur_pillar", "minecraft:block/purpur_pillar_side"},
		{"block/quartz_pillar", "block/quartz_pillar_side"},
		{"minecraft:block/quartz_pillar", "minecraft:block/quartz_pillar_side"},
	};

	public PillarSideConverter26_2(PackConverter packConverter) {
		super(packConverter);
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		return from <= Util.getVersionProtocol(gson, "26.1")
			&& to >= Util.getVersionProtocol(gson, "26.2");
	}

	@Override
	public void convert(Pack pack) throws IOException {
		Path textures = pack.getWorkingPath().resolve("assets/minecraft/textures".replace("/", File.separator));
		if (Files.isDirectory(textures)) {
			for (String[] move : MOVES) {
				Path from = textures.resolve(move[0].replace("/", File.separator));
				Path to = textures.resolve(move[1].replace("/", File.separator));
				if (!Files.exists(from)) {
					continue;
				}
				FileUtil.moveIfExists(from, to);
				Logger.debug("Moved pillar texture " + move[0] + " → " + move[1]);
				Path fromMeta = from.resolveSibling(from.getFileName().toString() + ".mcmeta");
				Path toMeta = to.resolveSibling(to.getFileName().toString() + ".mcmeta");
				FileUtil.moveIfExists(fromMeta, toMeta);
			}
		}

		rewriteModelTextureRefs(pack.getWorkingPath(), packConverter.getGson());
	}

	static void rewriteModelTextureRefs(Path packRoot, Gson gson) throws IOException {
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
			Path models = nsDir.toPath().resolve("models");
			if (!Files.isDirectory(models)) {
				continue;
			}
			Files.walkFileTree(models, new SimpleFileVisitor<Path>() {
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
							String rewritten = rewritePillarRef(raw);
							if (!rewritten.equals(raw)) {
								textures.addProperty(entry.getKey(), rewritten);
								changed = true;
							}
						}
						if (changed) {
							JsonUtil.writeJson(gson, file, model);
							Logger.debug("Rewrote pillar texture refs in " + file.getFileName());
						}
					} catch (IOException ignored) {
						// Best-effort per model.
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}

	/**
	 * Exact path match only — do not rewrite {@code *_pillar_top} or already-migrated {@code *_side}.
	 */
	static String rewritePillarRef(String raw) {
		if (raw == null) {
			return raw;
		}
		String trimmed = raw.replace("\\", "/").trim();
		for (String[] pair : TEXTURE_REFS) {
			if (trimmed.equals(pair[0])) {
				return pair[1];
			}
		}
		return raw;
	}
}

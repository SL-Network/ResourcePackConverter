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
import java.util.Map;
import java.util.Set;

/**
 * 1.21.4: {@code textures/item/empty_armor_slot_*.png} moved to
 * {@code textures/gui/sprites/container/slot/*} (jar diff). GUI sprites are not on the
 * items/blocks atlases, so item models that used those ids (invisible armor hosts) break.
 *
 * <p>When the pack ships {@code textures/block/transparent.png}, rewrite model texture
 * refs matching {@code item/empty_armor_slot_*} to {@code block/transparent}. Do not
 * point item models at {@code gui/sprites/...}.
 */
public class EmptyArmorSlotTextureConverter1_21_4 extends Converter {
	private static final Set<String> EMPTY_ARMOR_SLOTS = Set.of(
		"empty_armor_slot_helmet",
		"empty_armor_slot_chestplate",
		"empty_armor_slot_leggings",
		"empty_armor_slot_boots",
		"empty_armor_slot_shield"
	);

	public EmptyArmorSlotTextureConverter1_21_4(PackConverter packConverter) {
		super(packConverter);
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		return from <= Util.getVersionProtocol(gson, "1.21.3")
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

		int rewritten = 0;
		for (File nsDir : namespaces) {
			if (!nsDir.isDirectory()) {
				continue;
			}
			Path transparent = nsDir.toPath().resolve("textures")
				.resolve("block").resolve("transparent.png");
			if (!Files.exists(transparent)) {
				continue;
			}
			Path modelsRoot = nsDir.toPath().resolve("models");
			if (!Files.isDirectory(modelsRoot)) {
				continue;
			}
			String nsName = nsDir.getName();
			rewritten += rewriteModels(gson, modelsRoot, nsName);
		}
		if (rewritten > 0) {
			Logger.log("Rewrote " + rewritten + " empty_armor_slot texture ref(s) to block/transparent");
		}
	}

	private static int rewriteModels(Gson gson, Path modelsRoot, String namespace) throws IOException {
		int[] count = {0};
		Files.walkFileTree(modelsRoot, new SimpleFileVisitor<Path>() {
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
						String replacement = remapEmptyArmorSlot(raw, namespace);
						if (replacement != null) {
							textures.addProperty(entry.getKey(), replacement);
							changed = true;
							count[0]++;
						}
					}
					if (changed) {
						JsonUtil.writeJson(gson, file, model);
					}
				} catch (IOException ignored) {
					// Best-effort per model.
				}
				return FileVisitResult.CONTINUE;
			}
		});
		return count[0];
	}

	/**
	 * @return {@code block/transparent} (preserving namespace prefix if present) when
	 * {@code raw} is an empty armor slot item texture; otherwise {@code null}
	 */
	static String remapEmptyArmorSlot(String raw, String packNamespace) {
		if (raw == null) {
			return null;
		}
		String trimmed = raw.replace("\\", "/").trim();
		if (trimmed.isEmpty() || trimmed.startsWith("#")) {
			return null;
		}

		String refNamespace = null;
		String path = trimmed;
		int colon = trimmed.indexOf(':');
		if (colon >= 0) {
			refNamespace = trimmed.substring(0, colon);
			path = trimmed.substring(colon + 1);
		}
		path = path.replaceAll("^/+", "");
		if (!path.startsWith("item/")) {
			return null;
		}
		String name = path.substring("item/".length());
		if (!EMPTY_ARMOR_SLOTS.contains(name)) {
			return null;
		}

		// Prefer same namespace as the ref when present; else pack namespace that owns transparent.
		if (refNamespace != null) {
			return refNamespace + ":block/transparent";
		}
		if (packNamespace != null && !"minecraft".equals(packNamespace)) {
			return packNamespace + ":block/transparent";
		}
		return "block/transparent";
	}
}

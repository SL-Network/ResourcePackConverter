package com.agentdid127.resourcepack.forwards.impl.v1_21.textures;

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
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 1.21.2: armor layer textures move from {@code textures/models/armor/} to
 * {@code textures/entity/equipment/humanoid*} .
 *
 * <p>Vanilla materials use {@code <material>_layer_1.png} / {@code _layer_2.png}
 * (plus leather overlays and {@code turtle_layer_1.png} → {@code turtle_scute.png}).
 * Any pack material following that naming is moved the same way — not only the
 * hardcoded vanilla set.
 *
 * <p>Non-layer custom PNGs (e.g. TSF {@code noxcrew.png}) are often still referenced
 * by item models. Those are <em>copied</em> into {@code humanoid/} for equipment
 * coverage while leaving the {@code models/armor/} originals for item-model / atlas
 * use. Equipment JSON is not invented.
 *
 * <p>Also rewrites item-model texture refs wrongly prefixed to
 * {@code item/models/armor/...} back to {@code models/armor/...} when the PNG exists
 * under {@code textures/models/armor/}.
 */
public class ArmorMoverConverter1_21_2 extends Converter {
	public ArmorMoverConverter1_21_2(PackConverter packConverter) {
		super(packConverter);
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		return from <= Util.getVersionProtocol(gson, "1.21.1") && to >= Util.getVersionProtocol(gson, "1.21.2");
	}

	@Override
	public void convert(Pack pack) throws IOException {
		Path texturesPath = pack.getWorkingPath().resolve("assets/minecraft/textures".replace("/", File.separator));
		if (!Files.isDirectory(texturesPath)) {
			return;
		}

		Path modelsArmorPath = texturesPath.resolve("models/armor".replace("/", File.separator));
		if (Files.isDirectory(modelsArmorPath)) {
			Path equipmentFolderPath = texturesPath.resolve("entity/equipment".replace("/", File.separator));
			Files.createDirectories(equipmentFolderPath);

			Path humanoidPath = equipmentFolderPath.resolve("humanoid");
			Path humanoidLeggingsPath = equipmentFolderPath.resolve("humanoid_leggings");

			File[] armorFiles = modelsArmorPath.toFile().listFiles();
			if (armorFiles != null) {
				for (File file : armorFiles) {
					if (!file.isFile()) {
						continue;
					}
					String name = file.getName();
					if (!name.endsWith(".png") && !name.endsWith(".png.mcmeta")) {
						continue;
					}
					boolean meta = name.endsWith(".png.mcmeta");
					String stem = meta ? name.substring(0, name.length() - ".png.mcmeta".length())
						: name.substring(0, name.length() - ".png".length());
					migrateArmorFile(file.toPath(), stem, meta, humanoidPath, humanoidLeggingsPath);
				}
			}

			// Remove empty models/armor (and models/) if nothing custom remains.
			tryDeleteEmpty(modelsArmorPath);
			tryDeleteEmpty(modelsArmorPath.getParent());
		}

		rewriteItemModelArmorTextureRefs(pack.getWorkingPath(), packConverter.getGson());
	}

	/**
	 * Move layer-pattern armor textures; copy residual custom PNGs into humanoid.
	 */
	private static void migrateArmorFile(
		Path source,
		String stem,
		boolean meta,
		Path humanoidPath,
		Path humanoidLeggingsPath
	) throws IOException {
		String destName;
		Path destDir;
		boolean move;

		if ("turtle_layer_1".equals(stem)) {
			destName = "turtle_scute" + (meta ? ".png.mcmeta" : ".png");
			destDir = humanoidPath;
			move = true;
		} else if (stem.endsWith("_layer_1_overlay")) {
			String material = stem.substring(0, stem.length() - "_layer_1_overlay".length());
			destName = material + "_overlay" + (meta ? ".png.mcmeta" : ".png");
			destDir = humanoidPath;
			move = true;
		} else if (stem.endsWith("_layer_2_overlay")) {
			String material = stem.substring(0, stem.length() - "_layer_2_overlay".length());
			destName = material + "_overlay" + (meta ? ".png.mcmeta" : ".png");
			destDir = humanoidLeggingsPath;
			move = true;
		} else if (stem.endsWith("_layer_1")) {
			String material = stem.substring(0, stem.length() - "_layer_1".length());
			destName = material + (meta ? ".png.mcmeta" : ".png");
			destDir = humanoidPath;
			move = true;
		} else if (stem.endsWith("_layer_2")) {
			String material = stem.substring(0, stem.length() - "_layer_2".length());
			destName = material + (meta ? ".png.mcmeta" : ".png");
			destDir = humanoidLeggingsPath;
			move = true;
		} else {
			// Non-layer residual (noxcrew, rainbow, white, …): copy into humanoid, keep original.
			destName = stem + (meta ? ".png.mcmeta" : ".png");
			destDir = humanoidPath;
			move = false;
		}

		Files.createDirectories(destDir);
		Path dest = destDir.resolve(destName);
		if (move) {
			Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
			Logger.debug("Moved armor " + source.getFileName() + " → " + dest);
		} else {
			Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
			Logger.debug("Copied custom armor " + source.getFileName() + " → " + dest);
		}
	}

	/**
	 * ModelConverter's 1.9 layer0 heuristic used to prefix {@code item/} onto any
	 * non-{@code item/} path, turning {@code models/armor/noxcrew} into missing
	 * {@code item/models/armor/noxcrew}. Rewrite those back when the PNG exists.
	 */
	static void rewriteItemModelArmorTextureRefs(Path packRoot, Gson gson) throws IOException {
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
			Path itemModels = nsDir.toPath().resolve("models").resolve("item");
			if (!Files.isDirectory(itemModels)) {
				continue;
			}
			Path texturesRoot = nsDir.toPath().resolve("textures");
			Files.walkFileTree(itemModels, new SimpleFileVisitor<Path>() {
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
							String fixed = fixItemModelsArmorRef(texturesRoot, raw);
							if (fixed != null && !fixed.equals(raw)) {
								textures.addProperty(entry.getKey(), fixed);
								changed = true;
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
		}
	}

	/**
	 * {@code item/models/armor/X} → {@code models/armor/X} when
	 * {@code textures/models/armor/X.png} exists.
	 */
	static String fixItemModelsArmorRef(Path texturesRoot, String raw) {
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

		final String badPrefix = "item/models/armor/";
		if (!path.startsWith(badPrefix)) {
			return raw;
		}

		String armorRel = "models/armor/" + path.substring(badPrefix.length());
		Path png = texturesRoot.resolve(armorRel.replace("/", File.separator) + ".png");
		if (!Files.exists(png)) {
			return raw;
		}

		if (namespace != null) {
			return namespace + ":" + armorRel;
		}
		return armorRel;
	}

	private static void tryDeleteEmpty(Path dir) throws IOException {
		if (dir == null || !Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> stream = Files.list(dir)) {
			if (!stream.findAny().isPresent()) {
				Files.delete(dir);
			}
		}
	}
}

package com.agentdid127.resourcepack.forwards.impl.v26_2.other;

import com.agentdid127.resourcepack.forwards.impl.v26_2.textures.BedsSignsSlicerDefinitions26_2;
import com.agentdid127.resourcepack.library.Converter;
import com.agentdid127.resourcepack.library.PackConverter;
import com.agentdid127.resourcepack.library.pack.Pack;
import com.agentdid127.resourcepack.library.utilities.JsonUtil;
import com.agentdid127.resourcepack.library.utilities.Logger;
import com.agentdid127.resourcepack.library.utilities.Util;
import com.agentdid127.resourcepack.library.utilities.slicing.multipart.InputSpec;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 26.2 (pack_format 88.0): beds/signs become block models; {@code beds}/{@code signs}
 * atlases are removed.
 *
 * <p>Entity bed/sign textures are sliced into {@code textures/block/...} using the
 * official Mojang Slicer v1.2.1 UV definitions (MIT). Particle-only obsolete
 * {@code models/block/*_sign.json} / {@code bed.json} are removed when present.
 */
public class BedsSignsConverter26_2 extends Converter {
	private static final String[] REMOVED_ATLASES = {"beds.json", "signs.json"};

	/** Particle-only block models removed in 26.2 (jar diff removed_sample). */
	private static final String[] OBSOLETE_BLOCK_MODELS = {
		"bed.json",
		"oak_sign.json", "spruce_sign.json", "birch_sign.json", "jungle_sign.json",
		"acacia_sign.json", "dark_oak_sign.json", "mangrove_sign.json", "cherry_sign.json",
		"bamboo_sign.json", "crimson_sign.json", "warped_sign.json", "pale_oak_sign.json",
		"oak_hanging_sign.json", "spruce_hanging_sign.json", "birch_hanging_sign.json",
		"jungle_hanging_sign.json", "acacia_hanging_sign.json", "dark_oak_hanging_sign.json",
		"mangrove_hanging_sign.json", "cherry_hanging_sign.json", "bamboo_hanging_sign.json",
		"crimson_hanging_sign.json", "warped_hanging_sign.json", "pale_oak_hanging_sign.json",
	};

	public BedsSignsConverter26_2(PackConverter packConverter) {
		super(packConverter);
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		return from <= Util.getVersionProtocol(gson, "26.1")
			&& to >= Util.getVersionProtocol(gson, "26.2");
	}

	@Override
	public void convert(Pack pack) throws IOException {
		Path root = pack.getWorkingPath();
		Path minecraft = root.resolve("assets/minecraft".replace("/", File.separator));
		if (!Files.isDirectory(minecraft)) {
			return;
		}

		List<JsonObject> findings = new ArrayList<>();

		Path atlases = minecraft.resolve("atlases");
		for (String name : REMOVED_ATLASES) {
			Path atlas = atlases.resolve(name);
			if (Files.exists(atlas)) {
				Files.delete(atlas);
				Logger.log("Removed obsolete atlas " + name + " (26.2 / pack_format 88.0)");
				JsonObject f = new JsonObject();
				f.addProperty("level", "info");
				f.addProperty("id", "atlas_removed_26_2");
				f.addProperty("msg", "Removed assets/minecraft/atlases/" + name);
				findings.add(f);
			}
		}

		Path assets = root.resolve("assets");
		sliceEntityBedsAndSigns(assets, findings);

		Path textures = minecraft.resolve("textures");
		collectLeftoverEntityTextures(root, textures.resolve("entity/bed".replace("/", File.separator)),
			"bed_entity_texture_needs_slicer",
			"26.2 leftover textures/entity/bed/*.png (unknown color or failed slice)",
			findings);
		collectLeftoverEntityTextures(root, textures.resolve("entity/signs".replace("/", File.separator)),
			"sign_entity_texture_needs_slicer",
			"26.2 leftover textures/entity/signs/** (unknown wood or failed slice)",
			findings);

		Path blockModels = minecraft.resolve("models/block".replace("/", File.separator));
		if (Files.isDirectory(blockModels)) {
			for (String name : OBSOLETE_BLOCK_MODELS) {
				Path model = blockModels.resolve(name);
				if (Files.exists(model) && isParticleOnlyModel(packConverter.getGson(), model)) {
					Files.delete(model);
					Logger.log("Removed obsolete particle-only bed/sign model " + name);
					JsonObject f = new JsonObject();
					f.addProperty("level", "info");
					f.addProperty("id", "obsolete_bed_sign_block_model_removed_26_2");
					f.addProperty("msg", "Removed assets/minecraft/models/block/" + name
						+ " (particle-only; removed in 26.2 jar)");
					findings.add(f);
				} else if (Files.exists(model)) {
					JsonObject f = new JsonObject();
					f.addProperty("level", "review");
					f.addProperty("id", "obsolete_bed_sign_block_model_26_2");
					f.addProperty("msg", "assets/minecraft/models/block/" + name
						+ " still present but not particle-only; review manually — do not invent geometry");
					f.addProperty("path", "assets/minecraft/models/block/" + name);
					findings.add(f);
					Logger.log("Review: non-particle bed/sign model " + name);
				}
			}
		}

		appendReviewFindings(root, packConverter.getGson(), findings);
	}

	private static void sliceEntityBedsAndSigns(Path assetsRoot, List<JsonObject> findings) throws IOException {
		Set<String> processedInputs = new HashSet<>();
		for (InputSpec spec : BedsSignsSlicerDefinitions26_2.all()) {
			boolean ok = spec.process(assetsRoot, false);
			if (ok) {
				processedInputs.add(spec.relativePath());
				Logger.log("Sliced " + spec.relativePath() + " → block/gui sign/bed faces (Mojang Slicer v1.2.1)");
			}
		}
		for (String rel : processedInputs) {
			Path input = assetsRoot.resolve(rel.replace("/", File.separator));
			Files.deleteIfExists(input);
			Files.deleteIfExists(input.resolveSibling(input.getFileName().toString() + ".mcmeta"));
		}
		if (!processedInputs.isEmpty()) {
			JsonObject f = new JsonObject();
			f.addProperty("level", "info");
			f.addProperty("id", "beds_signs_sliced_26_2");
			f.addProperty("msg", "Sliced " + processedInputs.size()
				+ " entity bed/sign texture(s) via Mojang Slicer v1.2.1 definitions");
			findings.add(f);
		}
		pruneEmptyDirs(assetsRoot.resolve("minecraft/textures/entity/bed".replace("/", File.separator)));
		pruneEmptyDirs(assetsRoot.resolve("minecraft/textures/entity/signs".replace("/", File.separator)));
	}

	private static boolean isParticleOnlyModel(Gson gson, Path model) throws IOException {
		JsonObject obj = JsonUtil.readJson(gson, model);
		if (obj == null) {
			return false;
		}
		// Vanilla 26.1 particle-only: only "textures":{"particle":...} (no elements/parent).
		if (obj.has("elements") || obj.has("parent")) {
			return false;
		}
		if (!obj.has("textures") || !obj.get("textures").isJsonObject()) {
			return false;
		}
		JsonObject textures = obj.getAsJsonObject("textures");
		return textures.size() == 1 && textures.has("particle");
	}

	private static void pruneEmptyDirs(Path dir) throws IOException {
		if (dir == null || !Files.isDirectory(dir)) {
			return;
		}
		Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
				try (java.util.stream.Stream<Path> stream = Files.list(d)) {
					if (!stream.findAny().isPresent()) {
						Files.delete(d);
					}
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void collectLeftoverEntityTextures(Path packRoot, Path dir, String id, String msg,
		List<JsonObject> findings) throws IOException {
		if (!Files.isDirectory(dir)) {
			return;
		}
		Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				String name = file.getFileName().toString();
				if (!name.endsWith(".png")) {
					return FileVisitResult.CONTINUE;
				}
				JsonObject f = new JsonObject();
				f.addProperty("level", "review");
				f.addProperty("id", id);
				f.addProperty("msg", msg);
				f.addProperty("path", packRoot.relativize(file).toString().replace("\\", "/"));
				findings.add(f);
				Logger.log("Review: " + id + " @ " + file.getFileName());
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/** Append findings into pack-root {@code conversion_review.json} for harness scoring. */
	public static void appendReviewFindings(Path packRoot, Gson gson, List<JsonObject> findings) throws IOException {
		if (findings.isEmpty()) {
			return;
		}
		Path reviewPath = packRoot.resolve("conversion_review.json");
		JsonObject root;
		JsonArray arr;
		if (Files.exists(reviewPath)) {
			root = JsonUtil.readJson(gson, reviewPath);
			if (root == null) {
				root = new JsonObject();
			}
			if (root.has("findings") && root.get("findings").isJsonArray()) {
				arr = root.getAsJsonArray("findings");
			} else {
				arr = new JsonArray();
				root.add("findings", arr);
			}
		} else {
			root = new JsonObject();
			arr = new JsonArray();
			root.add("findings", arr);
		}
		for (JsonObject f : findings) {
			arr.add(f);
		}
		JsonUtil.writeJson(gson, reviewPath, root);
	}
}

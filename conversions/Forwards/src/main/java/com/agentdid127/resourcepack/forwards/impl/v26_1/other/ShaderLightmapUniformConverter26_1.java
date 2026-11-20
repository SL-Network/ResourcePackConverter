package com.agentdid127.resourcepack.forwards.impl.v26_1.other;

import com.agentdid127.resourcepack.forwards.impl.v26_2.other.BedsSignsConverter26_2;
import com.agentdid127.resourcepack.library.Converter;
import com.agentdid127.resourcepack.library.PackConverter;
import com.agentdid127.resourcepack.library.pack.Pack;
import com.agentdid127.resourcepack.library.utilities.Logger;
import com.agentdid127.resourcepack.library.utilities.Util;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * 26.1 (pack_format 84.0): lightmap / {@code LightmapInfo} uniform renames.
 *
 * <p>Mechanical (wiki + vanilla 1.21.11→26.1 lightmap.fsh):
 * {@code DarkenWorldFactor} → {@code BossOverlayWorldDarkeningFactor}.
 *
 * <p>N/A / review: {@code AmbientLightFactor} removed (value premultiplied into
 * {@code AmbientColor}) — cannot safely rewrite pack lightmap math; emit review
 * when the identifier remains. Packs without shaders are no-ops.
 */
public class ShaderLightmapUniformConverter26_1 extends Converter {
	public ShaderLightmapUniformConverter26_1(PackConverter packConverter) {
		super(packConverter);
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		return from <= Util.getVersionProtocol(gson, "1.21.11")
			&& to >= Util.getVersionProtocol(gson, "26.1");
	}

	@Override
	public void convert(Pack pack) throws IOException {
		Path shaders = pack.getWorkingPath()
			.resolve("assets/minecraft/shaders".replace("/", File.separator));
		if (!Files.isDirectory(shaders)) {
			return;
		}

		List<JsonObject> findings = new ArrayList<>();
		List<String> renamedFiles = new ArrayList<>();
		List<String> ambientHits = new ArrayList<>();

		Files.walkFileTree(shaders, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String name = file.getFileName().toString();
				if (!(name.endsWith(".fsh") || name.endsWith(".vsh") || name.endsWith(".glsl"))) {
					return FileVisitResult.CONTINUE;
				}
				String src = Files.readString(file, StandardCharsets.UTF_8);
				String next = src.replace("DarkenWorldFactor", "BossOverlayWorldDarkeningFactor");
				if (!next.equals(src)) {
					Files.writeString(file, next, StandardCharsets.UTF_8);
					renamedFiles.add(name);
					Logger.log("Renamed DarkenWorldFactor → BossOverlayWorldDarkeningFactor in " + name);
				}
				if (next.contains("AmbientLightFactor")) {
					ambientHits.add(pack.getWorkingPath().relativize(file).toString().replace("\\", "/"));
				}
				return FileVisitResult.CONTINUE;
			}
		});

		if (!renamedFiles.isEmpty()) {
			JsonObject f = new JsonObject();
			f.addProperty("level", "info");
			f.addProperty("id", "shader_lightmap_uniform_rename_26_1");
			f.addProperty("msg", "Renamed DarkenWorldFactor → BossOverlayWorldDarkeningFactor in "
				+ renamedFiles.size() + " shader file(s)");
			findings.add(f);
		}
		for (String path : ambientHits) {
			JsonObject f = new JsonObject();
			f.addProperty("level", "review");
			f.addProperty("id", "shader_ambient_light_factor_na_26_1");
			f.addProperty("msg", "AmbientLightFactor removed in 26.1 (premultiplied into AmbientColor); "
				+ "content port is N/A — review manually");
			f.addProperty("path", path);
			findings.add(f);
		}

		BedsSignsConverter26_2.appendReviewFindings(pack.getWorkingPath(), packConverter.getGson(), findings);
	}
}

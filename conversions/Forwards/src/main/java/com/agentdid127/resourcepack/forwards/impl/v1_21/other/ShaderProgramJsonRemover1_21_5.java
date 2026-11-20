package com.agentdid127.resourcepack.forwards.impl.v1_21.other;

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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.21.5 (pack_format 55): shader <em>program</em> JSON definitions removed for core
 * and post-processing shaders. Keep {@code .vsh}/{@code .fsh}; delete {@code .json}
 * under {@code shaders/core}, {@code shaders/post}, and {@code post_effect} companions
 * that are program defs.
 *
 * <p>Evidence: wiki pack format 55 / 25w07a — "removed shader program definitions for
 * core shaders and post-processing effects as JSON files". Vanilla 1.21.4 has
 * {@code shaders/core/*.json}; 1.21.5 has none. Packs without shaders are no-ops.
 */
public class ShaderProgramJsonRemover1_21_5 extends Converter {
	public ShaderProgramJsonRemover1_21_5(PackConverter packConverter) {
		super(packConverter);
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		return from <= Util.getVersionProtocol(gson, "1.21.4")
			&& to >= Util.getVersionProtocol(gson, "1.21.5");
	}

	@Override
	public void convert(Pack pack) throws IOException {
		Path minecraft = pack.getWorkingPath().resolve("assets/minecraft".replace("/", File.separator));
		if (!Files.isDirectory(minecraft)) {
			return;
		}

		List<JsonObject> findings = new ArrayList<>();
		int removed = 0;
		removed += deleteJsonTree(minecraft.resolve("shaders/core".replace("/", File.separator)));
		removed += deleteJsonTree(minecraft.resolve("shaders/post".replace("/", File.separator)));
		// post_effect/ holds relocated post programs after PostEffectShadersConverter1_21_2;
		// program JSON there is also obsolete in 1.21.5+.
		removed += deleteJsonTree(minecraft.resolve("post_effect"));

		if (removed > 0) {
			Logger.log("Removed " + removed + " shader program JSON file(s) (1.21.5 / pack_format 55)");
			JsonObject f = new JsonObject();
			f.addProperty("level", "info");
			f.addProperty("id", "shader_program_json_removed_1_21_5");
			f.addProperty("msg", "Removed " + removed + " obsolete shader program .json file(s)");
			findings.add(f);
		}

		BedsSignsConverter26_2.appendReviewFindings(pack.getWorkingPath(), packConverter.getGson(), findings);
	}

	private static int deleteJsonTree(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) {
			return 0;
		}
		List<Path> toDelete = new ArrayList<>();
		Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (file.getFileName().toString().endsWith(".json")) {
					toDelete.add(file);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		for (Path p : toDelete) {
			Files.deleteIfExists(p);
		}
		return toDelete.size();
	}
}

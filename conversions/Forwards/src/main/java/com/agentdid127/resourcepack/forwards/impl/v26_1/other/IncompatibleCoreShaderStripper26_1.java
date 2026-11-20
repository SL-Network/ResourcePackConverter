package com.agentdid127.resourcepack.forwards.impl.v26_1.other;

import com.agentdid127.resourcepack.forwards.impl.v26_2.other.BedsSignsConverter26_2;
import com.agentdid127.resourcepack.library.Converter;
import com.agentdid127.resourcepack.library.PackConverter;
import com.agentdid127.resourcepack.library.pack.Pack;
import com.agentdid127.resourcepack.library.utilities.FileUtil;
import com.agentdid127.resourcepack.library.utilities.Logger;
import com.agentdid127.resourcepack.library.utilities.Util;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 26.1+ (pack_format 84.0 / RenderPearl): strip pack-provided core/post shaders.
 *
 * <h2>Why we delete instead of converting</h2>
 *
 * <p>Minecraft 26.x compiles resource-pack GLSL through RenderPearl to SPIR-V for
 * Vulkan. Pre-26.1 core shaders (typical {@code #version 150}, bare {@code in}/
 * {@code out}, non-block {@code uniform mat4}, {@code gl_VertexID}, etc.) fail at
 * reload with errors such as:
 * <ul>
 *   <li>{@code SPIR-V requires location for user input/output}</li>
 *   <li>{@code non-opaque uniforms outside a block} (Vulkan GLSL)</li>
 *   <li>{@code gl_VertexID} undeclared (use {@code gl_VertexIndex})</li>
 * </ul>
 *
 * <p>Mechanical OpenGL→Vulkan porting of <em>custom</em> core overrides (e.g. sun /
 * cubemap hacks in {@code position_tex_color}) is not reliable: effects are
 * hand-authored and not a rename map. Leaving them in the pack overrides vanilla
 * pipelines (GUI, crosshair, logo, …) and makes the whole resource pack unloadable.
 *
 * <p>Therefore, when converting <strong>from ≤1.21.11 into ≥26.1</strong>, we remove
 * pack overrides under {@code shaders/core}, {@code shaders/post}, and
 * {@code shaders/include} so the client falls back to vanilla 26.x shaders. Custom
 * shader behavior is intentionally dropped; a {@code conversion_review.json} finding
 * records that. Hand-porting specific effects remains out of scope for this converter.
 *
 * <p>Packs that already target 26.1+ (from ≥26.1) are left alone so valid Vulkan-era
 * shaders are preserved on later hops.
 */
public class IncompatibleCoreShaderStripper26_1 extends Converter {
	private static final String[] SHADER_SUBDIRS = {"core", "post", "include"};

	public IncompatibleCoreShaderStripper26_1(PackConverter packConverter) {
		super(packConverter);
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		// Only when crossing the RenderPearl / 26.1 cliff from a pre-26.1 pack.
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

		List<String> removed = new ArrayList<>();
		for (String sub : SHADER_SUBDIRS) {
			Path dir = shaders.resolve(sub);
			if (!Files.isDirectory(dir)) {
				continue;
			}
			if (!hasAnyFiles(dir)) {
				continue;
			}
			// Delete the whole subtree: .vsh/.fsh/.json and any includes the pack shipped.
			FileUtil.deleteDirectoryAndContents(dir);
			removed.add("shaders/" + sub + "/");
			Logger.log("Removed pack shaders/" + sub
				+ "/ — pre-26.1 GLSL is incompatible with RenderPearl/Vulkan "
				+ "(cannot reliably auto-port custom core shaders); vanilla shaders used instead");
		}

		// Drop empty shaders/ parent if nothing left.
		if (Files.isDirectory(shaders) && isEmptyDir(shaders)) {
			Files.deleteIfExists(shaders);
		}

		if (removed.isEmpty()) {
			return;
		}

		List<JsonObject> findings = new ArrayList<>();
		JsonObject finding = new JsonObject();
		finding.addProperty("level", "warn");
		finding.addProperty("id", "core_shaders_stripped_vulkan_26_1");
		finding.addProperty("msg",
			"Removed pack-provided shaders (" + String.join(", ", removed)
				+ ") when converting to ≥26.1: OpenGL-era core/post GLSL cannot be "
				+ "reliably rewritten for RenderPearl/SPIR-V/Vulkan. Custom shader "
				+ "effects (e.g. sun overrides) are dropped; client uses vanilla shaders. "
				+ "Re-author effects for 26.x GLSL manually if needed.");
		JsonArray examples = new JsonArray();
		for (String r : removed) {
			examples.add(r);
		}
		finding.add("examples", examples);
		findings.add(finding);
		BedsSignsConverter26_2.appendReviewFindings(pack.getWorkingPath(), packConverter.getGson(), findings);
	}

	private static boolean hasAnyFiles(Path dir) throws IOException {
		try (Stream<Path> stream = Files.walk(dir)) {
			return stream.anyMatch(Files::isRegularFile);
		}
	}

	private static boolean isEmptyDir(Path dir) throws IOException {
		try (Stream<Path> stream = Files.list(dir)) {
			Iterator<Path> it = stream.iterator();
			return !it.hasNext();
		}
	}
}

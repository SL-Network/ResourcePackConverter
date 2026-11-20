package com.agentdid127.resourcepack.forwards.impl.v26_2.other;

import com.agentdid127.resourcepack.library.Converter;
import com.agentdid127.resourcepack.library.PackConverter;
import com.agentdid127.resourcepack.library.pack.Pack;
import com.agentdid127.resourcepack.library.utilities.Logger;
import com.agentdid127.resourcepack.library.utilities.Util;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 26.2 (pack_format 88.0): text / moving-block shader consolidation.
 *
 * <p>Known mechanical renames (wiki 88.0 / jar removed_sample):
 * <ul>
 *   <li>{@code rendertype_translucent_moving_block} → {@code block}</li>
 *   <li>{@code rendertype_text} → {@code text}</li>
 *   <li>{@code rendertype_text_background} → {@code text_background}</li>
 * </ul>
 *
 * <p>See-through / intensity variants merge via {@code IS_SEE_THROUGH} /
 * {@code IS_GRAYSCALE} / {@code IS_GUI} defines — content port is N/A. When a
 * base {@code text}/{@code text_background} is produced from the primary name,
 * leftover variant files are deleted if the destination already exists; otherwise
 * a review finding is emitted. Packs without shaders are no-ops.
 */
public class ShaderTextConsolidationConverter26_2 extends Converter {
	/** Primary stem → destination stem (1:1 file rename). */
	private static final Map<String, String> PRIMARY_RENAMES = new LinkedHashMap<>();

	/** Variant stems that fold into a consolidated destination (define-based). */
	private static final Map<String, String> VARIANT_DEST = new LinkedHashMap<>();

	static {
		PRIMARY_RENAMES.put("rendertype_translucent_moving_block", "block");
		PRIMARY_RENAMES.put("rendertype_text", "text");
		PRIMARY_RENAMES.put("rendertype_text_background", "text_background");

		VARIANT_DEST.put("rendertype_text_see_through", "text");
		VARIANT_DEST.put("rendertype_text_intensity", "text");
		VARIANT_DEST.put("rendertype_text_intensity_see_through", "text");
		VARIANT_DEST.put("rendertype_text_background_see_through", "text_background");
	}

	public ShaderTextConsolidationConverter26_2(PackConverter packConverter) {
		super(packConverter);
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		return from <= Util.getVersionProtocol(gson, "26.1")
			&& to >= Util.getVersionProtocol(gson, "26.2");
	}

	@Override
	public void convert(Pack pack) throws IOException {
		Path core = pack.getWorkingPath()
			.resolve("assets/minecraft/shaders/core".replace("/", File.separator));
		if (!Files.isDirectory(core)) {
			return;
		}

		List<JsonObject> findings = new ArrayList<>();

		for (Map.Entry<String, String> e : PRIMARY_RENAMES.entrySet()) {
			renamePair(core, e.getKey(), e.getValue(), findings, false);
		}

		for (Map.Entry<String, String> e : VARIANT_DEST.entrySet()) {
			handleVariant(core, e.getKey(), e.getValue(), findings);
		}

		BedsSignsConverter26_2.appendReviewFindings(pack.getWorkingPath(), packConverter.getGson(), findings);
	}

	private static void renamePair(Path core, String fromStem, String toStem, List<JsonObject> findings,
		boolean reviewOnlyIfConflict) throws IOException {
		for (String ext : new String[]{".vsh", ".fsh"}) {
			Path from = core.resolve(fromStem + ext);
			if (!Files.exists(from)) {
				continue;
			}
			Path to = core.resolve(toStem + ext);
			if (Files.exists(to)) {
				if (reviewOnlyIfConflict) {
					JsonObject f = new JsonObject();
					f.addProperty("level", "review");
					f.addProperty("id", "shader_text_consolidation_conflict_26_2");
					f.addProperty("msg", fromStem + ext + " conflicts with existing " + toStem + ext
						+ "; define-based merge is N/A — review manually");
					f.addProperty("path", "assets/minecraft/shaders/core/" + fromStem + ext);
					findings.add(f);
				} else {
					Files.delete(from);
					Logger.log("Removed obsolete " + fromStem + ext + " (destination " + toStem + ext + " exists)");
					JsonObject f = new JsonObject();
					f.addProperty("level", "info");
					f.addProperty("id", "shader_text_consolidation_removed_duplicate_26_2");
					f.addProperty("msg", "Removed " + fromStem + ext + "; " + toStem + ext + " already present");
					findings.add(f);
				}
				continue;
			}
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
			Logger.log("Renamed shader " + fromStem + ext + " → " + toStem + ext);
			JsonObject f = new JsonObject();
			f.addProperty("level", "info");
			f.addProperty("id", "shader_text_consolidation_renamed_26_2");
			f.addProperty("msg", fromStem + ext + " → " + toStem + ext);
			findings.add(f);
		}
	}

	private static void handleVariant(Path core, String variantStem, String destStem, List<JsonObject> findings)
		throws IOException {
		for (String ext : new String[]{".vsh", ".fsh"}) {
			Path variant = core.resolve(variantStem + ext);
			if (!Files.exists(variant)) {
				continue;
			}
			Path dest = core.resolve(destStem + ext);
			if (Files.exists(dest)) {
				Files.delete(variant);
				Logger.log("Removed variant " + variantStem + ext + " (folded into " + destStem + ")");
				JsonObject f = new JsonObject();
				f.addProperty("level", "info");
				f.addProperty("id", "shader_text_variant_removed_26_2");
				f.addProperty("msg", "Removed " + variantStem + ext + "; consolidated destination "
					+ destStem + ext + " present (IS_* defines N/A for content)");
				findings.add(f);
			} else {
				// No primary — promote this variant as the consolidated shader; defines still N/A.
				Files.move(variant, dest, StandardCopyOption.REPLACE_EXISTING);
				Logger.log("Promoted variant " + variantStem + ext + " → " + destStem + ext);
				JsonObject f = new JsonObject();
				f.addProperty("level", "review");
				f.addProperty("id", "shader_text_variant_promoted_26_2");
				f.addProperty("msg", "Promoted " + variantStem + ext + " → " + destStem + ext
					+ "; IS_SEE_THROUGH/IS_GRAYSCALE/IS_GUI define port is N/A — review");
				f.addProperty("path", "assets/minecraft/shaders/core/" + destStem + ext);
				findings.add(f);
			}
		}
	}
}

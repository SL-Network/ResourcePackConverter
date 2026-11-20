package com.agentdid127.resourcepack.forwards.impl.v1_21.textures;

import com.agentdid127.resourcepack.library.Converter;
import com.agentdid127.resourcepack.library.PackConverter;
import com.agentdid127.resourcepack.library.pack.Pack;
import com.agentdid127.resourcepack.library.utilities.Logger;
import com.agentdid127.resourcepack.library.utilities.Util;
import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 1.21.5: {@code textures/misc/enchanted_glint_entity.png} was renamed to
 * {@code enchanted_glint_armor.png} (vanilla jar diff 1.21.4→1.21.5).
 *
 * <p>{@code enchanted_glint_item.png} already existed from 1.19.4 and is left alone.
 * If only the entity glint is present, it is moved to the armor name.
 */
public class EnchantGlintConverter1_21_5 extends Converter {
	public EnchantGlintConverter1_21_5(PackConverter packConverter) {
		super(packConverter);
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		return from <= Util.getVersionProtocol(gson, "1.21.4")
			&& to >= Util.getVersionProtocol(gson, "1.21.5");
	}

	@Override
	public void convert(Pack pack) throws IOException {
		Path misc = pack.getWorkingPath().resolve("assets/minecraft/textures/misc".replace("/", File.separator));
		if (!Files.isDirectory(misc)) {
			return;
		}

		renameIfPresent(misc, "enchanted_glint_entity.png", "enchanted_glint_armor.png");
		renameIfPresent(misc, "enchanted_glint_entity.png.mcmeta", "enchanted_glint_armor.png.mcmeta");
	}

	private static void renameIfPresent(Path dir, String fromName, String toName) throws IOException {
		Path from = dir.resolve(fromName);
		if (!Files.exists(from)) {
			return;
		}
		Path to = dir.resolve(toName);
		Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		Logger.debug("Renamed " + fromName + " → " + toName);
	}
}

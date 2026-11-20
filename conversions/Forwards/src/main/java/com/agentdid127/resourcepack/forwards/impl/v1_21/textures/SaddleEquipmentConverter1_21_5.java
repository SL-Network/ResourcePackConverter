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
 * 1.21.5 (25w03a): saddles became equipment. Entity overlay textures moved:
 * <ul>
 *   <li>{@code textures/entity/pig/pig_saddle.png}
 *       → {@code textures/entity/equipment/pig_saddle/saddle.png}</li>
 *   <li>{@code textures/entity/strider/strider_saddle.png}
 *       → {@code textures/entity/equipment/strider_saddle/saddle.png}</li>
 * </ul>
 *
 * <p>Adventure packs often ship fully transparent overlays to hide saddles. Leaving
 * them at the pre-1.21.5 paths makes the client fall back to vanilla equipment
 * textures, so saddles become visible again.
 *
 * <p>Horse/camel/etc. did not use these standalone entity overlay paths before
 * 1.21.5; only pig and strider are migrated here. Runs after
 * {@link ArmorMoverConverter1_21_2} so the equipment tree already exists.
 */
public class SaddleEquipmentConverter1_21_5 extends Converter {
	private static final String[][] MOVES = {
		{"entity/pig/pig_saddle.png", "entity/equipment/pig_saddle/saddle.png"},
		{"entity/strider/strider_saddle.png", "entity/equipment/strider_saddle/saddle.png"},
	};

	public SaddleEquipmentConverter1_21_5(PackConverter packConverter) {
		super(packConverter);
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		return from <= Util.getVersionProtocol(gson, "1.21.4")
			&& to >= Util.getVersionProtocol(gson, "1.21.5");
	}

	@Override
	public void convert(Pack pack) throws IOException {
		Path textures = pack.getWorkingPath().resolve("assets/minecraft/textures".replace("/", File.separator));
		if (!Files.isDirectory(textures)) {
			return;
		}

		for (String[] move : MOVES) {
			Path from = textures.resolve(move[0].replace("/", File.separator));
			Path to = textures.resolve(move[1].replace("/", File.separator));
			moveTexture(from, to);
		}
	}

	private static void moveTexture(Path from, Path to) throws IOException {
		if (!Files.exists(from)) {
			return;
		}
		Files.createDirectories(to.getParent());
		Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		Logger.debug("Moved saddle overlay " + from.getFileName() + " → " + to);

		Path fromMeta = from.resolveSibling(from.getFileName().toString() + ".mcmeta");
		Path toMeta = to.resolveSibling(to.getFileName().toString() + ".mcmeta");
		if (Files.exists(fromMeta)) {
			Files.move(fromMeta, toMeta, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}

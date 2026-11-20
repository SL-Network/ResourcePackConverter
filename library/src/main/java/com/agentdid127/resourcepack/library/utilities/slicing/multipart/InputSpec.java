package com.agentdid127.resourcepack.library.utilities.slicing.multipart;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One entity texture input and its Mojang-defined outputs.
 */
public final class InputSpec {
	private final String relativePath;
	private final List<OutputSpec> outputs = new ArrayList<>();

	public InputSpec(String relativePath) {
		this.relativePath = relativePath.replace("\\", "/");
	}

	public InputSpec outputs(OutputSpec... files) {
		Collections.addAll(outputs, files);
		return this;
	}

	public String relativePath() {
		return relativePath;
	}

	/**
	 * @return true if the input existed and was processed
	 */
	public boolean process(Path assetsRoot, boolean deleteInput) throws IOException {
		Path inputPath = assetsRoot.resolve(relativePath.replace("/", java.io.File.separator));
		if (!Files.exists(inputPath)) {
			return false;
		}
		try (InputStream is = Files.newInputStream(inputPath)) {
			BufferedImage image = ImageIO.read(is);
			if (image == null) {
				return false;
			}
			for (OutputSpec output : outputs) {
				output.write(assetsRoot, inputPath, image);
			}
		}
		if (deleteInput) {
			Files.deleteIfExists(inputPath);
			Files.deleteIfExists(inputPath.resolveSibling(inputPath.getFileName().toString() + ".mcmeta"));
		}
		return true;
	}
}

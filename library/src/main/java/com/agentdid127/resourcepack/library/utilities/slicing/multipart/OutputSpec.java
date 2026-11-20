package com.agentdid127.resourcepack.library.utilities.slicing.multipart;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Output texture produced from an entity atlas (simple crop or multipart assembly).
 * Ported from Mojang Slicer SimpleOutputFile / MultipartOutputFile (MIT).
 */
public final class OutputSpec {
	private final String relativePath;
	private final Integer multipartWidth;
	private final Integer multipartHeight;
	private final ScaledBox simpleBox;
	private final List<TransformedRegion> regions;
	private final List<UnaryOperator<BufferedImage>> transformers = new ArrayList<>();

	private OutputSpec(String relativePath, ScaledBox simpleBox, Integer multipartWidth, Integer multipartHeight,
		List<TransformedRegion> regions) {
		this.relativePath = relativePath.replace("\\", "/");
		this.simpleBox = simpleBox;
		this.multipartWidth = multipartWidth;
		this.multipartHeight = multipartHeight;
		this.regions = regions;
	}

	public static OutputSpec simple(String relativePath, ScaledBox box) {
		return new OutputSpec(relativePath, box, null, null, List.of());
	}

	public static OutputSpec multipart(String relativePath, int width, int height, TransformedRegion... regions) {
		return new OutputSpec(relativePath, null, width, height, List.of(regions));
	}

	public OutputSpec apply(UnaryOperator<BufferedImage> transform) {
		transformers.add(transform);
		return this;
	}

	public String relativePath() {
		return relativePath;
	}

	public void write(Path assetsRoot, Path inputImagePath, BufferedImage input) throws IOException {
		Path outputPath = assetsRoot.resolve(relativePath.replace("/", java.io.File.separator));
		Files.createDirectories(outputPath.getParent());

		BufferedImage out;
		if (simpleBox != null) {
			int width = input.getWidth();
			int height = input.getHeight();
			int x = simpleBox.scaleX(width);
			int y = simpleBox.scaleY(height);
			int w = simpleBox.scaleW(width);
			int h = simpleBox.scaleH(height);
			if (x == 0 && y == 0 && w == width && h == height && transformers.isEmpty()) {
				Files.copy(inputImagePath, outputPath, StandardCopyOption.REPLACE_EXISTING);
				copyMcmeta(inputImagePath, outputPath);
				return;
			}
			out = input.getSubimage(x, y, w, h);
		} else {
			TransformedRegion first = regions.get(0);
			int outW = multipartWidth * input.getWidth() / first.box().totalW();
			int outH = multipartHeight * input.getHeight() / first.box().totalH();
			out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
			for (TransformedRegion region : regions) {
				region.blit(input, out);
			}
		}

		for (UnaryOperator<BufferedImage> op : transformers) {
			out = op.apply(out);
		}
		ImageIO.write(out, "png", outputPath.toFile());
		copyMcmeta(inputImagePath, outputPath);
	}

	private static void copyMcmeta(Path inputImagePath, Path outputPath) throws IOException {
		Path meta = inputImagePath.resolveSibling(inputImagePath.getFileName().toString() + ".mcmeta");
		if (Files.exists(meta)) {
			Files.copy(meta, outputPath.resolveSibling(outputPath.getFileName().toString() + ".mcmeta"),
				StandardCopyOption.REPLACE_EXISTING);
		}
	}
}

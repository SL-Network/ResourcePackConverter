package com.agentdid127.resourcepack.library.utilities.slicing.multipart;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * One cropped/transformed region pasted into a multipart output at an atlas offset.
 * Ported from Mojang Slicer {@code TransformedBox} (MIT).
 */
public final class TransformedRegion {
	private final ScaledBox box;
	private final List<UnaryOperator<BufferedImage>> transformers;
	private final int offsetX;
	private final int offsetY;

	public TransformedRegion(ScaledBox box, int offsetX, int offsetY, UnaryOperator<BufferedImage>... transformers) {
		this.box = box;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.transformers = transformers == null || transformers.length == 0
			? List.of()
			: Arrays.asList(transformers);
	}

	public ScaledBox box() {
		return box;
	}

	public void blit(BufferedImage input, BufferedImage output) {
		int width = input.getWidth();
		int height = input.getHeight();
		int x = box.scaleX(width);
		int y = box.scaleY(height);
		int w = box.scaleW(width);
		int h = box.scaleH(height);

		BufferedImage sub = input.getSubimage(x, y, w, h);
		for (UnaryOperator<BufferedImage> op : transformers) {
			sub = op.apply(sub);
		}

		Graphics2D g = output.createGraphics();
		int ox = offsetX * input.getWidth() / box.totalW();
		int oy = offsetY * input.getHeight() / box.totalH();
		g.drawImage(sub, ox, oy, null);
		g.dispose();
	}
}

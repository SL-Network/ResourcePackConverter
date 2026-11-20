package com.agentdid127.resourcepack.library.utilities.slicing.multipart;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Image operators used by Mojang Slicer multipart outputs (MIT).
 */
public final class ImageTransforms {
	private ImageTransforms() {
	}

	public static BufferedImage mirrorVertically(BufferedImage image) {
		BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(image, 0, image.getHeight(), image.getWidth(), -image.getHeight(), null);
		g.dispose();
		return out;
	}

	public static BufferedImage rotate90(BufferedImage image) {
		BufferedImage out = new BufferedImage(image.getHeight(), image.getWidth(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		AffineTransform transform = new AffineTransform();
		transform.quadrantRotate(1, 0, 0);
		transform.translate(0, -image.getHeight());
		g.setTransform(transform);
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return out;
	}

	public static BufferedImage rotate180(BufferedImage image) {
		BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		AffineTransform transform = new AffineTransform();
		transform.quadrantRotate(2, image.getWidth() / 2.0, image.getHeight() / 2.0);
		g.setTransform(transform);
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return out;
	}

	public static BufferedImage rotate270(BufferedImage image) {
		BufferedImage out = new BufferedImage(image.getHeight(), image.getWidth(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		AffineTransform transform = new AffineTransform();
		transform.quadrantRotate(3, 0, 0);
		transform.translate(-image.getWidth(), 0);
		g.setTransform(transform);
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return out;
	}
}

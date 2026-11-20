package com.agentdid127.resourcepack.library.utilities.slicing.multipart;

/**
 * UV box in atlas units, scaled to the real image size.
 * Ported from Mojang Slicer {@code com.mojang.slicer.library.Box} (MIT).
 */
public final class ScaledBox {
	private final int x;
	private final int y;
	private final int w;
	private final int h;
	private final int totalW;
	private final int totalH;

	public ScaledBox(int x, int y, int w, int h, int totalW, int totalH) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		this.totalW = totalW;
		this.totalH = totalH;
	}

	public int scaleX(int imgWidth) {
		return x * imgWidth / totalW;
	}

	public int scaleY(int imgHeight) {
		return y * imgHeight / totalH;
	}

	public int scaleW(int imgWidth) {
		return w * imgWidth / totalW;
	}

	public int scaleH(int imgHeight) {
		return h * imgHeight / totalH;
	}

	public int totalW() {
		return totalW;
	}

	public int totalH() {
		return totalH;
	}
}

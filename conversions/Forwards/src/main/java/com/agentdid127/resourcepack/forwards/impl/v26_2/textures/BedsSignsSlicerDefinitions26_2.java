package com.agentdid127.resourcepack.forwards.impl.v26_2.textures;

import com.agentdid127.resourcepack.library.utilities.slicing.multipart.ImageTransforms;
import com.agentdid127.resourcepack.library.utilities.slicing.multipart.InputSpec;
import com.agentdid127.resourcepack.library.utilities.slicing.multipart.OutputSpec;
import com.agentdid127.resourcepack.library.utilities.slicing.multipart.ScaledBox;
import com.agentdid127.resourcepack.library.utilities.slicing.multipart.TransformedRegion;

import java.util.ArrayList;
import java.util.List;

/**
 * Official Mojang Slicer v1.2.1 (26.2) bed / standing-sign / hanging-sign UV definitions.
 *
 * <p>Source: {@code https://github.com/Mojang/slicer} tag {@code v1.2.1}
 * {@code 26.2/.../Main.java} (MIT). Do not invent alternate UVs.
 */
public final class BedsSignsSlicerDefinitions26_2 {
	private static final String[] BED_COLORS = {
		"black", "blue", "brown", "cyan", "gray", "green", "light_blue", "light_gray",
		"lime", "magenta", "orange", "pink", "purple", "red", "white", "yellow",
	};

	private static final String[] WOOD_TYPES = {
		"acacia", "birch", "crimson", "mangrove", "pale_oak", "warped",
		"bamboo", "cherry", "dark_oak", "jungle", "oak", "spruce",
	};

	private BedsSignsSlicerDefinitions26_2() {
	}

	public static List<InputSpec> all() {
		List<InputSpec> inputs = new ArrayList<>();
		inputs.add(makeStaticBedPartProcessor());
		for (String color : BED_COLORS) {
			inputs.add(makeBedProcessor(color));
		}
		for (String wood : WOOD_TYPES) {
			inputs.add(makeStandingSignProcessor(wood));
			inputs.add(makeHangingSignProcessor(wood));
		}
		return List.copyOf(inputs);
	}

	private static InputSpec input(String path, OutputSpec... outputs) {
		return new InputSpec(path).outputs(outputs);
	}

	private static InputSpec makeStaticBedPartProcessor() {
		return input("minecraft/textures/entity/bed/black.png",
			OutputSpec.simple("minecraft/textures/block/bed_down.png",
				new ScaledBox(28, 6, 16, 16, 64, 64)).apply(ImageTransforms::rotate180),
			OutputSpec.multipart("minecraft/textures/block/bed_head_north.png", 16, 16,
				new TransformedRegion(new ScaledBox(6, 0, 16, 6, 64, 64), 0, 7, ImageTransforms::rotate180),
				new TransformedRegion(new ScaledBox(53, 21, 3, 3, 64, 64), 0, 13),
				new TransformedRegion(new ScaledBox(56, 21, 3, 3, 64, 64), 3, 13),
				new TransformedRegion(new ScaledBox(59, 9, 3, 3, 64, 64), 10, 13),
				new TransformedRegion(new ScaledBox(50, 9, 3, 3, 64, 64), 13, 13)
			)
		);
	}

	private static InputSpec makeBedProcessor(String color) {
		return input("minecraft/textures/entity/bed/" + color + ".png",
			OutputSpec.simple("minecraft/textures/block/" + color + "_bed_head_up.png",
				new ScaledBox(6, 6, 16, 16, 64, 64)),
			OutputSpec.simple("minecraft/textures/block/" + color + "_bed_foot_up.png",
				new ScaledBox(6, 28, 16, 16, 64, 64)),
			OutputSpec.multipart("minecraft/textures/block/" + color + "_bed_foot_south.png", 16, 16,
				new TransformedRegion(new ScaledBox(22, 22, 16, 6, 64, 64), 0, 7, ImageTransforms::mirrorVertically),
				new TransformedRegion(new ScaledBox(53, 3, 3, 3, 64, 64), 0, 13),
				new TransformedRegion(new ScaledBox(56, 3, 3, 3, 64, 64), 3, 13),
				new TransformedRegion(new ScaledBox(59, 15, 3, 3, 64, 64), 10, 13),
				new TransformedRegion(new ScaledBox(50, 15, 3, 3, 64, 64), 13, 13)
			),
			OutputSpec.multipart("minecraft/textures/block/" + color + "_bed_foot_west.png", 16, 16,
				new TransformedRegion(new ScaledBox(0, 28, 6, 16, 64, 64), 0, 7, ImageTransforms::rotate270),
				new TransformedRegion(new ScaledBox(56, 0, 3, 3, 64, 64), 7, 13, ImageTransforms::mirrorVertically),
				new TransformedRegion(new ScaledBox(59, 3, 3, 3, 64, 64), 10, 13),
				new TransformedRegion(new ScaledBox(50, 3, 3, 3, 64, 64), 13, 13)
			),
			OutputSpec.multipart("minecraft/textures/block/" + color + "_bed_foot_east.png", 16, 16,
				new TransformedRegion(new ScaledBox(22, 28, 6, 16, 64, 64), 0, 7, ImageTransforms::rotate90),
				new TransformedRegion(new ScaledBox(53, 15, 3, 3, 64, 64), 0, 13),
				new TransformedRegion(new ScaledBox(56, 15, 3, 3, 64, 64), 3, 13),
				new TransformedRegion(new ScaledBox(56, 12, 3, 3, 64, 64), 6, 13,
					ImageTransforms::rotate270, ImageTransforms::mirrorVertically)
			),
			OutputSpec.multipart("minecraft/textures/block/" + color + "_bed_head_east.png", 16, 16,
				new TransformedRegion(new ScaledBox(22, 6, 6, 16, 64, 64), 0, 7, ImageTransforms::rotate90),
				new TransformedRegion(new ScaledBox(56, 18, 3, 3, 64, 64), 7, 13,
					ImageTransforms::rotate180, ImageTransforms::mirrorVertically),
				new TransformedRegion(new ScaledBox(59, 21, 3, 3, 64, 64), 10, 13),
				new TransformedRegion(new ScaledBox(50, 21, 3, 3, 64, 64), 13, 13)
			),
			OutputSpec.multipart("minecraft/textures/block/" + color + "_bed_head_west.png", 16, 16,
				new TransformedRegion(new ScaledBox(0, 6, 6, 16, 64, 64), 0, 7, ImageTransforms::rotate270),
				new TransformedRegion(new ScaledBox(53, 9, 3, 3, 64, 64), 0, 13),
				new TransformedRegion(new ScaledBox(56, 9, 3, 3, 64, 64), 3, 13),
				new TransformedRegion(new ScaledBox(56, 6, 3, 3, 64, 64), 6, 13,
					ImageTransforms::rotate90, ImageTransforms::mirrorVertically)
			)
		);
	}

	private static InputSpec makeStandingSignProcessor(String woodType) {
		return input("minecraft/textures/entity/signs/" + woodType + ".png",
			OutputSpec.multipart("minecraft/textures/gui/signs/" + woodType + ".png", 24, 26,
				new TransformedRegion(new ScaledBox(2, 2, 24, 12, 64, 32), 0, 0),
				new TransformedRegion(new ScaledBox(2, 16, 2, 14, 64, 32), 11, 12)
			),
			OutputSpec.multipart("minecraft/textures/block/" + woodType + "_sign.png", 32, 32,
				new TransformedRegion(new ScaledBox(2, 0, 24, 14, 64, 32), 0, 0),
				new TransformedRegion(new ScaledBox(26, 2, 2, 12, 64, 32), 24, 2),
				new TransformedRegion(new ScaledBox(28, 2, 24, 12, 64, 32), 0, 16),
				new TransformedRegion(new ScaledBox(0, 2, 2, 12, 64, 32), 24, 16),
				new TransformedRegion(new ScaledBox(26, 0, 24, 2, 64, 32), 0, 28, ImageTransforms::mirrorVertically),
				new TransformedRegion(new ScaledBox(2, 16, 4, 14, 64, 32), 28, 0),
				new TransformedRegion(new ScaledBox(6, 16, 2, 14, 64, 32), 28, 16),
				new TransformedRegion(new ScaledBox(0, 16, 2, 14, 64, 32), 30, 16),
				new TransformedRegion(new ScaledBox(4, 14, 2, 2, 64, 32), 28, 30, ImageTransforms::mirrorVertically)
			)
		);
	}

	private static InputSpec makeHangingSignProcessor(String woodType) {
		return input("minecraft/textures/entity/signs/hanging/" + woodType + ".png",
			OutputSpec.multipart("minecraft/textures/block/" + woodType + "_hanging_sign.png", 32, 32,
				new TransformedRegion(new ScaledBox(0, 14, 32, 10, 64, 32), 0, 16),
				new TransformedRegion(new ScaledBox(2, 12, 14, 2, 64, 32), 2, 14),
				new TransformedRegion(new ScaledBox(16, 12, 14, 2, 64, 32), 2, 26, ImageTransforms::mirrorVertically),
				new TransformedRegion(new ScaledBox(4, 0, 16, 6, 64, 32), 0, 0),
				new TransformedRegion(new ScaledBox(20, 0, 16, 4, 64, 32), 0, 9, ImageTransforms::rotate180),
				new TransformedRegion(new ScaledBox(20, 4, 4, 2, 64, 32), 16, 4),
				new TransformedRegion(new ScaledBox(24, 4, 16, 2, 64, 32), 0, 7),
				new TransformedRegion(new ScaledBox(0, 4, 4, 2, 64, 32), 16, 7),
				new TransformedRegion(new ScaledBox(14, 6, 12, 6, 64, 32), 20, 0),
				new TransformedRegion(new ScaledBox(0, 6, 9, 6, 64, 32), 22, 7)
			)
		);
	}
}

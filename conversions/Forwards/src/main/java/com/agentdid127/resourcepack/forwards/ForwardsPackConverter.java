package com.agentdid127.resourcepack.forwards;

import com.agentdid127.resourcepack.forwards.impl.other.*;
import com.agentdid127.resourcepack.forwards.impl.other.textures.InventoryConverter;
import com.agentdid127.resourcepack.forwards.impl.v1_14.textures.ParticlesSlicerConverter1_14;
import com.agentdid127.resourcepack.forwards.impl.v1_11.other.RemoveSpacesConverter1_11;
import com.agentdid127.resourcepack.forwards.impl.v1_13.other.AnimationConverter1_13;
import com.agentdid127.resourcepack.forwards.impl.v1_13.other.LangConverter1_13;
import com.agentdid127.resourcepack.forwards.impl.v1_13.other.MCPatcherConverter1_13;
import com.agentdid127.resourcepack.forwards.impl.v1_13.other.SoundsConverter1_13;
import com.agentdid127.resourcepack.forwards.impl.v1_13.textures.MapIconConverter1_13;
import com.agentdid127.resourcepack.forwards.impl.v1_13.textures.WaterConverter1_13;
import com.agentdid127.resourcepack.forwards.impl.v1_14.textures.MobEffectAtlasSlicerConverter1_14;
import com.agentdid127.resourcepack.forwards.impl.v1_14.textures.PaintingSlicerConverter1_14_4;
import com.agentdid127.resourcepack.forwards.impl.v1_15.textures.ChestConverter1_15;
import com.agentdid127.resourcepack.forwards.impl.v1_15.textures.EnchantConverter1_15;
import com.agentdid127.resourcepack.forwards.impl.v1_18.textures.ParticleConverter1_18;
import com.agentdid127.resourcepack.forwards.impl.v1_19.other.AtlasConverter1_19_3;
import com.agentdid127.resourcepack.forwards.impl.v1_19.textures.EnchantPathConverter1_19_4;
import com.agentdid127.resourcepack.forwards.impl.v1_19.textures.CreativeTabsConverter1_19_3;
import com.agentdid127.resourcepack.forwards.impl.v1_19.textures.WidgetSlidersCreator1_19_4;
import com.agentdid127.resourcepack.forwards.impl.v1_20.other.ImageFormatConverter1_20_3;
import com.agentdid127.resourcepack.forwards.impl.v1_20.textures.GuiSlicerConverter1_20_2;
import com.agentdid127.resourcepack.forwards.impl.v1_20.textures.MapIconSlicerConverter1_20_5;
import com.agentdid127.resourcepack.forwards.impl.v1_20.textures.TitleConverter1_20;
import com.agentdid127.resourcepack.forwards.impl.v1_21.other.ArmorOverlayTintIndexConverter1_21_4;
import com.agentdid127.resourcepack.forwards.impl.v1_21.other.CustomEquipmentJsonConverter1_21_2;
import com.agentdid127.resourcepack.forwards.impl.v1_21.other.EmptyArmorSlotTextureConverter1_21_4;
import com.agentdid127.resourcepack.forwards.impl.v1_21.other.LeatherArmorItemsTintConverter1_21_4;
import com.agentdid127.resourcepack.forwards.impl.v1_21.other.ShaderProgramJsonRemover1_21_5;
import com.agentdid127.resourcepack.forwards.impl.v1_21.textures.ArmorMoverConverter1_21_2;
import com.agentdid127.resourcepack.forwards.impl.v1_21.textures.EnchantGlintConverter1_21_5;
import com.agentdid127.resourcepack.forwards.impl.v1_21.textures.PostEffectShadersConverter1_21_2;
import com.agentdid127.resourcepack.forwards.impl.v1_21.textures.SaddleEquipmentConverter1_21_5;
import com.agentdid127.resourcepack.forwards.impl.v1_21_11.other.ItemsAtlasConverter1_21_11;
import com.agentdid127.resourcepack.forwards.impl.v1_21_11.other.ParticleTextureFallbackConverter;
import com.agentdid127.resourcepack.forwards.impl.v1_21_11.textures.CelestialSlicerUpdater1_21_11;
import com.agentdid127.resourcepack.forwards.impl.v26_1.other.IncompatibleCoreShaderStripper26_1;
import com.agentdid127.resourcepack.forwards.impl.v26_1.other.ShaderLightmapUniformConverter26_1;
import com.agentdid127.resourcepack.forwards.impl.v26_1.textures.EntityTextureLayoutConverter26_1;
import com.agentdid127.resourcepack.forwards.impl.v26_2.other.BedsSignsConverter26_2;
import com.agentdid127.resourcepack.forwards.impl.v26_2.other.ShaderTextConsolidationConverter26_2;
import com.agentdid127.resourcepack.forwards.impl.v26_2.textures.PillarSideConverter26_2;
import com.agentdid127.resourcepack.forwards.impl.v26_3.other.TrimEquipmentOverrideConverter26_3;
import com.agentdid127.resourcepack.forwards.impl.v26_3.textures.CustomPalettedPermutationsConverter26_3;
import com.agentdid127.resourcepack.forwards.impl.v26_3.textures.TrimPaletteConverter26_3;
import com.agentdid127.resourcepack.forwards.impl.v1_9.textures.CompassConverter1_9;
import com.agentdid127.resourcepack.forwards.impl.v1_9.textures.OffHandCreator1_9;
import com.agentdid127.resourcepack.library.Converter;
import com.agentdid127.resourcepack.library.PackConverter;
import com.agentdid127.resourcepack.library.pack.Pack;
import com.agentdid127.resourcepack.library.utilities.Logger;
import com.agentdid127.resourcepack.library.utilities.Util;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

public class ForwardsPackConverter extends PackConverter {
	private final int from;
	private final int to;
	Path INPUT_DIR;

	public ForwardsPackConverter(Gson gson, int from, int to, String light, Path input, boolean debug, PrintStream out) {
		this.gson = gson;
		Logger.setDebug(debug);
		Logger.setStream(out);
		Logger.log("Converting packs from: " + from + " to " + to);
		this.from = from;
		this.to = to;
		this.INPUT_DIR = input;
		this.setupConverters(light);
	}

	private void setupConverters(String light) {
		this.registerConverter(new NameConverter(this, from, to)); // This needs to be run first, other converters might reference new directory names.
		this.registerConverter(new PackMetaConverter(this, from, to));
		this.registerConverter(new CompassConverter1_9(this, to));
		this.registerConverter(new OffHandCreator1_9(this));
		this.registerConverter(new RemoveSpacesConverter1_11(this));
		this.registerConverter(new ModelConverter(this, light, from, to));
		this.registerConverter(new SoundsConverter1_13(this));
		this.registerConverter(new AnimationConverter1_13(this));
		this.registerConverter(new MapIconConverter1_13(this));
		this.registerConverter(new MCPatcherConverter1_13(this));
		this.registerConverter(new WaterConverter1_13(this));
		this.registerConverter(new BlockStateConverter(this, from, to));
		this.registerConverter(new LangConverter1_13(this, from, to));
		this.registerConverter(new ParticlesSlicerConverter1_14(this, from, to));
		this.registerConverter(new PaintingSlicerConverter1_14_4(this));
		this.registerConverter(new MobEffectAtlasSlicerConverter1_14(this, from));
		this.registerConverter(new ChestConverter1_15(this));
		this.registerConverter(new EnchantConverter1_15(this));
		this.registerConverter(new ParticleConverter1_18(this));
		this.registerConverter(new InventoryConverter(this));
		this.registerConverter(new AtlasConverter1_19_3(this));
		this.registerConverter(new CreativeTabsConverter1_19_3(this));
		this.registerConverter(new EnchantPathConverter1_19_4(this));
		this.registerConverter(new WidgetSlidersCreator1_19_4(this));
		this.registerConverter(new TitleConverter1_20(this));
		this.registerConverter(new GuiSlicerConverter1_20_2(this, from));
		this.registerConverter(new ImageFormatConverter1_20_3(this));
		this.registerConverter(new MapIconSlicerConverter1_20_5(this, from));
		this.registerConverter(new ArmorMoverConverter1_21_2(this));
		// After ArmorMover: custom theatrical materials → equipment/<id>.json (vanilla shape + dyeable).
		this.registerConverter(new CustomEquipmentJsonConverter1_21_2(this));
		this.registerConverter(new PostEffectShadersConverter1_21_2(this));
		// 1.21.4+: strip tintindex from overlay faces; ensure leather items/*.json dye tints.
		this.registerConverter(new ArmorOverlayTintIndexConverter1_21_4(this));
		this.registerConverter(new LeatherArmorItemsTintConverter1_21_4(this));
		// 1.21.4: empty_armor_slot_* left item/; remap model refs to pack block/transparent.
		this.registerConverter(new EmptyArmorSlotTextureConverter1_21_4(this));
		// After ArmorMover so entity/equipment/ exists; pig/strider saddle overlays → equipment.
		this.registerConverter(new SaddleEquipmentConverter1_21_5(this));
		// 1.21.5: enchanted_glint_entity → enchanted_glint_armor (jar diff).
		this.registerConverter(new EnchantGlintConverter1_21_5(this));
		// 1.21.5: remove obsolete core/post shader program JSON (wiki pack_format 55).
		this.registerConverter(new ShaderProgramJsonRemover1_21_5(this));
		this.registerConverter(new CelestialSlicerUpdater1_21_11(this));
		// After AtlasConverter1_19_3 may have stuffed item/ into blocks.json; peel into items.json.
		// Also dual-stitches models/armor onto items for hand models.
		this.registerConverter(new ItemsAtlasConverter1_21_11(this));
		// After atlas/item rewrites: fill missing model particle slots (#layer0 / #0 / first key).
		this.registerConverter(new ParticleTextureFallbackConverter(this));
		// 26.1 (84.0): entity textures into per-entity folders / renamed variants.
		this.registerConverter(new EntityTextureLayoutConverter26_1(this));
		// 26.1: DarkenWorldFactor → BossOverlayWorldDarkeningFactor; AmbientLightFactor N/A review.
		this.registerConverter(new ShaderLightmapUniformConverter26_1(this));
		// 26.2 (88.0): beds/signs atlases + Mojang slicer; pillar sides; text/block shader renames.
		this.registerConverter(new BedsSignsConverter26_2(this));
		this.registerConverter(new PillarSideConverter26_2(this));
		this.registerConverter(new ShaderTextConsolidationConverter26_2(this));
		// 26.1+ last shader step: strip pack core/post GLSL — cannot reliably port to Vulkan/SPIR-V.
		this.registerConverter(new IncompatibleCoreShaderStripper26_1(this));
		// 26.3 (97.1): trim palettes → textures/palettes/; override_armor_assets → trim_overrides.
		this.registerConverter(new TrimPaletteConverter26_3(this));
		// After trim: custom atlas palette_key/permutations (e.g. pixel/*) → textures/palettes/.
		this.registerConverter(new CustomPalettedPermutationsConverter26_3(this));
		this.registerConverter(new TrimEquipmentOverrideConverter26_3(this));
	}

	public void runPack(Pack pack) {
		try {
			Logger.log("Converting " + pack);
			pack.getHandler().setup();
			Logger.addTab();
			Logger.log("Running Converters");
			for (Converter converter : converters.values()) {
				if (converter.shouldConvert(gson, from, to)) {
					Logger.addTab();
					Logger.log("Running " + converter.getClass().getSimpleName());
					converter.convert(pack);
					Logger.subTab();
				}
			}

			Logger.subTab();
			pack.getHandler().finish();
		} catch (Throwable t) {
			Logger.resetTab();
			Logger.log("Failed to convert!");
			Util.propagate(t);
		}
	}

	public void runDir() throws IOException {
		try (Stream<Path> pathStream = Files.list(INPUT_DIR)) {
			try (Stream<Pack> packStream = pathStream.map(Pack::parse).filter(Objects::nonNull)) {
				packStream.forEach(this::runPack);
			}
		}
	}
}

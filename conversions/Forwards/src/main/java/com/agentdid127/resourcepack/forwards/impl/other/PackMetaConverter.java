package com.agentdid127.resourcepack.forwards.impl.other;

import com.agentdid127.resourcepack.library.Converter;
import com.agentdid127.resourcepack.library.PackConverter;
import com.agentdid127.resourcepack.library.pack.Pack;
import com.agentdid127.resourcepack.library.utilities.JsonUtil;
import com.agentdid127.resourcepack.library.utilities.Util;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.file.Path;

// Reference: https://minecraft.wiki/w/Pack_format
public class PackMetaConverter extends Converter {
	private final int from;
	private final int to;

	public PackMetaConverter(PackConverter packConverter, int from, int to) {
		super(packConverter);
		this.from = from;
		this.to = to;
	}

	@Override
	public boolean shouldConvert(Gson gson, int from, int to) {
		return true;
	}

	/**
	 * Converts MCMeta to newer version
	 *
	 * @param pack The input pack
	 * @throws IOException The IO Exception
	 */
	@Override
	public void convert(Pack pack) throws IOException {
		final Path file = pack.getWorkingPath().resolve("pack.mcmeta");
		if (!file.toFile().exists()) {
			return;
		}

		final Gson gson = packConverter.getGson();

		double packVersion = 4;
		JsonObject versionObj = Util.getVersionObjectByProtocol(packConverter.getGson(), to);
		if (versionObj != null) {
			packVersion = versionObj.get("pack_format").getAsDouble();
		}

		JsonObject json = JsonUtil.readJson(packConverter.getGson(), file);
		if (json == null) {
			json = new JsonObject();
		}

		{
			JsonObject meta = json.getAsJsonObject("meta");
			if (meta == null) {
				meta = new JsonObject();
			}

			meta.addProperty("game_version", Util.getVersionFromProtocol(packConverter.getGson(), to));
			json.add("meta", meta);
		}

		{
			JsonObject packObject = json.getAsJsonObject("pack");
			if (packObject == null) {
				packObject = new JsonObject();
			}

			// 1.21.9+ (25w31a): game reads min_format / max_format. A pack that only
			// has pack_format (even a correct 97.1) is marked incompatible in the UI
			// while often still loading when forced — which is exactly the confusing
			// state users hit after converting 1.21.5 → 26.x.
			final boolean usesMinMaxFormat = to >= Util.getVersionProtocol(gson, "1.21.9");

			if (usesMinMaxFormat) {
				// Output is for `to` only. Do not emit supported_formats: wiki forbids
				// it unless the pack also supports resource formats < 65.
				packObject.remove("supported_formats");
				// Keep pack_format for tooling / older readers; game prefers min/max.
				packObject.addProperty("pack_format", packVersion);
				writeMinMaxFormat(packObject, packVersion);
			} else {
				packObject.addProperty("pack_format", (int) packVersion);
				if (from <= Util.getVersionProtocol(gson, "1.20.1")
						&& to >= Util.getVersionProtocol(gson, "1.20.2")) {
					final JsonObject fromVersion = Util.getVersionObjectByProtocol(gson, from);
					final JsonObject toVersion = Util.getVersionObjectByProtocol(gson, to);

					final JsonObject supportedFormats = new JsonObject();
					supportedFormats.addProperty("min_inclusive",
							fromVersion != null ? fromVersion.get("pack_format").getAsInt() : (int) packVersion);
					supportedFormats.addProperty("max_inclusive",
							toVersion != null ? toVersion.get("pack_format").getAsInt() : (int) packVersion);
					packObject.add("supported_formats", supportedFormats);
				}
			}

			json.add("pack", packObject);
		}

		JsonUtil.writeJson(gson, file, json);
	}

	/**
	 * Encode major.minor pack formats the way 1.21.9+ expects: int, or [major, minor].
	 * A JSON number like 97.1 is not a valid format token — use [97, 1].
	 * max_format as a bare major means "any minor of that major".
	 */
	private static void writeMinMaxFormat(JsonObject packObject, double packVersion) {
		int major = (int) Math.floor(packVersion);
		int minor = (int) Math.round((packVersion - major) * 10.0);

		packObject.add("min_format", encodeFormat(major, minor));
		// Bare major ⇒ any minor (covers 97.0 and 97.1 on a 26.3 client).
		packObject.addProperty("max_format", major);
	}

	private static JsonElement encodeFormat(int major, int minor) {
		if (minor <= 0) {
			return new JsonPrimitive(major);
		}
		JsonArray arr = new JsonArray();
		arr.add(major);
		arr.add(minor);
		return arr;
	}
}

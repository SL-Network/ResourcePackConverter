package com.agentdid127.resourcepack.forwards.impl.v1_19.other;

import com.agentdid127.resourcepack.library.Converter;
import com.agentdid127.resourcepack.library.PackConverter;
import com.agentdid127.resourcepack.library.pack.Pack;
import com.agentdid127.resourcepack.library.utilities.Util;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Generates assets/minecraft/atlases/blocks.json when crossing the 1.19.3 atlas cliff.
 * Directory sources are namespace-agnostic (source "npc" stitches every namespace's
 * textures/npc). Scans all assets/&lt;ns&gt;/textures so custom namespaces (demo, tsf)
 * get coverage for paths outside textures/block and textures/item.
 */
public class AtlasConverter1_19_3 extends Converter {
    private final Set<String> directorySources = new LinkedHashSet<>();
    private final Set<String> singleSources = new LinkedHashSet<>();

    public AtlasConverter1_19_3(PackConverter packConverter) {
        super(packConverter);
    }

    @Override
    public boolean shouldConvert(Gson gson, int from, int to) {
        return from <= Util.getVersionProtocol(gson, "1.19.2") && to >= Util.getVersionProtocol(gson, "1.19.3");
    }

    @Override
    public void convert(Pack pack) throws IOException {
        directorySources.clear();
        singleSources.clear();

        Path assetsPath = pack.getWorkingPath().resolve("assets");
        if (!Files.isDirectory(assetsPath)) {
            return;
        }

        File[] namespaces = assetsPath.toFile().listFiles();
        if (namespaces == null) {
            return;
        }

        for (File nsDir : namespaces) {
            if (!nsDir.isDirectory()) {
                continue;
            }
            Path texturesPath = nsDir.toPath().resolve("textures");
            if (!Files.isDirectory(texturesPath)) {
                continue;
            }
            scanTextureRoot(texturesPath);
        }

        if (directorySources.isEmpty() && singleSources.isEmpty()) {
            return;
        }

        Path atlasesPath = pack.getWorkingPath().resolve("assets/minecraft/atlases".replace("/", File.separator));
        if (!Files.exists(atlasesPath)) {
            Files.createDirectories(atlasesPath);
        }

        JsonObject out = new JsonObject();
        JsonArray sources = new JsonArray();
        for (String dir : directorySources) {
            JsonObject source = new JsonObject();
            source.addProperty("type", "directory");
            source.addProperty("source", dir);
            source.addProperty("prefix", dir + "/");
            sources.add(source);
        }
        for (String resource : singleSources) {
            JsonObject source = new JsonObject();
            source.addProperty("type", "single");
            source.addProperty("resource", resource);
            sources.add(source);
        }
        out.add("sources", sources);

        Path output = atlasesPath.resolve("blocks.json");
        if (!Files.exists(output)) {
            try (OutputStream os = Files.newOutputStream(output)) {
                os.write(packConverter.getGson().toJson(out).getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private void scanTextureRoot(Path texturesPath) {
        File texturesFile = texturesPath.toFile();
        File[] children = texturesFile.listFiles();
        if (children == null) {
            return;
        }
        for (File file : children) {
            if (file.isDirectory()) {
                String name = file.getName();
                directorySources.add(name);
                findFiles(texturesPath.resolve(name), name);
            } else if (file.getName().endsWith(".png")) {
                singleSources.add(file.getName().replaceAll("\\.png$", ""));
            }
        }
    }

    private void findFiles(Path directory, String prefix) {
        File directoryFile = directory.toFile();
        if (!directoryFile.isDirectory()) {
            return;
        }
        for (File file : Objects.requireNonNull(directoryFile.listFiles())) {
            if (file.isDirectory()) {
                String nextPrefix = prefix + "/" + file.getName();
                directorySources.add(nextPrefix);
                findFiles(directory.resolve(file.getName()), nextPrefix);
            }
        }
    }
}

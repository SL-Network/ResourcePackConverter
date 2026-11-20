package com.agentdid127.spawnercmd.nbt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemCmdConverterTest {

    @ParameterizedTest(name = "protocol {0} → {1}")
    @CsvSource({
            "754, LEGACY_TAG",
            "765, LEGACY_TAG",
            "766, COMPONENTS_INT",
            "768, COMPONENTS_INT",
            "769, COMPONENTS_FLOATS",
            "774, COMPONENTS_FLOATS",
            "775, COMPONENTS_FLOATS",
            "777, COMPONENTS_FLOATS"
    })
    void eraFromProtocol(int protocol, ClientItemEra expected) {
        assertEquals(expected, ClientItemEra.fromProtocol(protocol));
    }

    @Test
    void tsfProjector_legacyPassthrough() throws Exception {
        NbtCompound spawner = load("fixtures/tsf_projector/input.snbt");
        NbtCompound before = spawner.copy();
        assertFalse(ItemCmdConverter.convertSpawnerBlockEntity(spawner, ClientItemEra.LEGACY_TAG));
        assertEquals(before, spawner);
        assertEquals(Integer.valueOf(1), headCmdLegacy(spawner.getCompound("SpawnData")));
    }

    @Test
    void tsfProjector_componentsInt() throws Exception {
        NbtCompound spawner = load("fixtures/tsf_projector/input.snbt");
        assertTrue(ItemCmdConverter.convertSpawnerBlockEntity(spawner, ClientItemEra.COMPONENTS_INT));

        NbtCompound head = armorHead(spawner.getCompound("SpawnData"));
        // Dual-write: components for modern clients + legacy tag for Via mid-pipeline.
        assertEquals(Integer.valueOf(1), head.getCompound("tag").getInt("CustomModelData"));
        assertEquals(Integer.valueOf(1), head.getCompound("components").getInt("minecraft:custom_model_data"));
        assertEquals(Integer.valueOf(1), head.getInt("count"));

        NbtCompound potHead = armorHead(
                spawner.getList("SpawnPotentials").get(0).asCompound().getCompound("Entity"));
        assertEquals(Integer.valueOf(1),
                potHead.getCompound("components").getInt("minecraft:custom_model_data"));
    }

    @Test
    void tsfProjector_componentsFloats() throws Exception {
        NbtCompound spawner = load("fixtures/tsf_projector/input.snbt");
        assertTrue(ItemCmdConverter.convertSpawnerBlockEntity(spawner, ClientItemEra.COMPONENTS_FLOATS));

        NbtCompound head = armorHead(spawner.getCompound("SpawnData"));
        NbtCompound cmd = head.getCompound("components").getCompound("minecraft:custom_model_data");
        assertNotNull(cmd);
        assertEquals(1, cmd.getList("floats").get(0).asInt());
        assertEquals(Integer.valueOf(1), head.getCompound("tag").getInt("CustomModelData"));
    }

    @Test
    void tsfProjector_idempotentFloats() throws Exception {
        NbtCompound spawner = load("fixtures/tsf_projector/input.snbt");
        assertTrue(ItemCmdConverter.convertSpawnerBlockEntity(spawner, ClientItemEra.COMPONENTS_FLOATS));
        NbtCompound once = spawner.copy();
        assertFalse(ItemCmdConverter.convertSpawnerBlockEntity(spawner, ClientItemEra.COMPONENTS_FLOATS));
        assertEquals(once, spawner);
    }

    @Test
    void wrappedSpawnDataAndPotentials_diamondSword() throws Exception {
        NbtCompound spawner = load("fixtures/synthetic/spawn_data_entity_wrapped.snbt");
        assertTrue(ItemCmdConverter.convertSpawnerBlockEntity(spawner, ClientItemEra.COMPONENTS_FLOATS));

        NbtCompound entity = spawner.getCompound("SpawnData").getCompound("entity");
        NbtCompound head = armorHead(entity);
        assertEquals(42, head.getCompound("components")
                .getCompound("minecraft:custom_model_data")
                .getList("floats").get(0).asInt());

        NbtCompound potEntity = spawner.getList("SpawnPotentials").get(0).asCompound()
                .getCompound("data").getCompound("entity");
        assertEquals(42, armorHead(potEntity).getCompound("components")
                .getCompound("minecraft:custom_model_data")
                .getList("floats").get(0).asInt());
    }

    @Test
    void missingCmd_untouched() {
        NbtCompound item = SnbtReader.readCompound("{id:\"minecraft:stone\",Count:1b}");
        NbtCompound before = item.copy();
        assertFalse(ItemCmdConverter.convertItem(item, ClientItemEra.COMPONENTS_FLOATS));
        assertEquals(before, item);
    }

    @Test
    void downConvert_floatsToLegacy() {
        NbtCompound item = SnbtReader.readCompound(
                "{id:\"minecraft:white_stained_glass\",count:1,"
                        + "components:{\"minecraft:custom_model_data\":{floats:[1.0f]}}}");
        assertTrue(ItemCmdConverter.convertItem(item, ClientItemEra.LEGACY_TAG));
        assertEquals(Integer.valueOf(1), item.getCompound("tag").getInt("CustomModelData"));
        assertNull(item.getCompound("components"));
    }

    @Test
    void namedProtocolAnchors_matchWiki() {
        assertEquals(ClientItemEra.LEGACY_TAG, ClientItemEra.fromProtocol(ClientItemEra.PROTOCOL_1_16_4));
        assertEquals(ClientItemEra.LEGACY_TAG, ClientItemEra.fromProtocol(ClientItemEra.PROTOCOL_1_20_4));
        assertEquals(ClientItemEra.COMPONENTS_INT, ClientItemEra.fromProtocol(ClientItemEra.PROTOCOL_1_20_5));
        assertEquals(ClientItemEra.COMPONENTS_INT, ClientItemEra.fromProtocol(ClientItemEra.PROTOCOL_1_21_3));
        assertEquals(ClientItemEra.COMPONENTS_FLOATS, ClientItemEra.fromProtocol(ClientItemEra.PROTOCOL_1_21_4));
        assertEquals(ClientItemEra.COMPONENTS_FLOATS, ClientItemEra.fromProtocol(ClientItemEra.PROTOCOL_1_21_11));
        assertEquals(ClientItemEra.COMPONENTS_FLOATS, ClientItemEra.fromProtocol(ClientItemEra.PROTOCOL_26_1));
        assertEquals(ClientItemEra.COMPONENTS_FLOATS, ClientItemEra.fromProtocol(ClientItemEra.PROTOCOL_26_3));
    }

    private static NbtCompound load(String path) throws IOException {
        InputStream in = ItemCmdConverterTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(in, "missing fixture " + path);
        StringBuilder sb = new StringBuilder();
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) >= 0) {
                sb.append(buf, 0, n);
            }
        }
        return SnbtReader.readCompound(sb.toString());
    }

    private static NbtCompound armorHead(NbtCompound entity) {
        return entity.getList("ArmorItems").get(3).asCompound();
    }

    private static Integer headCmdLegacy(NbtCompound entity) {
        return armorHead(entity).getCompound("tag").getInt("CustomModelData");
    }
}

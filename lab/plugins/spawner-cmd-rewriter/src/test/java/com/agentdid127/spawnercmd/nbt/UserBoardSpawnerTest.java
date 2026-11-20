package com.agentdid127.spawnercmd.nbt;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserBoardSpawnerTest {
    static final String SNBT = "{"
        + "SpawnData:{id:\"minecraft:armor_stand\",ArmorItems:[{},{},{},{id:\"minecraft:white_stained_glass\",Count:1b,tag:{CustomModelData:10}}],"
        + "Pose:{Head:[-30.0f,180.0f,0.0f]}},"
        + "MaxNearbyEntities:0s,MinSpawnDelay:0s,id:\"minecraft:mob_spawner\","
        + "SpawnRange:0s,MaxSpawnDelay:0s,RequiredPlayerRange:0s,SpawnCount:4s,"
        + "SpawnPotentials:[{Weight:1,Entity:{ArmorItems:[{},{},{},{id:\"minecraft:white_stained_glass\",tag:{CustomModelData:1},Count:1b}],"
        + "Pose:{Head:[-30.0f,180.0f,0.0f]},id:\"minecraft:armor_stand\"}}],Delay:0s}";

    @Test
    void userBoard_26_3_migratesEquipmentHead() {
        NbtCompound spawner = SnbtReader.readCompound(SNBT);
        assertTrue(ItemCmdConverter.convertSpawnerBlockEntity(spawner, ClientItemEra.PROTOCOL_26_3));

        NbtCompound entity = spawner.getCompound("SpawnData");
        assertNull(entity.getList("ArmorItems"), "ArmorItems must be removed for 1.21.5+ clients");
        NbtCompound head = entity.getCompound("equipment").getCompound("head");
        assertNotNull(head);
        System.out.println("equipment.head: " + SnbtWriter.write(head));
        assertEquals(10, head.getCompound("components").getCompound("minecraft:custom_model_data")
            .getList("floats").get(0).asInt());

        NbtCompound pot = spawner.getList("SpawnPotentials").get(0).asCompound().getCompound("Entity");
        assertNull(pot.getList("ArmorItems"));
        assertEquals(1, pot.getCompound("equipment").getCompound("head")
            .getCompound("components").getCompound("minecraft:custom_model_data")
            .getList("floats").get(0).asInt());
    }

    @Test
    void userBoard_1_21_4_keepsArmorItems() {
        NbtCompound spawner = SnbtReader.readCompound(SNBT);
        assertTrue(ItemCmdConverter.convertSpawnerBlockEntity(spawner, ClientItemEra.PROTOCOL_1_21_4));
        NbtCompound head = spawner.getCompound("SpawnData").getList("ArmorItems").get(3).asCompound();
        assertNotNull(head.getCompound("components"));
        assertNull(spawner.getCompound("SpawnData").getCompound("equipment"));
    }
}

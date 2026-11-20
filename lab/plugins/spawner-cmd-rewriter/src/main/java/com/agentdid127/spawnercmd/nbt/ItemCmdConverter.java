package com.agentdid127.spawnercmd.nbt;

import static com.agentdid127.spawnercmd.nbt.NbtFactory.ofFloat;

import java.util.ArrayList;
import java.util.Map;

/**
 * Rewrites {@code CustomModelData} on item stacks nested under mob-spawner block-entity NBT
 * into the shape expected by a given {@link ClientItemEra}.
 *
 * <p><b>Why:</b> Adventure maps (e.g. Terra Swoop Force projector boards) store theatrical
 * models as spawner preview armor-stands holding CMD items. Paper 1.16 keeps
 * {@code tag:{CustomModelData:N}}. ViaVersion does not reliably upgrade that nested field for
 * modern clients, so 1.20.5+ clients never select the resource-pack model. This converter
 * applies the missing item encoding step for spawner BE payloads only.
 */
public final class ItemCmdConverter {
    private static final String CMD_COMPONENT = "minecraft:custom_model_data";
    private static final String CMD_COMPONENT_SHORT = "custom_model_data";

    private ItemCmdConverter() {
    }

    /**
     * Convert a mob-spawner block entity compound in-place for the target client era.
     * Returns {@code true} if any item was modified.
     *
     * @deprecated prefer {@link #convertSpawnerBlockEntity(NbtCompound, int)} so 1.21.5+
     *             {@code equipment} migration runs
     */
    @Deprecated
    public static boolean convertSpawnerBlockEntity(NbtCompound spawner, ClientItemEra era) {
        if (era == null) {
            return false;
        }
        int protocol = era == ClientItemEra.LEGACY_TAG
                ? ClientItemEra.PROTOCOL_1_16_4
                : era == ClientItemEra.COMPONENTS_INT
                ? ClientItemEra.PROTOCOL_1_20_5
                : ClientItemEra.PROTOCOL_1_21_4;
        return convertSpawnerBlockEntity(spawner, protocol);
    }

    /**
     * Convert a mob-spawner block entity for the client's Via protocol version.
     *
     * <p>For {@code protocol >= 770} (1.21.5 / 26.x) also migrates
     * {@code ArmorItems}/{@code HandItems} → {@code equipment} — modern clients
     * ignore the old lists, which is why TSF presentation boards show bare stands
     * even after CMD components are rewritten onto ArmorItems.
     */
    public static boolean convertSpawnerBlockEntity(NbtCompound spawner, int protocolVersion) {
        if (spawner == null) {
            return false;
        }
        ClientItemEra era = ClientItemEra.fromProtocol(protocolVersion);
        boolean migrateEquipment = ClientItemEra.needsEquipmentMap(protocolVersion);
        boolean changed = false;
        changed |= convertSpawnData(spawner.getCompound("SpawnData"), era, migrateEquipment);
        changed |= convertSpawnPotentials(spawner.getList("SpawnPotentials"), era, migrateEquipment);
        return changed;
    }

    private static boolean convertSpawnData(NbtCompound spawnData, ClientItemEra era, boolean migrateEquipment) {
        if (spawnData == null) {
            return false;
        }
        // 1.18+ Via shape: SpawnData:{entity:{...}}
        NbtCompound entity = spawnData.getCompound("entity");
        if (entity != null) {
            return convertEntity(entity, era, migrateEquipment);
        }
        // Legacy 1.16: SpawnData is the entity compound itself
        return convertEntity(spawnData, era, migrateEquipment);
    }

    private static boolean convertSpawnPotentials(NbtList potentials, ClientItemEra era, boolean migrateEquipment) {
        if (potentials == null) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < potentials.size(); i++) {
            NbtValue entryVal = potentials.get(i);
            if (entryVal.type() != NbtValue.Type.COMPOUND) {
                continue;
            }
            NbtCompound entry = entryVal.asCompound();
            // Legacy: {Weight:1, Entity:{...}}
            NbtCompound entity = entry.getCompound("Entity");
            if (entity != null) {
                changed |= convertEntity(entity, era, migrateEquipment);
                continue;
            }
            // Newer: {data:{entity:{...}}, weight:1}
            NbtCompound data = entry.getCompound("data");
            if (data != null) {
                NbtCompound nested = data.getCompound("entity");
                if (nested != null) {
                    changed |= convertEntity(nested, era, migrateEquipment);
                }
            }
        }
        return changed;
    }

    private static boolean convertEntity(NbtCompound entity, ClientItemEra era, boolean migrateEquipment) {
        boolean changed = false;
        if (migrateEquipment) {
            changed |= migrateArmorAndHandsToEquipment(entity, era);
        } else {
            changed |= convertItemList(entity.getList("ArmorItems"), era);
            changed |= convertItemList(entity.getList("HandItems"), era);
        }
        changed |= convertEquipmentMap(entity.getCompound("equipment"), era);
        if (entity.contains("body_armor_item") && entity.get("body_armor_item").type() == NbtValue.Type.COMPOUND) {
            // Pre-1.21.5 body slot; fold into equipment when migrating.
            if (migrateEquipment) {
                NbtCompound equipment = entity.getCompound("equipment");
                if (equipment == null) {
                    equipment = new NbtCompound();
                    entity.put("equipment", equipment);
                }
                if (!equipment.contains("body")) {
                    NbtCompound body = entity.getCompound("body_armor_item").copy();
                    convertItem(body, era);
                    equipment.put("body", body);
                    changed = true;
                }
                entity.remove("body_armor_item");
                changed = true;
            } else {
                changed |= convertItem(entity.getCompound("body_armor_item"), era);
            }
        }
        changed |= convertItemList(entity.getList("Inventory"), era);
        if (entity.contains("Item") && entity.get("Item").type() == NbtValue.Type.COMPOUND) {
            changed |= convertItem(entity.getCompound("Item"), era);
        }
        return changed;
    }

    /**
     * 1.21.5+: fold ArmorItems [feet,legs,chest,head] + HandItems [mainhand,offhand]
     * into {@code equipment:{slot:item}} and drop the legacy lists.
     */
    private static boolean migrateArmorAndHandsToEquipment(NbtCompound entity, ClientItemEra era) {
        boolean changed = false;
        NbtCompound equipment = entity.getCompound("equipment");
        if (equipment == null) {
            equipment = new NbtCompound();
        }
        boolean wroteSlot = false;

        NbtList armor = entity.getList("ArmorItems");
        if (armor != null) {
            String[] armorSlots = {"feet", "legs", "chest", "head"};
            for (int i = 0; i < armorSlots.length && i < armor.size(); i++) {
                NbtValue v = armor.get(i);
                if (v.type() != NbtValue.Type.COMPOUND) {
                    continue;
                }
                NbtCompound item = v.asCompound();
                if (item.isEmpty()) {
                    continue;
                }
                convertItem(item, era);
                putEquipmentSlot(equipment, armorSlots[i], item, era);
                wroteSlot = true;
            }
            entity.remove("ArmorItems");
            changed = true;
        }

        NbtList hands = entity.getList("HandItems");
        if (hands != null) {
            String[] handSlots = {"mainhand", "offhand"};
            for (int i = 0; i < handSlots.length && i < hands.size(); i++) {
                NbtValue v = hands.get(i);
                if (v.type() != NbtValue.Type.COMPOUND) {
                    continue;
                }
                NbtCompound item = v.asCompound();
                if (item.isEmpty()) {
                    continue;
                }
                convertItem(item, era);
                putEquipmentSlot(equipment, handSlots[i], item, era);
                wroteSlot = true;
            }
            entity.remove("HandItems");
            changed = true;
        }

        if (wroteSlot || !equipment.isEmpty()) {
            entity.put("equipment", equipment);
            changed = true;
        }
        return changed;
    }

    private static boolean convertEquipmentMap(NbtCompound equipment, ClientItemEra era) {
        if (equipment == null) {
            return false;
        }
        boolean changed = false;
        for (Map.Entry<String, NbtValue> e : new ArrayList<>(equipment.entries().entrySet())) {
            if (e.getValue().type() == NbtValue.Type.COMPOUND) {
                changed |= convertItem(e.getValue().asCompound(), era);
            }
        }
        return changed;
    }

    /**
     * Write a slot into {@code equipment}. If the slot already exists without CMD but
     * {@code fromLegacy} has CMD (Via moved the stack but dropped CustomModelData), replace it.
     */
    private static void putEquipmentSlot(
            NbtCompound equipment, String slot, NbtCompound fromLegacy, ClientItemEra era) {
        if (!equipment.contains(slot)) {
            equipment.put(slot, fromLegacy.copy());
            return;
        }
        NbtCompound existing = equipment.getCompound(slot);
        Integer legacyCmd = extractCustomModelData(fromLegacy);
        Integer existingCmd = extractCustomModelData(existing);
        if (legacyCmd != null && existingCmd == null) {
            equipment.put(slot, fromLegacy.copy());
        } else if (existing != null) {
            // Keep Via's stack but still try to upgrade its CMD encoding.
            convertItem(existing, era);
        }
    }

    private static boolean convertItemList(NbtList items, ClientItemEra era) {
        if (items == null) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < items.size(); i++) {
            NbtValue v = items.get(i);
            if (v.type() == NbtValue.Type.COMPOUND) {
                changed |= convertItem(v.asCompound(), era);
            }
        }
        return changed;
    }

    /**
     * Convert a single item stack compound. Idempotent for the target era.
     */
    public static boolean convertItem(NbtCompound item, ClientItemEra era) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        Integer cmd = extractCustomModelData(item);
        if (cmd == null) {
            return false;
        }
        if (alreadyCorrect(item, era, cmd)) {
            return false;
        }
        stripAllCmdForms(item);
        switch (era) {
            case LEGACY_TAG:
                writeLegacyCmd(item, cmd);
                break;
            case COMPONENTS_INT:
                writeComponentsInt(item, cmd);
                // Keep legacy tag too so Via (if it runs after us) still sees CustomModelData.
                writeLegacyCmd(item, cmd);
                normalizeCount(item);
                break;
            case COMPONENTS_FLOATS:
                writeComponentsFloats(item, cmd);
                writeLegacyCmd(item, cmd);
                normalizeCount(item);
                break;
            default:
                throw new IllegalStateException("Unknown era: " + era);
        }
        return true;
    }

    private static Integer extractCustomModelData(NbtCompound item) {
        NbtCompound tag = item.getCompound("tag");
        if (tag != null && tag.contains("CustomModelData")) {
            return tag.getInt("CustomModelData");
        }
        NbtCompound components = item.getCompound("components");
        if (components == null) {
            return null;
        }
        NbtValue cmd = getCmdComponent(components);
        if (cmd == null) {
            return null;
        }
        if (cmd.type() == NbtValue.Type.INT || cmd.type() == NbtValue.Type.BYTE || cmd.type() == NbtValue.Type.SHORT) {
            return cmd.asInt();
        }
        if (cmd.type() == NbtValue.Type.COMPOUND) {
            NbtList floats = cmd.asCompound().getList("floats");
            if (floats != null && floats.size() > 0) {
                return floats.get(0).asInt();
            }
        }
        return null;
    }

    private static NbtValue getCmdComponent(NbtCompound components) {
        if (components.contains(CMD_COMPONENT)) {
            return components.get(CMD_COMPONENT);
        }
        if (components.contains(CMD_COMPONENT_SHORT)) {
            return components.get(CMD_COMPONENT_SHORT);
        }
        return null;
    }

    private static boolean alreadyCorrect(NbtCompound item, ClientItemEra era, int cmd) {
        switch (era) {
            case LEGACY_TAG: {
                NbtCompound tag = item.getCompound("tag");
                return tag != null
                        && tag.contains("CustomModelData")
                        && tag.getInt("CustomModelData") == cmd
                        && item.getCompound("components") == null;
            }
            case COMPONENTS_INT: {
                NbtCompound components = item.getCompound("components");
                if (components == null) {
                    return false;
                }
                NbtValue v = getCmdComponent(components);
                // Legacy tag may also be present (dual-write for Via pipeline safety).
                return v != null && (v.type() == NbtValue.Type.INT || v.type() == NbtValue.Type.BYTE)
                        && v.asInt() == cmd;
            }
            case COMPONENTS_FLOATS: {
                NbtCompound components = item.getCompound("components");
                if (components == null) {
                    return false;
                }
                NbtValue v = getCmdComponent(components);
                if (v == null || v.type() != NbtValue.Type.COMPOUND) {
                    return false;
                }
                NbtList floats = v.asCompound().getList("floats");
                return floats != null
                        && floats.size() > 0
                        && floats.get(0).asInt() == cmd;
            }
            default:
                return false;
        }
    }

    private static boolean hasLegacyCmd(NbtCompound item) {
        NbtCompound tag = item.getCompound("tag");
        return tag != null && tag.contains("CustomModelData");
    }

    private static void stripAllCmdForms(NbtCompound item) {
        NbtCompound tag = item.getCompound("tag");
        if (tag != null) {
            tag.remove("CustomModelData");
            if (tag.isEmpty()) {
                item.remove("tag");
            }
        }
        NbtCompound components = item.getCompound("components");
        if (components != null) {
            components.remove(CMD_COMPONENT);
            components.remove(CMD_COMPONENT_SHORT);
            if (components.isEmpty()) {
                item.remove("components");
            }
        }
    }

    private static void writeLegacyCmd(NbtCompound item, int cmd) {
        NbtCompound tag = item.getCompound("tag");
        if (tag == null) {
            tag = new NbtCompound();
            item.put("tag", tag);
        }
        tag.putInt("CustomModelData", cmd);
    }

    private static void writeComponentsInt(NbtCompound item, int cmd) {
        NbtCompound components = item.getCompound("components");
        if (components == null) {
            components = new NbtCompound();
            item.put("components", components);
        }
        components.putInt(CMD_COMPONENT, cmd);
    }

    private static void writeComponentsFloats(NbtCompound item, int cmd) {
        NbtCompound components = item.getCompound("components");
        if (components == null) {
            components = new NbtCompound();
            item.put("components", components);
        }
        NbtCompound cmdTag = new NbtCompound();
        NbtList floats = new NbtList();
        floats.add(ofFloat(cmd));
        cmdTag.put("floats", floats);
        components.put(CMD_COMPONENT, cmdTag);
    }

    /** When rewriting into component eras, also emit lowercase {@code count} if only {@code Count} exists. */
    private static void normalizeCount(NbtCompound item) {
        if (item.contains("count")) {
            return;
        }
        if (item.contains("Count")) {
            NbtValue count = item.get("Count");
            item.put("count", new NbtInt(count.asInt()));
            item.remove("Count");
        }
    }
}

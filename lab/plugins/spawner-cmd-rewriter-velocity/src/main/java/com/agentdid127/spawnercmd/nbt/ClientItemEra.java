package com.agentdid127.spawnercmd.nbt;

/**
 * Item CustomModelData encoding eras keyed by client protocol version.
 *
 * <p>Protocol numbers match Java Edition / ViaVersion (see minecraft.wiki Protocol version):
 * <ul>
 *   <li>1.16.4 = 754</li>
 *   <li>1.20.5 = 766 (item components; CMD is an int component)</li>
 *   <li>1.21.4 = 769 (CMD becomes {floats:[...]})</li>
 *   <li>26.1 = 775, 26.2 = 776, 26.3 = 777 — still floats-era CMD</li>
 * </ul>
 */
public enum ClientItemEra {
    /** Pre-1.20.5: {@code tag:{CustomModelData:N}} */
    LEGACY_TAG,
    /** 1.20.5–1.21.3: {@code components:{"minecraft:custom_model_data":N}} */
    COMPONENTS_INT,
    /** 1.21.4+: {@code components:{"minecraft:custom_model_data":{floats:[Nf]}}} */
    COMPONENTS_FLOATS;

    /** First protocol that uses item components (1.20.5). */
    public static final int PROTOCOL_1_20_5 = 766;
    /** First protocol that uses CMD floats lists (1.21.4). */
    public static final int PROTOCOL_1_21_4 = 769;
    /**
     * First protocol where {@code ArmorItems}/{@code HandItems} were merged into
     * {@code equipment} (1.21.5 / 25w03a). Spawner preview stands on 26.x clients
     * ignore ArmorItems entirely.
     */
    public static final int PROTOCOL_1_21_5 = 770;
    public static final int PROTOCOL_1_16_4 = 754;
    public static final int PROTOCOL_1_20_4 = 765;
    public static final int PROTOCOL_1_21_3 = 768;
    public static final int PROTOCOL_1_21_11 = 774;
    public static final int PROTOCOL_26_1 = 775;
    public static final int PROTOCOL_26_3 = 777;

    public static ClientItemEra fromProtocol(int protocolVersion) {
        if (protocolVersion >= PROTOCOL_1_21_4) {
            return COMPONENTS_FLOATS;
        }
        if (protocolVersion >= PROTOCOL_1_20_5) {
            return COMPONENTS_INT;
        }
        return LEGACY_TAG;
    }

    /** True when living-entity gear must be the {@code equipment} map (1.21.5+). */
    public static boolean needsEquipmentMap(int protocolVersion) {
        return protocolVersion >= PROTOCOL_1_21_5;
    }
}

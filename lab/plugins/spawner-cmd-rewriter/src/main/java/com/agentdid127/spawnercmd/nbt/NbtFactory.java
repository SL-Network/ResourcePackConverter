package com.agentdid127.spawnercmd.nbt;

/** Public constructors for package-private primitive tags. */
public final class NbtFactory {
    private NbtFactory() {
    }

    public static NbtValue ofByte(byte v) {
        return new NbtByte(v);
    }

    public static NbtValue ofShort(short v) {
        return new NbtShort(v);
    }

    public static NbtValue ofInt(int v) {
        return new NbtInt(v);
    }

    public static NbtValue ofLong(long v) {
        return new NbtLong(v);
    }

    public static NbtValue ofFloat(float v) {
        return new NbtFloat(v);
    }

    public static NbtValue ofDouble(double v) {
        return new NbtDouble(v);
    }

    public static NbtValue ofString(String v) {
        return new NbtString(v);
    }
}

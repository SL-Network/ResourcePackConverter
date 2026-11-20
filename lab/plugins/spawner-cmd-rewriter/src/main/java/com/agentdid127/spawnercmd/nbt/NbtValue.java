package com.agentdid127.spawnercmd.nbt;

/**
 * Minimal NBT value tree used by {@link ItemCmdConverter} (no Bukkit/Via at runtime in tests).
 */
public abstract class NbtValue {
    public enum Type {
        END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY
    }

    public abstract Type type();

    public NbtCompound asCompound() {
        throw new IllegalStateException("Not a compound: " + type());
    }

    public NbtList asList() {
        throw new IllegalStateException("Not a list: " + type());
    }

    public String asString() {
        throw new IllegalStateException("Not a string: " + type());
    }

    public int asInt() {
        throw new IllegalStateException("Not a number: " + type());
    }

    public float asFloat() {
        throw new IllegalStateException("Not a number: " + type());
    }

    public byte asByte() {
        throw new IllegalStateException("Not a number: " + type());
    }

    public long asLong() {
        throw new IllegalStateException("Not a number: " + type());
    }

    public abstract NbtValue copy();
}

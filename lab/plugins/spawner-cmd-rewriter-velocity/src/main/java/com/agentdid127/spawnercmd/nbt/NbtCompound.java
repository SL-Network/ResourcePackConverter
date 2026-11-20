package com.agentdid127.spawnercmd.nbt;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class NbtCompound extends NbtValue {
    private final Map<String, NbtValue> map = new LinkedHashMap<String, NbtValue>();

    @Override
    public Type type() {
        return Type.COMPOUND;
    }

    @Override
    public NbtCompound asCompound() {
        return this;
    }

    @Override
    public NbtCompound copy() {
        NbtCompound out = new NbtCompound();
        for (Map.Entry<String, NbtValue> e : map.entrySet()) {
            out.put(e.getKey(), e.getValue().copy());
        }
        return out;
    }

    public void put(String key, NbtValue value) {
        map.put(key, value);
    }

    public void putInt(String key, int value) {
        map.put(key, new NbtInt(value));
    }

    public void putByte(String key, byte value) {
        map.put(key, new NbtByte(value));
    }

    public void putString(String key, String value) {
        map.put(key, new NbtString(value));
    }

    public void putFloat(String key, float value) {
        map.put(key, new NbtFloat(value));
    }

    public NbtValue get(String key) {
        return map.get(key);
    }

    public NbtCompound getCompound(String key) {
        NbtValue v = map.get(key);
        return v == null ? null : v.asCompound();
    }

    public NbtList getList(String key) {
        NbtValue v = map.get(key);
        return v == null ? null : v.asList();
    }

    public String getString(String key) {
        NbtValue v = map.get(key);
        return v == null ? null : v.asString();
    }

    public Integer getInt(String key) {
        NbtValue v = map.get(key);
        return v == null ? null : Integer.valueOf(v.asInt());
    }

    public boolean contains(String key) {
        return map.containsKey(key);
    }

    public NbtValue remove(String key) {
        return map.remove(key);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(map.keySet());
    }

    public Map<String, NbtValue> entries() {
        return Collections.unmodifiableMap(map);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NbtCompound)) {
            return false;
        }
        return map.equals(((NbtCompound) o).map);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public String toString() {
        return SnbtWriter.write(this);
    }
}

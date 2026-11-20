package com.agentdid127.spawnercmd.nbt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NbtList extends NbtValue {
    private final List<NbtValue> values = new ArrayList<NbtValue>();
    private Type elementType = Type.END;

    @Override
    public Type type() {
        return Type.LIST;
    }

    @Override
    public NbtList asList() {
        return this;
    }

    @Override
    public NbtList copy() {
        NbtList out = new NbtList();
        out.elementType = elementType;
        for (NbtValue v : values) {
            out.values.add(v.copy());
        }
        return out;
    }

    public void add(NbtValue value) {
        if (values.isEmpty()) {
            elementType = value.type();
        }
        values.add(value);
    }

    public int size() {
        return values.size();
    }

    public NbtValue get(int index) {
        return values.get(index);
    }

    public void set(int index, NbtValue value) {
        values.set(index, value);
    }

    public List<NbtValue> values() {
        return Collections.unmodifiableList(values);
    }

    public Type elementType() {
        return elementType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NbtList)) {
            return false;
        }
        return values.equals(((NbtList) o).values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }
}

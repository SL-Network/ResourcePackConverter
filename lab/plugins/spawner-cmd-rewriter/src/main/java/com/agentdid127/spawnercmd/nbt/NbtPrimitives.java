package com.agentdid127.spawnercmd.nbt;

final class NbtByte extends NbtValue {
    private final byte value;

    NbtByte(byte value) {
        this.value = value;
    }

    byte value() {
        return value;
    }

    @Override
    public Type type() {
        return Type.BYTE;
    }

    @Override
    public int asInt() {
        return value;
    }

    @Override
    public byte asByte() {
        return value;
    }

    @Override
    public float asFloat() {
        return value;
    }

    @Override
    public NbtValue copy() {
        return new NbtByte(value);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NbtByte && ((NbtByte) o).value == value;
    }

    @Override
    public int hashCode() {
        return value;
    }
}

final class NbtShort extends NbtValue {
    private final short value;

    NbtShort(short value) {
        this.value = value;
    }

    short value() {
        return value;
    }

    @Override
    public Type type() {
        return Type.SHORT;
    }

    @Override
    public int asInt() {
        return value;
    }

    @Override
    public float asFloat() {
        return value;
    }

    @Override
    public NbtValue copy() {
        return new NbtShort(value);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NbtShort && ((NbtShort) o).value == value;
    }

    @Override
    public int hashCode() {
        return value;
    }
}

final class NbtInt extends NbtValue {
    private final int value;

    NbtInt(int value) {
        this.value = value;
    }

    int value() {
        return value;
    }

    @Override
    public Type type() {
        return Type.INT;
    }

    @Override
    public int asInt() {
        return value;
    }

    @Override
    public float asFloat() {
        return value;
    }

    @Override
    public NbtValue copy() {
        return new NbtInt(value);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NbtInt && ((NbtInt) o).value == value;
    }

    @Override
    public int hashCode() {
        return value;
    }
}

final class NbtLong extends NbtValue {
    private final long value;

    NbtLong(long value) {
        this.value = value;
    }

    long value() {
        return value;
    }

    @Override
    public Type type() {
        return Type.LONG;
    }

    @Override
    public int asInt() {
        return (int) value;
    }

    @Override
    public long asLong() {
        return value;
    }

    @Override
    public float asFloat() {
        return value;
    }

    @Override
    public NbtValue copy() {
        return new NbtLong(value);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NbtLong && ((NbtLong) o).value == value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }
}

final class NbtFloat extends NbtValue {
    private final float value;

    NbtFloat(float value) {
        this.value = value;
    }

    float value() {
        return value;
    }

    @Override
    public Type type() {
        return Type.FLOAT;
    }

    @Override
    public int asInt() {
        return (int) value;
    }

    @Override
    public float asFloat() {
        return value;
    }

    @Override
    public NbtValue copy() {
        return new NbtFloat(value);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NbtFloat && Float.compare(((NbtFloat) o).value, value) == 0;
    }

    @Override
    public int hashCode() {
        return Float.hashCode(value);
    }
}

final class NbtDouble extends NbtValue {
    private final double value;

    NbtDouble(double value) {
        this.value = value;
    }

    double value() {
        return value;
    }

    @Override
    public Type type() {
        return Type.DOUBLE;
    }

    @Override
    public int asInt() {
        return (int) value;
    }

    @Override
    public float asFloat() {
        return (float) value;
    }

    @Override
    public NbtValue copy() {
        return new NbtDouble(value);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NbtDouble && Double.compare(((NbtDouble) o).value, value) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(value);
    }
}

final class NbtString extends NbtValue {
    private final String value;

    NbtString(String value) {
        this.value = value;
    }

    @Override
    public Type type() {
        return Type.STRING;
    }

    @Override
    public String asString() {
        return value;
    }

    @Override
    public NbtValue copy() {
        return new NbtString(value);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NbtString && value.equals(((NbtString) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}

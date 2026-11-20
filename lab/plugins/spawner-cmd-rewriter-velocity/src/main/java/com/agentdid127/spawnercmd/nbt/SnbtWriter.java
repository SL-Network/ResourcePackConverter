package com.agentdid127.spawnercmd.nbt;

import java.util.Locale;
import java.util.Map;

/** Deterministic SNBT writer for fixtures / debugging. */
public final class SnbtWriter {
    private SnbtWriter() {
    }

    public static String write(NbtValue value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, NbtValue value) {
        switch (value.type()) {
            case COMPOUND:
                writeCompound(sb, value.asCompound());
                return;
            case LIST:
                writeList(sb, value.asList());
                return;
            case STRING:
                sb.append('"').append(escape(value.asString())).append('"');
                return;
            case BYTE:
                sb.append(((NbtByte) value).value()).append('b');
                return;
            case SHORT:
                sb.append(((NbtShort) value).value()).append('s');
                return;
            case INT:
                sb.append(((NbtInt) value).value());
                return;
            case LONG:
                sb.append(((NbtLong) value).value()).append('L');
                return;
            case FLOAT:
                sb.append(formatFloat(((NbtFloat) value).value())).append('f');
                return;
            case DOUBLE:
                sb.append(((NbtDouble) value).value()).append('d');
                return;
            default:
                throw new IllegalArgumentException("Unsupported NBT type for SNBT: " + value.type());
        }
    }

    private static void writeCompound(StringBuilder sb, NbtCompound compound) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, NbtValue> e : compound.entries().entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(e.getKey()).append(':');
            write(sb, e.getValue());
        }
        sb.append('}');
    }

    private static void writeList(StringBuilder sb, NbtList list) {
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            write(sb, list.get(i));
        }
        sb.append(']');
    }

    private static String formatFloat(float f) {
        if (f == (long) f) {
            return String.format(Locale.ROOT, "%.1f", f);
        }
        return Float.toString(f);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

package com.agentdid127.spawnercmd.nbt;

/**
 * Small SNBT parser covering compounds/lists/primitives used by spawner fixtures.
 * Not a full Mojang SNBT implementation.
 */
public final class SnbtReader {
    private final String src;
    private int i;

    private SnbtReader(String src) {
        this.src = src.trim();
    }

    public static NbtCompound readCompound(String snbt) {
        SnbtReader r = new SnbtReader(snbt);
        NbtValue v = r.parseValue();
        r.skipWhitespace();
        if (!r.eof()) {
            throw r.error("Trailing input");
        }
        return v.asCompound();
    }

    private NbtValue parseValue() {
        skipWhitespace();
        if (eof()) {
            throw error("Unexpected end");
        }
        char c = peek();
        if (c == '{') {
            return parseCompound();
        }
        if (c == '[') {
            return parseList();
        }
        if (c == '"' || c == '\'') {
            return new NbtString(parseQuotedString());
        }
        return parseBare();
    }

    private NbtCompound parseCompound() {
        expect('{');
        NbtCompound compound = new NbtCompound();
        skipWhitespace();
        if (peek() == '}') {
            i++;
            return compound;
        }
        while (true) {
            skipWhitespace();
            String key = parseKey();
            skipWhitespace();
            expect(':');
            NbtValue value = parseValue();
            compound.put(key, value);
            skipWhitespace();
            if (peek() == ',') {
                i++;
                continue;
            }
            expect('}');
            return compound;
        }
    }

    private NbtList parseList() {
        expect('[');
        NbtList list = new NbtList();
        skipWhitespace();
        if (peek() == ']') {
            i++;
            return list;
        }
        while (true) {
            list.add(parseValue());
            skipWhitespace();
            if (peek() == ',') {
                i++;
                continue;
            }
            expect(']');
            return list;
        }
    }

    private String parseKey() {
        skipWhitespace();
        if (peek() == '"' || peek() == '\'') {
            return parseQuotedString();
        }
        int start = i;
        while (!eof()) {
            char c = peek();
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '+' || c == '-' || c == '/') {
                i++;
            } else {
                break;
            }
        }
        if (start == i) {
            throw error("Expected key");
        }
        return src.substring(start, i);
    }

    private String parseQuotedString() {
        char quote = next();
        StringBuilder sb = new StringBuilder();
        while (!eof()) {
            char c = next();
            if (c == quote) {
                return sb.toString();
            }
            if (c == '\\') {
                if (eof()) {
                    throw error("Bad escape");
                }
                sb.append(next());
            } else {
                sb.append(c);
            }
        }
        throw error("Unterminated string");
    }

    private NbtValue parseBare() {
        int start = i;
        while (!eof()) {
            char c = peek();
            if (c == ',' || c == ']' || c == '}' || Character.isWhitespace(c)) {
                break;
            }
            i++;
        }
        String token = src.substring(start, i);
        if (token.isEmpty()) {
            throw error("Expected value");
        }
        char last = token.charAt(token.length() - 1);
        String num = token;
        if (last == 'b' || last == 'B') {
            return new NbtByte(Byte.parseByte(num.substring(0, num.length() - 1)));
        }
        if (last == 's' || last == 'S') {
            return new NbtShort(Short.parseShort(num.substring(0, num.length() - 1)));
        }
        if (last == 'l' || last == 'L') {
            return new NbtLong(Long.parseLong(num.substring(0, num.length() - 1)));
        }
        if (last == 'f' || last == 'F') {
            return new NbtFloat(Float.parseFloat(num.substring(0, num.length() - 1)));
        }
        if (last == 'd' || last == 'D') {
            return new NbtDouble(Double.parseDouble(num.substring(0, num.length() - 1)));
        }
        if (token.contains(".") || token.contains("e") || token.contains("E")) {
            return new NbtDouble(Double.parseDouble(token));
        }
        // unquoted string (e.g. minecraft:armor_stand) vs int
        boolean numeric = token.charAt(0) == '-' || Character.isDigit(token.charAt(0));
        if (numeric) {
            boolean allDigits = true;
            for (int j = token.charAt(0) == '-' ? 1 : 0; j < token.length(); j++) {
                if (!Character.isDigit(token.charAt(j))) {
                    allDigits = false;
                    break;
                }
            }
            if (allDigits) {
                return new NbtInt(Integer.parseInt(token));
            }
        }
        return new NbtString(token);
    }

    private void skipWhitespace() {
        while (!eof() && Character.isWhitespace(peek())) {
            i++;
        }
    }

    private char peek() {
        return src.charAt(i);
    }

    private char next() {
        return src.charAt(i++);
    }

    private void expect(char c) {
        skipWhitespace();
        if (eof() || next() != c) {
            throw error("Expected '" + c + "'");
        }
    }

    private boolean eof() {
        return i >= src.length();
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException(msg + " at " + i + " in: " + src);
    }
}

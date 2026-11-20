package com.agentdid127.spawnercmd.via;

import com.agentdid127.spawnercmd.nbt.NbtCompound;
import com.agentdid127.spawnercmd.nbt.NbtFactory;
import com.agentdid127.spawnercmd.nbt.NbtList;
import com.agentdid127.spawnercmd.nbt.NbtValue;
import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTByte;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTDouble;
import com.github.retrooper.packetevents.protocol.nbt.NBTFloat;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTLong;
import com.github.retrooper.packetevents.protocol.nbt.NBTNumber;
import com.github.retrooper.packetevents.protocol.nbt.NBTShort;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.nbt.NBTType;

import java.util.List;
import java.util.Map;

/** Converts between PacketEvents NBT and the plugin's pure {@link NbtCompound} tree. */
public final class PeNbtAdapter {
    private PeNbtAdapter() {
    }

    public static NbtCompound toInternal(NBTCompound pe) {
        if (pe == null) {
            return null;
        }
        NbtCompound out = new NbtCompound();
        for (Map.Entry<String, NBT> e : pe.getTags().entrySet()) {
            out.put(e.getKey(), toInternalValue(e.getValue()));
        }
        return out;
    }

    public static NBTCompound toPacketEvents(NbtCompound internal) {
        if (internal == null) {
            return null;
        }
        NBTCompound out = new NBTCompound();
        for (Map.Entry<String, NbtValue> e : internal.entries().entrySet()) {
            out.setTag(e.getKey(), toPeValue(e.getValue()));
        }
        return out;
    }

    private static NbtValue toInternalValue(NBT nbt) {
        if (nbt instanceof NBTCompound) {
            return toInternal((NBTCompound) nbt);
        }
        if (nbt instanceof NBTList) {
            NBTList<?> list = (NBTList<?>) nbt;
            NbtList out = new NbtList();
            List<? extends NBT> tags = list.getTags();
            for (NBT tag : tags) {
                out.add(toInternalValue(tag));
            }
            return out;
        }
        if (nbt instanceof NBTString) {
            return NbtFactory.ofString(((NBTString) nbt).getValue());
        }
        if (nbt instanceof NBTByte) {
            return NbtFactory.ofByte(((NBTByte) nbt).getAsByte());
        }
        if (nbt instanceof NBTShort) {
            return NbtFactory.ofShort(((NBTShort) nbt).getAsShort());
        }
        if (nbt instanceof NBTInt) {
            return NbtFactory.ofInt(((NBTInt) nbt).getAsInt());
        }
        if (nbt instanceof NBTLong) {
            return NbtFactory.ofLong(((NBTLong) nbt).getAsLong());
        }
        if (nbt instanceof NBTFloat) {
            return NbtFactory.ofFloat(((NBTFloat) nbt).getAsFloat());
        }
        if (nbt instanceof NBTDouble) {
            return NbtFactory.ofDouble(((NBTDouble) nbt).getAsDouble());
        }
        if (nbt instanceof NBTNumber) {
            return NbtFactory.ofInt(((NBTNumber) nbt).getAsInt());
        }
        throw new IllegalArgumentException("Unsupported PE NBT type: " + nbt.getType());
    }

    private static NBT toPeValue(NbtValue value) {
        switch (value.type()) {
            case COMPOUND:
                return toPacketEvents(value.asCompound());
            case LIST: {
                NbtList list = value.asList();
                NBTType<?> elementType = peElementType(list);
                @SuppressWarnings({"rawtypes", "unchecked"})
                NBTList peList = new NBTList(elementType);
                for (int i = 0; i < list.size(); i++) {
                    //noinspection unchecked
                    peList.addTag(toPeValue(list.get(i)));
                }
                return peList;
            }
            case STRING:
                return new NBTString(value.asString());
            case BYTE:
                return new NBTByte(value.asByte());
            case SHORT:
                return new NBTShort((short) value.asInt());
            case INT:
                return new NBTInt(value.asInt());
            case LONG:
                return new NBTLong(value.asLong());
            case FLOAT:
                return new NBTFlo1at(value.asFloat());
            case DOUBLE:
                return new NBTDouble(value.asFloat());
            default:
                throw new IllegalArgumentException("Unsupported internal NBT: " + value.type());
        }
    }

    private static NBTType<?> peElementType(NbtList list) {
        if (list.size() == 0) {
            return NBTType.COMPOUND;
        }
        switch (list.get(0).type()) {
            case COMPOUND:
                return NBTType.COMPOUND;
            case STRING:
                return NBTType.STRING;
            case INT:
                return NBTType.INT;
            case FLOAT:
                return NBTType.FLOAT;
            case BYTE:
                return NBTType.BYTE;
            case SHORT:
                return NBTType.SHORT;
            case LONG:
                return NBTType.LONG;
            case DOUBLE:
                return NBTType.DOUBLE;
            case LIST:
                return NBTType.LIST;
            default:
                return NBTType.COMPOUND;
        }
    }
}

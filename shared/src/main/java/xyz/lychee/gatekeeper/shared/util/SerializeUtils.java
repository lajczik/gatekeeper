package xyz.lychee.gatekeeper.shared.util;

import java.nio.ByteBuffer;
import java.util.Collection;

public class SerializeUtils {
    public static byte[] serialize(Collection<Integer> input) {
        ByteBuffer buffer = ByteBuffer.allocate(input.size() * 4);
        for (int i : input) {
            buffer.putInt(i);
        }
        return buffer.array();
    }

    public static void deserialize(byte[] bytes, Collection<Integer> output) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        if (bytes.length % 4 != 0) {
            throw new IllegalArgumentException("Invalid byte array length: " + bytes.length);
        }

        int count = bytes.length / 4;
        for (int i = 0; i < count; i++) {
            output.add(buffer.getInt());
        }
    }
}
package de.btegermany.terraplusminus.gen;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ChunkStatusCache {
    private static final Set<Long> failed = Collections.synchronizedSet(new HashSet<>());

    public static void markAsFailed(int x, int z) {
        failed.add(((long) x << 32) | (z & 0xffffffffL));
    }

    public static boolean isFailed(int x, int z) {
        return failed.contains(((long) x << 32) | (z & 0xffffffffL));
    }

    public static void remove(int x, int z) {
        failed.remove(((long) x << 32) | (z & 0xffffffffL));
    }
}
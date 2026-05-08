package xyz.lychee.gatekeeper.shared.objects;

import java.util.Arrays;
import java.util.Random;

public class FastRandom extends Random implements Cloneable {
    protected long seed;

    public FastRandom() {
        this(System.nanoTime());
    }

    public FastRandom(long seed) {
        this.seed = seed;
    }

    public synchronized long getSeed() {
        return seed;
    }

    public synchronized void setSeed(long seed) {
        this.seed = seed;
        super.setSeed(seed);
    }

    public synchronized void setSeed(int[] array) {
        if (array.length == 0)
            throw new IllegalArgumentException("Array length must be greater than zero");
        setSeed(Arrays.hashCode(array));
    }

    public FastRandom clone() throws CloneNotSupportedException {
        return (FastRandom) super.clone();
    }

    @Override
    protected int next(int nbits) {
        long x = seed;
        x ^= (x << 21);
        x ^= (x >>> 35);
        x ^= (x << 4);
        seed = x;
        x &= ((1L << nbits) - 1);

        return (int) x;
    }
}
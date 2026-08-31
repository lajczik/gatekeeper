package xyz.lychee.gatekeeper.shared.util;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

/**
 * Ensures essential fastutil internal classes are retained by Shadow minimization.
 */
public final class FastutilKeep {
    public static final Class<?>[] KEEP = {
        ObjectSets.class,
        ObjectArraySet.class,
        IntSets.class,
        IntArraySet.class
    };
}

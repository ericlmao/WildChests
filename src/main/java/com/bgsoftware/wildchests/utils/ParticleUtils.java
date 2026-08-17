package com.bgsoftware.wildchests.utils;

import com.bgsoftware.wildchests.WildChestsPlugin;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class ParticleUtils {

    private static final Object UNKNOWN = new Object();

    /**
     * Resolution results per configured particle name, shared across every chest. The value is either
     * the resolved particle constant or {@link #UNKNOWN} for a name that does not exist on this server
     * version. The particle enum differs between NMS versions, so this is kept untyped and each caller
     * supplies its own resolver.
     */
    private static final Map<String, Object> RESOLVED = new ConcurrentHashMap<>();

    private ParticleUtils() {

    }

    /**
     * Resolves a configured particle name to its enum constant, remembering the outcome.
     * <p>
     * Chest particles are emitted on a repeating task for every chest, so resolving a name that does not
     * exist on this server version used to throw and swallow an {@link IllegalArgumentException} on every
     * single tick. Building those stack traces dominated the plugin's allocation on a busy server. The
     * name set is fixed after the config loads, so each name is only ever resolved once and each bad one
     * is reported once instead of failing silently forever.
     *
     * @param name The configured particle name
     * @param resolver Resolves a name to the particle enum of the running NMS version, throwing if unknown
     * @return the resolved particle, or null if the name is not valid on this server version
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T resolveParticle(String name, Function<String, T> resolver) {
        Object cached = RESOLVED.get(name);

        if (cached == null) {
            try {
                cached = resolver.apply(name);
            } catch (Exception error) {
                cached = UNKNOWN;
                WildChestsPlugin.log("&cUnknown particle \"" + name + "\" in chest settings, skipping it.");
            }

            RESOLVED.put(name, cached);
        }

        return cached == UNKNOWN ? null : (T) cached;
    }

}

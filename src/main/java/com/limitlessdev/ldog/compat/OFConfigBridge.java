package com.limitlessdev.ldog.compat;

import com.limitlessdev.ldog.LDOGMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Map;

/**
 * Reflective bridge to OptiFine's settings — Phase C4.
 *
 * <b>Discovery (2026-04-18):</b> OptiFine 1.12.2 doesn't expose a separate
 * {@code optifine.Config} class with static feature toggles. Instead, OF's
 * core-mod transformer adds {@code ofXxx} <i>instance</i> fields to vanilla
 * {@link GameSettings}. This bridge probes those instance fields on the
 * client's GameSettings instance ({@code Minecraft.getMinecraft().gameSettings})
 * and exposes type-safe setters for the OFF state of each {@link OFFeature}.
 *
 * <b>Why reflection rather than a compile dependency:</b> per LDOG's
 * coexistence policy, the mod never imports OF classes. Detection +
 * configuration changes are purely reflective, which gracefully degrades
 * (logged warning + fallback) when OF is absent or its internals shift.
 *
 * <b>Failure modes handled:</b>
 *   <ul>
 *     <li>OF not loaded → {@link #isAvailable()} returns false; setters no-op.</li>
 *     <li>Minecraft.gameSettings null at probe time (probed too early) →
 *         retry on next setter call.</li>
 *     <li>Per-feature field missing across all candidate names → that feature
 *         is unmapped; setter for it returns false. Logged once at probe time.</li>
 *     <li>Field write throws (security manager, type mismatch) → setter
 *         returns false; caller falls back to OPTIFINE_OVERRIDE behavior.</li>
 *   </ul>
 *
 * Probe is lazy + cached: first call to {@link #isAvailable()} or
 * {@link #setOptiFineFeatureEnabled(OFFeature, boolean)} triggers it.
 *
 * Not thread-safe to initialize concurrently — Forge's mod-loading runs
 * single-threaded on the main thread, and the client render thread only
 * reads after init completes.
 */
public final class OFConfigBridge {

    private static boolean probed = false;
    private static boolean available = false;
    private static final Map<OFFeature, Field> resolvedFields = new EnumMap<>(OFFeature.class);

    private OFConfigBridge() {}

    /**
     * Returns true if OF is loaded AND at least one feature field was resolved
     * on the GameSettings instance. Probes on first call (re-probes if the
     * earlier probe failed because GameSettings wasn't yet initialized).
     */
    public static boolean isAvailable() {
        if (!probed || (!available && OptiFineCompat.isOptiFineLoaded())) {
            probe();
        }
        return available;
    }

    /**
     * Returns true if a write target was resolved for the given feature.
     * Useful for the GUI to grey out features we can't actually control.
     */
    public static boolean canControl(OFFeature feature) {
        if (!isAvailable()) return false;
        return resolvedFields.containsKey(feature);
    }

    /**
     * Set the OF feature's effective on/off state via reflective field write.
     * Returns true on success, false on failure (no field resolved, OF absent,
     * or write threw). Caller is responsible for the AUTO/OVERRIDE decision
     * logic; this method only attempts the write and reports.
     *
     * For BOOLEAN fields: writes {@code enabled} directly.
     * For INT fields: writes {@code feature.offValue()} when {@code !enabled},
     * leaves field untouched when enabling (since "on" has many possible
     * values per feature — we don't want to clobber the user's chosen mode
     * like fast vs fancy when re-enabling).
     */
    public static boolean setOptiFineFeatureEnabled(OFFeature feature, boolean enabled) {
        if (!isAvailable()) return false;
        Field f = resolvedFields.get(feature);
        if (f == null) return false;

        GameSettings settings = currentSettings();
        if (settings == null) return false;

        try {
            switch (feature.fieldType()) {
                case BOOLEAN:
                    f.setBoolean(settings, enabled);
                    return true;
                case INT:
                    if (!enabled) {
                        f.setInt(settings, feature.offValue());
                        return true;
                    }
                    // Enabling an INT feature: don't touch — user's existing
                    // value (fast/fancy/etc.) is preserved. Returning true
                    // means "we know how to flip this off and chose not to."
                    return true;
                default:
                    return false;
            }
        } catch (IllegalAccessException | IllegalArgumentException e) {
            LDOGMod.LOGGER.warn(
                "LDOG: OF write failed for {} via field {}: {}",
                feature.displayName(), f.getName(), e.toString());
            return false;
        }
    }

    /**
     * One-time probe (re-runnable if earlier attempts failed because of
     * lifecycle ordering). Locates the GameSettings instance, then for each
     * {@link OFFeature} tries each candidate field name in order. Logs a
     * summary of resolved + unmapped features the first time it succeeds.
     */
    private static synchronized void probe() {
        probed = true;

        if (!OptiFineCompat.isOptiFineLoaded()) {
            // Not an error — common case. Bridge stays unavailable.
            return;
        }

        GameSettings settings = currentSettings();
        if (settings == null) {
            // Probed before client startup completed. Reset probed flag so
            // the next isAvailable() call retries — this is the only state
            // transition that can flip 'probed' back to false.
            probed = false;
            return;
        }

        Class<?> settingsClass = settings.getClass();
        int resolved = 0;
        int unmapped = 0;
        StringBuilder unmappedSummary = new StringBuilder();

        for (OFFeature feature : OFFeature.values()) {
            Field f = resolveField(settingsClass, feature);
            if (f != null) {
                resolvedFields.put(feature, f);
                resolved++;
            } else {
                unmapped++;
                if (unmappedSummary.length() > 0) unmappedSummary.append(", ");
                unmappedSummary.append(feature.displayName());
            }
        }

        if (resolved > 0) {
            available = true;
            LDOGMod.LOGGER.info(
                "LDOG: OF interop bridge ready — {} feature(s) controllable, {} unmapped{}",
                resolved, unmapped,
                unmapped > 0 ? " (" + unmappedSummary + ")" : "");
        } else {
            LDOGMod.LOGGER.warn(
                "LDOG: OF detected but no ofXxx fields resolved on {} — bridge disabled. " +
                "OF's transformer may not have run, or field names differ in this version. " +
                "AUTO/OPTIFINE_OVERRIDE modes still work; LDOG_OVERRIDE will fall back.",
                settingsClass.getName());
        }
    }

    /**
     * Try each candidate field name for a feature; return the first one that
     * exists, has the expected type, and is writable. Walks the class
     * hierarchy because OF may add fields directly to GameSettings or to a
     * parent class via transformer. Returns null if none match.
     */
    private static Field resolveField(Class<?> startClass, OFFeature feature) {
        for (String candidate : feature.candidateFieldNames()) {
            for (Class<?> c = startClass; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(candidate);
                    if (!matchesType(f, feature.fieldType())) continue;
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {
                    // try next class in hierarchy
                } catch (SecurityException e) {
                    LDOGMod.LOGGER.warn("LDOG: OF field {} access denied: {}",
                        candidate, e.toString());
                    return null;
                }
            }
        }
        return null;
    }

    private static boolean matchesType(Field f, OFFeature.FieldType type) {
        Class<?> ft = f.getType();
        switch (type) {
            case BOOLEAN: return ft == boolean.class || ft == Boolean.class;
            case INT:     return ft == int.class || ft == Integer.class;
            default: return false;
        }
    }

    private static GameSettings currentSettings() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return null;
        return mc.gameSettings;
    }
}

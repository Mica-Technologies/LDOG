package com.limitlessdev.ldog.render.randommobs;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.io.IOException;
import java.util.*;

/**
 * Provides random entity texture variants from resource packs following
 * OptiFine's random entity/mob format.
 *
 * Resource packs can provide numbered texture variants:
 *   assets/minecraft/optifine/random/entity/cow/cow2.png
 *   assets/minecraft/optifine/random/entity/cow/cow3.png
 *   (cow.png is the default/variant 1)
 *
 * Or with .properties files for weighted selection, biome filters, etc.:
 *   assets/minecraft/optifine/random/entity/cow.properties
 *
 * Texture selection is deterministic per entity UUID, so the same entity
 * always shows the same texture variant.
 *
 * Called from MixinEntityRenderer to redirect texture binding for entities
 * that have random variants available.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class RandomEntityTextureHandler {

    // Maps vanilla texture location -> list of variant texture locations
    // e.g., minecraft:textures/entity/cow/cow.png -> [.../cow.png, .../cow2.png, ...]
    //
    // Keyed by ResourceLocation rather than its String form: getVariantTexture
    // runs in the entity bind path (every entity, every frame) and toString()
    // allocated there. ResourceLocation's equals/hashCode are String-backed, so
    // the lookup is allocation-free.
    //
    // Both maps are published whole via a volatile swap and never mutated after
    // publication. The bind path reads them on the render thread while a
    // resource reload (which may be driven off-thread by another mod, or by
    // Forge's splash during startup) rebuilds them; clearing and refilling the
    // live map instead would race a HashMap.get against a resize.
    private static volatile Map<ResourceLocation, List<ResourceLocation>> variantMap = Collections.emptyMap();

    // Optional weights per texture group (from .properties files)
    private static volatile Map<ResourceLocation, int[]> weightMap = Collections.emptyMap();

    @SubscribeEvent
    public static void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (!LDOGConfig.enableRandomEntityTextures) {
            variantMap = Collections.emptyMap();
            weightMap = Collections.emptyMap();
            return;
        }
        scanForVariants();
    }

    /**
     * Get the texture variant for an entity. Returns null if no variants exist,
     * meaning the vanilla texture should be used.
     */
    public static ResourceLocation getVariantTexture(ResourceLocation originalTexture, Entity entity) {
        if (!LDOGConfig.enableRandomEntityTextures || entity == null) return null;

        // One read of each published reference — a reload swapping them
        // mid-call cannot leave us with a variant list from one generation and
        // a weight table from another of a different length.
        Map<ResourceLocation, List<ResourceLocation>> variantMap = RandomEntityTextureHandler.variantMap;
        Map<ResourceLocation, int[]> weightMap = RandomEntityTextureHandler.weightMap;
        List<ResourceLocation> variants = variantMap.get(originalTexture);
        if (variants == null || variants.size() <= 1) return null;

        // UUID-based deterministic selection. Mask the sign bit rather than
        // negating — `-Integer.MIN_VALUE` stays negative and would index out of
        // bounds.
        int hash = entity.getUniqueID().hashCode() & 0x7fffffff;

        int[] weights = weightMap.get(originalTexture);
        if (weights != null && weights.length == variants.size()) {
            // Weighted selection
            int totalWeight = 0;
            for (int w : weights) totalWeight += w;
            if (totalWeight > 0) {
                int roll = hash % totalWeight;
                int cumulative = 0;
                for (int i = 0; i < weights.length; i++) {
                    cumulative += weights[i];
                    if (roll < cumulative) return variants.get(i);
                }
            }
        }

        // Uniform selection
        return variants.get(hash % variants.size());
    }

    private static void scanForVariants() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getResourceManager() == null) return;

        // Common entity texture paths to check for variants
        String[][] entityPaths = {
            {"textures/entity/cow/cow.png", "cow"},
            {"textures/entity/pig/pig.png", "pig"},
            {"textures/entity/sheep/sheep.png", "sheep"},
            {"textures/entity/chicken.png", "chicken"},
            {"textures/entity/wolf/wolf.png", "wolf"},
            {"textures/entity/cat/ocelot.png", "cat/ocelot"},
            {"textures/entity/horse/horse_white.png", "horse/horse_white"},
            {"textures/entity/horse/horse_brown.png", "horse/horse_brown"},
            {"textures/entity/horse/horse_black.png", "horse/horse_black"},
            {"textures/entity/zombie/zombie.png", "zombie/zombie"},
            {"textures/entity/skeleton/skeleton.png", "skeleton/skeleton"},
            {"textures/entity/creeper/creeper.png", "creeper/creeper"},
            {"textures/entity/spider/spider.png", "spider/spider"},
            {"textures/entity/enderman/enderman.png", "enderman/enderman"},
            {"textures/entity/villager/villager.png", "villager/villager"},
            {"textures/entity/squid.png", "squid"},
            {"textures/entity/bat.png", "bat"},
            {"textures/entity/rabbit/brown.png", "rabbit/brown"},
            {"textures/entity/llama/llama.png", "llama/llama"},
            {"textures/entity/parrot/parrot_red_blue.png", "parrot/parrot_red_blue"},
        };

        Map<ResourceLocation, List<ResourceLocation>> newVariants = new HashMap<>();
        Map<ResourceLocation, int[]> newWeights = new HashMap<>();
        int totalVariants = 0;
        for (String[] entry : entityPaths) {
            String vanillaPath = entry[0];
            String entityName = entry[1];

            // Check OptiFine random paths
            List<ResourceLocation> variants = findVariants(mc, vanillaPath, entityName);
            if (variants.size() > 1) {
                // variants.get(0) is the vanilla location findVariants seeded.
                ResourceLocation key = variants.get(0);
                newVariants.put(key, Collections.unmodifiableList(variants));
                totalVariants += variants.size();

                // Try loading .properties for weights
                int[] weights = loadVariantProperties(mc, entityName, variants.size());
                if (weights != null) newWeights.put(key, weights);
            }
        }

        // Publish both generations together; readers snapshot each reference
        // once, so the worst case during a swap is one frame on the old set.
        weightMap = Collections.unmodifiableMap(newWeights);
        variantMap = Collections.unmodifiableMap(newVariants);

        if (totalVariants > 0) {
            LDOGMod.LOGGER.info("LDOG: Found {} random entity texture variants across {} entity types",
                totalVariants, newVariants.size());
        }
    }

    private static List<ResourceLocation> findVariants(Minecraft mc, String vanillaPath, String entityName) {
        List<ResourceLocation> variants = new ArrayList<>();

        // Variant 1 is always the vanilla texture
        ResourceLocation vanilla = new ResourceLocation("minecraft", vanillaPath);
        variants.add(vanilla);

        // Strip .png for numbered variants
        String basePath = vanillaPath.substring(0, vanillaPath.length() - 4);
        String ext = ".png";

        // Check for optifine/random/entity/name/name2.png, name3.png, etc.
        for (String prefix : new String[]{"optifine/random/", "mcpatcher/mob/"}) {
            for (int i = 2; i <= 32; i++) {
                ResourceLocation variant = new ResourceLocation("minecraft",
                    prefix + "entity/" + entityName + "/" +
                    getFileName(vanillaPath) + i + ext);
                try {
                    mc.getResourceManager().getResource(variant).close();
                    variants.add(variant);
                } catch (IOException e) {
                    // Also try without entity subdirectory
                    variant = new ResourceLocation("minecraft",
                        prefix + "entity/" + entityName + i + ext);
                    try {
                        mc.getResourceManager().getResource(variant).close();
                        variants.add(variant);
                    } catch (IOException ignored) {
                        break; // No more variants
                    }
                }
            }
            if (variants.size() > 1) break; // Found variants in this path
        }

        // Also check vanilla path with numbered variants (some packs put them there)
        if (variants.size() <= 1) {
            for (int i = 2; i <= 32; i++) {
                ResourceLocation variant = new ResourceLocation("minecraft", basePath + i + ext);
                try {
                    mc.getResourceManager().getResource(variant).close();
                    variants.add(variant);
                } catch (IOException e) {
                    break;
                }
            }
        }

        return variants;
    }

    /** Returns the parsed weight table, or null when the pack ships none. */
    private static int[] loadVariantProperties(Minecraft mc, String entityName, int variantCount) {
        for (String prefix : new String[]{"optifine/random/", "mcpatcher/mob/"}) {
            ResourceLocation propsLoc = new ResourceLocation("minecraft",
                prefix + "entity/" + entityName + ".properties");
            try {
                IResource resource = mc.getResourceManager().getResource(propsLoc);
                Properties props = new Properties();
                props.load(resource.getInputStream());
                resource.close();

                String weightsStr = props.getProperty("weights");
                if (weightsStr != null) {
                    String[] parts = weightsStr.trim().split("\\s+");
                    int[] weights = new int[variantCount];
                    for (int i = 0; i < Math.min(parts.length, variantCount); i++) {
                        try { weights[i] = Integer.parseInt(parts[i]); }
                        catch (NumberFormatException e) { weights[i] = 1; }
                    }
                    // Fill remaining with 1
                    for (int i = parts.length; i < variantCount; i++) weights[i] = 1;
                    return weights;
                }
                return null;
            } catch (IOException ignored) {}
        }
        return null;
    }

    private static String getFileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        String name = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }
}

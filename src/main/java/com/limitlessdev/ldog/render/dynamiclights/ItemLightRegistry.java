package com.limitlessdev.ldog.render.dynamiclights;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps items to their light emission level (0-15).
 * Block items automatically inherit the block's light value.
 * Non-block items (lava bucket, etc.) have hardcoded overrides.
 */
public final class ItemLightRegistry {

    private static final Map<Item, Integer> ITEM_OVERRIDES = new HashMap<>();

    static {
        ITEM_OVERRIDES.put(Items.LAVA_BUCKET, 15);
        ITEM_OVERRIDES.put(Items.NETHER_STAR, 8);
        ITEM_OVERRIDES.put(Items.BLAZE_ROD, 10);
        ITEM_OVERRIDES.put(Items.BLAZE_POWDER, 6);
        ITEM_OVERRIDES.put(Items.GLOWSTONE_DUST, 8);
        ITEM_OVERRIDES.put(Items.MAGMA_CREAM, 6);
        ITEM_OVERRIDES.put(Items.FIRE_CHARGE, 10);
    }

    private ItemLightRegistry() {}

    /**
     * Returns the light level (0-15) for the given item stack.
     * Returns 0 if the item doesn't emit light.
     */
    public static int getLightLevel(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        Item item = stack.getItem();

        // Check hardcoded overrides first
        Integer override = ITEM_OVERRIDES.get(item);
        if (override != null) return override;

        // For block items, use the block's light value
        if (item instanceof ItemBlock) {
            Block block = ((ItemBlock) item).getBlock();
            return block.getDefaultState().getLightValue();
        }

        return 0;
    }
}

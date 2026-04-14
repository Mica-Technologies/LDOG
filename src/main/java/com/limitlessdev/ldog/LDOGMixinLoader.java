package com.limitlessdev.ldog;

import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Arrays;
import java.util.List;

/**
 * Registers LDOG's mixin configs with MixinBooter.
 * Both configs are loaded as late mixins -- the classes they target
 * (GUI screens, BlockFluidRenderer, RenderGlobal, etc.) are not loaded
 * until the player enters a world or opens a menu.
 */
public class LDOGMixinLoader implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Arrays.asList(
            "mixins.ldog.json",
            "mixins.ldog.early.json"
        );
    }
}

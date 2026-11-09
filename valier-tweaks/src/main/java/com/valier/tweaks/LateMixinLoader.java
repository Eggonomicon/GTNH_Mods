package com.valier.tweaks;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

/** The boiler ash mixins ({@code mixins.valiertweaks.json}), only when GregTech is there to change. */
@LateMixin
public class LateMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.valiertweaks.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        if (!loadedMods.contains("gregtech")) return Collections.emptyList();
        return Arrays.asList("MixinBoilerBronze", "MixinAdvancedBoiler");
    }
}

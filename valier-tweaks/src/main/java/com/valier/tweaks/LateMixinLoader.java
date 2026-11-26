package com.valier.tweaks;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

/**
 * The boiler ash mixins and the XP crafter tank mixin ({@code mixins.valiertweaks.json}), only when GregTech is there
 * to change, and the real-terrain hook in Personal Space's world provider (2.3.7), only with Personal Space and RWG2.
 */
@LateMixin
public class LateMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.valiertweaks.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        List<String> mixins = new java.util.ArrayList<>();
        if (loadedMods.contains("gregtech")) mixins.addAll(Arrays.asList("MixinBoilerBronze", "MixinAdvancedBoiler", "MixinBasicTank"));
        if (loadedMods.contains("personalspace") && loadedMods.contains("rwg2")) mixins.add("MixinPersonalWorldProvider");
        return mixins;
    }
}

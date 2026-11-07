package gtnhserver.mmfreepower;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

@LateMixin
public class LateMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.mmfreepower.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        if (!loadedMods.contains("matter-manipulator")) return Collections.emptyList();
        return Arrays.asList("MixinItemMatterManipulator", "MixinMMInventory", "MixinAbstractBuildable");
    }
}

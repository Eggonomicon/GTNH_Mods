package gtnhserver.mmfreepower;

import net.minecraft.item.Item;
import net.minecraftforge.oredict.RecipeSorter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Server-side only. Every Matter Manipulator's EU draw succeeds without draining its charge, so the tool never needs
 * charging ({@link gtnhserver.mmfreepower.mixin.MixinItemMatterManipulator}). It also adds the Valier MM Mk IV: an
 * MK III crafted on its own, which places blocks without machines for free ({@link MkIV},
 * {@link gtnhserver.mmfreepower.mixin.MixinMMInventory}), and can delete the drops a player's void list names
 * ({@link MkIVVoid}, {@code /mmvoid}).
 */
@Mod(
    modid = MMFreePower.MODID,
    name = "MM Free Power",
    version = "1.2.1",
    acceptableRemoteVersions = "*",
    dependencies = "after:matter-manipulator")
public class MMFreePower {

    public static final String MODID = "mmfreepower";
    public static final Logger LOG = LogManager.getLogger("MMFreePower");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MkIVConfig.load(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (!MkIVConfig.enabled) return;
        Item mk3 = GameRegistry.findItem("matter-manipulator", "itemMatterManipulator3");
        if (mk3 == null) {
            LOG.warn("Matter Manipulator MK III not found; the Valier MM Mk IV recipe is not added.");
            return;
        }
        RecipeSorter.register(MODID + ":mk4", MkIVRecipe.class, RecipeSorter.Category.SHAPELESS, "after:minecraft:shapeless");
        GameRegistry.addRecipe(new MkIVRecipe(mk3));
        FMLCommonHandler.instance()
            .bus()
            .register(new MkIVCraftSync());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new VoidCommand());
        if (SelfTest.ENABLED) event.registerServerCommand(new SelfTest());
    }
}

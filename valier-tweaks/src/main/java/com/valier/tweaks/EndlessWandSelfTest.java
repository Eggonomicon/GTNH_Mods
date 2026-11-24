package com.valier.tweaks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;

import com.mojang.authlib.GameProfile;

/**
 * {@code valiertweaks selftest wand} (2.3.5), all at once: an {@link EndlessWand} is a real Thaumcraft wand, not a
 * staff, full and never over-full, named and owned; drained in its owner's inventory it's full again, in someone else's
 * it stays empty, and a plain wand in the owner's inventory stays empty; in a chest the owner has open it fills too; with
 * {@code endlessWands} off nothing fills; a player can't use {@code /valier wand}, the console can, and a wand made for
 * a player who is offline is waiting for them and handed over as they log in. Everything it adds is taken away again.
 */
final class EndlessWandSelfTest {

    private EndlessWandSelfTest() {}

    static void run() {
        if (!EndlessWand.thaumcraft()) {
            SelfTest.check(false, "Thaumcraft is loaded on the test server (for endless wands)");
            return;
        }
        Checks.run();
    }

    /** Names Thaumcraft's classes, so it's only loaded when Thaumcraft is there. */
    static final class Checks {

        static void run() {
            WorldServer world = DimensionManager.getWorld(0);
            MinecraftServer server = MinecraftServer.getServer();
            boolean was = TweaksConfig.endlessWands;
            TweaksConfig.endlessWands = true;
            GameProfile og = new GameProfile(UUID.fromString("00000000-0000-4000-8000-0000000000f1"), "WandOwner");
            GameProfile xg = new GameProfile(UUID.fromString("00000000-0000-4000-8000-0000000000f2"), "WandOther");
            GameProfile lg = new GameProfile(UUID.fromString("00000000-0000-4000-8000-0000000000f3"), "WandLater");
            List<String> heard = new ArrayList<>();
            FakePlayer owner = new FakePlayer(world, og) {

                @Override
                public void addChatMessage(IChatComponent message) {
                    heard.add(message.getUnformattedText());
                }
            };
            FakePlayer other = new FakePlayer(world, xg);
            try {
                ItemStack wand = EndlessWand.Tc.make(og.getId()
                    .toString(), og.getName());
                thaumcraft.common.items.wands.ItemWandCasting w = (thaumcraft.common.items.wands.ItemWandCasting) wand
                    .getItem();
                int max = w.getMaxVis(wand);
                SelfTest.check(!w.isStaff(wand) && !w.isSceptre(wand) && full(w, wand, max) && max >= 50000
                    && EndlessWand.owned(wand, og.getId()
                        .toString())
                    && wand.getDisplayName()
                        .contains("Endless Wand"),
                    "an endless wand is a Thaumcraft wand (not a staff), named, owned and full: exactly its capacity, "
                        + EndlessWand.Tc.describe(wand));

                // The owner's inventory.
                drain(w, wand);
                owner.inventory.mainInventory[5] = wand;
                int before = EndlessWand.filled;
                boolean did = EndlessWand.refill(owner);
                SelfTest.check(did && full(w, wand, max) && EndlessWand.filled == before + 1,
                    "drained in its owner's inventory, it's full again (and not over-full)");

                // Someone else's inventory.
                drain(w, wand);
                owner.inventory.mainInventory[5] = null;
                other.inventory.mainInventory[5] = wand;
                EndlessWand.refill(other);
                SelfTest.check(empty(w, wand), "in another player's hands it's a plain wand: it stays empty");
                other.inventory.mainInventory[5] = null;

                // A plain wand in the owner's inventory.
                ItemStack plain = wand.copy();
                plain.getTagCompound()
                    .removeTag(EndlessWand.TAG);
                owner.inventory.mainInventory[6] = plain;
                EndlessWand.refill(owner);
                SelfTest.check(empty(w, plain), "a plain wand in the owner's inventory stays empty");
                owner.inventory.mainInventory[6] = null;

                // A chest the owner has open (as the Arcane Worktable's wand slot would be).
                InventoryBasic chest = new InventoryBasic("wandtest", false, 27);
                chest.setInventorySlotContents(4, wand);
                owner.openContainer = new ContainerChest(owner.inventory, chest);
                EndlessWand.refill(owner);
                SelfTest.check(full(w, chest.getStackInSlot(4), max), "in a menu its owner has open (a chest here), it fills too");
                owner.openContainer = owner.inventoryContainer;

                // Off.
                drain(w, wand);
                owner.inventory.mainInventory[5] = wand;
                TweaksConfig.endlessWands = false;
                new EndlessWand().onTick(new cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent(
                    cpw.mods.fml.common.gameevent.TickEvent.Phase.END));
                String off = EndlessWand.give("WandOwner");
                SelfTest.check(empty(w, wand) && off.contains("off"), "with endlessWands off nothing fills and the "
                    + "console's /valier wand refuses (" + off + ")");
                TweaksConfig.endlessWands = true;
                owner.inventory.mainInventory[5] = null;

                // The command: a player is refused, the console isn't.
                heard.clear();
                new TweaksCommand().processCommand(owner, new String[] { "wand", "WandOwner" });
                SelfTest.check(heard.stream()
                    .anyMatch(s -> s.contains("Only the server console")) && countWands(owner) == 0,
                    "a player's /valier wand is refused, and makes no wand (heard " + heard + ")");
                server.func_152358_ax() // getPlayerProfileCache
                    .func_152649_a(lg); // addEntry: "WandLater" is known without asking Mojang
                String said = EndlessWand.give("WandLater");
                EndlessWand.Pending p = EndlessWand.Pending.get();
                SelfTest.check(p != null && p.waiting.containsKey(lg.getId()
                    .toString()) && said.contains("next login"),
                    "the console's /valier wand for a player who is offline keeps it for them (" + said + ")");
                FakePlayer later = new FakePlayer(world, lg);
                EndlessWand.handOver(later);
                ItemStack got = null;
                for (ItemStack s : later.inventory.mainInventory) if (EndlessWand.owned(s, lg.getId()
                    .toString())) got = s;
                SelfTest.check(got != null && full(w, got, w.getMaxVis(got)) && !p.waiting.containsKey(lg.getId()
                    .toString()), "as they log in, the wand is handed over, full, and no longer waiting");
                EndlessWand.handOver(later);
                SelfTest.check(countWands(later) == 1, "a second login hands nothing more over");
            } finally {
                TweaksConfig.endlessWands = was;
                EndlessWand.Pending p = EndlessWand.Pending.get();
                if (p != null) p.take(lg.getId()
                    .toString());
            }
        }

        static boolean full(thaumcraft.common.items.wands.ItemWandCasting w, ItemStack s, int max) {
            for (thaumcraft.api.aspects.Aspect a : thaumcraft.api.aspects.Aspect.getPrimalAspects())
                if (w.getVis(s, a) != max) return false;
            return true;
        }

        static boolean empty(thaumcraft.common.items.wands.ItemWandCasting w, ItemStack s) {
            for (thaumcraft.api.aspects.Aspect a : thaumcraft.api.aspects.Aspect.getPrimalAspects())
                if (w.getVis(s, a) != 0) return false;
            return true;
        }

        static void drain(thaumcraft.common.items.wands.ItemWandCasting w, ItemStack s) {
            for (thaumcraft.api.aspects.Aspect a : thaumcraft.api.aspects.Aspect.getPrimalAspects()) w.storeVis(s, a, 0);
        }

        static int countWands(FakePlayer p) {
            int n = 0;
            for (ItemStack s : p.inventory.mainInventory) if (s != null && EndlessWand.Tc.isWand(s)) n++;
            return n;
        }
    }
}

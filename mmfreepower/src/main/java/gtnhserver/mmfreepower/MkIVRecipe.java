package gtnhserver.mmfreepower;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

/**
 * A Matter Manipulator MK III on its own in a crafting grid makes the Valier MM Mk IV: the same manipulator (its
 * charge, settings and links kept), marked as a Mk IV. Registered only on the server; clients see the result as the
 * server sends it.
 */
public class MkIVRecipe implements IRecipe {

    private final Item mk3;

    MkIVRecipe(Item mk3) {
        this.mk3 = mk3;
    }

    @Override
    public boolean matches(InventoryCrafting grid, World world) {
        return single(grid) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        ItemStack input = single(grid);
        return input == null ? null : MkIV.mark(input);
    }

    /** The grid's only item, if it is an MK III that isn't a Mk IV yet. */
    private ItemStack single(InventoryCrafting grid) {
        ItemStack found = null;
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack s = grid.getStackInSlot(i);
            if (s == null) continue;
            if (found != null || s.getItem() != mk3 || MkIV.isMkIV(s)) return null;
            found = s;
        }
        return found;
    }

    @Override
    public int getRecipeSize() {
        return 1;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return MkIV.mark(new ItemStack(mk3));
    }
}

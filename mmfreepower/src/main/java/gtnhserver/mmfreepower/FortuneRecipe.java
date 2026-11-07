package gtnhserver.mmfreepower;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

/**
 * Any Matter Manipulator plus an enchanted book with Fortune, alone in a crafting grid, makes the same manipulator with
 * that Fortune level; the book is used up. Only an upgrade matches (a lower or equal book does nothing). Registered
 * only on the server; {@link MkIVCraftSync} shows the result in the output slot.
 */
public class FortuneRecipe implements IRecipe {

    /** Shown by anything that lists recipes; a null output trips up mods that scan the recipe list. */
    private final Item example;

    FortuneRecipe(Item example) {
        this.example = example;
    }

    @Override
    public boolean matches(InventoryCrafting grid, World world) {
        return getCraftingResult(grid) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        ItemStack manipulator = null, book = null;
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack s = grid.getStackInSlot(i);
            if (s == null) continue;
            if (MMFortune.isManipulator(s) && manipulator == null) manipulator = s;
            else if (s.getItem() == Items.enchanted_book && book == null) book = s;
            else return null;
        }
        if (manipulator == null || book == null) return null;
        int level = MMFortune.onBook(book);
        if (level <= MMFortune.onItem(manipulator)) return null;
        return MMFortune.withFortune(manipulator, level);
    }

    @Override
    public int getRecipeSize() {
        return 2;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return MMFortune.withFortune(new ItemStack(example), 3);
    }
}

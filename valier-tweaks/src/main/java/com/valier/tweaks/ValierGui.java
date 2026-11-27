package com.valier.tweaks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import com.valier.tweaks.pipe.PipeGui;

import cpw.mods.fml.common.network.IGuiHandler;

/** Opens Valier's menus: pipe faces (ids 10 to 16, {@link PipeGui}), loot spawners ({@value #LOOT}), barrels ({@value #BARREL}). */
public final class ValierGui implements IGuiHandler {

    public static final int LOOT = 20, BARREL = 21, SATCHEL = 22, GRID = 23, BARREL_CONTROLLER = 24;

    private final PipeGui pipes = new PipeGui();

    /** A Satchel or Flask menu: x is the inventory slot holding it (the hand's). */
    private static boolean satchel(EntityPlayer player, int slot) {
        return slot >= 0 && slot < 9 && com.valier.tweaks.storage.SatchelInventory.of(player.inventory.getStackInSlot(slot)) != null;
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == LOOT) return LootMenu.server(player, world, x, y, z);
        if (id == BARREL) return com.valier.tweaks.storage.BarrelMenu.server(player, world, x, y, z);
        if (id == BARREL_CONTROLLER) return com.valier.tweaks.storage.BarrelControllerMenu.server(player, world, x, y, z);
        if (id == GRID) return com.valier.tweaks.grid.GridMenu.server(player, world, x, y, z);
        if (id == SATCHEL) return satchel(player, x) ? new com.valier.tweaks.storage.SatchelMenu(player, x) : null;
        return pipes.getServerGuiElement(id, player, world, x, y, z);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == LOOT) return ValierTweaks.proxy.lootGui(LootMenu.client(player, world, x, y, z));
        if (id == BARREL) return ValierTweaks.proxy.barrelGui(com.valier.tweaks.storage.BarrelMenu.client(player, world, x, y, z));
        if (id == BARREL_CONTROLLER) return ValierTweaks.proxy
            .barrelControllerGui(com.valier.tweaks.storage.BarrelControllerMenu.client(player, world, x, y, z));
        if (id == GRID) return ValierTweaks.proxy.gridGui(com.valier.tweaks.grid.GridMenu.client(player, world, x, y, z));
        if (id == SATCHEL) return satchel(player, x) ? ValierTweaks.proxy.satchelGui(new com.valier.tweaks.storage.SatchelMenu(player, x)) : null;
        return pipes.getClientGuiElement(id, player, world, x, y, z);
    }
}

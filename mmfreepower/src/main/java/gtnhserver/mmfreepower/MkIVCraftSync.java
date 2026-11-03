package gtnhserver.mmfreepower;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraftforge.common.util.FakePlayer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Shows the Mk IV in a crafting output slot. Minecraft 1.7.10 never sends crafting output slots to the client (the
 * client works the output out itself), and clients don't have this server-side recipe, so without this the output
 * looks empty even though the server has the Mk IV ready there.
 */
public class MkIVCraftSync {

    /** What was last sent to each player: window id, slot number and the stack. */
    private final Map<EntityPlayerMP, Object[]> sent = new WeakHashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)
            || event.player instanceof FakePlayer) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (player.playerNetServerHandler == null) return;
        Container container = player.openContainer;
        Slot result = mkIVResult(container);
        if (result == null) {
            sent.remove(player);
            return;
        }
        ItemStack stack = result.getStack();
        Object[] last = sent.get(player);
        if (last != null && (Integer) last[0] == container.windowId
            && (Integer) last[1] == result.slotNumber
            && ItemStack.areItemStacksEqual((ItemStack) last[2], stack)) return;
        player.playerNetServerHandler.sendPacket(new S2FPacketSetSlot(container.windowId, result.slotNumber, stack));
        sent.put(player, new Object[] { container.windowId, result.slotNumber, stack.copy() });
    }

    /** The container's crafting output slot if it holds a Mk IV, otherwise null. */
    static Slot mkIVResult(Container container) {
        if (container == null) return null;
        for (Object o : container.inventorySlots) {
            if (o instanceof SlotCrafting && MkIV.isMkIV(((Slot) o).getStack())) return (Slot) o;
        }
        return null;
    }
}

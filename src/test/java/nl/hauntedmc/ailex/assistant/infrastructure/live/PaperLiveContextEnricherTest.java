package nl.hauntedmc.ailex.assistant.infrastructure.live;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperLiveContextEnricherTest {

    @Test
    void heldItemQuestionShouldProvideTheActualMainHandItem() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack item = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(item);
        when(item.getType()).thenReturn(Material.DIAMOND);
        when(item.getAmount()).thenReturn(2);

        String metadata = PaperLiveContextEnricher.collect(player, null, "Wat houd ik in mijn hand?");

        assertEquals("player_main_hand=minecraft:diamondx2", metadata);
    }
}

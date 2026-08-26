package nl.hauntedmc.ailex.assistant.infrastructure.live;

import nl.hauntedmc.ailex.assistant.application.context.RequiredContextPlanner;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperLiveContextEnricherTest {

    @Test
    void heldItemQuestionShouldUseCompactRequesterStateWithoutScanningFullInventory() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack item = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getHealth()).thenReturn(20.0D);
        when(player.getMaxHealth()).thenReturn(20.0D);
        when(player.getFoodLevel()).thenReturn(20);
        when(player.getSaturation()).thenReturn(5.0F);
        when(player.getRemainingAir()).thenReturn(300);
        when(player.getMaximumAir()).thenReturn(300);
        when(inventory.getItemInMainHand()).thenReturn(item);
        when(item.getType()).thenReturn(Material.DIAMOND);
        when(item.getAmount()).thenReturn(2);

        String metadata = PaperLiveContextEnricher.collect(
                player,
                null,
                "Wat houd ik in mijn hand?",
                Set.of(RequiredContextPlanner.LiveSource.REQUESTER)
        );

        assertTrue(metadata.contains("player_main_hand=minecraft:diamondx2"));
        assertTrue(metadata.contains("player_gamemode=survival"));
        assertFalse(metadata.contains("player_inventory_summary="));
        assertFalse(metadata.contains("player_world="));
    }
}

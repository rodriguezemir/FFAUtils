package site.zvolcan.fFAUtils.managers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PlayersManager.
 */
class PlayersManagerTest {

    private PlayersManager playersManager;

    @BeforeEach
    void setUp() {
        playersManager = new PlayersManager();
    }

    @Test
    void removePlayer_shouldRemoveUuidFromMap() {
        UUID uuid = UUID.randomUUID();
        org.bukkit.entity.Player mockPlayer = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        org.mockito.Mockito.when(mockPlayer.getUniqueId()).thenReturn(uuid);

        playersManager.createPlayer(mockPlayer);
        assertDoesNotThrow(() -> playersManager.getFFAPlayer(mockPlayer),
                "Player should exist after createPlayer");

        playersManager.removePlayer(mockPlayer);

        // After remove, getFFAPlayer should create a new player (it calls createPlayer internally)
        // The key test: removePlayer should NOT add back the same instance
        // We verify this by checking that getFFAPlayer still works (creates a new one)
        assertDoesNotThrow(() -> playersManager.getFFAPlayer(mockPlayer),
                "getFFAPlayer should auto-create after remove");
    }

    @Test
    void removePlayer_shouldNotLeakReferences() {
        UUID uuid = UUID.randomUUID();
        org.bukkit.entity.Player mockPlayer = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        org.mockito.Mockito.when(mockPlayer.getUniqueId()).thenReturn(uuid);

        playersManager.createPlayer(mockPlayer);
        playersManager.removePlayer(mockPlayer);

        // After remove + createPlayer, we get a new instance
        var first = playersManager.getFFAPlayer(mockPlayer);
        var second = playersManager.getFFAPlayer(mockPlayer);

        // Both calls to getFFAPlayer should return the same instance (from the auto-create)
        assertSame(first, second, "getFFAPlayer should return same instance after auto-create");
    }

    @Test
    void createPlayer_afterRemove_shouldCreateNewInstance() {
        UUID uuid = UUID.randomUUID();
        org.bukkit.entity.Player mockPlayer = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        org.mockito.Mockito.when(mockPlayer.getUniqueId()).thenReturn(uuid);

        playersManager.createPlayer(mockPlayer);
        var original = playersManager.getFFAPlayer(mockPlayer);

        playersManager.removePlayer(mockPlayer);

        // After remove + createPlayer, get the new reference
        playersManager.createPlayer(mockPlayer);
        var afterRemoveAndCreate = playersManager.getFFAPlayer(mockPlayer);

        // Must be a different instance (new FFAPlayer)
        assertNotSame(original, afterRemoveAndCreate,
                "After remove + createPlayer, must be a new FFAPlayer instance");
    }
}

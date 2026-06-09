package site.zvolcan.fFAUtils.objects;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FFAPlayer value object.
 */
class FFAPlayerTest {

    @Test
    void killstreak_shouldDefaultToZero() {
        FFAPlayer player = new FFAPlayer(UUID.randomUUID());
        assertEquals(0, player.getKillstreak(), "New FFAPlayer must have killstreak = 0");
    }

    @Test
    void killstreak_shouldRoundtripGetterAndSetter() {
        FFAPlayer player = new FFAPlayer(UUID.randomUUID());
        player.setKillstreak(7);
        assertEquals(7, player.getKillstreak(), "killstreak set to 7 must be returned by getter");
    }

    @Test
    void killstreak_shouldResetToZero() {
        FFAPlayer player = new FFAPlayer(UUID.randomUUID());
        player.setKillstreak(10);
        player.setKillstreak(0);
        assertEquals(0, player.getKillstreak(), "After resetting to 0, killstreak must be 0");
    }
}

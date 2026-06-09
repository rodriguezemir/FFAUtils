package site.zvolcan.fFAUtils.commands;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static site.zvolcan.fFAUtils.managers.SpawnManager.isKitAllowedAtSpawn;

/**
 * Tests for LoadMeCommand kit-list containment logic (Task 4.4).
 * These test the pure containment check that determines whether
 * a kit is allowed at a spawn based on the spawn's allowed-kits list.
 */
class LoadMeCommandTest {

    @Test
    void kitAllowed_whenAllowedKitsIsNull() {
        assertTrue(isKitAllowedAtSpawn(null, "archer"));
    }

    @Test
    void kitAllowed_whenAllowedKitsIsEmpty() {
        assertTrue(isKitAllowedAtSpawn(Collections.emptyList(), "archer"));
    }

    @Test
    void kitAllowed_whenKitIsInList() {
        assertTrue(isKitAllowedAtSpawn(
                Arrays.asList("archer", "warrior", "mage"), "warrior"));
    }

    @Test
    void kitRejected_whenKitIsNotInList() {
        assertFalse(isKitAllowedAtSpawn(
                Arrays.asList("archer", "warrior"), "mage"));
    }

    @Test
    void kitAllowed_withCaseInsensitiveMatch() {
        assertTrue(isKitAllowedAtSpawn(
                Arrays.asList("archer", "warrior"), "ARCHER"));
    }

    @Test
    void kitAllowed_withMixedCaseInput() {
        assertTrue(isKitAllowedAtSpawn(
                Arrays.asList("archer", "warrior"), "ArChEr"));
    }
}

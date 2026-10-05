package com.skillcore.utils;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * Sound effect toolkit.
 */
public final class SoundUtils {

    private SoundUtils() {
    }

    public static void play(Location location, Sound sound, float volume, float pitch) {
        if (location == null || location.getWorld() == null || sound == null) {
            return;
        }
        location.getWorld().playSound(location, sound, volume, pitch);
    }

    public static void play(Entity entity, Sound sound, float volume, float pitch) {
        if (entity == null) {
            return;
        }
        play(entity.getLocation(), sound, volume, pitch);
    }

    public static void playToPlayer(Player player, Sound sound, float volume, float pitch) {
        if (player == null || sound == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public static void playToPlayers(Collection<Player> players, Sound sound, float volume, float pitch) {
        if (players == null) {
            return;
        }
        for (Player player : players) {
            playToPlayer(player, sound, volume, pitch);
        }
    }

    /**
     * Radius-based sound (hearable within range).
     */
    public static void playRadius(Location center, Sound sound, float volume, float pitch, double radius) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        double radiusSq = radius * radius;
        for (Player player : center.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= radiusSq) {
                player.playSound(center, sound, volume, pitch);
            }
        }
    }

    // Common skill sounds

    public static void castSuccess(Player player) {
        playToPlayer(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
    }

    public static void castFail(Player player) {
        playToPlayer(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
    }

    public static void cooldown(Player player) {
        playToPlayer(player, Sound.UI_BUTTON_CLICK, 0.6f, 0.5f);
    }

    public static void hit(Player player) {
        playToPlayer(player, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.0f);
    }

    public static void critical(Player player) {
        playToPlayer(player, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
    }

    public static void heal(Player player) {
        playToPlayer(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
    }

    public static void dash(Player player) {
        playToPlayer(player, Sound.ENTITY_BREEZE_WIND_BURST, 0.8f, 1.2f);
    }

    public static void explosion(Location location) {
        play(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
    }

    public static void levelUp(Player player) {
        playToPlayer(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }
}

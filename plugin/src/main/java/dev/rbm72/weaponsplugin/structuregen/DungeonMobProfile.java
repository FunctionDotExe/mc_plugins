package dev.rbm72.weaponsplugin.structuregen;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * One boss's dungeon-grunt flavor: which vanilla mob stands in for it, what it holds, how tough it
 * is, and which shared {@link MobAbility} it pulses. Reuses vanilla mobs already close to each
 * boss's theme (a real drowned for the tide leviathan, a real blaze for the inferno warlord) rather
 * than dressing up a generic mob — same "weaponise the real object" instinct as the boss roster,
 * applied to grunts instead of bosses.
 */
public record DungeonMobProfile(
        Component label,
        EntityType base,
        Material handItem,
        double health,
        MobAbility ability
) {
}

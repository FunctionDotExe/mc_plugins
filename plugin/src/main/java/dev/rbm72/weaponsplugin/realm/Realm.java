package dev.rbm72.weaponsplugin.realm;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

/**
 * Stateless definition of one realm: its own dimension, an {@link ArenaTheme} describing what that
 * dimension looks like, the crystal that carries a player there, and which boss owns the arena at
 * its spawn point. Mirrors {@code boss.Boss} and {@code items.Weapon} — one instance per realm,
 * shared across every visit.
 */
public final class Realm {

    private final String id;
    private final String worldName;
    private final Component displayName;
    private final Material crystalMaterial;
    private final ArenaTheme theme;
    private final String bossId;

    public Realm(String id, String worldName, Component displayName, Material crystalMaterial, ArenaTheme theme, String bossId) {
        this.id = id;
        this.worldName = worldName;
        this.displayName = displayName;
        this.crystalMaterial = crystalMaterial;
        this.theme = theme;
        this.bossId = bossId;
    }

    public String id() {
        return id;
    }

    public String worldName() {
        return worldName;
    }

    public Component displayName() {
        return displayName;
    }

    public Material crystalMaterial() {
        return crystalMaterial;
    }

    public ArenaTheme theme() {
        return theme;
    }

    public String bossId() {
        return bossId;
    }
}

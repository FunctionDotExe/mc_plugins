package dev.rbm72.weaponsplugin.items;

import org.bukkit.Color;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;

public enum Rarity {

    COMMON("Common", NamedTextColor.WHITE, Particle.CRIT, 1.0),
    RARE("Rare", NamedTextColor.AQUA, Particle.ENCHANTED_HIT, 1.15),
    EPIC("Epic", NamedTextColor.LIGHT_PURPLE, Particle.WITCH, 1.3),
    LEGENDARY("Legendary", NamedTextColor.GOLD, Particle.FLAME, 1.5),
    MYTHIC("Mythic", NamedTextColor.RED, Particle.END_ROD, 1.75);

    private final String label;
    private final NamedTextColor color;
    private final Particle particle;
    private final double statMultiplier;

    Rarity(String label, NamedTextColor color, Particle particle, double statMultiplier) {
        this.label = label;
        this.color = color;
        this.particle = particle;
        this.statMultiplier = statMultiplier;
    }

    public String label() {
        return label;
    }

    public NamedTextColor color() {
        return color;
    }

    public Particle particle() {
        return particle;
    }

    public double statMultiplier() {
        return statMultiplier;
    }

    /** Bukkit RGB equivalent of {@link #color()}, for dust particles / colored FX that don't take Adventure colors. */
    public Color bukkitColor() {
        return Color.fromRGB(color.value());
    }

    /** Epic and above get the enchant-glint shimmer even without a real enchantment, matching Skyblock tiering. */
    public boolean glint() {
        return ordinal() >= EPIC.ordinal();
    }
}

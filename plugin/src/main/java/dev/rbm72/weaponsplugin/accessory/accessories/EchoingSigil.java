package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.CooldownManager.Slot;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Doesn't touch damage or cooldowns at all — instead it rewrites what casting an ability means:
 * every cast has a flat chance to immediately repeat itself for free, ignoring cooldown entirely.
 * Turns any weapon's kit into a potential double-cast without favoring one weapon over another.
 */
public final class EchoingSigil extends Accessory {

    private static final double PROC_CHANCE = 0.20;
    private static final Color ECHO_COLOR = Color.fromRGB(150, 220, 235);

    public EchoingSigil(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "echoing_sigil";
    }

    @Override
    public Material material() {
        return Material.ECHO_SHARD;
    }

    @Override
    public String displayNameText() {
        return "Echoing Sigil";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("20% chance", NamedTextColor.GREEN)
                        .append(Component.text(" for any ability cast to", NamedTextColor.GRAY)),
                Component.text("instantly repeat itself for free,", NamedTextColor.GRAY),
                Component.text("ignoring its cooldown.", NamedTextColor.GRAY));
    }

    @Override
    public void onAbilityCast(Player player, Weapon weapon, Slot slot) {
        if (ThreadLocalRandom.current().nextDouble() >= PROC_CHANCE) {
            return;
        }

        Fx.sound(player, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.4f);
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), ECHO_COLOR, 1.4f, 22, 0.5);
        Fx.coloredRing(player.getLocation().add(0, 0.1, 0), ECHO_COLOR, 0.9f, 0.9, 16, 0);

        switch (slot) {
            case ABILITY1 -> weapon.ability1(player);
            case ABILITY2 -> weapon.ability2(player);
            case ABILITY3 -> weapon.ability3(player);
            case ULTIMATE -> weapon.ultimate(player);
        }
    }
}

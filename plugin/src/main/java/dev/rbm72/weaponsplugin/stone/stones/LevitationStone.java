package dev.rbm72.weaponsplugin.stone.stones;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.stone.Stone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Standalone active ability: a timed burst of the vanilla Levitation effect (steady upward drift,
 * full WASD control retained) backed by Slow Falling so it never ends in fall damage. Deliberately
 * built on vanilla potion mechanics rather than toggling {@code setAllowFlight}/{@code setFlying} —
 * {@link SkyleapStone} and {@code WyrmwingPlate}'s wing-dash already fight over those flags every
 * half-second tick, and a third system doing the same would only add more races to referee.
 */
public final class LevitationStone extends Stone {

    private static final int LEVITATION_TICKS = 100;
    private static final int SLOW_FALLING_TICKS = 140;
    private static final Color SKY_COLOR = Color.fromRGB(200, 225, 255);

    public LevitationStone(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "levitation_stone";
    }

    @Override
    public Material material() {
        return Material.ELYTRA;
    }

    @Override
    public String displayNameText() {
        return "Levitation Stone";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("A standalone active ability.", NamedTextColor.DARK_GRAY),
                Component.text("Grants a standalone active", NamedTextColor.GRAY),
                Component.text("ability of its own.", NamedTextColor.GRAY));
    }

    @Override
    public boolean hasPersonalAbility() {
        return true;
    }

    @Override
    public String personalAbilityName() {
        return "Skyward Drift";
    }

    @Override
    public List<Component> personalAbilityLore() {
        return List.of(
                Component.text("Drift steadily upward for 5s", NamedTextColor.GRAY),
                Component.text("with full movement control,", NamedTextColor.GRAY),
                Component.text("plus enough Slow Falling to", NamedTextColor.GRAY),
                Component.text("land safely after.", NamedTextColor.GRAY));
    }

    @Override
    public double personalAbilityCooldownSeconds() {
        return 20.0;
    }

    @Override
    public void personalAbility(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, LEVITATION_TICKS, 0, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, SLOW_FALLING_TICKS, 0, true, true));

        Fx.sound(player, Sound.ITEM_ELYTRA_FLYING, 1.0f, 1.1f);
        Fx.sound(player, Sound.ENTITY_PHANTOM_FLAP, 0.7f, 0.8f);
        Fx.coloredBurst(player.getLocation(), SKY_COLOR, 1.4f, 20, 0.4);
        Fx.trail(player.getLocation(), Particle.CLOUD, 12, 0.3, 0.02);
    }
}

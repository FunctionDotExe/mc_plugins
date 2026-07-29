package dev.rbm72.weaponsplugin.stone.stones;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.kit.Props;
import dev.rbm72.weaponsplugin.stone.Stone;
import dev.rbm72.weaponsplugin.util.Grounded;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;

/**
 * Launches the wielder on a real wind charge, thrown by hand at their own feet.
 * <p>
 * The whole stone is the object. A {@code setVelocity} version of this would be two lines and would feel
 * like a scripted launch: fixed height, fixed direction, unaffected by where you are standing. A wind
 * charge is vanilla's own shove — it burst-shapes off the surface it hits, so a charge into a wall skims you
 * along it and one into a corner throws you higher than one in the open, it lifts anything else standing
 * nearby, and it can be heard and seen by everyone rather than by the caster's client. Aiming it is the
 * skill, and the arena geometry is half the input.
 * <p>
 * Mid-air the charge is thrown along your look direction instead of straight down, which turns the same
 * object into an air-brake or a redirect. It arrives tagged through {@link Props} so
 * {@code WeaponPropListener} strips the buttons-and-candles block damage a wind charge would otherwise do.
 */
public final class GustboundStone extends Stone {

    private static final Color GUST_COLOR = Color.fromRGB(225, 240, 235);

    public GustboundStone(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "gustbound_stone";
    }

    @Override
    public Material material() {
        return Material.WIND_CHARGE;
    }

    @Override
    public String displayNameText() {
        return "Gustbound Stone";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Throws a real wind charge, so", NamedTextColor.GRAY),
                Component.text("the launch bounces off whatever", NamedTextColor.GRAY),
                Component.text("you're standing against — and", NamedTextColor.GRAY),
                Component.text("shoves anyone stood beside you.", NamedTextColor.DARK_GRAY));
    }

    @Override
    public boolean hasPersonalAbility() {
        return true;
    }

    @Override
    public String personalAbilityName() {
        return "Updraft";
    }

    @Override
    public List<Component> personalAbilityLore() {
        return List.of(
                Component.text("Grounded: a charge at your feet", NamedTextColor.GRAY),
                Component.text("throws you up and forward.", NamedTextColor.GRAY),
                Component.text("Airborne: it fires where you", NamedTextColor.GRAY),
                Component.text("look, to redirect or brake.", NamedTextColor.GRAY));
    }

    @Override
    public double personalAbilityCooldownSeconds() {
        return configDouble("cooldown-seconds", 6.0);
    }

    private double groundThrowSpeed() {
        return configDouble("ground-throw-speed", 0.9);
    }

    private double airThrowSpeed() {
        return configDouble("air-throw-speed", 1.2);
    }

    @Override
    public void personalAbility(Player player) {
        boolean grounded = Grounded.onGround(player);
        Vector look = player.getLocation().getDirection().normalize();

        // Grounded: fire it down between the feet, so the burst reflects off the floor and lifts. Airborne:
        // fire it along the look axis, so the burst arrives from the side the player is aiming at and pushes
        // them the other way — the same object doing two jobs depending on where it detonates.
        Vector throwAt = grounded
                ? look.clone().multiply(groundThrowSpeed() * 0.3).setY(-groundThrowSpeed())
                : look.clone().multiply(airThrowSpeed());

        Props.windCharge(plugin, id(), player, player.getLocation().clone().add(0, grounded ? 0.1 : 0.6, 0), throwAt);

        Fx.sound(player, Sound.ENTITY_WIND_CHARGE_THROW, 1.0f, grounded ? 0.9f : 1.3f);
        Fx.coloredBurst(player.getLocation().add(0, 0.4, 0), GUST_COLOR, 1.3f, 18, 0.4);
    }

    @Override
    public Component actionBarStatus(Player player, boolean onCooldown, double remainingSeconds) {
        return displayName().append(Component.text(
                onCooldown ? String.format(Locale.ROOT, " %.1fs", remainingSeconds) : " READY",
                onCooldown ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
    }
}

package dev.rbm72.weaponsplugin.stone.stones;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.kit.Props;
import dev.rbm72.weaponsplugin.stone.Stone;
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
 * Throws a real ender pearl, and waives the toll vanilla charges for catching one.
 * <p>
 * A stone that teleported you {@code n} blocks along your look vector would be strictly more reliable than
 * this and strictly less interesting. A pearl is an object in flight: it arcs, so range is a function of how
 * far up you aim; it can be thrown short; it can clip the lip of the ledge you were aiming past and drop you
 * on the near side; and it takes long enough to land that you commit before you know it worked. All of that
 * is vanilla's, for free, and none of it can be reproduced by a teleport call.
 * <p>
 * The one thing overridden is the 5 self-damage on arrival — see {@code RiftstepListener}. A mobility tool
 * that hurts its user every time it is used doesn't read as a cost, it reads as broken, and the cooldown is
 * already where this stone's price is paid.
 */
public final class RiftstepStone extends Stone {

    private static final Color RIFT_COLOR = Color.fromRGB(120, 230, 200);

    public RiftstepStone(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "riftstep_stone";
    }

    @Override
    public Material material() {
        return Material.ENDER_PEARL;
    }

    @Override
    public String displayNameText() {
        return "Riftstep Stone";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Throws a real ender pearl — aim", NamedTextColor.LIGHT_PURPLE),
                Component.text("high for distance, and expect", NamedTextColor.GRAY),
                Component.text("walls to stop it. Costs you none", NamedTextColor.GRAY),
                Component.text("of the usual landing damage.", NamedTextColor.DARK_GRAY));
    }

    @Override
    public boolean hasPersonalAbility() {
        return true;
    }

    @Override
    public String personalAbilityName() {
        return "Riftstep";
    }

    @Override
    public List<Component> personalAbilityLore() {
        return List.of(
                Component.text("Hurl a pearl where you're", NamedTextColor.GRAY),
                Component.text("looking and go where it lands.", NamedTextColor.GRAY),
                Component.text("No pearls needed, and no", NamedTextColor.GRAY),
                Component.text("damage on arrival.", NamedTextColor.GRAY));
    }

    @Override
    public double personalAbilityCooldownSeconds() {
        return configDouble("cooldown-seconds", 12.0);
    }

    /** Throw speed. Vanilla's own hand-thrown pearl is 1.5; higher flies flatter and further. */
    private double throwSpeed() {
        return configDouble("throw-speed", 1.8);
    }

    @Override
    public void personalAbility(Player player) {
        Vector velocity = player.getLocation().getDirection().normalize().multiply(throwSpeed());
        Props.enderPearl(plugin, id(), player, velocity);

        Fx.sound(player, Sound.ENTITY_ENDER_PEARL_THROW, 1.0f, 1.1f);
        Fx.coloredBurst(player.getEyeLocation(), RIFT_COLOR, 1.2f, 16, 0.3);
    }

    @Override
    public Component actionBarStatus(Player player, boolean onCooldown, double remainingSeconds) {
        return displayName().append(Component.text(
                onCooldown ? String.format(Locale.ROOT, " %.1fs", remainingSeconds) : " READY",
                onCooldown ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
    }
}

package dev.rbm72.weaponsplugin.consumable.consumables;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.consumable.Consumable;
import dev.rbm72.weaponsplugin.consumable.Healing;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * The pre-emptive one: a modest heal wrapped in absorption and resistance, worth drinking just
 * before a telegraphed boss slam rather than after it has already taken the health off you.
 */
public final class WardingPoultice extends Consumable {

    private static final Color WARD_GOLD = Color.fromRGB(240, 220, 140);

    public WardingPoultice(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "warding_poultice";
    }

    @Override
    public Material material() {
        return Material.PHANTOM_MEMBRANE;
    }

    @Override
    public String displayNameText() {
        return "Warding Poultice";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Right-click for a small heal,", NamedTextColor.GRAY),
                Component.text("Absorption II, and Resistance.", NamedTextColor.GRAY),
                Component.text("Best drunk before the hit lands.", NamedTextColor.DARK_GRAY));
    }

    @Override
    public int maxCharges() {
        return configInt("max-charges", 2);
    }

    @Override
    public double rechargeSeconds() {
        return configDouble("recharge-seconds", 70.0);
    }

    @Override
    public double useCooldownSeconds() {
        return configDouble("use-cooldown-seconds", 10.0);
    }

    @Override
    public void onUse(Player player) {
        Healing.heal(player, configDouble("heal", 6.0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,
                configInt("absorption-ticks", 600), 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                configInt("resistance-ticks", 160), 0));
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), WARD_GOLD, 1.3f, 28, 0.6);
        Fx.sound(player, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
    }
}

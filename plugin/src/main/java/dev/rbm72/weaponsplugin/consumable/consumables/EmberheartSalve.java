package dev.rbm72.weaponsplugin.consumable.consumables;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.consumable.Consumable;
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
 * Healing over time rather than a lump sum, plus a hard answer to burning. Deliberately the weaker
 * option against a burst of damage and the stronger one against the fire bosses that keep ticking
 * you after the attack has already landed.
 */
public final class EmberheartSalve extends Consumable {

    private static final Color EMBER_ORANGE = Color.fromRGB(255, 140, 60);

    public EmberheartSalve(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "emberheart_salve";
    }

    @Override
    public Material material() {
        return Material.MAGMA_CREAM;
    }

    @Override
    public String displayNameText() {
        return "Emberheart Salve";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Right-click for Regeneration II,", NamedTextColor.GRAY),
                Component.text("Fire Resistance, and an", NamedTextColor.GRAY),
                Component.text("immediate end to any burning.", NamedTextColor.GRAY));
    }

    @Override
    public int maxCharges() {
        return configInt("max-charges", 2);
    }

    @Override
    public double rechargeSeconds() {
        return configDouble("recharge-seconds", 55.0);
    }

    @Override
    public double useCooldownSeconds() {
        return configDouble("use-cooldown-seconds", 8.0);
    }

    @Override
    public void onUse(Player player) {
        int regenTicks = configInt("regen-ticks", 160);
        int fireResistTicks = configInt("fire-resist-ticks", 200);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenTicks, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, fireResistTicks, 0));
        player.setFireTicks(0);
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), EMBER_ORANGE, 1.2f, 24, 0.5);
        Fx.sound(player, Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.3f);
    }
}

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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * The panic button: a huge heal that also strips whatever the boss has stacked on you. One charge
 * and a recharge measured in minutes, so it's the thing you have once per fight and have to pick
 * the right moment for — not part of the healing rotation.
 */
public final class LastLightCharm extends Consumable {

    private static final Color LAST_LIGHT = Color.fromRGB(255, 250, 205);

    public LastLightCharm(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "last_light_charm";
    }

    @Override
    public Material material() {
        return Material.ECHO_SHARD;
    }

    @Override
    public String displayNameText() {
        return "Last Light Charm";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Right-click to restore a large", NamedTextColor.GRAY),
                Component.text("share of your health, gain", NamedTextColor.GRAY),
                Component.text("Regeneration III, and shed every", NamedTextColor.GRAY),
                Component.text("harmful effect on you.", NamedTextColor.GRAY),
                Component.text("One charge. Make it count.", NamedTextColor.DARK_GRAY));
    }

    @Override
    public int maxCharges() {
        return configInt("max-charges", 1);
    }

    @Override
    public double rechargeSeconds() {
        return configDouble("recharge-seconds", 180.0);
    }

    @Override
    public double useCooldownSeconds() {
        return configDouble("use-cooldown-seconds", 30.0);
    }

    @Override
    public void onUse(Player player) {
        Healing.healFraction(player, configDouble("heal-fraction", 0.5));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                configInt("regen-ticks", 120), 2));
        // Strip debuffs only — clearing everything would also wipe the player's own buffs, turning a
        // panic button into a self-dispel of whatever armor set or accessory they just triggered.
        for (PotionEffect effect : List.copyOf(player.getActivePotionEffects())) {
            if (effect.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) {
                player.removePotionEffect(effect.getType());
            }
        }
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), LAST_LIGHT, 1.8f, 40, 0.8);
        Fx.burst(player.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 40, 0.6);
        Fx.sound(player, Sound.ITEM_TOTEM_USE, 0.6f, 1.4f);
    }
}

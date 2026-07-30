package dev.rbm72.weaponsplugin.opitem.opitems;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.opitem.OpItem;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * The panic button: back to full health, every debuff gone, fire and freeze out, hunger full, with a shield of
 * Absorption on top — all on one click, instantly.
 * <p>
 * The one op item that answers a moment rather than granting a state. Everything else on the shelf is drunk
 * before the fight; this is the one you click while a Wither IV stack and a poison and half a health bar of
 * damage are all landing at once, and its whole value is that it resolves all of them in the same tick instead
 * of asking which to fix first.
 * <p>
 * Cleansing is category-driven — every {@link PotionEffectType.Category#HARMFUL} effect goes — so an effect
 * this class has never heard of is still removed. Beneficial effects are deliberately left alone: a rescue that
 * also stripped the operator's own Resistance would be a trap.
 */
public final class SecondWind extends OpItem {

    private static final Color WIND_WHITE = Color.fromRGB(245, 245, 210);

    public SecondWind(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "second_wind";
    }

    @Override
    public Material material() {
        return Material.TOTEM_OF_UNDYING;
    }

    @Override
    public String displayNameText() {
        return "Second Wind";
    }

    @Override
    public NamedTextColor accentColor() {
        return NamedTextColor.WHITE;
    }

    @Override
    public Sound useSound() {
        return Sound.ITEM_TOTEM_USE;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Instantly, all at once:", NamedTextColor.WHITE),
                Component.text("• Health back to full", NamedTextColor.WHITE),
                Component.text("• Every debuff removed", NamedTextColor.WHITE),
                Component.text("• Fire and freezing out", NamedTextColor.WHITE),
                Component.text("• Hunger and saturation full", NamedTextColor.WHITE),
                Component.text("• Absorption " + roman(absorptionAmplifier())
                        + " for " + formatDuration(absorptionTicks()), NamedTextColor.WHITE),
                Component.empty(),
                Component.text("Buffs you already have are kept.", NamedTextColor.GRAY));
    }

    private int absorptionTicks() {
        return Math.max(20, configInt("absorption-ticks", 1200));
    }

    private int absorptionAmplifier() {
        return Math.max(0, configInt("absorption-amplifier", 3));
    }

    @Override
    public boolean onUse(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }

        // Category-driven so an effect added by a future Minecraft version is cleansed too. Beneficial and
        // neutral effects stay: this is a rescue, not a reset.
        for (PotionEffect effect : List.copyOf(player.getActivePotionEffects())) {
            if (effect.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) {
                player.removePotionEffect(effect.getType());
            }
        }

        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, absorptionTicks(),
                absorptionAmplifier(), false, true, true));

        plugin.actionBarHub().flash(player, Component.text("Second Wind", NamedTextColor.WHITE),
                1500, ActionBarHub.PRIORITY_NOTICE);
        Fx.coloredBurst(player.getEyeLocation(), WIND_WHITE, 1.8f, 34, 0.7);
        Fx.point(player.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 25);
        Fx.sound(player, Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.5f);
        return true;
    }
}

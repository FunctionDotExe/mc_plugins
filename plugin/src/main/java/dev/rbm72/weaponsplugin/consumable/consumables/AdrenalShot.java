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
 * A short, sharp damage window — and it costs health to open.
 * <p>
 * The only consumable in the family that takes something. Everything else here is a heal or a ward, which
 * means the whole family reads as "press when hurt"; this one is pressed when the boss is in a stagger and
 * the phase is about to end, and the health it takes is what stops it being free to press at any other time.
 * The cost lands before the buff so it can never kill outright — {@link #onUse} floors the player at 1 heart
 * rather than reducing them below it.
 */
public final class AdrenalShot extends Consumable {

    private static final Color ADRENAL_RED = Color.fromRGB(235, 80, 60);

    public AdrenalShot(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "adrenal_shot";
    }

    @Override
    public Material material() {
        return Material.BLAZE_POWDER;
    }

    @Override
    public String displayNameText() {
        return "Adrenal Shot";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Right-click for Strength, Speed II", NamedTextColor.GRAY),
                Component.text("and Haste II — a short burn window.", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Costs " + String.format("%.0f", healthCost() / 2.0)
                        + " heart" + (healthCost() == 2.0 ? "" : "s") + " to use.", NamedTextColor.RED),
                Component.text("Never kills you — floors at 1 heart.", NamedTextColor.DARK_GRAY));
    }

    @Override
    public int maxCharges() {
        return configInt("max-charges", 2);
    }

    @Override
    public double rechargeSeconds() {
        return configDouble("recharge-seconds", 75.0);
    }

    @Override
    public double useCooldownSeconds() {
        return configDouble("use-cooldown-seconds", 12.0);
    }

    @Override
    public Sound useSound() {
        return Sound.ITEM_HONEY_BOTTLE_DRINK;
    }

    private double healthCost() {
        return Math.max(0.0, configDouble("health-cost", 4.0));
    }

    @Override
    public void onUse(Player player) {
        // setHealth, not damage(): a damage event here would trigger every on-hit rule in the plugin — armor
        // procs, accessory flinch, boss damage tracing — for a cost the player paid to themselves.
        player.setHealth(Math.max(2.0, player.getHealth() - healthCost()));

        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                configInt("strength-ticks", 200), configInt("strength-amplifier", 0)));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                configInt("speed-ticks", 200), 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,
                configInt("haste-ticks", 200), 1));

        Fx.coloredBurst(player.getLocation().add(0, 1, 0), ADRENAL_RED, 1.3f, 28, 0.5);
        Fx.sound(player, Sound.ENTITY_RAVAGER_ROAR, 0.4f, 1.6f);
    }
}

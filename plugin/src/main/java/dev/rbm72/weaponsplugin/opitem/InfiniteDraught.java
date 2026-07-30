package dev.rbm72.weaponsplugin.opitem;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

/**
 * A bottle whose effects never run out — {@link PotionEffect#INFINITE_DURATION}, not a very large number.
 * <p>
 * An unending buff needs an off switch, and it cannot be "wait for it": drinking again with the set already
 * running <em>strips</em> it instead of re-applying, and returns false so the listener leaves the bottle in
 * hand. Toggling off is free; only turning it on spends a bottle. Without that, the only exits from an
 * infinite Resistance IV are death and a bucket of milk, and milk would take everything else with it.
 * <p>
 * "Already running" means every one of this bottle's {@link #infusions()} is present <em>and</em> infinite, so
 * a timed Speed from somewhere else can never be mistaken for this item's doing — and stripping only ever
 * removes types this bottle granted.
 */
public abstract class InfiniteDraught extends PotionOpItem {

    protected InfiniteDraught(WeaponsPlugin plugin) {
        super(plugin);
    }

    /** Accent used for the action-bar flash and the tooltip's effect list. */
    protected abstract NamedTextColor flashColor();

    /** Shown when the draught turns on, e.g. {@code "Eternal"}. */
    protected abstract String flashLabel();

    @Override
    public final boolean onUse(Player player) {
        if (active(player)) {
            for (Infusion infusion : infusions()) {
                player.removePotionEffect(infusion.type());
            }
            plugin.actionBarHub().flash(player,
                    Component.text(flashLabel() + " — lifted", NamedTextColor.GRAY),
                    1500, ActionBarHub.PRIORITY_NOTICE);
            Fx.sound(player, Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.1f);
            return false;
        }

        for (Infusion infusion : infusions()) {
            // ambient=false, particles=true: a buff this large should be visible on the player wearing it,
            // both to them and to everyone else in the fight.
            player.addPotionEffect(new PotionEffect(infusion.type(), PotionEffect.INFINITE_DURATION,
                    amplifier(infusion), false, true, true));
        }

        plugin.actionBarHub().flash(player,
                Component.text(flashLabel() + " — no time limit", flashColor()),
                2000, ActionBarHub.PRIORITY_NOTICE);
        Fx.coloredBurst(player.getEyeLocation(), bottleColor(), 1.8f, 30, 0.6);
        Fx.sound(player, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f);
        return true;
    }

    /** True only if every infusion is present and endless — i.e. this bottle is what put them there. */
    private boolean active(Player player) {
        for (Infusion infusion : infusions()) {
            PotionEffect effect = player.getPotionEffect(infusion.type());
            if (effect == null || !effect.isInfinite()) {
                return false;
            }
        }
        return true;
    }
}

package dev.rbm72.weaponsplugin.items;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Base for the pike family: ability1 fires from vanilla's charge attack, so it is always handed a target.
 * <p>
 * The one thing this exists to settle is that a spear's ability1 has no meaning without something to hit.
 * {@link Weapon#ability1(Player)} is the roster-wide contract and cannot be dropped, but for a spear it is
 * unreachable — {@link Weapon#ability1OnChargeAttack()} routes every cast through
 * {@link dev.rbm72.weaponsplugin.listeners.WeaponChargeListener}, which only runs on a connect. So it is
 * implemented once here as the "nothing to strike" click rather than five times in the pikes, and subclasses
 * implement {@link #ability1(Player, LivingEntity)} instead.
 */
public abstract class SpearWeapon extends Weapon {

    protected SpearWeapon(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public final boolean ability1OnChargeAttack() {
        return true;
    }

    @Override
    public abstract void ability1(Player player, LivingEntity contact);

    @Override
    public final void ability1(Player player) {
        Fx.sound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    /**
     * Blocks per second the wielder must be closing at before the charge attack does anything at all.
     * <p>
     * Vanilla's own gate, not ours: {@code NETHERITE_SPEAR}'s {@code KineticWeapon} carries
     * {@code damageConditions = ofRelativeSpeed(175, 4.6)}, so below this the charge lands no hit, fires no
     * damage event, and ability1 never casts. Sprint is ~5.6 b/s and a walk ~4.3, which is what makes 4.6
     * the sprint check it reads as. Mirrored here only so {@link dev.rbm72.weaponsplugin.ability.SpearChargeTask}
     * can say so on the action bar — changing the key does not move vanilla's threshold, it only moves where
     * the cue flips over, so keep the two in step or the HUD starts lying.
     */
    public double chargeMinSpeed() {
        return configDouble("charge-min-speed", 4.6);
    }

    /** Ticks the charge spends winding up before it can damage — {@code KineticWeapon.delayTicks}, 8 on a spear. */
    public int chargeArmTicks() {
        return configInt("charge-arm-ticks", 8);
    }
}

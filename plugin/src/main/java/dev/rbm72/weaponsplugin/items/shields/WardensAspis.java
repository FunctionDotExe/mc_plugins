package dev.rbm72.weaponsplugin.items.shields;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Shield;

/** Entry-level tank shield: heavy flat block reduction, but a tight parry window that punishes
 *  waiting for the read instead of just holding the block up. */
public final class WardensAspis extends Shield {

    public WardensAspis(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "wardens_aspis";
    }

    @Override
    public String displayNameText() {
        return "Warden's Aspis";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public double blockDamageReduction() {
        return configDouble("block-damage-reduction", 0.55);
    }

    @Override
    public long parryWindowMs() {
        return (long) configDouble("parry-window-ms", 250);
    }

    @Override
    public int parryStunTicks() {
        return (int) configDouble("parry-stun-ticks", 30);
    }
}

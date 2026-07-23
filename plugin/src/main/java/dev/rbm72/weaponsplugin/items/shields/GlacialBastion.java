package dev.rbm72.weaponsplugin.items.shields;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Shield;

/** Frost-forged bulwark: the highest flat block reduction of any shield, with parry timing and
 *  stun both tuned generously to match its legendary weight. */
public final class GlacialBastion extends Shield {

    public GlacialBastion(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "glacial_bastion";
    }

    @Override
    public String displayNameText() {
        return "Glacial Bastion";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double blockDamageReduction() {
        return configDouble("block-damage-reduction", 0.7);
    }

    @Override
    public long parryWindowMs() {
        return (long) configDouble("parry-window-ms", 300);
    }

    @Override
    public int parryStunTicks() {
        return (int) configDouble("parry-stun-ticks", 50);
    }
}

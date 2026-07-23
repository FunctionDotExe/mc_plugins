package dev.rbm72.weaponsplugin.items.shields;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Shield;

/** Necro-forged bone shield: absorbs chip damage better than anything else in the tier, but its
 *  parry window is punishingly tight — a pure tank tool, not a timing check. */
public final class BoneguardAegis extends Shield {

    public BoneguardAegis(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "boneguard_aegis";
    }

    @Override
    public String displayNameText() {
        return "Boneguard Aegis";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double blockDamageReduction() {
        return configDouble("block-damage-reduction", 0.65);
    }

    @Override
    public long parryWindowMs() {
        return (long) configDouble("parry-window-ms", 200);
    }

    @Override
    public int parryStunTicks() {
        return (int) configDouble("parry-stun-ticks", 45);
    }
}

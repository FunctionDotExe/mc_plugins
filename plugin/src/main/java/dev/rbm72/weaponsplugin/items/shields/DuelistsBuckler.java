package dev.rbm72.weaponsplugin.items.shields;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Shield;

/** Skill-reward shield: weak flat block, but a generous parry window and a long stun on a
 *  landed parry — built for reading swings rather than tanking hits. */
public final class DuelistsBuckler extends Shield {

    public DuelistsBuckler(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "duelists_buckler";
    }

    @Override
    public String displayNameText() {
        return "Duelist's Buckler";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double blockDamageReduction() {
        return configDouble("block-damage-reduction", 0.45);
    }

    @Override
    public long parryWindowMs() {
        return (long) configDouble("parry-window-ms", 500);
    }

    @Override
    public int parryStunTicks() {
        return (int) configDouble("parry-stun-ticks", 60);
    }
}

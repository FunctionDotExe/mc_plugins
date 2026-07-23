package dev.rbm72.weaponsplugin.items.shields;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Shield;

/** Endgame shield: no weak side, best-in-slot numbers across block, parry window, and stun alike. */
public final class VoidguardAegis extends Shield {

    public VoidguardAegis(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "voidguard_aegis";
    }

    @Override
    public String displayNameText() {
        return "Voidguard Aegis";
    }

    @Override
    public Rarity rarity() {
        return Rarity.MYTHIC;
    }

    @Override
    public double blockDamageReduction() {
        return configDouble("block-damage-reduction", 0.65);
    }

    @Override
    public long parryWindowMs() {
        return (long) configDouble("parry-window-ms", 450);
    }

    @Override
    public int parryStunTicks() {
        return (int) configDouble("parry-stun-ticks", 80);
    }
}

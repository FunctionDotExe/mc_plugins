package dev.rbm72.weaponsplugin.items.shields;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Shield;

/** Nature-forged bramble shield: heavy block reduction that bites back — a fraction of every
 *  blocked hit's raw damage reflects onto the attacker, thorns and all. */
public final class ThornboundWard extends Shield {

    public ThornboundWard(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "thornbound_ward";
    }

    @Override
    public String displayNameText() {
        return "Thornbound Ward";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double blockDamageReduction() {
        return configDouble("block-damage-reduction", 0.5);
    }

    @Override
    public long parryWindowMs() {
        return (long) configDouble("parry-window-ms", 300);
    }

    @Override
    public int parryStunTicks() {
        return (int) configDouble("parry-stun-ticks", 35);
    }

    @Override
    public double reflectFraction() {
        return configDouble("reflect-fraction", 0.35);
    }
}

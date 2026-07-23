package dev.rbm72.weaponsplugin.items.shields;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Shield;

/** The plugin's first shield: solid block reduction with a generous parry window to learn the timing on. */
public final class GuardianBulwark extends Shield {

    public GuardianBulwark(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "guardian_bulwark";
    }

    @Override
    public String displayNameText() {
        return "Guardian Bulwark";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }
}

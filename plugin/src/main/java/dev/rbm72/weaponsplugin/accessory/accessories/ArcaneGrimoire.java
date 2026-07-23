package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;
import java.util.Set;

/** A living spellbook that resonates with staves and wands: boosts their damage and quickens
 *  their cooldowns, ignoring every other weapon in the arsenal. */
public final class ArcaneGrimoire extends Accessory {

    private static final Set<String> CASTER_WEAPON_IDS = Set.of(
            "arcane_staff", "necromancer_staff", "glacial_scepter");

    public ArcaneGrimoire(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "arcane_grimoire";
    }

    @Override
    public Material material() {
        return Material.ENCHANTED_BOOK;
    }

    @Override
    public String displayNameText() {
        return "Arcane Grimoire";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Its pages rewrite themselves,", NamedTextColor.GRAY),
                Component.text("quietly reshaping the wielder's spells.", NamedTextColor.GRAY),
                Component.text("Resonates with staves and wands.", NamedTextColor.GRAY));
    }

    @Override
    public double damageMultiplier(Weapon weapon) {
        return CASTER_WEAPON_IDS.contains(weapon.id()) ? 1.2 : 1.0;
    }

    @Override
    public double cooldownMultiplier(Weapon weapon) {
        return CASTER_WEAPON_IDS.contains(weapon.id()) ? 0.85 : 1.0;
    }
}

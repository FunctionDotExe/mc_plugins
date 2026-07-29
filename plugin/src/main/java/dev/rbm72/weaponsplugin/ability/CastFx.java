package dev.rbm72.weaponsplugin.ability;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Weapon;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * The "an ability just fired" feedback, shared by every path that can fire one.
 * <p>
 * Extracted from {@code WeaponInteractListener} when spears arrived: a spear casts ability1 from the
 * vanilla lunge rather than from a right-click, so two listeners now start abilities and both have to look
 * and sound identical. A weapon whose cast feedback depends on which listener started it reads as a
 * different weapon.
 */
public final class CastFx {

    private CastFx() {
    }

    /**
     * Cast feedback scaled to how big the ability slot feels: ability1/2/3 get a punchy ring-and-chime,
     * but the ultimate gets a full flash-and-shockwave moment so double-sneak-offhand actually reads as
     * the biggest button on the weapon.
     */
    public static void play(WeaponsPlugin plugin, Player player, Weapon weapon, CooldownManager.Slot slot) {
        var color = weapon.rarity().bukkitColor();
        var loc = player.getLocation().add(0, 0.1, 0);

        if (slot != CooldownManager.Slot.ULTIMATE) {
            Fx.sound(player, weapon.castSound(), 1.0f, 1.0f);
            Fx.coloredRing(loc, color, 0.7f, 0.9, 16, 0);
            return;
        }

        Fx.sound(player, weapon.castSound(), 1.3f, 0.85f);
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.6f);
        Fx.flash(player.getEyeLocation(), 1);
        Fx.coloredRing(loc, color, 1.1f, 1.4, 28, 0);
        Fx.coloredBurst(player.getEyeLocation(), color, 1.4f, 24, 0.6);
        Fx.expandingRings(plugin, loc, weapon.rarity().particle(), 3.0, 4, 3L);
    }
}

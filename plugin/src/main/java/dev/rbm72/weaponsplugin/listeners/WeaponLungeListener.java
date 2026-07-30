package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.WeaponRegistry;
import io.papermc.paper.event.entity.EntityLungeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Raises the vanilla lunge a spear weapon gets on its jab. Nothing else.
 * <p>
 * Lunge is the spear's other mechanic, and it is not the one ability1 rides — {@code EntityLungeEvent} is
 * the {@link org.bukkit.enchantments.Enchantment#LUNGE} enchantment firing its {@code post_piercing_attack}
 * effect, which is the left-click jab shoving the wielder forward through the target. The charge attack
 * (hold right-click) is a separate component entirely and never fires this event; ability1 hangs off that
 * one, in {@link WeaponChargeListener}.
 * <p>
 * So all this handler does is add {@link Weapon#lungePowerBonus()} on top of the enchantment level the item
 * carries. It is the weapon's <em>stat</em>, not its ability: it lands whether or not ability1 is spent, and
 * the event is never cancelled. Vanilla's own conditions still gate the lunge before we are ever called —
 * no vehicle, not gliding, not in water, and 7+ hunger.
 */
public final class WeaponLungeListener implements Listener {

    private final WeaponRegistry registry;

    public WeaponLungeListener(WeaponRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLunge(EntityLungeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Weapon weapon = registry.identify(player.getInventory().getItemInMainHand()).orElse(null);
        if (weapon == null || weapon.lungePowerBonus() <= 0) {
            return;
        }
        event.setLungePower(event.getLungePower() + weapon.lungePowerBonus());
    }
}

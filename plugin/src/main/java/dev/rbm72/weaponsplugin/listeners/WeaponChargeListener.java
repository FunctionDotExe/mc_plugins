package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.CastFx;
import dev.rbm72.weaponsplugin.ability.CooldownManager;
import dev.rbm72.weaponsplugin.ability.CooldownManager.Slot;
import dev.rbm72.weaponsplugin.ability.UltimateChargeManager;
import dev.rbm72.weaponsplugin.ability.WeaponSwitchLock;
import dev.rbm72.weaponsplugin.accessory.AccessoryManager;
import dev.rbm72.weaponsplugin.commands.OpCooldownCommand;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.WeaponRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fires ability1 for the spear family off vanilla's own charge attack.
 * <p>
 * §0.1 asks an ability to be produced by a real Minecraft object rather than dressed in particles, and the
 * spear arrives with the object already built. Holding right-click runs {@code Item.use} into the item's
 * {@code KineticWeapon} component: the spear lowers into an attack position, and anything the player's own
 * momentum carries it through gets stabbed, with the damage scaling off velocity instead of off a swing
 * timer. A spear weapon that bound ability1 to a plain right-click would have to <em>cancel</em> all of that
 * to get its button back — {@link org.bukkit.event.player.PlayerInteractEvent} is exactly the event that
 * denies {@code Item.use} — deleting a real mechanic to make room for a simulated one, which is the
 * violation in the other direction.
 * <p>
 * So the connect is the cast. Three consequences worth stating, because all three are deliberate:
 * <ul>
 *   <li><b>The charge attack always happens.</b> Cooldown, switch lock and the ready chime gate the
 *       <em>ability</em>, never the attack — a spear on cooldown is still a spear. This is why the event is
 *       left uncancelled and unmodified on every path out of here.</li>
 *   <li><b>A miss costs nothing.</b> No connect, no event, no cooldown spent. That is the skill the family
 *       asks for, and it is why the ability is handed the entity it struck rather than made to hunt for
 *       one.</li>
 *   <li><b>One cast per charge, not one per entity.</b> A charge attack sweeps every entity along its line
 *       and fires a separate damage event for each. The cooldown swallows the extras on its own, but the
 *       "not yet" click would still fire once per body, so the tick guard below collapses a whole sweep into
 *       a single attempt.</li>
 * </ul>
 * The gating is the same sequence {@link WeaponInteractListener} runs for ability1, minus the charge-meter
 * check — a charge-gated slot is only ever the ultimate, which still lives on sneak-off-hand.
 */
public final class WeaponChargeListener implements Listener {

    private final WeaponsPlugin plugin;
    private final WeaponRegistry registry;
    private final CooldownManager cooldowns;
    private final AccessoryManager accessories;
    private final OpCooldownCommand opCooldown;
    private final WeaponSwitchLock switchLock;
    private final UltimateChargeManager ultimateCharge;

    /** Last server tick each player resolved a charge connect on, so one sweep casts once. */
    private final Map<UUID, Integer> lastChargeTick = new HashMap<>();

    public WeaponChargeListener(WeaponsPlugin plugin, WeaponRegistry registry, CooldownManager cooldowns,
                                AccessoryManager accessories, OpCooldownCommand opCooldown,
                                WeaponSwitchLock switchLock, UltimateChargeManager ultimateCharge) {
        this.plugin = plugin;
        this.registry = registry;
        this.cooldowns = cooldowns;
        this.accessories = accessories;
        this.opCooldown = opCooldown;
        this.switchLock = switchLock;
        this.ultimateCharge = ultimateCharge;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChargeConnect(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        // Both spear attacks land as DamageType.SPEAR — vanilla routes the jab and the charge through the
        // same LivingEntity.stabAttack — so the damage type alone cannot tell them apart. What can: the
        // charge attack only exists *while* the item is being used, because it is driven from the use tick.
        // A jab is a left-click with no item in use, and never reaches here.
        // The hand check matters because hasActiveItem() is true for anything being used — off-hand food
        // eaten while a pike is held would otherwise read as a charge.
        if (event.getDamageSource().getDamageType() != DamageType.SPEAR
                || !player.hasActiveItem() || player.getActiveItemHand() != EquipmentSlot.HAND) {
            return;
        }

        Weapon weapon = registry.identify(player.getInventory().getItemInMainHand()).orElse(null);
        if (weapon == null || !weapon.ability1OnChargeAttack()) {
            return;
        }

        // The dragon reports one of its hitbox parts as the damaged entity, never the dragon itself.
        Entity damaged = event.getEntity() instanceof ComplexEntityPart part ? part.getParent() : event.getEntity();
        if (!(damaged instanceof LivingEntity victim)) {
            return;
        }

        int tick = Bukkit.getCurrentTick();
        if (lastChargeTick.getOrDefault(player.getUniqueId(), Integer.MIN_VALUE) == tick) {
            return;
        }
        lastChargeTick.put(player.getUniqueId(), tick);

        boolean bypass = opCooldown.hasBypass(player);
        if (!bypass && (switchLock.isLocked(player) || cooldowns.isOnCooldown(player, weapon.id(), Slot.ABILITY1))) {
            // The attack stands; only its payoff is spent. The quiet click is the same "not yet" cue the
            // right-click path gives, so a player learns the weapon the same way either way.
            Fx.sound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            return;
        }

        if (!bypass) {
            cooldowns.start(player, weapon, Slot.ABILITY1,
                    weapon.ability1CooldownSeconds() * accessories.cooldownMultiplier(player, weapon));
        }

        CastFx.play(plugin, player, weapon, CooldownManager.Slot.ABILITY1);
        weapon.ability1(player, victim);
        accessories.onAbilityCast(player, weapon, Slot.ABILITY1);
        ultimateCharge.onAbilityCast(player, weapon, Slot.ABILITY1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastChargeTick.remove(event.getPlayer().getUniqueId());
    }
}

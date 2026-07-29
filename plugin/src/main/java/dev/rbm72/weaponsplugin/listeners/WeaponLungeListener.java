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
import io.papermc.paper.event.entity.EntityLungeEvent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Fires ability1 for the spear family off vanilla's own charged lunge.
 * <p>
 * §0.1 asks an ability to be produced by a real Minecraft object rather than dressed in particles, and the
 * spear arrives with the object already built: a charge-up with its own animation and sound, a release that
 * throws the player forward, and an enchantment ({@link org.bukkit.enchantments.Enchantment#LUNGE}) that
 * scales how far. A spear weapon that bound ability1 to a plain right-click would have to <em>cancel</em>
 * all of that to get its button back — deleting a real mechanic to make room for a simulated one, which is
 * the violation in the other direction.
 * <p>
 * So the release is the cast. Two consequences worth stating, because both are deliberate:
 * <ul>
 *   <li><b>The lunge always happens.</b> Cooldown, switch lock and the ready chime gate the <em>ability</em>,
 *       never the movement — a spear on cooldown is still a spear. This is why the event is left uncancelled
 *       on every path out of here.</li>
 *   <li><b>{@link Weapon#lungePowerBonus()} applies first and unconditionally.</b> It is the weapon's stat,
 *       not its ability, so it lands even when ability1 is spent.</li>
 * </ul>
 * The gating below is the same sequence {@link WeaponInteractListener} runs for ability1, minus the charge
 * check — a charge-gated slot is only ever the ultimate, which still lives on sneak-off-hand.
 */
public final class WeaponLungeListener implements Listener {

    private final WeaponsPlugin plugin;
    private final WeaponRegistry registry;
    private final CooldownManager cooldowns;
    private final AccessoryManager accessories;
    private final OpCooldownCommand opCooldown;
    private final WeaponSwitchLock switchLock;
    private final UltimateChargeManager ultimateCharge;

    public WeaponLungeListener(WeaponsPlugin plugin, WeaponRegistry registry, CooldownManager cooldowns,
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

    @EventHandler(ignoreCancelled = true)
    public void onLunge(EntityLungeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Weapon weapon = registry.identify(player.getInventory().getItemInMainHand()).orElse(null);
        if (weapon == null || !weapon.ability1OnLunge()) {
            return;
        }

        if (weapon.lungePowerBonus() > 0) {
            event.setLungePower(event.getLungePower() + weapon.lungePowerBonus());
        }

        boolean bypass = opCooldown.hasBypass(player);
        if (!bypass && (switchLock.isLocked(player) || cooldowns.isOnCooldown(player, weapon.id(), Slot.ABILITY1))) {
            // The lunge stands; only its payoff is spent. The quiet click is the same "not yet" cue the
            // right-click path gives, so a player learns the weapon the same way either way.
            Fx.sound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            return;
        }

        if (!bypass) {
            cooldowns.start(player, weapon, Slot.ABILITY1,
                    weapon.ability1CooldownSeconds() * accessories.cooldownMultiplier(player, weapon));
        }

        CastFx.play(plugin, player, weapon, Slot.ABILITY1);
        weapon.ability1(player);
        accessories.onAbilityCast(player, weapon, Slot.ABILITY1);
        ultimateCharge.onAbilityCast(player, weapon, Slot.ABILITY1);
    }
}

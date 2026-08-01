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
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Makes the bow family's basic attack exist, and fires ability1 off the shot that results.
 * <p>
 * This is {@link WeaponChargeListener}'s argument transplanted one material over. A bow's right-click is
 * already a mechanic — draw, hold, release, with power scaling off draw time — and
 * {@link WeaponInteractListener} used to cancel that click to claim ability1 for itself. The cost was not
 * subtle: a custom bow could cast three abilities and could not fire an arrow, so once those three were
 * on cooldown the player was holding a stick. §0.1 is explicit that the real object comes first, and here
 * the real object is the nocked arrow.
 * <p>
 * Two jobs, and they are separate on purpose:
 * <ul>
 *   <li><b>Ammo.</b> A drop that stops working when the quiver runs dry is worse than the vanilla bow it
 *       replaces. Bows carry Infinity from {@code Weapon#createItem}, so they need exactly one arrow
 *       present and never spend it; crossbows ignore Infinity in vanilla and are topped back up here on
 *       every draw. Either way the reserve arrow is granted at draw time rather than handed out with the
 *       weapon, so it cannot go missing between sessions.</li>
 *   <li><b>The cast.</b> Release fires ability1 through the same cooldown / switch-lock / {@link CastFx}
 *       path every other slot uses. The <em>shot</em> is never gated — a bow on cooldown is still a bow,
 *       exactly as a spear on cooldown still charges — so the quiet "not yet" click is the only thing a
 *       player loses by loosing early.</li>
 * </ul>
 */
public final class WeaponBowListener implements Listener {

    /** Kept at this count while a custom crossbow is drawn, so a burst of shots never runs it dry mid-fight. */
    private static final int CROSSBOW_RESERVE = 16;

    private final WeaponsPlugin plugin;
    private final WeaponRegistry registry;
    private final CooldownManager cooldowns;
    private final AccessoryManager accessories;
    private final OpCooldownCommand opCooldown;
    private final WeaponSwitchLock switchLock;
    private final UltimateChargeManager ultimateCharge;

    public WeaponBowListener(WeaponsPlugin plugin, WeaponRegistry registry, CooldownManager cooldowns,
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

    /**
     * Tops the reserve up before vanilla's own ammo check runs. Deliberately at HIGH rather than MONITOR:
     * the arrow has to be in the inventory by the time the draw is resolved, and MONITOR is too late.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDraw(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        Weapon weapon = registry.identify(player.getInventory().getItemInMainHand()).orElse(null);
        if (weapon == null || !weapon.ability1OnBowShot()) {
            return;
        }
        // A bow's single Infinity-protected arrow is never spent, so it only ever needs topping up from
        // zero. A crossbow really does consume them, hence the deeper reserve.
        int wanted = weapon.material() == Material.CROSSBOW ? CROSSBOW_RESERVE : 1;
        int held = countArrows(player);
        if (held < wanted) {
            player.getInventory().addItem(new ItemStack(Material.ARROW, wanted - held));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Weapon weapon = registry.identify(event.getBow()).orElse(null);
        if (weapon == null || !weapon.ability1OnBowShot()) {
            return;
        }

        boolean bypass = opCooldown.hasBypass(player);
        if (!bypass && (switchLock.isLocked(player) || cooldowns.isOnCooldown(player, weapon.id(), Slot.ABILITY1))) {
            // The arrow still flies — only its payoff is spent. Same "not yet" cue every other slot gives.
            Fx.sound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            return;
        }

        if (!bypass) {
            cooldowns.start(player, weapon, Slot.ABILITY1,
                    weapon.ability1CooldownSeconds() * accessories.cooldownMultiplier(player, weapon));
        }

        CastFx.play(plugin, player, weapon, CooldownManager.Slot.ABILITY1);
        weapon.ability1(player);
        accessories.onAbilityCast(player, weapon, Slot.ABILITY1);
        ultimateCharge.onAbilityCast(player, weapon, Slot.ABILITY1);
    }

    private static int countArrows(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == Material.ARROW) {
                total += stack.getAmount();
            }
        }
        return total;
    }
}

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
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Routes right-click to one of four ability slots based on which hand
 * holds the weapon and whether the player is sneaking:
 * main+no-sneak = ability1, main+sneak = ability2,
 * off-hand+no-sneak = ability3, off-hand+sneak = ultimate.
 */
public final class WeaponInteractListener implements Listener {

    private final WeaponsPlugin plugin;
    private final WeaponRegistry registry;
    private final CooldownManager cooldowns;
    private final AccessoryManager accessories;
    private final OpCooldownCommand opCooldown;
    private final WeaponSwitchLock switchLock;
    private final UltimateChargeManager ultimateCharge;

    public WeaponInteractListener(WeaponsPlugin plugin, WeaponRegistry registry, CooldownManager cooldowns,
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

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        boolean offHand = event.getHand() == EquipmentSlot.OFF_HAND;
        ItemStack heldItem = offHand
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();

        Weapon weapon = registry.identify(heldItem).orElse(null);
        if (weapon == null) {
            return;
        }

        boolean sneaking = player.isSneaking();

        // A spear's main-hand right-click belongs to the game: holding it charges the lunge, releasing it
        // performs one, and ability1 fires from that release in WeaponLungeListener. Cancelling here would
        // deny the item use and the weapon would never charge at all — the ability would be unreachable.
        if (!offHand && !sneaking && weapon.ability1OnLunge()) {
            return;
        }

        event.setCancelled(true);

        Slot slot;
        double durationSeconds;
        if (!offHand && !sneaking) {
            slot = Slot.ABILITY1;
            durationSeconds = weapon.ability1CooldownSeconds();
        } else if (!offHand) {
            slot = Slot.ABILITY2;
            durationSeconds = weapon.ability2CooldownSeconds();
        } else if (!sneaking) {
            slot = Slot.ABILITY3;
            durationSeconds = weapon.ability3CooldownSeconds();
        } else {
            slot = Slot.ULTIMATE;
            durationSeconds = weapon.ultimateCooldownSeconds();
        }

        boolean bypass = opCooldown.hasBypass(player);

        if (!bypass && switchLock.isLocked(player)) {
            Fx.sound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            return;
        }

        if (!bypass && cooldowns.isOnCooldown(player, weapon.id(), slot)) {
            Fx.sound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            return;
        }

        // A charge-gated ultimate is checked and spent before anything else fires, and told about
        // clearly when it is not ready. Without the notice, an unearned ultimate is a right-click that
        // plays a click and does nothing — indistinguishable from a broken weapon.
        boolean chargedUltimate = slot == Slot.ULTIMATE && weapon.ultimateChargeSpec() != null;
        if (!bypass && chargedUltimate && !ultimateCharge.canFire(player, weapon)) {
            Fx.sound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            plugin.actionBarHub().flash(player, ultimateCharge.notReadyNotice(player, weapon),
                    1500, ActionBarHub.PRIORITY_NOTICE);
            return;
        }
        if (!bypass && chargedUltimate) {
            ultimateCharge.spend(player, weapon);
        }

        if (!bypass) {
            // A charged ultimate still takes its floor through the normal cooldown path, so the HUD,
            // the durability meter and the ready chime all keep working exactly as they do elsewhere.
            double gate = chargedUltimate ? weapon.ultimateChargeSpec().cooldownFloorSeconds() : durationSeconds;
            cooldowns.start(player, weapon, slot, gate * accessories.cooldownMultiplier(player, weapon));
        }
        CastFx.play(plugin, player, weapon, slot);
        if (chargedUltimate) {
            ultimateCharge.onSpentFx(player, weapon);
        }

        switch (slot) {
            case ABILITY1 -> weapon.ability1(player);
            case ABILITY2 -> weapon.ability2(player);
            case ABILITY3 -> weapon.ability3(player);
            case ULTIMATE -> weapon.ultimate(player);
        }

        accessories.onAbilityCast(player, weapon, slot);
        ultimateCharge.onAbilityCast(player, weapon, slot);
    }
}

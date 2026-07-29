package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
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

        event.setCancelled(true);

        boolean sneaking = player.isSneaking();
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
        castFx(player, weapon, slot);
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

    /**
     * Cast feedback scaled to how big the ability slot feels: ability1/2/3 get the same punchy
     * ring-and-chime that's always been there, but the ultimate gets a full flash-and-shockwave
     * moment so double-sneak-offhand actually reads as the biggest button on the weapon.
     */
    private void castFx(Player player, Weapon weapon, Slot slot) {
        var color = weapon.rarity().bukkitColor();
        var loc = player.getLocation().add(0, 0.1, 0);

        if (slot != Slot.ULTIMATE) {
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

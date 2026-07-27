package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.BossManager;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ridable.Ridable;
import dev.rbm72.weaponsplugin.ridable.RidableManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

/**
 * Handles the whole ride lifecycle: right-clicking a live mob with the matching saddle equipped
 * tames and mounts it ({@link RidableManager#mount}); dismounting (shift-key, death, disconnect)
 * untames it again; a plain right-click while mounted fires the mount's active ability instead of
 * whatever's in the rider's hand.
 */
public final class RidableMountListener implements Listener {

    private final RidableManager manager;
    private final BossManager bossManager;

    public RidableMountListener(WeaponsPlugin plugin) {
        this.manager = plugin.ridableManager();
        this.bossManager = plugin.bossManager();
    }

    /** A live boss's entity type can coincidentally match a ridable's (e.g. Voidwyrm is an ender dragon) — never treat it as tamable or immune-granting. */
    private boolean isLiveBoss(Entity entity) {
        return bossManager.instanceForDamaged(entity).isPresent();
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isInsideVehicle()) {
            // While mounted, right-clicking almost always lands back on the mount's own hitbox
            // (its body/head is right under the crosshair) rather than reaching PlayerInteractEvent
            // at all — that's the ability trigger, not a re-mount attempt.
            if (!manager.mountedRidable(player).isEmpty()) {
                event.setCancelled(true);
                if (!manager.triggerAbility(player)) {
                    Fx.sound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
                }
            }
            return;
        }

        // Right-clicking an Ender Dragon hits one of its hitbox parts (head/body/wing), not the
        // dragon entity itself — resolve back to the real ComplexLivingEntity in that case.
        Entity clicked = event.getRightClicked();
        LivingEntity target;
        if (clicked instanceof ComplexEntityPart part) {
            if (!(part.getParent() instanceof LivingEntity parent)) {
                return;
            }
            target = parent;
        } else if (clicked instanceof LivingEntity living) {
            target = living;
        } else {
            return;
        }

        if (isLiveBoss(target)) {
            return;
        }
        Optional<Ridable> equipped = manager.equipped(player);
        if (equipped.isEmpty() || target.getType() != equipped.get().targetEntityType()) {
            return;
        }
        event.setCancelled(true);

        if (manager.isTamedByOther(target, player)) {
            player.sendMessage(Component.text("Someone else already tamed that one.", NamedTextColor.RED));
            return;
        }

        manager.mount(player, target, equipped.get());
        player.sendMessage(Component.text("You tame and mount the ", NamedTextColor.GREEN)
                .append(Component.text(target.getType().toString().toLowerCase(java.util.Locale.ROOT).replace('_', ' '))));
        player.playSound(player.getLocation(), Sound.ENTITY_HORSE_SADDLE, 1.0f, 1.0f);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && event.getDismounted() instanceof LivingEntity mount) {
            manager.dismount(player, mount);
        }
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof LivingEntity living && manager.isAnyonesMount(living)) {
            event.setCancelled(true);
            return;
        }
        // Blanket immunity: while a player has a saddle equipped, that mob type can't target them
        // at all — otherwise walking up to tame a live Wither/Ravager gets you killed first. Never
        // applies to a live boss, even one whose entity type happens to match (Voidwyrm is an ender
        // dragon, Threefold Bane is a wither) — a boss fight must never be trivialized by a saddle.
        if (event.getTarget() instanceof Player targetPlayer && matchesEquippedType(targetPlayer, event.getEntityType())
                && !isLiveBoss(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Entity damager = event.getDamager();
        Entity source = damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
                ? shooter
                : damager;
        if (matchesEquippedType(victim, source.getType()) && !isLiveBoss(source)) {
            event.setCancelled(true);
        }
    }

    private boolean matchesEquippedType(Player player, EntityType type) {
        Optional<Ridable> equipped = manager.equipped(player);
        return equipped.isPresent() && equipped.get().targetEntityType() == type;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (manager.mountedRidable(player).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (!manager.triggerAbility(player)) {
            Fx.sound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
        }
    }
}

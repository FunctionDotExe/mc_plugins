package dev.rbm72.weaponsplugin.boss.gates;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Escort gate: the boss hides behind live bodies rather than scenery. It is untouchable while any of
 * its guard is standing, and the guard are armed, aggressive fighters that chase and hit back — not
 * breakable props that sit still. Cut them all down inside the timer and the boss is left staggered
 * and wide open; let the timer run and it heals a chunk, blasts everyone back, and calls a fresh
 * detachment.
 * <p>
 * The ask is target-switching under pressure: your damage has to leave the boss and go somewhere
 * else while the boss itself keeps attacking you. That is a different muscle from breaking static
 * weak points, which never fight back, and from a timing gate, which never asks you to move your
 * damage at all.
 */
public final class AddCullGate implements PhaseMechanic {

    private final BossInstance instance;
    private final WeaponsPlugin plugin;
    private final Component label;
    private final Component guardName;
    private final Color color;
    private final EntityType guardType;
    private final Material weapon;
    private final int guardCount;
    private final double guardHealth;
    private final int initialDelayTicks;
    private final int timeoutTicks;
    private final double failHeal;
    private final double failKnockback;
    private final double exposedMultiplier;
    private final int exposedDurationTicks;
    private final int staggerTicks;
    private final int recallDelayTicks;

    private final List<LivingEntity> guards = new ArrayList<>();
    private BukkitTask pendingTask;
    private BukkitTask watchTask;
    private boolean stopped;

    public AddCullGate(BossInstance instance, Component label, Component guardName, Color color,
                        EntityType guardType, Material weapon, int guardCount, double guardHealth,
                        int initialDelayTicks, int timeoutTicks, double failHeal, double failKnockback,
                        double exposedMultiplier, int exposedDurationTicks, int staggerTicks,
                        int recallDelayTicks) {
        this.instance = instance;
        this.plugin = instance.plugin();
        this.label = label;
        this.guardName = guardName;
        this.color = color;
        this.guardType = guardType;
        this.weapon = weapon;
        this.guardCount = guardCount;
        this.guardHealth = guardHealth;
        this.initialDelayTicks = initialDelayTicks;
        this.timeoutTicks = timeoutTicks;
        this.failHeal = failHeal;
        this.failKnockback = failKnockback;
        this.exposedMultiplier = exposedMultiplier;
        this.exposedDurationTicks = exposedDurationTicks;
        this.staggerTicks = staggerTicks;
        this.recallDelayTicks = recallDelayTicks;
    }

    @Override
    public void start() {
        instance.setDamageMultiplier(1.0);
        schedule(this::summon, Math.max(1, initialDelayTicks));
    }

    @Override
    public void stop() {
        stopped = true;
        cancel(pendingTask);
        pendingTask = null;
        cancel(watchTask);
        watchTask = null;
        clearGuards();
        instance.setDamageMultiplier(1.0);
        instance.setForcedInvulnerable(false);
        if (instance.entity().isValid()) {
            instance.entity().setGlowing(false);
        }
    }

    private void summon() {
        if (stopped || !instance.entity().isValid()) {
            return;
        }
        clearGuards();
        instance.setDamageMultiplier(0.0);
        instance.setForcedInvulnerable(true);

        Location center = instance.entity().getLocation();
        Player focus = Arena.combatants(center, instance.arena().radius()).stream().findFirst().orElse(null);
        for (int i = 0; i < guardCount; i++) {
            double angle = 2 * Math.PI * i / guardCount;
            Location spot = center.clone().add(Math.cos(angle) * 2.8, 0, Math.sin(angle) * 2.8);
            Fx.coloredBurst(spot.clone().add(0, 1, 0), color, 1.4f, 20, 0.4);
            LivingEntity guard = instance.addManager().spawn(spot.getWorld(), spot, guardType, entity -> {
                entity.customName(guardName);
                entity.setCustomNameVisible(true);
                var maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealthAttr != null) {
                    maxHealthAttr.setBaseValue(guardHealth);
                    entity.setHealth(guardHealth);
                }
                EntityEquipment equipment = entity.getEquipment();
                if (equipment != null && weapon != null) {
                    equipment.setItemInMainHand(new ItemStack(weapon));
                    equipment.setItemInMainHandDropChance(0f);
                }
                if (entity instanceof Mob mob && focus != null) {
                    mob.setTarget(focus);
                }
            });
            if (guard != null) {
                guards.add(guard);
            }
        }

        Fx.sound(center, Sound.ENTITY_VINDICATOR_AMBIENT, 1.1f, 0.7f);
        instance.showTitle(
                label.color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("Cut them down before they reform it", NamedTextColor.GRAY));

        watch();
    }

    private void watch() {
        cancel(watchTask);
        watchTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks;

            @Override
            public void run() {
                if (stopped || !instance.entity().isValid()) {
                    cancel(watchTask);
                    watchTask = null;
                    return;
                }
                boolean anyAlive = guards.stream().anyMatch(g -> g.isValid() && !g.isDead());
                if (!anyAlive) {
                    cancel(watchTask);
                    watchTask = null;
                    onCleared();
                    return;
                }
                if (ticks >= timeoutTicks) {
                    cancel(watchTask);
                    watchTask = null;
                    onTimedOut();
                    return;
                }
                ticks++;
            }
        }, 0L, 1L);
        instance.trackTask(watchTask);
    }

    private void onCleared() {
        instance.setForcedInvulnerable(false);
        instance.recordExposure();
        instance.setDamageMultiplier(exposedMultiplier);
        instance.stagger(staggerTicks);

        Location loc = instance.entity().getLocation();
        instance.entity().setGlowing(true);
        Fx.coloredBurst(loc.clone().add(0, 1.2, 0), color, 2.2f, 50, 0.8);
        Fx.flash(loc.clone().add(0, 1.2, 0), 2);
        Fx.sound(loc, Sound.ITEM_SHIELD_BREAK, 1.2f, 1.2f);
        instance.showTitle(
                Component.text("GUARD BROKEN", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("It stands undefended", NamedTextColor.GRAY));

        schedule(() -> {
            if (instance.entity().isValid()) {
                instance.entity().setGlowing(false);
            }
            summon();
        }, Math.max(1, exposedDurationTicks + recallDelayTicks));
    }

    private void onTimedOut() {
        clearGuards();
        instance.setForcedInvulnerable(false);
        // Stays sealed through the recall gap: letting the timer lapse must not be a cheaper route to
        // an open boss than actually killing the guard.
        instance.setDamageMultiplier(0.0);

        Location loc = instance.entity().getLocation();
        var maxHealthAttr = instance.entity().getAttribute(Attribute.MAX_HEALTH);
        double cap = maxHealthAttr != null ? maxHealthAttr.getValue() : instance.entity().getHealth();
        instance.entity().setHealth(Math.min(cap, instance.entity().getHealth() + failHeal));
        Fx.coloredBurst(loc.clone().add(0, 1, 0), color, 2.0f, 45, 0.7);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.9f);
        for (Player player : Arena.combatants(loc, instance.arena().radius())) {
            Vector push = player.getLocation().toVector().subtract(loc.toVector());
            push = push.lengthSquared() > 1.0E-6 ? push.normalize() : new Vector(1, 0, 0);
            player.setVelocity(player.getVelocity().add(push.multiply(failKnockback).setY(0.3)));
        }
        instance.showTitle(
                Component.text("THE GUARD HOLDS", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                Component.text("It draws strength from them", NamedTextColor.GRAY));

        schedule(this::summon, Math.max(1, recallDelayTicks));
    }

    private void clearGuards() {
        for (LivingEntity guard : guards) {
            if (guard.isValid()) {
                guard.remove();
            }
        }
        guards.clear();
    }

    private void schedule(Runnable action, int delayTicks) {
        cancel(pendingTask);
        pendingTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingTask = null;
            action.run();
        }, delayTicks);
        instance.trackTask(pendingTask);
    }

    private static void cancel(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}

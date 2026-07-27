package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Attendants: the boss is hittable from the first second to the last, and never stops being hittable.
 * What its retinue does instead is <em>scale</em> the fight — every living attendant heals it on an
 * interval and shaves a slice off all incoming damage. At a full retinue it out-heals a group
 * outright; with the retinue dead it takes everything you have.
 * <p>
 * The point is that this is a genuine choice rather than a chore, and both answers can be correct.
 * Burn the attendants and you spend a long stretch dealing the boss no damage at all; ignore them and
 * race, and a group with enough output can simply out-damage the healing. Nothing is ever locked, so
 * players are deciding how to spend their damage rather than waiting for permission to spend it —
 * which is the exact opposite of what a gate does.
 */
public final class EmpoweringAddsMechanic implements PhaseMechanic {

    private static final long NOTICE_MS = 1500;
    private static final long TICK_INTERVAL = 20L;

    private final BossInstance instance;
    private final WeaponsPlugin plugin;
    private final Component attendantName;
    private final Color color;
    private final EntityType attendantType;
    private final int attendantCount;
    private final double attendantHealth;
    private final double healPerAttendant;
    private final double damageReductionPerAttendant;
    private final int respawnDelayTicks;

    private final List<LivingEntity> attendants = new ArrayList<>();
    private BukkitTask task;
    private int ticksSinceEmpty;
    private boolean stopped;

    public EmpoweringAddsMechanic(BossInstance instance, Component attendantName, Color color,
                                   EntityType attendantType, int attendantCount, double attendantHealth,
                                   double healPerAttendant, double damageReductionPerAttendant,
                                   int respawnDelayTicks) {
        this.instance = instance;
        this.plugin = instance.plugin();
        this.attendantName = attendantName;
        this.color = color;
        this.attendantType = attendantType;
        this.attendantCount = attendantCount;
        this.attendantHealth = attendantHealth;
        this.healPerAttendant = healPerAttendant;
        this.damageReductionPerAttendant = damageReductionPerAttendant;
        this.respawnDelayTicks = respawnDelayTicks;
    }

    @Override
    public void start() {
        instance.setDamageMultiplier(1.0);
        summon();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pulse, TICK_INTERVAL, TICK_INTERVAL);
        instance.trackTask(task);
    }

    @Override
    public void stop() {
        stopped = true;
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = null;
        clearAttendants();
        instance.setDamageMultiplier(1.0);
    }

    /**
     * Each living attendant shaves a slice off the hit. Floored at 10% rather than 0 so a full retinue
     * is a brutal soft wall and never a hard one — the race is always technically available.
     */
    @Override
    public double filterDamage(Player attacker, double damage) {
        int alive = aliveCount();
        if (alive <= 0) {
            return damage;
        }
        double retained = Math.max(0.10, 1.0 - alive * damageReductionPerAttendant);
        if (instance.signalDeflectReady()) {
            plugin.actionBarHub().flash(attacker,
                    Component.text(alive + " attendant(s) still shield it", NamedTextColor.GOLD),
                    NOTICE_MS, ActionBarHub.PRIORITY_NOTICE);
        }
        return damage * retained;
    }

    @Override
    public void onBossDamaged(Player attacker, double damageDealt) {
        // Hurting it while the retinue is down is exactly the window this phase is built around.
        if (damageDealt > 0 && aliveCount() == 0) {
            instance.recordExposure();
        }
    }

    private void pulse() {
        if (stopped || !instance.entity().isValid()) {
            return;
        }
        int alive = aliveCount();
        if (alive == 0) {
            ticksSinceEmpty += TICK_INTERVAL;
            if (respawnDelayTicks > 0 && ticksSinceEmpty >= respawnDelayTicks) {
                ticksSinceEmpty = 0;
                summon();
            }
            return;
        }
        ticksSinceEmpty = 0;

        var maxHealthAttr = instance.entity().getAttribute(Attribute.MAX_HEALTH);
        double cap = maxHealthAttr != null ? maxHealthAttr.getValue() : instance.entity().getHealth();
        double healed = Math.min(cap, instance.entity().getHealth() + healPerAttendant * alive);
        instance.entity().setHealth(healed);

        Location bossLoc = instance.entity().getLocation().add(0, 1.2, 0);
        for (LivingEntity attendant : attendants) {
            if (attendant.isValid() && !attendant.isDead()) {
                Fx.line(attendant.getLocation().add(0, 1, 0), bossLoc, Particle.HEART, 8);
            }
        }
    }

    private void summon() {
        clearAttendants();
        Location center = instance.entity().getLocation();
        Player focus = Arena.combatants(center, instance.arena().radius()).stream().findFirst().orElse(null);
        for (int i = 0; i < attendantCount; i++) {
            double angle = 2 * Math.PI * i / attendantCount;
            Location spot = center.clone().add(Math.cos(angle) * 4.0, 0, Math.sin(angle) * 4.0);
            Fx.coloredBurst(spot.clone().add(0, 1, 0), color, 1.4f, 18, 0.4);
            LivingEntity attendant = instance.addManager().spawn(spot.getWorld(), spot, attendantType, entity -> {
                entity.customName(attendantName);
                entity.setCustomNameVisible(true);
                var attr = entity.getAttribute(Attribute.MAX_HEALTH);
                if (attr != null) {
                    attr.setBaseValue(attendantHealth);
                    entity.setHealth(attendantHealth);
                }
                if (entity instanceof Mob mob && focus != null) {
                    mob.setTarget(focus);
                }
            });
            if (attendant != null) {
                attendants.add(attendant);
            }
        }
        Fx.sound(center, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.0f, 0.9f);
        instance.showTitle(
                Component.text("THE RETINUE ATTENDS", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("They mend it faster than you can cut — or do they?", NamedTextColor.GRAY));
    }

    private int aliveCount() {
        int alive = 0;
        for (LivingEntity attendant : attendants) {
            if (attendant.isValid() && !attendant.isDead()) {
                alive++;
            }
        }
        return alive;
    }

    private void clearAttendants() {
        for (LivingEntity attendant : attendants) {
            if (attendant.isValid()) {
                attendant.remove();
            }
        }
        attendants.clear();
    }
}

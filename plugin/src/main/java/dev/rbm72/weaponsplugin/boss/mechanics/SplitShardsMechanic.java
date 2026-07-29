package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The boss comes apart, and it wants to come back together. Every shard killed inside the window is
 * mass it never gets back; every shard still standing when the window closes walks home and heals it.
 * <p>
 * What makes this different from an escort-clearing gate is that partial success is real and
 * measured. There is no binary "did you clear it" — killing three of five shards is meaningfully
 * better than killing two, and the group can see the arithmetic running while it fights. That turns
 * the phase into a target-priority problem (spread out and clip them all, or focus the nearest few)
 * rather than a chore with a pass mark.
 * <p>
 * The boss is heavily blunted while split rather than immune, so the phase can never hard-lock, and
 * a group that simply ignores the shards still ends the fight — just much later, against a boss that
 * has healed itself several times over.
 */
public final class SplitShardsMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;
    /** Damage the core still takes while split. Never zero — see Pitfall 2. */
    private static final double SPLIT_DAMAGE_RETAINED = 0.2;

    private final String label;
    private final String shardName;
    private final Color color;
    private final EntityType shardType;
    private final int shardCount;
    private final double shardHealth;
    private final int windowTicks;
    private final int regroupDelayTicks;
    private final double healPerSurvivor;
    private final double exposedMultiplier;
    private final int exposedTicks;
    private final int staggerTicks;
    private final double spawnRadius;

    private final List<LivingEntity> shards = new ArrayList<>();
    private int windowLeft;
    private int regroupCountdown;
    private int exposedLeft;

    public SplitShardsMechanic(BossInstance instance, String label, String shardName, Color color,
                                EntityType shardType, int shardCount, double shardHealth, int windowTicks,
                                int regroupDelayTicks, double healPerSurvivor, double exposedMultiplier,
                                int exposedTicks, int staggerTicks, double spawnRadius) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.shardName = shardName;
        this.color = color;
        this.shardType = shardType;
        this.shardCount = Math.max(2, shardCount);
        this.shardHealth = Math.max(4.0, shardHealth);
        this.windowTicks = Math.max(60, windowTicks);
        this.regroupDelayTicks = Math.max(40, regroupDelayTicks);
        this.healPerSurvivor = healPerSurvivor;
        this.exposedMultiplier = exposedMultiplier;
        this.exposedTicks = Math.max(20, exposedTicks);
        this.staggerTicks = staggerTicks;
        this.spawnRadius = spawnRadius;
    }

    @Override
    protected void onStart() {
        instance.showTitle(
                Component.text(label, NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("Kill what it sheds before it takes the mass back", NamedTextColor.GRAY));
        split();
    }

    @Override
    protected void onStop() {
        clearShards();
    }

    @Override
    public double filterDamage(Player attacker, double damage) {
        if (stopped || aliveShards() == 0) {
            return damage;
        }
        if (instance.signalDeflectReady()) {
            plugin.actionBarHub().flash(attacker,
                    Component.text(aliveShards() + " shard(s) still carry its mass", NamedTextColor.GREEN),
                    1200, ActionBarHub.PRIORITY_NOTICE);
        }
        return damage * SPLIT_DAMAGE_RETAINED;
    }

    @Override
    protected void tick() {
        if (exposedLeft > 0) {
            exposedLeft -= TICK_INTERVAL;
            if (exposedLeft <= 0) {
                instance.setDamageMultiplier(1.0);
                if (instance.entity().isValid()) {
                    instance.entity().setGlowing(false);
                }
                regroupCountdown = regroupDelayTicks;
            }
            showBars();
            return;
        }

        if (shards.isEmpty()) {
            regroupCountdown -= TICK_INTERVAL;
            if (regroupCountdown <= 0) {
                split();
            }
            showBars();
            return;
        }

        // Retire the dead so the count on screen is always the count in the room.
        shards.removeIf(shard -> !shard.isValid() || shard.isDead());

        if (shards.isEmpty()) {
            fullySevered();
            return;
        }

        if (elapsedTicks % 6 == 0) {
            Location hub = instance.entity().getLocation().add(0, 1.2, 0);
            for (LivingEntity shard : shards) {
                Fx.line(shard.getLocation().add(0, 0.8, 0), hub, org.bukkit.Particle.ITEM_SLIME, 6);
            }
        }

        windowLeft -= TICK_INTERVAL;
        if (windowLeft <= 0) {
            reabsorb();
        }
        showBars();
    }

    private void showBars() {
        if (exposedLeft > 0) {
            Component text = Component.text("FULLY SHED  ", NamedTextColor.GREEN)
                    .append(Component.text(Math.max(0, exposedLeft / 20) + "s wide open", NamedTextColor.WHITE));
            instance.mechanicBar().updateShared(instance.barViewers(), text,
                    exposedLeft / (double) exposedTicks, BossBar.Color.GREEN);
            return;
        }
        if (shards.isEmpty()) {
            Component text = Component.text(label + "  ", NamedTextColor.GREEN)
                    .append(Component.text("splitting again in " + Math.max(0, regroupCountdown / 20) + "s",
                            NamedTextColor.GRAY));
            instance.mechanicBar().updateShared(instance.barViewers(), text, 0.0, BossBar.Color.WHITE);
            return;
        }
        double progress = Math.max(0.0, windowLeft / (double) windowTicks);
        Component text = Component.text(label + "  ", NamedTextColor.GREEN)
                .append(Component.text(shards.size() + " shard(s) left", NamedTextColor.WHITE))
                .append(Component.text("   " + Math.max(0, windowLeft / 20) + "s before it reabsorbs",
                        windowLeft < windowTicks * 0.3 ? NamedTextColor.RED : NamedTextColor.GRAY));
        instance.mechanicBar().updateShared(instance.barViewers(), text, progress,
                windowLeft < windowTicks * 0.3 ? BossBar.Color.RED : BossBar.Color.GREEN);
    }

    private void fullySevered() {
        clearShards();
        instance.recordExposure();
        instance.setDamageMultiplier(exposedMultiplier);
        instance.stagger(staggerTicks);
        exposedLeft = exposedTicks;

        Location at = instance.entity().getLocation();
        if (instance.entity().isValid()) {
            instance.entity().setGlowing(true);
        }
        Fx.coloredBurst(at.clone().add(0, 1.2, 0), color, 2.6f, 70, 1.0);
        Fx.flash(at.clone().add(0, 1.2, 0), 2);
        Fx.sound(at, Sound.ENTITY_SLIME_SQUISH, 1.5f, 0.6f);
        instance.showTitle(
                Component.text("NOTHING LEFT TO TAKE BACK", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("It is thin — hit it now", NamedTextColor.GRAY));
    }

    /** The window closed with shards alive: each one walks home and gives its mass back. */
    private void reabsorb() {
        int survivors = aliveShards();
        Location at = instance.entity().getLocation();
        for (LivingEntity shard : shards) {
            if (shard.isValid()) {
                Fx.line(shard.getLocation().add(0, 0.8, 0), at.clone().add(0, 1.2, 0),
                        org.bukkit.Particle.ITEM_SLIME, 12);
            }
        }
        clearShards();

        if (survivors > 0 && healPerSurvivor > 0 && instance.entity().isValid()) {
            var attr = instance.entity().getAttribute(Attribute.MAX_HEALTH);
            double cap = attr != null ? attr.getValue() : instance.entity().getHealth();
            instance.entity().setHealth(Math.min(cap, instance.entity().getHealth() + healPerSurvivor * survivors));
        }
        Fx.coloredBurst(at.clone().add(0, 1.2, 0), color, 2.4f, 60, 0.9);
        Fx.sound(at, Sound.ENTITY_SLIME_JUMP, 1.4f, 0.5f);
        instance.showTitle(
                Component.text("REABSORBED", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text(survivors + " shard(s) walked home", NamedTextColor.GRAY));
        // Deliberately no exposure credit: letting it reabsorb must never be worth more than killing
        // the shards (Pitfall 3).
        regroupCountdown = regroupDelayTicks;
    }

    private void split() {
        clearShards();
        windowLeft = windowTicks;
        Location centre = instance.entity().getLocation();
        for (int i = 0; i < shardCount; i++) {
            double angle = 2 * Math.PI * i / shardCount;
            Location spot = centre.clone().add(Math.cos(angle) * spawnRadius, 0, Math.sin(angle) * spawnRadius);
            Fx.coloredBurst(spot.clone().add(0, 0.8, 0), color, 1.6f, 22, 0.4);
            LivingEntity shard = instance.addManager().spawn(spot.getWorld(), spot, shardType, mob -> {
                mob.customName(Component.text(shardName, NamedTextColor.GREEN));
                mob.setCustomNameVisible(true);
                mob.setRemoveWhenFarAway(false);
                var attr = mob.getAttribute(Attribute.MAX_HEALTH);
                if (attr != null) {
                    attr.setBaseValue(shardHealth);
                    mob.setHealth(shardHealth);
                }
                if (mob instanceof Mob m) {
                    m.setTarget(combatants().stream().findFirst().orElse(null));
                }
            });
            if (shard != null) {
                shards.add(shard);
            }
        }
        // If nothing actually spawned (no world, a blocked location) the empty-shard branch in tick()
        // would call straight back in here every pulse. Back off instead of spinning.
        if (shards.isEmpty()) {
            regroupCountdown = regroupDelayTicks;
            return;
        }
        Fx.sound(centre, Sound.ENTITY_SLIME_SQUISH, 1.4f, 0.8f);
    }

    private int aliveShards() {
        int alive = 0;
        for (LivingEntity shard : shards) {
            if (shard.isValid() && !shard.isDead()) {
                alive++;
            }
        }
        return alive;
    }

    private void clearShards() {
        for (LivingEntity shard : shards) {
            if (shard.isValid()) {
                shard.remove();
            }
        }
        shards.clear();
    }
}

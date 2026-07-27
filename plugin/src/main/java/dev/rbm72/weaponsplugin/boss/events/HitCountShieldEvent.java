package dev.rbm72.weaponsplugin.boss.events;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Shield check: the boss throws up an absorbing shield that ignores damage entirely and counts
 * <em>hits</em>. Land enough distinct blows before the timer expires and it shatters, leaving the boss
 * staggered; miss the count and it detonates the shield into the arena and heals off what it absorbed.
 * <p>
 * The reason this exists is that every other system in the plugin rewards exactly one thing — damage
 * per swing. A shield counting hits inverts that in a single stroke: the greatsword that carries the
 * rest of the fight is actively the wrong tool here, and a fast weapon, a bow, or anything that throws
 * lots of small strikes suddenly matters. It is the cheapest possible way to make a player's gear
 * choice interesting, and it costs the boss nothing thematically.
 * <p>
 * Hits are counted per player per {@link #HIT_COOLDOWN_MS} so a multi-hit AoE ability cannot clear the
 * whole bar in one frame, and so the count reflects sustained pressure rather than a single lucky
 * proc. Every hit is loudly telegraphed with a shrinking counter so the group can see the race.
 */
public final class HitCountShieldEvent extends BossEvent {

    /** Minimum gap between two counted hits from the same player — stops one AoE proc clearing the bar. */
    private static final long HIT_COOLDOWN_MS = 150;
    private static final long NOTICE_MS = 1200;

    private static final Color SHIELD_BLUE = Color.fromRGB(120, 200, 255);

    private final double[] triggers;
    private final int hitsRequired;
    private final int durationTicks;
    private final double failDamage;
    private final double failHeal;
    private final int staggerTicks;

    private BukkitTask task;
    private int hits;
    private boolean resolved;
    /**
     * Held as state rather than threaded through each outcome: the shield can end from the timer or
     * from a hit landing in {@link #onHit}, and both paths must resume the fight. Missing it on the
     * success path would leave the boss stuck mid-event forever.
     */
    private Runnable onComplete;
    private final java.util.Map<java.util.UUID, Long> lastHitAtMs = new java.util.HashMap<>();

    public HitCountShieldEvent(WeaponsPlugin plugin, String bossId, double[] triggers) {
        super(plugin, bossId);
        this.triggers = triggers.clone();
        this.hitsRequired = configInt("shield-hits-required", 30);
        this.durationTicks = configInt("shield-duration-ticks", 160);
        this.failDamage = configDouble("shield-fail-damage", 18.0);
        this.failHeal = configDouble("shield-fail-heal", 60.0);
        this.staggerTicks = configInt("shield-stagger-ticks", 80);
    }

    @Override
    public String id() {
        return "hit_shield";
    }

    @Override
    public double[] triggerFractions() {
        return triggers.clone();
    }

    @Override
    public void run(BossInstance instance, Runnable onComplete) {
        hits = 0;
        resolved = false;
        this.onComplete = onComplete;
        lastHitAtMs.clear();

        instance.setForcedInvulnerable(true);
        Location loc = instance.entity().getLocation();
        instance.entity().setGlowing(true);
        Fx.coloredBurst(loc.clone().add(0, 1.4, 0), SHIELD_BLUE, 2.4f, 60, 0.9);
        Fx.sound(loc, Sound.ITEM_SHIELD_BLOCK, 1.4f, 0.5f);
        instance.showTitle(
                Component.text("ABSORBING SHIELD", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                Component.text(hitsRequired + " hits — damage will not help you", NamedTextColor.GRAY));

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks;

            @Override
            public void run() {
                if (resolved) {
                    return;
                }
                if (!instance.entity().isValid()) {
                    finish(instance);
                    return;
                }
                Location at = instance.entity().getLocation();
                Fx.coloredRing(at.clone().add(0, 1.2, 0), SHIELD_BLUE, 1.2f,
                        2.2 * (1.0 - hits / (double) hitsRequired) + 0.8, 24, ticks * 0.2);
                if (ticks % 4 == 0) {
                    showCounter(instance, durationTicks - ticks);
                }
                ticks++;
                if (ticks >= durationTicks) {
                    fail(instance);
                }
            }
        }, 1L, 1L);
        instance.trackTask(task);
    }

    @Override
    public void onHit(BossInstance instance, Player attacker, double damageDealt) {
        if (resolved) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastHitAtMs.get(attacker.getUniqueId());
        if (last != null && now - last < HIT_COOLDOWN_MS) {
            return;
        }
        lastHitAtMs.put(attacker.getUniqueId(), now);

        hits++;
        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1.4, 0), SHIELD_BLUE, 0.9f, 8, 0.4);
        Fx.sound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.0f + 0.8f * (hits / (float) hitsRequired));

        if (hits >= hitsRequired) {
            succeed(instance);
        }
    }

    /**
     * Shown on the dedicated mechanic bar rather than the action bar. This event is a race against a
     * visible counter, and on the action bar that counter was being suppressed for seconds at a time
     * by cast bars and routine combat notices — which left players unable to tell what the shield
     * wanted or whether they were making progress against it.
     */
    private void showCounter(BossInstance instance, int ticksLeft) {
        Component text = Component.text("ABSORBING SHIELD  ", NamedTextColor.AQUA)
                .append(Component.text(hits + " / " + hitsRequired + " hits", NamedTextColor.WHITE))
                .append(Component.text("   " + Math.max(0, ticksLeft / 20) + "s left", NamedTextColor.GRAY))
                .append(Component.text("   damage does nothing - land fast hits", NamedTextColor.DARK_GRAY));
        instance.mechanicBar().updateShared(MechanicBar.Owner.EVENT, instance.barViewers(), text,
                hits / (double) hitsRequired, BossBar.Color.BLUE);
    }

    private void succeed(BossInstance instance) {
        if (resolved) {
            return;
        }
        resolved = true;
        instance.setForcedInvulnerable(false);
        instance.recordExposure();
        instance.stagger(staggerTicks);

        Location loc = instance.entity().getLocation();
        instance.entity().setGlowing(false);
        Fx.coloredBurst(loc.clone().add(0, 1.4, 0), SHIELD_BLUE, 2.6f, 70, 1.0);
        Fx.flash(loc.clone().add(0, 1.4, 0), 2);
        Fx.sound(loc, Sound.ITEM_SHIELD_BREAK, 1.4f, 1.1f);
        instance.showTitle(
                Component.text("SHIELD SHATTERED", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("It reels — punish it", NamedTextColor.GRAY));
        // Resume immediately; the stagger above is what gives the group their punish window, so the
        // event has no reason to keep holding the boss hostage once the shield is down.
        finish(instance);
    }

    private void fail(BossInstance instance) {
        if (resolved) {
            return;
        }
        resolved = true;
        instance.setForcedInvulnerable(false);
        if (instance.entity().isValid()) {
            instance.entity().setGlowing(false);
            var attr = instance.entity().getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            double cap = attr != null ? attr.getValue() : instance.entity().getHealth();
            instance.entity().setHealth(Math.min(cap, instance.entity().getHealth() + failHeal));

            Location loc = instance.entity().getLocation();
            Fx.coloredBurst(loc.clone().add(0, 1.2, 0), SHIELD_BLUE, 2.6f, 70, 1.2);
            Fx.expandingRings(plugin, loc, org.bukkit.Particle.SOUL_FIRE_FLAME, 10.0, 3, 2L);
            Fx.sound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.7f);
            for (Player player : Arena.combatants(loc, instance.arena().radius())) {
                player.damage(failDamage, instance.entity());
            }
            instance.showTitle(
                    Component.text("SHIELD HELD", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                    Component.text("It feeds on what it absorbed", NamedTextColor.GRAY));
        }
        finish(instance);
    }

    /** Ends the event exactly once, whichever way it went, and always resumes the fight. */
    private void finish(BossInstance instance) {
        cleanup(instance);
        Runnable resume = onComplete;
        onComplete = null;
        if (resume != null) {
            resume.run();
        }
    }

    @Override
    public void cleanup(BossInstance instance) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = null;
        instance.mechanicBar().release(MechanicBar.Owner.EVENT);
        instance.setForcedInvulnerable(false);
        if (instance.entity().isValid()) {
            instance.entity().setGlowing(false);
        }
    }
}

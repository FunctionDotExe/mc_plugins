package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.bossbar.BossBar;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Several bound bodies, one life between them. Hurting any single one of them achieves nothing at
 * all: the pool only drops by however much damage <em>all</em> of them have taken since the last
 * drain, so a group that focuses one target makes literally no progress no matter how hard it hits.
 * <p>
 * This is the roster's one true coordination mechanic. Everything else can be brute-forced by the
 * strongest player in the group carrying the damage; this cannot, because the binding measures the
 * <em>weakest</em> stream of damage, not the total. It is also why it is worth building once and
 * parameterising rather than four times: linked wraiths, a hydra's heads and a split ooze are all
 * this rule with different art.
 * <p>
 * The boss is never fully immune while the binding holds — damage to it is heavily reduced, not
 * blocked, so a group that cannot solve the split still finishes the fight eventually rather than
 * standing in an unwinnable room. Breaking the binding cracks the boss wide open for a real window.
 */
public final class SharedPoolMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;
    /** Damage taken while the binding stands. Low enough to matter, never zero (Pitfall 2). */
    private static final double BOUND_DAMAGE_RETAINED = 0.15;
    /** Ceiling on the "ticks since this one was hit" counter, so it can never overflow in a long fight. */
    private static final int STALE_TICKS = 20 * 60 * 10;

    private final String label;
    private final String boundName;
    private final Color color;
    private final EntityType boundType;
    private final int boundCount;
    private final double poolHealth;
    private final double boundDisplayHealth;
    private final int syncWindowTicks;
    private final int exposedTicks;
    private final double exposedMultiplier;
    private final int staggerTicks;
    private final int respawnDelayTicks;
    private final double failHealPerSurvivor;
    private final double spawnRadius;

    private final List<Bound> bound = new ArrayList<>();
    private double pool;
    private int respawnCountdown;
    private int exposedLeft;

    private static final class Bound {
        final LivingEntity entity;
        double lastHealth;
        /** Damage this one has absorbed since the last drain — the binding drains by the smallest. */
        double banked;
        int ticksSinceHit = STALE_TICKS;

        Bound(LivingEntity entity, double health) {
            this.entity = entity;
            this.lastHealth = health;
        }
    }

    public SharedPoolMechanic(BossInstance instance, String label, String boundName, Color color,
                               EntityType boundType, int boundCount, double poolHealth,
                               double boundDisplayHealth, int syncWindowTicks, int exposedTicks,
                               double exposedMultiplier, int staggerTicks, int respawnDelayTicks,
                               double failHealPerSurvivor, double spawnRadius) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.boundName = boundName;
        this.color = color;
        this.boundType = boundType;
        this.boundCount = Math.max(2, boundCount);
        this.poolHealth = Math.max(1.0, poolHealth);
        this.boundDisplayHealth = Math.max(10.0, boundDisplayHealth);
        this.syncWindowTicks = Math.max(20, syncWindowTicks);
        this.exposedTicks = Math.max(20, exposedTicks);
        this.exposedMultiplier = exposedMultiplier;
        this.staggerTicks = staggerTicks;
        this.respawnDelayTicks = Math.max(40, respawnDelayTicks);
        this.failHealPerSurvivor = failHealPerSurvivor;
        this.spawnRadius = spawnRadius;
    }

    @Override
    protected void onStart() {
        instance.showTitle(
                Component.text(label, NamedTextColor.DARK_AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("One life between them — they must all bleed at once", NamedTextColor.GRAY));
        summon();
    }

    @Override
    protected void onStop() {
        clearBound();
    }

    /**
     * The binding is a rule about damage, not a wall: everything still lands, just heavily blunted,
     * so a group that never solves the split still gets there in the end.
     */
    @Override
    public double filterDamage(Player attacker, double damage) {
        if (stopped || bound.isEmpty()) {
            return damage;
        }
        if (instance.signalDeflectReady()) {
            plugin.actionBarHub().flash(attacker,
                    Component.text("The binding drinks your blow — strike them together", NamedTextColor.DARK_AQUA),
                    1200, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
        return damage * BOUND_DAMAGE_RETAINED;
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
                respawnCountdown = respawnDelayTicks;
            }
            showBars();
            return;
        }

        if (bound.isEmpty()) {
            respawnCountdown -= TICK_INTERVAL;
            if (respawnCountdown <= 0) {
                summon();
            }
            showBars();
            return;
        }

        pollDamage();
        drainIfSynced();
        drawTethers();
        showBars();
    }

    /**
     * Reads each bound body's health, banks whatever was taken off it, and puts it straight back.
     * Polling rather than an event listener keeps the whole mechanic inside this class — and healing
     * them back is what makes the binding real: they cannot be killed one at a time, only drained
     * together.
     */
    private void pollDamage() {
        for (Bound b : bound) {
            b.ticksSinceHit = Math.min(STALE_TICKS, b.ticksSinceHit + (int) TICK_INTERVAL);
            if (!b.entity.isValid() || b.entity.isDead()) {
                continue;
            }
            double delta = b.lastHealth - b.entity.getHealth();
            if (delta > 0.01) {
                b.banked += delta;
                b.ticksSinceHit = 0;
                b.entity.setHealth(boundDisplayHealth);
                Fx.coloredBurst(b.entity.getLocation().add(0, 1.2, 0), color, 1.0f, 8, 0.3);
            }
            b.lastHealth = b.entity.getHealth();
        }
    }

    /**
     * The pool drops by the smallest bank across every bound body — i.e. only by damage that every
     * one of them shared. Focusing a single target banks a huge number against one and drains nothing.
     */
    private void drainIfSynced() {
        boolean allAlive = bound.stream().allMatch(b -> b.entity.isValid() && !b.entity.isDead());
        if (!allAlive) {
            // Something despawned out from under us — rebuild rather than deadlock.
            clearBound();
            respawnCountdown = respawnDelayTicks;
            return;
        }

        double smallest = Double.MAX_VALUE;
        boolean allRecentlyHit = true;
        for (Bound b : bound) {
            smallest = Math.min(smallest, b.banked);
            if (b.ticksSinceHit > syncWindowTicks) {
                allRecentlyHit = false;
            }
        }
        if (!allRecentlyHit || smallest <= 0.01) {
            // Banks bleed off slowly so a group cannot bank one target for a minute and cash it in.
            for (Bound b : bound) {
                if (b.ticksSinceHit > syncWindowTicks) {
                    b.banked = Math.max(0.0, b.banked - 0.5);
                }
            }
            return;
        }

        pool -= smallest;
        for (Bound b : bound) {
            b.banked -= smallest;
        }
        instance.recordExposure();
        Location at = instance.entity().getLocation();
        Fx.coloredBurst(at.clone().add(0, 1.4, 0), color, 1.4f, 16, 0.5);
        Fx.sound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.4f);

        if (pool <= 0) {
            sever();
        }
    }

    private void drawTethers() {
        if (elapsedTicks % 4 != 0) {
            return;
        }
        Location hub = instance.entity().getLocation().add(0, 1.4, 0);
        for (Bound b : bound) {
            if (b.entity.isValid()) {
                Fx.line(b.entity.getLocation().add(0, 1.2, 0), hub, Particle.SCULK_SOUL, 8);
            }
        }
    }

    private void showBars() {
        if (exposedLeft > 0) {
            Component text = Component.text("BINDING SEVERED  ", NamedTextColor.GREEN)
                    .append(Component.text(Math.max(0, exposedLeft / 20) + "s wide open", NamedTextColor.WHITE));
            instance.mechanicBar().updateShared(instance.barViewers(), text,
                    exposedLeft / (double) exposedTicks, BossBar.Color.GREEN);
            return;
        }
        if (bound.isEmpty()) {
            Component text = Component.text(label + "  ", NamedTextColor.DARK_AQUA)
                    .append(Component.text("re-binding in " + Math.max(0, respawnCountdown / 20) + "s",
                            NamedTextColor.GRAY));
            instance.mechanicBar().updateShared(instance.barViewers(), text, 0.0, BossBar.Color.WHITE);
            return;
        }
        double fraction = Math.max(0.0, pool / poolHealth);
        long lagging = bound.stream().filter(b -> b.ticksSinceHit > syncWindowTicks).count();
        Component text = Component.text(label + "  ", NamedTextColor.DARK_AQUA)
                .append(Component.text("shared life " + (int) Math.ceil(Math.max(0, pool)), NamedTextColor.WHITE))
                .append(lagging > 0
                        ? Component.text("   " + lagging + " untouched — hit them ALL", NamedTextColor.RED)
                        : Component.text("   all bleeding — keep it up", NamedTextColor.GREEN));
        instance.mechanicBar().updateShared(instance.barViewers(), text, fraction,
                lagging > 0 ? BossBar.Color.RED : BossBar.Color.BLUE);
    }

    private void sever() {
        clearBound();
        instance.recordExposure();
        instance.setDamageMultiplier(exposedMultiplier);
        instance.stagger(staggerTicks);
        exposedLeft = exposedTicks;

        Location at = instance.entity().getLocation();
        if (instance.entity().isValid()) {
            instance.entity().setGlowing(true);
        }
        Fx.coloredBurst(at.clone().add(0, 1.4, 0), color, 2.6f, 70, 1.0);
        Fx.flash(at.clone().add(0, 1.4, 0), 2);
        Fx.sound(at, Sound.ENTITY_ELDER_GUARDIAN_HURT, 1.4f, 0.7f);
        instance.showTitle(
                Component.text("THE BINDING BREAKS", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("It has nothing left to hide behind", NamedTextColor.GRAY));
    }

    private void summon() {
        clearBound();
        pool = poolHealth;
        Location centre = instance.entity().getLocation();
        for (int i = 0; i < boundCount; i++) {
            double angle = 2 * Math.PI * i / boundCount;
            Location spot = centre.clone().add(Math.cos(angle) * spawnRadius, 0, Math.sin(angle) * spawnRadius);
            Fx.coloredBurst(spot.clone().add(0, 1, 0), color, 1.6f, 20, 0.4);
            final int index = i;
            LivingEntity entity = instance.addManager().spawn(spot.getWorld(), spot, boundType, mob -> {
                mob.customName(Component.text(boundName + " " + (index + 1), NamedTextColor.DARK_AQUA));
                mob.setCustomNameVisible(true);
                mob.setRemoveWhenFarAway(false);
                var attr = mob.getAttribute(Attribute.MAX_HEALTH);
                if (attr != null) {
                    attr.setBaseValue(boundDisplayHealth);
                    mob.setHealth(boundDisplayHealth);
                }
                if (mob instanceof Mob m) {
                    m.setTarget(combatants().stream().findFirst().orElse(null));
                }
            });
            if (entity != null) {
                bound.add(new Bound(entity, entity.getHealth()));
            }
        }
        // Nothing spawned (no world, a blocked location): the empty branch in tick() would call
        // straight back in here every pulse, so back off instead of spinning.
        if (bound.isEmpty()) {
            respawnCountdown = respawnDelayTicks;
            return;
        }
        Fx.sound(centre, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.2f, 0.7f);
    }

    /** Survivors of an unfinished binding feed the boss on their way out — failure must never be free. */
    private void clearBound() {
        int survivors = 0;
        for (Bound b : bound) {
            if (b.entity.isValid()) {
                survivors++;
                b.entity.remove();
            }
        }
        bound.clear();
        if (survivors > 0 && failHealPerSurvivor > 0 && pool > 0 && instance.entity().isValid() && !stopped) {
            var attr = instance.entity().getAttribute(Attribute.MAX_HEALTH);
            double cap = attr != null ? attr.getValue() : instance.entity().getHealth();
            instance.entity().setHealth(Math.min(cap,
                    instance.entity().getHealth() + failHealPerSurvivor * survivors));
        }
    }
}

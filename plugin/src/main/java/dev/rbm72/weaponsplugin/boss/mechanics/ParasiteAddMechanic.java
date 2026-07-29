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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Something detaches from the boss and starts feeding it. While it lives the boss is healing; if it
 * survives its window it walks home and hands over a much larger meal at once.
 * <p>
 * The boss stays fully hittable the whole time, which is the point: this is a pure resource race, not
 * a gate. The group is choosing where to spend damage — into the boss, which the parasite is partly
 * undoing, or into the parasite, which costs tempo but stops the bleeding. Both answers can be right
 * depending on how much output the group has, and the arithmetic is visible while they decide.
 * <p>
 * Two shapes come out of the same class. Untethered, it is a limb that broke off and has to be put
 * down before it reattaches. Tethered, it latches onto one specific player and drains <em>them</em>
 * while it feeds, which drags that player out of position and makes the rest of the group come to
 * where they are rather than the other way round.
 */
public final class ParasiteAddMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 4L;

    private final String label;
    private final String parasiteName;
    private final Color color;
    private final EntityType parasiteType;
    private final double parasiteHealth;
    private final boolean tetherToPlayer;
    private final int windowTicks;
    private final int respawnDelayTicks;
    private final double bossHealPerSecond;
    private final double tetherDrainPerSecond;
    private final double reattachHeal;
    private final double reattachHardening;
    private final int exposedTicks;
    private final double exposedMultiplier;

    private LivingEntity parasite;
    private Player tethered;
    private int windowLeft;
    private int respawnCountdown;
    private int exposedLeft;

    public ParasiteAddMechanic(BossInstance instance, String label, String parasiteName, Color color,
                                EntityType parasiteType, double parasiteHealth, boolean tetherToPlayer,
                                int windowTicks, int respawnDelayTicks, double bossHealPerSecond,
                                double tetherDrainPerSecond, double reattachHeal, double reattachHardening,
                                int exposedTicks, double exposedMultiplier) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.parasiteName = parasiteName;
        this.color = color;
        this.parasiteType = parasiteType;
        this.parasiteHealth = Math.max(4.0, parasiteHealth);
        this.tetherToPlayer = tetherToPlayer;
        this.windowTicks = Math.max(60, windowTicks);
        this.respawnDelayTicks = Math.max(40, respawnDelayTicks);
        this.bossHealPerSecond = bossHealPerSecond;
        this.tetherDrainPerSecond = tetherDrainPerSecond;
        this.reattachHeal = reattachHeal;
        this.reattachHardening = reattachHardening;
        this.exposedTicks = Math.max(20, exposedTicks);
        this.exposedMultiplier = exposedMultiplier;
    }

    @Override
    protected void onStart() {
        respawnCountdown = 40;
        instance.showTitle(
                Component.text(label, NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("Put it down before it feeds", NamedTextColor.GRAY));
    }

    @Override
    protected void onStop() {
        clearParasite();
        tethered = null;
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

        if (parasite == null || !parasite.isValid() || parasite.isDead()) {
            if (parasite != null) {
                // Killed inside its window: the heal stops and the boss is left open.
                parasite = null;
                tethered = null;
                killed();
                return;
            }
            respawnCountdown -= TICK_INTERVAL;
            if (respawnCountdown <= 0) {
                detach();
            }
            showBars();
            return;
        }

        feed();
        drain();
        drawTether();

        windowLeft -= TICK_INTERVAL;
        if (windowLeft <= 0) {
            reattach();
        }
        showBars();
    }

    private void feed() {
        if (bossHealPerSecond <= 0 || elapsedTicks % 20 != 0 || !instance.entity().isValid()) {
            return;
        }
        var attr = instance.entity().getAttribute(Attribute.MAX_HEALTH);
        double cap = attr != null ? attr.getValue() : instance.entity().getHealth();
        instance.entity().setHealth(Math.min(cap, instance.entity().getHealth() + bossHealPerSecond));
    }

    private void drain() {
        if (!tetherToPlayer || tethered == null || tetherDrainPerSecond <= 0) {
            return;
        }
        if (!tethered.isOnline() || !tethered.isValid() || tethered.isDead()) {
            tethered = pickTarget();
            return;
        }
        if (elapsedTicks % 20 == 0) {
            tickHurt(tethered, tetherDrainPerSecond);
            Fx.coloredBurst(tethered.getLocation().add(0, 1.2, 0), color, 1.2f, 10, 0.3);
        }
        if (parasite instanceof Mob mob) {
            mob.setTarget(tethered);
        }
    }

    private void drawTether() {
        if (elapsedTicks % 4 != 0 || parasite == null || !parasite.isValid()) {
            return;
        }
        Location from = parasite.getLocation().add(0, 1.0, 0);
        Fx.line(from, instance.entity().getLocation().add(0, 1.4, 0), Particle.SCULK_SOUL, 10);
        if (tethered != null && tethered.isValid()) {
            Fx.line(from, tethered.getLocation().add(0, 1.2, 0), Particle.SOUL, 8);
        }
    }

    private void showBars() {
        if (exposedLeft > 0) {
            Component text = Component.text("IT BLEEDS FREELY  ", NamedTextColor.GREEN)
                    .append(Component.text(Math.max(0, exposedLeft / 20) + "s", NamedTextColor.WHITE));
            instance.mechanicBar().updateShared(instance.barViewers(), text,
                    exposedLeft / (double) exposedTicks, BossBar.Color.GREEN);
            return;
        }
        if (parasite == null) {
            Component text = Component.text(label + "  ", NamedTextColor.DARK_GREEN)
                    .append(Component.text("next in " + Math.max(0, respawnCountdown / 20) + "s", NamedTextColor.GRAY));
            instance.mechanicBar().updateShared(instance.barViewers(), text, 0.0, BossBar.Color.WHITE);
            return;
        }
        double progress = Math.max(0.0, windowLeft / (double) windowTicks);
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            boolean isTarget = tethered != null && viewer.equals(tethered);
            Component text = Component.text(label + "  ", NamedTextColor.DARK_GREEN)
                    .append(isTarget
                            ? Component.text("IT IS ON YOU — keep it in the open", NamedTextColor.RED)
                            : Component.text("kill it", NamedTextColor.WHITE))
                    .append(Component.text("   " + Math.max(0, windowLeft / 20) + "s before it reattaches",
                            windowLeft < windowTicks * 0.3 ? NamedTextColor.RED : NamedTextColor.GRAY));
            return MechanicBar.Readout.of(text, progress,
                    windowLeft < windowTicks * 0.3 ? BossBar.Color.RED : BossBar.Color.GREEN);
        });
    }

    private void detach() {
        Location centre = instance.entity().getLocation();
        Location spot = centre.clone().add(
                ThreadLocalRandom.current().nextDouble(-3.0, 3.0), 0,
                ThreadLocalRandom.current().nextDouble(-3.0, 3.0));
        tethered = tetherToPlayer ? pickTarget() : null;
        windowLeft = windowTicks;
        // Armed up front rather than only on the outcome paths: if the spawn below fails, the
        // no-parasite branch in tick() would otherwise call straight back in here every pulse.
        respawnCountdown = respawnDelayTicks;

        Fx.coloredBurst(spot.clone().add(0, 1, 0), color, 2.0f, 34, 0.5);
        Fx.sound(centre, Sound.ENTITY_WITHER_HURT, 1.1f, 1.3f);

        parasite = instance.addManager().spawn(spot.getWorld(), spot, parasiteType, mob -> {
            mob.customName(Component.text(parasiteName, NamedTextColor.DARK_GREEN));
            mob.setCustomNameVisible(true);
            mob.setRemoveWhenFarAway(false);
            var attr = mob.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(parasiteHealth);
                mob.setHealth(parasiteHealth);
            }
            if (mob instanceof Mob m) {
                m.setTarget(tethered != null ? tethered : combatants().stream().findFirst().orElse(null));
            }
        });
        instance.showTitle(
                Component.text(label, NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                tetherToPlayer && tethered != null
                        ? Component.text("It has latched onto " + tethered.getName(), NamedTextColor.GRAY)
                        : Component.text("It broke away — put it down", NamedTextColor.GRAY));
    }

    private Player pickTarget() {
        List<Player> present = combatants();
        if (present.isEmpty()) {
            return null;
        }
        return present.get(ThreadLocalRandom.current().nextInt(present.size()));
    }

    private void killed() {
        instance.recordExposure();
        instance.setDamageMultiplier(exposedMultiplier);
        exposedLeft = exposedTicks;
        Location at = instance.entity().getLocation();
        if (instance.entity().isValid()) {
            instance.entity().setGlowing(true);
        }
        Fx.coloredBurst(at.clone().add(0, 1.2, 0), color, 2.2f, 50, 0.8);
        Fx.sound(at, Sound.ENTITY_WITHER_HURT, 1.3f, 0.6f);
        instance.showTitle(
                Component.text("CUT OFF", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("It stops feeding", NamedTextColor.GRAY));
    }

    /** Survived its window: a large heal all at once, and on the late roster a lasting hardening. */
    private void reattach() {
        Location at = instance.entity().getLocation();
        if (parasite != null && parasite.isValid()) {
            Fx.line(parasite.getLocation().add(0, 1, 0), at.clone().add(0, 1.4, 0), Particle.HEART, 12);
        }
        clearParasite();
        tethered = null;

        if (instance.entity().isValid() && reattachHeal > 0) {
            var attr = instance.entity().getAttribute(Attribute.MAX_HEALTH);
            double cap = attr != null ? attr.getValue() : instance.entity().getHealth();
            instance.entity().setHealth(Math.min(cap, instance.entity().getHealth() + reattachHeal));
        }
        if (reattachHardening > 0) {
            instance.addPermanentDamageReduction(reattachHardening);
        }
        Fx.coloredBurst(at.clone().add(0, 1.2, 0), color, 2.4f, 60, 0.9);
        Fx.sound(at, Sound.ENTITY_WITHER_AMBIENT, 1.3f, 0.6f);
        instance.showTitle(
                Component.text("REATTACHED", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("It got its meal", NamedTextColor.GRAY));
        respawnCountdown = respawnDelayTicks;
    }

    private void clearParasite() {
        if (parasite != null && parasite.isValid()) {
            parasite.remove();
        }
        parasite = null;
    }
}

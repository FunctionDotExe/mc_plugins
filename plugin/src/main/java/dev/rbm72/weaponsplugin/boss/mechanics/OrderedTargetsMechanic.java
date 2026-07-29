package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.props.ArenaTotem;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Several targets, and only one of them is the right one to hit at any moment. The order is cued by
 * a rising chime and by which one is lit, it changes every attempt, and striking out of turn throws
 * the whole sequence away and costs the group real health.
 * <p>
 * This is the roster's only mechanic where <em>restraint</em> is the skill. Everything else rewards
 * hitting whatever is in front of you as fast as possible; here the wrong swing is actively worse
 * than no swing, so a group has to communicate, assign targets and hold fire — which is a completely
 * different social texture from "everyone burn the adds". It is also why it is worth building once
 * and reusing: a sun-idol ritual and a choir singing in sequence are the same rule.
 * <p>
 * The boss is blunted rather than immune while the sequence stands, so the phase can never deadlock,
 * and the timeout flare is severe enough that ignoring the sequence entirely is never the fast line.
 */
public final class OrderedTargetsMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;
    /** Damage the boss still takes mid-sequence. Never zero — Pitfall 2. */
    private static final double SEQUENCE_DAMAGE_RETAINED = 0.15;

    private final String label;
    private final String targetName;
    private final Color color;
    private final Material targetMaterial;
    private final int targetCount;
    private final double targetHealth;
    private final int windowTicks;
    private final int restartDelayTicks;
    private final double wrongOrderDamage;
    private final double timeoutDamage;
    private final double exposedMultiplier;
    private final int exposedTicks;
    private final int staggerTicks;
    private final double placementFraction;

    private final List<ArenaTotem> totems = new ArrayList<>();
    private final List<Integer> order = new ArrayList<>();
    private int step;
    private int windowLeft;
    private int restartCountdown;
    private int exposedLeft;
    private boolean sequenceUp;

    public OrderedTargetsMechanic(BossInstance instance, String label, String targetName, Color color,
                                   Material targetMaterial, int targetCount, double targetHealth,
                                   int windowTicks, int restartDelayTicks, double wrongOrderDamage,
                                   double timeoutDamage, double exposedMultiplier, int exposedTicks,
                                   int staggerTicks, double placementFraction) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.targetName = targetName;
        this.color = color;
        this.targetMaterial = targetMaterial;
        this.targetCount = Math.max(2, targetCount);
        this.targetHealth = Math.max(4.0, targetHealth);
        this.windowTicks = Math.max(100, windowTicks);
        this.restartDelayTicks = Math.max(40, restartDelayTicks);
        this.wrongOrderDamage = wrongOrderDamage;
        this.timeoutDamage = timeoutDamage;
        this.exposedMultiplier = exposedMultiplier;
        this.exposedTicks = Math.max(20, exposedTicks);
        this.staggerTicks = staggerTicks;
        this.placementFraction = placementFraction;
    }

    @Override
    protected void onStart() {
        instance.showTitle(
                Component.text(label, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("In order — the lit one, and only the lit one", NamedTextColor.GRAY));
        raise();
    }

    @Override
    protected void onStop() {
        clearTotems();
    }

    @Override
    public double filterDamage(Player attacker, double damage) {
        if (stopped || !sequenceUp) {
            return damage;
        }
        if (instance.signalDeflectReady()) {
            plugin.actionBarHub().flash(attacker,
                    Component.text("The ritual holds it — finish the sequence", NamedTextColor.GOLD),
                    1200, ActionBarHub.PRIORITY_NOTICE);
        }
        return damage * SEQUENCE_DAMAGE_RETAINED;
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
                restartCountdown = restartDelayTicks;
            }
            showBars();
            return;
        }

        if (!sequenceUp) {
            restartCountdown -= TICK_INTERVAL;
            if (restartCountdown <= 0) {
                raise();
            }
            showBars();
            return;
        }

        // Something removed a totem without going through our callbacks (fight teardown, a stray
        // despawn): rebuild rather than sit on a sequence that can never be finished.
        if (totems.stream().noneMatch(ArenaTotem::isValid)) {
            sequenceUp = false;
            restartCountdown = restartDelayTicks;
            showBars();
            return;
        }

        highlightNext();
        windowLeft -= TICK_INTERVAL;
        if (windowLeft <= 0) {
            flare();
        }
        showBars();
    }

    /** The lit target: a bright ring and a chime pitched to its place in the sequence. */
    private void highlightNext() {
        ArenaTotem next = currentTarget();
        if (next == null || !next.isValid()) {
            return;
        }
        Location at = next.location();
        Fx.coloredRing(at.clone().add(0, 1.2, 0), color, 1.6f, 1.8, 16, elapsedTicks * 0.2);
        if (elapsedTicks % 10 == 0) {
            Fx.burst(at.clone().add(0, 2.2, 0), Particle.END_ROD, 6, 0.3);
            Fx.sound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f,
                    0.7f + 0.25f * step);
        }
    }

    private ArenaTotem currentTarget() {
        if (step >= order.size()) {
            return null;
        }
        int index = order.get(step);
        return index < totems.size() ? totems.get(index) : null;
    }

    private void showBars() {
        if (exposedLeft > 0) {
            Component text = Component.text("RITUAL BROKEN  ", NamedTextColor.GREEN)
                    .append(Component.text(Math.max(0, exposedLeft / 20) + "s wide open", NamedTextColor.WHITE));
            instance.mechanicBar().updateShared(instance.barViewers(), text,
                    exposedLeft / (double) exposedTicks, BossBar.Color.GREEN);
            return;
        }
        if (!sequenceUp) {
            Component text = Component.text(label + "  ", NamedTextColor.GOLD)
                    .append(Component.text("re-lighting in " + Math.max(0, restartCountdown / 20) + "s",
                            NamedTextColor.GRAY));
            instance.mechanicBar().updateShared(instance.barViewers(), text, 0.0, BossBar.Color.WHITE);
            return;
        }
        Component text = Component.text(label + "  ", NamedTextColor.GOLD)
                .append(Component.text(step + " / " + order.size(), NamedTextColor.WHITE))
                .append(Component.text("   hit only the lit one", NamedTextColor.DARK_GRAY))
                .append(Component.text("   " + Math.max(0, windowLeft / 20) + "s",
                        windowLeft < windowTicks * 0.25 ? NamedTextColor.RED : NamedTextColor.GRAY));
        instance.mechanicBar().updateShared(instance.barViewers(), text,
                order.isEmpty() ? 0.0 : step / (double) order.size(),
                windowLeft < windowTicks * 0.25 ? BossBar.Color.RED : BossBar.Color.YELLOW);
    }

    private void onTotemBroken(ArenaTotem broken) {
        if (stopped || !sequenceUp) {
            return;
        }
        int brokenIndex = totems.indexOf(broken);
        ArenaTotem expected = currentTarget();
        if (expected == null || brokenIndex < 0) {
            return;
        }
        if (!expected.equals(broken)) {
            wrongOrder(broken);
            return;
        }

        step++;
        Location at = broken.location();
        Fx.coloredBurst(at.clone().add(0, 1.2, 0), color, 2.0f, 34, 0.6);
        Fx.sound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 0.8f + 0.2f * step);
        if (step >= order.size()) {
            complete();
        }
    }

    private void wrongOrder(ArenaTotem broken) {
        Location at = broken.location();
        Fx.coloredBurst(at.clone().add(0, 1.2, 0), Color.fromRGB(200, 40, 40), 2.4f, 44, 0.7);
        Fx.sound(at, Sound.ENTITY_VILLAGER_NO, 1.4f, 0.7f);
        for (Player player : combatants()) {
            hurt(player, wrongOrderDamage);
        }
        instance.showTitle(
                Component.text("OUT OF TURN", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("The sequence resets", NamedTextColor.GRAY));
        // Deliberately no exposure credit and a full reset: swinging at whatever is closest must never
        // be a faster route than reading the order (Pitfall 3).
        sequenceUp = false;
        clearTotems();
        restartCountdown = restartDelayTicks;
    }

    private void complete() {
        sequenceUp = false;
        clearTotems();
        instance.recordExposure();
        instance.setDamageMultiplier(exposedMultiplier);
        instance.stagger(staggerTicks);
        exposedLeft = exposedTicks;

        Location at = instance.entity().getLocation();
        if (instance.entity().isValid()) {
            instance.entity().setGlowing(true);
        }
        Fx.coloredBurst(at.clone().add(0, 1.4, 0), color, 2.8f, 80, 1.1);
        Fx.flash(at.clone().add(0, 1.4, 0), 3);
        Fx.sound(at, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.8f);
        instance.showTitle(
                Component.text("THE SEQUENCE HOLDS", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("It has nothing left holding it up", NamedTextColor.GRAY));
    }

    private void flare() {
        sequenceUp = false;
        clearTotems();
        restartCountdown = restartDelayTicks;

        Location at = instance.entity().getLocation();
        Fx.expandingRings(plugin, at, Particle.FLAME, Math.min(18.0, instance.arena().radius() * 0.8), 5, 2L);
        Fx.flash(at.clone().add(0, 1.4, 0), 3);
        Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.5f);
        instance.showTitle(
                Component.text("THE RITUAL COMPLETES", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("You were too slow", NamedTextColor.GRAY));
        for (Player player : combatants()) {
            hurt(player, timeoutDamage);
        }
    }

    private void raise() {
        clearTotems();
        step = 0;
        windowLeft = windowTicks;
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);

        for (int i = 0; i < targetCount; i++) {
            double angle = startAngle + 2 * Math.PI * i / targetCount;
            Location spot = surfaceSpot(angle, placementFraction);
            final int ordinal = i + 1;
            ArenaTotem totem = ArenaTotem.spawn(plugin, instance, spot, targetMaterial,
                    Component.text(targetName + " " + ordinal, NamedTextColor.GOLD),
                    targetHealth, windowTicks + 100,
                    this::onTotemBroken,
                    expired -> {
                    });
            if (totem != null) {
                totems.add(totem);
            }
            Fx.coloredBurst(spot.clone().add(0, 1, 0), color, 1.6f, 22, 0.4);
        }

        order.clear();
        for (int i = 0; i < totems.size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order, ThreadLocalRandom.current());
        sequenceUp = !totems.isEmpty();
        if (!sequenceUp) {
            restartCountdown = restartDelayTicks;
            return;
        }
        Fx.sound(instance.arena().center(), Sound.BLOCK_BEACON_ACTIVATE, 1.3f, 1.0f);
        instance.showTitle(
                Component.text(label, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("Follow the light — wrong order and it starts again", NamedTextColor.GRAY));
    }

    private void clearTotems() {
        for (ArenaTotem totem : totems) {
            totem.discard();
        }
        totems.clear();
        order.clear();
        step = 0;
    }
}

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
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * A standing current across the whole arena: everything is dragged toward one point, or shoved away
 * from it, for the entire phase. Nothing is blocked and nothing is paused — you simply cannot stand
 * still, and every approach to the boss costs you ground you have to keep retaking.
 * <p>
 * Its value is that it re-tunes every other thing happening at the same time. Attack telegraphs that
 * were comfortable to sidestep become genuinely dangerous when the floor is moving under you, melee
 * range becomes a thing you hold rather than a thing you have, and any mechanic that wants you
 * somewhere specific gets much harder without a single extra rule. That is the whole reason this is a
 * phase-long condition rather than a scripted event.
 * <p>
 * Anchors are the release valve: an optional {@link MechanicField} where the current does not reach,
 * so there is always somewhere to plant yourself and always a cost to being there instead of on the
 * boss. With no anchor field the current is inescapable and the phase is purely about fighting the
 * drift.
 */
public final class ForceFieldMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;

    public enum Direction {
        /** Drags everything toward the focus — a gravity well, an undertow. */
        INWARD,
        /** Shoves everything away from the focus — a wing gust, a pressure wave. */
        OUTWARD
    }

    /** What the current is centred on. The boss moves; the arena centre does not. */
    public enum Focus {
        ARENA_CENTRE,
        BOSS
    }

    private final String label;
    private final Color color;
    private final Direction direction;
    private final Focus focus;
    private final double pullPerTick;
    private final double dangerRadius;
    private final double dangerDamagePerSecond;
    private final MechanicField anchorField;

    private int ticksSinceExposureCredit;

    public ForceFieldMechanic(BossInstance instance, String label, Color color, Direction direction,
                               Focus focus, double pullPerTick, double dangerRadius,
                               double dangerDamagePerSecond, MechanicField anchorField) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.direction = direction;
        this.focus = focus;
        this.pullPerTick = pullPerTick;
        this.dangerRadius = dangerRadius;
        this.dangerDamagePerSecond = dangerDamagePerSecond;
        this.anchorField = anchorField;
    }

    @Override
    protected void onStart() {
        if (anchorField != null) {
            anchorField.start(instance);
        }
        instance.showTitle(
                Component.text(label, NamedTextColor.DARK_AQUA).decoration(TextDecoration.BOLD, true),
                Component.text(direction == Direction.INWARD
                        ? "The floor is pulling — do not let it take you"
                        : "Brace, or be swept off", NamedTextColor.GRAY));
        Fx.sound(instance.arena().center(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.6f);
    }

    @Override
    protected void onStop() {
        if (anchorField != null) {
            anchorField.stop(instance);
        }
    }

    private Location focusPoint() {
        return focus == Focus.BOSS ? instance.entity().getLocation() : instance.arena().center();
    }

    @Override
    protected void tick() {
        if (anchorField != null) {
            anchorField.tick(instance, elapsedTicks);
        }
        Location centre = focusPoint();
        drawCurrent(centre);

        List<Player> present = combatants();
        boolean anyoneHolding = false;

        for (Player player : present) {
            if (anchorField != null && anchorField.contains(instance, player)) {
                anyoneHolding = true;
                if (elapsedTicks % 10 == 0) {
                    Fx.coloredBurst(player.getLocation().add(0, 0.2, 0), Color.fromRGB(140, 255, 170), 0.8f, 4, 0.2);
                }
                continue;
            }

            Location at = player.getLocation();
            double dx = at.getX() - centre.getX();
            double dz = at.getZ() - centre.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 0.001) {
                continue;
            }

            double sign = direction == Direction.INWARD ? -1.0 : 1.0;
            Vector nudge = new Vector(dx / dist * sign * pullPerTick, 0, dz / dist * sign * pullPerTick);
            // Added rather than set, so the player's own movement always still counts — this is a
            // current to fight, not a lock that takes control away from them.
            player.setVelocity(player.getVelocity().add(nudge));

            boolean inDanger = direction == Direction.INWARD
                    ? dist <= dangerRadius
                    : dist >= dangerRadius;
            if (inDanger) {
                if (elapsedTicks % 20 == 0) {
                    hurt(player, dangerDamagePerSecond);
                    Fx.coloredBurst(at.clone().add(0, 1, 0), color, 1.4f, 14, 0.4);
                    Fx.sound(at, Sound.ENTITY_PLAYER_HURT, 0.7f, 0.8f);
                }
            } else {
                anyoneHolding = true;
            }
        }

        ticksSinceExposureCredit += TICK_INTERVAL;
        if (anyoneHolding && ticksSinceExposureCredit >= 20) {
            ticksSinceExposureCredit = 0;
            // Holding position against the current is the thing this phase is asking for.
            instance.recordExposure();
        }
        showBars(centre);
    }

    private void drawCurrent(Location centre) {
        double phase = direction == Direction.INWARD ? -elapsedTicks * 0.12 : elapsedTicks * 0.12;
        double maxRadius = Math.min(instance.arena().radius() - 2, dangerRadius + 14);
        for (int band = 0; band < 3; band++) {
            double radius = dangerRadius + band * 4.5;
            if (radius > maxRadius) {
                break;
            }
            Fx.coloredRing(centre, color, 1.2f, radius, 20, phase + band * 0.4);
        }
        if (elapsedTicks % 6 == 0) {
            Fx.coloredRing(centre, color, 1.6f, Math.max(1.0, dangerRadius), 18, -phase);
            Fx.burst(centre.clone().add(0, 0.6, 0), Particle.SPLASH, 6, 0.5);
        }
    }

    private void showBars(Location centre) {
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            boolean anchored = anchorField != null && anchorField.contains(instance, viewer);
            double dist = flatDistance(viewer.getLocation(), centre);
            boolean inDanger = !anchored && (direction == Direction.INWARD ? dist <= dangerRadius : dist >= dangerRadius);
            double safety = direction == Direction.INWARD
                    ? Math.min(1.0, dist / Math.max(1.0, dangerRadius * 2))
                    : Math.max(0.0, 1.0 - dist / Math.max(1.0, dangerRadius));
            Component text = Component.text(label + "  ", NamedTextColor.DARK_AQUA)
                    .append(anchored
                            ? Component.text("anchored", NamedTextColor.GREEN)
                            : inDanger
                                    ? Component.text("IT HAS YOU — FIGHT OUT", NamedTextColor.RED)
                                    : Component.text("holding", NamedTextColor.WHITE));
            BossBar.Color barColor = anchored ? BossBar.Color.GREEN
                    : inDanger ? BossBar.Color.RED : BossBar.Color.BLUE;
            return MechanicBar.Readout.of(text, safety, barColor);
        });
    }
}

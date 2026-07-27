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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A charge latches onto one player and hunts for the next: every hop it jumps to whoever is closest,
 * and every hop it hits harder. It only dies when it cannot find anyone within reach — so the answer
 * is to break the group apart and keep it apart, permanently, while still fighting.
 * <p>
 * This is the roster's one mechanic that punishes <em>proximity itself</em>. Everything else in the
 * plugin quietly rewards stacking up (shared AoE healing, overlapping cleave, one safe spot to hold),
 * and a group's default resting state is a clump around the boss's feet. A growing chain makes that
 * clump lethal without ever asking anyone to stop attacking, and the counterplay — spread out, watch
 * who is carrying it, do not run toward the person shouting — is legible the first time it fires.
 * <p>
 * Solo: with nobody to jump to, the chain grounds itself on the first hop. A lone player takes one
 * small hit and it is over, which is the intended "you brought nobody, so this mechanic is mostly not
 * about you" outcome.
 */
public final class ChainTagMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;

    private final String label;
    private final Color color;
    private final double hopRange;
    private final int hopIntervalTicks;
    private final double baseDamage;
    private final double damageGrowthPerHop;
    private final int maxHops;
    private final int respawnDelayTicks;
    private final double groundedRewardExposure;

    private UUID carrierId;
    private int hopCountdown;
    private int hops;
    private int respawnCountdown;
    /** Everyone the current chain has already burned through, so it cannot ping-pong between two people. */
    private final List<UUID> visited = new ArrayList<>();

    public ChainTagMechanic(BossInstance instance, String label, Color color, double hopRange,
                             int hopIntervalTicks, double baseDamage, double damageGrowthPerHop,
                             int maxHops, int respawnDelayTicks) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.hopRange = hopRange;
        this.hopIntervalTicks = Math.max(4, hopIntervalTicks);
        this.baseDamage = baseDamage;
        this.damageGrowthPerHop = damageGrowthPerHop;
        this.maxHops = Math.max(1, maxHops);
        this.respawnDelayTicks = Math.max(20, respawnDelayTicks);
        this.groundedRewardExposure = 1.0;
    }

    @Override
    protected void onStart() {
        respawnCountdown = 40;
        instance.showTitle(
                Component.text(label, NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                Component.text("It jumps to whoever is nearest — and it grows", NamedTextColor.GRAY));
    }

    @Override
    protected void onStop() {
        carrierId = null;
        visited.clear();
    }

    @Override
    protected void tick() {
        List<Player> present = combatants();

        if (carrierId == null) {
            respawnCountdown -= TICK_INTERVAL;
            if (respawnCountdown <= 0 && !present.isEmpty()) {
                strike(present.get(ThreadLocalRandom.current().nextInt(present.size())));
            }
            showBars(null);
            return;
        }

        Player carrier = findCarrier(present);
        if (carrier == null) {
            // Carrier died or left mid-chain: the charge has nowhere to go and dissipates. No reward —
            // it was not grounded by anything the group did.
            extinguish(false);
            showBars(null);
            return;
        }

        drawArc(carrier);
        hopCountdown -= TICK_INTERVAL;
        if (hopCountdown > 0) {
            showBars(carrier);
            return;
        }

        Player next = nearestUnvisited(carrier, present);
        double damage = baseDamage + damageGrowthPerHop * hops;
        hurt(carrier, damage);
        Fx.coloredBurst(carrier.getLocation().add(0, 1, 0), color, 1.8f, 26, 0.5);
        Fx.sound(carrier.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.9f, 1.4f);

        if (next == null || hops + 1 >= maxHops) {
            // Nobody in reach — the group actually spread, which is exactly what the phase asks for.
            extinguish(next == null);
            showBars(null);
            return;
        }

        Fx.line(carrier.getLocation().add(0, 1, 0), next.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 14);
        visited.add(carrier.getUniqueId());
        hops++;
        carrierId = next.getUniqueId();
        hopCountdown = hopIntervalTicks;
        Fx.sound(next.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.6f);
        showBars(next);
    }

    private void strike(Player target) {
        carrierId = target.getUniqueId();
        hops = 0;
        hopCountdown = hopIntervalTicks;
        visited.clear();
        Fx.coloredBurst(target.getLocation().add(0, 1.4, 0), color, 2.0f, 34, 0.5);
        Fx.sound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 1.0f);
    }

    private void extinguish(boolean grounded) {
        Player carrier = findCarrier(combatants());
        if (carrier != null) {
            Fx.coloredBurst(carrier.getLocation().add(0, 1, 0), color, 1.4f, 18, 0.4);
        }
        if (grounded && groundedRewardExposure > 0) {
            instance.recordExposure();
        }
        carrierId = null;
        hops = 0;
        visited.clear();
        respawnCountdown = respawnDelayTicks;
    }

    private Player findCarrier(List<Player> present) {
        if (carrierId == null) {
            return null;
        }
        for (Player player : present) {
            if (player.getUniqueId().equals(carrierId) && player.isValid() && !player.isDead()) {
                return player;
            }
        }
        return null;
    }

    private Player nearestUnvisited(Player carrier, List<Player> present) {
        Player best = null;
        double bestDist = hopRange;
        for (Player candidate : present) {
            if (candidate.equals(carrier) || visited.contains(candidate.getUniqueId())
                    || !candidate.isValid() || candidate.isDead()) {
                continue;
            }
            double dist = flatDistance(carrier.getLocation(), candidate.getLocation());
            if (dist <= bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    /** A visible tether from the carrier to anyone currently in jump range — the "get away from them" cue. */
    private void drawArc(Player carrier) {
        Location from = carrier.getLocation().add(0, 1.2, 0);
        Fx.coloredRing(carrier.getLocation(), color, 1.2f, hopRange, 24, elapsedTicks * 0.1);
        for (Player candidate : combatants()) {
            if (candidate.equals(carrier) || visited.contains(candidate.getUniqueId())) {
                continue;
            }
            if (flatDistance(carrier.getLocation(), candidate.getLocation()) <= hopRange) {
                Fx.line(from, candidate.getLocation().add(0, 1.2, 0), Particle.ELECTRIC_SPARK, 6);
            }
        }
    }

    private void showBars(Player carrier) {
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            if (carrier == null) {
                Component idle = Component.text(label + "  ", NamedTextColor.GRAY)
                        .append(Component.text("grounded — stay spread", NamedTextColor.GREEN));
                return MechanicBar.Readout.of(idle, 0.0, BossBar.Color.GREEN);
            }
            boolean isCarrier = viewer.equals(carrier);
            double growth = Math.min(1.0, (hops + 1) / (double) maxHops);
            Component text = isCarrier
                    ? Component.text("YOU ARE CARRYING IT  ", NamedTextColor.RED)
                            .append(Component.text("run from your allies", NamedTextColor.WHITE))
                    : Component.text(label + "  ", NamedTextColor.YELLOW)
                            .append(Component.text(carrier.getName(), NamedTextColor.WHITE))
                            .append(Component.text("  hop " + (hops + 1) + "/" + maxHops, NamedTextColor.GRAY))
                            .append(Component.text("   get away from them", NamedTextColor.DARK_GRAY));
            return MechanicBar.Readout.of(text, growth, isCarrier ? BossBar.Color.RED : BossBar.Color.YELLOW);
        });
    }
}

package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.Arena;
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * The arena remembers. Wherever the boss stood a few seconds ago, its grief detonates again — so the
 * fight leaves a trail of delayed explosions retracing exactly the path everyone has just been
 * following.
 * <p>
 * The demand this makes is unusual and, once it clicks, very satisfying: melee players are punished
 * for standing <em>where the boss was</em>, which is where chasing it naturally leaves them. The
 * correct play is to lead it rather than follow it, and to keep a mental note of the last few
 * seconds of the fight. Nothing about it is random — every echo is a place the group chose to be —
 * so it always feels earned rather than arbitrary.
 * <p>
 * Echoes are drawn from the moment they are recorded, faintly, and brighten as they mature, so there
 * is always a readable countdown on the floor. The boss keeps attacking and stays hittable
 * throughout; this only changes which square of ground is worth standing on.
 */
public final class EchoZoneMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;

    private final String label;
    private final Color color;
    private final int recordIntervalTicks;
    private final int echoDelayTicks;
    private final double radius;
    private final double damage;
    private final int maxEchoes;

    private final Deque<Echo> echoes = new ArrayDeque<>();
    private int recordCountdown;
    private int dodged;
    private int caught;

    private static final class Echo {
        final Location at;
        int fuse;

        Echo(Location at, int fuse) {
            this.at = at;
            this.fuse = fuse;
        }
    }

    public EchoZoneMechanic(BossInstance instance, String label, Color color, int recordIntervalTicks,
                             int echoDelayTicks, double radius, double damage, int maxEchoes) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.recordIntervalTicks = Math.max(10, recordIntervalTicks);
        this.echoDelayTicks = Math.max(30, echoDelayTicks);
        this.radius = Math.max(1.5, radius);
        this.damage = damage;
        this.maxEchoes = Math.max(1, maxEchoes);
    }

    @Override
    protected void onStart() {
        instance.showTitle(
                Component.text(label, NamedTextColor.BLUE).decoration(TextDecoration.BOLD, true),
                Component.text("Where it has been, it happens again — do not follow it", NamedTextColor.GRAY));
        Fx.sound(instance.entity().getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.1f, 0.5f);
    }

    @Override
    protected void onStop() {
        echoes.clear();
    }

    @Override
    protected void tick() {
        for (Iterator<Echo> it = echoes.iterator(); it.hasNext(); ) {
            Echo echo = it.next();
            echo.fuse -= TICK_INTERVAL;
            if (echo.fuse <= 0) {
                detonate(echo);
                it.remove();
                continue;
            }
            double maturity = 1.0 - echo.fuse / (double) echoDelayTicks;
            if (elapsedTicks % 4 == 0) {
                Fx.coloredRing(echo.at, color, (float) (0.7 + maturity * 1.3), radius, 18,
                        elapsedTicks * 0.06);
            }
            if (maturity > 0.75 && elapsedTicks % 6 == 0) {
                Fx.burst(echo.at.clone().add(0, 0.4, 0), Particle.DRIPPING_WATER, 5, radius * 0.4);
            }
        }

        recordCountdown -= TICK_INTERVAL;
        if (recordCountdown <= 0) {
            recordCountdown = recordIntervalTicks;
            record();
        }
        showBars();
    }

    private void record() {
        if (!instance.entity().isValid()) {
            return;
        }
        while (echoes.size() >= maxEchoes) {
            echoes.pollFirst();
        }
        Location at = instance.entity().getLocation().clone();
        echoes.addLast(new Echo(at, echoDelayTicks));
        Fx.coloredRing(at, color, 0.8f, radius, 12, 0);
        Fx.sound(at, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
    }

    private void detonate(Echo echo) {
        Fx.coloredBurst(echo.at.clone().add(0, 0.8, 0), color, 2.2f, 40, radius * 0.4);
        Fx.burst(echo.at.clone().add(0, 0.8, 0), Particle.SPLASH, 20, radius * 0.4);
        Fx.sound(echo.at, Sound.ENTITY_ELDER_GUARDIAN_HURT, 1.1f, 0.7f);

        var caughtHere = Arena.combatants(echo.at, radius);
        if (caughtHere.isEmpty()) {
            dodged++;
            // Keeping clear of the boss's own wake is what this phase asks for.
            instance.recordExposure();
            return;
        }
        caught++;
        for (Player player : caughtHere) {
            hurt(player, damage);
            Fx.coloredBurst(player.getLocation().add(0, 1, 0), color, 1.4f, 18, 0.4);
        }
    }

    private void showBars() {
        int total = dodged + caught;
        double clean = total == 0 ? 1.0 : dodged / (double) total;
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            boolean standingInOne = echoes.stream()
                    .anyMatch(echo -> flatDistance(viewer.getLocation(), echo.at) <= radius);
            Component text = Component.text(label + "  ", NamedTextColor.BLUE)
                    .append(standingInOne
                            ? Component.text("YOU ARE IN AN ECHO", NamedTextColor.RED)
                            : Component.text("clear of its wake", NamedTextColor.GREEN))
                    .append(Component.text("   " + echoes.size() + " waiting", NamedTextColor.GRAY));
            return MechanicBar.Readout.of(text, clean,
                    standingInOne ? BossBar.Color.RED : BossBar.Color.BLUE);
        });
    }
}

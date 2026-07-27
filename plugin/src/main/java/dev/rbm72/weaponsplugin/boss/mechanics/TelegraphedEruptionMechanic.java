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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The ground marks itself under your feet, gives you a beat to notice, and then erupts. Anyone still
 * standing on the mark takes the hit and gets pinned in place, which is worse than the damage.
 * <p>
 * This is the roster's pure reaction mechanic, and the reason it earns a phase rather than being just
 * another attack is the cadence: marks keep landing under whoever is standing still, forever, so the
 * phase quietly forbids camping. A melee group that plants itself at the boss's feet is marked
 * continuously; a group that keeps circling is barely troubled by it. Nothing is ever locked and the
 * boss stays fully hittable — the phase only changes where you are allowed to fight from.
 * <p>
 * The snare on failure is what makes it bite. Damage alone would be a tax; being rooted for a moment
 * while the rest of the boss's kit is still firing is a real consequence that the group can see
 * coming and avoid entirely with footwork.
 */
public final class TelegraphedEruptionMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;

    private final String label;
    private final Color color;
    private final Particle eruptionParticle;
    private final Sound telegraphSound;
    private final Sound eruptionSound;
    private final int volleyIntervalTicks;
    private final int telegraphTicks;
    private final double radius;
    private final double damage;
    private final int snareTicks;
    private final int maxMarksPerVolley;

    private final List<Mark> marks = new ArrayList<>();
    private int volleyCountdown;
    private int dodgedThisPhase;
    private int caughtThisPhase;

    private static final class Mark {
        final Location at;
        int fuse;

        Mark(Location at, int fuse) {
            this.at = at;
            this.fuse = fuse;
        }
    }

    public TelegraphedEruptionMechanic(BossInstance instance, String label, Color color,
                                        Particle eruptionParticle, Sound telegraphSound, Sound eruptionSound,
                                        int volleyIntervalTicks, int telegraphTicks, double radius,
                                        double damage, int snareTicks, int maxMarksPerVolley) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.eruptionParticle = eruptionParticle;
        this.telegraphSound = telegraphSound;
        this.eruptionSound = eruptionSound;
        this.volleyIntervalTicks = Math.max(20, volleyIntervalTicks);
        this.telegraphTicks = Math.max(10, telegraphTicks);
        this.radius = Math.max(1.0, radius);
        this.damage = damage;
        this.snareTicks = snareTicks;
        this.maxMarksPerVolley = Math.max(1, maxMarksPerVolley);
    }

    @Override
    protected void onStart() {
        volleyCountdown = 30;
        instance.showTitle(
                Component.text(label, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("It marks the ground you stand on — do not stand still", NamedTextColor.GRAY));
    }

    @Override
    protected void onStop() {
        marks.clear();
    }

    @Override
    protected void tick() {
        for (Iterator<Mark> it = marks.iterator(); it.hasNext(); ) {
            Mark mark = it.next();
            mark.fuse -= TICK_INTERVAL;
            if (mark.fuse <= 0) {
                erupt(mark);
                it.remove();
                continue;
            }
            double urgency = 1.0 - mark.fuse / (double) telegraphTicks;
            Fx.coloredRing(mark.at, color, (float) (1.1 + urgency), radius, 20, elapsedTicks * 0.15);
            if (mark.fuse % 10 < TICK_INTERVAL) {
                Fx.coloredBurst(mark.at.clone().add(0, 0.3, 0), color, 1.0f, 6, radius * 0.3);
            }
        }

        volleyCountdown -= TICK_INTERVAL;
        if (volleyCountdown <= 0) {
            volleyCountdown = volleyIntervalTicks;
            mark();
        }
        showBars();
    }

    private void showBars() {
        int total = dodgedThisPhase + caughtThisPhase;
        double cleanRate = total == 0 ? 1.0 : dodgedThisPhase / (double) total;
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            boolean standingOnMark = marks.stream()
                    .anyMatch(mark -> flatDistance(viewer.getLocation(), mark.at) <= radius);
            Component text = Component.text(label + "  ", NamedTextColor.GOLD)
                    .append(standingOnMark
                            ? Component.text("MOVE — IT IS UNDER YOU", NamedTextColor.RED)
                            : Component.text("clear", NamedTextColor.GREEN))
                    .append(Component.text("   dodged " + dodgedThisPhase + " / " + total, NamedTextColor.GRAY));
            return MechanicBar.Readout.of(text, cleanRate,
                    standingOnMark ? BossBar.Color.RED : BossBar.Color.YELLOW);
        });
    }

    /** Marks the ground under a slice of the group — under their feet, so only movement saves them. */
    private void mark() {
        List<Player> present = new ArrayList<>(combatants());
        if (present.isEmpty()) {
            return;
        }
        int count = Math.min(maxMarksPerVolley, present.size());
        for (int i = 0; i < count; i++) {
            Player target = present.get(i);
            Location at = target.getLocation().clone();
            marks.add(new Mark(at, telegraphTicks));
            Fx.coloredRing(at, color, 1.4f, radius, 20, 0);
            Fx.sound(at, telegraphSound, 1.0f, 1.4f);
        }
    }

    private void erupt(Mark mark) {
        Fx.coloredBurst(mark.at.clone().add(0, 1.0, 0), color, 2.2f, 40, radius * 0.4);
        Fx.burst(mark.at.clone().add(0, 0.8, 0), eruptionParticle, 24, radius * 0.35);
        Fx.sound(mark.at, eruptionSound, 1.2f, 0.8f);

        List<Player> caught = Arena.combatants(mark.at, radius);
        if (caught.isEmpty()) {
            dodgedThisPhase++;
            // Reading the telegraph and clearing it is precisely what this phase asks for.
            instance.recordExposure();
            return;
        }
        caughtThisPhase++;
        for (Player player : caught) {
            hurt(player, damage);
            if (player.isValid() && !player.isDead() && snareTicks > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, snareTicks, 4));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, snareTicks, 128));
            }
        }
    }
}

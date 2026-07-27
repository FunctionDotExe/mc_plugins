package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.boss.props.ArenaTotem;
import dev.rbm72.weaponsplugin.fx.Fx;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Three heads, three completely different problems, all happening at once and all on their own
 * clocks. One is winding up a bomb, one is throwing something that follows you, and one has picked
 * somebody out to kill. Breaking a head buys silence from <em>that</em> problem for a while, and
 * nothing else.
 * <p>
 * The point is the triage. Every other mechanic in the roster asks the group to do one thing well;
 * this asks them to decide, continuously, which of three ongoing threats is the one they can least
 * afford right now — and the answer changes with the group's health, positioning and who is marked.
 * It is the last real escalation before the roster's finale, and it works by layering three simple,
 * individually legible demands rather than by inventing a complicated one.
 * <p>
 * The boss stays fully hittable throughout. Heads are pure pressure, not a gate: a group can ignore
 * them entirely and race the boss down, they will simply be taking all three problems the whole way.
 */
public final class ThreeHeadsMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 2L;
    private static final int HEAD_COUNT = 3;

    private final String label;
    private final Color color;
    private final Material headMaterial;
    private final double headHealth;
    private final int silenceTicks;
    private final int nukeIntervalTicks;
    private final int nukeTelegraphTicks;
    private final double nukeRadius;
    private final double nukeDamage;
    private final int seekerIntervalTicks;
    private final double seekerDamage;
    private final int markIntervalTicks;
    private final int markFuseTicks;
    private final double markDamage;
    private final double markEscapeDistance;
    private final double placementFraction;

    private final List<Head> heads = new ArrayList<>();
    private final List<Nuke> nukes = new ArrayList<>();
    private final List<Seeker> seekers = new ArrayList<>();

    private int nukeCountdown;
    private int seekerCountdown;
    private int markCountdown;
    private Player marked;
    private Location markOrigin;
    private int markFuse;

    /** One head: a destructible prop plus the clock for the single gimmick it drives. */
    private static final class Head {
        final int gimmick;
        Location at;
        ArenaTotem totem;
        int silencedLeft;

        Head(int gimmick) {
            this.gimmick = gimmick;
        }

        boolean active() {
            return silencedLeft <= 0;
        }
    }

    private static final class Nuke {
        final Location at;
        int fuse;

        Nuke(Location at, int fuse) {
            this.at = at;
            this.fuse = fuse;
        }
    }

    private static final class Seeker {
        Location at;
        final Player target;
        int life;

        Seeker(Location at, Player target, int life) {
            this.at = at;
            this.target = target;
            this.life = life;
        }
    }

    public ThreeHeadsMechanic(BossInstance instance, String label, Color color, Material headMaterial,
                               double headHealth, int silenceTicks, int nukeIntervalTicks,
                               int nukeTelegraphTicks, double nukeRadius, double nukeDamage,
                               int seekerIntervalTicks, double seekerDamage, int markIntervalTicks,
                               int markFuseTicks, double markDamage, double markEscapeDistance,
                               double placementFraction) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.headMaterial = headMaterial;
        this.headHealth = Math.max(10.0, headHealth);
        this.silenceTicks = Math.max(40, silenceTicks);
        this.nukeIntervalTicks = Math.max(60, nukeIntervalTicks);
        this.nukeTelegraphTicks = Math.max(20, nukeTelegraphTicks);
        this.nukeRadius = Math.max(2.0, nukeRadius);
        this.nukeDamage = nukeDamage;
        this.seekerIntervalTicks = Math.max(60, seekerIntervalTicks);
        this.seekerDamage = seekerDamage;
        this.markIntervalTicks = Math.max(80, markIntervalTicks);
        this.markFuseTicks = Math.max(40, markFuseTicks);
        this.markDamage = markDamage;
        this.markEscapeDistance = Math.max(3.0, markEscapeDistance);
        this.placementFraction = placementFraction;
    }

    @Override
    protected void onStart() {
        instance.showTitle(
                Component.text(label, NamedTextColor.DARK_AQUA).decoration(TextDecoration.BOLD, true),
                Component.text("Three heads, three problems — pick which one you can survive", NamedTextColor.GRAY));
        for (int i = 0; i < HEAD_COUNT; i++) {
            heads.add(new Head(i));
        }
        raiseHeads();
        nukeCountdown = nukeIntervalTicks;
        seekerCountdown = seekerIntervalTicks / 2;
        markCountdown = markIntervalTicks;
    }

    @Override
    protected void onStop() {
        for (Head head : heads) {
            if (head.totem != null) {
                head.totem.discard();
            }
        }
        heads.clear();
        nukes.clear();
        seekers.clear();
        clearMark();
    }

    @Override
    protected void tick() {
        for (Head head : heads) {
            if (head.silencedLeft > 0) {
                head.silencedLeft -= TICK_INTERVAL;
                if (head.silencedLeft <= 0) {
                    regrow(head);
                }
            }
        }

        advanceNukes();
        advanceSeekers();
        advanceMark();

        if (headActive(0)) {
            nukeCountdown -= TICK_INTERVAL;
            if (nukeCountdown <= 0) {
                nukeCountdown = nukeIntervalTicks;
                castNuke();
            }
        }
        if (headActive(1)) {
            seekerCountdown -= TICK_INTERVAL;
            if (seekerCountdown <= 0) {
                seekerCountdown = seekerIntervalTicks;
                castSeeker();
            }
        }
        if (headActive(2)) {
            markCountdown -= TICK_INTERVAL;
            if (markCountdown <= 0 && marked == null) {
                markCountdown = markIntervalTicks;
                castMark();
            }
        }
        showBars();
    }

    private boolean headActive(int gimmick) {
        for (Head head : heads) {
            if (head.gimmick == gimmick) {
                return head.active();
            }
        }
        return false;
    }

    // ------------------------------------------------------------ head 1: the bomb

    private void castNuke() {
        List<Player> present = combatants();
        if (present.isEmpty()) {
            return;
        }
        Location at = present.get(ThreadLocalRandom.current().nextInt(present.size())).getLocation().clone();
        nukes.add(new Nuke(at, nukeTelegraphTicks));
        Fx.sound(at, Sound.ENTITY_WITHER_SHOOT, 1.2f, 0.6f);
    }

    private void advanceNukes() {
        nukes.removeIf(nuke -> {
            nuke.fuse -= TICK_INTERVAL;
            if (nuke.fuse > 0) {
                double urgency = 1.0 - nuke.fuse / (double) nukeTelegraphTicks;
                Fx.coloredRing(nuke.at, color, (float) (1.1 + urgency), nukeRadius, 24, elapsedTicks * 0.15);
                return false;
            }
            Fx.coloredBurst(nuke.at.clone().add(0, 1, 0), color, 2.6f, 60, nukeRadius * 0.4);
            Fx.flash(nuke.at.clone().add(0, 1, 0), 2);
            Fx.sound(nuke.at, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.7f);
            for (Player player : Arena.combatants(nuke.at, nukeRadius)) {
                hurt(player, nukeDamage);
            }
            return true;
        });
    }

    // ------------------------------------------------------------ head 2: the thing that follows

    private void castSeeker() {
        List<Player> present = combatants();
        if (present.isEmpty()) {
            return;
        }
        Player target = present.get(ThreadLocalRandom.current().nextInt(present.size()));
        Location start = instance.entity().getLocation().add(0, 1.6, 0);
        seekers.add(new Seeker(start, target, 200));
        Fx.sound(start, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.8f);
    }

    /**
     * Homes slowly enough to be outrun in a straight line but not to be ignored — the answer is to
     * keep moving away from it, which is exactly the wrong thing to be doing while a bomb is ticking
     * somewhere else.
     */
    private void advanceSeekers() {
        seekers.removeIf(seeker -> {
            seeker.life -= TICK_INTERVAL;
            if (seeker.life <= 0 || !seeker.target.isValid() || seeker.target.isDead()
                    || !seeker.target.isOnline()) {
                Fx.coloredBurst(seeker.at, color, 1.2f, 12, 0.3);
                return true;
            }
            Location goal = seeker.target.getLocation().add(0, 1.0, 0);
            org.bukkit.util.Vector step = goal.toVector().subtract(seeker.at.toVector());
            double distance = step.length();
            if (distance <= 1.4) {
                Fx.coloredBurst(seeker.at, color, 2.0f, 30, 0.5);
                Fx.sound(seeker.at, Sound.ENTITY_BLAZE_HURT, 1.1f, 1.2f);
                hurt(seeker.target, seekerDamage);
                return true;
            }
            seeker.at = seeker.at.clone().add(step.normalize().multiply(0.42 * TICK_INTERVAL));
            Fx.coloredBurst(seeker.at, color, 1.1f, 4, 0.1);
            return false;
        });
    }

    // ------------------------------------------------------------ head 3: the execution

    private void castMark() {
        List<Player> present = combatants();
        if (present.isEmpty()) {
            return;
        }
        marked = present.get(ThreadLocalRandom.current().nextInt(present.size()));
        markOrigin = marked.getLocation().clone();
        markFuse = markFuseTicks;
        marked.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, markFuseTicks, 0, false, false));
        Fx.sound(marked.getLocation(), Sound.ENTITY_WARDEN_LISTENING_ANGRY, 1.4f, 0.7f);
        instance.showTitle(
                Component.text("MARKED", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text(marked.getName() + " — get clear of where you are standing", NamedTextColor.GRAY));
    }

    private void advanceMark() {
        if (marked == null) {
            return;
        }
        if (!marked.isValid() || marked.isDead() || !marked.isOnline()) {
            clearMark();
            return;
        }
        markFuse -= TICK_INTERVAL;
        Fx.coloredRing(marked.getLocation(), color, 1.4f, 1.6, 14, elapsedTicks * 0.25);
        if (markFuse > 0) {
            return;
        }

        double travelled = flatDistance(marked.getLocation(), markOrigin);
        Location at = marked.getLocation();
        Fx.coloredBurst(at.clone().add(0, 1.2, 0), color, 2.4f, 50, 0.7);
        if (travelled >= markEscapeDistance) {
            Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 1.4f);
            // Breaking the execution by moving is precisely what this head is asking for.
            instance.recordExposure();
        } else {
            Fx.sound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 0.8f);
            hurt(marked, markDamage);
        }
        clearMark();
    }

    private void clearMark() {
        if (marked != null && marked.isOnline()) {
            marked.removePotionEffect(PotionEffectType.GLOWING);
        }
        marked = null;
        markOrigin = null;
        markFuse = 0;
    }

    // ------------------------------------------------------------ heads themselves

    private void raiseHeads() {
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < heads.size(); i++) {
            Head head = heads.get(i);
            head.at = surfaceSpot(startAngle + 2 * Math.PI * i / heads.size(), placementFraction);
            spawnTotem(head);
        }
    }

    private void spawnTotem(Head head) {
        head.totem = ArenaTotem.spawn(plugin, instance, head.at, headMaterial,
                Component.text(gimmickName(head.gimmick), NamedTextColor.DARK_AQUA),
                headHealth, 20 * 60 * 30,
                broken -> silence(head),
                expired -> {
                });
        Fx.coloredBurst(head.at.clone().add(0, 1.2, 0), color, 1.6f, 22, 0.4);
    }

    private void silence(Head head) {
        if (stopped) {
            return;
        }
        head.silencedLeft = silenceTicks;
        head.totem = null;
        instance.recordExposure();
        Fx.coloredBurst(head.at.clone().add(0, 1.2, 0), color, 2.2f, 40, 0.6);
        Fx.sound(head.at, Sound.ENTITY_WITHER_HURT, 1.3f, 1.2f);
        instance.showTitle(
                Component.text(gimmickName(head.gimmick).toUpperCase(java.util.Locale.ROOT) + " SILENCED",
                        NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("It will not stay quiet", NamedTextColor.GRAY));
    }

    private void regrow(Head head) {
        if (stopped) {
            return;
        }
        spawnTotem(head);
        Fx.sound(head.at, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.4f);
    }

    private static String gimmickName(int gimmick) {
        return switch (gimmick) {
            case 0 -> "Ruin Head";
            case 1 -> "Hunting Head";
            default -> "Execution Head";
        };
    }

    private void showBars() {
        long active = heads.stream().filter(Head::active).count();
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            boolean isMarked = marked != null && viewer.equals(marked);
            Component text = Component.text(label + "  ", NamedTextColor.DARK_AQUA)
                    .append(Component.text(active + " / " + HEAD_COUNT + " heads awake",
                            active == HEAD_COUNT ? NamedTextColor.RED : NamedTextColor.GREEN))
                    .append(isMarked
                            ? Component.text("   YOU ARE MARKED — MOVE", NamedTextColor.RED)
                            : Component.text("   break a head to quiet it", NamedTextColor.DARK_GRAY));
            return MechanicBar.Readout.of(text, active / (double) HEAD_COUNT,
                    isMarked ? BossBar.Color.RED : BossBar.Color.BLUE);
        });
    }
}

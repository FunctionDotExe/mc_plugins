package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Hands erupt out of the ground under a marked player and hold them there.
 * <p>
 * The hands are {@link EvokerFangs} — a real vanilla entity with its own wind-up animation, its own hitbox
 * and its own bite. The mark is a <em>tile</em>, not a target: it is placed where the player is standing when
 * the cast begins and stays there, so the counterplay is to step off it, and standing still is what gets
 * punished.
 * <p>
 * That is deliberate, because camping is otherwise the correct play against this boss — the group is supposed
 * to build a chokepoint and hold it. Grave Grasp is the rent on that: the camp is fine, standing motionless
 * inside it is not. So the mark preferentially lands on whoever is not moving, and being rooted in the middle
 * of a horde is usually worse than the damage.
 * <p>
 * Marks scale one per two players. The damage and the root length never change with group size.
 */
public final class GraveGraspAttack extends BossAttack {

    private static final Color NECROTIC = Color.fromRGB(120, 200, 110);

    /** Fangs per mark, as a small ring — one fang would be a coin flip on hitbox overlap rather than a zone. */
    private static final int FANGS_PER_MARK = 5;

    private final double damage;
    private final double radius;
    private final int rootTicks;
    private final int telegraphTicks;

    public GraveGraspAttack(WeaponsPlugin plugin) {
        super(plugin, "necro_overlord");
        this.damage = configDouble("grave-grasp-damage", 7.0);
        this.radius = configDouble("grave-grasp-radius", 2.6);
        this.rootTicks = configInt("grave-grasp-root-ticks", 50);
        this.telegraphTicks = configInt("grave-grasp-telegraph-ticks", 24);
    }

    @Override
    public String name() {
        return "Grave Grasp";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("grave-grasp-cooldown-seconds", 10.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        List<Location> marks = pickMarks(ctx);
        sequence(telegraphTicks,
                () -> {
                    for (Location mark : marks) {
                        Telegraph.dangerZone(mark, radius);
                        Fx.coloredRing(mark, NECROTIC, 1.4f, radius, 26, 0);
                        Fx.burst(mark.clone().add(0, 0.2, 0), Particle.SOUL, 4, radius * 0.4);
                    }
                },
                () -> {
                    BossAudio.play(ctx.bossLocation(), "boss.necro_overlord.grave_grasp",
                            Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.1f, 0.5f);
                    for (Location mark : marks) {
                        erupt(ctx, mark);
                    }
                },
                14, onComplete);
    }

    /**
     * One mark per two players, stationary players first. Marks are placed on the ground the players are
     * currently on and never follow them afterwards.
     */
    private List<Location> pickMarks(AttackContext ctx) {
        List<Player> candidates = new ArrayList<>(Arena.combatants(ctx.bossLocation(), ctx.arena().radius()));
        candidates.sort((a, b) -> Double.compare(horizontalSpeed(a), horizontalSpeed(b)));
        int wanted = Math.max(1, candidates.size() / 2);

        List<Location> marks = new ArrayList<>(wanted);
        for (int i = 0; i < wanted && i < candidates.size(); i++) {
            marks.add(candidates.get(i).getLocation());
        }
        if (marks.isEmpty()) {
            marks.add(ctx.target().getLocation());
        }
        return marks;
    }

    private static double horizontalSpeed(Player player) {
        return Math.hypot(player.getVelocity().getX(), player.getVelocity().getZ());
    }

    private void erupt(AttackContext ctx, Location mark) {
        if (mark.getWorld() == null) {
            return;
        }
        Fx.expandingRings(plugin, mark, Particle.SOUL, radius, 3, 2L);
        Fx.sound(mark, Sound.BLOCK_SOUL_SAND_BREAK, 1.0f, 0.5f);

        for (int i = 0; i < FANGS_PER_MARK; i++) {
            double angle = 2 * Math.PI * i / FANGS_PER_MARK;
            Location spot = mark.clone().add(Math.cos(angle) * radius * 0.6, 0, Math.sin(angle) * radius * 0.6);
            EvokerFangs fangs = mark.getWorld().spawn(spot, EvokerFangs.class);
            fangs.setOwner(ctx.boss());
            ctx.instance().trackEntity(fangs);
        }

        double r2 = radius * radius;
        for (Player player : Arena.combatants(mark, radius)) {
            if (player.getLocation().distanceSquared(mark) > r2) {
                continue;
            }
            player.damage(damage, ctx.boss());
            // Slowness rather than a teleport lock: the player keeps control, they just cannot walk out of
            // the encirclement the horde is closing around them, which is where the real cost is.
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, rootTicks, 5));
            Fx.bloodSpray(player.getLocation().add(0, 1, 0));
        }
    }
}

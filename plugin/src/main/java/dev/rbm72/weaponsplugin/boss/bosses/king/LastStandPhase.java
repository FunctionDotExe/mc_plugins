package dev.rbm72.weaponsplugin.boss.bosses.king;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * <b>P4 — Last Stand.</b> The throne room is a field of anvils, he swings at everyone, and between
 * sweeps he picks the player closest to death and charges them.
 * <p>
 * The Execution is the payoff for every anvil that has landed since P3. It has a long, unmistakable
 * wind-up and a straight scar on the ground, and it has exactly two answers, both of which use something
 * the fight itself created:
 * <ul>
 *   <li><b>Body-block.</b> Anyone standing between him and the marked player eats it instead, and it is
 *       tuned not to kill a healthy interceptor. That is the whole of the phase's coordination test: the
 *       group has to notice who is about to die and physically get in the way.</li>
 *   <li><b>Break the line.</b> If anything solid stands between them — a landed anvil, a column the group
 *       stacked out of the arena's own cobble — the charge collides with it and staggers him. This is the
 *       designed solo substitute (§0.2 rule 7), not a disabled mechanic: a different solve, available to
 *       one player, using the terrain the earlier phases spent on them.</li>
 * </ul>
 * Damage is no longer filtered at all here. He has nothing left to hide behind and nobody left to name,
 * so the only rule in the last band is the one the players make about where they stand.
 */
final class LastStandPhase extends KingPhaseMechanic {

    private int chargeCountdown;
    private int windupLeft;
    private Player marked;
    private Vector chargeAim;

    LastStandPhase(BossInstance instance) {
        super(instance, "Last Stand", 0.0);
    }

    @Override
    protected void onArm() {
        fight.duel().abandon();
        fight.judgment().setRunning(true);
        chargeCountdown = fight.config().num("execution-first-delay-ticks", 120);
    }

    /**
     * The final phase's objective is satisfied the instant it starts. There is nothing left to gate: the
     * band ends when he dies, and pinning a seam that has no next phase behind it would only ever be a
     * way to make the kill feel arbitrary.
     */
    @Override
    protected boolean objectiveMet() {
        return true;
    }

    @Override
    protected boolean announcesCompletion() {
        return false;
    }

    /** No filter at all — see the class header. */
    @Override
    public double filterDamage(Player attacker, double damage) {
        return damage;
    }

    @Override
    protected void onPulse(int intervalTicks) {
        if (windupLeft > 0) {
            windupLeft -= intervalTicks;
            drawCharge();
            if (windupLeft <= 0) {
                resolveCharge();
            }
            return;
        }
        chargeCountdown -= intervalTicks;
        if (chargeCountdown <= 0) {
            beginCharge();
        }
    }

    // ---------------------------------------------------------------- execution

    /**
     * Marks the player nearest to death. Deliberately the lowest absolute health <em>fraction</em>, the
     * same rule the framework's own targeting uses — so the mechanic is legible ("he goes for whoever is
     * hurt") and the counterplay to it is legible too ("do not let anyone sit at low HP").
     */
    private void beginCharge() {
        Player victim = weakest();
        if (victim == null) {
            chargeCountdown = 40;
            return;
        }
        marked = victim;
        windupLeft = Math.max(30, fight.config().num("execution-windup-ticks", 50));
        Location at = instance.entity().getLocation();
        chargeAim = victim.getLocation().toVector().subtract(at.toVector()).setY(0);
        chargeAim = chargeAim.lengthSquared() > 1.0E-4 ? chargeAim.normalize() : new Vector(1, 0, 0);

        Fx.sound(at, Sound.ENTITY_RAVAGER_ROAR, 1.6f, 0.5f);
        Fx.sound(at, Sound.ITEM_TRIDENT_THUNDER, 1.2f, 0.4f);
        fight.plugin().actionBarHub().flash(victim,
                Component.text("HE HAS MARKED YOU — get cover or get a body in the way", NamedTextColor.DARK_RED),
                2600L, ActionBarHub.PRIORITY_NOTICE);
        instance.showTitle(Component.text("EXECUTION", NamedTextColor.DARK_RED),
                Component.text(victim.getName() + " is marked", NamedTextColor.GRAY));
    }

    /** The ground scar: a straight line from him to the mark, tightening as the wind-up runs out. */
    private void drawCharge() {
        if (marked == null || !marked.isValid()) {
            return;
        }
        Location at = instance.entity().getLocation();
        Fx.line(at.clone().add(0, 0.3, 0), marked.getLocation().clone().add(0, 0.3, 0), Particle.CRIT, 24);
        Telegraph.dangerZone(marked.getLocation(), 1.8);
        Fx.coloredBurst(at.clone().add(0, 2.0, 0), KingFight.KING_SHADOW, 2.2f, 12, 0.3);
    }

    private void resolveCharge() {
        Player victim = marked;
        marked = null;
        chargeCountdown = Math.max(60, fight.config().num("execution-interval-ticks", 200));
        if (victim == null || !victim.isValid() || victim.isDead()) {
            return;
        }
        Location from = instance.entity().getLocation();
        Location to = victim.getLocation();

        // Cover first: a charge that runs into a wall never reaches anybody, so the terrain answer has
        // to be checked before the body answer or a player hiding behind an anvil would still be hit
        // through it whenever a teammate happened to be standing in the open.
        if (fight.judgment().blockedBetween(from.clone().add(0, 1, 0), to.clone().add(0, 1, 0))) {
            collide(from, to);
            return;
        }

        Player interceptor = firstInLine(from, to, victim);
        Player struck = interceptor != null ? interceptor : victim;
        double damage = interceptor != null
                ? fight.config().dbl("execution-blocked-damage", 14.0)
                : fight.config().dbl("execution-damage-fraction", 0.7) * maxHealthOf(victim);

        dash(from, to);
        struck.damage(damage, instance.entity());
        struck.setVelocity(to.toVector().subtract(from.toVector()).setY(0.35).normalize().multiply(0.9));
        Fx.coloredBurst(struck.getLocation().add(0, 1.2, 0), KingFight.KING_SHADOW, 2.6f, 60, 0.9);
        Fx.sound(struck.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.6f, 0.5f);
        if (interceptor != null) {
            fight.plugin().actionBarHub().flash(interceptor,
                    Component.text("YOU TOOK IT", NamedTextColor.GOLD), 2200L, ActionBarHub.PRIORITY_NOTICE);
        }
    }

    /**
     * The charge hitting cover instead of a player: he is staggered for a real window, which is the
     * reward for having used the arena. Longer than the bell's stagger on purpose — this one costs a
     * piece of terrain and a correctly-read wind-up, and it is the solo player's only breathing room.
     */
    private void collide(Location from, Location to) {
        instance.stagger(fight.config().num("execution-collide-stagger-ticks", 80));
        Location impact = from.clone().add(to.toVector().subtract(from.toVector()).multiply(0.5));
        Fx.blockBurst(impact, org.bukkit.Material.ANVIL, 40, 0.8);
        Fx.sound(impact, Sound.BLOCK_ANVIL_LAND, 1.8f, 0.5f);
        Fx.expandingRings(fight.plugin(), impact, Particle.CRIT, 5.0, 3, 2L);
        instance.showTitle(Component.text("HE CRASHES", NamedTextColor.GOLD),
                Component.text("The wreckage held", NamedTextColor.GRAY));
    }

    /**
     * The first combatant standing on the charge line who is not the mark — the body-block. Measured as
     * perpendicular distance to the segment, so "in the way" means genuinely in the way rather than
     * merely somewhere in the same direction.
     */
    private Player firstInLine(Location from, Location to, Player exclude) {
        Vector direction = to.toVector().subtract(from.toVector()).setY(0);
        double length = direction.length();
        if (length < 0.5) {
            return null;
        }
        direction = direction.normalize();
        double halfWidth = fight.config().dbl("execution-block-half-width", 1.3);
        Player best = null;
        double bestAlong = Double.MAX_VALUE;
        for (Player player : fight.combatants()) {
            if (player.equals(exclude)) {
                continue;
            }
            Vector offset = player.getLocation().toVector().subtract(from.toVector()).setY(0);
            double along = offset.dot(direction);
            if (along < 0.5 || along > length) {
                continue;
            }
            double lateral = offset.clone().subtract(direction.clone().multiply(along)).length();
            if (lateral <= halfWidth && along < bestAlong) {
                bestAlong = along;
                best = player;
            }
        }
        return best;
    }

    /** Moves him bodily along the charge, so the hit lands where the scar said it would. */
    private void dash(Location from, Location to) {
        Location landing = to.clone();
        landing.setYaw(from.getYaw());
        landing.setPitch(0f);
        instance.entity().teleport(landing);
        Fx.line(from.clone().add(0, 0.5, 0), landing.clone().add(0, 0.5, 0), Particle.SWEEP_ATTACK, 20);
    }

    private Player weakest() {
        Player best = null;
        double bestFraction = Double.MAX_VALUE;
        for (Player player : fight.combatants()) {
            double fraction = player.getHealth() / maxHealthOf(player);
            if (fraction < bestFraction) {
                bestFraction = fraction;
                best = player;
            }
        }
        return best;
    }

    private static double maxHealthOf(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute != null ? Math.max(1.0, attribute.getValue()) : 20.0;
    }

    @Override
    protected Component readoutText() {
        if (marked != null) {
            return Component.text("EXECUTION on " + marked.getName() + " — block the line", NamedTextColor.DARK_RED);
        }
        return Component.text("the wreckage is your cover — " + fight.judgment().landedCount() + " anvils down",
                NamedTextColor.GOLD);
    }

    @Override
    protected double readoutProgress() {
        if (windupLeft <= 0) {
            return 0.0;
        }
        int total = Math.max(1, fight.config().num("execution-windup-ticks", 50));
        return 1.0 - Math.max(0.0, (double) windupLeft / total);
    }
}

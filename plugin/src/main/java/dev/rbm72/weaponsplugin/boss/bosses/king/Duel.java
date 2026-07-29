package dev.rbm72.weaponsplugin.boss.bosses.king;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import dev.rbm72.weaponsplugin.util.Grounded;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The duel: one named Challenger, three readable combos, and a rotation that passes on events rather
 * than on a timer.
 * <p>
 * This is the one boss in the roster where <b>who is allowed to hurt him is a mechanic</b>. He is never
 * invulnerable — {@link #filterDamage} simply scales an outsider's blow down to a tenth, which is a rule
 * about how you are fighting rather than permission he grants. That distinction is the whole point: a
 * group ignoring the duel is not locked out, it is merely doing almost nothing, and the fix is available
 * to them at any second by letting their Challenger take the front.
 * <p>
 * <b>The rotation is the reason no one player tanks this fight.</b> The mantle passes on two events and
 * only two: the Challenger lands a hit inside the recovery window after a combo (a <em>riposte</em>, the
 * reward for reading the tell correctly), or the Challenger takes a Wound from one of the combos. Both
 * are things that happen because of how the duel is going, so the focal point of the fight is constantly
 * moving without anything ever announcing "swap now".
 * <p>
 * Each combo has a distinct pose, a distinct shape on the floor, and a distinct correct answer — step
 * back out of the cleave line, jump or step <em>inside</em> the sweep, strafe off the thrust. None of
 * them is dodgeable by holding still, and none of them is unavoidable.
 */
final class Duel {

    /** The three combos, in the order they are first taught. Shuffled from the second cycle on. */
    private enum Combo {
        /** Sword high, floor cracks in a line ahead. Answer: step back. */
        OVERHEAD_CLEAVE,
        /** Sword low, dust ring on the floor. Answer: jump, or step inside his guard. */
        RING_SWEEP,
        /** Sword drawn back, one narrow line. Answer: strafe. */
        THRUST
    }

    private enum Stage {
        /** Between combos — he is circling, and the Challenger is free to press. */
        IDLE,
        /** The pose is up and the shape is on the floor. Nothing has landed yet. */
        TELEGRAPH,
        /** The blow has landed and his guard is down. A hit here is a riposte. */
        RIPOSTE
    }

    private final KingFight fight;

    private UUID challenger;
    private boolean active = true;
    /** True once he has abandoned the duel outright (P3 onward) — he swings at everyone. */
    private boolean abandoned;

    private Stage stage = Stage.IDLE;
    private Combo combo;
    private int stageTicksLeft;
    private int telegraphTotalTicks;
    /** Snapshot of his facing when the pose went up, so the payload lands where the telegraph pointed. */
    private Vector aim = new Vector(1, 0, 0);

    private final List<Combo> cycle = new ArrayList<>();
    private int cycleIndex;
    private boolean firstCycle = true;

    private int rotations;
    private int ripostes;
    /** Every player who has held the mantle at least once — what P1's "full rotation" objective actually checks. */
    private final Set<UUID> everHeld = new HashSet<>();
    /** Milliseconds of continuous "nobody is duelling him" before he takes a sip of health back. */
    private long neglectedSinceMs;

    Duel(KingFight fight) {
        this.fight = fight;
        Collections.addAll(cycle, Combo.OVERHEAD_CLEAVE, Combo.RING_SWEEP, Combo.THRUST);
    }

    // ---------------------------------------------------------------- state

    /** Who he currently recognises, or null when nobody does. */
    Player challenger() {
        Player player = challenger == null ? null : fight.plugin().getServer().getPlayer(challenger);
        return player != null && player.isOnline() && Arena.isCombatant(player) ? player : null;
    }

    boolean isChallenger(Player player) {
        return player != null && player.getUniqueId().equals(challenger);
    }

    /** Completed Challenger hand-offs. P1 exits on a full rotation of the group through the mantle. */
    int rotations() {
        return rotations;
    }

    /** How many distinct players have held the mantle at least once — the real measure of "a full rotation". */
    int distinctHolders() {
        return everHeld.size();
    }

    int ripostes() {
        return ripostes;
    }

    /**
     * Ends the duel for good — P3's "he abandons the duel". The mark comes off, everyone's hits count
     * fully, and the combos keep coming but no longer name a target.
     */
    void abandon() {
        abandoned = true;
        clearMark();
        challenger = null;
    }

    boolean abandoned() {
        return abandoned;
    }

    /** Suspends combo generation without ending the duel — for a phase driving its own melee rhythm. */
    void setActive(boolean active) {
        this.active = active;
    }

    void clear() {
        clearMark();
        challenger = null;
        stage = Stage.IDLE;
    }

    // ---------------------------------------------------------------- damage rule

    /**
     * The duel expressed as arithmetic rather than as a wall.
     * <p>
     * An outsider's hit is cut to {@code duel-outsider-fraction} of its value — visibly landing, audibly
     * connecting, and worth almost nothing. A Challenger striking inside the riposte window gets a spike
     * and passes the mantle on. Everything else is untouched.
     */
    double filterDamage(Player attacker, double damage) {
        if (abandoned) {
            return damage;
        }
        if (!isChallenger(attacker)) {
            return damage * fight.config().dbl("duel-outsider-fraction", 0.10);
        }
        if (stage == Stage.RIPOSTE) {
            return damage * fight.config().dbl("duel-riposte-multiplier", 2.2);
        }
        return damage;
    }

    /**
     * Fired once a Challenger's hit has actually resolved. A hit inside the recovery window is the
     * riposte: it closes the window (so one opening is one reward, not a free-for-all for a fast weapon)
     * and hands the mantle to somebody else.
     */
    void onBossDamaged(Player attacker, double dealt) {
        if (abandoned || dealt <= 0 || !isChallenger(attacker) || stage != Stage.RIPOSTE) {
            return;
        }
        ripostes++;
        stage = Stage.IDLE;
        stageTicksLeft = fight.config().num("duel-combo-gap-ticks", 30);
        Location at = fight.instance().entity().getLocation();
        Fx.coloredBurst(at.clone().add(0, 1.4, 0), KingFight.KING_GOLD, 2.0f, 40, 0.7);
        Fx.sound(at, Sound.ITEM_SHIELD_BREAK, 1.1f, 1.4f);
        notice(attacker, Component.text("RIPOSTE — the mantle passes", NamedTextColor.GOLD));
        rotate("riposte");
    }

    // ---------------------------------------------------------------- pulse

    void pulse(int intervalTicks) {
        if (abandoned) {
            // No Challenger to mark or neglect-check any more, but the combos themselves must keep
            // coming — P3/P4's entire point is that he now swings at everyone (see #victims()).
            if (active) {
                advance(intervalTicks, null);
            }
            return;
        }
        ensureChallenger();
        Player marked = challenger();
        if (marked == null) {
            return;
        }
        drawMark(marked);
        chargeNeglect(marked, intervalTicks);
        if (!active) {
            return;
        }
        advance(intervalTicks, marked);
    }

    /**
     * Picks a Challenger when there is none, and only when there is none. Rotation is event-driven by
     * design (see the class header), so this is the cold-start path and the recovery path for a
     * Challenger who logged out mid-duel — never a timer that would quietly turn the mantle into one.
     */
    private void ensureChallenger() {
        if (challenger() != null) {
            return;
        }
        List<Player> present = fight.combatants();
        if (present.isEmpty()) {
            challenger = null;
            return;
        }
        markChallenger(present.get(ThreadLocalRandom.current().nextInt(present.size())));
    }

    /** Hands the mantle to somebody who is not currently holding it, or keeps it if nobody else is here. */
    private void rotate(String reason) {
        List<Player> present = new ArrayList<>(fight.combatants());
        Player current = challenger();
        present.removeIf(p -> p.equals(current));
        clearMark();
        if (present.isEmpty()) {
            // Solo, or everyone else is down: the mark stays put. The design is explicit that a lone
            // player simply <em>is</em> the Challenger for the whole fight rather than losing the duel.
            if (current != null) {
                markChallenger(current);
            }
            return;
        }
        rotations++;
        // Bias toward whoever hasn't held the mantle yet, so "a full rotation" actually converges instead
        // of being a coupon-collector gamble between the same two players.
        List<Player> unvisited = new ArrayList<>(present);
        unvisited.removeIf(p -> everHeld.contains(p.getUniqueId()));
        List<Player> pool = unvisited.isEmpty() ? present : unvisited;
        markChallenger(pool.get(ThreadLocalRandom.current().nextInt(pool.size())));
        Location at = fight.instance().entity().getLocation();
        Fx.sound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.6f);
        fight.plugin().getLogger().fine(() -> "Fallen King challenger rotated on " + reason);
    }

    private void markChallenger(Player player) {
        challenger = player.getUniqueId();
        everHeld.add(player.getUniqueId());
        neglectedSinceMs = 0L;
        player.setGlowing(true);
        notice(player, Component.text("YOU ARE THE CHALLENGER", NamedTextColor.GOLD));
        Fx.sound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 0.8f);
    }

    /**
     * Takes the outline back off whoever had it. Reads the player straight out of the server rather than
     * through {@link #challenger()}, because the mark has to come off someone who has already stopped
     * qualifying as a combatant — that is precisely when the glow would otherwise be left stuck on.
     */
    private void clearMark() {
        if (challenger == null) {
            return;
        }
        Player previous = fight.plugin().getServer().getPlayer(challenger);
        if (previous != null) {
            previous.setGlowing(false);
        }
    }

    /** The tether: a real chain of particles from his hand to the person he has named. */
    private void drawMark(Player marked) {
        Location from = fight.instance().entity().getEyeLocation();
        Location to = marked.getLocation().add(0, 1.0, 0);
        Fx.line(from, to, Particle.WAX_OFF, 12);
        Fx.coloredBurst(to, KingFight.KING_GOLD, 0.9f, 3, 0.25);
    }

    /**
     * The rent on ignoring the duel. If the Challenger is nowhere near him for long enough, he takes a
     * small sip of health back — the design's "King gains a small heal if the group ignores the duel for
     * too long". Not a punishment for kiting him during a shard relay: the clock resets the moment the
     * Challenger closes, and the heal is deliberately smaller than a single honest exchange.
     */
    private void chargeNeglect(Player marked, int intervalTicks) {
        double range = fight.config().dbl("duel-engagement-range", 9.0);
        LivingEntity king = fight.instance().entity();
        boolean engaged = marked.getWorld().equals(king.getWorld())
                && marked.getLocation().distance(king.getLocation()) <= range;
        long now = System.currentTimeMillis();
        if (engaged) {
            neglectedSinceMs = 0L;
            return;
        }
        if (neglectedSinceMs == 0L) {
            neglectedSinceMs = now;
            return;
        }
        if (now - neglectedSinceMs < fight.config().num("duel-neglect-ms", 12_000)) {
            return;
        }
        neglectedSinceMs = now;
        double heal = fight.config().dbl("duel-neglect-heal", 6.0);
        king.setHealth(Math.min(king.getHealth() + heal,
                king.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
        Fx.coloredBurst(king.getLocation().add(0, 1.4, 0), KingFight.KING_GOLD, 1.4f, 30, 0.6);
        Fx.sound(king.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.6f);
        notice(marked, Component.text("He is recovering — get back in front of him", NamedTextColor.RED));
    }

    // ---------------------------------------------------------------- combos

    private void advance(int intervalTicks, Player marked) {
        stageTicksLeft -= intervalTicks;
        switch (stage) {
            case IDLE -> {
                if (stageTicksLeft <= 0) {
                    beginCombo(marked);
                }
            }
            case TELEGRAPH -> {
                drawTelegraph(marked);
                if (stageTicksLeft <= 0) {
                    land(marked);
                }
            }
            case RIPOSTE -> {
                drawOpening();
                if (stageTicksLeft <= 0) {
                    stage = Stage.IDLE;
                    stageTicksLeft = fight.config().num("duel-combo-gap-ticks", 30);
                }
            }
            default -> {
            }
        }
    }

    private void beginCombo(Player marked) {
        if (cycleIndex >= cycle.size()) {
            cycleIndex = 0;
            // Fixed order for the first cycle so all three tells are taught cleanly, shuffled after so
            // the duel becomes a read rather than a memorised script.
            if (firstCycle) {
                firstCycle = false;
            }
            Collections.shuffle(cycle);
        }
        combo = cycle.get(cycleIndex++);
        stage = Stage.TELEGRAPH;
        telegraphTotalTicks = telegraphTicksFor(combo);
        stageTicksLeft = telegraphTotalTicks;
        aim = facing(marked);
        pose();
    }

    /**
     * Wind-up length per combo, scaled by how much the blow costs to eat. §0.2 rule 3 sets the floor at
     * 15 ticks; the Thrust, being the hardest single hit in the duel, gets the longest tell.
     */
    private int telegraphTicksFor(Combo which) {
        return switch (which) {
            case OVERHEAD_CLEAVE -> Math.max(15, fight.config().num("cleave-telegraph-ticks", 26));
            case RING_SWEEP -> Math.max(15, fight.config().num("sweep-telegraph-ticks", 20));
            case THRUST -> Math.max(15, fight.config().num("thrust-telegraph-ticks", 30));
        };
    }

    private void pose() {
        Location at = fight.instance().entity().getLocation();
        switch (combo) {
            case OVERHEAD_CLEAVE -> {
                Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);
                Fx.coloredBurst(at.clone().add(0, 2.6, 0), KingFight.KING_GOLD, 2.4f, 26, 0.3);
            }
            case RING_SWEEP -> {
                Fx.sound(at, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.1f, 0.7f);
                Fx.burst(at.clone().add(0, 0.2, 0), Particle.CLOUD, 22, 0.7);
            }
            case THRUST -> {
                Fx.sound(at, Sound.ITEM_TRIDENT_THROW, 1.2f, 0.6f);
                Fx.coloredBurst(at.clone().add(0, 1.2, 0), KingFight.KING_SHADOW, 2.0f, 22, 0.3);
            }
        }
    }

    /** The shape on the floor, redrawn each pulse and tightening as the blow approaches. */
    private void drawTelegraph(Player marked) {
        Location at = fight.instance().entity().getLocation();
        double progress = 1.0 - Math.max(0.0, (double) stageTicksLeft / Math.max(1, telegraphTotalTicks));
        switch (combo) {
            case OVERHEAD_CLEAVE -> {
                Location end = at.clone().add(aim.clone().multiply(cleaveRange()));
                Fx.line(at.clone().add(0, 0.2, 0), end.clone().add(0, 0.2, 0), Particle.CRIT, 16);
                Fx.coloredRing(end, KingFight.KING_GOLD, (float) (1.2 + progress), 1.4, 14, 0);
            }
            case RING_SWEEP -> Fx.coloredRing(at, KingFight.KING_GOLD, (float) (1.2 + progress),
                    sweepOuterRadius(), 30, progress * 4);
            case THRUST -> {
                Location end = at.clone().add(aim.clone().multiply(thrustRange()));
                Fx.line(at.clone().add(0, 1.0, 0), end.clone().add(0, 1.0, 0), Particle.SOUL_FIRE_FLAME, 20);
            }
        }
        // The pose is the tell; keeping him pointed at his own aim vector is what makes "step out of the
        // line" an honest instruction rather than a guess about where he will have turned by then.
        if (marked != null && fight.instance().entity() instanceof org.bukkit.entity.Mob mob) {
            mob.lookAt(marked.getEyeLocation());
        }
    }

    private void drawOpening() {
        Location at = fight.instance().entity().getLocation();
        // Guard visibly down: no gold, low sparse smoke. The absence of his own aura is the tell.
        Fx.burst(at.clone().add(0, 1.0, 0), Particle.SMOKE, 6, 0.5);
        Fx.coloredRing(at, KingFight.KING_SHADOW, 1.0f, 1.8, 10, 0);
    }

    private void land(Player marked) {
        Location at = fight.instance().entity().getLocation();
        switch (combo) {
            case OVERHEAD_CLEAVE -> cleave(at);
            case RING_SWEEP -> sweep(at);
            case THRUST -> thrust(at);
        }
        stage = Stage.RIPOSTE;
        stageTicksLeft = fight.config().num("duel-riposte-ticks", 20);
        Fx.sound(at, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 0.6f);
        if (marked != null) {
            notice(marked, Component.text("His guard is down", NamedTextColor.GREEN));
        }
    }

    /**
     * Overhead Cleave: a straight line ahead, and the floor genuinely breaks where it lands. The crater
     * is the memory of the swing — §0.1's rule that the arena should read as a record of the fight — and
     * it is why standing in the same spot through two cleaves leaves you in a hole.
     */
    private void cleave(Location at) {
        double range = cleaveRange();
        double halfWidth = fight.config().dbl("cleave-half-width", 1.8);
        double damage = fight.config().dbl("cleave-damage", 11.0);
        for (Player player : victims()) {
            if (distanceToRay(at, aim, player.getLocation(), range) <= halfWidth) {
                hurt(player, damage);
                player.setVelocity(aim.clone().multiply(0.4).setY(0.25));
            }
        }
        Location impact = at.clone().add(aim.clone().multiply(range * 0.6));
        Grief.breakCrater(fight.griefContext(), impact.clone().add(0, -0.5, 0),
                fight.config().dbl("cleave-crater-radius", 1.8));
        Fx.line(at.clone().add(0, 0.3, 0), at.clone().add(aim.clone().multiply(range)).add(0, 0.3, 0),
                Particle.SWEEP_ATTACK, 14);
        Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.4f, 0.6f);
    }

    /**
     * Ring Sweep: an annulus, not a disc. Standing <em>inside</em> his guard is safe, which is what makes
     * "step in" a real answer alongside "jump" and stops the combo from being one more get-out circle.
     */
    private void sweep(Location at) {
        double outer = sweepOuterRadius();
        double inner = fight.config().dbl("sweep-inner-radius", 1.6);
        double damage = fight.config().dbl("sweep-damage", 8.0);
        for (Player player : victims()) {
            double distance = flat(at, player.getLocation());
            if (distance > outer || distance < inner) {
                continue;
            }
            // Jumped it: the sweep is low, so anyone genuinely off the ground clears it.
            if (!Grounded.onGround(player) && player.getLocation().getY() > at.getY() + 0.6) {
                Fx.sound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1.0f, 1.4f);
                continue;
            }
            hurt(player, damage);
            Vector out = player.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (out.lengthSquared() > 1.0E-4) {
                player.setVelocity(out.normalize().multiply(1.1).setY(0.4));
            }
        }
        Fx.coloredRing(at, KingFight.KING_GOLD, 2.0f, outer, 40, 0);
        Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.7f);
    }

    /**
     * Thrust: the hardest single hit, on the narrowest line, and the only one that leaves something
     * behind on the player — a bleed that outpaces natural regeneration, so overhealing does not
     * trivialise it (§1.6, "infinite healing").
     */
    private void thrust(Location at) {
        double range = thrustRange();
        double halfWidth = fight.config().dbl("thrust-half-width", 1.0);
        double damage = fight.config().dbl("thrust-damage", 15.0);
        int bleedTicks = fight.config().num("thrust-bleed-ticks", 90);
        for (Player player : victims()) {
            if (distanceToRay(at, aim, player.getLocation(), range) > halfWidth) {
                continue;
            }
            hurt(player, damage);
            player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, bleedTicks, 0, false, true));
            notice(player, Component.text("BLEEDING", NamedTextColor.DARK_RED));
        }
        Fx.line(at.clone().add(0, 1.0, 0), at.clone().add(aim.clone().multiply(range)).add(0, 1.0, 0),
                Particle.CRIT, 24);
        Fx.sound(at, Sound.ITEM_TRIDENT_HIT, 1.4f, 0.7f);
    }

    /**
     * Who a combo may actually hit. Before he abandons the duel this is the Challenger alone — the fight
     * is a duel and hitting bystanders with it would make the mantle a punishment rather than a role.
     * Afterwards it is everyone, which is the entire physical difference P3 makes.
     */
    private List<Player> victims() {
        if (abandoned) {
            return fight.combatants();
        }
        Player marked = challenger();
        return marked == null ? List.of() : List.of(marked);
    }

    /**
     * A combo connecting is a Wound, and a Wound passes the mantle. Together with the riposte that gives
     * the rotation its two symmetrical triggers: you hand it on by reading him correctly, or you hand it
     * on by failing to.
     */
    private void hurt(Player player, double amount) {
        if (player == null || !player.isValid() || player.isDead()) {
            return;
        }
        player.damage(amount, fight.instance().entity());
        if (!abandoned && isChallenger(player)) {
            notice(player, Component.text("WOUNDED — the mantle passes", NamedTextColor.RED));
            rotate("wound");
        }
    }

    // ---------------------------------------------------------------- geometry

    private double cleaveRange() {
        return fight.config().dbl("cleave-range", 7.0);
    }

    private double sweepOuterRadius() {
        return fight.config().dbl("sweep-radius", 4.5);
    }

    private double thrustRange() {
        return fight.config().dbl("thrust-range", 9.0);
    }

    private Vector facing(Player marked) {
        Location at = fight.instance().entity().getLocation();
        if (marked == null) {
            Vector direction = at.getDirection().setY(0);
            return direction.lengthSquared() > 1.0E-4 ? direction.normalize() : new Vector(1, 0, 0);
        }
        Vector toward = marked.getLocation().toVector().subtract(at.toVector()).setY(0);
        return toward.lengthSquared() > 1.0E-4 ? toward.normalize() : new Vector(1, 0, 0);
    }

    /**
     * Perpendicular distance from {@code point} to the segment starting at {@code origin} along
     * {@code direction} for {@code length} blocks, or a large number if the point is off either end.
     * Horizontal only: a line attack on the floor has no business caring how high someone is standing.
     */
    private static double distanceToRay(Location origin, Vector direction, Location point, double length) {
        if (origin.getWorld() == null || point.getWorld() == null || !origin.getWorld().equals(point.getWorld())) {
            return Double.MAX_VALUE;
        }
        Vector offset = point.toVector().subtract(origin.toVector()).setY(0);
        double along = offset.dot(direction);
        if (along < -0.5 || along > length) {
            return Double.MAX_VALUE;
        }
        return offset.clone().subtract(direction.clone().multiply(along)).length();
    }

    private static double flat(Location a, Location b) {
        if (a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void notice(Player player, Component message) {
        fight.plugin().actionBarHub().flash(player, message, 2000L, ActionBarHub.PRIORITY_NOTICE);
    }
}

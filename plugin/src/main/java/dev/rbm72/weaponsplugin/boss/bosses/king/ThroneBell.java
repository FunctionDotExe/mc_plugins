package dev.rbm72.weaponsplugin.boss.bosses.king;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * The bell on the throne dais: a real bell block, rung by hand, that staggers the King and turns him to
 * face whoever rang it.
 * <p>
 * It is the only counterplay to P3's facing rule, and it is a <b>trip</b> rather than a button — the dais
 * sits two thirds of the way to the arena wall, so somebody has to leave the fight to ring it while the
 * rest hold his front and his back. That is the coordination the phase is actually testing.
 * <p>
 * The re-orient matters as much as the stagger: turning him to face the ringer is what puts his exposed
 * spine toward everyone else. A stagger alone would just be free damage; a stagger that changes where he
 * is looking is a positioning puzzle with an answer.
 * <p>
 * <b>Interaction is listened for, not polled.</b> Every other physical prop in this fight is read back
 * out of the world because blocks can leave for reasons no listener sees — but a bell being rung is an
 * event and nothing else, with no world state left behind to read a moment later. Registered when the
 * bell is placed and unregistered in {@link #discard()}; a leaked listener here would go on staggering a
 * boss that no longer exists.
 */
final class ThroneBell implements Listener {

    private final KingFight fight;

    private Block bellBlock;
    private boolean listening;
    private int rings;
    private long readyAtMs;

    ThroneBell(KingFight fight) {
        this.fight = fight;
    }

    /** Times the bell has actually been rung this fight — P3's exit condition counts these. */
    int rings() {
        return rings;
    }

    boolean isUp() {
        return bellBlock != null && bellBlock.getType() == Material.BELL;
    }

    /**
     * Hangs the bell. Idempotent, and called from P1 rather than P3: the design says the bell is
     * "available all fight, becomes mandatory P3", and a group that has been ringing it out of curiosity
     * since the opening minute should arrive at the phase already knowing what it does.
     */
    void raise() {
        if (bellBlock != null) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location dais = fight.dais();
        Block support = world.getBlockAt(dais.getBlockX(), dais.getBlockY() - 1, dais.getBlockZ());
        if (support.getType().isAir()) {
            Grief.setMechanicBlock(fight.griefContext(), support, Material.POLISHED_ANDESITE);
        }
        Block spot = world.getBlockAt(dais.getBlockX(), dais.getBlockY(), dais.getBlockZ());
        if (!Grief.setMechanicBlock(fight.griefContext(), spot, Material.BELL)) {
            // Out of ledger budget, or the dais landed on something the fight must not touch. Without a
            // bell in the world there is nothing to ring, so the phase's readout says so rather than the
            // group hunting for a prop that was never placed.
            fight.plugin().getLogger().warning(
                    "Fallen King could not place his throne bell — P3's stagger will be unavailable.");
            return;
        }
        bellBlock = spot;
        startListening();
        KingFight.royalFlourish(dais, Sound.BLOCK_BELL_RESONATE, 0.8f);
        Fx.glowPillar(fight.plugin(), dais.clone().add(0, 1, 0), Material.GOLD_BLOCK, 0.2f, 3.0f, 200);
    }

    private void startListening() {
        if (listening) {
            return;
        }
        listening = true;
        fight.plugin().getServer().getPluginManager().registerEvents(this, fight.plugin());
    }

    /** Keeps the bell visible from across the arena, and nudges P3 when it has gone unrung. */
    void pulse() {
        if (!isUp()) {
            return;
        }
        Location at = bellBlock.getLocation().add(0.5, 0.5, 0.5);
        Fx.coloredBurst(at, KingFight.KING_GOLD, 1.0f, 4, 0.25);
        if (ready()) {
            Fx.coloredRing(at, KingFight.KING_GOLD, 1.2f, 1.6, 12, 0);
        }
    }

    private boolean ready() {
        return System.currentTimeMillis() >= readyAtMs;
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        tryRing(event.getPlayer(), event.getClickedBlock());
    }

    /**
     * Left-clicking counts too. A player's instinct in a boss fight is to hit the thing, and a bell that
     * only answered to a right-click would read as broken to everyone who swung at it first.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeftClick(BlockDamageEvent event) {
        tryRing(event.getPlayer(), event.getBlock());
    }

    private void tryRing(Player player, Block clicked) {
        if (clicked == null || bellBlock == null || !clicked.equals(bellBlock)) {
            return;
        }
        if (!Arena.isCombatant(player) || !ready()) {
            return;
        }
        ring(player);
    }

    // ---------------------------------------------------------------- the ring

    private void ring(Player ringer) {
        rings++;
        readyAtMs = System.currentTimeMillis() + fight.config().num("bell-cooldown-ticks", 100) * 50L;

        int stagger = fight.config().num("bell-stagger-ticks", 60);
        if (fight.playerCount() <= 1) {
            // Solo: the trip to the dais and back is the entire cost, and there is nobody holding his
            // front while it is made. A longer stagger is what buys that round trip back.
            stagger = fight.config().num("bell-stagger-ticks-solo", 110);
        }
        fight.instance().stagger(stagger);

        LivingEntity king = fight.instance().entity();
        faceRinger(king, ringer);

        Location at = bellBlock.getLocation().add(0.5, 0.5, 0.5);
        Fx.sound(at, Sound.BLOCK_BELL_USE, 2.0f, 0.7f);
        Fx.sound(king.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.6f, 0.6f);
        Fx.expandingRings(fight.plugin(), at, Particle.END_ROD, 10.0, 4, 2L);
        Fx.coloredBurst(king.getLocation().add(0, 1.6, 0), KingFight.KING_GOLD, 2.4f, 60, 0.9);

        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("THE BELL — get behind him", NamedTextColor.GOLD),
                    2400L, ActionBarHub.PRIORITY_NOTICE);
        }
    }

    /**
     * Turns him bodily toward the ringer. Yaw is written directly rather than through a pathfinder look:
     * he is staggered, so his AI is not running, and the whole point of the beat is that his facing is
     * pinned somewhere the group chose for the length of the window.
     */
    private void faceRinger(LivingEntity king, Player ringer) {
        Location at = king.getLocation();
        double dx = ringer.getLocation().getX() - at.getX();
        double dz = ringer.getLocation().getZ() - at.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        at.setYaw(yaw);
        at.setPitch(0f);
        king.teleport(at);
        // Vanilla tracks head yaw separately from body yaw; without this he "faces" the ringer on paper
        // while his model — and therefore the arc the phase's damage rule reads — stays where it was.
        king.setRotation(yaw, 0f);
    }

    // ---------------------------------------------------------------- teardown

    /** Unhooks the listener. The bell block itself is in the ledger and is restored with the arena. */
    void discard() {
        if (listening) {
            listening = false;
            HandlerList.unregisterAll(this);
        }
        bellBlock = null;
    }

    /**
     * Whether {@code attacker} is standing behind the King right now — P3's damage rule, kept here
     * because the bell is what makes the answer to it reachable and the two have to agree on the arc.
     *
     * @param arcDegrees total width of the <em>front</em> arc, centred on his facing
     */
    static boolean isBehind(LivingEntity king, Player attacker, double arcDegrees) {
        Location at = king.getLocation();
        Location from = attacker.getLocation();
        if (at.getWorld() == null || from.getWorld() == null || !at.getWorld().equals(from.getWorld())) {
            return false;
        }
        double dx = from.getX() - at.getX();
        double dz = from.getZ() - at.getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            return false;
        }
        double toAttacker = Math.toDegrees(Math.atan2(dz, dx));
        // Bukkit yaw is measured from +Z toward -X; convert it into the same frame as atan2(dz, dx).
        double facing = at.getYaw() + 90.0;
        double delta = Math.abs(wrap(toAttacker - facing));
        return delta > arcDegrees / 2.0;
    }

    /** Folds an angle into (-180, 180] so the comparison above never trips over the 0/360 seam. */
    private static double wrap(double degrees) {
        double wrapped = (degrees + 180.0) % 360.0;
        if (wrapped < 0) {
            wrapped += 360.0;
        }
        return wrapped - 180.0;
    }
}

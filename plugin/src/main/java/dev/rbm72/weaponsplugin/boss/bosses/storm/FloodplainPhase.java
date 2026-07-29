package dev.rbm72.weaponsplugin.boss.bosses.storm;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Breeze;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <b>P2 — Floodplain.</b> Real water pours across several sections of the floor (see {@link Flooding}),
 * and Thunderstrike bolts land on a clock — any bolt landing in a connected water body damages everyone
 * standing in it, so "safe" is a shape the group has to read rather than a distance from the impact.
 * <p>
 * Exit requires one full strike cycle survived with no rod lost to him during it, on top of the health
 * threshold — batch-1 §3.3. A rod he destroys stays gone for the rest of this phase (the Lightning Rods
 * row's own rule), so losing one raises the stakes on every remaining cycle rather than ending the
 * fight's ability to ever finish this objective outright — only that <em>cycle's</em> loss counts
 * against it.
 */
final class FloodplainPhase extends StormPhaseMechanic {

    private static final int CYCLE_LENGTH = 5;

    private int strikesThisCycle;
    private boolean rodLostThisCycle;
    private int cyclesSurvived;
    private int strikeCountdown;

    FloodplainPhase(BossInstance instance, double exitFraction) {
        super(instance, "Floodplain", exitFraction);
    }

    @Override
    protected void onArm() {
        // Rods lost in P1 don't respawn until "next phase" (§3.4) — this is that phase boundary.
        fight.rods().raiseAll();
        fight.flooding().floodSections();
        strikeCountdown = fight.config().num("thunderstrike-first-delay-ticks", 60);
        spawnBreezeAdds();
    }

    /**
     * §3.4: "P2 onward... real Breeze mobs drop in with sound... count = 1 + 1 per 2 players." Plain
     * vanilla Breezes with their own AI — killing them for wind charges is the counterplay, not a custom
     * fight mechanic on top of them.
     */
    private void spawnBreezeAdds() {
        World world = fight.world();
        if (world == null) {
            return;
        }
        int count = fight.config().num("breeze-count-base", 1) + fight.playerCount() / 2;
        double fraction = fight.config().dbl("breeze-spawn-fraction", 0.75);
        double startAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        Location centre = fight.instance().arena().center();
        double radius = fight.instance().arena().radius() * fraction;
        for (int i = 0; i < count; i++) {
            double angle = startAngle + (Math.PI * 2 * i) / count;
            Location spot = centre.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()) + 1);
            Breeze breeze = world.spawn(spot, Breeze.class);
            breeze.setPersistent(false);
            fight.instance().trackEntity(breeze);
        }
    }

    @Override
    protected void onPulse(int intervalTicks) {
        strikeCountdown -= intervalTicks;
        if (strikeCountdown > 0) {
            return;
        }
        strikeCountdown = fight.config().num("thunderstrike-interval-ticks", 60);

        List<org.bukkit.entity.Player> combatants = fight.combatants();
        if (combatants.isEmpty()) {
            return;
        }
        // Occasionally he goes for a rod instead of a player — batch-1 §3.4: "rods can be destroyed by
        // the Tyrant" — which is what makes protecting them a real job rather than a passive backdrop.
        if (ThreadLocalRandom.current().nextInt(4) == 0) {
            fight.rods().destroyOne();
            rodLostThisCycle = true;
        } else {
            var target = combatants.get(ThreadLocalRandom.current().nextInt(combatants.size()));
            Location at = target.getLocation();
            fight.flooding().strike(at, fight.config().dbl("thunderstrike-conducted-damage", 6.0));
        }
        strikesThisCycle++;
        if (strikesThisCycle >= CYCLE_LENGTH) {
            if (!rodLostThisCycle) {
                cyclesSurvived++;
            }
            strikesThisCycle = 0;
            rodLostThisCycle = false;
        }
    }

    @Override
    protected boolean objectiveMet() {
        return cyclesSurvived >= 1;
    }

    @Override
    protected int progressSignal() {
        return cyclesSurvived * 100 + strikesThisCycle;
    }

    @Override
    protected Component readoutText() {
        return cyclesSurvived >= 1
                ? Component.text("the sky is spent, for now", NamedTextColor.GREEN)
                : Component.text("survive a strike cycle — keep the rods standing", NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, (double) strikesThisCycle / CYCLE_LENGTH);
    }
}

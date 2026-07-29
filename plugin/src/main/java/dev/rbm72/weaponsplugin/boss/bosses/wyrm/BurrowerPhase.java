package dev.rbm72.weaponsplugin.boss.bosses.wyrm;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <b>P2 — The Burrower.</b> It goes underground, real tunnels open in the floor, and it erupts under
 * whoever it was tracking. It is not invulnerable as a gate — while it is physically elsewhere (under
 * the map, hidden) nobody could reach it if they wanted to, which is why {@link
 * BossInstance#setForcedInvulnerable} is scoped tightly to exactly the traveling window and lifted the
 * instant it erupts. Exit requires two surfacings actually punished, on top of the health threshold
 * (batch-4 §1.3) — surfacing and immediately backing off doesn't count.
 */
final class BurrowerPhase extends WyrmPhaseMechanic {

    private enum Stage { SURFACED, TRAVELING, RECOVERING }

    private Stage stage = Stage.SURFACED;
    private int stageTicksLeft;
    private UUID targetId;
    private Location lastKnownTarget;
    private int punishedSurfacings;
    private boolean tookDamageThisRecovery;

    BurrowerPhase(BossInstance instance, double exitFraction) {
        super(instance, exitFraction);
    }

    @Override
    protected void onArm() {
        stage = Stage.SURFACED;
        stageTicksLeft = fight.config().num("burrow-surfaced-ticks-first", 100);
        punishedSurfacings = 0;
        instance.setForcedInvulnerable(false);
    }

    @Override
    protected void onDisarm() {
        fight.burrow().forceSurface();
        instance.setForcedInvulnerable(false);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        switch (stage) {
            case SURFACED -> tickSurfaced(intervalTicks);
            case TRAVELING -> tickTraveling(intervalTicks);
            case RECOVERING -> tickRecovering(intervalTicks);
        }
    }

    private void tickSurfaced(int intervalTicks) {
        stageTicksLeft -= intervalTicks;
        if (stageTicksLeft > 0) {
            return;
        }
        Player target = pickTarget();
        if (target == null) {
            stageTicksLeft = 20;
            return;
        }
        targetId = target.getUniqueId();
        lastKnownTarget = target.getLocation();
        fight.burrow().dive();
        instance.setForcedInvulnerable(true);
        stage = Stage.TRAVELING;
        stageTicksLeft = fight.config().num("burrow-travel-timeout-ticks", 140);
        instance.showTitle(Component.empty(),
                Component.text("IT'S UNDERGROUND — watch for the tremor", NamedTextColor.GRAY));
    }

    private void tickTraveling(int intervalTicks) {
        Player target = targetId != null ? instance.plugin().getServer().getPlayer(targetId) : null;
        if (target != null && target.isOnline() && target.isValid()) {
            lastKnownTarget = target.getLocation();
        }
        Location column = lastKnownTarget != null ? lastKnownTarget : instance.arena().center();
        fight.burrow().tickTravel(column, fight.config().dbl("burrow-travel-speed", 0.6), intervalTicks);

        stageTicksLeft -= intervalTicks;
        double arriveThreshold = fight.config().dbl("burrow-arrive-threshold", 1.5);
        if (fight.burrow().arrivedNear(column, arriveThreshold) || stageTicksLeft <= 0) {
            erupt(column);
        }
    }

    private void erupt(Location column) {
        double damage = fight.config().dbl("burrow-eruption-damage", 14.0);
        double radius = fight.config().dbl("burrow-eruption-radius", 4.0);
        float launch = (float) fight.config().dbl("burrow-eruption-launch", 0.9);
        fight.burrow().erupt(column, damage, radius, launch);
        instance.setForcedInvulnerable(false);
        stage = Stage.RECOVERING;
        stageTicksLeft = fight.config().num("burrow-recovery-ticks", 45);
        tookDamageThisRecovery = false;
        instance.showTitle(Component.empty(),
                Component.text("IT SURFACES — punish it", NamedTextColor.GOLD));
    }

    private void tickRecovering(int intervalTicks) {
        stageTicksLeft -= intervalTicks;
        if (stageTicksLeft > 0) {
            return;
        }
        if (tookDamageThisRecovery) {
            punishedSurfacings++;
        }
        stage = Stage.SURFACED;
        stageTicksLeft = fight.config().num("burrow-surfaced-ticks", 140);
    }

    @Override
    public void onBossDamaged(Player attacker, double damageDealt) {
        if (stage == Stage.RECOVERING && damageDealt > 0) {
            tookDamageThisRecovery = true;
        }
    }

    private Player pickTarget() {
        List<Player> combatants = fight.combatants();
        if (combatants.isEmpty()) {
            return null;
        }
        return combatants.get(ThreadLocalRandom.current().nextInt(combatants.size()));
    }

    @Override
    protected boolean objectiveMet() {
        return punishedSurfacings >= 2;
    }

    @Override
    protected int progressSignal() {
        return punishedSurfacings * 1000 + (tookDamageThisRecovery ? 500 : 0);
    }
}

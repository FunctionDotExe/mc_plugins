package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.boss.PhaseMechanic;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Duel: the boss names one opponent and fights only them. Nobody else's blows land at all — not
 * reduced, ignored — and the boss attacks its chosen opponent almost exclusively.
 * <p>
 * There is no chore here and nothing to break. The boss is permanently "vulnerable"; the question is
 * only <em>who</em> is allowed to fight it, which turns the phase into a job-assignment problem. The
 * duellist has to survive a boss aimed squarely at them while everyone else, unable to contribute
 * damage at all, does the things damage cannot do — pulling adds, staying alive, and being ready to
 * take the mantle. When the duellist drops below a health threshold the boss contemptuously names
 * someone new, so the phase naturally rotates through the group.
 * <p>
 * Distinct from every gate archetype in a way that matters: a gate asks the whole group to do the
 * same thing at once, and rewards stacking up. This forbids stacking up, and each player's job is
 * different from the person standing next to them.
 */
public final class DuelLockMechanic implements PhaseMechanic {

    private static final long NOTICE_MS = 1500;
    private static final long TICK_INTERVAL = 10L;

    private final BossInstance instance;
    private final WeaponsPlugin plugin;
    private final Color color;
    private final double rotateBelowHealthFraction;
    private final int maxDuelTicks;
    private final double outsiderDamageFraction;

    private BukkitTask task;
    private Player duellist;
    private int duelTicks;
    private boolean stopped;

    public DuelLockMechanic(BossInstance instance, Color color, double rotateBelowHealthFraction,
                             int maxDuelTicks, double outsiderDamageFraction) {
        this.instance = instance;
        this.plugin = instance.plugin();
        this.color = color;
        this.rotateBelowHealthFraction = rotateBelowHealthFraction;
        this.maxDuelTicks = maxDuelTicks;
        this.outsiderDamageFraction = outsiderDamageFraction;
    }

    @Override
    public void start() {
        // Fully hittable as a rule — the restriction is who, not whether, so the global multiplier
        // stays open and filterDamage does all the work.
        instance.setDamageMultiplier(1.0);
        chooseDuellist();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pulse, TICK_INTERVAL, TICK_INTERVAL);
        instance.trackTask(task);
    }

    @Override
    public void stop() {
        stopped = true;
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = null;
        duellist = null;
        instance.setDamageMultiplier(1.0);
        instance.mechanicBar().release(MechanicBar.Owner.MECHANIC);
    }

    /**
     * The whole mechanic in one method: only the named opponent's blows count. Outsiders are scaled by
     * {@code outsiderDamageFraction}, normally 0 — left configurable so a server can soften this into
     * "everyone else hits weakly" rather than a hard lockout.
     */
    @Override
    public double filterDamage(Player attacker, double damage) {
        if (duellist == null || attacker.equals(duellist)) {
            return damage;
        }
        if (outsiderDamageFraction <= 0.0 && instance.signalDeflectReady()) {
            plugin.actionBarHub().flash(attacker,
                    Component.text("It refuses your blade — this is not your duel", NamedTextColor.RED),
                    NOTICE_MS, ActionBarHub.PRIORITY_NOTICE);
        }
        return damage * outsiderDamageFraction;
    }

    @Override
    public void onBossDamaged(Player attacker, double damageDealt) {
        // Landing real blows as the named opponent is what this phase is asking for, so it satisfies
        // the phase floor and lets the fight progress out of this band.
        if (damageDealt > 0 && attacker.equals(duellist)) {
            instance.recordExposure();
        }
    }

    private void pulse() {
        if (stopped || !instance.entity().isValid()) {
            return;
        }
        duelTicks += TICK_INTERVAL;

        // Arena.combatants already excludes spectators/creative, so a duellist who switches into
        // either mid-duel simply stops appearing in this list and gets treated the same as leaving.
        boolean duellistGone = duellist == null || !duellist.isOnline() || duellist.isDead()
                || !Arena.combatants(instance.entity().getLocation(), instance.arena().radius()).contains(duellist);
        boolean duellistSpent = duellist != null && duellist.isOnline()
                && duellist.getHealth() / maxHealthOf(duellist) <= rotateBelowHealthFraction;
        if (duellistGone || duellistSpent || duelTicks >= maxDuelTicks) {
            chooseDuellist();
            return;
        }

        // Tether so everyone can see whose fight it currently is.
        Location bossLoc = instance.entity().getLocation().add(0, 1.2, 0);
        Fx.line(bossLoc, duellist.getLocation().add(0, 1, 0), org.bukkit.Particle.CRIT, 14);
        updateBar();
    }

    /** MAX_HEALTH read through the attribute; the 20.0 fallback is the vanilla default. */
    private static double maxHealthOf(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        return maxHealth != null ? maxHealth.getValue() : 20.0;
    }

    /**
     * Running state belongs on the mechanic bar, not a repeated action-bar flash — a NOTICE-priority
     * flash re-sent every pulse (0.5s) would sit up for the entire duel and smother any sustained
     * readout (weapon cooldowns, other mechanics) for as long as this one runs. See MechanicBar's
     * own header for why "state that changes over the whole phase" needs its own real estate instead.
     */
    private void updateBar() {
        if (duellist == null) {
            return;
        }
        double remaining = 1.0 - Math.min(1.0, duelTicks / (double) maxDuelTicks);
        instance.mechanicBar().update(instance.barViewers(), player -> {
            boolean isDuellist = player.equals(duellist);
            Component text = Component.text("THE DUEL  ", NamedTextColor.GOLD)
                    .append(isDuellist
                            ? Component.text("yours — nobody else can hurt it", NamedTextColor.WHITE)
                            : Component.text(duellist.getName() + " alone may strike", NamedTextColor.DARK_GRAY));
            return MechanicBar.Readout.of(text, remaining, BossBar.Color.YELLOW);
        });
    }

    private void chooseDuellist() {
        List<Player> candidates = Arena.combatants(instance.entity().getLocation(), instance.arena().radius());
        if (candidates.isEmpty()) {
            duellist = null;
            return;
        }
        // Prefer someone other than the current duellist so the phase actually rotates, but fall back
        // to them when they are the only person left standing in the arena.
        List<Player> others = candidates.stream().filter(p -> !p.equals(duellist)).toList();
        List<Player> pool = others.isEmpty() ? candidates : others;
        Player chosen = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        if (chosen.equals(duellist)) {
            return;
        }
        duellist = chosen;
        duelTicks = 0;

        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1.4, 0), color, 1.8f, 40, 0.7);
        Fx.sound(loc, Sound.ITEM_TRIDENT_RETURN, 1.1f, 0.8f);
        instance.showTitle(
                Component.text("A DUEL IS NAMED", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text(chosen.getName() + " alone may strike", NamedTextColor.GRAY));
        updateBar();
    }
}

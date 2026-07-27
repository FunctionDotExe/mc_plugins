package dev.rbm72.weaponsplugin.boss;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.entity.Player;

/**
 * A scripted interruption that fires once at each of a set of health milestones, on top of whatever
 * phase is running.
 * <p>
 * This is the layer the roster was missing. Attacks are constant and random; phases are long standing
 * conditions. Neither produces the moment where the fight <em>stops</em>, names one specific thing,
 * and gives the group a few seconds to do it or eat the consequence. That beat is most of what makes
 * a boss feel scripted and dangerous rather than like a damage sponge with particles, and it is the
 * shape behind every memorable raid mechanic — shield checks, beacon drops, "get out of the beam".
 * <p>
 * An event owns the fight while it runs: the boss stops selecting attacks so the event is never
 * competing with a sword swing for the player's attention, and it must call its {@code onComplete}
 * exactly once so the fight resumes. Events fire strictly on descending health, once per milestone,
 * so a boss that heals cannot replay one it already spent.
 */
public abstract class BossEvent {

    protected final WeaponsPlugin plugin;
    private final String bossId;

    protected BossEvent(WeaponsPlugin plugin, String bossId) {
        this.plugin = plugin;
        this.bossId = bossId;
    }

    /** Stable identifier, used with the milestone to record that this one already fired. */
    public abstract String id();

    /**
     * Health fractions at which this fires, e.g. {@code {0.8, 0.55, 0.3}}. Order does not matter;
     * each is armed independently and fires the first tick health drops to or below it.
     */
    public abstract double[] triggerFractions();

    /** Runs the event. Must call {@code onComplete} exactly once, however it ends. */
    public abstract void run(BossInstance instance, Runnable onComplete);

    /**
     * Whether the boss stops attacking and chasing for the duration.
     * <p>
     * True suits a short, total interruption the group must give its whole attention to — a shield
     * check, a channelled execution — where a sword combo competing for focus would just be noise.
     * <p>
     * False suits anything that runs long or is meant to be handled <em>while</em> fighting: a
     * repositioning mechanic, a set of props to chip down on the side. Those are only interesting
     * because the boss keeps pressuring you throughout; freezing it would turn ten seconds of tension
     * into ten seconds of dead air and a free chore.
     */
    public boolean blocksCombat() {
        return true;
    }

    /**
     * Every player hit that lands on the boss while this event is active, with the damage that
     * actually got through (frequently 0, since a shielded event blocks damage outright). Counting
     * <em>hits</em> rather than damage is the whole point of some events: it makes attack speed the
     * stat that matters instead of damage per swing, which no other system in this plugin rewards.
     */
    public void onHit(BossInstance instance, Player attacker, double damageDealt) {
    }

    /** Called if the fight ends or the event is cut short — cancel tasks, clear props, restore state. */
    public void cleanup(BossInstance instance) {
    }

    protected final double configDouble(String key, double def) {
        return plugin.getConfig().getDouble("bosses." + bossId + "." + key, def);
    }

    protected final int configInt(String key, int def) {
        return plugin.getConfig().getInt("bosses." + bossId + "." + key, def);
    }
}

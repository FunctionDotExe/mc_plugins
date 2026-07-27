package dev.rbm72.weaponsplugin.boss;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * A second, dedicated boss bar that shows what the <em>current mechanic</em> wants from you — hits
 * remaining on a shield, executioners left to kill, plates still standing, which half of the floor
 * you are on.
 * <p>
 * This exists because the action bar could not do the job. It is a single line shared by weapon
 * cooldowns, movement charges, attack cast bars and one-off combat notices, and {@link
 * dev.rbm72.weaponsplugin.ui.ActionBarHub} resolves that contention by letting the highest-priority
 * flash win outright. A mechanic's running state is exactly the wrong shape for that: it needs to be
 * visible <em>continuously</em>, and it was being suppressed for seconds at a time by routine notices
 * ("plate destroyed", "attendants still shield it") that fire on every hit. The result in play was a
 * fight where the counters existed but were invisible, so players could not tell what a mechanic
 * wanted, whether they were making progress on it, or whether they had failed it.
 * <p>
 * A boss bar is the right channel instead: it has its own permanent real estate, it cannot be stomped
 * by anything else, and it carries a progress fraction natively — which is most of what a mechanic
 * readout is. Kept strictly separate from the health bar in {@link BossBarController} so the two never
 * compete.
 * <p>
 * Bars are per player rather than shared, because plenty of mechanics say different things to
 * different people at the same instant: the condemned player in a judgment, the one holding the zone,
 * or anyone standing on the cursed half of the floor.
 */
public final class MechanicBar {

    /** What one player should see right now, or null if this mechanic has nothing to say to them. */
    public record Readout(Component text, float progress, BossBar.Color color) {

        public static Readout of(Component text, double progress, BossBar.Color color) {
            return new Readout(text, (float) Math.max(0.0, Math.min(1.0, progress)), color);
        }
    }

    /**
     * Who is currently entitled to this bar.
     * <p>
     * Phases and events run <em>concurrently</em> — {@link BossEvent#blocksCombat()} stops the boss
     * selecting attacks, but it does not stop the active {@link PhaseMechanic}'s scheduled task, which
     * keeps writing its readout throughout. With both of them calling {@code update} every few ticks
     * against one shared bar, the bar's name flips between the two several times a second and neither
     * is readable — the same contention that made the action bar unusable for mechanic state in the
     * first place (see this class's header), reappearing one layer up.
     * <p>
     * So the bar is claimed rather than shared. An event outranks a phase mechanic for exactly as long
     * as it is running: an event is a short, scripted interruption that has already taken over the
     * fight, and it is unambiguously the thing the player needs to be reading. The phase mechanic's
     * readout returns by itself on its next tick once the event {@link #release}s.
     */
    public enum Owner {
        /** The running phase mechanic. Yields to an event. */
        MECHANIC(0),
        /** A scripted event. Outranks the phase mechanic while it is active. */
        EVENT(1);

        private final int priority;

        Owner(int priority) {
            this.priority = priority;
        }
    }

    private final Map<UUID, BossBar> bars = new HashMap<>();
    /** Null when nobody holds the bar; otherwise the highest-priority writer currently using it. */
    private Owner holder;

    /**
     * Updates every viewer's bar from {@code readout}, creating and showing bars as needed and hiding
     * them again for anyone the mechanic has nothing to say to.
     * <p>
     * Defaults to {@link Owner#MECHANIC} — the safe end of the priority order, so any caller that has
     * not thought about contention loses to one that has.
     */
    public void update(List<Player> viewers, Function<Player, Readout> readout) {
        update(Owner.MECHANIC, viewers, readout);
    }

    /**
     * Updates the bar on behalf of {@code owner}, or does nothing at all if something higher-priority
     * currently holds it.
     */
    public void update(Owner owner, List<Player> viewers, Function<Player, Readout> readout) {
        if (holder != null && holder.priority > owner.priority) {
            return;
        }
        holder = owner;
        write(viewers, readout);
    }

    /**
     * Hands the bar back. Only the current holder can release it, so a phase mechanic tearing down
     * mid-event cannot yank the bar out from under the event that outranked it.
     */
    public void release(Owner owner) {
        if (holder != owner) {
            return;
        }
        holder = null;
        clearBars();
    }

    private void write(List<Player> viewers, Function<Player, Readout> readout) {
        for (Player player : viewers) {
            Readout shown = readout.apply(player);
            if (shown == null) {
                hideFor(player.getUniqueId());
                continue;
            }
            BossBar bar = bars.get(player.getUniqueId());
            if (bar == null) {
                bar = BossBar.bossBar(shown.text(), shown.progress(), shown.color(), BossBar.Overlay.PROGRESS);
                bars.put(player.getUniqueId(), bar);
                player.showBossBar(bar);
            } else {
                bar.name(shown.text());
                bar.progress(shown.progress());
                bar.color(shown.color());
            }
        }
        // Anyone who has a bar but was not in this refresh (left the arena, logged out) loses it.
        bars.keySet().removeIf(id -> {
            if (viewers.stream().anyMatch(p -> p.getUniqueId().equals(id))) {
                return false;
            }
            hideFor(id);
            return true;
        });
    }

    /** Convenience for the common case: the same readout for everyone present. */
    public void updateShared(List<Player> viewers, Component text, double progress, BossBar.Color color) {
        updateShared(Owner.MECHANIC, viewers, text, progress, color);
    }

    /** Convenience for the common case: the same readout for everyone present, on {@code owner}'s behalf. */
    public void updateShared(Owner owner, List<Player> viewers, Component text, double progress, BossBar.Color color) {
        Readout shared = Readout.of(text, progress, color);
        update(owner, viewers, player -> shared);
    }

    /**
     * Hard reset — every bar gone and the claim dropped, whoever held it. For the fight ending or a
     * phase changing, where both the outgoing mechanic and any event are being torn down together.
     */
    public void clear() {
        holder = null;
        clearBars();
    }

    private void clearBars() {
        for (Map.Entry<UUID, BossBar> entry : bars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        bars.clear();
    }

    private void hideFor(UUID id) {
        BossBar bar = bars.get(id);
        if (bar == null) {
            return;
        }
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            player.hideBossBar(bar);
        }
    }
}

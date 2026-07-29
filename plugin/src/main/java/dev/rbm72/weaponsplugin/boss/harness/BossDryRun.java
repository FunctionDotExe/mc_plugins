package dev.rbm72.weaponsplugin.boss.harness;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.BossPhase;
import dev.rbm72.weaponsplugin.boss.grief.ArenaLedger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * The cheap version of a test suite for a plugin that cannot have one: spawns a boss, force-runs every
 * attack in every phase exactly once against the operator, despawns it, and then asserts the fight left
 * nothing behind.
 * <p>
 * This replaces the only verification the roster had, which was spawning all seventeen bosses by hand and
 * watching. Seventeen bosses times a dozen-plus attacks each is past what anyone re-checks after a
 * refactor, so in practice nothing was re-checked — a null dereference in one attack of one phase of one
 * boss could sit undiscovered until a group found it mid-fight.
 * <p>What it actually catches:
 * <ul>
 *   <li><b>Throwing attacks.</b> {@code BossAttack#sequence} deliberately swallows exceptions so one bad
 *       tick cannot freeze a boss — which also means a broken attack is invisible in play. The run
 *       installs a log handler and counts every SEVERE the framework emits, so a swallowed throw becomes
 *       a reported failure.</li>
 *   <li><b>Entity leaks.</b> Adds, thrown blocks, display props: anything alive in the arena after
 *       teardown that was not there before.</li>
 *   <li><b>Task leaks.</b> Repeating tasks a mechanic scheduled and never cancelled — the leak that
 *       silently costs a server its tick budget for the rest of its uptime.</li>
 *   <li><b>Unrestored terrain.</b> Ledger entries still outstanding after the rollback finishes.</li>
 * </ul>
 * <p>What it does not catch: whether an attack is any <em>good</em>. It runs code, not design.
 */
public final class BossDryRun {

    /** Ticks between forced attacks. Long enough for a telegraph plus payload plus recovery to finish. */
    private static final long ATTACK_SPACING_TICKS = 45L;
    /** If an attack never calls its completion callback, move on anyway after this long and say so. */
    private static final long ATTACK_TIMEOUT_TICKS = 120L;
    /** Grace after teardown before the leak assertions run, so batched restore and removals settle. */
    private static final long SETTLE_TICKS = 40L;
    /** Entities this far outside the arena are somebody else's business. */
    private static final double LEAK_SCAN_MARGIN = 24.0;

    private final WeaponsPlugin plugin;
    private final Player operator;
    private final Boss boss;

    private final List<String> failures = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final Set<String> severeMessages = new LinkedHashSet<>();

    private BossInstance instance;
    private ArenaLedger ledger;
    private Location arenaCentre;
    private double arenaRadius;
    private int entitiesBefore;
    private int tasksBefore;
    private boolean operatorWasInvulnerable;
    private Handler logHandler;
    private int attacksRun;
    private int attacksTimedOut;

    public BossDryRun(WeaponsPlugin plugin, Player operator, Boss boss) {
        this.plugin = plugin;
        this.operator = operator;
        this.boss = boss;
    }

    /** Kicks the whole run off. Everything after this is scheduled work; the command returns immediately. */
    public void start() {
        if (plugin.bossManager().isLive(boss.id())) {
            operator.sendMessage(Component.text(boss.id() + " is already live — despawn it first.", NamedTextColor.RED));
            return;
        }

        arenaCentre = operator.getLocation().clone();
        arenaRadius = boss.arenaRadius();
        entitiesBefore = countArenaEntities();
        tasksBefore = countPluginTasks();
        installLogHandler();

        // The operator is the attack target for the whole run and every attack in the roster is aimed at
        // its target. Made invulnerable rather than moved to creative: several attacks pick their victims
        // with Arena.combatants(), which excludes creative players, and a run where the boss can find
        // nobody to hit exercises none of the damage paths that are most likely to be broken.
        operatorWasInvulnerable = operator.isInvulnerable();
        operator.setInvulnerable(true);

        instance = plugin.bossManager().spawn(boss.id(), arenaCentre).orElse(null);
        if (instance == null) {
            finishEarly("spawn failed — nothing else could be tested");
            return;
        }
        ledger = instance.ledger();

        List<BossAttack> queue = attackQueue();
        operator.sendMessage(Component.text("Dry-running " + boss.id() + ": " + queue.size()
                + " attack(s) across " + boss.phases().size() + " phase(s). Stand still.", NamedTextColor.AQUA));
        runNext(queue, 0);
    }

    /** Every distinct attack across every phase, in authored order — a shared instance is only run once. */
    private List<BossAttack> attackQueue() {
        List<BossAttack> queue = new ArrayList<>();
        for (BossPhase phase : boss.phases()) {
            for (BossAttack attack : phase.attacks()) {
                if (!queue.contains(attack)) {
                    queue.add(attack);
                }
            }
        }
        return queue;
    }

    /**
     * Runs one attack, then schedules the next when it reports completion.
     * <p>
     * Strictly one at a time: {@link BossAttack} stashes its context for the duration of a cast, so two
     * overlapping runs of the same instance would have the second stomp the first's cast bar. The timeout
     * exists because an attack that never calls its callback is itself a bug worth reporting rather than
     * one that should hang the whole run.
     */
    private void runNext(List<BossAttack> queue, int index) {
        if (index >= queue.size()) {
            teardown();
            return;
        }
        BossAttack attack = queue.get(index);
        if (instance == null || !instance.entity().isValid()) {
            failures.add("boss entity died or vanished partway through the run (at " + attack.name() + ")");
            teardown();
            return;
        }

        boolean[] completed = {false};
        AttackContext ctx = new AttackContext(plugin, instance, operator);
        attacksRun++;
        try {
            attack.run(ctx, () -> completed[0] = true);
        } catch (Exception | LinkageError e) {
            // A throw out of run() itself, before the sequence ever starts, never reaches the log handler
            // below — the framework catches it in the tick loop, not here.
            failures.add(attack.name() + " threw immediately: " + e);
        }

        long deadline = ATTACK_TIMEOUT_TICKS;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!completed[0]) {
                attacksTimedOut++;
                notes.add(attack.name() + " never called its completion callback within "
                        + deadline * 50 + "ms");
            }
            runNext(queue, index + 1);
        }, Math.max(ATTACK_SPACING_TICKS, deadline));
    }

    private void teardown() {
        if (instance != null) {
            try {
                instance.end(BossInstance.EndReason.DESPAWNED);
            } catch (Exception e) {
                failures.add("teardown threw: " + e);
            }
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, this::assertClean, SETTLE_TICKS);
    }

    /** The actual assertions. Everything above only sets up the state these read. */
    private void assertClean() {
        restoreOperator();
        removeLogHandler();

        if (plugin.bossManager().isLive(boss.id())) {
            failures.add("boss is still registered as live after teardown — it can never be spawned again");
        }

        int entitiesAfter = countArenaEntities();
        if (entitiesAfter > entitiesBefore) {
            failures.add((entitiesAfter - entitiesBefore) + " leftover entit(ies) in the arena after teardown"
                    + " (adds, thrown blocks or props that were never removed)");
        }

        int tasksAfter = countPluginTasks();
        if (tasksAfter > tasksBefore) {
            failures.add((tasksAfter - tasksBefore) + " leftover scheduled task(s) after teardown"
                    + " — a mechanic scheduled work it never cancelled");
        }

        if (ledger != null && ledger.enabled() && ledger.size() > 0) {
            failures.add(ledger.size() + " block(s) still unrestored after the rollback finished");
        }

        for (String message : severeMessages) {
            failures.add("logged SEVERE: " + message);
        }

        report();
    }

    private void report() {
        operator.sendMessage(Component.text("— Dry run: " + boss.id() + " —", NamedTextColor.GOLD));
        operator.sendMessage(Component.text("Ran " + attacksRun + " attack(s)"
                + (attacksTimedOut > 0 ? ", " + attacksTimedOut + " did not complete in time" : ""),
                NamedTextColor.GRAY));
        for (String note : notes) {
            operator.sendMessage(Component.text("  note: " + note, NamedTextColor.YELLOW));
        }
        if (failures.isEmpty()) {
            operator.sendMessage(Component.text("PASS — no throws, no leftover entities, tasks or blocks.",
                    NamedTextColor.GREEN));
            return;
        }
        operator.sendMessage(Component.text("FAIL — " + failures.size() + " problem(s):", NamedTextColor.RED));
        for (String failure : failures) {
            operator.sendMessage(Component.text("  " + failure, NamedTextColor.RED));
        }
        operator.sendMessage(Component.text("Full stack traces are in the server log.", NamedTextColor.DARK_GRAY));
    }

    private void finishEarly(String reason) {
        restoreOperator();
        removeLogHandler();
        operator.sendMessage(Component.text("Dry run aborted: " + reason, NamedTextColor.RED));
    }

    private void restoreOperator() {
        operator.setInvulnerable(operatorWasInvulnerable);
    }

    // ------------------------------------------------------------------ probes

    /**
     * Live non-player, non-item entities near the arena. Dropped items are excluded because a defeated
     * boss is supposed to leave loot behind, and a run that counted it would report every clean fight as
     * a leak.
     */
    private int countArenaEntities() {
        if (arenaCentre == null || arenaCentre.getWorld() == null) {
            return 0;
        }
        int count = 0;
        double reach = arenaRadius + LEAK_SCAN_MARGIN;
        for (Entity entity : arenaCentre.getWorld().getEntities()) {
            if (entity instanceof Player || entity instanceof Item || !entity.isValid()) {
                continue;
            }
            if (entity.getType() == EntityType.ARMOR_STAND && entity.getScoreboardTags().isEmpty()) {
                // Untagged armour stands are part of the scenery in some realms, not fight props.
                continue;
            }
            if (entity.getLocation().distanceSquared(arenaCentre) <= reach * reach) {
                count++;
            }
        }
        return count;
    }

    private int countPluginTasks() {
        int count = 0;
        for (BukkitTask task : plugin.getServer().getScheduler().getPendingTasks()) {
            if (plugin.equals(task.getOwner())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Catches what the framework's own safety nets hide. Every guarded step in a boss fight logs at
     * SEVERE and carries on, by design — a fight must not die of one bad tick. The cost is that a broken
     * attack looks identical to a working one from inside the game, so the only way a dry run can see it
     * is to listen to the log while it runs.
     */
    private void installLogHandler() {
        logHandler = new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                if (logRecord.getLevel().intValue() >= Level.SEVERE.intValue()) {
                    severeMessages.add(logRecord.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        plugin.getLogger().addHandler(logHandler);
    }

    private void removeLogHandler() {
        if (logHandler != null) {
            plugin.getLogger().removeHandler(logHandler);
            logHandler = null;
        }
    }
}

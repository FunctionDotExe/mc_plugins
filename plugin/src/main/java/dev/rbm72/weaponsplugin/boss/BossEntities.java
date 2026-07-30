package dev.rbm72.weaponsplugin.boss;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * Marks every entity this engine spawns, so an operator can always find and delete them again.
 * <p>
 * {@link BossInstance#end} removes what it owns, and it is reliable for a fight that ends while the
 * plugin is running. The gap is everything that ends the fight from <em>outside</em> that path — a
 * hard-killed server, a crash, a world unloaded before {@code onDisable} could reach the entity, a
 * plugin reload mid-pull. Boss entities are deliberately {@code setPersistent(true)} with
 * {@code setRemoveWhenFarAway(false)} so a fight survives everyone walking away, which means anything
 * left behind by one of those paths is saved into the chunk and comes back on the next load — as a
 * custom-named mob with no boss bar, no tick loop and no manager entry. It is not merely inert: it is
 * a real hostile mob, and it is invisible to every command that works off the live registry.
 * <p>
 * A scoreboard tag rather than persistent data: it survives the save/load round trip identically,
 * needs no plugin handle at the call site, and is visible to a plain {@code /execute as @e[tag=...]}
 * when someone is diagnosing this from the console.
 */
public final class BossEntities {

    /** On the boss mob itself. */
    public static final String TAG_BOSS = "weaponsplugin_boss";
    /** On every combatant add a fight summons. */
    public static final String TAG_ADD = "weaponsplugin_boss_add";

    private BossEntities() {
    }

    public static void markBoss(Entity entity) {
        entity.addScoreboardTag(TAG_BOSS);
    }

    public static void markAdd(Entity entity) {
        entity.addScoreboardTag(TAG_ADD);
    }

    /**
     * Removes every boss-engine entity in every loaded world, whether or not a live fight owns it.
     * <p>
     * Two detectors, because the tag can only help going forward. Anything already orphaned before
     * marking existed carries no tag at all, and the only durable thing left on it is the custom name
     * the manager gave it at spawn — so a mob whose name matches a registered boss's display name is
     * treated as one of ours too. That is a deliberate, narrow heuristic: it will not match a mob named
     * anything else, and the ids it matches against are exactly the roster.
     *
     * @return how many entities were removed
     */
    public static int sweep(BossManager manager) {
        Set<String> bossNames = new HashSet<>();
        for (Boss boss : manager.all()) {
            bossNames.add(plain(boss.displayName()));
        }

        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player || !isOurs(entity, bossNames)) {
                    continue;
                }
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    private static boolean isOurs(Entity entity, Set<String> bossNames) {
        if (entity.getScoreboardTags().contains(TAG_BOSS) || entity.getScoreboardTags().contains(TAG_ADD)) {
            return true;
        }
        Component name = entity.customName();
        return name != null && bossNames.contains(plain(name));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}

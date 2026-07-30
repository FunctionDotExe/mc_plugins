package dev.rbm72.weaponsplugin.boss.commands;

import dev.rbm72.weaponsplugin.boss.BossManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * The bigger hammer next to {@code /bossclear}: ends every live fight, restores every arena, and then
 * deletes every boss-engine entity left anywhere in any loaded world — including ones no live fight
 * knows about.
 * <p>
 * {@code /bossclear} only ever sees the live registry. An orphaned boss (left by a hard-killed server,
 * a crash, or a world unloaded before the plugin could clean up) is not in that registry, so clear
 * cannot touch it — and because boss mobs are persistent by design, it comes back every load and stacks
 * up one per incident. This is the command for that: after it runs, nothing of ours is left standing.
 */
public final class BossKillAllCommand implements CommandExecutor {

    private final BossManager bossManager;

    public BossKillAllCommand(BossManager bossManager) {
        this.bossManager = bossManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        BossManager.KillAllResult result = bossManager.killAll();

        if (result.fightsCleared() == 0 && result.entitiesRemoved() == 0) {
            sender.sendMessage(Component.text(
                    "Nothing to kill — no live fights and no boss entities anywhere.", NamedTextColor.YELLOW));
            return true;
        }

        sender.sendMessage(Component.text("Killed everything:", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("  " + result.fightsCleared() + " live fight(s) ended, arenas restored.",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  " + result.entitiesRemoved() + " boss entity/entities removed.",
                NamedTextColor.GRAY));
        // Said plainly, because it is the one thing this command cannot do: an orphan's undo log died
        // with the fight that made it, so whatever terrain it left is now just part of the world.
        if (result.entitiesRemoved() > result.fightsCleared()) {
            sender.sendMessage(Component.text(
                    "  Orphans had no undo log — any terrain they left behind stays. Regenerate the realm to reset it.",
                    NamedTextColor.DARK_GRAY));
        }
        return true;
    }
}

package dev.rbm72.weaponsplugin.boss.commands;

import dev.rbm72.weaponsplugin.boss.DamageTrace;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Toggles {@link DamageTrace} for the caller — the "what is actually ticking me" switch. On, every bit
 * of health you lose prints a line saying which path took it and whether that path tilts the camera.
 */
public final class BossDamageTraceCommand implements CommandExecutor {

    private final DamageTrace trace;

    public BossDamageTraceCommand(DamageTrace trace) {
        this.trace = trace;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Run this in-game — it traces your own health.",
                    NamedTextColor.RED));
            return true;
        }
        if (trace.toggle(player)) {
            player.sendMessage(Component.text(
                    "Damage trace ON. RED lines tilt your camera, grey lines do not. Run again to stop.",
                    NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Damage trace OFF.", NamedTextColor.YELLOW));
        }
        return true;
    }
}

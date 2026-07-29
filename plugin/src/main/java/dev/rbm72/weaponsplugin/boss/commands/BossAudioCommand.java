package dev.rbm72.weaponsplugin.boss.commands;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /bossaudio list} shows every boss sound key touched this session with the vanilla sound it falls
 * back to; {@code /bossaudio dump} writes a pack-ready {@code sounds.json} declaring all of them.
 * <p>
 * The intended sequence is {@code /bosstest all} (which fires every attack in the roster, and therefore
 * plays every key) followed by {@code /bossaudio dump}. That produces a complete, correct declaration for
 * ~100 keys without anyone transcribing them by hand — which is the actual reason the boss audio layer sat
 * unused: the indirection was written, and nothing generated the other half of it.
 */
public final class BossAudioCommand implements CommandExecutor, TabCompleter {

    private final WeaponsPlugin plugin;

    public BossAudioCommand(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        Map<String, Sound> seen = BossAudio.seen();

        if (action.equals("dump")) {
            if (seen.isEmpty()) {
                sender.sendMessage(Component.text("No boss sounds have played yet — run /bosstest all first.",
                        NamedTextColor.RED));
                return true;
            }
            File out = new File(plugin.getDataFolder(), "sounds.json");
            try {
                Files.writeString(out.toPath(), BossAudio.soundsJson(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                sender.sendMessage(Component.text("Failed to write sounds.json: " + e.getMessage(), NamedTextColor.RED));
                return true;
            }
            sender.sendMessage(Component.text("Wrote " + seen.size() + " sound key(s) to "
                    + out.getPath(), NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Merge it into resourcepack/assets/weaponsplugin/sounds.json,"
                    + " then set boss-audio.custom-sounds: true.", NamedTextColor.GRAY));
            return true;
        }

        if (seen.isEmpty()) {
            sender.sendMessage(Component.text("No boss sounds have played yet this session.", NamedTextColor.GRAY));
            return true;
        }
        sender.sendMessage(Component.text("— Boss sound keys seen this session (" + seen.size() + ") —",
                NamedTextColor.GOLD));
        sender.sendMessage(Component.text("custom-sounds is currently "
                        + (plugin.getConfig().getBoolean("boss-audio.custom-sounds", false) ? "ON" : "off"),
                NamedTextColor.GRAY));
        seen.forEach((key, fallback) -> sender.sendMessage(Component.text("  " + key, NamedTextColor.WHITE)
                .append(Component.text("  ← " + fallback, NamedTextColor.DARK_GRAY))));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("list", "dump").stream().filter(option -> option.startsWith(prefix)).toList();
    }
}

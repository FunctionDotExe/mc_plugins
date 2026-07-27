package dev.rbm72.weaponsplugin.boss;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Custom per-phase boss bar, shown only to players currently inside the arena. */
public final class BossBarController {

    private final BossBar bar = BossBar.bossBar(Component.text("Boss"), 1f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_20);
    private final Set<UUID> viewers = new HashSet<>();

    public void refresh(Component bossName, BossPhase phase, double healthFraction, List<Player> desiredViewers) {
        bar.name(bossName.append(Component.text(" — " + phase.name())));
        bar.progress((float) Math.max(0f, Math.min(1f, healthFraction)));
        bar.color(phase.isEnrage() ? BossBar.Color.RED : BossBar.Color.PURPLE);

        Set<UUID> desired = desiredViewers.stream().map(Player::getUniqueId).collect(Collectors.toSet());
        for (Player player : desiredViewers) {
            if (viewers.add(player.getUniqueId())) {
                player.showBossBar(bar);
            }
        }
        viewers.removeIf(id -> {
            if (desired.contains(id)) {
                return false;
            }
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.hideBossBar(bar);
            }
            return true;
        });
    }

    /** Whether this player currently has the health bar on screen — one line of their boss-bar stack. */
    public boolean isShowingTo(Player player) {
        return player != null && viewers.contains(player.getUniqueId());
    }

    public void hideAll() {
        for (UUID id : viewers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.hideBossBar(bar);
            }
        }
        viewers.clear();
    }
}

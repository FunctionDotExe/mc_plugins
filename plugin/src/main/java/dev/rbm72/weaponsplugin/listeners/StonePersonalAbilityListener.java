package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.commands.OpCooldownCommand;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.stone.Stone;
import dev.rbm72.weaponsplugin.stone.StoneManager;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Stone personal abilities (Windrunner's dash, Levitation's drift) answer to the same
 * double-tap-sneak gesture accessory personal abilities use — see
 * {@link AccessoryPersonalAbilityListener}. Kept as a separate listener/tracking map so a
 * double-tap fires both an equipped accessory's ability and an equipped stone's ability
 * independently, matching how multiple equipped accessories already both fire together.
 */
public final class StonePersonalAbilityListener implements Listener {

    private static final long DOUBLE_TAP_WINDOW_MS = 400;
    /** How long the blocked-tap readout owns the action bar before the merged line comes back. */
    private static final long COOLDOWN_NOTICE_MS = 1200;

    private final StoneManager stones;
    private final OpCooldownCommand opCooldown;
    private final ActionBarHub actionBar;
    private final Map<UUID, Long> lastSneakStartMs = new HashMap<>();

    public StonePersonalAbilityListener(StoneManager stones, OpCooldownCommand opCooldown, ActionBarHub actionBar) {
        this.stones = stones;
        this.opCooldown = opCooldown;
        this.actionBar = actionBar;
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long lastStart = lastSneakStartMs.put(player.getUniqueId(), now);
        if (lastStart == null || now - lastStart > DOUBLE_TAP_WINDOW_MS) {
            return;
        }

        boolean bypass = opCooldown.hasBypass(player);

        if (stones.triggerPersonalAbilities(player, bypass)) {
            lastSneakStartMs.remove(player.getUniqueId());
        } else if (stones.hasEquippedPersonalAbility(player)) {
            Fx.sound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
            showCooldowns(player, bypass);
        }
    }

    /** Surfaces remaining cooldowns on a blocked double-tap so the player can see what they're waiting on. */
    private void showCooldowns(Player player, boolean bypass) {
        Component message = null;
        for (Stone stone : stones.equipped(player)) {
            if (!stone.hasPersonalAbility()) {
                continue;
            }
            double remaining = stones.remainingCooldownSeconds(player, stone, bypass);
            if (remaining <= 0) {
                continue;
            }
            Component line = stone.displayName().append(Component.text(
                    String.format(Locale.ROOT, " %.1fs", remaining), NamedTextColor.YELLOW));
            message = message == null ? line : message.append(Component.text("  |  ", NamedTextColor.GRAY)).append(line);
        }
        if (message != null) {
            actionBar.flash(player, message, COOLDOWN_NOTICE_MS, ActionBarHub.PRIORITY_NOTICE);
        }
    }
}

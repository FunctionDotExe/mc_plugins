package dev.rbm72.weaponsplugin.boss.modifier;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * Enforces {@link BossModifier#NO_HEAL}: no healing of any kind for a player standing in a fight armed
 * with it — potions, regeneration, golden apples, natural saturation regen, another player's support.
 * <p>
 * Cancelling {@link EntityRegainHealthEvent} rather than stripping effects or blocking item use, because
 * that is the single point every heal in the game funnels through; anything narrower would leave a
 * healing route open and turn the affix into a puzzle about which consumable still works.
 * <p>
 * Scoped by arena, not by world, so a player who steps out of the fight can heal — leaving is meant to
 * be the answer, and a whole-realm ban would make it a punishment for entering at all.
 */
public final class NoHealListener implements Listener {

    /** How often a blocked heal says so. Frequent enough to explain, rare enough not to spam. */
    private static final long NOTICE_INTERVAL_MS = 3000L;

    private final WeaponsPlugin plugin;
    private final java.util.Map<java.util.UUID, Long> lastNoticeMs = new java.util.HashMap<>();

    public NoHealListener(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        plugin.bossManager().instanceCovering(player.getLocation()).ifPresent(instance -> {
            if (!instance.affixes().contains(BossModifier.NO_HEAL)) {
                return;
            }
            event.setCancelled(true);
            notice(player);
        });
    }

    /**
     * Tells the player why their heal did nothing. Silent suppression is the worst version of this affix:
     * a potion that visibly drinks and heals nothing reads as a broken server, not as a rule.
     */
    private void notice(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastNoticeMs.get(player.getUniqueId());
        if (last != null && now - last < NOTICE_INTERVAL_MS) {
            return;
        }
        lastNoticeMs.put(player.getUniqueId(), now);
        // PRIORITY_NOTICE, not SUSTAINED: this is a one-off explanation, and SUSTAINED is the boss cast
        // bar's channel — a notice posted there would blank the telegraph readout it sits next to.
        plugin.actionBarHub().flash(player,
                Component.text("No Quarter — you cannot heal in this fight", NamedTextColor.RED),
                1500L, ActionBarHub.PRIORITY_NOTICE);
    }
}

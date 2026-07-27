package dev.rbm72.weaponsplugin.stone.stones;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.stone.Stone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Grants a real mid-air double jump using the same "vanilla double-tap-space" gesture
 * {@code WyrmwingPlate}'s wing dash hijacks: {@link #onEquipTick} arms {@code allowFlight} only
 * while the wielder is airborne (never while grounded, so it can't be spammed into infinite hops),
 * and {@code StoneDoubleJumpListener} cancels the resulting {@code PlayerToggleFlightEvent} and
 * substitutes a single upward boost instead of real flight. One extra jump per airtime, gated by
 * {@link #jumpAvailable} — but that alone only stops chaining mid-air; it doesn't stop bunny-hopping
 * it on every single landing, so {@link #DEFAULT_COOLDOWN_MS} adds a short cooldown on top, tracked the same
 * way {@link WindrunnerStone} tracks its recharge timers.
 */
public final class SkyleapStone extends Stone {

    private static final double BOOST_UP = 0.85;
    private static final double BOOST_FORWARD = 0.6;
    /**
     * Short on purpose. {@link #jumpAvailable} is the real anti-spam gate — one extra jump per
     * airtime, no chaining — so this only exists to stop bunny-hopping a leap off every single
     * landing. Three seconds did that by making the stone feel dead most of the time you reached for
     * it; under a second still breaks the hop loop while leaving it responsive.
     */
    private static final long DEFAULT_COOLDOWN_MS = 900;
    private static final Color SKY_COLOR = Color.fromRGB(180, 210, 255);

    private final Map<UUID, Boolean> jumpAvailable = new HashMap<>();
    private final Map<UUID, Long> nextReadyMs = new HashMap<>();

    public SkyleapStone(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "skyleap_stone";
    }

    @Override
    public Material material() {
        return Material.BREEZE_ROD;
    }

    @Override
    public String displayNameText() {
        return "Skyleap Stone";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Double-tap space mid-air to", NamedTextColor.GRAY),
                Component.text("leap again. Resets on landing,", NamedTextColor.GRAY),
                Component.text(String.format(Locale.ROOT, "%.1fs cooldown between uses.", cooldownMs() / 1000.0),
                        NamedTextColor.GRAY));
    }

    /** Config override: {@code stones.skyleap_stone.cooldown-seconds}. */
    private long cooldownMs() {
        double seconds = plugin.getConfig().getDouble("stones." + id() + ".cooldown-seconds",
                DEFAULT_COOLDOWN_MS / 1000.0);
        return Math.max(0L, Math.round(seconds * 1000));
    }

    @Override
    public void onEquipTick(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (player.isOnGround()) {
            jumpAvailable.put(uuid, true);
            if (player.getAllowFlight() && !player.isFlying()) {
                player.setAllowFlight(false);
            }
        } else {
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
        }
    }

    /** True if a bonus jump is available right now; consumes it (and starts the cooldown) if so. */
    public boolean consumeJump(Player player) {
        UUID uuid = player.getUniqueId();
        Boolean available = jumpAvailable.get(uuid);
        if (available == null || !available) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long readyAt = nextReadyMs.get(uuid);
        if (readyAt != null && now < readyAt) {
            return false;
        }
        jumpAvailable.put(uuid, false);
        nextReadyMs.put(uuid, now + cooldownMs());
        return true;
    }

    @Override
    public boolean showsCooldownStatus() {
        return true;
    }

    @Override
    public Component actionBarStatus(Player player, boolean onCooldown, double remainingSeconds) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long readyAt = nextReadyMs.get(uuid);
        boolean cooling = readyAt != null && now < readyAt;

        if (cooling) {
            double secs = (readyAt - now) / 1000.0;
            return displayName().append(Component.text(String.format(Locale.ROOT, " %.1fs", secs), NamedTextColor.YELLOW));
        }
        boolean ready = Boolean.TRUE.equals(jumpAvailable.get(uuid));
        return displayName().append(Component.text(ready ? " READY" : " (land to reset)",
                ready ? NamedTextColor.GREEN : NamedTextColor.GRAY));
    }

    public void applyBoost(Player player) {
        Vector direction = player.getLocation().getDirection().setY(0).normalize().multiply(BOOST_FORWARD);
        direction.setY(BOOST_UP);
        player.setVelocity(direction);

        Fx.sound(player, Sound.ENTITY_BREEZE_JUMP, 1.0f, 1.2f);
        Fx.coloredBurst(player.getLocation(), SKY_COLOR, 1.1f, 16, 0.3);
        Fx.trail(player.getLocation(), Particle.CLOUD, 8, 0.25, 0.02);
    }
}

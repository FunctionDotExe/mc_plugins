package dev.rbm72.weaponsplugin.stone.stones;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.stone.Stone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Grants a real mid-air double jump using the same "vanilla double-tap-space" gesture
 * {@code WyrmwingPlate}'s wing dash hijacks: the stone arms {@code allowFlight} only while the wielder is
 * airborne (never while grounded, so it can't be spammed into infinite hops), and
 * {@code StoneDoubleJumpListener} cancels the resulting {@code PlayerToggleFlightEvent} and substitutes a
 * single upward boost instead of real flight. One extra jump per airtime, gated by {@link #jumpAvailable} —
 * but that alone only stops chaining mid-air; it doesn't stop bunny-hopping it on every single landing, so
 * a short cooldown sits on top, tracked the same way {@link WindrunnerStone} tracks its recharge timers.
 * <p>
 * <b>Two fixes over the first version.</b> The arming ran on the shared 2Hz stone tick, which meant the
 * gesture was dead for up to ten ticks after every jump — the exact ten ticks a player reaches for it in —
 * so it now runs on {@link #onFastTick}. And nothing ever un-armed a player who unsocketed the stone while
 * airborne: {@code allowFlight} stayed set with no listener left to cancel the toggle, which is permanent
 * creative flight for the price of one inventory click. {@link #onIdleTick} closes that, and
 * {@link #armedByStone} is why it can do so without stomping {@code WyrmwingPlate}, which arms the very
 * same flag for its own gesture and would otherwise be switched off every tick by this stone's cleanup.
 */
public final class SkyleapStone extends Stone {

    /**
     * Short on purpose. {@link #jumpAvailable} is the real anti-spam gate — one extra jump per airtime, no
     * chaining — so this only exists to stop bunny-hopping a leap off every single landing. Three seconds
     * did that by making the stone feel dead most of the time you reached for it; under a second still
     * breaks the hop loop while leaving it responsive.
     */
    private static final double DEFAULT_COOLDOWN_SECONDS = 0.9;
    private static final Color SKY_COLOR = Color.fromRGB(180, 210, 255);

    private final Map<UUID, Boolean> jumpAvailable = new HashMap<>();
    private final Map<UUID, Long> nextReadyMs = new HashMap<>();
    /** Players whose {@code allowFlight} this stone switched on, and is therefore allowed to switch off. */
    private final Set<UUID> armedByStone = new HashSet<>();

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
        return Math.max(0L, Math.round(configDouble("cooldown-seconds", DEFAULT_COOLDOWN_SECONDS) * 1000));
    }

    private double boostUp() {
        return configDouble("boost-up", 0.85);
    }

    private double boostForward() {
        return configDouble("boost-forward", 0.6);
    }

    @Override
    public void onFastTick(Player player) {
        UUID uuid = player.getUniqueId();
        if (player.isOnGround()) {
            jumpAvailable.put(uuid, true);
            disarm(player);
            return;
        }
        if (!player.getAllowFlight()) {
            player.setAllowFlight(true);
            armedByStone.add(uuid);
        }
    }

    /**
     * Un-arms a player who no longer has the stone socketed. Guarded on {@link #armedByStone} so a player
     * wearing the full Wyrmwing set — which arms the same flag for its own double-tap gesture — doesn't have
     * it torn back down every tick by this stone's housekeeping.
     */
    @Override
    public void onIdleTick(Player player) {
        UUID uuid = player.getUniqueId();
        if (!armedByStone.contains(uuid)) {
            return;
        }
        disarm(player);
        jumpAvailable.remove(uuid);
    }

    private void disarm(Player player) {
        UUID uuid = player.getUniqueId();
        if (!armedByStone.remove(uuid)) {
            return;
        }
        if (player.getAllowFlight() && !player.isFlying()) {
            player.setAllowFlight(false);
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
        Vector direction = player.getLocation().getDirection().setY(0).normalize().multiply(boostForward());
        direction.setY(boostUp());
        player.setVelocity(direction);

        Fx.sound(player, Sound.ENTITY_BREEZE_JUMP, 1.0f, 1.2f);
        Fx.coloredBurst(player.getLocation(), SKY_COLOR, 1.1f, 16, 0.3);
        Fx.trail(player.getLocation(), Particle.CLOUD, 8, 0.25, 0.02);
    }
}

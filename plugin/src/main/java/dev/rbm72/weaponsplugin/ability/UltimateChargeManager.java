package dev.rbm72.weaponsplugin.ability;

import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every player's ultimate charge, for the weapons that opt into being earned instead of waited out.
 * <p>
 * Charge is held <b>per player per weapon id</b>, and that is the whole reason this is a manager rather
 * than state on the weapon: a {@link Weapon} is a single shared instance for the entire server, so a field
 * on it would be one global charge bar that every player on the server filled together. Keying by weapon id
 * rather than by item also means charge survives putting the weapon away — banking progress on your
 * greatsword, swapping to a bow for a phase and coming back to it is correct behaviour, not an exploit,
 * because the decay clock keeps running the whole time.
 *
 * <p>Implements {@link ActionBarHub.Source} for the same reason {@link CooldownManager} does: the action bar
 * holds one line and the last writer each tick wins, so a charge bar that sent itself would strobe against
 * every cooldown timer already there.
 */
public final class UltimateChargeManager implements ActionBarHub.Source {

    /** Decay/readout cadence. 4 ticks is smooth to read and a fifth of the work of a per-tick sweep. */
    private static final long TICK_INTERVAL = 4L;
    /** Segments in the drawn bar. Ten reads as a percentage without needing the number next to it. */
    private static final int BAR_SEGMENTS = 10;
    /** Charge below this fraction is not worth a bar segment or the player's attention. */
    private static final double SHOW_THRESHOLD = 0.02;

    private final Plugin plugin;

    /** player -> weapon id -> that weapon's charge state. */
    private final Map<UUID, Map<String, Charge>> charges = new HashMap<>();

    private static final class Charge {
        double value;
        long lastGainMs;
        /** When the cooldown floor expires — set on spend, so a full meter still cannot chain instantly. */
        long readyAtMs;
        /** Whether the "full" chime has already fired for this fill, so it does not repeat every tick at cap. */
        boolean announcedFull;
    }

    public UltimateChargeManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Starts the decay sweep. Called once from {@code onEnable}. */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, TICK_INTERVAL, TICK_INTERVAL);
    }

    /** This player's charge on {@code weapon}, in spec units. */
    public double charge(Player player, Weapon weapon) {
        Charge charge = existing(player, weapon);
        return charge == null ? 0.0 : charge.value;
    }

    public double fraction(Player player, Weapon weapon) {
        ChargeSpec spec = weapon.ultimateChargeSpec();
        return spec == null ? 0.0 : spec.fractionOf(charge(player, weapon));
    }

    /**
     * True when {@code player} may fire {@code weapon}'s ultimate: meter full, and the floor between two
     * ultimates elapsed.
     * <p>
     * Answers true for a weapon with no charge spec, so the gate in
     * {@link dev.rbm72.weaponsplugin.listeners.WeaponInteractListener} can ask unconditionally and the 50-odd
     * weapons still on pure cooldowns behave exactly as they did.
     */
    public boolean canFire(Player player, Weapon weapon) {
        ChargeSpec spec = weapon.ultimateChargeSpec();
        if (spec == null) {
            return true;
        }
        Charge charge = existing(player, weapon);
        if (charge == null) {
            return false;
        }
        return charge.value >= spec.cap() && System.currentTimeMillis() >= charge.readyAtMs;
    }

    /**
     * Spends a full meter and arms the floor before the next one.
     *
     * @return false if the meter was not full, in which case nothing was spent and the ultimate must not fire.
     */
    public boolean spend(Player player, Weapon weapon) {
        ChargeSpec spec = weapon.ultimateChargeSpec();
        if (spec == null) {
            return true;
        }
        if (!canFire(player, weapon)) {
            return false;
        }
        Charge charge = existing(player, weapon);
        charge.value = 0;
        charge.announcedFull = false;
        charge.readyAtMs = System.currentTimeMillis() + Math.round(spec.cooldownFloorSeconds() * 1000);
        return true;
    }

    /**
     * Adds charge and refreshes the decay grace. All the {@code on*} hooks funnel through here.
     * <p>
     * No-ops for a weapon without a spec, so callers never have to check first — the listeners fire these on
     * every weapon hit in the game, and a null check at each call site is a null check someone forgets.
     */
    public void add(Player player, Weapon weapon, double amount) {
        ChargeSpec spec = weapon.ultimateChargeSpec();
        if (spec == null || amount <= 0) {
            return;
        }
        Charge charge = charges.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .computeIfAbsent(weapon.id(), k -> new Charge());

        boolean wasFull = charge.value >= spec.cap();
        charge.value = Math.min(spec.cap(), charge.value + amount);
        charge.lastGainMs = System.currentTimeMillis();

        if (!wasFull && charge.value >= spec.cap() && !charge.announcedFull) {
            charge.announcedFull = true;
            Fx.sound(player, weapon.readySound(), 0.9f, 1.8f);
            Fx.coloredBurst(player.getEyeLocation(), spec.accent(), 1.4f, 26, 0.6);
        }
    }

    public void onMeleeHit(Player player, Weapon weapon, double damageDealt) {
        ChargeSpec spec = weapon.ultimateChargeSpec();
        if (spec == null) {
            return;
        }
        add(player, weapon, spec.perMeleeHit() + damageDealt * spec.perDamageDealt());
    }

    public void onKill(Player player, Weapon weapon) {
        ChargeSpec spec = weapon.ultimateChargeSpec();
        if (spec != null) {
            add(player, weapon, spec.perKill());
        }
    }

    public void onDamageTaken(Player player, Weapon weapon, double damageTaken) {
        ChargeSpec spec = weapon.ultimateChargeSpec();
        if (spec != null) {
            add(player, weapon, damageTaken * spec.perDamageTaken());
        }
    }

    public void onAbilityCast(Player player, Weapon weapon, CooldownManager.Slot slot) {
        ChargeSpec spec = weapon.ultimateChargeSpec();
        // The ultimate itself never feeds the meter that gates it — that is the loop that lets one
        // ultimate pay for the next, and the cooldown floor alone would only slow it down, not stop it.
        if (spec == null || slot == CooldownManager.Slot.ULTIMATE) {
            return;
        }
        add(player, weapon, spec.perAbilityCast());
    }

    /**
     * Charge readouts for the merged action bar — only for the weapon in hand, and only while it has
     * something to show.
     * <p>
     * Hand-scoped rather than showing every charged weapon at once: a player carrying three charge weapons
     * would otherwise push the cooldown timers off the line entirely, and the bar is read during a fight for
     * the thing currently in their hands.
     */
    @Override
    public List<Component> segments(Player player) {
        Map<String, Charge> perWeapon = charges.get(player.getUniqueId());
        if (perWeapon == null || perWeapon.isEmpty()) {
            return List.of();
        }
        Weapon held = heldWeapon(player);
        if (held == null) {
            return List.of();
        }
        ChargeSpec spec = held.ultimateChargeSpec();
        Charge charge = perWeapon.get(held.id());
        if (spec == null || charge == null) {
            return List.of();
        }
        double fraction = spec.fractionOf(charge.value);
        if (fraction < SHOW_THRESHOLD) {
            return List.of();
        }

        boolean full = charge.value >= spec.cap();
        boolean floored = System.currentTimeMillis() < charge.readyAtMs;
        TextColor colour = full && !floored
                ? NamedTextColor.GREEN
                : TextColor.color(spec.accent().asRGB());

        int filled = (int) Math.round(fraction * BAR_SEGMENTS);
        StringBuilder bar = new StringBuilder(BAR_SEGMENTS);
        for (int i = 0; i < BAR_SEGMENTS; i++) {
            bar.append(i < filled ? '▉' : '░');
        }

        List<Component> parts = new ArrayList<>(1);
        parts.add(Component.text("☠ " + spec.label() + " ", NamedTextColor.GRAY)
                .append(Component.text(bar.toString(), colour))
                .append(Component.text(full
                        ? (floored ? " HELD" : " READY")
                        : " " + Math.round(fraction * 100) + "%", colour)));
        return parts;
    }

    private void sweep() {
        if (charges.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        double elapsedSeconds = TICK_INTERVAL / 20.0;

        charges.entrySet().removeIf(playerEntry -> {
            Player player = plugin.getServer().getPlayer(playerEntry.getKey());
            if (player == null || !player.isOnline()) {
                // Dropped rather than frozen: charge is a combat resource, and holding an offline
                // player's map alive is a leak that grows with every player who ever logged in.
                return true;
            }
            playerEntry.getValue().entrySet().removeIf(weaponEntry -> {
                Charge charge = weaponEntry.getValue();
                ChargeSpec spec = specFor(weaponEntry.getKey());
                if (spec == null || spec.decayPerSecond() <= 0) {
                    return false;
                }
                if (now - charge.lastGainMs < spec.decayGraceSeconds() * 1000) {
                    return false;
                }
                charge.value -= spec.decayPerSecond() * elapsedSeconds;
                if (charge.value <= 0) {
                    charge.value = 0;
                    charge.announcedFull = false;
                    // Empty and past its floor: nothing left to track, so stop paying for the entry.
                    return now >= charge.readyAtMs;
                }
                if (charge.value < spec.cap()) {
                    charge.announcedFull = false;
                }
                return false;
            });
            return playerEntry.getValue().isEmpty();
        });
    }

    private Charge existing(Player player, Weapon weapon) {
        Map<String, Charge> perWeapon = charges.get(player.getUniqueId());
        return perWeapon == null ? null : perWeapon.get(weapon.id());
    }

    private ChargeSpec specFor(String weaponId) {
        if (!(plugin instanceof dev.rbm72.weaponsplugin.WeaponsPlugin wp)) {
            return null;
        }
        return wp.weaponRegistry().get(weaponId).map(Weapon::ultimateChargeSpec).orElse(null);
    }

    private Weapon heldWeapon(Player player) {
        if (!(plugin instanceof dev.rbm72.weaponsplugin.WeaponsPlugin wp)) {
            return null;
        }
        return wp.weaponRegistry().identify(player.getInventory().getItemInMainHand())
                .or(() -> wp.weaponRegistry().identify(player.getInventory().getItemInOffHand()))
                .orElse(null);
    }

    /**
     * The line shown when a player tries an ultimate they have not earned.
     * <p>
     * Told rather than shown, because the failure is otherwise indistinguishable from the ability being
     * broken: a right-click that plays a soft click and does nothing looks identical whether the meter is at
     * 5% or the weapon is bugged.
     */
    public Component notReadyNotice(Player player, Weapon weapon) {
        ChargeSpec spec = weapon.ultimateChargeSpec();
        if (spec == null) {
            return Component.text("Not ready", NamedTextColor.GRAY);
        }
        Charge charge = existing(player, weapon);
        if (charge != null && charge.value >= spec.cap()) {
            long seconds = Math.max(1, Math.round((charge.readyAtMs - System.currentTimeMillis()) / 1000.0));
            return Component.text(spec.label() + " held — " + seconds + "s", NamedTextColor.YELLOW);
        }
        int percent = (int) Math.round(spec.fractionOf(charge == null ? 0 : charge.value) * 100);
        return Component.text(spec.label() + " " + percent + "% — fight for it", NamedTextColor.GRAY);
    }

    /** Drops a disconnecting player's charge. Called from the quit handler so the sweep is not the only reaper. */
    public void forget(Player player) {
        charges.remove(player.getUniqueId());
    }

    /**
     * Feedback for a spent ultimate: the bar visibly empties and the weapon's cast is punctuated.
     * <p>
     * Separate from {@link #spend} so the gate can stay a pure state change — the listener already owns
     * every other piece of cast feedback, and two places firing sounds for one button is how the ultimate
     * ends up double-chiming.
     */
    public void onSpentFx(Player player, Weapon weapon) {
        ChargeSpec spec = weapon.ultimateChargeSpec();
        if (spec == null) {
            return;
        }
        Fx.sound(player, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.7f);
        Fx.coloredRing(player.getLocation(), spec.accent(), 1.3f, 1.8, 30, 0);
    }
}

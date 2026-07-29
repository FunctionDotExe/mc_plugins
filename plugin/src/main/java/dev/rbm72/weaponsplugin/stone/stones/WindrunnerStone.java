package dev.rbm72.weaponsplugin.stone.stones;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.stone.Stone;
import dev.rbm72.weaponsplugin.util.Grounded;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Chainable air dash with its own charge pool instead of a flat cooldown: up to
 * {@link #MAX_CHARGES} dashes stored at once, each regenerating independently every
 * {@link #RECHARGE_MS}. The shared personal-ability cooldown ({@link #personalAbilityCooldownSeconds})
 * is set low — just enough to stop a single sneak-double-tap from double-firing — with the real
 * gate being charge count, tracked here since it's specific to this stone's economy.
 */
public final class WindrunnerStone extends Stone {

    private static final Color WIND_COLOR = Color.fromRGB(225, 240, 220);

    private final Map<UUID, Integer> charges = new HashMap<>();
    private final Map<UUID, Long> nextRechargeMs = new HashMap<>();

    public WindrunnerStone(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "windrunner_stone";
    }

    @Override
    public Material material() {
        return Material.ECHO_SHARD;
    }

    @Override
    public String displayNameText() {
        return "Windrunner Stone";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Air-dash on demand, on a charge", NamedTextColor.GRAY),
                Component.text("pool instead of a flat cooldown —", NamedTextColor.GRAY),
                Component.text("bank two dashes and spend them", NamedTextColor.GRAY),
                Component.text("back to back, or hold one in", NamedTextColor.GRAY),
                Component.text("reserve to save a bad jump.", NamedTextColor.GRAY));
    }

    private int maxCharges() {
        return Math.max(1, configInt("max-charges", 2));
    }

    private long rechargeMs() {
        return Math.max(0L, Math.round(configDouble("recharge-seconds", 4.0) * 1000));
    }

    private double dashSpeed() {
        return configDouble("dash-speed", 1.5);
    }

    private double groundLift() {
        return configDouble("ground-lift", 0.3);
    }

    private double airLift() {
        return configDouble("air-lift", 0.12);
    }

    @Override
    public void onEquipTick(Player player) {
        UUID uuid = player.getUniqueId();
        int max = maxCharges();
        int current = charges.computeIfAbsent(uuid, k -> max);
        if (current >= max) {
            return;
        }
        long now = System.currentTimeMillis();
        Long readyAt = nextRechargeMs.get(uuid);
        if (readyAt == null) {
            nextRechargeMs.put(uuid, now + rechargeMs());
            return;
        }
        if (now >= readyAt) {
            charges.put(uuid, current + 1);
            nextRechargeMs.put(uuid, now + rechargeMs());
        }
    }

    @Override
    public boolean hasPersonalAbility() {
        return true;
    }

    @Override
    public String personalAbilityName() {
        return "Wind Dash";
    }

    @Override
    public List<Component> personalAbilityLore() {
        return List.of(
                Component.text("Burst forward in whatever", NamedTextColor.GRAY),
                Component.text("direction you're facing. Stores", NamedTextColor.GRAY),
                Component.text("up to " + maxCharges() + " charges, each", NamedTextColor.GRAY),
                Component.text(String.format(Locale.ROOT, "recharging every %.0fs.", rechargeMs() / 1000.0),
                        NamedTextColor.GRAY));
    }

    /** Just long enough that one sneak double-tap can't double-fire; the real gate is charge count. */
    @Override
    public double personalAbilityCooldownSeconds() {
        return configDouble("trigger-lockout-seconds", 0.3);
    }

    /** Overridden because this stone's real gate is charge count, not the shared cooldown timer. */
    @Override
    public Component actionBarStatus(Player player, boolean onCooldown, double remainingSeconds) {
        UUID uuid = player.getUniqueId();
        int max = maxCharges();
        int current = charges.computeIfAbsent(uuid, k -> max);

        Component status = displayName().append(Component.text(" " + current + "/" + max,
                current > 0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        if (current < max) {
            long readyAt = nextRechargeMs.getOrDefault(uuid, 0L);
            double secs = Math.max(0, (readyAt - System.currentTimeMillis()) / 1000.0);
            status = status.append(Component.text(String.format(Locale.ROOT, " (+1 in %.1fs)", secs), NamedTextColor.GRAY));
        }
        return status;
    }

    @Override
    public void personalAbility(Player player) {
        UUID uuid = player.getUniqueId();
        int current = charges.computeIfAbsent(uuid, k -> maxCharges());
        if (current <= 0) {
            Fx.sound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
            return;
        }
        charges.put(uuid, current - 1);
        nextRechargeMs.putIfAbsent(uuid, System.currentTimeMillis() + rechargeMs());

        Vector direction = player.getLocation().getDirection().normalize();
        Vector velocity = direction.multiply(dashSpeed());
        boolean airborne = !Grounded.onGround(player);
        velocity.setY(Math.max(velocity.getY(), airborne ? airLift() : groundLift()));
        player.setVelocity(velocity);

        Location origin = player.getLocation().add(0, 1, 0);
        Fx.sound(player, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.8f);
        Fx.sound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.4f);
        Fx.coloredBurst(origin, WIND_COLOR, 1.1f, 16, 0.3);
        Fx.trail(origin, Particle.CLOUD, 8, 0.25, 0.02);
    }
}

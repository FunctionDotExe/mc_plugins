package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.status.StatusEffectManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Time-themed sword: an enemy-only slow field, an afterimage dash, a 5-second rewind, and a freeze-everyone-else ultimate. */
public final class ChronoBlade extends Weapon {

    private static final Color CHRONO_TEAL = Color.fromRGB(64, 224, 208);
    private static final Color CHRONO_ICE = Color.fromRGB(120, 220, 255);

    private final long perfectTimingWindowMs;
    private final double perfectTimingDamageBonus;
    private final double slowFieldRadius;
    private final int slowFieldDurationTicks;
    private final int afterimageCount;
    private final double afterimageDistance;
    private final int afterimageLifetimeTicks;
    private final double rewindBufferSeconds;
    private final double freezeRadius;
    private final int freezeDurationTicks;

    public ChronoBlade(WeaponsPlugin plugin) {
        super(plugin);
        this.perfectTimingWindowMs = configInt("perfect-timing-window-ms", 1000);
        this.perfectTimingDamageBonus = configDouble("perfect-timing-damage-bonus", 0.3);
        this.slowFieldRadius = configDouble("slow-field-radius", 4.5);
        this.slowFieldDurationTicks = configInt("slow-field-duration-ticks", 80);
        this.afterimageCount = configInt("afterimage-count", 4);
        this.afterimageDistance = configDouble("afterimage-distance", 6.0);
        this.afterimageLifetimeTicks = configInt("afterimage-lifetime-ticks", 60);
        this.rewindBufferSeconds = configDouble("rewind-buffer-seconds", 5.0);
        this.freezeRadius = configDouble("freeze-radius", 6.0);
        this.freezeDurationTicks = configInt("freeze-duration-ticks", 70);
    }

    private final Map<UUID, long[]> predictedFinishMs = new HashMap<>();
    private final Map<UUID, Deque<Location>> positionHistory = new HashMap<>();

    @Override
    public String id() {
        return "chrono_blade";
    }

    @Override
    public Material material() {
        return Material.DIAMOND_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Chrono Blade";
    }

    @Override
    public Rarity rarity() {
        return Rarity.MYTHIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 3.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 9.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 6.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 12.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 60.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: slow every enemy", NamedTextColor.GRAY),
                Component.text("nearby. You're unaffected.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Temporal Field";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: dash forward,", NamedTextColor.GRAY),
                Component.text("leaving fading afterimages behind.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Afterimage Dash";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: rewind to where you", NamedTextColor.GRAY),
                Component.text("were 5 seconds ago.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Rewind";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: freeze time for", NamedTextColor.GRAY),
                Component.text("everyone nearby except you.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Time Stop";
    }

    @Override
    public Sound castSound() {
        return Sound.BLOCK_AMETHYST_BLOCK_CHIME;
    }

    @Override
    public Sound hitSound() {
        return Sound.BLOCK_CONDUIT_ACTIVATE;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_BEACON_ACTIVATE;
    }

    @Override
    public void onTick(Player player) {
        Deque<Location> history = positionHistory.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        history.addLast(player.getLocation());
        int maxEntries = (int) Math.ceil((rewindBufferSeconds + 1) * 2);
        while (history.size() > maxEntries) {
            history.removeFirst();
        }
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        long now = System.currentTimeMillis();
        long[] finishTimes = predictedFinishMs.get(attacker.getUniqueId());
        if (finishTimes == null) {
            return;
        }
        for (int i = 0; i < finishTimes.length; i++) {
            if (finishTimes[i] != 0 && now >= finishTimes[i] && now - finishTimes[i] <= perfectTimingWindowMs) {
                event.setDamage(event.getDamage() * (1 + perfectTimingDamageBonus));
                finishTimes[i] = 0;
                Fx.point(victim.getLocation().add(0, 1.5, 0), Particle.END_ROD, 4);
                break;
            }
        }
    }

    @Override
    public void ability1(Player player) {
        predictedFinishMs.computeIfAbsent(player.getUniqueId(), k -> new long[4])[0] = System.currentTimeMillis() + Math.round(ability1CooldownSeconds() * 1000);
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        spinningTimeRing(center, slowFieldRadius);

        for (Entity entity : world.getNearbyEntities(center, slowFieldRadius, slowFieldRadius, slowFieldRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                StatusEffectManager.apply(living, PotionEffectType.SLOWNESS, slowFieldDurationTicks, 2);
                StatusEffectManager.apply(living, PotionEffectType.MINING_FATIGUE, slowFieldDurationTicks, 2);
                Fx.coloredBurst(living.getLocation().add(0, 1, 0), CHRONO_TEAL, 1.8f, 26, 0.5);
                Fx.point(living.getLocation().add(0, 1, 0), Particle.END_ROD, 8);
            }
        }
    }

    /**
     * A briefly spinning teal ring so the slow field reads as a moving time-distortion, not a static
     * circle. Draws an inner and outer ring together for thickness, and runs longer since this is a
     * purely cosmetic loop (the slow potion effect itself is already applied) so the field feels like
     * it lingers instead of flickering out immediately.
     */
    private void spinningTimeRing(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (ticks >= 28) {
                    cancel();
                    return;
                }
                Fx.ring(center, Particle.END_ROD, radius, 40, angle);
                Fx.ring(center, Particle.END_ROD, radius - 0.5, 32, -angle);
                int points = 40;
                double[] radii = {radius - 0.5, radius, radius + 0.5};
                for (double r : radii) {
                    for (int i = 0; i < points; i++) {
                        double a = angle + (2 * Math.PI * i) / points;
                        double x = center.getX() + r * Math.cos(a);
                        double z = center.getZ() + r * Math.sin(a);
                        Fx.coloredBurst(new Location(world, x, center.getY(), z), CHRONO_TEAL, 1.4f, 2, 0.04);
                    }
                }
                angle += 0.35;
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability2(Player player) {
        predictedFinishMs.computeIfAbsent(player.getUniqueId(), k -> new long[4])[1] = System.currentTimeMillis() + Math.round(ability2CooldownSeconds() * 1000);
        Location start = player.getLocation();
        Vector direction = start.getDirection().normalize();
        World world = start.getWorld();
        if (world == null) {
            return;
        }

        for (int i = 1; i <= afterimageCount; i++) {
            Location imageLoc = start.clone().add(direction.clone().multiply(afterimageDistance * i / afterimageCount));
            ArmorStand image = world.spawn(imageLoc, ArmorStand.class, stand -> {
                stand.setInvisible(true);
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setSmall(true);
            });
            Fx.coloredBurst(imageLoc.clone().add(0, 1, 0), CHRONO_TEAL, 1.8f, 28, 0.45);
            Fx.point(imageLoc.clone().add(0, 1, 0), Particle.END_ROD, 10);
            Fx.spinningIcon(plugin, imageLoc.clone().add(0, 0.9, 0), Material.DIAMOND_SWORD, 1.0f, afterimageLifetimeTicks, 12);
            plugin.getServer().getScheduler().runTaskLater(plugin, image::remove, afterimageLifetimeTicks);
        }

        Location end = start.clone().add(direction.clone().multiply(afterimageDistance));
        Location safeEnd = end.getBlock().getType().isSolid() ? start : end;
        player.teleport(safeEnd.setDirection(direction));
    }

    @Override
    public void ability3(Player player) {
        predictedFinishMs.computeIfAbsent(player.getUniqueId(), k -> new long[4])[2] = System.currentTimeMillis() + Math.round(ability3CooldownSeconds() * 1000);
        Deque<Location> history = positionHistory.get(player.getUniqueId());
        if (history == null || history.isEmpty()) {
            return;
        }
        Location rewindTarget = history.peekFirst();
        Fx.burst(player.getLocation(), Particle.REVERSE_PORTAL, 40, 0.6);
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), CHRONO_TEAL, 2.0f, 34, 0.6);
        timeSpiral(player.getLocation(), true);
        player.teleport(rewindTarget);
        Fx.burst(rewindTarget, Particle.REVERSE_PORTAL, 40, 0.6);
        Fx.coloredBurst(rewindTarget.clone().add(0, 1, 0), CHRONO_TEAL, 2.0f, 34, 0.6);
        timeSpiral(rewindTarget, false);
        Fx.sound(player, Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 1.3f);
    }

    /**
     * A helix that spirals inward (departure) or unwinds outward (arrival) — sells "rewinding time"
     * instead of a flat burst. Two counter-rotating strand sets (doubled strand count) give it a dense,
     * braided look instead of a sparse 3-point dotted line, and it runs longer since this is a purely
     * cosmetic tail on an already-completed teleport.
     */
    private void timeSpiral(Location base, boolean inward) {
        Location origin = base.clone();
        if (origin.getWorld() == null) {
            return;
        }
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 18) {
                    cancel();
                    return;
                }
                double progress = ticks / 18.0;
                double radius = inward ? 1.6 * (1 - progress) : 1.6 * progress;
                double height = inward ? 1.8 * progress : 1.8 * (1 - progress);
                double angle = ticks * (inward ? 0.9 : -0.9);
                Fx.helixFrame(origin, Particle.REVERSE_PORTAL, Math.max(radius, 0.1), 6, angle, height);
                Fx.helixFrame(origin, Particle.REVERSE_PORTAL, Math.max(radius * 0.7, 0.1), 6, -angle * 1.3, height + 0.2);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ultimate(Player player) {
        predictedFinishMs.computeIfAbsent(player.getUniqueId(), k -> new long[4])[3] = System.currentTimeMillis() + Math.round(ultimateCooldownSeconds() * 1000);
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.6f);

        // Big shockwave burst at the caster so the ultimate reads as a real "time stop" epicenter,
        // not just quiet particles appearing on distant enemies.
        Fx.coloredBurst(center.clone().add(0, 1, 0), CHRONO_ICE, 2.4f, 50, freezeRadius * 0.5);
        Fx.expandingRings(plugin, center.clone().add(0, 0.1, 0), Particle.END_ROD, freezeRadius, 5, 3L);

        List<LivingEntity> frozen = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center, freezeRadius, freezeRadius, freezeRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                StatusEffectManager.apply(living, PotionEffectType.SLOWNESS, freezeDurationTicks, 6);
                StatusEffectManager.apply(living, PotionEffectType.MINING_FATIGUE, freezeDurationTicks, 6);
                living.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, freezeDurationTicks, -10, false, false));
                Fx.coloredBurst(living.getLocation().add(0, 1, 0), CHRONO_ICE, 2.2f, 36, 0.6);
                Fx.point(living.getLocation().add(0, 1, 0), Particle.END_ROD, 16);
                Fx.spinningIcon(plugin, living.getLocation().add(0, 2.3, 0), Material.CLOCK, 1.2f, freezeDurationTicks, 10);
                frozen.add(living);
            }
        }

        // Sustained ambient frost around every frozen target for the whole freeze window (cosmetic
        // only — the slowness/mining-fatigue/jump-boost effects above are already applied once and
        // are not re-triggered here) so the ultimate feels like it's actively holding time the entire
        // duration instead of firing one burst and going quiet.
        if (!frozen.isEmpty()) {
            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (ticks >= freezeDurationTicks) {
                        cancel();
                        return;
                    }
                    if (ticks % 4 == 0) {
                        for (LivingEntity living : frozen) {
                            if (!living.isValid()) {
                                continue;
                            }
                            Fx.coloredBurst(living.getLocation().add(0, 1, 0), CHRONO_ICE, 1.4f, 8, 0.35);
                            Fx.point(living.getLocation().add(0, 1, 0), Particle.END_ROD, 3);
                        }
                    }
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }
}

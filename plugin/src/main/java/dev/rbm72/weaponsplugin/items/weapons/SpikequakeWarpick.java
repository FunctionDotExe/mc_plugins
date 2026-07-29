package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Earthbending-style spike weapon: every ability raises real, physically-present
 * {@link Material#POINTED_DRIPSTONE} spike props ({@link BlockDisplay}, not particles) that grow
 * out of the ground in front of the caster, impaling anything caught mid-rise. The ultimate scales
 * that same primitive up into expanding rings of spikes for an Avatar-finale-style eruption.
 */
public final class SpikequakeWarpick extends Weapon {

    private static final Color STONE_GRAY = Color.fromRGB(120, 110, 100);

    private final double impaleDamage;
    private final double impaleRange;
    private final double impaleHitRadius;
    private final double wallDamagePerTick;
    private final int wallSpikeCount;
    private final double wallRadius;
    private final int wallDurationTicks;
    private final double trapDamage;
    private final double trapRadius;
    private final double trapTriggerRadius;
    private final int trapArmDelayTicks;
    private final int trapLifetimeTicks;
    private final int ultimateRings;
    private final double ultimateMaxRadius;
    private final double ultimateDamage;

    public SpikequakeWarpick(WeaponsPlugin plugin) {
        super(plugin);
        this.impaleDamage = configDouble("impale-damage", 8.0);
        this.impaleRange = configDouble("impale-range", 6.0);
        this.impaleHitRadius = configDouble("impale-hit-radius", 1.3);
        this.wallDamagePerTick = configDouble("wall-damage-per-tick", 1.5);
        this.wallSpikeCount = configInt("wall-spike-count", 10);
        this.wallRadius = configDouble("wall-radius", 3.5);
        this.wallDurationTicks = configInt("wall-duration-ticks", 80);
        this.trapDamage = configDouble("trap-damage", 10.0);
        this.trapRadius = configDouble("trap-radius", 2.5);
        this.trapTriggerRadius = configDouble("trap-trigger-radius", 1.8);
        this.trapArmDelayTicks = configInt("trap-arm-delay-ticks", 10);
        this.trapLifetimeTicks = configInt("trap-lifetime-ticks", 200);
        this.ultimateRings = configInt("ultimate-rings", 4);
        this.ultimateMaxRadius = configDouble("ultimate-max-radius", 8.0);
        this.ultimateDamage = configDouble("ultimate-damage", 9.0);
    }

    @Override
    public String id() {
        return "spikequake_warpick";
    }

    @Override
    public Material material() {
        return Material.IRON_PICKAXE;
    }

    @Override
    public String displayNameText() {
        return "Spikequake Warpick";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 4.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 6.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 10.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 8.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 55.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Upheaval")
                .accent(STONE_GRAY)
                .perMeleeHit(configDouble("upheaval-per-hit", 6.0))
                .perDamageDealt(configDouble("upheaval-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("upheaval-per-ability", 8.0))
                .perKill(configDouble("upheaval-per-kill", 12.0))
                .decay(configDouble("upheaval-decay-per-second", 2.0), configDouble("upheaval-decay-grace", 7.0))
                .cooldownFloor(configDouble("upheaval-cooldown-floor", 50.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: a line of jagged", NamedTextColor.GRAY),
                Component.text("spikes erupts from the ground", NamedTextColor.GRAY),
                Component.text("ahead of you, impaling enemies.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Impale";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: raise a ring", NamedTextColor.GRAY),
                Component.text("of spikes around you that keeps", NamedTextColor.GRAY),
                Component.text("goring anyone standing in it.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Spike Ring";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: plant a hidden trap", NamedTextColor.GRAY),
                Component.text("that erupts in spikes when an", NamedTextColor.GRAY),
                Component.text("enemy walks over it.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Spike Trap";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: rings of towering", NamedTextColor.GRAY),
                Component.text("spikes erupt outward from you in", NamedTextColor.GRAY),
                Component.text("a single cataclysmic upheaval.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Cataclysmic Eruption";
    }

    @Override
    public Sound castSound() {
        return Sound.BLOCK_POINTED_DRIPSTONE_BREAK;
    }

    @Override
    public Sound hitSound() {
        return Sound.BLOCK_POINTED_DRIPSTONE_HIT;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_ANVIL_LAND;
    }

    /**
     * Grows a single {@link Material#POINTED_DRIPSTONE} spike prop out of the ground at {@code base}:
     * a {@link BlockDisplay} whose vertical scale animates from 0 up to {@code maxHeight} over
     * {@code riseTicks}, dealing {@code damage} to anything within {@code hitRadius} the instant it
     * finishes rising (the "impale" moment) before the spike lingers and self-removes.
     */
    private void spike(Player caster, Location base, float maxHeight, int riseTicks, int lingerTicks,
                       double hitRadius, double damage, Set<UUID> alreadyHit) {
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        BlockData data = Material.POINTED_DRIPSTONE.createBlockData();
        BlockDisplay display = world.spawn(base, BlockDisplay.class, entity -> {
            entity.setBlock(data);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(1, 0.01f, 1),
                    new AxisAngle4f(0, 0, 1, 0)));
            entity.setPersistent(false);
        });
        Fx.blockBurst(base, Material.POINTED_DRIPSTONE, 10, 0.3);

        new BukkitRunnable() {
            int ticks = 0;
            boolean impaled = false;

            @Override
            public void run() {
                if (display.isDead()) {
                    cancel();
                    return;
                }
                if (ticks <= riseTicks) {
                    float height = maxHeight * (ticks / (float) riseTicks);
                    display.setTransformation(new Transformation(
                            new Vector3f(0, 0, 0),
                            new AxisAngle4f(0, 0, 1, 0),
                            new Vector3f(1, Math.max(0.01f, height), 1),
                            new AxisAngle4f(0, 0, 1, 0)));
                    if (!impaled && ticks >= riseTicks - 1) {
                        impaled = true;
                        Location hitCenter = base.clone().add(0, maxHeight * 0.5, 0);
                        for (Entity nearby : world.getNearbyEntities(hitCenter, hitRadius, maxHeight * 0.6, hitRadius)) {
                            if (nearby instanceof LivingEntity living && !nearby.equals(caster)
                                    && (alreadyHit == null || alreadyHit.add(living.getUniqueId()))) {
                                living.damage(damage, caster);
                                living.setVelocity(living.getVelocity().setY(Math.min(0.6, living.getVelocity().getY() + 0.5)));
                                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                            }
                        }
                    }
                } else if (ticks >= riseTicks + lingerTicks) {
                    display.remove();
                    cancel();
                    return;
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability1(Player player) {
        double damage = impaleDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Vector direction = origin.getDirection().setY(0).normalize();
        Fx.sound(player, castSound(), 1.0f, 0.9f);
        Set<UUID> alreadyHit = new HashSet<>();

        for (int i = 1; i <= 4; i++) {
            Location point = origin.clone().add(direction.clone().multiply(i * 1.4));
            Block ground = world.getHighestBlockAt(point.getBlockX(), point.getBlockZ());
            Location base = ground.getLocation().add(0.5, 1.0, 0.5);
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> spike(player, base, 2.2f, 4, 20, impaleHitRadius, damage, alreadyHit), (long) (i - 1) * 2L);
        }
    }

    @Override
    public void ability2(Player player) {
        double damagePerTick = wallDamagePerTick * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, castSound(), 1.0f, 0.7f);
        Fx.coloredBurst(center.clone().add(0, 0.2, 0), STONE_GRAY, 2.0f, 30, wallRadius * 0.5);

        for (int i = 0; i < wallSpikeCount; i++) {
            double angle = (2 * Math.PI * i) / wallSpikeCount;
            Location point = center.clone().add(Math.cos(angle) * wallRadius, 0, Math.sin(angle) * wallRadius);
            Block ground = world.getHighestBlockAt(point.getBlockX(), point.getBlockZ());
            Location base = ground.getLocation().add(0.5, 1.0, 0.5);
            spike(player, base, 1.8f, 5, wallDurationTicks - 5, 1.1, damagePerTick, null);
        }

        new BukkitRunnable() {
            int ticks = 0;
            final Set<UUID> recentlyHit = new HashSet<>();

            @Override
            public void run() {
                if (ticks >= wallDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                if (ticks % 10 == 0) {
                    recentlyHit.clear();
                    for (int i = 0; i < wallSpikeCount; i++) {
                        double angle = (2 * Math.PI * i) / wallSpikeCount;
                        Location point = center.clone().add(Math.cos(angle) * wallRadius, 1, Math.sin(angle) * wallRadius);
                        for (Entity entity : world.getNearbyEntities(point, 1.1, 1.4, 1.1)) {
                            if (entity instanceof LivingEntity living && !entity.equals(player) && recentlyHit.add(living.getUniqueId())) {
                                living.damage(damagePerTick, player);
                                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                            }
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 10L, 1L);
    }

    @Override
    public void ability3(Player player) {
        double damage = trapDamage * rarity().statMultiplier();
        Block targetBlock = player.getTargetBlockExact(20, FluidCollisionMode.NEVER);
        if (targetBlock == null) {
            return;
        }
        World world = targetBlock.getWorld();
        Location trapLoc = targetBlock.getLocation().add(0.5, 1, 0.5);
        Fx.sound(trapLoc, Sound.BLOCK_STONE_PLACE, 0.7f, 0.8f);
        Fx.coloredBurst(trapLoc, STONE_GRAY, 1.2f, 10, 0.4);

        new BukkitRunnable() {
            int ticks = 0;
            boolean armed = false;
            boolean triggered = false;

            @Override
            public void run() {
                if (triggered || !player.isOnline() || ticks >= trapLifetimeTicks) {
                    cancel();
                    return;
                }
                if (!armed) {
                    if (ticks >= trapArmDelayTicks) {
                        armed = true;
                    }
                    ticks++;
                    return;
                }
                for (Entity entity : world.getNearbyEntities(trapLoc, trapTriggerRadius, 1.5, trapTriggerRadius)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player)) {
                        triggered = true;
                        Fx.sound(trapLoc, hitSound(), 1.2f, 0.8f);
                        int ring = 8;
                        for (int i = 0; i < ring; i++) {
                            double angle = (2 * Math.PI * i) / ring;
                            Location spikeLoc = trapLoc.clone().add(Math.cos(angle) * trapRadius * 0.6, -1, Math.sin(angle) * trapRadius * 0.6);
                            spike(player, spikeLoc, 2.0f, 3, 20, 1.2, damage, new HashSet<>());
                        }
                        cancel();
                        return;
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @Override
    public void ultimate(Player player) {
        double damage = ultimateDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ENTITY_RAVAGER_ROAR, 1.3f, 0.6f);
        Fx.sound(player, Sound.BLOCK_POINTED_DRIPSTONE_BREAK, 1.5f, 0.5f);

        new BukkitRunnable() {
            int ring = 0;

            @Override
            public void run() {
                if (ring >= ultimateRings) {
                    cancel();
                    return;
                }
                double radius = ultimateMaxRadius * (ring + 1) / (double) ultimateRings;
                float height = 2.0f + ring * 0.6f;
                int spikeCount = 10 + ring * 4;
                Set<UUID> alreadyHit = new HashSet<>();

                for (int i = 0; i < spikeCount; i++) {
                    double angle = (2 * Math.PI * i) / spikeCount;
                    Location point = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                    Block ground = world.getHighestBlockAt(point.getBlockX(), point.getBlockZ());
                    Location base = ground.getLocation().add(0.5, 1.0, 0.5);
                    spike(player, base, height, 5, 25, 1.6, damage, alreadyHit);
                }
                Fx.coloredBurst(center.clone().add(0, 0.2, 0), STONE_GRAY, 2.4f, 20, radius * 0.4);
                ring++;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }
}

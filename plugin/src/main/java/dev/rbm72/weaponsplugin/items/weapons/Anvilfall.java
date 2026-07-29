package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.util.Grounded;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cartoon-physical crush hammer: every ability is a real, gravity-driven {@link Material#ANVIL}
 * {@link FallingBlock} — dropped overhead, driven into the ground underfoot, bowled through a line
 * of enemies, or triple-stacked onto a single target for the ultimate. Anvils never place or drop
 * (matching every other falling-block weapon in this roster), so nothing about it grief's the world.
 */
public final class Anvilfall extends Weapon {

    private static final Color IRON_GRAY = Color.fromRGB(150, 150, 158);

    private final double overheadDropDamage;
    private final double overheadDropRange;
    private final double overheadDropRadius;
    private final int overheadSlowTicks;
    private final double piledriverDamage;
    private final double piledriverRadius;
    private final double scrapTossDamage;
    private final double scrapTossSpeed;
    private final double scrapTossPierceRadius;
    private final int ultimateDropCount;
    private final double ultimateDropDamageEach;
    private final int ultimateWeaknessTicks;

    public Anvilfall(WeaponsPlugin plugin) {
        super(plugin);
        this.overheadDropDamage = configDouble("overhead-drop-damage", 10.0);
        this.overheadDropRange = configDouble("overhead-drop-range", 8.0);
        this.overheadDropRadius = configDouble("overhead-drop-radius", 1.8);
        this.overheadSlowTicks = configInt("overhead-slow-ticks", 40);
        this.piledriverDamage = configDouble("piledriver-damage", 7.0);
        this.piledriverRadius = configDouble("piledriver-radius", 3.0);
        this.scrapTossDamage = configDouble("scrap-toss-damage", 6.0);
        this.scrapTossSpeed = configDouble("scrap-toss-speed", 1.3);
        this.scrapTossPierceRadius = configDouble("scrap-toss-pierce-radius", 1.3);
        this.ultimateDropCount = configInt("ultimate-drop-count", 3);
        this.ultimateDropDamageEach = configDouble("ultimate-drop-damage-each", 9.0);
        this.ultimateWeaknessTicks = configInt("ultimate-weakness-ticks", 100);
    }

    @Override
    public String id() {
        return "anvilfall";
    }

    @Override
    public Material material() {
        return Material.ANVIL;
    }

    @Override
    public String displayNameText() {
        return "Anvilfall";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 5.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 7.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 9.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 8.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 50.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Momentum")
                .accent(IRON_GRAY)
                .perMeleeHit(configDouble("momentum-per-hit", 6.0))
                .perDamageDealt(configDouble("momentum-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("momentum-per-ability", 8.0))
                .perKill(configDouble("momentum-per-kill", 10.0))
                .decay(configDouble("momentum-decay-per-second", 2.0), configDouble("momentum-decay-grace", 7.0))
                .cooldownFloor(configDouble("momentum-cooldown-floor", 45.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: drop a real anvil", NamedTextColor.GRAY),
                Component.text("on the enemy ahead of you,", NamedTextColor.GRAY),
                Component.text("pinning them under the weight.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Overhead Drop";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: leap up and", NamedTextColor.GRAY),
                Component.text("drive an anvil into the ground", NamedTextColor.GRAY),
                Component.text("beneath you on landing.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Piledriver";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: bowl a real anvil", NamedTextColor.GRAY),
                Component.text("forward through everything in", NamedTextColor.GRAY),
                Component.text("its path, shattering into scrap.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Scrap Toss";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: three anvils drop", NamedTextColor.GRAY),
                Component.text("on your target in rapid succession,", NamedTextColor.GRAY),
                Component.text("shattering their defenses for good.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "The Great Weight";
    }

    @Override
    public Sound castSound() {
        return Sound.BLOCK_ANVIL_LAND;
    }

    @Override
    public Sound hitSound() {
        return Sound.BLOCK_ANVIL_LAND;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_ANVIL_USE;
    }

    /** Closest living entity within {@code range} that the player is roughly facing — same cone-lookup every targeted-drop ability shares. */
    private LivingEntity closestInFront(Player player, double range) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        LivingEntity best = null;
        double closest = range;
        for (Entity entity : world.getNearbyEntities(eye, range, range, range)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(eye.toVector());
            double distance = toEntity.length();
            if (distance > range || distance < 0.001) {
                continue;
            }
            double alignment = toEntity.normalize().dot(direction);
            if (alignment > 0.85 && distance < closest) {
                closest = distance;
                best = living;
            }
        }
        return best;
    }

    /** Drops one real anvil straight down onto {@code target}, damaging + slowing everything within {@code radius} of impact. */
    private void dropAnvil(Player caster, Location target, double damage, double radius, int slowTicks) {
        World world = target.getWorld();
        if (world == null) {
            return;
        }
        Location spawnAt = target.clone().add(0, 10, 0);
        BlockData anvilData = Material.ANVIL.createBlockData();
        FallingBlock anvil = world.spawn(spawnAt, FallingBlock.class, fb -> fb.setBlockData(anvilData));
        anvil.setDropItem(false);
        anvil.setCancelDrop(true);
        anvil.setHurtEntities(false);
        anvil.setPersistent(false);
        anvil.setVelocity(new Vector(0, -0.1, 0));

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!anvil.isValid() || anvil.isOnGround() || ticks >= 40) {
                    if (anvil.isValid()) {
                        Location impact = anvil.getLocation();
                        Fx.sound(impact, hitSound(), 1.1f, 0.8f);
                        Fx.coloredBurst(impact.clone().add(0, 0.3, 0), IRON_GRAY, 2.0f, 30, radius * 0.6);
                        Fx.blockBurst(impact, Material.ANVIL, 20, 0.6);
                        for (Entity nearby : world.getNearbyEntities(impact, radius, radius, radius)) {
                            if (nearby instanceof LivingEntity living) {
                                living.damage(damage, caster);
                                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 3, false, true));
                                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                            }
                        }
                        anvil.remove();
                    }
                    cancel();
                    return;
                }
                Fx.trail(anvil.getLocation(), Particle.CRIT, 2, 0.05, 0.01);
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void ability1(Player player) {
        double damage = overheadDropDamage * rarity().statMultiplier();
        LivingEntity target = closestInFront(player, overheadDropRange);
        Location dropAt = target != null
                ? target.getLocation()
                : player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(4));
        Fx.sound(player, Sound.ITEM_ARMOR_EQUIP_IRON, 1.0f, 0.7f);
        dropAnvil(player, dropAt, damage, overheadDropRadius, overheadSlowTicks);
    }

    @Override
    public void ability2(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.multiply(0.25).setY(1.0));
        Fx.sound(player, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 0.7f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                Fx.point(player.getLocation(), Particle.CRIT, 2);
                if ((ticks > 3 && Grounded.onGround(player)) || ticks >= 30) {
                    cancel();
                    Location loc = player.getLocation();
                    World world = loc.getWorld();
                    if (world == null) {
                        return;
                    }
                    double damage = piledriverDamage * rarity().statMultiplier();
                    Fx.sound(player, hitSound(), 1.1f, 0.8f);
                    Fx.expandingRings(plugin, loc, Particle.CLOUD, piledriverRadius * 1.1, 3, 2L);
                    Fx.coloredBurst(loc.clone().add(0, 0.2, 0), IRON_GRAY, 2.0f, 30, piledriverRadius * 0.5);
                    Fx.blockBurst(loc, Material.ANVIL, 18, 0.7);
                    for (Entity nearby : world.getNearbyEntities(loc, piledriverRadius, piledriverRadius, piledriverRadius)) {
                        if (nearby instanceof LivingEntity living && !nearby.equals(player)) {
                            living.damage(damage, player);
                            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                            Vector away = living.getLocation().toVector().subtract(loc.toVector());
                            if (away.lengthSquared() < 0.01) {
                                away = new Vector(1, 0, 0);
                            }
                            living.setVelocity(away.normalize().multiply(0.9).setY(0.45));
                        }
                    }
                    return;
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability3(Player player) {
        double damage = scrapTossDamage * rarity().statMultiplier();
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize();
        BlockData anvilData = Material.ANVIL.createBlockData();
        FallingBlock anvil = world.spawn(start, FallingBlock.class, fb -> fb.setBlockData(anvilData));
        anvil.setDropItem(false);
        anvil.setCancelDrop(true);
        anvil.setHurtEntities(false);
        anvil.setPersistent(false);
        anvil.setGravity(false);
        anvil.setVelocity(direction.multiply(scrapTossSpeed));
        Fx.sound(player, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.9f);

        new BukkitRunnable() {
            int ticks = 0;
            final Set<UUID> alreadyHit = new HashSet<>();

            @Override
            public void run() {
                if (!anvil.isValid() || ticks >= 40) {
                    if (anvil.isValid()) {
                        shatter(anvil.getLocation());
                        anvil.remove();
                    }
                    cancel();
                    return;
                }
                Location loc = anvil.getLocation();
                Fx.trail(loc, Particle.CRIT, 2, 0.05, 0.01);
                if (loc.getBlock().getType().isSolid()) {
                    shatter(loc);
                    anvil.remove();
                    cancel();
                    return;
                }
                for (Entity entity : world.getNearbyEntities(loc, scrapTossPierceRadius, scrapTossPierceRadius, scrapTossPierceRadius)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player) && alreadyHit.add(living.getUniqueId())) {
                        living.damage(damage, player);
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                        living.setVelocity(direction.clone().multiply(0.6).setY(0.25));
                    }
                }
                ticks++;
            }

            void shatter(Location loc) {
                Fx.sound(loc, hitSound(), 1.0f, 0.9f);
                Fx.blockBurst(loc, Material.ANVIL, 24, 0.6);
                Fx.coloredBurst(loc, IRON_GRAY, 1.8f, 20, 0.4);
                for (int i = 0; i < 6; i++) {
                    Item nugget = world.dropItem(loc, new ItemStack(Material.IRON_NUGGET));
                    nugget.setPickupDelay(Integer.MAX_VALUE);
                    nugget.setGlowing(true);
                    nugget.setVelocity(new Vector(
                            ThreadLocalRandom.current().nextDouble(-0.4, 0.4),
                            ThreadLocalRandom.current().nextDouble(0.2, 0.5),
                            ThreadLocalRandom.current().nextDouble(-0.4, 0.4)));
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!nugget.isDead()) {
                                nugget.remove();
                            }
                        }
                    }.runTaskLater(plugin, 30L);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ultimate(Player player) {
        double damageEach = ultimateDropDamageEach * rarity().statMultiplier();
        LivingEntity target = closestInFront(player, overheadDropRange * 1.5);
        Location dropAt = target != null
                ? target.getLocation()
                : player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(4));
        Fx.sound(player, Sound.ENTITY_RAVAGER_ROAR, 1.1f, 0.6f);

        for (int i = 0; i < ultimateDropCount; i++) {
            int delay = i * 12;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Location current = target != null && target.isValid() ? target.getLocation() : dropAt;
                dropAnvil(player, current, damageEach, overheadDropRadius * 1.2, overheadSlowTicks);
                if (target instanceof LivingEntity living && living.isValid()) {
                    living.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ultimateWeaknessTicks, 1, false, true));
                }
            }, delay);
        }
    }
}

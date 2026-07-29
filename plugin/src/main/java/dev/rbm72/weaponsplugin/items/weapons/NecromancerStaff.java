package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
import dev.rbm72.weaponsplugin.ability.SummonManager;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.listeners.PlayerSummonTargetListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/** Summoner staff: raises skeletons, summons harassing vexes, throws a bone barrage, and calls a giant undead guardian ultimate. */
public final class NecromancerStaff extends Weapon {

    private final int skeletonCount;
    private final int skeletonDurationTicks;
    private final double skeletonHealthBonus;
    private final double skeletonDamageBonus;
    private final int ghostCount;
    private final int ghostDurationTicks;
    private final int barrageProjectileCount;
    private final double barrageDamage;
    private final double barrageSpeed;
    private final double barrageSpreadDegrees;
    private final int guardianDurationTicks;
    private final double guardianHealthMultiplier;
    private final double guardianDamageMultiplier;
    private final double guardianScale;
    private final int killBuffDurationTicks;
    private final NamespacedKey playerSummonKey;

    public NecromancerStaff(WeaponsPlugin plugin) {
        super(plugin);
        this.playerSummonKey = new NamespacedKey(plugin, PlayerSummonTargetListener.KEY_NAME);
        this.skeletonCount = configInt("skeleton-count", 2);
        this.skeletonDurationTicks = configInt("skeleton-duration-ticks", 400);
        this.skeletonHealthBonus = configDouble("skeleton-health-bonus", 10.0);
        this.skeletonDamageBonus = configDouble("skeleton-damage-bonus", 2.0);
        this.ghostCount = configInt("ghost-count", 2);
        this.ghostDurationTicks = configInt("ghost-duration-ticks", 300);
        this.barrageProjectileCount = configInt("barrage-projectile-count", 5);
        this.barrageDamage = configDouble("barrage-damage", 3.0);
        this.barrageSpeed = configDouble("barrage-speed", 1.6);
        this.barrageSpreadDegrees = configDouble("barrage-spread-degrees", 8.0);
        this.guardianDurationTicks = configInt("guardian-duration-ticks", 600);
        this.guardianHealthMultiplier = configDouble("guardian-health-multiplier", 3.0);
        this.guardianDamageMultiplier = configDouble("guardian-damage-multiplier", 2.5);
        this.guardianScale = configDouble("guardian-scale", 1.6);
        this.killBuffDurationTicks = configInt("kill-buff-duration-ticks", 100);
    }

    private final SummonManager summonManager = new SummonManager();

    @Override
    public String id() {
        return "necromancer_staff";
    }

    @Override
    public Material material() {
        return Material.STICK;
    }

    @Override
    public String displayNameText() {
        return "Necromancer Staff";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 1.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 20.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 15.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 6.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 60.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Ritual")
                .accent(Color.fromRGB(90, 130, 90))
                .perMeleeHit(configDouble("ritual-per-hit", 3.0))
                .perDamageDealt(configDouble("ritual-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("ritual-per-ability", 10.0))
                .perKill(configDouble("ritual-per-kill", 11.0))
                .decay(configDouble("ritual-decay-per-second", 2.0), configDouble("ritual-decay-grace", 6.0))
                .cooldownFloor(configDouble("ritual-cooldown-floor", 55.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: crack open the earth and", NamedTextColor.GRAY),
                Component.text("raise skeleton thralls to fight for you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Raise Dead";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: loose a wail of", NamedTextColor.GRAY),
                Component.text("vengeful spirits to harass nearby foes.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Vengeful Spirits";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: rattle a grisly barrage of", NamedTextColor.GRAY),
                Component.text("bones down on everything ahead.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Bone Barrage";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: tear a colossal undead", NamedTextColor.GRAY),
                Component.text("guardian from the grave to fight for you.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Undead Guardian";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_ZOMBIE_VILLAGER_CURE;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_SKELETON_HURT;
    }

    @Override
    public Sound readySound() {
        return Sound.ENTITY_WITHER_AMBIENT;
    }

    @Override
    public void onKill(Player attacker, LivingEntity victim) {
        for (Mob summon : summonManager.activeSummons(attacker)) {
            summon.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, killBuffDurationTicks, 0));
            summon.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, killBuffDurationTicks, 0));
            Fx.burst(summon.getLocation().add(0, 1, 0), Particle.SOUL, 10, 0.3);
            Fx.coloredBurst(summon.getLocation().add(0, 1, 0), Color.fromRGB(90, 130, 90), 1.0f, 8, 0.3);
        }
    }

    private void tagAsPlayerSummon(Mob mob) {
        mob.getPersistentDataContainer().set(playerSummonKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void despawnAfter(Mob mob, int durationTicks) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!mob.isValid() || ticks >= durationTicks) {
                    mob.remove();
                    cancel();
                    return;
                }
                ticks += 10;
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    @Override
    public void ability1(Player player) {
        World world = player.getWorld();
        Location origin = player.getLocation();
        double healthBonus = skeletonHealthBonus * rarity().statMultiplier();
        double damageBonus = skeletonDamageBonus * rarity().statMultiplier();

        for (int i = 0; i < skeletonCount; i++) {
            Location spawnLoc = origin.clone().add((Math.random() - 0.5) * 3, 0, (Math.random() - 0.5) * 3);
            Skeleton skeleton = world.spawn(spawnLoc, Skeleton.class, mob -> {
                mob.setCanPickupItems(false);
                mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(mob.getAttribute(Attribute.MAX_HEALTH).getBaseValue() + healthBonus);
                mob.setHealth(mob.getAttribute(Attribute.MAX_HEALTH).getValue());
                mob.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(damageBonus);
                tagAsPlayerSummon(mob);
            });
            summonManager.add(player, skeleton);
            despawnAfter(skeleton, skeletonDurationTicks);
            Fx.coloredBurst(spawnLoc.clone().add(0, 1, 0), Color.fromRGB(235, 235, 210), 1.8f, 22, 0.5);

            Location riseBase = spawnLoc.clone();
            new BukkitRunnable() {
                int ticks = 0;
                double angle = 0;

                @Override
                public void run() {
                    if (ticks >= 18) {
                        cancel();
                        return;
                    }
                    angle += Math.PI / 5;
                    Fx.helixFrame(riseBase, Particle.SOUL, 0.7, 8, angle, ticks * 0.15);
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
        Fx.sound(player, Sound.ENTITY_SKELETON_AMBIENT, 1.0f, 0.7f);
    }

    @Override
    public void ability2(Player player) {
        World world = player.getWorld();
        Location origin = player.getLocation();

        for (int i = 0; i < ghostCount; i++) {
            Location spawnLoc = origin.clone().add((Math.random() - 0.5) * 3, 1, (Math.random() - 0.5) * 3);
            Vex ghost = world.spawn(spawnLoc, Vex.class, this::tagAsPlayerSummon);
            summonManager.add(player, ghost);
            despawnAfter(ghost, ghostDurationTicks);
            Fx.coloredBurst(spawnLoc, Color.fromRGB(180, 220, 200), 1.6f, 20, 0.45);

            Location swirlBase = spawnLoc.clone();
            new BukkitRunnable() {
                int ticks = 0;
                double angle = 0;

                @Override
                public void run() {
                    if (ticks >= 10) {
                        cancel();
                        return;
                    }
                    angle += Math.PI / 3;
                    Fx.helixFrame(swirlBase, Particle.SOUL, 0.3, 4, angle, Math.sin(ticks * 0.6) * 0.4);
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
        Fx.sound(player, Sound.ENTITY_VEX_AMBIENT, 1.0f, 1.0f);
    }

    @Override
    public void ability3(Player player) {
        Location eye = player.getEyeLocation();

        for (int i = 0; i < barrageProjectileCount; i++) {
            double offsetDegrees = (i - (barrageProjectileCount - 1) / 2.0) * barrageSpreadDegrees;
            Vector direction = eye.getDirection().clone();
            double radians = Math.toRadians(offsetDegrees);
            Vector rotated = rotateAroundY(direction, radians);

            Snowball projectile = player.launchProjectile(Snowball.class, rotated.multiply(barrageSpeed));
            projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());

            var icon = Fx.spinningIcon(plugin, projectile.getLocation(), Material.BONE, 0.7f, 40, 20.0);

            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (!projectile.isValid()) {
                        if (icon != null && !icon.isDead()) {
                            icon.remove();
                        }
                        cancel();
                        return;
                    }
                    Fx.point(projectile.getLocation(), Particle.SOUL, 3);
                    if (ticks % 2 == 0) {
                        Fx.coloredBurst(projectile.getLocation(), Color.fromRGB(235, 235, 210), 0.7f, 2, 0.05);
                    }
                    if (icon != null && !icon.isDead()) {
                        icon.teleport(projectile.getLocation());
                    }
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }

    private Vector rotateAroundY(Vector v, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = v.getX() * cos + v.getZ() * sin;
        double z = -v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        double damage = barrageDamage * rarity().statMultiplier();
        Fx.burst(loc, Particle.SOUL, 10, 0.3);
        Fx.coloredBurst(loc, Color.fromRGB(235, 235, 210), 1.0f, 14, 0.3);
        Fx.sound(loc, hitSound(), 0.8f, 1.2f);

        if (event.getHitEntity() instanceof LivingEntity target) {
            target.damage(damage, shooter);
            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ultimate(Player player) {
        World world = player.getWorld();
        Location spawnLoc = player.getLocation().add(player.getLocation().getDirection().multiply(2));

        Zombie guardian = world.spawn(spawnLoc, Zombie.class, mob -> {
            mob.setCanPickupItems(false);
            mob.setAdult();
            double baseHealth = mob.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
            mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(baseHealth * guardianHealthMultiplier * rarity().statMultiplier());
            mob.setHealth(mob.getAttribute(Attribute.MAX_HEALTH).getValue());
            double baseDamage = mob.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue();
            mob.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(baseDamage * guardianDamageMultiplier * rarity().statMultiplier());
            if (mob.getAttribute(Attribute.SCALE) != null) {
                mob.getAttribute(Attribute.SCALE).setBaseValue(guardianScale);
            }
            tagAsPlayerSummon(mob);
        });
        summonManager.add(player, guardian);
        despawnAfter(guardian, guardianDurationTicks);
        Fx.burst(spawnLoc.clone().add(0, 1, 0), Particle.SOUL, 90, 0.9);
        Fx.coloredBurst(spawnLoc.clone().add(0, 1, 0), Color.fromRGB(60, 90, 60), 2.6f, 55, 0.9);
        Fx.coloredBurst(spawnLoc.clone().add(0, 2, 0), Color.fromRGB(90, 130, 90), 2.0f, 35, 0.7);
        Fx.sound(player, Sound.ENTITY_ZOMBIE_AMBIENT, 1.5f, 0.5f);
        Fx.spinningIcon(plugin, spawnLoc.clone().add(0, 3.0, 0), Material.WITHER_SKELETON_SKULL, 1.5f, 60, 12);

        Location guardianRiseBase = spawnLoc.clone();
        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (ticks >= 40) {
                    cancel();
                    return;
                }
                angle += Math.PI / 6;
                double height = ticks * 0.25;
                Fx.helixFrame(guardianRiseBase, Particle.SOUL, 1.8, 16, angle, height);
                Fx.helixFrame(guardianRiseBase, Particle.SOUL, 1.3, 12, -angle * 1.3, height * 0.9);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}

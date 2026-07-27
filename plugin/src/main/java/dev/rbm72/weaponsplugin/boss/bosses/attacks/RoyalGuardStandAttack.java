package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AddManager;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Fallen King's signature: he kneels behind a real shield wall instead of a passive object.
 * Untouchable while any Royal Guard stands — these are armed, aggressive fighting adds that hit
 * back, not breakable scenery. Clear them all before the timer runs out and he's staggered wide
 * open; fail and he heals a burst and blasts the arena back. A genuine "clear the adds" fight, not
 * another totem ring.
 */
public final class RoyalGuardStandAttack extends BossAttack {

    private static final Color GOLD = Color.fromRGB(212, 175, 55);

    private final int telegraphTicks;
    private final int guardCount;
    private final int timeoutTicks;
    private final double guardHealth;
    private final double failHealBurst;
    private final double failKnockback;
    private final int exposedStaggerTicks;
    private final double exposedMultiplier;
    private final int exposedTicks;

    public RoyalGuardStandAttack(WeaponsPlugin plugin) {
        super(plugin, "fallen_king");
        this.telegraphTicks = configInt("royal-guard-stand-telegraph-ticks", 28);
        this.guardCount = configInt("royal-guard-stand-count", 4);
        this.timeoutTicks = configInt("royal-guard-stand-timeout-ticks", 200);
        this.guardHealth = configDouble("royal-guard-stand-health", 26.0);
        this.failHealBurst = configDouble("royal-guard-stand-fail-heal", 50.0);
        this.failKnockback = configDouble("royal-guard-stand-fail-knockback", 1.4);
        this.exposedStaggerTicks = configInt("royal-guard-stand-stagger-ticks", 60);
        this.exposedMultiplier = configDouble("royal-guard-stand-exposed-multiplier", 2.0);
        this.exposedTicks = configInt("royal-guard-stand-exposed-ticks", 100);
    }

    @Override
    public String name() {
        return "Royal Guard's Stand";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("royal-guard-stand-cooldown-seconds", 50.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(ctx.bossLocation(), GOLD, 1.4f, 3.0, 20, 0);
                    Fx.sound(ctx.bossLocation(), Sound.ITEM_SHIELD_BLOCK, 0.9f, 0.6f);
                },
                () -> {
                    ctx.instance().setForcedInvulnerable(true);
                    ctx.instance().showTitle(
                            Component.text("THE GUARD STANDS", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                            Component.text("Break the shield wall before it reforms him", NamedTextColor.GRAY));
                    BossAudio.play(ctx.bossLocation(), "boss.royal_guard_stand", Sound.ENTITY_VINDICATOR_AMBIENT, 1.1f, 0.7f);

                    AddManager adds = ctx.instance().addManager();
                    List<LivingEntity> guards = new ArrayList<>(guardCount);
                    for (int i = 0; i < guardCount; i++) {
                        double angle = 2 * Math.PI * i / guardCount;
                        Location spot = ctx.bossLocation().clone().add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
                        Fx.coloredBurst(spot.clone().add(0, 1, 0), GOLD, 1.4f, 20, 0.4);
                        LivingEntity guard = adds.spawn(spot.getWorld(), spot, EntityType.VINDICATOR, entity -> {
                            entity.customName(Component.text("Royal Guard", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                            entity.setCustomNameVisible(true);
                            var maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
                            if (maxHealthAttr != null) {
                                maxHealthAttr.setBaseValue(guardHealth);
                                entity.setHealth(guardHealth);
                            }
                            EntityEquipment equipment = entity.getEquipment();
                            if (equipment != null) {
                                equipment.setItemInMainHand(new ItemStack(Material.IRON_AXE));
                                equipment.setHelmet(new ItemStack(Material.IRON_HELMET));
                                equipment.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                            }
                            if (entity instanceof Mob mob) {
                                mob.setTarget(ctx.target());
                            }
                        });
                        guards.add(guard);
                    }

                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (!ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            boolean anyAlive = guards.stream().anyMatch(g -> g.isValid() && !g.isDead());
                            if (!anyAlive) {
                                succeed();
                                cancel();
                                return;
                            }
                            if (ticks >= timeoutTicks) {
                                fail(guards);
                                cancel();
                                return;
                            }
                            ticks++;
                        }

                        private void succeed() {
                            ctx.instance().setForcedInvulnerable(false);
                            ctx.instance().recordExposure();
                            ctx.instance().setDamageMultiplier(exposedMultiplier);
                            ctx.instance().stagger(exposedStaggerTicks);
                            ctx.instance().entity().setGlowing(true);
                            Location loc = ctx.bossLocation();
                            Fx.coloredBurst(loc.clone().add(0, 1.2, 0), GOLD, 2.2f, 50, 0.8);
                            Fx.flash(loc.clone().add(0, 1.2, 0), 2);
                            Fx.sound(loc, Sound.ITEM_SHIELD_BREAK, 1.2f, 1.2f);
                            ctx.instance().showTitle(
                                    Component.text("SHIELD BROKEN", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                                    Component.text("The king stands undefended", NamedTextColor.GRAY));
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (ctx.boss().isValid()) {
                                        ctx.instance().entity().setGlowing(false);
                                        ctx.instance().setDamageMultiplier(1.0);
                                    }
                                }
                            }.runTaskLater(plugin, exposedTicks);
                        }

                        private void fail(List<LivingEntity> guards) {
                            ctx.instance().setForcedInvulnerable(false);
                            for (LivingEntity guard : guards) {
                                if (guard.isValid()) {
                                    guard.remove();
                                }
                            }
                            Location loc = ctx.bossLocation();
                            var maxHealthAttr = ctx.boss().getAttribute(Attribute.MAX_HEALTH);
                            double cap = maxHealthAttr != null ? maxHealthAttr.getValue() : ctx.boss().getHealth();
                            ctx.boss().setHealth(Math.min(cap, ctx.boss().getHealth() + failHealBurst));
                            Fx.coloredBurst(loc.clone().add(0, 1, 0), GOLD, 2.0f, 45, 0.7);
                            Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.9f);
                            for (Player player : Arena.combatants(loc, ctx.arena().radius())) {
                                if (player.getLocation().distanceSquared(loc) <= 64.0) {
                                    Vector push = player.getLocation().toVector().subtract(loc.toVector());
                                    push = push.lengthSquared() > 1.0E-6 ? push.normalize() : new Vector(1, 0, 0);
                                    player.setVelocity(player.getVelocity().add(push.multiply(failKnockback).setY(0.3)));
                                }
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                timeoutTicks + 20, onComplete);
    }
}

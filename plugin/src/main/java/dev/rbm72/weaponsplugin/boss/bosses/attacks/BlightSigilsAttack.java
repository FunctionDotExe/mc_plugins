package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.props.ArenaTotem;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plants blight sigils around the arena that pulse poison at anyone nearby every second they're
 * left standing, and detonate a bigger toxic burst if any survive their full lifetime. Break one
 * and it just fizzles out — no detonation, no more pulses from that spot.
 */
public final class BlightSigilsAttack extends BossAttack {

    private static final Color TOXIC = Color.fromRGB(80, 140, 40);

    private final int sigilCount;
    private final double sigilHealth;
    private final int lifetimeTicks;
    private final double pulseDamage;
    private final double pulseRadius;
    private final int poisonTicks;
    private final double detonationDamage;
    private final double detonationRadius;
    private final int telegraphTicks;

    public BlightSigilsAttack(WeaponsPlugin plugin) {
        super(plugin, "plague_warden");
        this.sigilCount = configInt("blight-sigils-count", 3);
        this.sigilHealth = configDouble("blight-sigils-health", 24.0);
        this.lifetimeTicks = configInt("blight-sigils-lifetime-ticks", 140);
        this.pulseDamage = configDouble("blight-sigils-pulse-damage", 2.0);
        this.pulseRadius = configDouble("blight-sigils-pulse-radius", 3.5);
        this.poisonTicks = configInt("blight-sigils-poison-ticks", 60);
        this.detonationDamage = configDouble("blight-sigils-detonation-damage", 10.0);
        this.detonationRadius = configDouble("blight-sigils-detonation-radius", 4.5);
        this.telegraphTicks = configInt("blight-sigils-telegraph-ticks", 22);
    }

    @Override
    public String name() {
        return "Blight Sigils";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("blight-sigils-cooldown-seconds", 26.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.arena().center();
        double radius = ctx.arena().radius();

        sequence(telegraphTicks,
                () -> {
                    Telegraph.groundRing(center, radius * 0.6, Particle.SPORE_BLOSSOM_AIR);
                    Fx.coloredRing(ctx.bossLocation(), TOXIC, 1.6f, 3.0, 24, 0);
                },
                () -> {
                    BossAudio.play(center, "boss.plague_warden.blight_sigils", Sound.BLOCK_SCULK_SPREAD, 1.2f, 0.5f);
                    Fx.sound(center, Sound.ENTITY_WITCH_AMBIENT, 1.0f, 0.4f);

                    List<ArenaTotem> sigils = new ArrayList<>(sigilCount);
                    for (int i = 0; i < sigilCount; i++) {
                        double ox = ThreadLocalRandom.current().nextDouble(-radius * 0.6, radius * 0.6);
                        double oz = ThreadLocalRandom.current().nextDouble(-radius * 0.6, radius * 0.6);
                        Location spot = center.clone().add(ox, 0, oz);
                        Fx.coloredBurst(spot.clone().add(0, 1, 0), TOXIC, 1.8f, 30, 0.5);
                        Fx.burst(spot.clone().add(0, 1, 0), Particle.ITEM_SLIME, 20, 0.4);
                        ArenaTotem sigil = ArenaTotem.spawn(plugin, ctx.instance(), spot, Material.FERMENTED_SPIDER_EYE,
                                Component.text("Blight Sigil", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                                sigilHealth, lifetimeTicks,
                                destroyed -> Fx.sound(destroyed.location(), Sound.ENTITY_WITCH_CELEBRATE, 0.8f, 1.3f),
                                this::detonate);
                        sigils.add(sigil);
                    }

                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= lifetimeTicks || sigils.stream().noneMatch(ArenaTotem::isValid)) {
                                cancel();
                                return;
                            }
                            if (ticks % 20 == 0) {
                                for (ArenaTotem sigil : sigils) {
                                    if (!sigil.isValid()) {
                                        continue;
                                    }
                                    Fx.coloredBurst(sigil.location().add(0, 0.8, 0), TOXIC, 1.2f, 12, 0.3);
                                    for (Player player : ctx.arena().playersInside()) {
                                        if (player.getLocation().distanceSquared(sigil.location()) <= pulseRadius * pulseRadius) {
                                            tickHurt(ctx, player, pulseDamage);
                                            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonTicks, 0));
                                        }
                                    }
                                }
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                10, onComplete);
    }

    private void detonate(ArenaTotem sigil) {
        Location loc = sigil.location().add(0, 1, 0);
        Fx.coloredBurst(loc, TOXIC, 2.4f, 60, 0.9);
        Fx.burst(loc, Particle.ITEM_SLIME, 40, 0.8);
        Fx.sound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.6f);
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= detonationRadius * detonationRadius) {
                player.damage(detonationDamage);
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonTicks * 2, 1));
            }
        }
    }
}

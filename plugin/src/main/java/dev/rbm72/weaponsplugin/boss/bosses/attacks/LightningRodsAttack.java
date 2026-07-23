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
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Real objective mechanic for the storm's mid-game: the tyrant drives three lightning rods into
 * the ground and starts drawing the whole sky's charge down through them. Left standing the full
 * channel, every rod still up discharges into a single arena-wide megabolt at the end — a hit
 * worth actively preventing, not just tanking. Break a rod first and it backfires: the charge
 * arcs into the tyrant instead, hurting him and grounding him (stunned, briefly). Replaces Static
 * Field, which was a plain damage-radius pulse identical in shape to Gale Push and added nothing
 * of its own.
 */
public final class LightningRodsAttack extends BossAttack {

    private static final Color STORM_YELLOW = Color.fromRGB(255, 240, 120);

    private final int rodCount;
    private final double rodHealth;
    private final int channelDurationTicks;
    private final double megaboltDamage;
    private final double backfireDamage;
    private final int stunDurationTicks;
    private final int telegraphTicks;

    public LightningRodsAttack(WeaponsPlugin plugin) {
        super(plugin, "storm_tyrant");
        this.rodCount = configInt("lightning-rods-count", 3);
        this.rodHealth = configDouble("lightning-rods-health", 24.0);
        this.channelDurationTicks = configInt("lightning-rods-channel-ticks", 120);
        this.megaboltDamage = configDouble("lightning-rods-megabolt-damage", 14.0);
        this.backfireDamage = configDouble("lightning-rods-backfire-damage", 10.0);
        this.stunDurationTicks = configInt("lightning-rods-stun-duration-ticks", 50);
        this.telegraphTicks = configInt("lightning-rods-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Lightning Rods";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("lightning-rods-cooldown-seconds", 26.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.arena().center();
        double radius = ctx.arena().radius();

        sequence(telegraphTicks,
                () -> {
                    Telegraph.groundRing(center, radius * 0.6, Particle.ELECTRIC_SPARK);
                    Fx.coloredRing(ctx.bossLocation(), STORM_YELLOW, 1.6f, 3.0, 24, 0);
                },
                () -> {
                    ctx.instance().showTitle(
                            Component.text("Lightning Rods", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                            Component.text("Break one before the sky discharges", NamedTextColor.GRAY));
                    BossAudio.play(center, "boss.storm_tyrant.lightning_rods", Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.6f);

                    List<ArenaTotem> rods = new ArrayList<>(rodCount);
                    for (int i = 0; i < rodCount; i++) {
                        double angle = 2 * Math.PI * i / rodCount;
                        Location spot = center.clone().add(Math.cos(angle) * radius * 0.55, 0, Math.sin(angle) * radius * 0.55);
                        spot.setY(center.getY());
                        Fx.coloredBurst(spot.clone().add(0, 1, 0), STORM_YELLOW, 1.6f, 26, 0.5);
                        Fx.burst(spot.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 20, 0.4);
                        ArenaTotem rod = ArenaTotem.spawn(plugin, ctx.instance(), spot, Material.LIGHTNING_ROD,
                                Component.text("Lightning Rod", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                                rodHealth, channelDurationTicks,
                                destroyed -> onRodBroken(ctx, destroyed.location()),
                                expired -> { });
                        rods.add(rod);
                    }

                    new BukkitRunnable() {
                        int ticks = 0;
                        boolean interrupted = false;

                        @Override
                        public void run() {
                            long aliveCount = rods.stream().filter(ArenaTotem::isValid).count();
                            if (aliveCount == 0) {
                                interrupted = true;
                            }
                            if (ticks >= channelDurationTicks || !ctx.boss().isValid() || interrupted) {
                                if (!interrupted && aliveCount > 0) {
                                    megabolt(ctx, center, radius);
                                }
                                cancel();
                                return;
                            }
                            // Visible charge building at each surviving rod — an escalating "it's about to go off" tell.
                            if (ticks % 10 == 0) {
                                for (ArenaTotem rod : rods) {
                                    if (rod.isValid()) {
                                        Fx.point(rod.location().add(0, 1.5, 0), Particle.ELECTRIC_SPARK, 6);
                                    }
                                }
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                10, onComplete);
    }

    /** A rod broke: the charge backfires into the tyrant himself instead of the arena. */
    private void onRodBroken(AttackContext ctx, Location rodLocation) {
        if (!ctx.boss().isValid()) {
            return;
        }
        Fx.line(rodLocation.clone().add(0, 1, 0), ctx.bossLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 24);
        ctx.boss().damage(backfireDamage);
        ctx.boss().addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, stunDurationTicks, 3));
        Fx.coloredBurst(ctx.bossLocation().add(0, 1.2, 0), Color.fromRGB(230, 230, 255), 1.8f, 36, 0.6);
        Fx.sound(ctx.bossLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.3f, 1.2f);
        ctx.instance().showTitle(
                Component.text("Backfire!", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("The tyrant reels from his own storm", NamedTextColor.GRAY));
    }

    /** Every rod survived: one heavy arena-wide bolt as the punishment for ignoring the objective. */
    private void megabolt(AttackContext ctx, Location center, double radius) {
        Fx.coloredBurst(ctx.bossLocation().add(0, 1.5, 0), STORM_YELLOW, 2.4f, 80, 1.0);
        Fx.sound(ctx.bossLocation(), Sound.ENTITY_WITHER_SPAWN, 1.3f, 0.5f);
        center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0, 3, 0), 400, radius, 4.0, radius, 0.05);
        if (center.getWorld() != null) {
            center.getWorld().strikeLightningEffect(center);
        }
        for (Player player : ctx.arena().playersInside()) {
            player.damage(megaboltDamage, ctx.boss());
            Fx.bloodSpray(player.getLocation().add(0, 1, 0));
            Fx.flash(player.getLocation().add(0, 1, 0), 2);
            Fx.sound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
            if (player.getWorld() != null) {
                player.getWorld().strikeLightningEffect(player.getLocation());
            }
        }
    }
}

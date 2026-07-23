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
import org.bukkit.attribute.Attribute;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Plants soul anchors around the arena that channel life force back to the Overlord every second
 * they're left standing. Break them to cut the drain off; leave any alive for the full channel and
 * he gets a burst heal when it completes. A destructible arena hazard, not another summon — nothing
 * to kill it, only to break.
 */
public final class SoulAnchorsAttack extends BossAttack {

    private static final Color NECROTIC = Color.fromRGB(120, 200, 110);

    private final int anchorCount;
    private final double anchorHealth;
    private final int channelDurationTicks;
    private final double healPerPulse;
    private final double survivalHealBonus;
    private final int telegraphTicks;

    public SoulAnchorsAttack(WeaponsPlugin plugin) {
        super(plugin, "necro_overlord");
        this.anchorCount = configInt("soul-anchors-count", 3);
        this.anchorHealth = configDouble("soul-anchors-health", 30.0);
        this.channelDurationTicks = configInt("soul-anchors-channel-ticks", 160);
        this.healPerPulse = configDouble("soul-anchors-heal-per-pulse", 6.0);
        this.survivalHealBonus = configDouble("soul-anchors-survival-heal-bonus", 60.0);
        this.telegraphTicks = configInt("soul-anchors-telegraph-ticks", 24);
    }

    @Override
    public String name() {
        return "Soul Anchors";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("soul-anchors-cooldown-seconds", 30.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.arena().center();
        double radius = ctx.arena().radius();

        sequence(telegraphTicks,
                () -> {
                    Telegraph.groundRing(center, radius * 0.6, Particle.SOUL);
                    Fx.coloredRing(ctx.bossLocation(), NECROTIC, 1.6f, 3.0, 24, 0);
                },
                () -> {
                    BossAudio.play(center, "boss.necro_overlord.soul_anchors", Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.5f);
                    Fx.sound(center, Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.4f);

                    List<ArenaTotem> anchors = new ArrayList<>(anchorCount);
                    for (int i = 0; i < anchorCount; i++) {
                        double angle = 2 * Math.PI * i / anchorCount;
                        Location spot = center.clone().add(Math.cos(angle) * radius * 0.55, 0, Math.sin(angle) * radius * 0.55);
                        spot.setY(center.getY());
                        Fx.coloredBurst(spot.clone().add(0, 1, 0), NECROTIC, 1.8f, 30, 0.5);
                        Fx.burst(spot.clone().add(0, 1, 0), Particle.SOUL, 20, 0.4);
                        ArenaTotem anchor = ArenaTotem.spawn(plugin, ctx.instance(), spot, Material.WITHER_SKELETON_SKULL,
                                Component.text("Soul Anchor", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false),
                                anchorHealth, channelDurationTicks,
                                destroyed -> Fx.sound(destroyed.location(), Sound.ENTITY_WITHER_SKELETON_DEATH, 0.9f, 1.2f),
                                expired -> { });
                        anchors.add(anchor);
                    }

                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= channelDurationTicks || !ctx.boss().isValid()) {
                                boolean anySurvived = anchors.stream().anyMatch(ArenaTotem::isValid);
                                if (anySurvived) {
                                    var maxHealthAttr = ctx.boss().getAttribute(Attribute.MAX_HEALTH);
                                    double cap = maxHealthAttr != null ? maxHealthAttr.getValue() : ctx.boss().getHealth();
                                    ctx.boss().setHealth(Math.min(cap, ctx.boss().getHealth() + survivalHealBonus));
                                    Fx.coloredBurst(ctx.bossLocation().add(0, 1.2, 0), NECROTIC, 2.4f, 60, 0.8);
                                    Fx.sound(ctx.bossLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.3f, 0.6f);
                                }
                                cancel();
                                return;
                            }
                            if (ticks % 20 == 0) {
                                long aliveCount = anchors.stream().filter(ArenaTotem::isValid).count();
                                if (aliveCount > 0 && ctx.boss().isValid()) {
                                    var maxHealthAttr = ctx.boss().getAttribute(Attribute.MAX_HEALTH);
                                    double cap = maxHealthAttr != null ? maxHealthAttr.getValue() : ctx.boss().getHealth();
                                    ctx.boss().setHealth(Math.min(cap, ctx.boss().getHealth() + healPerPulse * aliveCount));
                                    Fx.coloredBurst(ctx.bossLocation().add(0, 1, 0), NECROTIC, 1.4f, 14, 0.4);
                                    for (ArenaTotem anchor : anchors) {
                                        if (anchor.isValid()) {
                                            Fx.trail(anchor.location().add(0, 1.5, 0), Particle.SOUL, 4, 0.1, 0.02);
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
}

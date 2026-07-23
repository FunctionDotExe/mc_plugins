package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Dragon Elder's signature: it doesn't hide behind wards, it takes the sky. It beats up out of
 * reach and turns untouchable aloft — melee and arrows both glance off — then folds into a telegraphed
 * dive at one player, and for the brief beat it's grounded from that dive it's wide open. That swoop
 * window is the only time it can be hurt; land enough across the run and it's driven down, staggered
 * wide open. Miss the windows chasing it while it's aloft and it just wheels back up. A punish-the-
 * landing check, not another totem ring.
 */
public final class SkyboundAssaultAttack extends BossAttack {

    private static final Color WYRM_GOLD = Color.fromRGB(230, 190, 60);
    private static final Color SKY_CYAN = Color.fromRGB(140, 220, 255);

    private final int telegraphTicks;
    private final int swoopCount;
    private final int aerialTicks;
    private final int groundWindowTicks;
    private final double altitude;
    private final double diveDamage;
    private final double diveRadius;
    private final double requiredDamage;
    private final double groundedMultiplier;
    private final int exposedStaggerTicks;
    private final double exposedMultiplier;
    private final int exposedTicks;

    public SkyboundAssaultAttack(WeaponsPlugin plugin) {
        super(plugin, "dragon_elder");
        this.telegraphTicks = configInt("skybound-assault-telegraph-ticks", 30);
        this.swoopCount = configInt("skybound-assault-swoop-count", 3);
        this.aerialTicks = configInt("skybound-assault-aerial-ticks", 40);
        this.groundWindowTicks = configInt("skybound-assault-ground-window-ticks", 45);
        this.altitude = configDouble("skybound-assault-altitude", 9.0);
        this.diveDamage = configDouble("skybound-assault-dive-damage", 12.0);
        this.diveRadius = configDouble("skybound-assault-dive-radius", 3.5);
        this.requiredDamage = configDouble("skybound-assault-required-damage", 120.0);
        this.groundedMultiplier = configDouble("skybound-assault-grounded-multiplier", 2.2);
        this.exposedStaggerTicks = configInt("skybound-assault-stagger-ticks", 70);
        this.exposedMultiplier = configDouble("skybound-assault-exposed-multiplier", 2.0);
        this.exposedTicks = configInt("skybound-assault-exposed-ticks", 110);
    }

    @Override
    public String name() {
        return "Skybound Assault";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("skybound-assault-cooldown-seconds", 50.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        // Total runtime the recovery timer must outlast: every swoop's aloft telegraph + grounded window.
        int totalTicks = swoopCount * (aerialTicks + groundWindowTicks) + 20;
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(ctx.bossLocation(), WYRM_GOLD, 1.4f, 3.0, 20, 0);
                    Fx.sound(ctx.bossLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.9f, 0.7f);
                },
                () -> {
                    double radius = ctx.arena().radius();
                    double startHealth = ctx.boss().getHealth();
                    Location anchor = ctx.bossLocation().clone();

                    ctx.instance().showTitle(
                            Component.text("SKYBOUND ASSAULT", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                            Component.text("It only bleeds where it lands — punish the dives", NamedTextColor.GRAY));
                    BossAudio.play(anchor, "boss.skybound_assault", Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.7f);

                    new BukkitRunnable() {
                        int swoop = 0;
                        int phaseTick = 0;
                        boolean aloft = true;
                        boolean started = false;

                        @Override
                        public void run() {
                            if (!ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            if (!started) {
                                started = true;
                                ascend();
                            }
                            if (aloft) {
                                Location up = ctx.boss().getLocation();
                                Fx.coloredBurst(up.clone().add(0, 0.5, 0), SKY_CYAN, 1.4f, 8, 0.5);
                                if (phaseTick >= aerialTicks) {
                                    dive();
                                    return;
                                }
                            } else {
                                // Grounded window: it's exposed, glowing, taking bonus damage — this
                                // is the whole point, the players' only opening this cast.
                                Location g = ctx.boss().getLocation();
                                Fx.coloredBurst(g.clone().add(0, 1.2, 0), WYRM_GOLD, 1.4f, 10, 0.5);
                                if (phaseTick >= groundWindowTicks) {
                                    swoop++;
                                    if (swoop >= swoopCount) {
                                        resolve(startHealth);
                                        cancel();
                                        return;
                                    }
                                    ascend();
                                    return;
                                }
                            }
                            phaseTick++;
                        }

                        private void ascend() {
                            aloft = true;
                            phaseTick = 0;
                            ctx.instance().setForcedInvulnerable(true);
                            ctx.instance().entity().setGlowing(false);
                            Location up = anchor.clone().add(0, altitude, 0);
                            ctx.boss().teleport(up);
                            Fx.coloredRing(up, SKY_CYAN, 1.4f, 3.0, 20, 0);
                            Fx.sound(up, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.1f, 0.8f);
                        }

                        private void dive() {
                            List<Player> nearby = Arena.playersNear(anchor, radius);
                            Location landing = nearby.isEmpty()
                                    ? anchor.clone()
                                    : nearby.get(ThreadLocalRandom.current().nextInt(nearby.size())).getLocation().clone();
                            Fx.line(ctx.boss().getLocation().add(0, 0.5, 0), landing.clone().add(0, 1, 0),
                                    Particle.FLAME, 20);
                            ctx.boss().teleport(landing);

                            aloft = false;
                            phaseTick = 0;
                            ctx.instance().setForcedInvulnerable(false);
                            ctx.instance().setDamageMultiplier(groundedMultiplier);
                            ctx.instance().entity().setGlowing(true);

                            double rSq = diveRadius * diveRadius;
                            for (Player player : Arena.playersNear(landing, diveRadius + 1)) {
                                if (player.getLocation().distanceSquared(landing) <= rSq) {
                                    player.damage(diveDamage, ctx.boss());
                                    Fx.coloredBurst(player.getLocation().add(0, 1, 0), WYRM_GOLD, 1.4f, 20, 0.4);
                                }
                            }
                            Fx.coloredBurst(landing.clone().add(0, 0.5, 0), WYRM_GOLD, 2.0f, 40, 0.6);
                            Fx.sound(landing, Sound.ENTITY_ENDER_DRAGON_HURT, 1.0f, 0.7f);
                            ctx.instance().showTitle(
                                    Component.text("GROUNDED", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                                    Component.text("Now — hit it before it wheels back up", NamedTextColor.GRAY));
                        }

                        private void resolve(double startHp) {
                            ctx.instance().setForcedInvulnerable(false);
                            ctx.instance().entity().setGlowing(false);
                            ctx.instance().setDamageMultiplier(1.0);
                            ctx.boss().teleport(anchor);
                            double dealt = startHp - ctx.boss().getHealth();

                            if (dealt >= requiredDamage) {
                                Location loc = ctx.bossLocation();
                                ctx.instance().recordExposure();
                                ctx.instance().setDamageMultiplier(exposedMultiplier);
                                ctx.instance().stagger(exposedStaggerTicks);
                                ctx.instance().entity().setGlowing(true);
                                Fx.coloredBurst(loc.clone().add(0, 1.2, 0), WYRM_GOLD, 2.4f, 60, 0.9);
                                Fx.flash(loc.clone().add(0, 1.2, 0), 3);
                                Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.8f, 1.2f);
                                ctx.instance().showTitle(
                                        Component.text("DRIVEN DOWN", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                                        Component.text("Its wings fail — it can't climb again", NamedTextColor.GRAY));
                                ctx.plugin().getServer().getScheduler().runTaskLater(ctx.plugin(), () -> {
                                    if (ctx.boss().isValid()) {
                                        ctx.instance().entity().setGlowing(false);
                                        ctx.instance().setDamageMultiplier(1.0);
                                    }
                                }, exposedTicks);
                            } else {
                                Location loc = ctx.bossLocation();
                                Fx.coloredBurst(loc.clone().add(0, 1.5, 0), SKY_CYAN, 2.0f, 40, 0.7);
                                Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 0.6f);
                                ctx.instance().showTitle(
                                        Component.text("IT WHEELS AWAY", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, true),
                                        Component.text("Not enough — it keeps the sky", NamedTextColor.DARK_GRAY));
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                totalTicks, onComplete);
    }
}

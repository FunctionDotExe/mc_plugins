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
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A forced-positioning trial: the boss goes briefly untouchable and drops a handful of ward zones
 * around the arena. Anyone not standing inside one when the window closes takes a real punishing
 * hit — melee range on the boss means nothing here, the only thing that matters is where your feet
 * are. Reusable across bosses via the {@code bossId} constructor param.
 */
public final class SanctuaryTrialAttack extends BossAttack {

    private final int telegraphTicks;
    private final int durationTicks;
    private final int wardCount;
    private final double wardRadius;
    private final double punishDamage;

    public SanctuaryTrialAttack(WeaponsPlugin plugin, String bossId) {
        super(plugin, bossId);
        this.telegraphTicks = configInt("sanctuary-trial-telegraph-ticks", 30);
        this.durationTicks = configInt("sanctuary-trial-duration-ticks", 80);
        this.wardCount = configInt("sanctuary-trial-ward-count", 2);
        this.wardRadius = configDouble("sanctuary-trial-ward-radius", 3.0);
        this.punishDamage = configDouble("sanctuary-trial-punish-damage", 16.0);
    }

    @Override
    public String name() {
        return "Sanctuary Trial";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("sanctuary-trial-cooldown-seconds", 45.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        // Anchored to the boss's current position, not the arena's fixed spawn-time center — see
        // Vulnerability.spawnSet() for why a stale spawn-point anchor makes a mechanic disappear
        // the moment a chased/knocked-back fight drifts away from where it started.
        Location center = ctx.bossLocation();
        double radius = ctx.arena().radius();
        List<Location> wards = new ArrayList<>(wardCount);
        for (int i = 0; i < wardCount; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);
            double dist = ThreadLocalRandom.current().nextDouble(radius * 0.3, radius * 0.75);
            Location spot = center.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            spot.setY(center.getY());
            wards.add(spot);
        }

        sequence(telegraphTicks,
                () -> {
                    for (Location ward : wards) {
                        Fx.coloredRing(ward, Color.fromRGB(80, 220, 255), 1.2f, wardRadius, 16, 0);
                    }
                    Fx.sound(ctx.bossLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.6f);
                },
                () -> {
                    ctx.instance().setForcedInvulnerable(true);
                    ctx.instance().showTitle(
                            Component.text("SEEK SHELTER", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                            Component.text("Stand within a ward before it breaks loose", NamedTextColor.GRAY));
                    BossAudio.play(ctx.bossLocation(), "boss.sanctuary_trial", Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.8f);

                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= durationTicks || !ctx.boss().isValid()) {
                                resolve();
                                cancel();
                                return;
                            }
                            if (ticks % 5 == 0) {
                                for (Location ward : wards) {
                                    Fx.coloredBurst(ward.clone().add(0, 1, 0), Color.fromRGB(80, 220, 255), 1.0f, 8, 0.2);
                                }
                            }
                            ticks++;
                        }

                        private void resolve() {
                            for (Player player : Arena.playersNear(center, radius)) {
                                boolean sheltered = wards.stream()
                                        .anyMatch(w -> w.distanceSquared(player.getLocation()) <= wardRadius * wardRadius);
                                if (!sheltered) {
                                    player.damage(punishDamage, ctx.boss());
                                    Fx.coloredBurst(player.getLocation().add(0, 1, 0), Color.fromRGB(255, 60, 0), 1.8f, 35, 0.6);
                                    Fx.sound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.1f);
                                }
                            }
                            for (Location ward : wards) {
                                Fx.coloredBurst(ward.clone().add(0, 1, 0), Color.fromRGB(80, 220, 255), 1.6f, 25, 0.4);
                            }
                            ctx.instance().setForcedInvulnerable(false);
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                durationTicks + 10, onComplete);
    }
}

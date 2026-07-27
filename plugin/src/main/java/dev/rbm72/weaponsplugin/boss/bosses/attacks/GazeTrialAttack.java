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
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A forced-action trial, not another dodge check: the boss goes briefly untouchable and demands
 * everyone tilt their camera up or down (picked at random each cast) for the whole window. Standing
 * there DPSing does nothing — the boss can't be hurt right now — and anyone who spends most of the
 * window not complying takes a real punishing hit the instant it ends. Reusable across bosses via
 * the {@code bossId} constructor param, same as the rest of the attack roster.
 */
public final class GazeTrialAttack extends BossAttack {

    private final int telegraphTicks;
    private final int durationTicks;
    private final double punishDamage;
    private final double pitchThreshold;
    private final double noncomplianceFractionToPunish;

    public GazeTrialAttack(WeaponsPlugin plugin, String bossId) {
        super(plugin, bossId);
        this.telegraphTicks = configInt("gaze-trial-telegraph-ticks", 30);
        this.durationTicks = configInt("gaze-trial-duration-ticks", 100);
        this.punishDamage = configDouble("gaze-trial-punish-damage", 14.0);
        this.pitchThreshold = configDouble("gaze-trial-pitch-threshold", 35.0);
        this.noncomplianceFractionToPunish = configDouble("gaze-trial-noncompliance-fraction", 0.5);
    }

    @Override
    public String name() {
        return "Gaze Trial";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("gaze-trial-cooldown-seconds", 40.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        boolean lookUp = ThreadLocalRandom.current().nextBoolean();
        Component instruction = lookUp
                ? Component.text("LOOK UP", NamedTextColor.RED).decoration(TextDecoration.BOLD, true)
                : Component.text("LOOK DOWN", NamedTextColor.RED).decoration(TextDecoration.BOLD, true);

        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(ctx.bossLocation(), Color.fromRGB(200, 20, 20), 1.4f, 3.0, 20, 0);
                    Fx.sound(ctx.bossLocation(), Sound.ENTITY_WARDEN_LISTENING_ANGRY, 0.8f, 0.6f);
                },
                () -> {
                    ctx.instance().setForcedInvulnerable(true);
                    ctx.instance().showTitle(instruction,
                            Component.text("Averting your eyes is the only shelter", NamedTextColor.GRAY));
                    BossAudio.play(ctx.bossLocation(), "boss.gaze_trial", Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.4f);

                    Map<UUID, Integer> noncompliantTicks = new HashMap<>();
                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= durationTicks || !ctx.boss().isValid()) {
                                resolve();
                                cancel();
                                return;
                            }
                            for (Player player : Arena.combatants(ctx.bossLocation(), ctx.arena().radius())) {
                                float pitch = player.getLocation().getPitch();
                                boolean compliant = lookUp ? pitch <= -pitchThreshold : pitch >= pitchThreshold;
                                if (!compliant) {
                                    noncompliantTicks.merge(player.getUniqueId(), 1, Integer::sum);
                                }
                            }
                            if (ticks % 10 == 0) {
                                Fx.coloredBurst(ctx.bossLocation().add(0, 2, 0), Color.fromRGB(200, 20, 20), 1.0f, 6, 0.3);
                            }
                            ticks++;
                        }

                        private void resolve() {
                            for (Player player : Arena.combatants(ctx.bossLocation(), ctx.arena().radius())) {
                                int bad = noncompliantTicks.getOrDefault(player.getUniqueId(), 0);
                                if (bad >= durationTicks * noncomplianceFractionToPunish) {
                                    player.damage(punishDamage, ctx.boss());
                                    Fx.coloredBurst(player.getLocation().add(0, 1, 0), Color.fromRGB(255, 0, 0), 1.6f, 30, 0.5);
                                    Fx.sound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.7f);
                                }
                            }
                            ctx.instance().setForcedInvulnerable(false);
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                durationTicks + 10, onComplete);
    }
}

package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.mechanics.SoloEscape;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Frost Queen's signature: she doesn't hide behind wards, she takes a player hostage. She
 * encases one random player solid — they can't act at all — and it's on their teammates to stand
 * beside them and channel body heat back into them before the cold finishes the job. Free them in time
 * and she's left cracked open; fail and the frozen player takes the full brunt. A rescue-or-suffer check,
 * not another totem ring.
 * <p>
 * Solo, the frozen player struggles out themselves — hold sneak, at twice the channel length (see
 * {@link SoloEscape}). This previously had no solo path at all: a lone player could do nothing but watch
 * the timer run out, which made every cast an unavoidable 20-damage hit rather than a mechanic. That is
 * the failure mode batch-1 rule 0.2 #7 names explicitly — a mechanic disabled for solo play is still a
 * two-player requirement, it just hides the wall behind a countdown.
 */
public final class FrozenHeartAttack extends BossAttack {

    private static final Color FROST_BLUE = Color.fromRGB(150, 220, 255);

    /** How much longer struggling out alone takes than being freed by an ally — see {@link SoloEscape}. */
    private static final double SOLO_CHANNEL_MULTIPLIER = 2.0;

    private final int telegraphTicks;
    private final int durationTicks;
    private final int channelTicksNeeded;
    private final double failDamage;
    private final int exposedStaggerTicks;
    private final double exposedMultiplier;
    private final int exposedTicks;

    public FrozenHeartAttack(WeaponsPlugin plugin) {
        super(plugin, "frost_queen");
        this.telegraphTicks = configInt("frozen-heart-telegraph-ticks", 24);
        this.durationTicks = configInt("frozen-heart-duration-ticks", 100);
        this.channelTicksNeeded = configInt("frozen-heart-channel-ticks-needed", 60);
        this.failDamage = configDouble("frozen-heart-fail-damage", 20.0);
        this.exposedStaggerTicks = configInt("frozen-heart-stagger-ticks", 60);
        this.exposedMultiplier = configDouble("frozen-heart-exposed-multiplier", 2.0);
        this.exposedTicks = configInt("frozen-heart-exposed-ticks", 100);
    }

    @Override
    public String name() {
        return "Frozen Heart";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("frozen-heart-cooldown-seconds", 46.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        List<Player> candidates = Arena.combatants(ctx.bossLocation(), ctx.arena().radius());
        if (candidates.isEmpty()) {
            sequence(telegraphTicks, () -> { }, () -> { }, 5, onComplete);
            return;
        }
        Player frozen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));

        sequence(telegraphTicks,
                () -> Fx.coloredBurst(frozen.getLocation().add(0, 2, 0), FROST_BLUE, 1.2f, 10, 0.3),
                () -> {
                    if (!frozen.isOnline() || !frozen.isValid()) {
                        return;
                    }
                    ctx.instance().setForcedInvulnerable(true);
                    Location base = frozen.getLocation().clone();
                    Grief.raiseColumns(ctx, base, Material.PACKED_ICE, 2, 6, 1.3, durationTicks + 20);
                    frozen.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks + 20, 6));
                    frozen.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, durationTicks + 20, 6));
                    frozen.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, durationTicks + 20, 128));
                    ctx.plugin().actionBarHub().flash(frozen,
                            Component.text("You're frozen solid — your allies must stand with you!", NamedTextColor.AQUA),
                            3000, ActionBarHub.PRIORITY_NOTICE);
                    BossAudio.play(base, "boss.frozen_heart", Sound.BLOCK_GLASS_PLACE, 1.0f, 0.5f);
                    Fx.coloredBurst(base.clone().add(0, 1.2, 0), FROST_BLUE, 2.0f, 50, 0.7);

                    ctx.instance().showTitle(
                            Component.text("FROZEN HEART", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                            Component.text("Stand beside the frozen ally to free them", NamedTextColor.GRAY));

                    new BukkitRunnable() {
                        int ticks = 0;
                        int channelTicks = 0;

                        @Override
                        public void run() {
                            if (!ctx.boss().isValid() || !frozen.isOnline()) {
                                resolve(false);
                                cancel();
                                return;
                            }
                            boolean alone = SoloEscape.alone(ctx.instance(), frozen);
                            boolean rescuerPresent = alone
                                    ? SoloEscape.struggling(frozen)
                                    : Arena.combatants(frozen.getLocation(), 3.0).stream()
                                            .anyMatch(p -> !p.equals(frozen));
                            if (rescuerPresent) {
                                channelTicks++;
                                if (ticks % 5 == 0) {
                                    Fx.coloredBurst(frozen.getLocation().add(0, 1, 0), Color.fromRGB(255, 200, 120), 0.8f, 8, 0.2);
                                }
                            } else if (alone && ticks % 20 == 0) {
                                plugin.actionBarHub().flash(frozen, SoloEscape.prompt(), 1200,
                                        ActionBarHub.PRIORITY_NOTICE);
                            }
                            // Recomputed each tick, so someone arriving mid-freeze drops the requirement
                            // straight back to the group figure.
                            int needed = alone
                                    ? (int) Math.ceil(channelTicksNeeded * SOLO_CHANNEL_MULTIPLIER)
                                    : channelTicksNeeded;
                            if (channelTicks >= needed) {
                                resolve(true);
                                cancel();
                                return;
                            }
                            if (ticks >= durationTicks) {
                                resolve(false);
                                cancel();
                                return;
                            }
                            ticks++;
                        }

                        private void resolve(boolean rescued) {
                            ctx.instance().setForcedInvulnerable(false);
                            frozen.removePotionEffect(PotionEffectType.SLOWNESS);
                            frozen.removePotionEffect(PotionEffectType.MINING_FATIGUE);
                            frozen.removePotionEffect(PotionEffectType.JUMP_BOOST);
                            Location loc = ctx.bossLocation();
                            if (rescued) {
                                ctx.instance().recordExposure();
                                ctx.instance().setDamageMultiplier(exposedMultiplier);
                                ctx.instance().stagger(exposedStaggerTicks);
                                ctx.instance().entity().setGlowing(true);
                                Fx.coloredBurst(loc.clone().add(0, 1.2, 0), FROST_BLUE, 2.2f, 50, 0.8);
                                Fx.flash(loc.clone().add(0, 1.2, 0), 2);
                                Fx.sound(loc, Sound.BLOCK_GLASS_BREAK, 1.2f, 1.2f);
                                ctx.instance().showTitle(
                                        Component.text("HEART CRACKED", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                                        Component.text("The ally is free — she's left exposed", NamedTextColor.GRAY));
                                ctx.plugin().getServer().getScheduler().runTaskLater(ctx.plugin(), () -> {
                                    if (ctx.boss().isValid()) {
                                        ctx.instance().entity().setGlowing(false);
                                        ctx.instance().setDamageMultiplier(1.0);
                                    }
                                }, exposedTicks);
                            } else if (frozen.isOnline()) {
                                frozen.damage(failDamage, ctx.boss());
                                Fx.coloredBurst(frozen.getLocation().add(0, 1, 0), FROST_BLUE, 2.0f, 40, 0.6);
                                Fx.burst(frozen.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 30, 0.5);
                                Fx.sound(frozen.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.6f);
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                durationTicks + 20, onComplete);
    }
}

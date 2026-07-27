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

/**
 * The Inferno Warlord's signature: it doesn't hide behind wards, it dares you to race it. It plants
 * itself, throws its guard wide open (takes extra damage) and starts overheating — and every second
 * you don't burn it down hard enough it stokes itself hotter, permanently: bigger, faster, and
 * hitting a wider fire pulse each tick. Push enough damage through before the window closes and it
 * overloads, cracked wide open. Fail and the heat it built up stays for the rest of the fight — a
 * rising soft-enrage that punishes stalling, not another totem ring.
 */
public final class MoltenOverloadAttack extends BossAttack {

    private static final Color MOLTEN = Color.fromRGB(255, 110, 20);
    private static final Color WHITE_HOT = Color.fromRGB(255, 230, 160);

    private final int telegraphTicks;
    private final int durationTicks;
    private final int stokeIntervalTicks;
    private final double requiredDamage;
    private final double stokeScalePerTick;
    private final double stokeSpeedPerTick;
    private final double pulseBaseDamage;
    private final double pulseRadius;
    private final int exposedStaggerTicks;
    private final double exposedMultiplier;
    private final int exposedTicks;

    public MoltenOverloadAttack(WeaponsPlugin plugin) {
        super(plugin, "inferno_warlord");
        this.telegraphTicks = configInt("molten-overload-telegraph-ticks", 30);
        this.durationTicks = configInt("molten-overload-duration-ticks", 140);
        this.stokeIntervalTicks = configInt("molten-overload-stoke-interval-ticks", 20);
        this.requiredDamage = configDouble("molten-overload-required-damage", 160.0);
        this.stokeScalePerTick = configDouble("molten-overload-stoke-scale", 1.04);
        this.stokeSpeedPerTick = configDouble("molten-overload-stoke-speed", 1.04);
        this.pulseBaseDamage = configDouble("molten-overload-pulse-damage", 4.0);
        this.pulseRadius = configDouble("molten-overload-pulse-radius", 4.5);
        this.exposedStaggerTicks = configInt("molten-overload-stagger-ticks", 70);
        this.exposedMultiplier = configDouble("molten-overload-exposed-multiplier", 2.2);
        this.exposedTicks = configInt("molten-overload-exposed-ticks", 110);
    }

    @Override
    public String name() {
        return "Molten Overload";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("molten-overload-cooldown-seconds", 48.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(ctx.bossLocation(), MOLTEN, 1.4f, 3.0, 20, 0);
                    Fx.sound(ctx.bossLocation(), Sound.BLOCK_LAVA_POP, 0.8f, 0.6f);
                },
                () -> {
                    Location bossLoc = ctx.bossLocation();
                    double radius = ctx.arena().radius();
                    // Open guard: it can be hurt the whole window (this is a DPS race, not a stand-here
                    // trial), and hits count for extra so the race is winnable inside the timer.
                    ctx.instance().setDamageMultiplier(exposedMultiplier);
                    double startHealth = ctx.boss().getHealth();

                    ctx.instance().showTitle(
                            Component.text("MOLTEN OVERLOAD", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                            Component.text("Burn it down — every second it only gets hotter", NamedTextColor.GRAY));
                    BossAudio.play(bossLoc, "boss.molten_overload", Sound.BLOCK_FIRE_AMBIENT, 1.0f, 0.6f);

                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (!ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            double dealt = startHealth - ctx.boss().getHealth();
                            if (dealt >= requiredDamage) {
                                overload();
                                cancel();
                                return;
                            }
                            if (ticks >= durationTicks) {
                                fizzle();
                                cancel();
                                return;
                            }
                            Location loc = ctx.boss().getLocation();
                            Fx.coloredBurst(loc.clone().add(0, 1.4, 0), MOLTEN, 1.6f, 12, 0.6);
                            if (ticks % stokeIntervalTicks == 0) {
                                stoke();
                            }
                            ticks++;
                        }

                        /** Each stoke: permanent size/speed creep + a widening fire pulse punishing anyone lingering close. */
                        private void stoke() {
                            ctx.instance().empower(stokeScalePerTick, stokeSpeedPerTick);
                            Location loc = ctx.boss().getLocation();
                            double stokeRadius = pulseRadius + ticks * 0.03;
                            Fx.coloredRing(loc, WHITE_HOT, 1.4f, stokeRadius, 26, 0);
                            Fx.sound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);
                            double stokeDamage = pulseBaseDamage + ticks * 0.02;
                            double rSq = stokeRadius * stokeRadius;
                            for (Player player : Arena.combatants(loc, radius)) {
                                if (player.getLocation().distanceSquared(loc) <= rSq) {
                                    player.damage(stokeDamage, ctx.boss());
                                    player.setFireTicks(Math.max(player.getFireTicks(), 40));
                                }
                            }
                        }

                        private void overload() {
                            Location loc = ctx.boss().getLocation();
                            ctx.instance().recordExposure();
                            ctx.instance().stagger(exposedStaggerTicks);
                            ctx.instance().entity().setGlowing(true);
                            // Keep guard open a beat longer as the reward, then settle back to normal.
                            Fx.coloredBurst(loc.clone().add(0, 1.2, 0), WHITE_HOT, 2.4f, 60, 0.9);
                            Fx.flash(loc.clone().add(0, 1.2, 0), 3);
                            Fx.sound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
                            ctx.instance().showTitle(
                                    Component.text("OVERLOADED", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                                    Component.text("The core cracks — pour it on", NamedTextColor.GRAY));
                            ctx.plugin().getServer().getScheduler().runTaskLater(ctx.plugin(), () -> {
                                if (ctx.boss().isValid()) {
                                    ctx.instance().entity().setGlowing(false);
                                    ctx.instance().setDamageMultiplier(1.0);
                                }
                            }, exposedTicks);
                        }

                        private void fizzle() {
                            // Survived the race: the heat it stoked this window is already baked in
                            // permanently (empower is never undone) — that IS the punishment. Just
                            // close the open-guard window; no exposure, they'll have to try again.
                            ctx.instance().setDamageMultiplier(1.0);
                            Location loc = ctx.boss().getLocation();
                            Fx.coloredBurst(loc.clone().add(0, 1.4, 0), MOLTEN, 2.2f, 50, 0.8);
                            Fx.sound(loc, Sound.ENTITY_BLAZE_DEATH, 1.2f, 0.5f);
                            ctx.instance().showTitle(
                                    Component.text("STOKED", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                                    Component.text("Too slow — the fire feeds on your hesitation", NamedTextColor.GRAY));
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                durationTicks + 20, onComplete);
    }
}

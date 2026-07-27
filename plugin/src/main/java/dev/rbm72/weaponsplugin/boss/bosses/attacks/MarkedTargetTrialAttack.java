package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.props.ArenaTotem;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A forced-triage trial: something latches onto one random player and starts feeding. The boss goes
 * briefly untouchable — hitting it does nothing this window — so the only real task is landing
 * enough hits on the thing beside that player before its fuse runs out. Left alive, it detonates on
 * them (with a splash to anyone standing too close). Reusable across bosses via the {@code bossId}
 * constructor param.
 */
public final class MarkedTargetTrialAttack extends BossAttack {

    private final int telegraphTicks;
    private final int fuseTicks;
    private final double markHealth;
    private final double detonationDamage;
    private final double detonationSplashRadius;
    private final double detonationSplashDamage;

    public MarkedTargetTrialAttack(WeaponsPlugin plugin, String bossId) {
        super(plugin, bossId);
        this.telegraphTicks = configInt("marked-target-telegraph-ticks", 24);
        this.fuseTicks = configInt("marked-target-fuse-ticks", 100);
        this.markHealth = configDouble("marked-target-health", 30.0);
        this.detonationDamage = configDouble("marked-target-detonation-damage", 18.0);
        this.detonationSplashRadius = configDouble("marked-target-splash-radius", 3.5);
        this.detonationSplashDamage = configDouble("marked-target-splash-damage", 8.0);
    }

    @Override
    public String name() {
        return "Marked Target Trial";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("marked-target-cooldown-seconds", 35.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        // Anchored to the boss's current position, not the arena's fixed spawn-time center — a
        // chased/knocked-back fight routinely drifts away from where it started, and the stale
        // check would silently exclude everyone actually still in the fight.
        List<Player> candidates = Arena.combatants(ctx.bossLocation(), ctx.arena().radius());
        if (candidates.isEmpty()) {
            // Nobody to mark this cast — a short, quiet cycle rather than forcing a fizzled telegraph.
            sequence(telegraphTicks, () -> { }, () -> { }, 5, onComplete);
            return;
        }
        Player marked = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));

        sequence(telegraphTicks,
                () -> Fx.coloredBurst(marked.getLocation().add(0, 2, 0), Color.fromRGB(180, 0, 0), 1.2f, 10, 0.3),
                () -> {
                    if (!marked.isOnline() || !marked.isValid()) {
                        return;
                    }
                    ctx.instance().setForcedInvulnerable(true);
                    ctx.plugin().actionBarHub().flash(marked,
                            Component.text("You've been marked — kill it before it feeds!", NamedTextColor.RED),
                            3000, ActionBarHub.PRIORITY_NOTICE);
                    BossAudio.play(marked.getLocation(), "boss.marked_target_trial", Sound.ENTITY_VEX_CHARGE, 1.0f, 1.0f);

                    // Aiming straight up/down zeroes the horizontal component — can't normalize that
                    // (would throw and, since forcedInvulnerable is already set above, strand the
                    // boss permanently untouchable with nothing left to ever clear the flag).
                    Vector flat = marked.getLocation().getDirection().setY(0);
                    Vector facing = flat.lengthSquared() > 1.0E-6 ? flat.normalize() : new Vector(1, 0, 0);
                    Location spot = marked.getLocation().add(facing.multiply(1.5));
                    ArenaTotem.spawn(plugin, ctx.instance(), spot, Material.SOUL_SAND,
                            Component.text("Feeding Wraith", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false),
                            markHealth, fuseTicks,
                            destroyed -> {
                                ctx.instance().setForcedInvulnerable(false);
                                Fx.coloredBurst(destroyed.location().add(0, 1, 0), Color.fromRGB(0, 200, 0), 1.4f, 25, 0.4);
                                Fx.sound(destroyed.location(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
                            },
                            expired -> {
                                ctx.instance().setForcedInvulnerable(false);
                                detonate(ctx, marked);
                            });
                },
                fuseTicks + 10, onComplete);
    }

    private void detonate(AttackContext ctx, Player marked) {
        if (!marked.isOnline()) {
            return;
        }
        Location loc = marked.getLocation();
        marked.damage(detonationDamage, ctx.boss());
        Fx.coloredBurst(loc.clone().add(0, 1, 0), Color.fromRGB(255, 0, 0), 2.2f, 45, 0.7);
        Grief.explosion(ctx, loc, 1.2f);
        Fx.sound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
        if (loc.getWorld() == null) {
            return;
        }
        for (Player nearby : loc.getWorld().getPlayers()) {
            if (nearby.equals(marked)) {
                continue;
            }
            if (nearby.getLocation().distanceSquared(loc) <= detonationSplashRadius * detonationSplashRadius) {
                nearby.damage(detonationSplashDamage, ctx.boss());
            }
        }
    }
}

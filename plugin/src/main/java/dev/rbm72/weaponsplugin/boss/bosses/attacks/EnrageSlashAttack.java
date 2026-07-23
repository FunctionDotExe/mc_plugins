package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enrage-only (&lt;15% HP): a long telegraph gives every player in the arena
 * a real chance to reposition. Originally hit everyone regardless of position —
 * that made it unavoidable rather than "the boss's biggest hit, if you don't
 * read it," so it's a raw stat check no positioning can answer. Now it leaves
 * a handful of blue-marked safe pockets around the rim; players who make it
 * into one before the telegraph ends take nothing.
 */
public final class EnrageSlashAttack extends BossAttack {

    private final double damage;
    private final double safeZoneRadius;
    private final int safeZoneCount;
    private final int telegraphTicks;

    public EnrageSlashAttack(WeaponsPlugin plugin) {
        super(plugin, "fallen_king");
        this.damage = configDouble("enrage-slash-damage", 8.0);
        this.safeZoneRadius = configDouble("enrage-slash-safe-zone-radius", 2.2);
        this.safeZoneCount = configInt("enrage-slash-safe-zone-count", 3);
        this.telegraphTicks = configInt("enrage-slash-telegraph-ticks", 30);
    }

    @Override
    public String name() {
        return "Enrage Slash";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("enrage-slash-cooldown-seconds", 14.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Arena arena = ctx.arena();
        int perimeterPillars = 12;

        // Safe pockets are rolled once up front (not on every telegraph tick) so they stay in one
        // place the whole wind-up — players need to be able to commit to a destination and run.
        List<Location> safeSpots = new ArrayList<>(safeZoneCount);
        double baseAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < safeZoneCount; i++) {
            double angle = baseAngle + (2 * Math.PI * i) / safeZoneCount;
            safeSpots.add(arena.center().clone().add(
                    Math.cos(angle) * arena.radius() * 0.8, 0, Math.sin(angle) * arena.radius() * 0.8));
        }

        ctx.instance().showTitle(
                Component.text("⚔ ENRAGE SLASH ⚔", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Reach a blue pocket before it lands!", NamedTextColor.GRAY));

        // Columns of light erupt around the entire arena perimeter during the wind-up — the clearest,
        // biggest telegraph in the whole fight, matching the scale of what's about to land.
        for (int i = 0; i < perimeterPillars; i++) {
            double angle = (2 * Math.PI * i) / perimeterPillars;
            Location pillarBase = arena.center().clone().add(
                    Math.cos(angle) * arena.radius(), 0, Math.sin(angle) * arena.radius());
            Fx.glowPillar(plugin, pillarBase, Material.RED_STAINED_GLASS, 0.4f, 6f, telegraphTicks);
        }

        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(arena.center(), arena.radius());
                    for (Location safeSpot : safeSpots) {
                        Telegraph.safeZone(safeSpot, safeZoneRadius);
                    }
                    Fx.coloredRing(ctx.bossLocation(), Color.fromRGB(200, 0, 0), 2.0f, 3.0, 38,
                            System.currentTimeMillis() % 1000 / 1000.0);
                },
                () -> {
                    BossAudio.play(ctx.bossLocation(), "boss.fallen_king.enrage_slash", Sound.ENTITY_WITHER_AMBIENT, 1.5f, 0.4f);
                    Fx.sound(ctx.bossLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.5f);
                    Fx.expandingRings(plugin, arena.center(), Particle.SWEEP_ATTACK, arena.radius(), 7, 3L);
                    Fx.expandingRings(plugin, arena.center(), Particle.FLAME, arena.radius() * 0.8, 6, 4L);
                    ctx.bossLocation().getWorld().spawnParticle(Particle.EXPLOSION_EMITTER,
                            ctx.bossLocation().add(0, 1, 0), 9, 0.8, 0.8, 0.8, 0);
                    for (Player player : arena.playersInside()) {
                        boolean sheltered = safeSpots.stream()
                                .anyMatch(spot -> spot.distanceSquared(player.getLocation()) <= safeZoneRadius * safeZoneRadius);
                        if (sheltered) {
                            Fx.coloredBurst(player.getLocation().add(0, 1, 0), Color.fromRGB(60, 140, 255), 1.4f, 18, 0.4);
                            continue;
                        }
                        player.damage(damage, ctx.boss());
                        Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                        Fx.coloredBurst(player.getLocation().add(0, 1, 0), Color.fromRGB(200, 0, 0), 1.8f, 26, 0.5);
                        Fx.flash(player.getLocation().add(0, 1, 0), 1);
                    }
                },
                16, onComplete);
    }
}

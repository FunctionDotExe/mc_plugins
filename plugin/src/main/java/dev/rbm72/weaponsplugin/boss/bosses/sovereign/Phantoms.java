package dev.rbm72.weaponsplugin.boss.bosses.sovereign;

import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * P3's phantom split: three identical Sovereigns. Nothing here is invulnerable — the real one is just
 * {@link dev.rbm72.weaponsplugin.boss.BossInstance#entity()} itself, so ordinary damage already only
 * ever lands on it through the normal pipeline. What this class adds is the puzzle: two decoys that look
 * and move the same way, a periodic physical tell only the real one casts (a shadow of falling blocks
 * beneath itself), and a counter-blink punishment for guessing wrong — batch-1 §5.3.
 */
final class Phantoms {

    private static final double DECOY_HEALTH = 200.0;

    private final SovereignFight fight;
    private final List<Decoy> decoys = new ArrayList<>();
    private int tellIntervalTicks = 60;
    private int tellCountdown;

    private static final class Decoy {
        final LivingEntity entity;
        double lastHealth;

        Decoy(LivingEntity entity) {
            this.entity = entity;
            this.lastHealth = entity.getHealth();
        }
    }

    Phantoms(SovereignFight fight) {
        this.fight = fight;
    }

    void spawn() {
        clear();
        var world = fight.world();
        if (world == null) {
            return;
        }
        Location centre = fight.instance().entity().getLocation();
        int count = fight.config().num("phantom-decoys", 2);
        double radius = fight.config().dbl("phantom-spawn-radius", 6.0);
        for (int i = 0; i < count; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            Location spot = centre.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            LivingEntity decoy = fight.instance().addManager().spawn(world, spot, EntityType.ENDERMAN, mob -> {
                mob.customName(fight.instance().boss().displayName());
                mob.setCustomNameVisible(false);
                mob.setRemoveWhenFarAway(false);
                var health = mob.getAttribute(Attribute.MAX_HEALTH);
                if (health != null) {
                    health.setBaseValue(DECOY_HEALTH);
                    mob.setHealth(DECOY_HEALTH);
                }
                var damageAttr = mob.getAttribute(Attribute.ATTACK_DAMAGE);
                if (damageAttr != null) {
                    damageAttr.setBaseValue(0.0);
                }
                if (mob instanceof Mob m) {
                    m.setTarget(fight.combatants().stream().findFirst().orElse(null));
                }
            });
            decoys.add(new Decoy(decoy));
        }
        tellIntervalTicks = fight.config().num("phantom-tell-interval-ticks", 60);
        tellCountdown = tellIntervalTicks;
    }

    /** A crystal broken down (see {@link EndCrystals}) makes the real one's tell fire more often. */
    void shortenTellInterval() {
        int min = fight.config().num("phantom-tell-min-interval-ticks", 25);
        tellIntervalTicks = Math.max(min, tellIntervalTicks - fight.config().num("phantom-tell-shorten-ticks", 8));
    }

    void pulse(int intervalTicks) {
        pollHits();
        drawDecoys();

        tellCountdown -= intervalTicks;
        if (tellCountdown <= 0) {
            tellCountdown = tellIntervalTicks;
            showTell();
        }
    }

    /**
     * The real Sovereign's only tell: real, physically-falling blocks only it casts beneath itself —
     * not a particle standing in for one. Spawned as cosmetic {@code FallingBlock}s exactly like
     * {@code Grief#throwBlock}'s own non-placing props: they never place, never drop, and never hurt
     * anyone, so the tell can never be mistaken for a hazard, only for the physical shadow it is.
     */
    private void showTell() {
        Location at = fight.instance().entity().getLocation();
        var world = at.getWorld();
        if (world == null) {
            return;
        }
        int count = fight.config().num("phantom-tell-shard-count", 3);
        for (int i = 0; i < count; i++) {
            double ox = ThreadLocalRandom.current().nextDouble(-0.4, 0.4);
            double oz = ThreadLocalRandom.current().nextDouble(-0.4, 0.4);
            var shard = world.spawnFallingBlock(at.clone().add(ox, 3.0 + i * 0.6, oz),
                    Material.OBSIDIAN.createBlockData());
            shard.setDropItem(false);
            shard.setCancelDrop(true);
            shard.setHurtEntities(false);
            shard.setPersistent(false);
            fight.instance().trackEntity(shard);
            new org.bukkit.scheduler.BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (ticks++ >= 20 || !shard.isValid() || shard.isOnGround()) {
                        if (shard.isValid()) {
                            shard.remove();
                        }
                        cancel();
                    }
                }
            }.runTaskTimer(fight.plugin(), 1L, 1L);
        }
        Fx.burst(at.clone().add(0, 0.1, 0), Particle.SQUID_INK, 6, 0.4);
    }

    private void pollHits() {
        for (Decoy decoy : decoys) {
            if (!decoy.entity.isValid() || decoy.entity.isDead()) {
                continue;
            }
            double delta = decoy.lastHealth - decoy.entity.getHealth();
            if (delta > 0.01) {
                decoy.entity.setHealth(DECOY_HEALTH);
                punish(decoy);
            }
            decoy.lastHealth = decoy.entity.getHealth();
        }
    }

    /** The phantom blinks onto whoever struck it and hits back — "counter-blinks onto you" (batch-1 §5.4). */
    private void punish(Decoy decoy) {
        Player culprit = null;
        double best = 6.0;
        for (Player player : fight.combatants()) {
            double dist = player.getLocation().distance(decoy.entity.getLocation());
            if (dist < best) {
                best = dist;
                culprit = player;
            }
        }
        if (culprit == null) {
            return;
        }
        Location behind = culprit.getLocation().clone().subtract(culprit.getLocation().getDirection().multiply(1.5));
        decoy.entity.teleport(behind);
        double damage = fight.config().dbl("phantom-counter-damage", 8.0);
        culprit.damage(damage, decoy.entity);
        Fx.coloredBurst(behind.clone().add(0, 1.2, 0), SovereignFight.VOID_PURPLE, 2.0f, 40, 0.6);
        Fx.sound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.3f, 0.7f);
        fight.plugin().actionBarHub().flash(culprit,
                Component.text("WRONG ONE — it blinked onto you", NamedTextColor.LIGHT_PURPLE),
                1800L, ActionBarHub.PRIORITY_NOTICE);
    }

    private void drawDecoys() {
        for (Decoy decoy : decoys) {
            if (decoy.entity.isValid()) {
                Fx.burst(decoy.entity.getLocation().add(0, 1.2, 0), Particle.PORTAL, 3, 0.4);
            }
        }
    }

    void clear() {
        for (Decoy decoy : decoys) {
            if (decoy.entity.isValid()) {
                decoy.entity.remove();
            }
        }
        decoys.clear();
    }

    void discard() {
        clear();
    }
}

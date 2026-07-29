package dev.rbm72.weaponsplugin.boss.bosses.choir;

import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vex;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Everything the Hollow Choir does with what it heard. Every attack here resolves at a point taken from
 * {@link Noise} rather than at a player, which is what makes misdirection a real mechanic instead of a
 * flavour: strike a note block across the arena and the fangs erupt <em>there</em> (batch-3 §4.4).
 * <p>
 * The Darkness here is the supernatural kind and <b>cannot be lit</b> — §5.3's explicit deconfliction
 * against the Weeping Colossus, whose darkness is real block light and is answered with torches. The two
 * bosses have the same feeling and opposite counterplay on purpose, so this one forces the switch to
 * audio and no amount of light will help.
 */
final class ChoirAttacks {

    private final ChoirFight fight;
    private final Set<UUID> illusions = new HashSet<>();

    private Handler handler;
    private int misdirected;

    ChoirAttacks(ChoirFight fight) {
        this.fight = fight;
    }

    void arm() {
        if (handler != null) {
            return;
        }
        handler = new Handler();
        fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
    }

    // ---------------------------------------------------------------- attacks

    /**
     * <b>Choir Wail.</b> Travels the path the sound took — from the Choir to the last thing it heard —
     * and hits everyone standing on that line. Being off the path is the whole counterplay, and after a
     * good misdirection the path runs somewhere nobody is.
     */
    void wail() {
        Location heard = fight.noise().lastHeardAt();
        if (heard == null) {
            return;
        }
        Location origin = fight.instance().entity().getLocation().add(0, 1.4, 0);
        Fx.line(origin, heard, Particle.SOUL_FIRE_FLAME, 40);
        Fx.sound(origin, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.6f, 0.6f);
        countMisdirection(heard);

        double width = fight.config().dbl("wail-width", 2.0);
        double damage = fight.config().dbl("wail-damage", 14.0);
        for (Player player : fight.combatants()) {
            if (distanceToSegment(player.getLocation(), origin, heard) > width) {
                continue;
            }
            player.damage(damage, fight.instance().entity());
            Fx.coloredBurst(player.getLocation().add(0, 1, 0), ChoirFight.PALE_VIOLET, 1.6f, 20, 0.4);
        }
    }

    /** <b>Fang Line.</b> Erupts from the nearest sensor toward the noise, and lands where the noise was. */
    void fangLine() {
        Location heard = fight.noise().lastHeardAt();
        if (heard == null) {
            return;
        }
        countMisdirection(heard);
        double damage = fight.config().dbl("fang-damage", 11.0);
        int lines = 1 + Math.max(0, fight.playerCount() - 2);
        World world = fight.world();
        if (world == null) {
            return;
        }
        for (int line = 0; line < lines; line++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            for (int step = 0; step < 6; step++) {
                Location at = heard.clone().add(Math.cos(angle) * step, 0, Math.sin(angle) * step);
                Fx.burst(at, Particle.SOUL, 8, 0.2);
                Fx.sound(at, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.0f, 1.0f);
                for (Player player : dev.rbm72.weaponsplugin.boss.Arena.combatants(at, 1.4)) {
                    player.damage(damage, fight.instance().entity());
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 4, true, false, false));
                }
            }
        }
    }

    /** <b>Vex swarm</b> on whoever has been loudest — harassment aimed squarely at the damage dealers. */
    void vexSwarm() {
        Player target = fight.noise().loudest();
        World world = fight.world();
        if (target == null || world == null) {
            return;
        }
        int size = fight.config().num("vex-swarm-size", 2) + Math.max(0, fight.playerCount() - 2);
        for (int i = 0; i < Math.min(size, fight.config().num("vex-swarm-max", 6)); i++) {
            Location at = target.getLocation().add(
                    ThreadLocalRandom.current().nextDouble(-3, 3), 2, ThreadLocalRandom.current().nextDouble(-3, 3));
            LivingEntity vex = fight.instance().addManager().spawn(world, at, EntityType.VEX, entity -> {
                entity.setPersistent(false);
                entity.setRemoveWhenFarAway(false);
                if (entity instanceof Vex mob) {
                    mob.setTarget(target);
                }
            });
            fight.instance().trackEntity(vex);
        }
        Fx.sound(target.getLocation(), Sound.ENTITY_VEX_CHARGE, 1.4f, 1.0f);
    }

    /** A Darkness wave. Real, supernatural, and unlightable — see the class header. */
    void darknessWave() {
        int ticks = fight.config().num("darkness-ticks", 160);
        Location centre = fight.instance().arena().center();
        Fx.sound(centre, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.8f, 0.5f);
        for (Player player : fight.combatants()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0, true, false, true));
        }
    }

    /**
     * <b>Grand Illusion.</b> Copies of the Choir that make no sound. Only the real one is audible when it
     * moves, so the counter is to listen rather than look — and hitting a copy is loud, which hands your
     * position straight back to the thing hunting you.
     */
    void raiseIllusions() {
        World world = fight.world();
        if (world == null || !illusions.isEmpty()) {
            return;
        }
        int count = Math.max(1, fight.config().num("illusion-count", 3));
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 * i) / count;
            Location at = fight.instance().entity().getLocation().add(
                    Math.cos(angle) * 5, 0, Math.sin(angle) * 5);
            LivingEntity copy = fight.instance().addManager().spawn(world, at, EntityType.EVOKER, entity -> {
                entity.setPersistent(false);
                entity.setRemoveWhenFarAway(false);
                entity.setSilent(true);
                entity.setInvulnerable(true);
                entity.setCollidable(false);
                if (entity instanceof Mob mob) {
                    mob.setAI(false);
                    mob.setTarget(null);
                }
            });
            fight.instance().trackEntity(copy);
            illusions.add(copy.getUniqueId());
        }
        Fx.sound(fight.instance().arena().center(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.6f, 0.7f);
    }

    void dismissIllusions() {
        for (UUID id : new ArrayList<>(illusions)) {
            Entity entity = fight.plugin().getServer().getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        illusions.clear();
    }

    /** P4: everything sings at once, so misdirection stops working and it hunts the nearest body instead. */
    void allVoices() {
        Location centre = fight.instance().arena().center();
        for (int i = 0; i < fight.instruments().noteCount(); i++) {
            fight.instruments().sing(i);
        }
        Fx.sound(centre, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.6f, 0.5f);
    }

    // ---------------------------------------------------------------- queries

    /** Attacks that resolved somewhere nobody was standing — P1's exit condition. */
    int misdirected() {
        return misdirected;
    }

    private void countMisdirection(Location heard) {
        if (fight.noise().lastHeardIsEmpty()) {
            misdirected++;
            for (Player player : fight.combatants()) {
                fight.plugin().actionBarHub().flash(player,
                        Component.text("IT SWUNG AT NOTHING", NamedTextColor.GREEN),
                        1800L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
            }
        }
    }

    void discardAll() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        dismissIllusions();
    }

    // ----------------------------------------------------------------- helpers

    private static double distanceToSegment(Location point, Location from, Location to) {
        if (point.getWorld() == null || !point.getWorld().equals(from.getWorld())) {
            return Double.MAX_VALUE;
        }
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared < 1.0e-6) {
            return point.distance(from);
        }
        double t = ((point.getX() - from.getX()) * dx + (point.getZ() - from.getZ()) * dz) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        double px = from.getX() + t * dx;
        double pz = from.getZ() + t * dz;
        double ox = point.getX() - px;
        double oz = point.getZ() - pz;
        return Math.sqrt(ox * ox + oz * oz);
    }

    private final class Handler implements Listener {

        /**
         * Swinging at a copy does nothing to it and everything to you: the swing is noise, and noise is
         * how this boss finds people.
         */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onIllusionHit(EntityDamageByEntityEvent event) {
            if (!illusions.contains(event.getEntity().getUniqueId())) {
                return;
            }
            Player attacker = null;
            if (event.getDamager() instanceof Player player) {
                attacker = player;
            } else if (event.getDamager() instanceof Projectile projectile
                    && projectile.getShooter() instanceof Player shooter) {
                attacker = shooter;
            }
            if (attacker == null) {
                return;
            }
            fight.noise().register(attacker.getLocation(), attacker,
                    fight.config().dbl("noise-illusion-hit", 1.6));
            fight.plugin().actionBarHub().flash(attacker,
                    Component.text("THAT ONE MADE NO SOUND", NamedTextColor.RED),
                    1800L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    /** True if this entity is one of the Choir's copies — the phases use it to keep their readouts honest. */
    boolean isIllusion(UUID id) {
        return illusions.contains(id);
    }

    /** Illusion count still standing, for the P3/P4 readouts. */
    int illusionCount() {
        int alive = 0;
        for (UUID id : illusions) {
            Entity entity = fight.plugin().getServer().getEntity(id);
            if (entity != null && entity.isValid()) {
                alive++;
            }
        }
        return alive;
    }

    /** Live vex/illusion housekeeping between phases without tearing the listener down. */
    List<UUID> illusionIds() {
        return new ArrayList<>(illusions);
    }
}

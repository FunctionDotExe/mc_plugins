package dev.rbm72.weaponsplugin.boss.bosses.wyrm;

import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * P3's coiling serpent body — and P4's decorative ring, the same chain of real, positioned entities
 * used two different ways. Same trick {@code colossus.Joints} established for "hit a specific part of a
 * moving body": one small marker entity per segment, repositioned every pulse, every hit on it cancelled
 * and replayed onto the real boss entity so the health that matters is always {@code instance.entity()}
 * — see {@link #isForwarding()} and {@code SerpentPhase#filterDamage}.
 * <p>
 * Most segments are armored (hits barely register); a rotating subset is exposed (visibly cracked,
 * full damage). Exposure <em>migrates</em> — {@link #setExposed} can be called on a different subset
 * every few seconds — which is the one thing {@code Joints} never needed, since the Colossus's weak
 * points don't move around its own body mid-phase.
 */
final class WyrmSegments {

    private final WyrmFight fight;
    private final List<Segment> chain = new ArrayList<>();
    private Handler handler;
    private boolean forwarding;

    private static final class Segment {
        LivingEntity entity;
        double hp;
        double maxHp;
        boolean broken;
        boolean exposed;
    }

    WyrmSegments(WyrmFight fight) {
        this.fight = fight;
    }

    /** (Re)builds a chain of {@code count} segments, replacing any previous one. */
    void spawnChain(int count, double maxHpEach) {
        discardAll();
        ensureHandler();
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location at = fight.instance().arena().center();
        for (int i = 0; i < count; i++) {
            Segment segment = new Segment();
            segment.hp = maxHpEach;
            segment.maxHp = maxHpEach;
            LivingEntity marker = (LivingEntity) world.spawnEntity(at, EntityType.ZOMBIE);
            marker.setPersistent(true);
            marker.setSilent(true);
            marker.setRemoveWhenFarAway(false);
            marker.setCollidable(true);
            marker.setGravity(false);
            marker.setCanPickupItems(false);
            marker.setGlowing(true);
            if (marker instanceof Mob mob) {
                mob.setAI(false);
                mob.setTarget(null);
            }
            AttributeInstance scaleAttr = marker.getAttribute(Attribute.SCALE);
            if (scaleAttr != null) {
                scaleAttr.setBaseValue(fight.config().dbl("segment-scale", 1.6));
            }
            AttributeInstance maxHealthAttr = marker.getAttribute(Attribute.MAX_HEALTH);
            double markerHealth = 4000.0;
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(markerHealth);
                markerHealth = maxHealthAttr.getValue();
            }
            marker.setHealth(markerHealth);
            AttributeInstance knockbackAttr = marker.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (knockbackAttr != null) {
                knockbackAttr.setBaseValue(1.0);
            }
            fight.instance().trackEntity(marker);
            segment.entity = marker;
            chain.add(segment);
            marker.customName(nameFor(i, segment));
            marker.setCustomNameVisible(true);
        }
    }

    int size() {
        return chain.size();
    }

    /** The lead segment's current world position — the "head" the real boss entity rides along with. */
    Location headLocation() {
        if (chain.isEmpty()) {
            return null;
        }
        LivingEntity head = chain.get(0).entity;
        return head != null && head.isValid() ? head.getLocation() : null;
    }

    boolean isForwarding() {
        return forwarding;
    }

    void setExposed(int index, boolean exposed) {
        if (index < 0 || index >= chain.size()) {
            return;
        }
        Segment segment = chain.get(index);
        if (segment.broken || segment.exposed == exposed) {
            return;
        }
        segment.exposed = exposed;
        segment.entity.customName(nameFor(index, segment));
        if (exposed && segment.entity.isValid()) {
            Fx.coloredBurst(segment.entity.getLocation().add(0, 0.6, 0), WyrmFight.STARLIGHT, 1.6f, 26, 0.5);
            Fx.sound(segment.entity.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.4f);
        }
    }

    boolean isBroken(int index) {
        return index >= 0 && index < chain.size() && chain.get(index).broken;
    }

    int brokenCount() {
        int count = 0;
        for (Segment segment : chain) {
            if (segment.broken) {
                count++;
            }
        }
        return count;
    }

    /**
     * Repositions the whole chain along a coiling path that sweeps through the arena — {@code
     * coilAngle} advances every pulse (see {@code SerpentPhase}), so the body physically moves rather
     * than sitting still with only its exposed set changing.
     */
    void reposition(double coilAngle, double baseRadius, double amplitude, double angularSpacing) {
        Location centre = fight.instance().arena().center();
        World world = centre.getWorld();
        if (world == null) {
            return;
        }
        for (int i = 0; i < chain.size(); i++) {
            Segment segment = chain.get(i);
            if (segment.entity == null || !segment.entity.isValid()) {
                continue;
            }
            double angle = coilAngle - i * angularSpacing;
            double radius = baseRadius + amplitude * Math.sin(coilAngle * 0.7 + i * 0.6);
            double x = centre.getX() + radius * Math.cos(angle);
            double z = centre.getZ() + radius * Math.sin(angle);
            double y = world.getHighestBlockYAt((int) x, (int) z) + 1.0;
            segment.entity.teleport(new Location(world, x, y, z));
            if (!segment.broken) {
                Fx.coloredBurst(segment.entity.getLocation().add(0, 0.4, 0),
                        segment.exposed ? WyrmFight.STARLIGHT : WyrmFight.VOID_PURPLE,
                        segment.exposed ? 1.0f : 0.6f, segment.exposed ? 4 : 2, 0.15);
            }
        }
    }

    /** Placement for P4's static perimeter ring — no coiling, no exposed/armor distinction, pure cover. */
    void positionRing(double baseRadiusFraction) {
        Location centre = fight.instance().arena().center();
        World world = centre.getWorld();
        double radius = fight.instance().arena().radius() * baseRadiusFraction;
        if (world == null || chain.isEmpty()) {
            return;
        }
        for (int i = 0; i < chain.size(); i++) {
            Segment segment = chain.get(i);
            if (segment.entity == null || !segment.entity.isValid()) {
                continue;
            }
            double angle = (2 * Math.PI * i) / chain.size();
            double x = centre.getX() + radius * Math.cos(angle);
            double z = centre.getZ() + radius * Math.sin(angle);
            double y = world.getHighestBlockYAt((int) x, (int) z) + 1.0;
            segment.entity.teleport(new Location(world, x, y, z));
        }
    }

    void discardAll() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        for (Segment segment : chain) {
            if (segment.entity != null && segment.entity.isValid()) {
                segment.entity.remove();
            }
        }
        chain.clear();
    }

    private void ensureHandler() {
        if (handler == null) {
            handler = new Handler();
            fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
        }
    }

    private Component nameFor(int index, Segment segment) {
        int pips = 8;
        int filled = (int) Math.ceil(pips * Math.max(0.0, segment.hp) / segment.maxHp);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < pips; i++) {
            bar.append(i < filled ? '█' : '░');
        }
        NamedTextColor color = segment.broken ? NamedTextColor.DARK_GRAY
                : segment.exposed ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY;
        return Component.text("Segment " + (index + 1) + "  " + bar, color).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * A broken segment is an open wound, not a shield — same fix and same reasoning as
     * {@code colossus.Joints#resolveHit}. Returning early on {@code segment.broken} ate the hit outright
     * (the handler has already cancelled the marker's own damage), and a broken segment keeps standing in
     * the coil catching swings, so every one of the three segments P3 asks the group to break turned into
     * a piece of body that absorbs damage forever.
     */
    private void resolveHit(Segment segment, int index, double amount, Player attacker) {
        if (amount <= 0 || segment.entity == null) {
            return;
        }
        boolean fullValue = segment.broken || segment.exposed;
        double effective = fullValue ? amount : amount * fight.config().dbl("segment-armored-fraction", 0.03);
        Fx.coloredBurst(segment.entity.getLocation().add(0, 0.6, 0),
                fullValue ? WyrmFight.STARLIGHT : WyrmFight.VOID_PURPLE, 1.2f, 10, 0.3);
        if (!segment.broken) {
            segment.hp = Math.max(0, segment.hp - effective);
            if (segment.hp <= 0) {
                segment.broken = true;
                segment.exposed = false;
                Fx.coloredBurst(segment.entity.getLocation().add(0, 0.8, 0), WyrmFight.STARLIGHT, 2.2f, 50, 0.7);
                Fx.sound(segment.entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_HURT, 1.0f, 1.3f);
            }
            segment.entity.customName(nameFor(index, segment));
        }

        forwarding = true;
        try {
            fight.instance().entity().damage(effective, attacker);
        } finally {
            forwarding = false;
        }
    }

    private final class Handler implements Listener {

        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        public void onDamage(EntityDamageByEntityEvent event) {
            for (int i = 0; i < chain.size(); i++) {
                Segment segment = chain.get(i);
                if (segment.entity != null && segment.entity.getUniqueId().equals(event.getEntity().getUniqueId())) {
                    event.setCancelled(true);
                    Player attacker = resolveAttacker(event);
                    if (attacker != null) {
                        resolveHit(segment, i, event.getDamage(), attacker);
                    }
                    return;
                }
            }
        }

        private Player resolveAttacker(EntityDamageByEntityEvent event) {
            Entity damager = event.getDamager();
            if (damager instanceof Player player) {
                return player;
            }
            if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
                return shooter;
            }
            return null;
        }
    }
}

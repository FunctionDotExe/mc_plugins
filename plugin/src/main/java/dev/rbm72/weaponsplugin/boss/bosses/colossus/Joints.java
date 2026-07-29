package dev.rbm72.weaponsplugin.boss.bosses.colossus;

import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.Map;

/**
 * The Colossus's damageable body regions — ankles, knees, a shoulder core and a chest core — and the
 * single rule the whole fight hangs off: <b>hits anywhere but a joint barely register</b>.
 * <p>
 * <b>Why a real entity per joint, and not a location check on the boss's own hitbox.</b> Bukkit's
 * {@code EntityDamageByEntityEvent} tells you which entity was hit, never <em>where on it</em> — there
 * is no bone/part id for a single {@code IronGolem} hitbox to ray-cast against. The only way to make
 * "hit the joint, not the body" a real, independently-clickable target rather than a proximity guess a
 * player could cheat from anywhere is to give each joint its own small hittable entity, positioned on
 * the Colossus's body and re-derived every pulse as it moves. {@code ArenaTotem} already established
 * this trick for a fixed-position destructible prop (a NoAI mob wearing a head item as its visual
 * identity); this is the same idea made to track a moving parent and to <em>forward</em> its damage
 * into the real boss rather than dying on its own.
 * <p>
 * <b>Why the forwarding, not a marker with its own HP bar.</b> The Colossus's health — the number every
 * phase threshold and the death check actually watch — has to come down when a joint is hit, or none of
 * P1–P2's exit conditions and P4's core brawl would ever move the boss's own HP at all. So a joint hit
 * cancels the marker's own (irrelevant) damage and replays the same amount onto
 * {@code instance.entity()} via {@link LivingEntity#damage}, which re-enters the framework's real
 * damage pipeline ({@code BossDamageListener} → the phase's {@code filterDamage} → the phase floor) —
 * the same path a direct melee hit on the boss takes. {@link #forwarding} is the signal a phase's
 * {@code filterDamage} reads to tell those two cases apart: a hit arriving while this flag is true came
 * from a joint and should be treated as real damage; a hit arriving with the flag false landed directly
 * on the Colossus's own hitbox and is the "barely registers" body hit the design calls for. See
 * {@code ColossusPhaseMechanic#filterDamage}.
 */
final class Joints {

    /** The six body regions the spec names. Not every phase spawns every one — see each phase's onArm. */
    enum Id {
        ANKLE_LEFT, ANKLE_RIGHT, KNEE_LEFT, KNEE_RIGHT, SHOULDER, CHEST_CORE
    }

    private final ColossusFight fight;
    private final Map<Id, Joint> joints = new EnumMap<>(Id.class);
    private Handler handler;

    /**
     * True for the exact duration of one forwarded {@code damage()} call. Bukkit dispatches events
     * synchronously on the calling thread, so a phase's {@code filterDamage} reading this from inside
     * that same call always sees the correct answer — set immediately before the call, cleared in a
     * {@code finally} immediately after.
     */
    private boolean forwarding;

    private static final class Joint {
        final Id id;
        LivingEntity entity;
        double hp;
        double maxHp;
        boolean broken;
        /** Whether a hit lands at (near) full value right now, or is scaled down like a body hit. */
        boolean exposed = true;

        Joint(Id id, double maxHp) {
            this.id = id;
            this.hp = maxHp;
            this.maxHp = maxHp;
        }
    }

    Joints(ColossusFight fight) {
        this.fight = fight;
    }

    // ---------------------------------------------------------------- spawn/despawn

    /** Spawns {@code id} with {@code maxHp} if it isn't already live; spawning an already-live joint is a no-op. */
    void spawn(Id id, double maxHp) {
        ensureHandler();
        Joint joint = joints.computeIfAbsent(id, k -> new Joint(id, maxHp));
        if (joint.entity != null && joint.entity.isValid()) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location at = location(id, fight.instance().entity().getLocation());
        LivingEntity marker = (LivingEntity) world.spawnEntity(at, EntityType.ZOMBIE);
        marker.setPersistent(true);
        marker.setSilent(true);
        marker.setRemoveWhenFarAway(false);
        marker.setCollidable(false);
        marker.setGravity(false);
        marker.setCanPickupItems(false);
        marker.setGlowing(true);
        if (marker instanceof Mob mob) {
            mob.setAI(false);
            mob.setTarget(null);
        }
        AttributeInstance maxHealthAttr = marker.getAttribute(Attribute.MAX_HEALTH);
        double markerHealth = 4000.0;
        if (maxHealthAttr != null) {
            // Vastly more than any single hit could apply — this marker's own health is a red herring,
            // never meant to reach zero. Every hit is cancelled and replayed onto the real boss instead
            // (see Handler#onDamage); this pool only exists so the entity can never actually die mid-swing.
            maxHealthAttr.setBaseValue(markerHealth);
            markerHealth = maxHealthAttr.getValue();
        }
        marker.setHealth(markerHealth);
        AttributeInstance knockbackAttr = marker.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockbackAttr != null) {
            knockbackAttr.setBaseValue(1.0);
        }
        EntityEquipment equipment = marker.getEquipment();
        if (equipment != null) {
            // Also stops an undead marker igniting in daylight arenas — a side effect, not the point.
            equipment.setHelmet(new ItemStack(headFor(id)));
            equipment.setHelmetDropChance(0f);
        }
        fight.instance().trackEntity(marker);

        joint.entity = marker;
        joint.maxHp = maxHp;
        if (joint.hp <= 0 && !joint.broken) {
            joint.hp = maxHp;
        }
        marker.customName(nameFor(joint));
        marker.setCustomNameVisible(true);
        Fx.coloredBurst(at.clone().add(0, 0.6, 0), ColossusFight.SOLAR_GOLD, 1.4f, 26, 0.5);
        Fx.sound(at, Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 1.3f);
    }

    void despawn(Id id) {
        Joint joint = joints.get(id);
        if (joint == null || joint.entity == null) {
            return;
        }
        if (joint.entity.isValid()) {
            joint.entity.remove();
        }
        joint.entity = null;
    }

    boolean isLive(Id id) {
        Joint joint = joints.get(id);
        return joint != null && joint.entity != null && joint.entity.isValid();
    }

    void setExposed(Id id, boolean exposed) {
        Joint joint = joints.get(id);
        if (joint == null || joint.exposed == exposed) {
            return;
        }
        joint.exposed = exposed;
        if (joint.entity != null && joint.entity.isValid()) {
            joint.entity.customName(nameFor(joint));
            if (exposed) {
                Fx.coloredBurst(joint.entity.getLocation().add(0, 0.6, 0), ColossusFight.RADIANT_WHITE, 1.6f, 30, 0.5);
                Fx.sound(joint.entity.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
            }
        }
    }

    boolean isBroken(Id id) {
        Joint joint = joints.get(id);
        return joint != null && joint.broken;
    }

    boolean allBroken(Id... ids) {
        for (Id id : ids) {
            if (!isBroken(id)) {
                return false;
            }
        }
        return true;
    }

    /** Cumulative chip fraction across {@code ids}, averaged 0..1 — a monotonic progress signal even before anything breaks. */
    double damageProgress(Id... ids) {
        double total = 0;
        for (Id id : ids) {
            Joint joint = joints.get(id);
            if (joint == null) {
                continue;
            }
            total += joint.broken ? 1.0 : Math.max(0.0, 1.0 - joint.hp / joint.maxHp);
        }
        return ids.length == 0 ? 0 : total / ids.length;
    }

    // ---------------------------------------------------------------- per-pulse

    /** Re-derives every live joint's world position from the Colossus's current body — called every phase pulse. */
    void reposition() {
        boolean anyLive = false;
        for (Joint joint : joints.values()) {
            if (joint.entity != null) {
                anyLive = true;
                break;
            }
        }
        if (!anyLive) {
            return;
        }
        Location bossLoc = fight.instance().entity().getLocation();
        for (Joint joint : joints.values()) {
            if (joint.entity == null || !joint.entity.isValid()) {
                continue;
            }
            joint.entity.teleport(location(joint.id, bossLoc));
            if (!joint.broken) {
                Fx.coloredBurst(joint.entity.getLocation().add(0, 0.3, 0),
                        joint.exposed ? ColossusFight.RADIANT_WHITE : ColossusFight.SOLAR_GOLD,
                        joint.exposed ? 1.0f : 0.6f, joint.exposed ? 4 : 2, 0.15);
            }
        }
    }

    /**
     * Restores every joint this fight has ever tracked to full and unbroken, and heals the Colossus a
     * real fraction of its max health — batch-2 §2.3's "a completed charge fully restores its broken
     * joints", explicitly <em>a far better threat than damage</em>. The framework has no lever to
     * literally rewind a phase transition ({@code BossInstance#selectPhase} only ever advances the
     * index), so this expresses the repair the way that actually matters to a group mid-fight: the
     * health bar they spent the last phase grinding down visibly climbs back up, and they have to burn
     * through it again in whatever phase they are currently in.
     */
    void repairAll() {
        boolean repairedAny = false;
        for (Joint joint : joints.values()) {
            if (joint.broken || joint.hp < joint.maxHp) {
                repairedAny = true;
            }
            joint.hp = joint.maxHp;
            joint.broken = false;
            if (joint.entity != null && joint.entity.isValid()) {
                joint.entity.customName(nameFor(joint));
                Fx.coloredBurst(joint.entity.getLocation().add(0, 0.5, 0), ColossusFight.SOLAR_GOLD, 1.6f, 24, 0.4);
            }
        }
        if (!repairedAny) {
            return;
        }
        LivingEntity boss = fight.instance().entity();
        AttributeInstance maxHealthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : boss.getHealth();
        double heal = maxHealth * fight.config().dbl("beacon-repair-heal-fraction", 0.16);
        boss.setHealth(Math.min(maxHealth * 0.97, boss.getHealth() + heal));
    }

    void discardAll() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        for (Joint joint : joints.values()) {
            if (joint.entity != null && joint.entity.isValid()) {
                joint.entity.remove();
            }
        }
        joints.clear();
    }

    boolean isForwarding() {
        return forwarding;
    }

    private void ensureHandler() {
        if (handler == null) {
            handler = new Handler();
            fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
        }
    }

    // ---------------------------------------------------------------- geometry

    /**
     * Local-space offset (right, forward, up) for {@code id} on a humanoid iron-golem frame, rotated
     * onto the Colossus's live facing and scaled by its live {@link Attribute#SCALE} — so a joint
     * tracks correctly whether the boss is at its base spawn size or has grown from an enrage empower.
     */
    private Location location(Id id, Location bossLoc) {
        double right;
        double forward;
        double up;
        switch (id) {
            case ANKLE_LEFT -> {
                right = -0.7;
                forward = 0.5;
                up = 0.2;
            }
            case ANKLE_RIGHT -> {
                right = 0.7;
                forward = 0.5;
                up = 0.2;
            }
            case KNEE_LEFT -> {
                right = -0.6;
                forward = 0.35;
                up = 1.15;
            }
            case KNEE_RIGHT -> {
                right = 0.6;
                forward = 0.35;
                up = 1.15;
            }
            case SHOULDER -> {
                right = 0.0;
                forward = 0.55;
                up = 2.65;
            }
            default -> {
                right = 0.0;
                forward = 0.65;
                up = 1.9;
            }
        }
        AttributeInstance scaleAttr = fight.instance().entity().getAttribute(Attribute.SCALE);
        double scale = scaleAttr != null ? scaleAttr.getValue() : 1.5;
        Vector forwardVec = bossLoc.getDirection().setY(0);
        if (forwardVec.lengthSquared() < 1.0E-4) {
            forwardVec = new Vector(1, 0, 0);
        } else {
            forwardVec.normalize();
        }
        Vector rightVec = new Vector(-forwardVec.getZ(), 0, forwardVec.getX());
        Location out = bossLoc.clone()
                .add(rightVec.multiply(right * scale))
                .add(forwardVec.multiply(forward * scale));
        out.setY(bossLoc.getY() + up * scale);
        return out;
    }

    private static Material headFor(Id id) {
        return switch (id) {
            case SHOULDER, CHEST_CORE -> Material.GLOWSTONE;
            default -> Material.GOLD_BLOCK;
        };
    }

    private Component nameFor(Joint joint) {
        String label = switch (joint.id) {
            case ANKLE_LEFT -> "Left Ankle";
            case ANKLE_RIGHT -> "Right Ankle";
            case KNEE_LEFT -> "Left Knee";
            case KNEE_RIGHT -> "Right Knee";
            case SHOULDER -> "Shoulder Core";
            case CHEST_CORE -> "Chest Core";
        };
        int pips = 10;
        int filled = (int) Math.ceil(pips * Math.max(0.0, joint.hp) / joint.maxHp);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < pips; i++) {
            bar.append(i < filled ? '█' : '░');
        }
        NamedTextColor color = joint.broken ? NamedTextColor.DARK_GRAY
                : joint.exposed ? NamedTextColor.YELLOW : NamedTextColor.GRAY;
        return Component.text(label + "  " + bar, color).decoration(TextDecoration.ITALIC, false);
    }

    // ---------------------------------------------------------------- damage

    /**
     * <b>A broken joint is an open wound, not a shield.</b> This used to return early on
     * {@code joint.broken}, and because {@link Handler#onDamage} has already cancelled the marker's own
     * damage by then, the hit reached nothing at all — the swing was simply eaten. The marker also stays
     * live and standing on the boss's own hitbox, so it keeps catching swings after it breaks. In
     * {@code CollapsePhase} that combination is fatal: the chest core is the only joint that phase
     * spawns, its HP is a small fraction of the boss's, and past the break every hit aimed at the
     * Colossus's body either hit the dead marker (zero) or the body itself (cut to
     * {@code body-hit-fraction}) — an unkillable final phase. So a broken joint keeps forwarding at full
     * value and simply stops tracking HP.
     */
    private void resolveHit(Joint joint, double amount, Player attacker) {
        if (amount <= 0 || joint.entity == null) {
            return;
        }
        boolean fullValue = joint.broken || joint.exposed;
        double effective = fullValue ? amount : amount * fight.config().dbl("body-hit-fraction", 0.04);
        Fx.coloredBurst(joint.entity.getLocation().add(0, 0.6, 0),
                fullValue ? ColossusFight.RADIANT_WHITE : ColossusFight.SOLAR_GOLD, 1.2f, 10, 0.3);
        Fx.sound(joint.entity.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.9f, fullValue ? 1.4f : 0.6f);
        if (!joint.broken) {
            joint.hp = Math.max(0, joint.hp - effective);
            if (joint.hp <= 0) {
                joint.broken = true;
                Fx.coloredBurst(joint.entity.getLocation().add(0, 0.8, 0), ColossusFight.CORE_RED, 2.2f, 50, 0.7);
                Fx.sound(joint.entity.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 1.0f, 1.3f);
            }
            joint.entity.customName(nameFor(joint));
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
            Joint hit = null;
            for (Joint joint : joints.values()) {
                if (joint.entity != null && joint.entity.getUniqueId().equals(event.getEntity().getUniqueId())) {
                    hit = joint;
                    break;
                }
            }
            if (hit == null) {
                return;
            }
            // This marker's own health is a red herring — see the comment in #spawn. Every hit is
            // cancelled here and replayed onto the real boss entity through the normal pipeline instead,
            // so BossDamageListener still applies the phase floor, the global multiplier, and — via
            // Joints#forwarding — this phase's own filterDamage rule.
            event.setCancelled(true);
            Player attacker = resolveAttacker(event);
            if (attacker != null) {
                resolveHit(hit, event.getDamage(), attacker);
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

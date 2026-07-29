package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.CooldownManager;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.kit.LungeStrike;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The roped pike: what its lunge connects with comes away on a real vanilla leash, and a second pull hauls
 * everything tethered to the wielder into melee.
 * <p>
 * §0.1, applied to crowd control. Every other "drag the target" ability in the roster is a
 * {@code setVelocity} call with particles drawn along the path — an effect that exists for one tick and
 * cannot be seen, interacted with, or escaped. A leash is the real object doing the same job: it is drawn by
 * the game, it holds continuously, it pulls the mob along as the wielder walks, it stops the mob wandering
 * off, and it has vanilla's own reach limit built in. Delete the particle code here and the ability is
 * unchanged, which is the test §0.1 asks for.
 * <p>
 * Two honest limits, both vanilla's rather than ours: a leash has no effect on players, and a boss that
 * refuses to be leashed just gets yanked instead — see {@link #ability1}.
 */
public final class Tetherpike extends Weapon {

    private static final Color HEMP = Color.fromRGB(196, 168, 104);

    private final double snareDamage;
    private final double contactRadius;
    private final int contactTicks;
    private final int tetherTicks;
    private final double snapDistance;
    private final double reelRadius;
    private final double reelDamage;
    private final double reelSpeed;

    public Tetherpike(WeaponsPlugin plugin) {
        super(plugin);
        this.snareDamage = configDouble("snare-damage", 6.5);
        this.contactRadius = configDouble("contact-radius", 2.2);
        this.contactTicks = configInt("contact-ticks", 12);
        this.tetherTicks = configInt("tether-ticks", 160);
        this.snapDistance = configDouble("snap-distance", 8.0);
        this.reelRadius = configDouble("reel-radius", 12.0);
        this.reelDamage = configDouble("reel-damage", 5.0);
        this.reelSpeed = configDouble("reel-speed", 1.1);
    }

    @Override
    public String id() {
        return "tetherpike";
    }

    @Override
    public Material material() {
        return Material.GOLDEN_SPEAR;
    }

    @Override
    public String displayNameText() {
        return "Tetherpike";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 2.0);
    }

    @Override
    public boolean ability1OnLunge() {
        return true;
    }

    @Override
    public int lungePowerBonus() {
        return configInt("lunge-power-bonus", 1);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 7.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 5.0);
    }

    @Override
    public String ability1Name() {
        return "Snare";
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Hold right-click, then release to", NamedTextColor.GRAY),
                Component.text("lunge. What you reach is roped to", NamedTextColor.GRAY),
                Component.text("you on a real leash and dragged", NamedTextColor.GRAY),
                Component.text("wherever you walk.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Reel";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: haul everything", NamedTextColor.GRAY),
                Component.text("still roped to you into melee, then", NamedTextColor.GRAY),
                Component.text("cut the lines.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_SPEAR_LUNGE_1;
    }

    @Override
    public Sound hitSound() {
        return Sound.ITEM_SPEAR_HIT;
    }

    @Override
    public Sound readySound() {
        return Sound.ITEM_LEAD_TIED;
    }

    @Override
    public Map<CooldownManager.Slot, Double> damageProfile() {
        return Map.of(CooldownManager.Slot.ABILITY1, snareDamage,
                CooldownManager.Slot.ABILITY2, reelDamage);
    }

    /**
     * <b>Counterplay.</b> A wielder roped to something heavy is a wielder who is hard to move. The tether is
     * the answer to a shove phase: keep something anchored on the line and the arena's attempts to relocate
     * you have something to argue with.
     */
    @Override
    public Set<CounterVerb> counterVerbs() {
        return Set.of(CounterVerb.DISPLACEMENT);
    }

    @Override
    public void ability1(Player player) {
        double damage = snareDamage * rarity().statMultiplier();

        LungeStrike.onFirstContact(plugin, player, contactRadius, contactTicks, (victim, at) -> {
            victim.damage(damage, player);
            Fx.bloodSpray(at.clone().add(0, 1, 0));
            Fx.coloredBurst(at.clone().add(0, 1, 0), HEMP, 1.0f, 12, 0.35);

            if (victim.setLeashHolder(player)) {
                Fx.sound(at, Sound.ITEM_LEAD_TIED, 1.0f, 1.0f);
                holdTether(player, victim);
                return;
            }
            // Vanilla refuses the leash on players and on anything not built to be led, which includes
            // every boss. Rather than have the signature ability do nothing against the fights it exists
            // for, the rope becomes a single hard pull — one shove, not a hold.
            Vector pull = player.getLocation().toVector().subtract(at.toVector());
            if (pull.lengthSquared() > 1.0e-4) {
                victim.setVelocity(pull.normalize().multiply(reelSpeed * 0.6).setY(0.25));
            }
            Fx.sound(at, Sound.ITEM_LEAD_UNTIED, 1.0f, 0.9f);
        });
    }

    /**
     * Holds the tether for its window and takes it off cleanly at the end.
     * <p>
     * <b>Why this watchdog exists at all: a leash that snaps drops a real lead.</b> Vanilla breaks a leash
     * stretched past its limit and pays the player an item for it, which would make this ability an infinite
     * lead farm on a 7-second cooldown. Unleashing through the API drops nothing, so the tether is released
     * by hand at {@link #snapDistance} — comfortably inside vanilla's own limit — and the sweep below clears
     * any lead that still managed to hit the ground (a leashed mob dying is the other way one appears).
     */
    private void holdTether(Player holder, LivingEntity victim) {
        new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                boolean stillOurs = victim.isValid() && !victim.isDead() && victim.isLeashed()
                        && victim.getLeashHolder().equals(holder);
                if (!stillOurs || !holder.isOnline() || elapsed >= tetherTicks
                        || victim.getLocation().distance(holder.getLocation()) > snapDistance) {
                    cancel();
                    release(victim);
                    return;
                }
                Fx.coloredBurst(victim.getLocation().add(0, 1, 0), HEMP, 0.6f, 1, 0.1);
                elapsed += 4;
            }
        }.runTaskTimer(plugin, 4L, 4L);
    }

    /** Takes the rope off without paying anyone a lead for it. */
    private void release(LivingEntity victim) {
        if (!victim.isValid()) {
            return;
        }
        Location at = victim.getLocation();
        if (victim.isLeashed()) {
            victim.setLeashHolder(null);
            Fx.sound(at, Sound.ITEM_LEAD_UNTIED, 0.8f, 1.1f);
        }
        sweepDroppedLeads(at);
    }

    /** Removes any lead that appeared where a tether came off in the last few ticks. */
    private void sweepDroppedLeads(Location at) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Entity nearby : at.getWorld().getNearbyEntities(at, 2.5, 2.5, 2.5)) {
                    if (nearby instanceof Item item && item.getItemStack().getType() == Material.LEAD
                            && item.getTicksLived() <= 10) {
                        item.remove();
                    }
                }
            }
        }.runTaskLater(plugin, 2L);
    }

    @Override
    public void ability2(Player player) {
        double damage = reelDamage * rarity().statMultiplier();
        int hauled = 0;

        for (Entity nearby : player.getNearbyEntities(reelRadius, reelRadius, reelRadius)) {
            if (!(nearby instanceof LivingEntity living) || !living.isLeashed()
                    || !living.getLeashHolder().equals(player)) {
                continue;
            }
            Vector pull = player.getLocation().toVector().subtract(living.getLocation().toVector());
            if (pull.lengthSquared() > 1.0e-4) {
                living.setVelocity(pull.normalize().multiply(reelSpeed).setY(0.35));
            }
            living.damage(damage, player);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            release(living);
            hauled++;
        }

        Fx.sound(player, hauled > 0 ? Sound.ITEM_LEAD_UNTIED : Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
        if (hauled > 0) {
            Fx.coloredRing(player.getLocation().add(0, 0.3, 0), HEMP, 1.1f, 1.8, 22, 0);
        }
    }
}

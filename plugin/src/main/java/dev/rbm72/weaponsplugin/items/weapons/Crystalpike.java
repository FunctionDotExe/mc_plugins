package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
import dev.rbm72.weaponsplugin.ability.CooldownManager;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.SpearWeapon;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.kit.Props;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The mythic pike: it nails real end crystals into the ground, and then it sets them off.
 * <p>
 * An end crystal is the most physically present object vanilla has that a player can place at will. It hovers,
 * it hums, it draws a beam, it is targetable, it can be shot out of the air by anyone — the wielder, a
 * teammate, a skeleton — and when it goes it produces one of the largest explosions in the game. §0.1 asks for
 * exactly this kind of object in place of a particle-and-{@code damage()} pairing: the crystals are things
 * standing in the arena between the cast and the payoff, and everyone in the fight can see them and act on
 * them.
 * <p>
 * <b>The crystals are the mechanic, not the decoration.</b> Pinning one costs nothing by itself; the damage
 * only arrives when something breaks it. That is the skill the weapon asks for — pin, reposition, then
 * shatter — and it is also its counterplay, because a pinned crystal is a several-second warning that anything
 * with a ranged attack can pop early, in the wielder's face.
 * <p>
 * Block damage is off on every blast (the explosions are real, the craters are not) and the wielder is excluded
 * from their own detonations: the ultimate rings them at close range deliberately, and a real end-crystal blast
 * at that distance is not a risk, it is a suicide. Everything else standing there — allies included — takes it
 * in full.
 */
public final class Crystalpike extends SpearWeapon {

    private static final Color END_VIOLET = Color.fromRGB(196, 108, 226);

    private final double pinDamage;
    private final int crystalLifetimeTicks;
    private final double shatterRadius;
    private final double shatterPower;
    private final int shatterIntervalTicks;
    private final int crownCrystals;
    private final double crownRadius;
    private final int crownTelegraphTicks;
    private final double crownPower;

    public Crystalpike(WeaponsPlugin plugin) {
        super(plugin);
        this.pinDamage = configDouble("pin-damage", 9.0);
        this.crystalLifetimeTicks = configInt("crystal-lifetime-ticks", 140);
        this.shatterRadius = configDouble("shatter-radius", 16.0);
        this.shatterPower = configDouble("shatter-power", 2.6);
        this.shatterIntervalTicks = configInt("shatter-interval-ticks", 3);
        this.crownCrystals = configInt("crown-crystals", 5);
        this.crownRadius = configDouble("crown-radius", 4.0);
        this.crownTelegraphTicks = configInt("crown-telegraph-ticks", 24);
        this.crownPower = configDouble("crown-power", 3.2);
    }

    @Override
    public String id() {
        return "crystalpike";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_SPEAR;
    }

    @Override
    public String displayNameText() {
        return "Crystalpike";
    }

    @Override
    public Rarity rarity() {
        return Rarity.MYTHIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 4.5);
    }

    @Override
    public int lungePowerBonus() {
        return configInt("lunge-power-bonus", 2);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 6.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 4.0);
    }

    @Override
    public String ability1Name() {
        return "Crystal Pin";
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Hold right-click and run something", NamedTextColor.GRAY),
                Component.text("down. A real end crystal is nailed", NamedTextColor.GRAY),
                Component.text("in beside whatever you skewer —", NamedTextColor.GRAY),
                Component.text("anything can shoot it, including them.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Shatter";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: break every crystal", NamedTextColor.GRAY),
                Component.text("you have pinned, one after another.", NamedTextColor.GRAY),
                Component.text("Real blasts, no craters, and you are", NamedTextColor.GRAY),
                Component.text("the only one they spare.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Crown of Nails";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Sneak+off-hand: ring yourself in", NamedTextColor.GRAY),
                Component.text("crystals, then bring them all down", NamedTextColor.GRAY),
                Component.text("at once.", NamedTextColor.GRAY));
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Resonance")
                .accent(END_VIOLET)
                .perMeleeHit(configDouble("resonance-per-hit", 6.0))
                .perDamageDealt(configDouble("resonance-per-damage-dealt", 0.45))
                .perAbilityCast(configDouble("resonance-per-ability", 10.0))
                .perKill(configDouble("resonance-per-kill", 14.0))
                .decay(configDouble("resonance-decay-per-second", 2.0), configDouble("resonance-decay-grace", 7.0))
                .cooldownFloor(configDouble("resonance-cooldown-floor", 60.0))
                .build();
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_SPEAR_LUNGE_3;
    }

    @Override
    public Sound hitSound() {
        return Sound.ITEM_SPEAR_HIT;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_END_PORTAL_FRAME_FILL;
    }

    @Override
    public Map<CooldownManager.Slot, Double> damageProfile() {
        // Explosion power is not damage, so the figures here are the pin plus what one vanilla blast of this
        // power lands on a target at its centre — roughly 7 per point of power at point blank.
        return Map.of(CooldownManager.Slot.ABILITY1, pinDamage,
                CooldownManager.Slot.ABILITY2, shatterPower * 7,
                CooldownManager.Slot.ULTIMATE, crownPower * 7 * 2);
    }

    @Override
    public void ability1(Player player, LivingEntity victim) {
        double damage = pinDamage * rarity().statMultiplier();
        Location at = victim.getLocation();

        victim.damage(damage, player);
        Fx.bloodSpray(at.clone().add(0, 1, 0));
        Fx.sound(at, hitSound(), 1.0f, 0.9f);
        pin(at);
    }

    @Override
    public void ability2(Player player) {
        List<EnderCrystal> mine = pinned(player.getLocation(), shatterRadius);
        if (mine.isEmpty()) {
            Fx.sound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            return;
        }
        detonate(player, mine, shatterPower, shatterIntervalTicks);
    }

    @Override
    public void ultimate(Player player) {
        Location centre = player.getLocation();
        List<EnderCrystal> crown = new ArrayList<>();

        for (int i = 0; i < crownCrystals; i++) {
            double angle = Math.PI * 2 * i / crownCrystals;
            Location spot = centre.clone().add(Math.cos(angle) * crownRadius, 0, Math.sin(angle) * crownRadius);
            crown.add(pin(spot));
        }
        Fx.coloredRing(centre.clone().add(0, 0.2, 0), END_VIOLET, 1.4f, crownRadius, 36, 0);

        // The ring stands for its telegraph before it goes: a mythic finisher that fires instantly is one
        // nobody in the fight — including the wielder's allies — gets to react to.
        new BukkitRunnable() {
            @Override
            public void run() {
                detonate(player, crown, crownPower, shatterIntervalTicks);
            }
        }.runTaskLater(plugin, Math.max(1, crownTelegraphTicks));
    }

    /** Nails one real crystal into the ground at {@code at}, on a lifetime so an unused one is never permanent. */
    private EnderCrystal pin(Location at) {
        EnderCrystal crystal = Props.crystal(plugin, this, at.clone().add(0, 0.1, 0), crystalLifetimeTicks);
        Fx.sound(at, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0f, 1.2f);
        Fx.coloredBurst(at.clone().add(0, 1, 0), END_VIOLET, 1.2f, 16, 0.4);
        return crystal;
    }

    /**
     * Every crystal this weapon pinned within {@code radius}, found by its own marker rather than by a map.
     * <p>
     * {@link Props#tag} already stamps the weapon id on anything the kit spawns, so the world is the
     * bookkeeping: no static registry to leak, nothing to clean up on quit, and a crystal that some other
     * weapon or a boss placed is never mistaken for ours.
     */
    private List<EnderCrystal> pinned(Location around, double radius) {
        List<EnderCrystal> found = new ArrayList<>();
        for (Entity nearby : around.getWorld().getNearbyEntities(around, radius, radius, radius)) {
            if (!(nearby instanceof EnderCrystal crystal) || !crystal.isValid()) {
                continue;
            }
            String owner = crystal.getPersistentDataContainer().get(Weapon.idKey(plugin), PersistentDataType.STRING);
            if (id().equals(owner)) {
                found.add(crystal);
            }
        }
        return found;
    }

    /**
     * Walks the list and turns each crystal into a real explosion.
     * <p>
     * Removing the entity and calling {@code createExplosion} ourselves rather than damaging the crystal into
     * detonating: {@link EnderCrystal} is not {@code Damageable} in the API, so there is no honest way to ask
     * it to blow itself up. The explosion is still vanilla's own — real knockback, real falloff, real damage
     * to real armour — with blocks and fire off and the wielder excluded.
     */
    private void detonate(Player caster, List<EnderCrystal> crystals, double power, int intervalTicks) {
        new BukkitRunnable() {
            int index;

            @Override
            public void run() {
                if (index >= crystals.size()) {
                    cancel();
                    return;
                }
                EnderCrystal crystal = crystals.get(index++);
                if (!crystal.isValid()) {
                    return;
                }
                Location at = crystal.getLocation();
                crystal.remove();
                at.getWorld().createExplosion(caster, at, (float) power, false, false, true);
                Fx.coloredBurst(at, END_VIOLET, 1.6f, 24, 0.6);
            }
        }.runTaskTimer(plugin, 0L, Math.max(1, intervalTicks));
    }
}

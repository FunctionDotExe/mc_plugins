package dev.rbm72.weaponsplugin.boss.events;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossEvent;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Court Judgment: the king passes sentence on whoever is closest to death, and his guard carry it out.
 * The condemned player is marked, a detachment spawns, and the sentence lands when the timer expires —
 * unless every guard is dead before then, which breaks the court and staggers the king.
 * <p>
 * The reason it targets the <em>lowest health</em> player rather than a random one is what makes it
 * work: it deliberately kicks the person already having the worst time, so the group's healthiest
 * players are the ones who have to drop everything and save them. It also inverts the usual add-clear
 * instinct — the guards are not in your way, they are a countdown, and killing them is the only thing
 * that matters for those few seconds.
 * <p>
 * The condemned can help but cannot save themselves: guards are tuned so one player cannot clear them
 * alone in the window. Solo, the sentence lands, which is survivable but expensive.
 */
public final class CourtJudgmentEvent extends BossEvent {

    private static final Color JUDGMENT_GOLD = Color.fromRGB(255, 215, 90);

    private final double[] triggers;
    private final int guardCount;
    private final double guardHealth;
    private final int windowTicks;
    private final double executeDamage;
    private final int staggerTicks;

    private final List<LivingEntity> guards = new ArrayList<>();
    private BukkitTask task;
    private Player condemned;
    private Runnable onComplete;
    private boolean resolved;

    public CourtJudgmentEvent(WeaponsPlugin plugin, String bossId, double[] triggers) {
        super(plugin, bossId);
        this.triggers = triggers.clone();
        this.guardCount = configInt("judgment-guard-count", 3);
        this.guardHealth = configDouble("judgment-guard-health", 30.0);
        this.windowTicks = configInt("judgment-window-ticks", 160);
        this.executeDamage = configDouble("judgment-execute-damage", 40.0);
        this.staggerTicks = configInt("judgment-stagger-ticks", 70);
    }

    @Override
    public String id() {
        return "court_judgment";
    }

    @Override
    public double[] triggerFractions() {
        return triggers.clone();
    }

    @Override
    public void run(BossInstance instance, Runnable onComplete) {
        this.onComplete = onComplete;
        this.resolved = false;
        guards.clear();

        List<Player> present = Arena.combatants(instance.entity().getLocation(), instance.arena().radius());
        if (present.isEmpty()) {
            finish(instance);
            return;
        }
        condemned = present.stream()
                .min((a, b) -> Double.compare(a.getHealth(), b.getHealth()))
                .orElse(present.get(0));

        instance.setForcedInvulnerable(true);
        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1.6, 0), JUDGMENT_GOLD, 2.4f, 60, 0.9);
        Fx.sound(loc, Sound.ENTITY_WITHER_AMBIENT, 1.3f, 0.5f);
        Fx.sound(loc, Sound.BLOCK_BELL_USE, 1.4f, 0.7f);
        instance.showTitle(
                Component.text("COURT JUDGMENT", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text(condemned.getName() + " is condemned — kill the guard", NamedTextColor.GRAY));

        spawnGuards(instance);

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks;

            @Override
            public void run() {
                if (resolved) {
                    return;
                }
                if (!instance.entity().isValid()) {
                    finish(instance);
                    return;
                }
                boolean anyAlive = guards.stream().anyMatch(g -> g.isValid() && !g.isDead());
                if (!anyAlive) {
                    interrupted(instance);
                    return;
                }
                if (condemned.isOnline() && condemned.isValid()) {
                    // A beam of light on the condemned so the group can find them instantly, and a
                    // tether from the king so it is obvious who is being sentenced and by whom.
                    Fx.line(instance.entity().getLocation().add(0, 1.4, 0),
                            condemned.getLocation().add(0, 1, 0), Particle.WAX_OFF, 16);
                    condemned.getWorld().spawnParticle(Particle.END_ROD,
                            condemned.getLocation().add(0, 2.4, 0), 4, 0.15, 0.4, 0.15, 0);
                }
                if (ticks % 4 == 0) {
                    announce(instance, Math.max(0, (windowTicks - ticks) / 20));
                }
                ticks++;
                if (ticks >= windowTicks) {
                    execute(instance);
                }
            }
        }, 1L, 1L);
        instance.trackTask(task);
    }

    private void spawnGuards(BossInstance instance) {
        Location center = instance.entity().getLocation();
        for (int i = 0; i < guardCount; i++) {
            double angle = 2 * Math.PI * i / guardCount;
            Location spot = center.clone().add(Math.cos(angle) * 3.0, 0, Math.sin(angle) * 3.0);
            Fx.coloredBurst(spot.clone().add(0, 1, 0), JUDGMENT_GOLD, 1.4f, 20, 0.4);
            LivingEntity guard = instance.addManager().spawn(spot.getWorld(), spot, EntityType.VINDICATOR, entity -> {
                entity.customName(Component.text("Executioner", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
                entity.setCustomNameVisible(true);
                var attr = entity.getAttribute(Attribute.MAX_HEALTH);
                if (attr != null) {
                    attr.setBaseValue(guardHealth);
                    entity.setHealth(guardHealth);
                }
                EntityEquipment equipment = entity.getEquipment();
                if (equipment != null) {
                    equipment.setItemInMainHand(new ItemStack(Material.IRON_AXE));
                    equipment.setItemInMainHandDropChance(0f);
                }
                // They hunt the condemned specifically — the threat has to look like it is coming for
                // that player, not milling about near whoever happens to be closest.
                if (entity instanceof Mob mob && condemned != null) {
                    mob.setTarget(condemned);
                }
            });
            if (guard != null) {
                guards.add(guard);
            }
        }
    }

    /**
     * Per player, because this mechanic genuinely says two different things at the same instant: the
     * condemned needs to know they are the one about to be executed, and everybody else needs to know
     * that killing the executioners is what saves them.
     */
    private void announce(BossInstance instance, int secondsLeft) {
        int alive = (int) guards.stream().filter(g -> g.isValid() && !g.isDead()).count();
        double progress = alive / (double) Math.max(1, guardCount);
        String condemnedName = condemned == null ? "your ally" : condemned.getName();
        instance.mechanicBar().update(MechanicBar.Owner.EVENT, instance.barViewers(), player -> {
            if (player.equals(condemned)) {
                return MechanicBar.Readout.of(
                        Component.text("YOU ARE CONDEMNED  ", NamedTextColor.RED)
                                .append(Component.text(alive + " executioners left", NamedTextColor.WHITE))
                                .append(Component.text("   " + secondsLeft + "s", NamedTextColor.GRAY)),
                        progress, BossBar.Color.RED);
            }
            return MechanicBar.Readout.of(
                    Component.text("COURT JUDGMENT  ", NamedTextColor.GOLD)
                            .append(Component.text("kill " + alive + " executioner(s)", NamedTextColor.WHITE))
                            .append(Component.text("   to save " + condemnedName + "   " + secondsLeft + "s",
                                    NamedTextColor.GRAY)),
                    progress, BossBar.Color.YELLOW);
        });
    }

    private void interrupted(BossInstance instance) {
        if (resolved) {
            return;
        }
        resolved = true;
        instance.setForcedInvulnerable(false);
        instance.recordExposure();
        instance.stagger(staggerTicks);

        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1.4, 0), JUDGMENT_GOLD, 2.4f, 60, 0.9);
        Fx.flash(loc.clone().add(0, 1.4, 0), 2);
        Fx.sound(loc, Sound.BLOCK_BELL_RESONATE, 1.4f, 1.4f);
        instance.showTitle(
                Component.text("THE COURT IS BROKEN", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("The sentence goes unserved", NamedTextColor.GRAY));
        finish(instance);
    }

    private void execute(BossInstance instance) {
        if (resolved) {
            return;
        }
        resolved = true;
        instance.setForcedInvulnerable(false);
        if (condemned != null && condemned.isOnline() && condemned.isValid()) {
            condemned.damage(executeDamage, instance.entity());
            Location at = condemned.getLocation();
            Fx.coloredBurst(at.clone().add(0, 1, 0), JUDGMENT_GOLD, 2.6f, 60, 0.7);
            Fx.flash(at.clone().add(0, 1, 0), 2);
            Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.8f);
        }
        instance.showTitle(
                Component.text("SENTENCE CARRIED OUT", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                Component.text("The court was not broken in time", NamedTextColor.GRAY));
        finish(instance);
    }

    private void finish(BossInstance instance) {
        cleanup(instance);
        Runnable resume = onComplete;
        onComplete = null;
        if (resume != null) {
            resume.run();
        }
    }

    @Override
    public void cleanup(BossInstance instance) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = null;
        for (LivingEntity guard : guards) {
            if (guard.isValid()) {
                guard.remove();
            }
        }
        guards.clear();
        condemned = null;
        instance.mechanicBar().release(MechanicBar.Owner.EVENT);
        instance.setForcedInvulnerable(false);
    }
}

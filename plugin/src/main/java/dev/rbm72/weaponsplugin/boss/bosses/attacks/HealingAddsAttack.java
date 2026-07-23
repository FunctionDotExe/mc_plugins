package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AddManager;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Summons a ring of healer adds that channel life back into the boss every second they're left
 * alive — ignore them and the boss claws its health back faster than a group can push it, so they
 * have to be cleared. Reusable across bosses via the {@code bossId} constructor param; the add's
 * entity type, display name and theme colour are supplied per boss (Royal Clerics, Sun Acolytes,
 * Abyssal Priests, whatever fits). A "kill the healers" threat, deliberately NOT a phase-floor gate
 * — that's what each boss's signature is for; this is a soft time-pressure that stacks on top.
 */
public final class HealingAddsAttack extends BossAttack {

    private final String addName;
    private final EntityType addType;
    private final Color color;

    private final int telegraphTicks;
    private final int addCount;
    private final double addHealth;
    private final double healPerSecond;
    private final int durationTicks;

    public HealingAddsAttack(WeaponsPlugin plugin, String bossId, String addName, EntityType addType, Color color) {
        super(plugin, bossId);
        this.addName = addName;
        this.addType = addType;
        this.color = color;
        this.telegraphTicks = configInt("healing-adds-telegraph-ticks", 30);
        this.addCount = configInt("healing-adds-count", 2);
        this.addHealth = configDouble("healing-adds-health", 40.0);
        this.healPerSecond = configDouble("healing-adds-heal-per-second", 12.0);
        this.durationTicks = configInt("healing-adds-duration-ticks", 200);
    }

    @Override
    public String name() {
        return "Summon " + addName + "s";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("healing-adds-cooldown-seconds", 40.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(ctx.bossLocation(), color, 1.4f, 3.0, 20, 0);
                    Fx.sound(ctx.bossLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 0.8f);
                },
                () -> {
                    AddManager adds = ctx.instance().addManager();
                    List<LivingEntity> healers = new ArrayList<>(addCount);
                    for (int i = 0; i < addCount; i++) {
                        double angle = 2 * Math.PI * i / addCount;
                        Location spot = ctx.bossLocation().clone().add(Math.cos(angle) * 4.0, 0, Math.sin(angle) * 4.0);
                        Fx.coloredBurst(spot.clone().add(0, 1, 0), color, 1.4f, 22, 0.4);
                        LivingEntity healer = adds.spawn(spot.getWorld(), spot, addType, entity -> {
                            entity.customName(Component.text(addName, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                            entity.setCustomNameVisible(true);
                            var maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
                            if (maxHealthAttr != null) {
                                maxHealthAttr.setBaseValue(addHealth);
                                entity.setHealth(addHealth);
                            }
                            if (entity instanceof Mob mob) {
                                mob.setTarget(ctx.target());
                            }
                        });
                        healers.add(healer);
                    }

                    ctx.instance().showTitle(
                            Component.text(addName.toUpperCase(java.util.Locale.ROOT) + "S RISE", NamedTextColor.GREEN)
                                    .decoration(TextDecoration.BOLD, true),
                            Component.text("Cut down the healers before they mend it", NamedTextColor.GRAY));
                    BossAudio.play(ctx.bossLocation(), "boss.healing_adds", Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.9f);

                    double perTickHeal = healPerSecond;
                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= durationTicks || !ctx.boss().isValid()) {
                                cleanup();
                                cancel();
                                return;
                            }
                            long alive = healers.stream().filter(h -> h.isValid() && !h.isDead()).count();
                            if (alive == 0) {
                                cancel();
                                return;
                            }
                            LivingEntity boss = ctx.boss();
                            var maxAttr = boss.getAttribute(Attribute.MAX_HEALTH);
                            double max = maxAttr != null ? maxAttr.getValue() : boss.getHealth();
                            double healed = Math.min(max, boss.getHealth() + perTickHeal * alive);
                            boss.setHealth(healed);
                            Location bossLoc = boss.getLocation();
                            for (LivingEntity h : healers) {
                                if (h.isValid() && !h.isDead()) {
                                    Fx.line(h.getLocation().add(0, 1, 0), bossLoc.clone().add(0, 1, 0), Particle.HEART, 8);
                                }
                            }
                            Fx.coloredBurst(bossLoc.clone().add(0, 1.4, 0), color, 1.2f, 10, 0.4);
                            ticks += 20;
                        }

                        private void cleanup() {
                            for (LivingEntity h : healers) {
                                if (h.isValid()) {
                                    h.remove();
                                }
                            }
                        }
                    }.runTaskTimer(plugin, 20L, 20L);
                },
                0, onComplete);
    }
}

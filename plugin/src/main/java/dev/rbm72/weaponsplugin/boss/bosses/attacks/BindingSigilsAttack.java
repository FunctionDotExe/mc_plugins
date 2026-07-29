package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.props.ArenaTotem;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Real interrupt objective for the "Dark Awakening" phase: three cursed sigils drop around the
 * arena, each bleeding an expanding pool of corruption that damages anyone who lingers near it.
 * Left standing for the whole channel, the king draws on all three and comes out hitting harder
 * for the rest of the fight. Break even one and the ritual is cut short — he's staggered
 * (weakened, briefly) instead. Replaces the old Shadow Clones add-spam, which was just a weaker
 * reskin of Summon Royal Guards with no mechanic of its own.
 */
public final class BindingSigilsAttack extends BossAttack {

    private static final Color CURSE = Color.fromRGB(90, 10, 130);

    private final int sigilCount;
    private final double sigilHealth;
    private final int channelDurationTicks;
    private final double poolDamagePerPulse;
    private final double poolRadius;
    private final double empowerDamageBonus;
    private final int empowerDurationTicks;
    private final int staggerDurationTicks;
    private final int telegraphTicks;

    public BindingSigilsAttack(WeaponsPlugin plugin) {
        super(plugin, "fallen_king");
        this.sigilCount = configInt("binding-sigils-count", 3);
        this.sigilHealth = configDouble("binding-sigils-health", 26.0);
        this.channelDurationTicks = configInt("binding-sigils-channel-ticks", 140);
        this.poolDamagePerPulse = configDouble("binding-sigils-pool-damage", 3.0);
        this.poolRadius = configDouble("binding-sigils-pool-radius", 2.5);
        this.empowerDamageBonus = configDouble("binding-sigils-empower-damage-bonus", 4.0);
        this.empowerDurationTicks = configInt("binding-sigils-empower-duration-ticks", 400);
        this.staggerDurationTicks = configInt("binding-sigils-stagger-duration-ticks", 60);
        this.telegraphTicks = configInt("binding-sigils-telegraph-ticks", 22);
    }

    @Override
    public String name() {
        return "Binding Sigils";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("binding-sigils-cooldown-seconds", 28.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.arena().center();
        double radius = ctx.arena().radius();

        sequence(telegraphTicks,
                () -> {
                    Telegraph.groundRing(center, radius * 0.65, Particle.SOUL);
                    Fx.coloredRing(ctx.bossLocation(), CURSE, 1.6f, 3.0, 24, 0);
                },
                () -> {
                    ctx.instance().showTitle(
                            Component.text("Binding Sigils", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                            Component.text("Break one to stop the ritual", NamedTextColor.GRAY));
                    BossAudio.play(center, "boss.fallen_king.binding_sigils", Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.1f, 0.6f);

                    List<ArenaTotem> sigils = new ArrayList<>(sigilCount);
                    for (int i = 0; i < sigilCount; i++) {
                        double angle = 2 * Math.PI * i / sigilCount;
                        Location spot = center.clone().add(Math.cos(angle) * radius * 0.6, 0, Math.sin(angle) * radius * 0.6);
                        spot.setY(center.getY());
                        Fx.coloredBurst(spot.clone().add(0, 1, 0), CURSE, 1.6f, 26, 0.5);
                        Fx.burst(spot.clone().add(0, 1, 0), Particle.SQUID_INK, 18, 0.4);
                        ArenaTotem sigil = ArenaTotem.spawn(plugin, ctx.instance(), spot, Material.SOUL_LANTERN,
                                Component.text("Binding Sigil", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false),
                                sigilHealth, channelDurationTicks,
                                destroyed -> onSigilBroken(ctx),
                                expired -> { });
                        sigils.add(sigil);
                    }

                    new BukkitRunnable() {
                        int ticks = 0;
                        boolean interrupted = false;

                        @Override
                        public void run() {
                            long aliveCount = sigils.stream().filter(ArenaTotem::isValid).count();
                            if (aliveCount == 0) {
                                interrupted = true;
                            }
                            if (ticks >= channelDurationTicks || !ctx.boss().isValid() || interrupted) {
                                if (interrupted && ctx.boss().isValid()) {
                                    onInterrupted(ctx, staggerDurationTicks);
                                } else if (!interrupted && aliveCount > 0) {
                                    empowerKing(ctx);
                                }
                                cancel();
                                return;
                            }
                            // Each standing sigil bleeds a growing pool of corruption — a reason to close
                            // distance and fight near it instead of kiting at range for the whole channel.
                            if (ticks % 20 == 0) {
                                for (ArenaTotem sigil : sigils) {
                                    if (!sigil.isValid()) {
                                        continue;
                                    }
                                    double pulseRadius = poolRadius * (1.0 + ticks / (double) channelDurationTicks);
                                    Telegraph.dangerZone(sigil.location(), pulseRadius);
                                    for (Player player : ctx.arena().playersInside()) {
                                        if (player.getLocation().distanceSquared(sigil.location()) <= pulseRadius * pulseRadius) {
                                            tickHurt(ctx, player, poolDamagePerPulse);
                                        }
                                    }
                                }
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                10, onComplete);
    }

    private void onSigilBroken(AttackContext ctx) {
        Fx.sound(ctx.bossLocation(), Sound.ENTITY_ILLUSIONER_HURT, 1.0f, 0.8f);
    }

    /** All three sigils survived: a real, lasting reward — the king hits harder for the rest of the fight. */
    private void empowerKing(AttackContext ctx) {
        NamespacedKey key = new NamespacedKey(plugin, "binding_sigils_empower");
        AttributeInstance damageAttr = ctx.boss().getAttribute(Attribute.ATTACK_DAMAGE);
        if (damageAttr != null && damageAttr.getModifiers().stream().noneMatch(m -> m.getKey().equals(key))) {
            damageAttr.addModifier(new AttributeModifier(key, empowerDamageBonus,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (ctx.boss().isValid()) {
                    AttributeInstance attr = ctx.boss().getAttribute(Attribute.ATTACK_DAMAGE);
                    if (attr != null) {
                        attr.removeModifier(key);
                    }
                }
            }, empowerDurationTicks);
        }
        Location loc = ctx.bossLocation();
        Fx.coloredBurst(loc.add(0, 1.2, 0), CURSE, 2.2f, 60, 0.8);
        Fx.sound(loc, Sound.ENTITY_WITHER_AMBIENT, 1.3f, 0.5f);
        ctx.instance().showTitle(
                Component.text("The Ritual Completes", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true),
                Component.text("The king strikes harder", NamedTextColor.GRAY));
    }

    /** At least one sigil broke in time: the ritual is cut short and the king is staggered. */
    private static void onInterrupted(AttackContext ctx, int staggerDurationTicks) {
        ctx.boss().addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, staggerDurationTicks, 1));
        ctx.boss().addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, staggerDurationTicks, 1));
        Fx.coloredBurst(ctx.bossLocation().add(0, 1.2, 0), Color.fromRGB(230, 230, 230), 1.6f, 40, 0.6);
        Fx.sound(ctx.bossLocation(), Sound.ENTITY_WITHER_HURT, 1.2f, 1.1f);
        ctx.instance().showTitle(
                Component.text("Ritual Interrupted", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("The king staggers, weakened", NamedTextColor.GRAY));
    }
}

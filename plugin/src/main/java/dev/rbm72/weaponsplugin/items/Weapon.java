package dev.rbm72.weaponsplugin.items;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.util.TooltipFrame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Base type every weapon extends. Owns all the shared, easy-to-get-wrong
 * mechanics (item construction, identification, rarity scaling, lore
 * assembly) so each concrete weapon only has to describe itself and
 * implement whichever ability slots and passive hooks it uses. Ability2,
 * ability3, ultimate, and every passive hook default to no-ops so weapons
 * that only use ability1 need not override anything else.
 */
public abstract class Weapon {

    private static final String WEAPON_ID_KEY = "weapon_id";

    protected final WeaponsPlugin plugin;

    protected Weapon(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    public abstract String id();

    public abstract Material material();

    public abstract String displayNameText();

    public abstract Rarity rarity();

    /** Flat melee damage bonus before rarity scaling. */
    public abstract double baseMeleeDamage();

    /** Right-click, main hand, not sneaking. */
    public abstract void ability1(Player player);

    /**
     * Ability1 for weapons whose cast already knows what it connected with.
     * <p>
     * Only the spear family reaches this overload: a spear's ability1 fires from a landed charge attack
     * ({@link #ability1OnChargeAttack()}), and vanilla hands the struck entity over as part of that hit, so
     * there is nothing to go looking for. Everything else keeps the plain {@code ability1(Player)} contract
     * and this default forwards to it.
     */
    public void ability1(Player player, LivingEntity contact) {
        ability1(player);
    }

    public abstract double ability1CooldownSeconds();

    public abstract List<Component> ability1Lore();

    /** Short display name shown as the tooltip header for each ability. Empty = no header. */
    public String ability1Name() {
        return "";
    }

    public String ability2Name() {
        return "";
    }

    public String ability3Name() {
        return "";
    }

    public String ultimateName() {
        return "";
    }

    /**
     * True when ability1 fires from the vanilla spear charge attack instead of from a bare right-click.
     * <p>
     * Spears are the one material in the game whose right-click is already a mechanic. Holding it runs
     * {@code Item.use} into the item's {@code KineticWeapon} component: the spear drops into an attack
     * position and hits whatever the player's own momentum carries it through, damage scaling off velocity
     * rather than off a swing timer. §0.1 says name the real Minecraft object rather than simulate it, and
     * the real object here is that charge attack — so a spear weapon leaves it alone and hangs its ability
     * on the connect, via {@link dev.rbm72.weaponsplugin.listeners.WeaponChargeListener}. The interact
     * listener sees this flag and stops cancelling the main-hand right-click, which is what lets the charge
     * start at all: cancelling {@link org.bukkit.event.player.PlayerInteractEvent} cancels {@code Item.use},
     * and {@code Item.use} <em>is</em> the charge attack.
     * <p>
     * The other three slots are unaffected: sneak, off-hand and sneak-off-hand still route through
     * {@code WeaponInteractListener} exactly as they do for a sword.
     */
    public boolean ability1OnChargeAttack() {
        return false;
    }

    /**
     * True when ability1 fires from a released bow/crossbow shot instead of from a bare right-click.
     * <p>
     * The same trap the spear family walked into, one material over. A bow's right-click <em>is</em> the
     * draw, so {@link WeaponInteractListener} cancelling {@link org.bukkit.event.player.PlayerInteractEvent}
     * to claim the button denied the draw outright: every custom bow in the roster could cast its three
     * abilities and could not fire a single arrow, which left it useless the moment all three were on
     * cooldown. §0.1 says the real Minecraft object comes first, and for a bow the real object is the
     * nocked arrow.
     * <p>
     * So the release is the cast, exactly as the connect is for a spear — see
     * {@link dev.rbm72.weaponsplugin.listeners.WeaponBowListener}. Drawing and loosing is a plain,
     * always-available basic attack that no cooldown ever gates; ability1 rides along on top whenever it
     * happens to be ready. Slots 2–4 are untouched and still answer sneak, off-hand and sneak-off-hand.
     */
    public boolean ability1OnBowShot() {
        Material m = material();
        return m == Material.BOW || m == Material.CROSSBOW;
    }

    /**
     * Extra {@link org.bukkit.enchantments.Enchantment#LUNGE} power this weapon carries on top of the level
     * {@link #buildItem()} bakes into the item.
     * <p>
     * Lunge is the spear's <em>other</em> mechanic and a separate one from the charge attack: it fires on
     * {@code post_piercing_attack} — the left-click jab — and shoves the wielder forward through the target.
     * A spear with no Lunge enchantment simply never does it, which is why {@code buildItem} stamps
     * {@link org.bukkit.enchantments.Enchantment#LUNGE} onto every {@link #ability1OnChargeAttack()} weapon;
     * this bonus is then added on top in {@link dev.rbm72.weaponsplugin.listeners.WeaponLungeListener}.
     * <p>
     * Applied on every jab, on cooldown or not: it is the weapon's <em>stat</em>, not its ability. Note
     * vanilla's own conditions still gate it — no vehicle, not gliding, not in water, and 7+ hunger.
     */
    public int lungePowerBonus() {
        return 0;
    }

    /**
     * Level of {@link org.bukkit.enchantments.Enchantment#LUNGE} baked into a spear weapon's item.
     * <p>
     * Level I is deliberate as the floor rather than 0: at 0 the enchantment is absent and the jab lunge
     * does not exist at all, so {@link #lungePowerBonus()} would have no event to add itself to and
     * {@code WeaponLungeListener} would never fire.
     */
    public int lungeEnchantLevel() {
        return 1;
    }

    /** Right-click, main hand, sneaking. No-op unless overridden. */
    public void ability2(Player player) {
    }

    public double ability2CooldownSeconds() {
        return 0;
    }

    public List<Component> ability2Lore() {
        return List.of();
    }

    /** Right-click, off hand, not sneaking. No-op unless overridden. */
    public void ability3(Player player) {
    }

    public double ability3CooldownSeconds() {
        return 0;
    }

    public List<Component> ability3Lore() {
        return List.of();
    }

    /** Right-click, off hand, sneaking. No-op unless overridden. */
    public void ultimate(Player player) {
    }

    public double ultimateCooldownSeconds() {
        return 0;
    }

    public List<Component> ultimateLore() {
        return List.of();
    }

    /**
     * How this weapon's ultimate is earned, or null — the default — for one gated purely on
     * {@link #ultimateCooldownSeconds()}.
     * <p>
     * Opt-in per weapon rather than roster-wide because the two gates say different things about a weapon.
     * A cooldown ultimate is a tool you plan around ("it's up in 40 seconds, save it for the adds"); a
     * charge ultimate is a payoff you build toward, and only suits a weapon whose normal use is the
     * building. Forcing every weapon onto a meter would make the fifty that are fine as timers worse, and
     * a null here is a considered answer, not an unfinished one.
     *
     * @see dev.rbm72.weaponsplugin.ability.ChargeSpec
     */
    public dev.rbm72.weaponsplugin.ability.ChargeSpec ultimateChargeSpec() {
        return null;
    }

    /**
     * What each ability slot hits for, in the same units {@link #baseMeleeDamage()} uses — before rarity
     * scaling, before crits, before the multi-hit an ability may land.
     * <p>
     * Exists so a balance sheet can be produced at all. Every weapon's ability damage currently lives in
     * private fields read from per-weapon config keys, which means there is no view of the roster's damage
     * distribution anywhere: 57 files each hold one number and nothing holds the comparison, so an outlier
     * is only discovered by a player finding it. Declaring the numbers here makes
     * {@code /weaponbalance} able to rank them.
     * <p>
     * Empty by default and filled in as each weapon goes through the §0.1 doctrine pass — the sheet reports
     * an undeclared profile as exactly that rather than as zero damage, so a partially-migrated roster
     * produces an honest table instead of a flattering one.
     *
     * @return slot -> nominal damage per cast. Slots the weapon does not use are simply absent.
     */
    public java.util.Map<dev.rbm72.weaponsplugin.ability.CooldownManager.Slot, Double> damageProfile() {
        return java.util.Map.of();
    }

    /**
     * Which boss verbs this weapon answers — see {@link dev.rbm72.weaponsplugin.items.kit.Counterplay}.
     * <p>
     * Declared as data rather than left implicit in the ability code so the tooltip can say it and the
     * balance sheet can audit coverage. A boss with no drop answering its own signature verb is a gap worth
     * seeing in a table; found by playing the fight, it is just a boss that feels unfair.
     */
    public java.util.Set<CounterVerb> counterVerbs() {
        return java.util.Set.of();
    }

    /** The vocabulary of things bosses do to players that a drop may be built to answer. */
    public enum CounterVerb {
        /** Armour-ignoring stacks — Chill, Static Charge, Infection, Void Echo. */
        METER("clears the boss's stacks"),
        /** Forced loss of audio cues, i.e. the Hollow Choir's darkness. */
        SILENCE("breaks silence"),
        /** Terrain the boss placed to wall you in or encase you. */
        PILLARS("shatters the boss's terrain"),
        /** Floor that cannot be stood on — ice, powder snow, magma. */
        FOOTING("restores footing"),
        /** Knockback and forced relocation. */
        DISPLACEMENT("resists displacement");

        private final String description;

        CounterVerb(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    public abstract Sound castSound();

    public abstract Sound hitSound();

    public abstract Sound readySound();

    /** Only overridden by weapons that fire a tagged projectile (see {@link #idKey}). */
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
    }

    /** Fires every 10 ticks while this weapon is held in the main hand. Drives passives. */
    public void onTick(Player player) {
    }

    /** Fires when this weapon (held main hand) lands a melee hit. Drives on-hit passives. */
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
    }

    /** Fires when an {@link #onMeleeDamage} hit was lethal. Drives on-kill passives. */
    public void onKill(Player attacker, LivingEntity victim) {
    }

    /** Fires when this weapon (held main hand) breaks a block. Drives tool-style passives (AoE break, etc). */
    public void onBlockBreak(Player player, BlockBreakEvent event) {
    }

    public final Component displayName() {
        return Component.text(displayNameText(), rarity().color())
                .decoration(TextDecoration.ITALIC, false);
    }

    public final double effectiveMeleeDamage() {
        return baseMeleeDamage() * rarity().statMultiplier() * weightClass().damageScalar();
    }

    /**
     * Chance [0,1] this weapon's melee hit rolls a critical strike, rolled independently of
     * vanilla's sprint-jump crit in {@code WeaponDamageListener}. Rarity-scaled by default so
     * rarity buys something beyond the flat damage stat; override per-weapon for a signature feel
     * (daggers high chance/low multiplier, hammers the reverse).
     */
    public double critChance() {
        return configDouble("crit-chance", 0.10 + rarity().ordinal() * 0.03);
    }

    /** Damage multiplier applied on a critical strike. Rarity-scaled by default, see {@link #critChance()}. */
    public double critMultiplier() {
        return configDouble("crit-multiplier", 1.5 + rarity().ordinal() * 0.1);
    }

    /** Fraction of max health below which this weapon's hits deal bonus "execute" damage. */
    public double executeThresholdFraction() {
        return configDouble("execute-threshold", 0.2);
    }

    /** Damage multiplier applied when the victim is at or below {@link #executeThresholdFraction()}. */
    public double executeBonusMultiplier() {
        return configDouble("execute-bonus-multiplier", 1.3);
    }

    /**
     * How much weapon is being swung — the one stat that decides both how fast it swings and how hard
     * each swing lands.
     * <p>
     * This exists because of a silent vanilla rule with roster-wide consequences. Since the
     * {@code attribute_modifiers} item component arrived, an item that declares <em>any</em> modifier
     * no longer inherits its material's built-in ones: {@link #createItem()} sets an
     * {@link Attribute#ATTACK_DAMAGE} bonus, so every weapon here quietly threw away the material's
     * {@link Attribute#ATTACK_SPEED} entry too and fell back to the player's bare-fist 4.0/s. Nothing
     * in the roster had a swing cooldown, so a netherite greatsword and a pair of daggers both hit as
     * fast as the player could click, at full damage every time — which is why bosses died to
     * undifferentiated spam-clicking regardless of what was equipped.
     * <p>
     * Restoring the attribute is only half of it. A single restored speed for all sixty weapons would
     * make them identical again, so the class carries a matching damage scalar: light weapons trade
     * per-hit damage for swings, heavy weapons the reverse, and sustained DPS lands close enough that
     * the choice is about feel and openings rather than about a strictly better number.
     */
    public enum WeightClass {
        /** Daggers, knuckles, chakrams, whips — flurry weapons. */
        LIGHT(2.0, 0.75),
        /** Swords, spears, tridents, scythes, staves. The roster's default. */
        MEDIUM(1.55, 1.0),
        /** Mauls, hammers, axes, greatswords, anvils — poise-breakers. */
        HEAVY(0.9, 1.45);

        /** Swings per second this class is allowed. The player's unmodified base is 4.0. */
        private final double attacksPerSecond;
        private final double damageScalar;

        WeightClass(double attacksPerSecond, double damageScalar) {
            this.attacksPerSecond = attacksPerSecond;
            this.damageScalar = damageScalar;
        }

        public double attacksPerSecond() {
            return attacksPerSecond;
        }

        public double damageScalar() {
            return damageScalar;
        }
    }

    /** Base value of {@link Attribute#ATTACK_SPEED} on a player carrying nothing. */
    private static final double BASE_ATTACK_SPEED = 4.0;

    /**
     * Weapon-id fragments that mark a flurry weapon regardless of what material it borrows.
     * Deliberately narrow: "fang" and "claw" were the tempting additions and both are wrong, since
     * Dragon Fang and Starfang are greatsword-shaped and Ironclaw Knuckles is already caught by
     * "knuckle". A fragment that misfiles one weapon is worse than a fragment that catches none.
     */
    private static final List<String> LIGHT_ID_HINTS = List.of(
            "dagger", "knuckle", "chakram", "whip", "lash", "hook");

    /** Weapon-id fragments that mark a poise-breaker regardless of what material it borrows. */
    private static final List<String> HEAVY_ID_HINTS = List.of(
            "maul", "hammer", "greatsword", "breaker", "anvil", "warpick", "judgment", "cleaver");

    /**
     * This weapon's weight class. Derived from its id first and its material second, so the roster
     * classifies itself: a "meteor_maul" and an "earthbreaker_axe" both land in {@link WeightClass#HEAVY}
     * without either file having to say so, and the handful of weapons that borrow a material for its
     * look rather than its heft (a mace-shaped staff, a sword-shaped dagger) are caught by the id pass
     * that runs first. Override on any weapon the derivation gets wrong.
     */
    public WeightClass weightClass() {
        String id = id();
        for (String hint : LIGHT_ID_HINTS) {
            if (id.contains(hint)) {
                return WeightClass.LIGHT;
            }
        }
        for (String hint : HEAVY_ID_HINTS) {
            if (id.contains(hint)) {
                return WeightClass.HEAVY;
            }
        }
        Material m = material();
        String name = m.name();
        if (m == Material.MACE || m == Material.ANVIL || m == Material.TNT
                || m == Material.PACKED_ICE || m == Material.BEEHIVE || name.endsWith("_AXE")) {
            return WeightClass.HEAVY;
        }
        if (m == Material.STICK || m == Material.BAMBOO || m == Material.SHEARS
                || m == Material.FISHING_ROD || m == Material.IRON_CHAIN || m == Material.SPYGLASS
                || m == Material.BLAZE_ROD || m == Material.TOTEM_OF_UNDYING
                || m == Material.WITHER_SKELETON_SKULL) {
            return WeightClass.LIGHT;
        }
        return WeightClass.MEDIUM;
    }

    /** Swings per second, before the config override every tunable number in this plugin gets. */
    public final double attackSpeed() {
        return configDouble("attack-speed", weightClass().attacksPerSecond());
    }

    /**
     * Heavy weapons (mauls, hammers, axes) carry poise-break: enough weight behind a hit to
     * interrupt a boss's cast or knock down a blocking player's guard. Reads straight off
     * {@link #weightClass()} so "swings slowly, hits hard, staggers" is one decision rather than
     * three that can drift apart; override to opt in/out.
     */
    public boolean isHeavyWeapon() {
        return weightClass() == WeightClass.HEAVY;
    }

    /** Ticks a heavy weapon's hit staggers a boss (freezes its attack/movement) or disables a blocking player's shield. */
    public int poiseStaggerTicks() {
        return isHeavyWeapon() ? configInt("poise-stagger-ticks", 30) : 0;
    }

    /**
     * Scalar applied only when the victim is another player. Lets PvP balance move independently
     * of the PvE numbers that otherwise drive every hit this weapon lands — see
     * {@code combat.pvp-damage-multiplier} in config.yml for the server-wide default.
     */
    public double pvpDamageMultiplier() {
        return configDouble("pvp-damage-multiplier", plugin.getConfig().getDouble("combat.pvp-damage-multiplier", 0.65));
    }

    protected final double configDouble(String key, double def) {
        return plugin.getConfig().getDouble("weapons." + id() + "." + key, def);
    }

    protected final int configInt(String key, int def) {
        return plugin.getConfig().getInt("weapons." + id() + "." + key, def);
    }

    /** Leading glyph per ability slot (ability1, ability2, ability3, ultimate) — sells which trigger a block belongs to at a glance. */
    private static final String[] ABILITY_ICONS = {"⚔", "❋", "✦", "☠"};

    /**
     * Weapon ids with a custom item model + texture in the {@code resourcepack/} directory.
     * Only these get {@link ItemMeta#setItemModel}; every other weapon keeps its plain material
     * look, since pointing at a model that doesn't exist in the pack renders as a missing texture.
     * <p>
     * {@code maelstrom_trident} is deliberately absent. A trident is one of the handful of items
     * vanilla renders with its own in-hand and throwing poses rather than a flat sprite, and an
     * {@code item_model} override replaces that whole definition — the mcd pack pointed it at a
     * glaive, so the Leviathan's trident was a trident in name and a polearm on screen. Leaving it
     * off keeps {@link Material#TRIDENT}'s real model, which is what the weapon is supposed to look
     * like; the rarity glint and lore still apply as normal.
     */
    private static final Set<String> TEXTURED_IDS = Set.of(
            "anglers_hook", "anvilfall", "apotheosis", "arcane_staff", "arcpike",
            "ballista_crossbow", "blastcaller", "blood_reaper", "celestial_bow",
            "chainwhip", "chrono_blade", "cinder_cleaver", "cryoclasm", "crystalpike",
            "dawnbreaker", "dragon_fang", "dreadlance", "duskfall_mace",
            "earthbreaker_axe", "excavators_pick", "exsanguinator", "flame_katana",
            "frost_scythe", "glacial_scepter", "harrowpike", "hive_breaker", "hunters_crossbow",
            "ironclaw_knuckles", "kings_judgment", "legionnaires_pike", "lunar_blade",
            "meteor_maul", "mournsong", "necromancer_staff",
            "nullblade", "plague_scythe", "rotscourge", "sakura_blade",
            "serpentfang_crossbow", "shadow_daggers", "solar_greatsword", "soulcrown",
            "soulharvester", "spikequake_warpick", "spinelash", "starbreaker",
            "starfang", "storm_chakrams", "stormbreaker", "stormreach_halberd", "sunderpike",
            "tearfall", "tempest_maul", "tetherpike", "thunder_hammer", "tidal_trident",
            "venomtip_javelin", "vitriol", "void_blade", "wind_spear",
            "wyrmscale_bow");

    public final ItemStack createItem() {
        ItemStack item = new ItemStack(material());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(displayName());

        List<Component> body = new ArrayList<>();
        body.add(Component.text("❁ Damage  ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.format(Locale.ROOT, "+%.1f", effectiveMeleeDamage()), NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, true)));
        // The speed line is only meaningful next to the weight it comes from — "1.55/s" alone reads as
        // noise, "MEDIUM · 1.55/s" reads as the trade the weapon is making.
        body.add(Component.text("❁ Speed   ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(weightClass().name(), NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text(String.format(Locale.ROOT, " · %.2f/s", attackSpeed()), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)));

        double[] cooldowns = {ability1CooldownSeconds(), ability2CooldownSeconds(), ability3CooldownSeconds(), ultimateCooldownSeconds()};
        List<List<Component>> blocks = List.of(ability1Lore(), ability2Lore(), ability3Lore(), ultimateLore());
        String[] names = {ability1Name(), ability2Name(), ability3Name(), ultimateName()};

        for (int i = 0; i < blocks.size(); i++) {
            List<Component> block = blocks.get(i);
            if (block.isEmpty()) {
                continue;
            }
            body.add(Component.empty());

            String name = names[i];
            boolean hasName = name != null && !name.isBlank();
            if (hasName) {
                body.add(Component.text(ABILITY_ICONS[i] + " ", rarity().color())
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(name, rarity().color())
                                .decoration(TextDecoration.BOLD, true)
                                .decoration(TextDecoration.ITALIC, false)));
            }
            for (int line = 0; line < block.size(); line++) {
                Component text = block.get(line).decoration(TextDecoration.ITALIC, false);
                if (line == 0 && !hasName) {
                    text = Component.text(ABILITY_ICONS[i] + " ", rarity().color())
                            .decoration(TextDecoration.ITALIC, false)
                            .append(text);
                } else {
                    text = Component.text(" ").append(text);
                }
                body.add(text);
            }
            // An earned ultimate shows what fills it, not a timer. Printing the cooldown floor next to a
            // charge meter reads as "wait 8 seconds", which is the opposite of what the meter is for.
            boolean chargedUltimate = i == 3 && ultimateChargeSpec() != null;
            if (chargedUltimate) {
                body.add(Component.text(" ◈ ", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("Fills " + ultimateChargeSpec().label() + " in combat", NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, true)));
            } else if (cooldowns[i] > 0) {
                body.add(Component.text(" ⏱ ", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(String.format(Locale.ROOT, "%.1fs", cooldowns[i]), NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, true)));
            }
        }

        // Boss counterplay, spelled out. A drop that answers a boss verb is worth carrying into that
        // fight for a reason no damage number communicates, so the tooltip has to say it out loud —
        // otherwise the design exists only in the ability code and nobody gears around it.
        if (!counterVerbs().isEmpty()) {
            body.add(Component.empty());
            body.add(Component.text("⛨ Counterplay", NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            for (CounterVerb verb : counterVerbs()) {
                body.add(Component.text(" • " + verb.description(), NamedTextColor.DARK_AQUA)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        String footerLabel = rarity().label().toUpperCase(Locale.ROOT) + " WEAPON";
        int frameWidth = Math.max(TooltipFrame.widestLine(body), TooltipFrame.footerWidth(footerLabel));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.addAll(body);
        lore.add(Component.empty());
        lore.add(TooltipFrame.footer(footerLabel, rarity().color(), frameWidth));
        meta.lore(lore);

        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        meta.setEnchantmentGlintOverride(rarity().glint());

        // A spear's jab lunge is enchantment-gated in vanilla: with no LUNGE on the item the effect does not
        // exist, EntityLungeEvent never fires, and lungePowerBonus() has nothing to raise. HIDE_ENCHANTS is
        // already set above, so this never shows up in the tooltip next to the weapon's own ability text.
        if (ability1OnChargeAttack() && lungeEnchantLevel() > 0) {
            meta.addEnchant(Enchantment.LUNGE, lungeEnchantLevel(), true);
        }

        // A legendary drop that stops working when the quiver runs dry is a worse weapon than a plain
        // bow, so the ammo problem is solved on the item rather than in the player's inventory. Infinity
        // is bow-only in vanilla; a crossbow keeps consuming arrows and is topped up by
        // WeaponBowListener instead.
        if (material() == Material.BOW) {
            meta.addEnchant(Enchantment.INFINITY, 1, true);
        }

        if (TEXTURED_IDS.contains(id())) {
            meta.setItemModel(new NamespacedKey("weaponsplugin", id()));
        }

        NamespacedKey damageKey = new NamespacedKey(plugin, id() + "_damage");
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                damageKey, effectiveMeleeDamage(), AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

        // Declaring the damage modifier above is what makes this line mandatory rather than optional:
        // an item carrying explicit attribute modifiers inherits none of its material's defaults, so
        // without an ATTACK_SPEED entry every weapon in the roster swings at the player's bare-fist
        // 4.0/s with no swing cooldown and lands full damage on every click. The modifier is additive
        // against that 4.0 base, hence the subtraction.
        NamespacedKey speedKey = new NamespacedKey(plugin, id() + "_attack_speed");
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                speedKey, attackSpeed() - BASE_ATTACK_SPEED,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, WEAPON_ID_KEY), PersistentDataType.STRING, id());

        item.setItemMeta(meta);
        return item;
    }

    public final boolean matches(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, WEAPON_ID_KEY), PersistentDataType.STRING);
        return id().equals(value);
    }

    public static NamespacedKey idKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, WEAPON_ID_KEY);
    }
}

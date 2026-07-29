package dev.rbm72.weaponsplugin;

import dev.rbm72.weaponsplugin.ability.CooldownManager;
import dev.rbm72.weaponsplugin.ability.SummonRetargetTask;
import dev.rbm72.weaponsplugin.ability.WeaponSwitchLock;
import dev.rbm72.weaponsplugin.ability.UltimateChargeManager;
import dev.rbm72.weaponsplugin.ability.WeaponTickTask;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.fx.PlayerParticlePrefs;
import dev.rbm72.weaponsplugin.accessory.AccessoryManager;
import dev.rbm72.weaponsplugin.accessory.AccessoryRegistry;
import dev.rbm72.weaponsplugin.accessory.AccessoryTickTask;
import dev.rbm72.weaponsplugin.accessory.accessories.ArcaneGrimoire;
import dev.rbm72.weaponsplugin.accessory.accessories.BerserkerCharm;
import dev.rbm72.weaponsplugin.accessory.accessories.Chronostone;
import dev.rbm72.weaponsplugin.accessory.accessories.EchoingSigil;
import dev.rbm72.weaponsplugin.accessory.accessories.Emberheart;
import dev.rbm72.weaponsplugin.accessory.accessories.GaleStepBoots;
import dev.rbm72.weaponsplugin.accessory.accessories.GrappleHook;
import dev.rbm72.weaponsplugin.accessory.accessories.HollowMask;
import dev.rbm72.weaponsplugin.accessory.accessories.RingOfElements;
import dev.rbm72.weaponsplugin.accessory.accessories.RunicOverload;
import dev.rbm72.weaponsplugin.accessory.accessories.ShroudedCloak;
import dev.rbm72.weaponsplugin.accessory.accessories.SteadfastAnchor;
import dev.rbm72.weaponsplugin.accessory.accessories.SkyreaverTalons;
import dev.rbm72.weaponsplugin.accessory.accessories.SwiftcasterRing;
import dev.rbm72.weaponsplugin.accessory.accessories.TitansGirdle;
import dev.rbm72.weaponsplugin.accessory.accessories.VampiricLocket;
import dev.rbm72.weaponsplugin.accessory.accessories.WarriorsBand;
import dev.rbm72.weaponsplugin.accessory.accessories.WindwalkerBoots;
import dev.rbm72.weaponsplugin.armor.ArmorManager;
import dev.rbm72.weaponsplugin.armor.ArmorRegistry;
import dev.rbm72.weaponsplugin.armor.ArmorTickTask;
import dev.rbm72.weaponsplugin.armor.sets.CinderforgedPlate;
import dev.rbm72.weaponsplugin.armor.sets.GlacialWard;
import dev.rbm72.weaponsplugin.armor.sets.GrimshroudPlate;
import dev.rbm72.weaponsplugin.armor.sets.PenitentsVestments;
import dev.rbm72.weaponsplugin.armor.sets.ShroudveilMantle;
import dev.rbm72.weaponsplugin.armor.sets.StormplateAegis;
import dev.rbm72.weaponsplugin.armor.sets.ThornweaveVestments;
import dev.rbm72.weaponsplugin.armor.sets.WyrmwingPlate;
import dev.rbm72.weaponsplugin.boss.ArenaSafetyListener;
import dev.rbm72.weaponsplugin.boss.BossDamageListener;
import dev.rbm72.weaponsplugin.boss.grief.ExplosionLedgerListener;
import dev.rbm72.weaponsplugin.boss.grief.LedgerDropListener;
import dev.rbm72.weaponsplugin.boss.BossManager;
import dev.rbm72.weaponsplugin.boss.bosses.AmalgamatedBulk;
import dev.rbm72.weaponsplugin.boss.bosses.DragonElder;
import dev.rbm72.weaponsplugin.boss.bosses.FallenKing;
import dev.rbm72.weaponsplugin.boss.bosses.FrostQueen;
import dev.rbm72.weaponsplugin.boss.bosses.GraftedHorror;
import dev.rbm72.weaponsplugin.boss.bosses.HollowChoir;
import dev.rbm72.weaponsplugin.boss.bosses.InfernoWarlord;
import dev.rbm72.weaponsplugin.boss.bosses.NecroOverlord;
import dev.rbm72.weaponsplugin.boss.bosses.PlagueWarden;
import dev.rbm72.weaponsplugin.boss.bosses.SolarColossus;
import dev.rbm72.weaponsplugin.boss.bosses.StormTyrant;
import dev.rbm72.weaponsplugin.boss.bosses.ThreefoldBane;
import dev.rbm72.weaponsplugin.boss.bosses.TideLeviathan;
import dev.rbm72.weaponsplugin.boss.bosses.Voidwyrm;
import dev.rbm72.weaponsplugin.boss.bosses.VoidSovereign;
import dev.rbm72.weaponsplugin.boss.bosses.WeepingColossus;
import dev.rbm72.weaponsplugin.boss.bosses.Worldender;
import dev.rbm72.weaponsplugin.boss.commands.BossClearCommand;
import dev.rbm72.weaponsplugin.boss.commands.BossDespawnCommand;
import dev.rbm72.weaponsplugin.boss.commands.BossHardModeCommand;
import dev.rbm72.weaponsplugin.boss.commands.BossInfoCommand;
import dev.rbm72.weaponsplugin.boss.commands.BossMusicCommand;
import dev.rbm72.weaponsplugin.boss.commands.BossParticlesCommand;
import dev.rbm72.weaponsplugin.boss.commands.BossReloadCommand;
import dev.rbm72.weaponsplugin.boss.commands.BossSpawnCommand;
import dev.rbm72.weaponsplugin.boss.commands.GriefCommand;
import dev.rbm72.weaponsplugin.boss.props.ArenaTotemDamageListener;
import dev.rbm72.weaponsplugin.commands.EnderChestCommand;
import dev.rbm72.weaponsplugin.commands.GiveAccessoryCommand;
import dev.rbm72.weaponsplugin.commands.GiveArmorCommand;
import dev.rbm72.weaponsplugin.commands.GiveConsumableCommand;
import dev.rbm72.weaponsplugin.commands.GiveOpItemCommand;
import dev.rbm72.weaponsplugin.commands.GiveRidableCommand;
import dev.rbm72.weaponsplugin.commands.GiveShieldCommand;
import dev.rbm72.weaponsplugin.commands.GiveStoneCommand;
import dev.rbm72.weaponsplugin.commands.HeartsCommand;
import dev.rbm72.weaponsplugin.commands.GiveWeaponCommand;
import dev.rbm72.weaponsplugin.commands.HubCommand;
import dev.rbm72.weaponsplugin.commands.OpCooldownCommand;
import dev.rbm72.weaponsplugin.commands.ParticlesCommand;
import dev.rbm72.weaponsplugin.commands.WeaponBalanceCommand;
import dev.rbm72.weaponsplugin.commands.WeaponMenuCommand;
import dev.rbm72.weaponsplugin.opitem.HeartManager;
import dev.rbm72.weaponsplugin.opitem.OpItemRegistry;
import dev.rbm72.weaponsplugin.opitem.opitems.AscendantElixir;
import dev.rbm72.weaponsplugin.opitem.opitems.HeartVessel;
import dev.rbm72.weaponsplugin.consumable.ConsumableManager;
import dev.rbm72.weaponsplugin.consumable.ConsumableRegistry;
import dev.rbm72.weaponsplugin.consumable.consumables.EmberheartSalve;
import dev.rbm72.weaponsplugin.consumable.consumables.LastLightCharm;
import dev.rbm72.weaponsplugin.consumable.consumables.LifebloomVial;
import dev.rbm72.weaponsplugin.consumable.consumables.WardingPoultice;
import dev.rbm72.weaponsplugin.items.ShieldRegistry;
import dev.rbm72.weaponsplugin.items.WeaponRegistry;
import dev.rbm72.weaponsplugin.items.kit.TempTerrain;
import dev.rbm72.weaponsplugin.ridable.RidableActionBarSource;
import dev.rbm72.weaponsplugin.ridable.RidableManager;
import dev.rbm72.weaponsplugin.ridable.RidableMovementTask;
import dev.rbm72.weaponsplugin.ridable.RidableRegistry;
import dev.rbm72.weaponsplugin.ridable.ridables.EnderDragonSaddle;
import dev.rbm72.weaponsplugin.ridable.ridables.PhantomSaddle;
import dev.rbm72.weaponsplugin.ridable.ridables.RavagerSaddle;
import dev.rbm72.weaponsplugin.ridable.ridables.SpiderSaddle;
import dev.rbm72.weaponsplugin.ridable.ridables.StriderSaddle;
import dev.rbm72.weaponsplugin.ridable.ridables.WitherSaddle;
import dev.rbm72.weaponsplugin.realm.RealmDefinitions;
import dev.rbm72.weaponsplugin.realm.RealmManager;
import dev.rbm72.weaponsplugin.realm.RealmRegistry;
import dev.rbm72.weaponsplugin.stone.StoneActionBarSource;
import dev.rbm72.weaponsplugin.stone.StoneManager;
import dev.rbm72.weaponsplugin.stone.StoneRegistry;
import dev.rbm72.weaponsplugin.stone.StoneTickTask;
import dev.rbm72.weaponsplugin.stone.StoneMovementTask;
import dev.rbm72.weaponsplugin.stone.stones.CliffwalkerStone;
import dev.rbm72.weaponsplugin.stone.stones.GustboundStone;
import dev.rbm72.weaponsplugin.stone.stones.HighstepStone;
import dev.rbm72.weaponsplugin.stone.stones.LevitationStone;
import dev.rbm72.weaponsplugin.stone.stones.PathmakerStone;
import dev.rbm72.weaponsplugin.stone.stones.RiftstepStone;
import dev.rbm72.weaponsplugin.stone.stones.RimewalkStone;
import dev.rbm72.weaponsplugin.stone.stones.SkyleapStone;
import dev.rbm72.weaponsplugin.stone.stones.SlimeboundStone;
import dev.rbm72.weaponsplugin.stone.stones.SwiftStone;
import dev.rbm72.weaponsplugin.stone.stones.TideStone;
import dev.rbm72.weaponsplugin.stone.stones.WindrunnerStone;
import dev.rbm72.weaponsplugin.items.shields.BoneguardAegis;
import dev.rbm72.weaponsplugin.items.shields.DuelistsBuckler;
import dev.rbm72.weaponsplugin.items.shields.GlacialBastion;
import dev.rbm72.weaponsplugin.items.shields.GuardianBulwark;
import dev.rbm72.weaponsplugin.items.shields.ThornboundWard;
import dev.rbm72.weaponsplugin.items.shields.VoidguardAegis;
import dev.rbm72.weaponsplugin.items.shields.WardensAspis;
import dev.rbm72.weaponsplugin.items.weapons.AnglersHook;
import dev.rbm72.weaponsplugin.items.weapons.Anvilfall;
import dev.rbm72.weaponsplugin.items.weapons.Apotheosis;
import dev.rbm72.weaponsplugin.items.weapons.ArcaneStaff;
import dev.rbm72.weaponsplugin.items.weapons.BallistaCrossbow;
import dev.rbm72.weaponsplugin.items.weapons.Blastcaller;
import dev.rbm72.weaponsplugin.items.weapons.BloodReaper;
import dev.rbm72.weaponsplugin.items.weapons.CelestialBow;
import dev.rbm72.weaponsplugin.items.weapons.Chainwhip;
import dev.rbm72.weaponsplugin.items.weapons.ChronoBlade;
import dev.rbm72.weaponsplugin.items.weapons.CinderCleaver;
import dev.rbm72.weaponsplugin.items.weapons.Cryoclasm;
import dev.rbm72.weaponsplugin.items.weapons.Dawnbreaker;
import dev.rbm72.weaponsplugin.items.weapons.DragonFang;
import dev.rbm72.weaponsplugin.items.weapons.Dreadlance;
import dev.rbm72.weaponsplugin.items.weapons.DuskfallMace;
import dev.rbm72.weaponsplugin.items.weapons.EarthbreakerAxe;
import dev.rbm72.weaponsplugin.items.weapons.Exsanguinator;
import dev.rbm72.weaponsplugin.items.weapons.ExcavatorsPick;
import dev.rbm72.weaponsplugin.items.weapons.FlameKatana;
import dev.rbm72.weaponsplugin.items.weapons.FrostScythe;
import dev.rbm72.weaponsplugin.items.weapons.GlacialScepter;
import dev.rbm72.weaponsplugin.items.weapons.HiveBreaker;
import dev.rbm72.weaponsplugin.items.weapons.HuntersCrossbow;
import dev.rbm72.weaponsplugin.items.weapons.IronclawKnuckles;
import dev.rbm72.weaponsplugin.items.weapons.KingsJudgment;
import dev.rbm72.weaponsplugin.items.weapons.LegionnairesPike;
import dev.rbm72.weaponsplugin.items.weapons.LunarBlade;
import dev.rbm72.weaponsplugin.items.weapons.MaelstromTrident;
import dev.rbm72.weaponsplugin.items.weapons.MeteorMaul;
import dev.rbm72.weaponsplugin.items.weapons.Mournsong;
import dev.rbm72.weaponsplugin.items.weapons.NecromancerStaff;
import dev.rbm72.weaponsplugin.items.weapons.Nullblade;
import dev.rbm72.weaponsplugin.items.weapons.PlagueScythe;
import dev.rbm72.weaponsplugin.items.weapons.Rotscourge;
import dev.rbm72.weaponsplugin.items.weapons.SakuraBlade;
import dev.rbm72.weaponsplugin.items.weapons.SerpentfangCrossbow;
import dev.rbm72.weaponsplugin.items.weapons.ShadowDaggers;
import dev.rbm72.weaponsplugin.items.weapons.SolarGreatsword;
import dev.rbm72.weaponsplugin.items.weapons.Soulcrown;
import dev.rbm72.weaponsplugin.items.weapons.Soulharvester;
import dev.rbm72.weaponsplugin.items.weapons.Spinelash;
import dev.rbm72.weaponsplugin.items.weapons.SpikequakeWarpick;
import dev.rbm72.weaponsplugin.items.weapons.Starbreaker;
import dev.rbm72.weaponsplugin.items.weapons.Starfang;
import dev.rbm72.weaponsplugin.items.weapons.Stormbreaker;
import dev.rbm72.weaponsplugin.items.weapons.StormChakrams;
import dev.rbm72.weaponsplugin.items.weapons.StormreachHalberd;
import dev.rbm72.weaponsplugin.items.weapons.Tearfall;
import dev.rbm72.weaponsplugin.items.weapons.TempestMaul;
import dev.rbm72.weaponsplugin.items.weapons.ThunderHammer;
import dev.rbm72.weaponsplugin.items.weapons.TidalTrident;
import dev.rbm72.weaponsplugin.items.weapons.VenomtipJavelin;
import dev.rbm72.weaponsplugin.items.weapons.Vitriol;
import dev.rbm72.weaponsplugin.items.weapons.VoidBlade;
import dev.rbm72.weaponsplugin.items.weapons.WindSpear;
import dev.rbm72.weaponsplugin.items.weapons.WyrmscaleBow;
import dev.rbm72.weaponsplugin.listeners.AccessoryDamageListener;
import dev.rbm72.weaponsplugin.listeners.AccessoryKnockbackListener;
import dev.rbm72.weaponsplugin.listeners.AccessoryPersonalAbilityListener;
import dev.rbm72.weaponsplugin.listeners.AerialStrikeListener;
import dev.rbm72.weaponsplugin.listeners.BossMenuListener;
import dev.rbm72.weaponsplugin.listeners.CinderforgedListener;
import dev.rbm72.weaponsplugin.listeners.ConsumableUseListener;
import dev.rbm72.weaponsplugin.listeners.OpItemUseListener;
import dev.rbm72.weaponsplugin.listeners.GlacialWardListener;
import dev.rbm72.weaponsplugin.listeners.GrimshroudListener;
import dev.rbm72.weaponsplugin.listeners.HubListener;
import dev.rbm72.weaponsplugin.listeners.MagicProjectileListener;
import dev.rbm72.weaponsplugin.listeners.MenuListener;
import dev.rbm72.weaponsplugin.listeners.PenanceListener;
import dev.rbm72.weaponsplugin.listeners.PlayerSummonTargetListener;
import dev.rbm72.weaponsplugin.listeners.RealmListener;
import dev.rbm72.weaponsplugin.listeners.RidableMountListener;
import dev.rbm72.weaponsplugin.listeners.ShieldBlockListener;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import dev.rbm72.weaponsplugin.listeners.ShroudveilListener;
import dev.rbm72.weaponsplugin.listeners.RiftstepListener;
import dev.rbm72.weaponsplugin.listeners.StoneDoubleJumpListener;
import dev.rbm72.weaponsplugin.listeners.StonePersonalAbilityListener;
import dev.rbm72.weaponsplugin.listeners.StormplateListener;
import dev.rbm72.weaponsplugin.listeners.ThornweaveListener;
import dev.rbm72.weaponsplugin.listeners.WeaponBlockBreakListener;
import dev.rbm72.weaponsplugin.listeners.WeaponDamageListener;
import dev.rbm72.weaponsplugin.listeners.WeaponInteractListener;
import dev.rbm72.weaponsplugin.listeners.WeaponMenuListener;
import dev.rbm72.weaponsplugin.listeners.WeaponPropListener;
import dev.rbm72.weaponsplugin.listeners.WeaponSwitchListener;
import dev.rbm72.weaponsplugin.listeners.WingDashListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class WeaponsPlugin extends JavaPlugin {

    private WeaponRegistry weaponRegistry;
    private CooldownManager cooldownManager;
    private UltimateChargeManager ultimateChargeManager;
    private TempTerrain tempTerrain;
    private BossManager bossManager;
    private dev.rbm72.weaponsplugin.boss.progress.BossProgressStore bossProgress;
    private dev.rbm72.weaponsplugin.boss.telemetry.FightLog fightLog;
    private AccessoryRegistry accessoryRegistry;
    private AccessoryManager accessoryManager;
    private dev.rbm72.weaponsplugin.boss.DamageTrace damageTrace;
    private ArmorRegistry armorRegistry;
    private ArmorManager armorManager;
    private ShieldRegistry shieldRegistry;
    private StoneRegistry stoneRegistry;
    private StoneManager stoneManager;
    private RidableRegistry ridableRegistry;
    private RidableManager ridableManager;
    private RealmRegistry realmRegistry;
    private RealmManager realmManager;
    private ActionBarHub actionBarHub;
    private ConsumableRegistry consumableRegistry;
    private ConsumableManager consumableManager;
    private OpItemRegistry opItemRegistry;
    private HeartManager heartManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Before anything reads a boss tunable: overlays bosses/<id>.yml onto the live config (and
        // performs the one-time split out of config.yml on a first run).
        dev.rbm72.weaponsplugin.boss.config.BossConfigFiles.apply(this);
        Fx.init(this);
        dev.rbm72.weaponsplugin.boss.BossAudio.init(this);
        dev.rbm72.weaponsplugin.boss.BossMusic.init(this);
        PlayerParticlePrefs.init(this);

        actionBarHub = new ActionBarHub();
        weaponRegistry = new WeaponRegistry();
        cooldownManager = new CooldownManager(this);
        ultimateChargeManager = new UltimateChargeManager(this);
        tempTerrain = new TempTerrain(this);
        bossManager = new BossManager(this);
        // Both read/write their own folders under the plugin data dir and are needed by the very first
        // fight, so they are built before anything can spawn a boss.
        bossProgress = new dev.rbm72.weaponsplugin.boss.progress.BossProgressStore(this);
        fightLog = new dev.rbm72.weaponsplugin.boss.telemetry.FightLog(this);
        accessoryRegistry = new AccessoryRegistry();
        accessoryManager = new AccessoryManager(this, accessoryRegistry);
        armorRegistry = new ArmorRegistry();
        armorManager = new ArmorManager(armorRegistry);
        shieldRegistry = new ShieldRegistry();
        stoneRegistry = new StoneRegistry();
        stoneManager = new StoneManager(this, stoneRegistry);
        ridableRegistry = new RidableRegistry();
        ridableManager = new RidableManager(this, ridableRegistry);
        consumableRegistry = new ConsumableRegistry();
        consumableManager = new ConsumableManager(this);
        opItemRegistry = new OpItemRegistry();
        heartManager = new HeartManager(this);
        realmRegistry = new RealmRegistry();
        realmManager = new RealmManager(this);
        WeaponSwitchLock weaponSwitchLock = new WeaponSwitchLock();
        OpCooldownCommand opCooldownCommand = new OpCooldownCommand();

        accessoryRegistry.register(new WarriorsBand(this));
        accessoryRegistry.register(new BerserkerCharm(this));
        accessoryRegistry.register(new SwiftcasterRing(this));
        accessoryRegistry.register(new Chronostone(this));
        accessoryRegistry.register(new Emberheart(this));
        accessoryRegistry.register(new VampiricLocket(this));
        accessoryRegistry.register(new TitansGirdle(this));
        accessoryRegistry.register(new WindwalkerBoots(this));
        accessoryRegistry.register(new EchoingSigil(this));
        accessoryRegistry.register(new RunicOverload(this));
        accessoryRegistry.register(new HollowMask(this));
        accessoryRegistry.register(new GrappleHook(this));
        accessoryRegistry.register(new GaleStepBoots(this));
        accessoryRegistry.register(new ArcaneGrimoire(this));
        accessoryRegistry.register(new RingOfElements(this));
        accessoryRegistry.register(new ShroudedCloak(this));
        accessoryRegistry.register(new SteadfastAnchor(this));
        SkyreaverTalons skyreaverTalons = new SkyreaverTalons(this);
        accessoryRegistry.register(skyreaverTalons);

        shieldRegistry.register(new GuardianBulwark(this));
        shieldRegistry.register(new WardensAspis(this));
        shieldRegistry.register(new DuelistsBuckler(this));
        shieldRegistry.register(new GlacialBastion(this));
        shieldRegistry.register(new VoidguardAegis(this));
        shieldRegistry.register(new ThornboundWard(this));
        shieldRegistry.register(new BoneguardAegis(this));

        consumableRegistry.register(new LifebloomVial(this));
        consumableRegistry.register(new EmberheartSalve(this));
        consumableRegistry.register(new WardingPoultice(this));
        consumableRegistry.register(new LastLightCharm(this));

        opItemRegistry.register(new AscendantElixir(this));
        opItemRegistry.register(new HeartVessel(this));

        stoneRegistry.register(new HighstepStone(this));
        stoneRegistry.register(new SwiftStone(this));
        stoneRegistry.register(new TideStone(this));
        SkyleapStone skyleapStone = new SkyleapStone(this);
        stoneRegistry.register(skyleapStone);
        stoneRegistry.register(new WindrunnerStone(this));
        stoneRegistry.register(new CliffwalkerStone(this));
        stoneRegistry.register(new LevitationStone(this));
        stoneRegistry.register(new RimewalkStone(this));
        stoneRegistry.register(new SlimeboundStone(this));
        stoneRegistry.register(new PathmakerStone(this));
        stoneRegistry.register(new GustboundStone(this));
        stoneRegistry.register(new RiftstepStone(this));

        ridableRegistry.register(new RavagerSaddle(this));
        ridableRegistry.register(new EnderDragonSaddle(this));
        ridableRegistry.register(new WitherSaddle(this));
        ridableRegistry.register(new SpiderSaddle(this));
        ridableRegistry.register(new PhantomSaddle(this));
        ridableRegistry.register(new StriderSaddle(this));

        WyrmwingPlate wyrmwingPlate = new WyrmwingPlate(this);
        PenitentsVestments penitentsVestments = new PenitentsVestments(this);
        StormplateAegis stormplateAegis = new StormplateAegis(this);
        CinderforgedPlate cinderforgedPlate = new CinderforgedPlate(this);
        GlacialWard glacialWard = new GlacialWard(this);
        ShroudveilMantle shroudveilMantle = new ShroudveilMantle(this);
        ThornweaveVestments thornweaveVestments = new ThornweaveVestments(this);
        GrimshroudPlate grimshroudPlate = new GrimshroudPlate(this);
        armorRegistry.register(wyrmwingPlate);
        armorRegistry.register(penitentsVestments);
        armorRegistry.register(stormplateAegis);
        armorRegistry.register(cinderforgedPlate);
        armorRegistry.register(glacialWard);
        armorRegistry.register(shroudveilMantle);
        armorRegistry.register(thornweaveVestments);
        armorRegistry.register(grimshroudPlate);

        weaponRegistry.register(new Stormbreaker(this));
        weaponRegistry.register(new FlameKatana(this));
        weaponRegistry.register(new ThunderHammer(this));
        weaponRegistry.register(new FrostScythe(this));
        weaponRegistry.register(new ShadowDaggers(this));
        weaponRegistry.register(new ArcaneStaff(this));
        weaponRegistry.register(new WindSpear(this));
        weaponRegistry.register(new TidalTrident(this));
        weaponRegistry.register(new VoidBlade(this));
        weaponRegistry.register(new SolarGreatsword(this));
        weaponRegistry.register(new LunarBlade(this));
        weaponRegistry.register(new PlagueScythe(this));
        weaponRegistry.register(new DragonFang(this));
        weaponRegistry.register(new CelestialBow(this));
        weaponRegistry.register(new BloodReaper(this));
        weaponRegistry.register(new ChronoBlade(this));
        weaponRegistry.register(new StormChakrams(this));
        weaponRegistry.register(new EarthbreakerAxe(this));
        weaponRegistry.register(new NecromancerStaff(this));
        weaponRegistry.register(new SakuraBlade(this));
        weaponRegistry.register(new Starbreaker(this));
        weaponRegistry.register(new KingsJudgment(this));
        weaponRegistry.register(new GlacialScepter(this));
        weaponRegistry.register(new TempestMaul(this));
        weaponRegistry.register(new CinderCleaver(this));
        weaponRegistry.register(new Rotscourge(this));
        weaponRegistry.register(new Nullblade(this));
        weaponRegistry.register(new Dawnbreaker(this));
        weaponRegistry.register(new MaelstromTrident(this));
        weaponRegistry.register(new WyrmscaleBow(this));
        weaponRegistry.register(new Soulharvester(this));
        weaponRegistry.register(new Apotheosis(this));
        weaponRegistry.register(new DuskfallMace(this));
        weaponRegistry.register(new Chainwhip(this));
        weaponRegistry.register(new HuntersCrossbow(this));
        weaponRegistry.register(new SerpentfangCrossbow(this));
        weaponRegistry.register(new BallistaCrossbow(this));
        weaponRegistry.register(new LegionnairesPike(this));
        weaponRegistry.register(new Dreadlance(this));
        weaponRegistry.register(new IronclawKnuckles(this));
        weaponRegistry.register(new StormreachHalberd(this));
        weaponRegistry.register(new VenomtipJavelin(this));
        weaponRegistry.register(new AnglersHook(this));
        weaponRegistry.register(new ExcavatorsPick(this));
        weaponRegistry.register(new Spinelash(this));
        weaponRegistry.register(new Soulcrown(this));
        weaponRegistry.register(new Starfang(this));
        weaponRegistry.register(new Vitriol(this));
        weaponRegistry.register(new Mournsong(this));
        weaponRegistry.register(new Tearfall(this));
        weaponRegistry.register(new Exsanguinator(this));
        weaponRegistry.register(new MeteorMaul(this));
        weaponRegistry.register(new SpikequakeWarpick(this));
        weaponRegistry.register(new Anvilfall(this));
        weaponRegistry.register(new Cryoclasm(this));
        weaponRegistry.register(new HiveBreaker(this));
        weaponRegistry.register(new Blastcaller(this));

        bossManager.register(new FallenKing(this));
        bossManager.register(new FrostQueen(this));
        bossManager.register(new StormTyrant(this));
        bossManager.register(new InfernoWarlord(this));
        bossManager.register(new PlagueWarden(this));
        bossManager.register(new VoidSovereign(this));
        bossManager.register(new SolarColossus(this));
        bossManager.register(new TideLeviathan(this));
        bossManager.register(new DragonElder(this));
        bossManager.register(new NecroOverlord(this));
        bossManager.register(new GraftedHorror(this));
        bossManager.register(new ThreefoldBane(this));
        bossManager.register(new Voidwyrm(this));
        bossManager.register(new AmalgamatedBulk(this));
        bossManager.register(new HollowChoir(this));
        bossManager.register(new WeepingColossus(this));
        bossManager.register(new Worldender(this));

        RealmDefinitions.registerAll(realmRegistry);

        getServer().getPluginManager().registerEvents(
                new WeaponInteractListener(this, weaponRegistry, cooldownManager, accessoryManager, opCooldownCommand,
                        weaponSwitchLock, ultimateChargeManager), this);
        getServer().getPluginManager().registerEvents(
                new WeaponDamageListener(this, weaponRegistry, accessoryManager, cooldownManager, bossManager,
                        ultimateChargeManager), this);
        getServer().getPluginManager().registerEvents(
                new AccessoryDamageListener(weaponRegistry, accessoryManager), this);
        getServer().getPluginManager().registerEvents(
                new AccessoryKnockbackListener(accessoryManager), this);
        // Same promise, third symptom: the accessory also silences TickDamage's quiet hurt cue for its
        // wearer. The damage-indicator particles stay, so the tick is still visible.
        dev.rbm72.weaponsplugin.boss.TickDamage.setHurtCueExempt(accessoryManager::negatesTickFlinch);
        // The other half of "never shoved or flinched": no hurt tilt from damage-over-time, anywhere.
        // Rate-classified rather than cause-listed, and it reimplements vanilla's i-frames because
        // cancelling a damage event throws them away — see the class for why that matters.
        getServer().getPluginManager().registerEvents(
                new dev.rbm72.weaponsplugin.listeners.AccessoryFlinchListener(accessoryManager), this);
        getServer().getPluginManager().registerEvents(new WeaponSwitchListener(weaponRegistry, weaponSwitchLock), this);
        getServer().getPluginManager().registerEvents(new WeaponBlockBreakListener(weaponRegistry), this);
        getServer().getPluginManager().registerEvents(new ShieldBlockListener(shieldRegistry, actionBarHub), this);
        getServer().getPluginManager().registerEvents(
                new MagicProjectileListener(this, weaponRegistry), this);
        getServer().getPluginManager().registerEvents(new WeaponPropListener(this), this);
        getServer().getPluginManager().registerEvents(new WeaponMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new HubListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new BossMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new BossDamageListener(bossManager), this);
        // Explosions destroy terrain without going through Grief, so the ledger has to catch their
        // block lists here or every crater becomes a permanent hole in an otherwise-clean rollback.
        getServer().getPluginManager().registerEvents(new ExplosionLedgerListener(this), this);
        // A fight's own blocks are put back by the ledger, so mining them out must not also pay — see
        // LedgerDropListener: without it the Necro Overlord's corpse floor is a bone farm.
        getServer().getPluginManager().registerEvents(new LedgerDropListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaSafetyListener(bossManager), this);
        // Strips the hurt tilt off vanilla's recurring environmental ticks inside a live arena — the
        // fire trails and lava floods §0.1 asks for should cost health, not the player's camera.
        getServer().getPluginManager().registerEvents(
                new dev.rbm72.weaponsplugin.boss.AmbientTickListener(bossManager), this);
        // Off unless someone runs /bossdamagetrace on themselves. Answers "what is ticking me" by
        // watching both paths health can leave by: the damage event, and TickDamage's direct write.
        damageTrace = new dev.rbm72.weaponsplugin.boss.DamageTrace(this);
        getServer().getPluginManager().registerEvents(damageTrace, this);
        // Records player deaths against whichever live fight owns the ground they died on — the only
        // source for "what actually killed them" in a wipe recap.
        getServer().getPluginManager().registerEvents(
                new dev.rbm72.weaponsplugin.boss.telemetry.FightDeathListener(this), this);
        // Enforces the no-heal affix. Inert unless that affix is armed on a live fight.
        getServer().getPluginManager().registerEvents(
                new dev.rbm72.weaponsplugin.boss.modifier.NoHealListener(this), this);
        PlayerSummonTargetListener summonTargetListener = new PlayerSummonTargetListener(this);
        getServer().getPluginManager().registerEvents(summonTargetListener, this);
        getServer().getPluginManager().registerEvents(new ArenaTotemDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new AccessoryPersonalAbilityListener(accessoryManager), this);
        getServer().getPluginManager().registerEvents(new StonePersonalAbilityListener(stoneManager, opCooldownCommand, actionBarHub), this);
        getServer().getPluginManager().registerEvents(new StoneDoubleJumpListener(stoneManager, skyleapStone), this);
        getServer().getPluginManager().registerEvents(new RiftstepListener(stoneManager), this);
        getServer().getPluginManager().registerEvents(new AerialStrikeListener(accessoryManager, skyreaverTalons), this);
        getServer().getPluginManager().registerEvents(new WingDashListener(armorManager, wyrmwingPlate), this);
        getServer().getPluginManager().registerEvents(new PenanceListener(this, armorManager, penitentsVestments), this);
        getServer().getPluginManager().registerEvents(new StormplateListener(armorManager, stormplateAegis), this);
        getServer().getPluginManager().registerEvents(new CinderforgedListener(armorManager, cinderforgedPlate), this);
        getServer().getPluginManager().registerEvents(new GlacialWardListener(this, armorManager, glacialWard), this);
        getServer().getPluginManager().registerEvents(new ShroudveilListener(armorManager, shroudveilMantle), this);
        getServer().getPluginManager().registerEvents(new ThornweaveListener(armorManager, thornweaveVestments), this);
        getServer().getPluginManager().registerEvents(new GrimshroudListener(armorManager, grimshroudPlate), this);
        getServer().getPluginManager().registerEvents(new RidableMountListener(this), this);
        getServer().getPluginManager().registerEvents(new RealmListener(this), this);
        getServer().getPluginManager().registerEvents(new ConsumableUseListener(this), this);
        getServer().getPluginManager().registerEvents(new OpItemUseListener(this), this);

        new WeaponTickTask(weaponRegistry).start(this);
        ultimateChargeManager.start();
        tempTerrain.start();
        new SummonRetargetTask(this, summonTargetListener.key()).start();
        new AccessoryTickTask(accessoryManager).start(this);
        new ArmorTickTask(armorManager).start(this);
        new StoneTickTask(stoneManager).start(this);
        new StoneMovementTask(stoneManager).start(this);
        new RidableMovementTask(ridableManager).start(this);

        // One action bar, one writer. Registration order here is left-to-right display order on the
        // merged line, and it's fixed — a segment never shifts position because some other system
        // started or stopped showing something.
        actionBarHub.register(cooldownManager);
        actionBarHub.register(ultimateChargeManager);
        actionBarHub.register(new StoneActionBarSource(stoneManager, opCooldownCommand));
        actionBarHub.register(new RidableActionBarSource(ridableManager));
        actionBarHub.register(consumableManager);
        actionBarHub.start(this);

        // Hand the hub star to anyone already online (e.g. after a /reload).
        getServer().getOnlinePlayers().forEach(player ->
                dev.rbm72.weaponsplugin.gui.HubItem.ensure(this, player));
        // And re-sync their bonus hearts: a /reload skips the join handler that normally applies them, which
        // would leave the tally on disk and the health bar disagreeing until the next relog.
        heartManager.applyAll(new java.util.ArrayList<>(getServer().getOnlinePlayers()));

        GiveWeaponCommand giveWeaponCommand = new GiveWeaponCommand(weaponRegistry);
        getCommand("giveweapon").setExecutor(giveWeaponCommand);
        getCommand("giveweapon").setTabCompleter(giveWeaponCommand);
        getCommand("weapons").setExecutor(new WeaponMenuCommand(this));
        WeaponBalanceCommand weaponBalanceCommand = new WeaponBalanceCommand(this);
        getCommand("weaponbalance").setExecutor(weaponBalanceCommand);
        getCommand("weaponbalance").setTabCompleter(weaponBalanceCommand);
        getCommand("opcooldown").setExecutor(opCooldownCommand);
        getCommand("bossspawn").setExecutor(new BossSpawnCommand(this));
        getCommand("bossdespawn").setExecutor(new BossDespawnCommand(bossManager));
        getCommand("bossclear").setExecutor(new BossClearCommand(bossManager));
        BossInfoCommand bossInfoCommand = new BossInfoCommand(this);
        getCommand("bossinfo").setExecutor(bossInfoCommand);
        getCommand("bossinfo").setTabCompleter(bossInfoCommand);
        getCommand("bossreload").setExecutor(new BossReloadCommand(this));
        BossHardModeCommand bossHardModeCommand = new BossHardModeCommand(bossManager);
        getCommand("bosshardmode").setExecutor(bossHardModeCommand);
        getCommand("bosshardmode").setTabCompleter(bossHardModeCommand);
        dev.rbm72.weaponsplugin.boss.commands.BossAffixCommand bossAffixCommand =
                new dev.rbm72.weaponsplugin.boss.commands.BossAffixCommand(this);
        getCommand("bossaffix").setExecutor(bossAffixCommand);
        getCommand("bossaffix").setTabCompleter(bossAffixCommand);
        dev.rbm72.weaponsplugin.boss.commands.BossReportCommand bossReportCommand =
                new dev.rbm72.weaponsplugin.boss.commands.BossReportCommand(this);
        getCommand("bossreport").setExecutor(bossReportCommand);
        getCommand("bossreport").setTabCompleter(bossReportCommand);
        dev.rbm72.weaponsplugin.boss.commands.BossLeaderboardCommand bossLeaderboardCommand =
                new dev.rbm72.weaponsplugin.boss.commands.BossLeaderboardCommand(this);
        getCommand("bossleaderboard").setExecutor(bossLeaderboardCommand);
        getCommand("bossleaderboard").setTabCompleter(bossLeaderboardCommand);
        dev.rbm72.weaponsplugin.boss.commands.BossTestCommand bossTestCommand =
                new dev.rbm72.weaponsplugin.boss.commands.BossTestCommand(this);
        getCommand("bosstest").setExecutor(bossTestCommand);
        getCommand("bosstest").setTabCompleter(bossTestCommand);
        dev.rbm72.weaponsplugin.boss.commands.BossAudioCommand bossAudioCommand =
                new dev.rbm72.weaponsplugin.boss.commands.BossAudioCommand(this);
        getCommand("bossaudio").setExecutor(bossAudioCommand);
        getCommand("bossaudio").setTabCompleter(bossAudioCommand);
        BossParticlesCommand bossParticlesCommand = new BossParticlesCommand(this);
        getCommand("bossparticles").setExecutor(bossParticlesCommand);
        getCommand("bossparticles").setTabCompleter(bossParticlesCommand);
        getCommand("bossdamagetrace").setExecutor(
                new dev.rbm72.weaponsplugin.boss.commands.BossDamageTraceCommand(damageTrace));

        BossMusicCommand bossMusicCommand = new BossMusicCommand(this);
        getCommand("bossmusic").setExecutor(bossMusicCommand);
        getCommand("bossmusic").setTabCompleter(bossMusicCommand);
        ParticlesCommand particlesCommand = new ParticlesCommand();
        getCommand("particles").setExecutor(particlesCommand);
        getCommand("particles").setTabCompleter(particlesCommand);
        getCommand("grief").setExecutor(new GriefCommand());
        getCommand("hub").setExecutor(new HubCommand(this));
        EnderChestCommand enderChestCommand = new EnderChestCommand();
        getCommand("enderchest").setExecutor(enderChestCommand);
        getCommand("enderchest").setTabCompleter(enderChestCommand);
        GiveAccessoryCommand giveAccessoryCommand = new GiveAccessoryCommand(accessoryRegistry);
        getCommand("giveaccessory").setExecutor(giveAccessoryCommand);
        getCommand("giveaccessory").setTabCompleter(giveAccessoryCommand);
        GiveArmorCommand giveArmorCommand = new GiveArmorCommand(armorRegistry);
        getCommand("givearmor").setExecutor(giveArmorCommand);
        getCommand("givearmor").setTabCompleter(giveArmorCommand);
        GiveShieldCommand giveShieldCommand = new GiveShieldCommand(shieldRegistry);
        getCommand("giveshield").setExecutor(giveShieldCommand);
        getCommand("giveshield").setTabCompleter(giveShieldCommand);
        GiveConsumableCommand giveConsumableCommand = new GiveConsumableCommand(consumableRegistry);
        getCommand("giveconsumable").setExecutor(giveConsumableCommand);
        getCommand("giveconsumable").setTabCompleter(giveConsumableCommand);
        GiveOpItemCommand giveOpItemCommand = new GiveOpItemCommand(opItemRegistry);
        getCommand("giveopitem").setExecutor(giveOpItemCommand);
        getCommand("giveopitem").setTabCompleter(giveOpItemCommand);
        HeartsCommand heartsCommand = new HeartsCommand(heartManager);
        getCommand("hearts").setExecutor(heartsCommand);
        getCommand("hearts").setTabCompleter(heartsCommand);
        GiveStoneCommand giveStoneCommand = new GiveStoneCommand(stoneRegistry);
        getCommand("givestone").setExecutor(giveStoneCommand);
        getCommand("givestone").setTabCompleter(giveStoneCommand);
        GiveRidableCommand giveRidableCommand = new GiveRidableCommand(ridableRegistry);
        getCommand("giveridable").setExecutor(giveRidableCommand);
        getCommand("giveridable").setTabCompleter(giveRidableCommand);

        getLogger().info("WeaponsPlugin enabled with " + weaponRegistry.all().size() + " weapon(s) and "
                + bossManager.all().size() + " boss(es)");
    }

    @Override
    public void onDisable() {
        if (bossManager != null) {
            bossManager.shutdownAll();
        }
        // Before the logger line, and unconditionally: any weapon terrain still waiting on its TTL has
        // no tick left to expire on, so a restart is the one way temporary blocks become permanent ones.
        if (tempTerrain != null) {
            tempTerrain.revertAll();
        }
        getLogger().info("WeaponsPlugin disabled");
    }

    public WeaponRegistry weaponRegistry() {
        return weaponRegistry;
    }

    public CooldownManager cooldownManager() {
        return cooldownManager;
    }

    /** Per-player ultimate charge for the weapons that earn their ultimate rather than wait for it. */
    public UltimateChargeManager ultimateChargeManager() {
        return ultimateChargeManager;
    }

    /**
     * The undo log for terrain a weapon ability lays down. Weapons must place blocks through this and
     * never with a bare {@code setType} — an unledgered write is permanent grief with no fight to end.
     */
    public TempTerrain tempTerrain() {
        return tempTerrain;
    }

    public BossManager bossManager() {
        return bossManager;
    }

    /** The persistent per-player boss kill ledger — clear gates, first-clear loot, /bossinfo, leaderboards. */
    public dev.rbm72.weaponsplugin.boss.progress.BossProgressStore bossProgress() {
        return bossProgress;
    }

    /** Per-fight telemetry sink — every finished fight's phase/attack/wipe record, for /bossreport. */
    public dev.rbm72.weaponsplugin.boss.telemetry.FightLog fightLog() {
        return fightLog;
    }

    public AccessoryRegistry accessoryRegistry() {
        return accessoryRegistry;
    }

    public AccessoryManager accessoryManager() {
        return accessoryManager;
    }

    public ArmorRegistry armorRegistry() {
        return armorRegistry;
    }

    public ArmorManager armorManager() {
        return armorManager;
    }

    public ShieldRegistry shieldRegistry() {
        return shieldRegistry;
    }

    public StoneRegistry stoneRegistry() {
        return stoneRegistry;
    }

    public StoneManager stoneManager() {
        return stoneManager;
    }

    public RidableRegistry ridableRegistry() {
        return ridableRegistry;
    }

    public RidableManager ridableManager() {
        return ridableManager;
    }

    public RealmRegistry realmRegistry() {
        return realmRegistry;
    }

    /** The single owner of every player's action bar — anything with something to display goes through it. */
    public ConsumableRegistry consumableRegistry() {
        return consumableRegistry;
    }

    public ConsumableManager consumableManager() {
        return consumableManager;
    }

    /** The operator shelf — deliberately outside the balanced weapon/consumable registries. */
    public OpItemRegistry opItemRegistry() {
        return opItemRegistry;
    }

    /** Every player's bonus-heart tally, and the max-health modifier that renders it. */
    public HeartManager heartManager() {
        return heartManager;
    }

    /** The single owner of every player's action bar — anything with something to display goes through it. */
    public ActionBarHub actionBarHub() {
        return actionBarHub;
    }

    public RealmManager realmManager() {
        return realmManager;
    }
}

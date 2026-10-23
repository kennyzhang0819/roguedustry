package roguedustry;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;

public class Rewards{

    public enum Rarity{
        common("Common", Color.valueOf("84f491"), 67),
        uncommon("Uncommon", Color.valueOf("6f9dff"), 28),
        rare("Rare", Color.valueOf("ffd37f"), 5),
        relic("Relic", Color.valueOf("c77dff"), 0);

        public final String label;
        public final Color color;
        public final int weight;

        Rarity(String label, Color color, int weight){
            this.label = label;
            this.color = color;
            this.weight = weight;
        }
    }

    public static class Reward{
        public final String id, name, description;
        public final Rarity rarity;
        public @Nullable UnlockableContent iconContent;
        public final Seq<Block> blocks = new Seq<>();
        public float speed, wallHealth;
        public final Seq<UnitType> units = new Seq<>();
        public float unitHealth, unitDamage, unitSpeed, unitReload;
        public @Nullable StatusEffect effect;

        Reward(String id, String name, Rarity rarity, String description){
            this.id = id;
            this.name = name;
            this.rarity = rarity;
            this.description = description;
        }

        public boolean hasIcon(){
            return iconContent != null || Core.atlas.has("roguedustry-" + id);
        }

        public TextureRegion icon(){
            return iconContent != null ? iconContent.uiIcon : Core.atlas.find("roguedustry-" + id);
        }

        void faster(float perStack, Block... targets){
            speed = perStack;
            blocks.addAll(targets);
            iconContent = targets[0];
        }

        void tougher(float perStack, Block... targets){
            wallHealth = perStack;
            blocks.addAll(targets);
            iconContent = targets[0];
        }

        void units(UnitType icon, float health, float damage, float speed, float reload, UnitType... targets){
            unitHealth = health;
            unitDamage = damage;
            unitSpeed = speed;
            unitReload = reload;
            units.addAll(targets);
            iconContent = icon;
        }

        void setStacks(int stacks){
            if(effect == null) return;
            effect.healthMultiplier = 1f + unitHealth * stacks;
            effect.damageMultiplier = 1f + unitDamage * stacks;
            effect.speedMultiplier = 1f + unitSpeed * stacks;
            effect.reloadMultiplier = 1f + unitReload * stacks;
        }

        public boolean isRelic(){
            return rarity == Rarity.relic;
        }
    }

    public static final int wavesPerModifier = 10, prefabCount = 100, lastStandRadius = 50;
    public static final float plunderShare = 0.5f;

    public static final Reward
    coreLink = new Reward("core-link", "Core Link", Rarity.relic, "All drills send what they mine straight into your core. Does not apply to extractors."),
    livingMetal = new Reward("living-metal", "Living Metal", Rarity.relic, "All your structures repair 2% of their health every second."),
    gracePeriod = new Reward("grace-period", "Grace Period", Rarity.relic, "On each new sector, the first wave comes 3 minutes later."),
    prefab = new Reward("prefab", "Prefab", Rarity.relic, "On each sector, the first " + prefabCount + " structures you place that aren't conveyors finish instantly. You still pay their cost."),
    biggerCore = new Reward("bigger-core", "Bigger Core", Rarity.relic, "Your cores hold twice as many items."),
    warFactory = new Reward("war-factory", "War Factory", Rarity.relic, "Unit factories and reconstructors work twice as fast."),
    recycle = new Reward("recycle", "Recycle", Rarity.relic, "When your units die, they have a 50% chance to come back at your nearest core."),
    fieldCommander = new Reward("field-commander", "Field Commander", Rarity.relic, "Your core unit (Alpha, Beta, Gamma) has 8 times the health and moves twice as fast."),
    oreMagnet = new Reward("ore-magnet", "Ore Magnet", Rarity.relic, "All drills and extractors mine three times as fast."),
    scrapRebirth = new Reward("scrap-rebirth", "Scrap Rebirth", Rarity.relic, "When one of your structures is destroyed, half its cost goes back into your core."),
    lastStand = new Reward("last-stand", "Last Stand", Rarity.relic, "Once per sector, when a core drops below 25% health, every enemy unit within " + lastStandRadius + " tiles of it dies."),
    plunder = new Reward("plunder", "Plunder", Rarity.relic, "Every enemy unit you kill puts half of what it costs to build into your core."),
    absolution = new Reward("absolution", "Absolution", Rarity.relic, "Removes 2 random curses."),

    duoDrills = new Reward("duo-drills", "Duo Drills", Rarity.common, "Duos fire 50% faster."),
    flakBarrage = new Reward("flak-barrage", "Flak Barrage", Rarity.common, "Scatters fire 50% faster."),
    hailStorm = new Reward("hail-storm", "Hail Storm", Rarity.common, "Hails fire 50% faster."),
    conductor = new Reward("conductor", "Conductor", Rarity.common, "Arcs fire 50% faster."),
    capacitors = new Reward("capacitors", "Capacitors", Rarity.common, "Lancers charge 50% faster."),
    highPressure = new Reward("high-pressure", "High Pressure", Rarity.common, "Waves spray 50% faster."),
    copperPlating = new Reward("copper-plating", "Copper Plating", Rarity.common, "Copper walls have 100% more health."),
    fieldMedics = new Reward("field-medics", "Field Medics", Rarity.common, "Menders and mend projectors heal 50% faster."),
    sharpBits = new Reward("sharp-bits", "Sharp Bits", Rarity.common, "Mechanical and pneumatic drills mine 50% faster."),
    graphiteRollers = new Reward("graphite-rollers", "Graphite Rollers", Rarity.common, "Graphite presses work 50% faster."),
    siliconLine = new Reward("silicon-line", "Silicon Line", Rarity.common, "Silicon smelters work 50% faster."),
    miningCrew = new Reward("mining-crew", "Mining Crew", Rarity.common, "Monos mine 100% faster."),
    infantryDrills = new Reward("infantry-drills", "Infantry Drills", Rarity.common, "Daggers, Maces and Fortresses deal 25% more damage."),
    strikeWings = new Reward("strike-wings", "Strike Wings", Rarity.common, "Flares and Horizons move 20% faster and reload 30% faster."),

    hardenedShields = new Reward("hardened-shields", "Hardened Shields", Rarity.uncommon, "All your unit and structure shields are 100% stronger."),
    salvoClips = new Reward("salvo-clips", "Salvo Clips", Rarity.uncommon, "Salvos fire 50% faster."),
    rippleEffect = new Reward("ripple-effect", "Ripple Effect", Rarity.uncommon, "Ripples fire 50% faster."),
    titaniumPlating = new Reward("titanium-plating", "Titanium Plating", Rarity.uncommon, "Titanium walls have 100% more health."),
    focusedLasers = new Reward("focused-lasers", "Focused Lasers", Rarity.uncommon, "Laser drills and airblast drills mine 50% faster."),
    deepWells = new Reward("deep-wells", "Deep Wells", Rarity.uncommon, "Water extractors and oil extractors pump 50% faster."),
    hotKilns = new Reward("hot-kilns", "Hot Kilns", Rarity.uncommon, "Kilns and plastanium compressors work 50% faster."),
    exoSuits = new Reward("exo-suits", "Exo Suits", Rarity.uncommon, "Novas, Pulsars and Quasars have 50% more health and deal 25% more damage."),
    bugSwarm = new Reward("bug-swarm", "Bug Swarm", Rarity.uncommon, "Crawlers, Atraxes and Spirocts move 20% faster and have 50% more health."),
    seaLegs = new Reward("sea-legs", "Sea Legs", Rarity.uncommon, "Rissos, Minkes and Brydes have 50% more health and reload 25% faster."),

    ironLegion = new Reward("iron-legion", "Iron Legion", Rarity.rare, "New Daggers, Maces and Fortresses get a shield as big as their health."),
    airSuperiority = new Reward("air-superiority", "Air Superiority", Rarity.rare, "Zeniths, Antumbras and Eclipses have 100% more health."),
    fuseBox = new Reward("fuse-box", "Fuse Box", Rarity.rare, "Fuses fire 100% faster.");

    public static final Seq<Reward> all = Seq.with(coreLink, livingMetal, gracePeriod, prefab, biggerCore, warFactory, recycle, fieldCommander, oreMagnet, scrapRebirth, lastStand, plunder, absolution,
        duoDrills, flakBarrage, hailStorm, conductor, capacitors, highPressure, copperPlating, fieldMedics, sharpBits, graphiteRollers, siliconLine, miningCrew, infantryDrills, strikeWings,
        hardenedShields, salvoClips, rippleEffect, titaniumPlating, focusedLasers, deepWells, hotKilns, exoSuits, bugSwarm, seaLegs,
        ironLegion, airSuperiority, fuseBox);

    public static void load(){
        duoDrills.faster(0.5f, Blocks.duo);
        flakBarrage.faster(0.5f, Blocks.scatter);
        hailStorm.faster(0.5f, Blocks.hail);
        conductor.faster(0.5f, Blocks.arc);
        capacitors.faster(0.5f, Blocks.lancer);
        highPressure.faster(0.5f, Blocks.wave);
        copperPlating.tougher(1f, Blocks.copperWall, Blocks.copperWallLarge);
        fieldMedics.faster(0.5f, Blocks.mender, Blocks.mendProjector);
        sharpBits.faster(0.5f, Blocks.pneumaticDrill, Blocks.mechanicalDrill);
        graphiteRollers.faster(0.5f, Blocks.graphitePress);
        siliconLine.faster(0.5f, Blocks.siliconSmelter);
        miningCrew.iconContent = UnitTypes.mono;
        infantryDrills.units(UnitTypes.mace, 0f, 0.25f, 0f, 0f, UnitTypes.dagger, UnitTypes.mace, UnitTypes.fortress);
        strikeWings.units(UnitTypes.flare, 0f, 0f, 0.2f, 0.3f, UnitTypes.flare, UnitTypes.horizon);

        hardenedShields.iconContent = Blocks.forceProjector;
        salvoClips.faster(0.5f, Blocks.salvo);
        rippleEffect.faster(0.5f, Blocks.ripple);
        titaniumPlating.tougher(1f, Blocks.titaniumWall, Blocks.titaniumWallLarge);
        focusedLasers.faster(0.5f, Blocks.laserDrill, Blocks.blastDrill);
        deepWells.faster(0.5f, Blocks.waterExtractor, Blocks.oilExtractor);
        hotKilns.faster(0.5f, Blocks.kiln, Blocks.plastaniumCompressor);
        exoSuits.units(UnitTypes.nova, 0.5f, 0.25f, 0f, 0f, UnitTypes.nova, UnitTypes.pulsar, UnitTypes.quasar);
        bugSwarm.units(UnitTypes.crawler, 0.5f, 0f, 0.2f, 0f, UnitTypes.crawler, UnitTypes.atrax, UnitTypes.spiroct);
        seaLegs.units(UnitTypes.risso, 0.5f, 0f, 0f, 0.25f, UnitTypes.risso, UnitTypes.minke, UnitTypes.bryde);

        ironLegion.iconContent = UnitTypes.dagger;
        airSuperiority.units(UnitTypes.zenith, 1f, 0f, 0f, 0f, UnitTypes.zenith, UnitTypes.antumbra, UnitTypes.eclipse);
        fuseBox.faster(1f, Blocks.fuse);
        fieldCommander.unitHealth = 7f;
        fieldCommander.unitSpeed = 1f;

        for(Reward reward : all){
            if(reward.units.isEmpty() && reward != fieldCommander) continue;
            //one shared effect per modifier: units read its multipliers every frame, so changing them updates every unit
            reward.effect = new StatusEffect("modifier-" + reward.id){{
                permanent = true;
                show = false;
                color = reward.rarity.color;
            }};
            reward.setStacks(0);
        }
    }

    public static Reward get(String id){
        return all.find(r -> r.id.equals(id));
    }

    public static Seq<Reward> rollRelics(ObjectIntMap<Reward> owned, int curses, Rand rand){
        Seq<Reward> pool = all.select(r -> r.isRelic() && owned.get(r, 0) == 0 && (r != absolution || curses > 0)), out = new Seq<>();
        while(out.size < 3 && pool.any()){
            out.add(pool.remove(rand.random(pool.size - 1)));
        }
        return out;
    }

    public static Seq<Reward> rollModifiers(ObjectIntMap<Reward> owned, Rand rand){
        Seq<Reward> pool = all.select(r -> !r.isRelic() && owned.get(r, 0) == 0), out = new Seq<>();
        while(out.size < 3 && pool.any()){
            Seq<Rarity> left = Seq.with(Rarity.common, Rarity.uncommon, Rarity.rare).select(k -> pool.contains(r -> r.rarity == k));
            int pick = rand.random(left.sum(k -> k.weight) - 1);
            Rarity rarity = left.peek();
            for(Rarity k : left){
                pick -= k.weight;
                if(pick < 0){
                    rarity = k;
                    break;
                }
            }
            Rarity chosenRarity = rarity;
            Seq<Reward> group = pool.select(r -> r.rarity == chosenRarity);
            Reward chosen = group.get(rand.random(group.size - 1));
            out.add(chosen);
            pool.remove(chosen);
        }
        return out;
    }

    public static Seq<Reward> rerollChoices(ObjectIntMap<Reward> owned, Seq<Reward> shown, Reward old){
        return all.select(r -> r.rarity == old.rarity && owned.get(r, 0) == 0 && !shown.contains(r));
    }

    public static boolean ironLegionType(UnitType type){
        return type == UnitTypes.dagger || type == UnitTypes.mace || type == UnitTypes.fortress;
    }
}

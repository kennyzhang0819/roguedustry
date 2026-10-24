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
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.storage.*;

import static mindustry.Vars.*;

public class Rewards{

    public enum Rarity{
        common("Modifier", Color.valueOf("6f9dff"), 1),
        uncommon("Modifier", Color.valueOf("6f9dff"), 1),
        rare("Modifier", Color.valueOf("6f9dff"), 1),
        relic("Relic", Color.valueOf("ffd37f"), 0),
        copies("Supply", Color.valueOf("e8e8e8"), 0);

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
        public float speed, wallHealth, damage, range, splash, cost, buildTime, regen;
        public final Seq<UnitType> units = new Seq<>();
        public float unitHealth, unitDamage, unitSpeed, unitReload, unitBuild;
        public boolean coreUnits;
        public @Nullable StatusEffect effect;
        public @Nullable Block copyOf;

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

        void faster(float perStack, UnlockableContent icon, Seq<Block> targets){
            speed = perStack;
            blocks.addAll(targets);
            iconContent = icon;
        }

        void tougher(float perStack, UnlockableContent icon, Seq<Block> targets){
            wallHealth = perStack;
            blocks.addAll(targets);
            iconContent = icon;
        }

        public boolean available(){
            if(copyOf != null) return Arena.offered(copyOf);
            if(blocks.any()) return RogueRun.current != null && blocks.contains(b -> RogueRun.current.copies.get(b, 0) > 0);
            if(units.any()) return units.contains(UnitType::unlocked);
            return iconContent == null || iconContent.unlocked();
        }

        void turret(Block block){
            blocks.add(block);
            iconContent = block;
        }

        void group(Block... targets){
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
            effect.buildSpeedMultiplier = 1f + unitBuild * stacks;
        }

        public boolean isRelic(){
            return rarity == Rarity.relic;
        }
    }

    public static final float modifierShare = 0.35f;
    public static final int lastStandRadius = 50, giftAmount = 10000, giftChoices = 3;

    public static final Reward
    livingMetal = new Reward("living-metal", "Living Metal", Rarity.relic, "All your structures repair 2% of their health every second."),
    gracePeriod = new Reward("grace-period", "Grace Period", Rarity.relic, "Get " + giftAmount / 1000 + "k each of " + giftChoices + " resources you choose. Whatever doesn't fit in your core is lost."),
    prefab = new Reward("prefab", "Prefab", Rarity.relic, "Every structure you build finishes instantly. You still pay its cost."),
    warFactory = new Reward("war-factory", "War Factory", Rarity.relic, "Unit factories and reconstructors work three times as fast."),
    recycle = new Reward("recycle", "Recycle", Rarity.relic, "When your units die, they have a 50% chance to come back at your nearest core."),
    fieldCommander = new Reward("field-commander", "Field Commander", Rarity.relic, "Your core unit (Alpha, Beta, Gamma) has 32 times the health and moves twice as fast."),
    oreMagnet = new Reward("ore-magnet", "Ore Magnet", Rarity.relic, "All drills and extractors mine three times as fast."),
    scrapRebirth = new Reward("scrap-rebirth", "Scrap Rebirth", Rarity.relic, "When one of your structures is destroyed, half its cost goes back into your core."),
    lastStand = new Reward("last-stand", "Last Stand", Rarity.relic, "Once, when a core drops below 25% health, every enemy unit within " + lastStandRadius + " tiles of it dies."),
    absolution = new Reward("absolution", "Absolution", Rarity.relic, "Removes 2 random curses."),

    swarmDoctrine = new Reward("swarm-doctrine", "Swarm Doctrine", Rarity.common, "Duos cost half as much, fire 50% faster and deal 50% more damage."),
    gatling = new Reward("gatling", "Gatling", Rarity.uncommon, "A Duo speeds up the longer it fires, up to 5 times as fast. It cools down after 2 seconds without shooting."),
    phalanx = new Reward("phalanx", "Phalanx", Rarity.rare, "Touching Duos link up: each one fires 10% faster and hits 10% harder for every other Duo in its group."),
    flakWall = new Reward("flak-wall", "Flak Wall", Rarity.common, "Scatters fire twice as fast and their flak hits twice as hard."),
    groundFlak = new Reward("ground-flak", "Ground Flak", Rarity.uncommon, "Scatters also shoot ground units."),
    skyfall = new Reward("skyfall", "Skyfall", Rarity.rare, "Air units a Scatter shoots down crash like bombs, dealing their max health as damage to everything below."),
    inferno = new Reward("inferno", "Inferno", Rarity.common, "Scorches reach 50% farther, burn enemies twice as long and have double health."),
    wildfire = new Reward("wildfire", "Wildfire", Rarity.uncommon, "Enemies that die while burning explode in flames that set every enemy within 3 tiles on fire."),
    phoenix = new Reward("phoenix", "Phoenix", Rarity.rare, "A destroyed Scorch blows up in a firestorm and rebuilds itself for free 10 seconds later, every time."),
    siegeShells = new Reward("siege-shells", "Siege Shells", Rarity.common, "Hails reach 50% farther and their shells hit twice as hard."),
    shockField = new Reward("shock-field", "Shock Field", Rarity.uncommon, "Hail shells leave an electric field for 8 seconds that shocks ground enemies inside it."),
    meteor = new Reward("meteor", "Meteor", Rarity.rare, "Every 10th Hail shell is a huge, slow meteor that flattens everything within 6 tiles."),

    junkyard = new Reward("junkyard", "Junkyard", Rarity.common, "Scrap walls cost 75% less and have double health."),
    selfAssembly = new Reward("self-assembly", "Self-Assembly", Rarity.uncommon, "A destroyed scrap wall rebuilds itself for free 5 seconds later."),
    junkGolem = new Reward("junk-golem", "Junk Golem", Rarity.rare, "Every 20 scrap walls destroyed build you a free Fortress at your core."),
    massProduction = new Reward("mass-production", "Mass Production", Rarity.common, "Copper walls cost 75% less and build twice as fast."),
    thorns = new Reward("thorns", "Thorns", Rarity.uncommon, "Copper walls hit back at whoever damaged them for 50% of the damage taken."),
    pillbox = new Reward("pillbox", "Pillbox", Rarity.rare, "Every copper wall shoots at enemies within 6 tiles, like a weak Duo that needs no ammo."),
    plating = new Reward("plating", "Plating", Rarity.common, "Titanium walls cost half and have double health."),
    shrapnel = new Reward("shrapnel", "Shrapnel", Rarity.uncommon, "A destroyed titanium wall bursts, dealing its max health to enemies within 4 tiles."),
    fallback = new Reward("fallback", "Fallback", Rarity.rare, "A destroyed titanium wall leaves a free copper wall in its place."),
    selfSealing = new Reward("self-sealing", "Self-Sealing", Rarity.common, "Plastanium walls cost half and repair 3% of their health every second."),
    tarPit = new Reward("tar-pit", "Tar Pit", Rarity.uncommon, "Ground enemies touching a plastanium wall are stuck in place."),
    elastic = new Reward("elastic", "Elastic", Rarity.rare, "Every bullet that hits a plastanium wall bounces back at the enemy with double damage."),
    denseCore = new Reward("dense-core", "Dense Core", Rarity.common, "Thorium walls cost half and have triple health."),
    radiation = new Reward("radiation", "Radiation", Rarity.uncommon, "Thorium walls deal 30 damage per second to every enemy within 3 tiles."),
    criticalMass = new Reward("critical-mass", "Critical Mass", Rarity.rare, "A destroyed thorium wall explodes for 2,000 damage across 10 tiles."),
    phaseLattice = new Reward("phase-lattice", "Phase Lattice", Rarity.common, "Phase walls cost half and deflect bullets twice as often."),
    repulsor = new Reward("repulsor", "Repulsor", Rarity.uncommon, "Enemies that touch a phase wall are thrown 10 tiles back the way they came."),
    refraction = new Reward("refraction", "Refraction", Rarity.rare, "Every bullet a phase wall deflects splits into 3 that home in on enemies."),
    overcharge = new Reward("overcharge", "Overcharge", Rarity.common, "Surge walls cost half and throw lightning three times as often."),
    teslaFence = new Reward("tesla-fence", "Tesla Fence", Rarity.uncommon, "Every surge wall zaps an enemy within 5 tiles each second."),
    powerGrid = new Reward("power-grid", "Power Grid", Rarity.rare, "When a surge wall is hit, every surge wall touching it throws lightning too."),
    fieldHospital = new Reward("field-hospital", "Field Hospital", Rarity.common, "Menders, mend projectors and repair points work three times as fast."),
    triage = new Reward("triage", "Triage", Rarity.uncommon, "Menders fully heal any structure in range that drops below 20% health, once every 10 seconds each."),
    resurrection = new Reward("resurrection", "Resurrection", Rarity.rare, "Menders rebuild destroyed structures in their range for free 5 seconds later."),
    fortressShield = new Reward("fortress-shield", "Fortress", Rarity.common, "Force fields are three times as strong and recover twice as fast after breaking."),
    mirror = new Reward("mirror", "Mirror", Rarity.uncommon, "Enemy bullets that hit your force fields are reflected back."),
    backlash = new Reward("backlash", "Backlash", Rarity.rare, "When a force field breaks, it deals its full strength as damage to every enemy inside."),
    overclocked = new Reward("overclocked", "Overclocked", Rarity.common, "Mechanical drills cost half and mine twice as fast."),
    wideBore = new Reward("wide-bore", "Wide Bore", Rarity.uncommon, "Mechanical drills also mine the ore up to 2 tiles around them."),
    luckyStrike = new Reward("lucky-strike", "Lucky Strike", Rarity.rare, "Each working mechanical drill has a 2% chance every second to find 25 of your rarest unlocked resource."),
    airPressure = new Reward("air-pressure", "Air Pressure", Rarity.common, "Pneumatic drills mine twice as fast and always count as water-boosted."),
    doubleHaul = new Reward("double-haul", "Double Haul", Rarity.uncommon, "Every item a pneumatic drill mines comes out as 2."),
    deepVein = new Reward("deep-vein", "Deep Vein", Rarity.rare, "Pneumatic drills mine titanium from any ore."),
    focusedLaser = new Reward("focused-laser", "Focused", Rarity.common, "Laser drills cost half and mine three times as fast."),
    prospector = new Reward("prospector", "Prospector", Rarity.uncommon, "Laser drills mine every kind of ore under them at once, not just the most common one."),
    alchemy = new Reward("alchemy", "Alchemy", Rarity.rare, "Laser drills also put 1 thorium into your core for every 10 items they mine."),
    turbine = new Reward("turbine", "Turbine", Rarity.common, "Airblast drills cost half and mine twice as fast."),
    overpressure = new Reward("overpressure", "Overpressure", Rarity.uncommon, "Every 30 seconds, each airblast drill puts a minute's worth of ore straight into your core."),
    coreTap = new Reward("core-tap", "Core Tap", Rarity.rare, "Each airblast drill mines 25% faster for every other airblast drill you own."),
    aquifer = new Reward("aquifer", "Aquifer", Rarity.common, "Water extractors cost half and pump three times as fast."),
    sprinkler = new Reward("sprinkler", "Sprinkler", Rarity.uncommon, "Water extractors soak enemies within 6 tiles and put out fires on your buildings nearby."),
    geyser = new Reward("geyser", "Geyser", Rarity.rare, "Every 10 seconds, water extractors erupt and knock back every enemy within 8 tiles."),
    greenhouse = new Reward("greenhouse", "Greenhouse", Rarity.common, "Cultivators cost half and grow three times as fast."),
    sporeCloud = new Reward("spore-cloud", "Spore Cloud", Rarity.uncommon, "Cultivators slow enemies within 6 tiles and deal 10 damage to them every second."),
    fungalArmy = new Reward("fungal-army", "Fungal Army", Rarity.rare, "Each cultivator grows you a free Crawler every 30 seconds, up to 3 each."),
    pressureWell = new Reward("pressure-well", "Pressure Well", Rarity.common, "Oil extractors cost half and pump twice as fast."),
    oilSlick = new Reward("oil-slick", "Oil Slick", Rarity.uncommon, "Oil extractors tar enemies within 6 tiles, slowing them and making them easy to burn."),
    firewall = new Reward("firewall", "Firewall", Rarity.rare, "Tarred enemies within 10 tiles of an oil extractor burst into flames when hit.");

    public static final Seq<Reward> all = Seq.with(livingMetal, gracePeriod, prefab, warFactory, recycle, fieldCommander, oreMagnet, scrapRebirth, lastStand, absolution,
        swarmDoctrine, gatling, phalanx, flakWall, groundFlak, skyfall, inferno, wildfire, phoenix, siegeShells, shockField, meteor,
        junkyard, selfAssembly, junkGolem, massProduction, thorns, pillbox, plating, shrapnel, fallback, selfSealing, tarPit, elastic, denseCore, radiation, criticalMass, phaseLattice, repulsor, refraction, overcharge, teslaFence, powerGrid, fieldHospital, triage, resurrection, fortressShield, mirror, backlash, overclocked, wideBore, luckyStrike, airPressure, doubleHaul, deepVein, focusedLaser, prospector, alchemy, turbine, overpressure, coreTap, aquifer, sprinkler, geyser, greenhouse, sporeCloud, fungalArmy, pressureWell, oilSlick, firewall);

    public static void load(){
        for(Block block : Arena.buildable){
            int n = Arena.cardSize(block);
            Reward card = new Reward("copies-" + block.name, block.localizedName, Rarity.copies, "+" + n + " " + block.localizedName);
            card.copyOf = block;
            card.iconContent = block;
            all.add(card);
        }
        swarmDoctrine.turret(Blocks.duo);
        swarmDoctrine.speed = swarmDoctrine.damage = 0.5f;
        swarmDoctrine.cost = 0.5f;
        gatling.turret(Blocks.duo);
        phalanx.turret(Blocks.duo);
        flakWall.turret(Blocks.scatter);
        flakWall.speed = 1f;
        flakWall.splash = 1f;
        groundFlak.turret(Blocks.scatter);
        skyfall.turret(Blocks.scatter);
        inferno.turret(Blocks.scorch);
        inferno.range = 0.5f;
        inferno.wallHealth = 1f;
        wildfire.turret(Blocks.scorch);
        phoenix.turret(Blocks.scorch);
        siegeShells.turret(Blocks.hail);
        siegeShells.range = 0.5f;
        siegeShells.splash = 1f;
        shockField.turret(Blocks.hail);
        meteor.turret(Blocks.hail);
        junkyard.group(Blocks.scrapWall, Blocks.scrapWallLarge, Blocks.scrapWallHuge, Blocks.scrapWallGigantic);
        selfAssembly.group(Blocks.scrapWall, Blocks.scrapWallLarge, Blocks.scrapWallHuge, Blocks.scrapWallGigantic);
        junkGolem.group(Blocks.scrapWall, Blocks.scrapWallLarge, Blocks.scrapWallHuge, Blocks.scrapWallGigantic);
        massProduction.group(Blocks.copperWall, Blocks.copperWallLarge);
        thorns.group(Blocks.copperWall, Blocks.copperWallLarge);
        pillbox.group(Blocks.copperWall, Blocks.copperWallLarge);
        plating.group(Blocks.titaniumWall, Blocks.titaniumWallLarge);
        shrapnel.group(Blocks.titaniumWall, Blocks.titaniumWallLarge);
        fallback.group(Blocks.titaniumWall, Blocks.titaniumWallLarge);
        selfSealing.group(Blocks.plastaniumWall, Blocks.plastaniumWallLarge);
        tarPit.group(Blocks.plastaniumWall, Blocks.plastaniumWallLarge);
        elastic.group(Blocks.plastaniumWall, Blocks.plastaniumWallLarge);
        denseCore.group(Blocks.thoriumWall, Blocks.thoriumWallLarge);
        radiation.group(Blocks.thoriumWall, Blocks.thoriumWallLarge);
        criticalMass.group(Blocks.thoriumWall, Blocks.thoriumWallLarge);
        phaseLattice.group(Blocks.phaseWall, Blocks.phaseWallLarge);
        repulsor.group(Blocks.phaseWall, Blocks.phaseWallLarge);
        refraction.group(Blocks.phaseWall, Blocks.phaseWallLarge);
        overcharge.group(Blocks.surgeWall, Blocks.surgeWallLarge);
        teslaFence.group(Blocks.surgeWall, Blocks.surgeWallLarge);
        powerGrid.group(Blocks.surgeWall, Blocks.surgeWallLarge);
        fieldHospital.group(Blocks.mender, Blocks.mendProjector, Blocks.repairPoint, Blocks.repairTurret);
        triage.group(Blocks.mender, Blocks.mendProjector, Blocks.repairPoint, Blocks.repairTurret);
        resurrection.group(Blocks.mender, Blocks.mendProjector, Blocks.repairPoint, Blocks.repairTurret);
        fortressShield.group(Blocks.forceProjector);
        mirror.group(Blocks.forceProjector);
        backlash.group(Blocks.forceProjector);
        overclocked.group(Blocks.mechanicalDrill);
        wideBore.group(Blocks.mechanicalDrill);
        luckyStrike.group(Blocks.mechanicalDrill);
        airPressure.group(Blocks.pneumaticDrill);
        doubleHaul.group(Blocks.pneumaticDrill);
        deepVein.group(Blocks.pneumaticDrill);
        focusedLaser.group(Blocks.laserDrill);
        prospector.group(Blocks.laserDrill);
        alchemy.group(Blocks.laserDrill);
        turbine.group(Blocks.blastDrill);
        overpressure.group(Blocks.blastDrill);
        coreTap.group(Blocks.blastDrill);
        aquifer.group(Blocks.waterExtractor);
        sprinkler.group(Blocks.waterExtractor);
        geyser.group(Blocks.waterExtractor);
        greenhouse.group(Blocks.cultivator);
        sporeCloud.group(Blocks.cultivator);
        fungalArmy.group(Blocks.cultivator);
        pressureWell.group(Blocks.oilExtractor);
        oilSlick.group(Blocks.oilExtractor);
        firewall.group(Blocks.oilExtractor);
        junkyard.cost = 0.75f;
        junkyard.wallHealth = 1f;
        massProduction.cost = 0.75f;
        massProduction.buildTime = 0.5f;
        plating.cost = 0.5f;
        plating.wallHealth = 1f;
        selfSealing.cost = 0.5f;
        selfSealing.regen = 0.03f;
        denseCore.cost = 0.5f;
        denseCore.wallHealth = 2f;
        phaseLattice.cost = 0.5f;
        overcharge.cost = 0.5f;
        fieldHospital.speed = 2f;
        overclocked.cost = 0.5f;
        overclocked.speed = 1f;
        airPressure.speed = 1f;
        focusedLaser.cost = 0.5f;
        focusedLaser.speed = 2f;
        turbine.cost = 0.5f;
        turbine.speed = 1f;
        aquifer.cost = 0.5f;
        aquifer.speed = 2f;
        greenhouse.cost = 0.5f;
        greenhouse.speed = 2f;
        pressureWell.cost = 0.5f;
        pressureWell.speed = 1f;
        fieldCommander.coreUnits = true;
        fieldCommander.unitHealth = 31f;
        fieldCommander.unitSpeed = 1f;

        for(Reward reward : all){
            if(reward.units.isEmpty() && !reward.coreUnits) continue;
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
        Seq<Reward> econ = Seq.with(gracePeriod, prefab, warFactory, oreMagnet, scrapRebirth, recycle);
        Seq<Reward> pool = all.select(r -> r.isRelic() && !econ.contains(r) && owned.get(r, 0) == 0 && (r != absolution || curses > 0)), out = new Seq<>();
        while(out.size < 3 && pool.any()){
            out.add(pool.remove(rand.random(pool.size - 1)));
        }
        return out;
    }

    public static Seq<Reward> rollModifiers(ObjectIntMap<Reward> owned, Rand rand){
        Seq<Reward> mods = all.select(r -> !r.isRelic() && r.copyOf == null && owned.get(r, 0) == 0 && r.available());
        Seq<Reward> cards = all.select(r -> r.copyOf != null && r.available()), out = new Seq<>();
        while(out.size < 3 && (mods.any() || cards.any())){
            Seq<Reward> pool = cards.isEmpty() || (mods.any() && rand.chance(modifierShare)) ? mods : cards;
            out.add(pool.remove(rand.random(pool.size - 1)));
        }
        return out;
    }

    public static Seq<Reward> rerollChoices(ObjectIntMap<Reward> owned, Seq<Reward> shown, Reward old){
        return all.select(r -> !r.isRelic() && (r.copyOf != null || owned.get(r, 0) == 0) && !shown.contains(r) && r.available());
    }

}

package roguedustry;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.graphics.*;
import mindustry.type.*;

public class Rewards{

    public enum Rarity{
        common("Common", Color.valueOf("84f491"), 60),
        uncommon("Uncommon", Color.valueOf("6f9dff"), 30),
        rare("Rare", Color.valueOf("ffd37f"), 10),
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
        public final int max;

        Reward(String id, String name, Rarity rarity, int max, String description){
            this.id = id;
            this.name = name;
            this.rarity = rarity;
            this.max = max;
            this.description = description;
            if(rarity == Rarity.relic ? max != 1 : max < 2){
                throw new IllegalArgumentException(name + ": relics can't stack and modifiers must stack");
            }
        }

        public boolean hasIcon(){
            return Core.atlas.has("roguedustry-" + id);
        }

        public TextureRegion icon(){
            return Core.atlas.find("roguedustry-" + id);
        }

        public boolean isRelic(){
            return rarity == Rarity.relic;
        }
    }

    public static final int wavesPerModifier = 10, prefabCount = 100, lastStandRadius = 50;

    public static StatusEffect commanderEffect;

    public static final Reward
    coreLink = new Reward("core-link", "Core Link", Rarity.relic, 1, "All drills send what they mine straight into your core. Does not apply to extractors."),
    livingMetal = new Reward("living-metal", "Living Metal", Rarity.relic, 1, "All your structures repair 2% of their health every second."),
    gracePeriod = new Reward("grace-period", "Grace Period", Rarity.relic, 1, "On each new sector, the first wave comes 3 minutes later."),
    prefab = new Reward("prefab", "Prefab", Rarity.relic, 1, "On each sector, the first " + prefabCount + " structures you place that aren't conveyors finish instantly. You still pay their cost."),
    biggerCore = new Reward("bigger-core", "Bigger Core", Rarity.relic, 1, "Your cores hold twice as many items."),
    warFactory = new Reward("war-factory", "War Factory", Rarity.relic, 1, "Unit factories and reconstructors work twice as fast."),
    recycle = new Reward("recycle", "Recycle", Rarity.relic, 1, "When your units die, they have a 50% chance to come back at your nearest core."),
    fieldCommander = new Reward("field-commander", "Field Commander", Rarity.relic, 1, "Your core unit (Alpha, Beta, Gamma) has 8 times the health and moves twice as fast."),
    oreMagnet = new Reward("ore-magnet", "Ore Magnet", Rarity.relic, 1, "All drills and extractors mine three times as fast."),
    scrapRebirth = new Reward("scrap-rebirth", "Scrap Rebirth", Rarity.relic, 1, "When one of your structures is destroyed, half its cost goes back into your core."),
    lastStand = new Reward("last-stand", "Last Stand", Rarity.relic, 1, "Once per sector, when a core drops below 25% health, every enemy unit within " + lastStandRadius + " tiles of it dies."),
    absolution = new Reward("absolution", "Absolution", Rarity.relic, 1, "Removes 2 random curses."),

    duoDrills = new Reward("duo-drills", "Duo Drills", Rarity.common, 3, "Duos fire 50% faster."),
    hardenedShields = new Reward("hardened-shields", "Hardened Shields", Rarity.uncommon, 2, "All your unit and structure shields are 100% stronger."),
    ironLegion = new Reward("iron-legion", "Iron Legion", Rarity.rare, 2, "New Daggers, Maces and Fortresses get a shield as big as their health.");

    public static final Seq<Reward> all = Seq.with(coreLink, livingMetal, gracePeriod, prefab, biggerCore, warFactory, recycle, fieldCommander, oreMagnet, scrapRebirth, lastStand, absolution, duoDrills, hardenedShields, ironLegion);

    public static void load(){
        commanderEffect = new StatusEffect("field-commander"){{
            healthMultiplier = 8f;
            speedMultiplier = 2f;
            permanent = true;
            color = Rarity.relic.color;
        }};
    }

    public static Reward get(String id){
        return all.find(r -> r.id.equals(id));
    }

    public static Seq<Reward> rollRelics(ObjectIntMap<Reward> owned, int curses, Rand rand){
        Seq<Reward> pool = all.select(r -> r.isRelic() && owned.get(r, 0) < r.max && (r != absolution || curses > 0)), out = new Seq<>();
        while(out.size < 3 && pool.any()){
            out.add(pool.remove(rand.random(pool.size - 1)));
        }
        return out;
    }

    public static Seq<Reward> rollModifiers(ObjectIntMap<Reward> owned, Rand rand){
        Seq<Reward> pool = all.select(r -> !r.isRelic() && owned.get(r, 0) < r.max), out = new Seq<>();
        while(out.size < 3 && pool.any()){
            int total = pool.sum(r -> r.rarity.weight), pick = rand.random(total - 1);
            for(Reward r : pool){
                pick -= r.rarity.weight;
                if(pick < 0){
                    out.add(r);
                    pool.remove(r);
                    break;
                }
            }
        }
        return out;
    }

    public static boolean ironLegionType(UnitType type){
        return type == UnitTypes.dagger || type == UnitTypes.mace || type == UnitTypes.fortress;
    }
}

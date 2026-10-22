package roguedustry;

import arc.graphics.*;
import arc.math.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.graphics.*;
import mindustry.type.*;

public class Rewards{

    public enum Rarity{
        common("Common", Color.white, 60),
        uncommon("Uncommon", Pal.heal, 30),
        rare("Rare", Pal.lancerLaser, 10),
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
        }

        public boolean isRelic(){
            return rarity == Rarity.relic;
        }
    }

    public static final int modifiersPerMap = 3;

    public static final Reward
    coreLink = new Reward("core-link", "Core Link", Rarity.relic, 1, "All drills send what they mine straight into your core."),
    overclock = new Reward("overclock", "Overclock", Rarity.relic, 1, "Everything you build works 50% faster."),
    livingMetal = new Reward("living-metal", "Living Metal", Rarity.relic, 1, "All your blocks repair 2% of their health every second."),

    duoDrills = new Reward("duo-drills", "Duo Drills", Rarity.common, 3, "Duos fire 50% faster."),
    hardenedShields = new Reward("hardened-shields", "Hardened Shields", Rarity.uncommon, 2, "All your unit and structure shields are 100% stronger."),
    ironLegion = new Reward("iron-legion", "Iron Legion", Rarity.rare, 1, "New Daggers, Maces and Fortresses get a shield as big as their health.");

    public static final Seq<Reward> all = Seq.with(coreLink, overclock, livingMetal, duoDrills, hardenedShields, ironLegion);

    public static Reward get(String id){
        return all.find(r -> r.id.equals(id));
    }

    public static Seq<Reward> rollRelics(ObjectIntMap<Reward> owned, Rand rand){
        Seq<Reward> pool = all.select(r -> r.isRelic() && owned.get(r, 0) < r.max), out = new Seq<>();
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

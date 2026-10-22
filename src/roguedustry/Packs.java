package roguedustry;

import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.world.*;

import static mindustry.content.Blocks.*;

public class Packs{

    public static class Entry{
        public final Block block;
        public final int amount;

        Entry(Block block, int amount){
            this.block = block;
            this.amount = amount;
        }
    }

    public static class Pack{
        public final String name;
        public final int tier;
        public final Seq<Entry> entries = new Seq<>();

        Pack(int tier, String name, Object... blockAmountPairs){
            this.tier = tier;
            this.name = name;
            for(int i = 0; i < blockAmountPairs.length; i += 2){
                entries.add(new Entry((Block)blockAmountPairs[i], (Integer)blockAmountPairs[i + 1]));
            }
        }
    }

    public static final int[] tierLevels = {1, 3, 6, 9};
    public static final int[] tierPrices = {60, 180, 500, 1200};
    public static final int startingCopper = 60, rerollPrice = 20, shopSize = 4;

    static @Nullable OrderedMap<Block, Integer> drills;

    public static OrderedMap<Block, Integer> drills(){
        if(drills == null){
            drills = new OrderedMap<>();
            drills.put(mechanicalDrill, 20);
            drills.put(pneumaticDrill, 50);
            drills.put(laserDrill, 200);
            drills.put(blastDrill, 500);
        }
        return drills;
    }

    public static int price(Pack pack){
        return tierPrices[pack.tier];
    }

    public static Seq<Entry> startingKit(){
        return new Pack(0, "Starting kit", duo, 4, copperWall, 12, mender, 1).entries;
    }

    public static Seq<Pack> all(){
        return Seq.with(
            new Pack(0, "Duo Battery", duo, 3),
            new Pack(0, "Flak Nest", scatter, 2),
            new Pack(0, "Artillery", hail, 2),
            new Pack(0, "Copper Line", copperWall, 16),
            new Pack(0, "Arc Coils", arc, 2),
            new Pack(0, "Field Medic", mender, 2),
            new Pack(0, "Mixed Bag", duo, 2, copperWall, 8),

            new Pack(1, "Lancer Pair", lancer, 2),
            new Pack(1, "Water Cannon", wave, 1, titaniumWall, 6),
            new Pack(1, "Salvo", salvo, 1),
            new Pack(1, "Titanium Bulwark", titaniumWallLarge, 6),
            new Pack(1, "Mend Projector", mendProjector, 1),
            new Pack(1, "Swarmer", swarmer, 1),
            new Pack(1, "Point Defense", segment, 1),
            new Pack(1, "Minefield", shockMine, 12),
            new Pack(1, "Tractor Beam", parallax, 1),
            new Pack(1, "Scorchers", scorch, 3),

            new Pack(2, "Ripple", ripple, 1),
            new Pack(2, "Cyclone", cyclone, 1),
            new Pack(2, "Fuse", fuse, 1),
            new Pack(2, "Force Field", forceProjector, 1),
            new Pack(2, "Thorium Fortress", thoriumWallLarge, 6),
            new Pack(2, "Tsunami", tsunami, 1),
            new Pack(2, "Overdrive", overdriveProjector, 1),
            new Pack(2, "Plastanium Wall", plastaniumWallLarge, 6),

            new Pack(3, "Spectre", spectre, 1),
            new Pack(3, "Meltdown", meltdown, 1),
            new Pack(3, "Foreshadow", foreshadow, 1),
            new Pack(3, "Surge Bastion", surgeWallLarge, 6),
            new Pack(3, "Phase Walls", phaseWallLarge, 6)
        );
    }

    public static int maxTier(int level){
        int tier = 0;
        for(int i = 0; i < tierLevels.length; i++){
            if(level >= tierLevels[i]) tier = i;
        }
        return tier;
    }

    public static Seq<Pack> roll(int level, Rand rand){
        return roll(level, rand, 3);
    }

    public static Seq<Pack> roll(int level, Rand rand, int count){
        int top = maxTier(level);
        Seq<Pack> pool = all().select(p -> p.tier <= top);
        Seq<Pack> topPool = pool.select(p -> p.tier == top);
        Seq<Pack> out = new Seq<>();

        out.add(topPool.get(rand.random(topPool.size - 1)));
        pool.remove(out.first());
        while(out.size < count && pool.any()){
            out.add(pool.remove(rand.random(pool.size - 1)));
        }
        return out.shuffle();
    }
}

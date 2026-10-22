package roguedustry;

import arc.struct.*;
import mindustry.type.*;

import static mindustry.content.UnitTypes.*;

/** Groups in a wave run in parallel: count units, one every spacing ticks, after delay ticks. 60 ticks = 1 second. */
public class Waves{

    public static class Group{
        public final UnitType type;
        public final int count;
        public final float spacing, delay;
        public float shields;
        public boolean boss;

        Group(UnitType type, int count, float spacing, float delay){
            this.type = type;
            this.count = count;
            this.spacing = spacing;
            this.delay = delay;
        }

        public Group shields(float amount){
            shields = amount;
            return this;
        }

        public Group boss(){
            boss = true;
            return this;
        }

        public float duration(){
            return delay + spacing * (count - 1);
        }
    }

    public static class Wave{
        public final Seq<Group> groups;

        Wave(Group... groups){
            this.groups = Seq.with(groups);
        }

        public int totalUnits(){
            return groups.sum(g -> g.count);
        }
    }

    static Group g(UnitType type, int count, float spacing){
        return new Group(type, count, spacing, 0f);
    }

    static Group g(UnitType type, int count, float spacing, float delay){
        return new Group(type, count, spacing, delay);
    }

    /** Built later because unit types load after the mod constructor. */
    public static Seq<Wave> schedule(){
        return Seq.with(
            /* 1 */ new Wave(g(dagger, 6, 60f)),
            /* 2 */ new Wave(g(dagger, 10, 45f), g(flare, 4, 60f, 180f)),
            /* 3 */ new Wave(g(crawler, 12, 20f), g(dagger, 8, 50f, 60f)),
            /* 4 */ new Wave(g(dagger, 12, 30f), g(nova, 4, 90f, 120f), g(risso, 3, 90f, 60f)),
            /* 5 */ new Wave(g(mace, 2, 180f), g(dagger, 10, 40f), g(flare, 8, 30f, 240f)),
            /* 6 */ new Wave(g(atrax, 4, 120f), g(crawler, 20, 15f, 60f)),
            /* 7 */ new Wave(g(horizon, 6, 60f), g(dagger, 16, 25f), g(risso, 6, 50f, 120f)),
            /* 8 */ new Wave(g(mace, 5, 100f), g(nova, 8, 60f), g(crawler, 15, 20f, 300f)),
            /* 9 */ new Wave(g(fortress, 1, 1f).boss(), g(dagger, 20, 20f, 60f), g(flare, 12, 20f, 120f)),
            /* 10 */ new Wave(g(pulsar, 6, 80f), g(atrax, 8, 80f), g(horizon, 8, 50f, 200f), g(minke, 3, 120f, 100f)),
            /* 11 */ new Wave(g(zenith, 3, 150f), g(mace, 8, 70f), g(crawler, 30, 10f, 120f)),
            /* 12 */ new Wave(g(fortress, 3, 200f), g(spiroct, 4, 150f, 100f), g(dagger, 25, 15f), g(risso, 10, 30f, 60f)),
            /* 13 */ new Wave(g(quasar, 3, 200f), g(zenith, 6, 100f), g(horizon, 12, 30f, 240f), g(minke, 4, 100f, 120f)),
            /* 14 */ new Wave(g(fortress, 5, 120f), g(mace, 10, 50f), g(crawler, 40, 8f, 200f), g(zenith, 4, 120f, 400f), g(bryde, 2, 200f, 100f)),
            /* 15 */ new Wave(g(scepter, 1, 1f).boss().shields(500f), g(fortress, 4, 180f, 120f), g(quasar, 4, 180f, 240f), g(zenith, 8, 80f, 300f), g(dagger, 30, 12f), g(bryde, 2, 240f, 60f), g(minke, 4, 120f, 200f))
        );
    }
}

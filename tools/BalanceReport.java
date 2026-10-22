package roguedustry;

import arc.struct.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.bullet.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.production.*;

public class BalanceReport{
    static final float travelSeconds = 20f;

    public static void main(String[] args){
        Vars.headless = true;
        Vars.content = new ContentLoader();
        Vars.content.createBaseContent();

        Seq<Waves.Wave> waves = Waves.schedule();
        Seq<Packs.Pack> packs = Packs.all();

        System.out.println("== Waves ==");
        System.out.println("(HP includes shields and the boss health multiplier. HP/s spreads it over the spawn window + " + (int)travelSeconds + "s of walking.)");
        System.out.printf("%-5s %6s %9s %8s %8s %7s %9s %6s %6s%n", "wave", "units", "HP", "spawn s", "HP/s", "XP", "total XP", "level", "picks");

        int level = 1, xp = 0, totalXp = 0;
        for(int w = 0; w < waves.size; w++){
            Waves.Wave wave = waves.get(w);
            float hp = 0f, spawnTicks = 0f;
            int waveXp = 0;

            for(Waves.Group g : wave.groups){
                float mult = g.boss ? StatusEffects.boss.healthMultiplier : 1f;
                hp += (g.type.health * mult + g.shields) * g.count;
                waveXp += Math.max(1, (int)(g.type.health / 10f)) * g.count;
                spawnTicks = Math.max(spawnTicks, g.duration());
            }

            xp += waveXp;
            totalXp += waveXp;
            while(xp >= RogueRun.xpToNext(level)){
                xp -= RogueRun.xpToNext(level);
                level++;
            }

            float window = spawnTicks / 60f + travelSeconds;
            System.out.printf("%-5d %6d %9.0f %8.1f %8.0f %7d %9d %6d %6d%n",
                w + 1, wave.totalUnits(), hp, spawnTicks / 60f, hp / window, waveXp, totalXp, level, level - 1);
        }

        System.out.println();
        System.out.println("== Tier unlocks ==");
        for(int t = 0; t < Packs.tierLevels.length; t++){
            System.out.printf("tier %d at level %d (needs %d total XP)%n", t + 1, Packs.tierLevels[t], xpForLevel(Packs.tierLevels[t]));
        }

        System.out.println();
        System.out.println("== Packs ==");
        System.out.println("(DPS = rough damage per second if every block in the pack is a turret firing nonstop.)");
        System.out.printf("%-4s %-18s %-36s %8s%n", "tier", "pack", "contents", "DPS");
        printPack("start", "Starting kit", Packs.startingKit());
        for(Packs.Pack pack : packs){
            printPack(String.valueOf(pack.tier + 1), pack.name, pack.entries);
        }

        System.out.println();
        System.out.println("== Turrets used in packs ==");
        System.out.printf("%-14s %8s %8s%n", "turret", "DPS", "range");
        ObjectSet<Block> seen = new ObjectSet<>();
        for(Packs.Pack pack : packs){
            for(Packs.Entry e : pack.entries){
                if(e.block instanceof BaseTurret t && seen.add(t)){
                    System.out.printf("%-14s %8.0f %8.0f%n", t.name, dps(t), t.range / 8f);
                }
            }
        }
        System.out.println("(range in tiles)");

        System.out.println();
        System.out.println("== Copper ==");
        System.out.println("(start " + Packs.startingCopper + ", reroll " + Packs.rerollPrice + ". Mining speed assumes every drill tile is on copper ore.)");
        for(int t = 0; t < Packs.tierPrices.length; t++){
            System.out.printf("tier %d pack: %d copper%n", t + 1, Packs.tierPrices[t]);
        }
        System.out.printf("%-16s %6s %9s %10s%n", "drill", "price", "copper/s", "pays back");
        for(var e : Packs.drills()){
            Drill d = (Drill)e.key;
            float perSecond = 60f / d.getDrillTime(Items.copper) * d.size * d.size * d.liquidBoostIntensity;
            System.out.printf("%-16s %6d %9.2f %9.0fs%n", d.name, e.value, perSecond, e.value / perSecond);
        }
        System.out.println("(drills get the water speed boost for free)");
    }

    static void printPack(String tier, String name, Seq<Packs.Entry> entries){
        StringBuilder contents = new StringBuilder();
        float total = 0f;
        for(Packs.Entry e : entries){
            if(contents.length() > 0) contents.append(", ");
            contents.append(e.amount).append("x ").append(e.block.name);
            if(e.block instanceof BaseTurret t) total += dps(t) * e.amount;
        }
        System.out.printf("%-4s %-18s %-36s %8s%n", tier, name, contents, total > 0 ? String.format("%.0f", total) : "-");
    }

    static float dps(BaseTurret block){
        if(block instanceof TractorBeamTurret tractor) return tractor.damage * 60f;
        if(!(block instanceof Turret t)) return 0f;

        BulletType bullet = null;
        if(t instanceof ItemTurret it && it.ammoTypes.size > 0) bullet = it.ammoTypes.get(it.ammoTypes.keys().next());
        else if(t instanceof LiquidTurret lt) bullet = lt.ammoTypes.get(Liquids.water);
        else if(t instanceof PowerTurret pt) bullet = pt.shootType;
        else if(t instanceof ContinuousTurret ct) bullet = ct.shootType;
        if(bullet == null) return 0f;

        if(bullet instanceof ContinuousBulletType c) return c.damage * 60f / c.damageInterval;
        return t.shoot.shots / t.reload * 60f * bullet.estimateDPS();
    }

    static int xpForLevel(int target){
        int sum = 0;
        for(int l = 1; l < target; l++) sum += RogueRun.xpToNext(l);
        return sum;
    }
}

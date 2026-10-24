package mindustrysurvivors;

import arc.struct.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.type.*;
import mindustry.world.*;

public class RunReport{

    public static void main(String[] args){
        Vars.headless = true;
        Vars.content = new ContentLoader();
        Vars.content.createBaseContent();
        Arena.load();
        Rewards.load();

        System.out.println("== Campaign sectors ==");
        System.out.println("(other sectors stay locked until " + SectorPresets.planetaryTerminal.localizedName + " is captured)");
        for(SectorPreset p : Vars.content.sectors()){
            if(p.planet != Planets.serpulo) continue;
            String goal = p.captureWave > 0 ? "survive " + p.captureWave + " waves" : "destroy the base";
            System.out.printf("%-26s threat %4.1f  %s%n", p.localizedName, p.difficulty, goal);
        }

        System.out.println();
        System.out.println("== Disabled blocks ==");
        for(Block block : Vars.content.blocks()){
            if(SurvivorsRun.disabled(block)) System.out.println(block.localizedName);
        }

        System.out.println();
        System.out.println("== Roguelike Campaign: blocks that can't be built ==");
        System.out.println(Vars.content.blocks().select(Wireless::banned).map(b -> b.localizedName).toString(", "));
        System.out.println();

        System.out.println("== Relics and modifiers (roguelike) ==");
        System.out.println("(per sector: a relic and curse pair on landing and at levels 10, 20, 30; a modifier pick per level; each modifier once, each card rerolled once)");
        for(Rewards.Reward r : Rewards.all){
            String icon = r.iconContent == null ? "" : "  [icon: " + r.iconContent.localizedName + "]";
            System.out.printf("%-9s %-18s %s%s%n", r.rarity.label, r.name, r.description, icon);
        }

        System.out.println();
        System.out.println("Modifiers all have the same chance. XP for each level: " + Seq.with(0, 1, 2, 4, 9, 19, 29).map(l -> l + 1 + "=" + (int)SurvivorsRun.xpFor(l)).toString(", "));
        System.out.println();
        Weapons.load();
        System.out.println("== Orbiting weapons (level 1 is the base ammo) ==");
        System.out.println(String.format("(global: damage x%.1f, fire rate x%.1f, range x%.1f on top of the base game; every level also adds +%.0f%% damage)", Weapons.globalDamage, Weapons.globalFireRate, Weapons.globalRange, Weapons.levelDamage * 100f));
        for(var e : Weapons.steps){
            mindustry.world.blocks.defense.turrets.BaseTurret t = (mindustry.world.blocks.defense.turrets.BaseTurret)e.key;
            mindustry.ctype.UnlockableContent ammo = Weapons.firstAmmo(t);
            System.out.println(t.localizedName + ": 1 " + (Weapons.support(t) ? "support" : ammo == null ? "power" : ammo.localizedName + " ammo") + String.format("  (range %.0f tiles, game damage per second %.0f, balance x%.2f, level 1 damage per second %.0f)", t.range * Weapons.globalRange / 8f, Weapons.gameDps(t), Weapons.balance(t), Weapons.levelOneDps(t)));
            for(int i = 0; i < e.value.size; i++) System.out.println("   " + (i + 2) + " " + e.value.get(i).text);
        }
        System.out.println();
        System.out.println("== Waves ==");
        float total = 0f;
        int units = 0;
        StringBuilder levels = new StringBuilder();
        int level = 0;
        float xp = 0f;
        for(int w = 0; w < Arena.waves.length; w++){
            float hp = 0f;
            for(int i = 0; i < 15; i++){
                if(Arena.waves[w][i] == 0) continue;
                int n = Math.max(1, Math.round(Arena.waves[w][i] * Arena.waveScale));
                float avg = 0f;
                for(Arena.Family f : Arena.families) avg += Math.max(1, Math.round(f.tiers[i % 5].health / 130f)) / (float)Arena.families.size;
                hp += avg * n;
                units += n;
            }
            total += hp;
            xp += hp * Survivor.xpRate;
            while(xp >= SurvivorsRun.xpFor(level)){
                xp -= SurvivorsRun.xpFor(level);
                level++;
            }
            if((w + 1) % 5 == 0 || w < 3) levels.append("wave ").append(w + 1).append(": level ").append(level).append(", ");
        }
        System.out.println(units + " enemies, " + (int)total + " XP in gems. Levels if every gem is picked up: " + levels);
        System.out.println();
        System.out.println("== Curses ==");
        System.out.println("(rolled at the start of an Eradication +1 to +4 run: budgets 4, 6, 8, 11 points, at least 2, 2, 3, 3 curses, at most 4)");
        for(Curses.Curse m : Curses.all){
            System.out.printf("%-20s %d  %s%n", m.name, m.cost, m.description);
        }
        System.out.println();
        System.out.println("== Sample curse rolls ==");
        for(int heat = 1; heat <= SurvivorsRun.maxHeat; heat++){
            for(int sample = 0; sample < 3; sample++){
                Seq<Curses.Curse> rolled = Curses.roll(SurvivorsRun.heatBudget(heat), SurvivorsRun.heatMinCurses(heat), new arc.math.Rand(heat * 1000L + sample));
                System.out.println("Eradication +" + heat + ": " + rolled.map(c -> c.name + " (" + c.cost + ")").toString(", ") + "  = " + rolled.sum(c -> c.cost) + " of " + SurvivorsRun.heatBudget(heat));
            }
        }
    }
}

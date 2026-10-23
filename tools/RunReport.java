package roguedustry;

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
            if(RogueRun.disabled(block)) System.out.println(block.localizedName);
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
        System.out.println("Modifier odds: " + Rewards.Rarity.common.weight + "% common, " + Rewards.Rarity.uncommon.weight + "% uncommon, " + Rewards.Rarity.rare.weight + "% rare");
        System.out.println();
        System.out.println("== Curses ==");
        System.out.println("(each relic brings one at random; they last until the sector is captured)");
        for(Curses.Curse m : Curses.all){
            System.out.printf("%-28s %s%n", m.name, m.description);
        }
    }
}

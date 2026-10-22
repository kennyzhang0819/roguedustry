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
        System.out.println("== Relics and modifiers ==");
        System.out.println("(a relic pick on each new sector, a modifier pick every " + Rewards.wavesPerModifier + " waves cleared on a sector)");
        for(Rewards.Reward r : Rewards.all){
            System.out.printf("%-9s %-18s max %d  %s%n", r.rarity.label, r.name, r.max, r.description);
        }

        System.out.println();
        System.out.println("== Curses ==");
        System.out.println("(a pick of 3 on every second new sector, kept for the whole run)");
        for(Mutators.Mutator m : Mutators.all){
            System.out.printf("%-28s %s%n", m.name, m.description);
        }
    }
}

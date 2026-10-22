package roguedustry;

import arc.struct.*;
import mindustry.*;
import mindustry.content.TechTree.*;
import mindustry.core.*;
import mindustry.type.*;

public class RunReport{

    public static void main(String[] args){
        Vars.headless = true;
        Vars.content = new ContentLoader();
        Vars.content.createBaseContent();

        RogueRun run = new RogueRun();
        run.unlocked.addAll(Tech.starting());
        Tech.autoUnlock(run);

        System.out.println("== Campaign maps ==");
        System.out.println("(what each map needs before it can be played)");
        for(TechNode node : Tech.nodes()){
            if(node.content instanceof SectorPreset p){
                String wave = p.captureWave > 0 ? "survive " + p.captureWave : "destroy base";
                System.out.printf("%-22s threat %4.1f  %-13s needs: %s%n", p.name, p.difficulty, wave, node.objectives.map(Tech::describe).toString(", "));
            }
        }

        System.out.println();
        System.out.println("== Research ==");
        ItemSeq total = new ItemSeq();
        int count = 0;
        for(TechNode node : Tech.nodes()){
            if(node.content instanceof SectorPreset) continue;
            count++;
            for(ItemStack stack : node.requirements) total.add(stack.item, stack.amount);
        }
        System.out.println(count + " things to research. Unlocked at the start: " + run.unlocked.toSeq().map(c -> c.name));
        System.out.println("Total cost of the whole tree:");
        total.each((item, amount) -> System.out.printf("  %-14s %9d%n", item.name, amount));

        System.out.println();
        System.out.println("== Relics and modifiers ==");
        System.out.println("(a relic pick at the start of each map, " + Rewards.modifiersPerMap + " modifier picks spread over each map)");
        for(Rewards.Reward r : Rewards.all){
            System.out.printf("%-9s %-18s max %d  %s%n", r.rarity.label, r.name, r.max, r.description);
        }

        System.out.println();
        System.out.println("== Enemy mutators ==");
        for(Mutators.Mutator m : Mutators.all){
            System.out.printf("%-14s %d points  %s%n", m.name, m.points, m.description);
        }
        System.out.println();
        System.out.println("Terror level = maps cleared. Mutator points per map = terror x 2.");
        for(int terror = 0; terror <= 6; terror++){
            System.out.printf("terror %d: %2d points, example roll: %s%n", terror, Mutators.budget(terror), Mutators.roll(terror, terror * 97L).map(m -> m.name));
        }
    }
}

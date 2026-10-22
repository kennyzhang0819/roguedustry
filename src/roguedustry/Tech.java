package roguedustry;

import arc.math.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.ctype.*;
import mindustry.game.Objectives.*;
import mindustry.type.*;

public class Tech{

    public static Seq<TechNode> nodes(){
        Seq<TechNode> out = new Seq<>();
        Planets.serpulo.techTree.each(out::add);
        return out;
    }

    public static boolean done(RogueRun run, Objective o){
        if(o instanceof Research r) return run.unlocked.contains(r.content);
        if(o instanceof Produce p) return run.unlocked.contains(p.content);
        if(o instanceof SectorComplete s) return run.cleared.contains(s.preset);
        if(o instanceof OnSector s) return run.cleared.contains(s.preset) || run.missionPreset() == s.preset;
        return true;
    }

    public static boolean open(RogueRun run, TechNode node){
        return !run.unlocked.contains(node.content)
            && (node.parent == null || run.unlocked.contains(node.parent.content))
            && !node.objectives.contains(o -> !done(run, o));
    }

    public static Seq<Objective> missing(RogueRun run, TechNode node){
        return node.objectives.select(o -> !done(run, o));
    }

    public static String describe(Objective o){
        if(o instanceof Research r) return "research " + r.content.localizedName;
        if(o instanceof Produce p) return "get " + p.content.localizedName;
        if(o instanceof SectorComplete s) return "clear " + s.preset.localizedName;
        if(o instanceof OnSector s) return "play " + s.preset.localizedName;
        return o.toString();
    }

    public static String missingText(RogueRun run, TechNode node){
        return missing(run, node).map(Tech::describe).toString(", ");
    }

    public static boolean waitingOnParent(RogueRun run, TechNode node){
        return !run.unlocked.contains(node.content) && node.parent != null && run.unlocked.contains(node.parent.content);
    }

    public static boolean autoUnlock(RogueRun run){
        boolean any = false, changed = true;
        Seq<TechNode> all = nodes();
        while(changed){
            changed = false;
            for(TechNode node : all){
                if(open(run, node) && cost(run, node).length == 0){
                    run.unlocked.add(node.content);
                    changed = any = true;
                }
            }
        }
        return any;
    }

    public static ItemStack[] cost(RogueRun run, TechNode node){
        float mult = Mathf.pow(0.75f, run.stacks(Modifiers.scholar));
        Seq<ItemStack> out = new Seq<>();
        for(ItemStack stack : node.requirements){
            int amount = Math.max(1, Mathf.round(stack.amount * mult));
            if(stack.amount > 0) out.add(new ItemStack(stack.item, amount));
        }
        return out.toArray(ItemStack.class);
    }

    public static int paid(RogueRun run, TechNode node, Item item){
        ItemSeq seq = run.progress.get(node.content);
        return seq == null ? 0 : seq.get(item);
    }

    public static boolean canAfford(RogueRun run, TechNode node){
        for(ItemStack stack : cost(run, node)){
            if(paid(run, node, stack.item) + run.available(stack.item) < stack.amount) return false;
        }
        return true;
    }

    public static boolean canContribute(RogueRun run, TechNode node){
        for(ItemStack stack : cost(run, node)){
            if(paid(run, node, stack.item) < stack.amount && run.available(stack.item) > 0) return true;
        }
        return false;
    }

    public static boolean contribute(RogueRun run, TechNode node){
        if(!open(run, node)) return false;
        ItemSeq seq = run.progress.get(node.content, ItemSeq::new);
        boolean finished = true;
        for(ItemStack stack : cost(run, node)){
            int need = stack.amount - seq.get(stack.item);
            int take = Math.min(need, run.available(stack.item));
            if(take > 0){
                run.spend(stack.item, take);
                seq.add(stack.item, take);
            }
            if(seq.get(stack.item) < stack.amount) finished = false;
        }
        if(finished){
            run.progress.remove(node.content);
            run.unlocked.add(node.content);
            autoUnlock(run);
        }
        return finished;
    }

    public static Seq<TechNode> researchable(RogueRun run){
        return nodes().select(n -> !(n.content instanceof SectorPreset) && open(run, n) && cost(run, n).length > 0);
    }

    public static Seq<TechNode> lockedNext(RogueRun run){
        return nodes().select(n -> !(n.content instanceof SectorPreset) && waitingOnParent(run, n) && !open(run, n));
    }

    public static Seq<SectorPreset> playableSectors(RogueRun run){
        Seq<SectorPreset> out = new Seq<>();
        for(TechNode node : nodes()){
            if(node.content instanceof SectorPreset p && run.unlocked.contains(p) && !run.cleared.contains(p) && p.generator != null && p.generator.map != null){
                out.add(p);
            }
        }
        return out;
    }

    public static Seq<TechNode> lockedSectors(RogueRun run){
        return nodes().select(n -> n.content instanceof SectorPreset && waitingOnParent(run, n) && !open(run, n));
    }

    public static boolean campaignBeaten(RogueRun run){
        return run.cleared.contains(SectorPresets.planetaryTerminal);
    }

    public static boolean usable(RogueRun run, UnlockableContent content){
        return run.unlocked.contains(content) || content.techNode == null;
    }

    public static ObjectSet<UnlockableContent> starting(){
        ObjectSet<UnlockableContent> out = new ObjectSet<>();
        for(TechNode node : nodes()){
            if(node.content.alwaysUnlocked || node.parent == null) out.add(node.content);
        }
        return out;
    }
}

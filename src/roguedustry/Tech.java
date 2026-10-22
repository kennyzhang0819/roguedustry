package roguedustry;

import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.ctype.*;
import mindustry.game.Objectives.*;
import mindustry.type.*;
import mindustry.world.*;

public class Tech{

    public static Seq<TechNode> nodes(){
        Seq<TechNode> out = new Seq<>();
        Planets.serpulo.techTree.each(n -> {
            if(!removed(n)) out.add(n);
        });
        return out;
    }

    public static boolean removed(TechNode node){
        for(TechNode n = node; n != null; n = n.parent){
            if(n.content instanceof Block b && b.category == Category.logic) return true;
        }
        return false;
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
        return describe(null, o);
    }

    public static String describe(@Nullable RogueRun hideLocked, Objective o){
        if(o instanceof Research r) return (r.content instanceof Item || r.content instanceof Liquid ? "get " : "unlock ") + name(hideLocked, r.content);
        if(o instanceof Produce p) return "get " + name(hideLocked, p.content);
        if(o instanceof SectorComplete s) return "clear " + s.preset.localizedName;
        if(o instanceof OnSector s) return "play " + s.preset.localizedName;
        return o.toString();
    }

    static String name(@Nullable RogueRun hideLocked, UnlockableContent content){
        return hideLocked != null && !hideLocked.unlocked.contains(content) ? "???" : content.localizedName;
    }

    public static String missingText(RogueRun run, TechNode node){
        return missing(run, node).map(Tech::describe).toString(", ");
    }

    public static boolean waitingOnParent(RogueRun run, TechNode node){
        return !run.unlocked.contains(node.content) && node.parent != null && run.unlocked.contains(node.parent.content);
    }

    public static int autoUnlock(RogueRun run){
        int count = 0;
        boolean changed = true;
        Seq<TechNode> all = nodes();
        while(changed){
            changed = false;
            for(TechNode node : all){
                if(open(run, node)){
                    run.unlocked.add(node.content);
                    changed = true;
                    count++;
                }
            }
        }
        return count;
    }

    public static Seq<TechNode> unlockedNodes(RogueRun run){
        return nodes().select(n -> !(n.content instanceof SectorPreset) && n.parent != null && run.unlocked.contains(n.content));
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

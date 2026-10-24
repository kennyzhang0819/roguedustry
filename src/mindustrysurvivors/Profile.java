package mindustrysurvivors;

import arc.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.TechTree.*;
import mindustry.ctype.*;
import mindustry.type.*;

import java.lang.reflect.*;

import static mindustry.Vars.*;

public class Profile{
    public static final String player = "player", run = "run";
    static final String activeKey = "mindustry-survivors-profile", stashPrefix = "survivors.", backupKey = "mindustry-survivors-backup-made";
    static final ObjectSet<String> singleKeys = ObjectSet.with("unlocks", "launch-resources-seq", "utimei", "turn", "last-sector-save");

    public static String live(){
        return Core.settings.getString(activeKey, player);
    }

    public static boolean inRun(){
        return run.equals(live());
    }

    static boolean campaignKey(String key){
        if(key.startsWith(stashPrefix) || key.startsWith("mindustry-survivors")) return false;
        if(key.endsWith("-unlocked") || key.startsWith("req-") || key.startsWith("lastloadout-") || singleKeys.contains(key)) return true;
        for(Planet planet : content.planets()){
            if(key.startsWith(planet.name + "-")) return true;
        }
        return false;
    }

    static Fi folder(String profile){
        return dataDirectory.child("mindustry-survivors").child(profile);
    }

    static Seq<Fi> sectorFiles(Fi dir){
        return Seq.with(dir.list()).select(f -> f.name().startsWith("sector-"));
    }

    static void stash(String profile){
        String prefix = stashPrefix + profile + ".";
        for(String key : Seq.with(Core.settings.keys()).select(Profile::campaignKey)){
            Core.settings.put(prefix + key, Core.settings.get(key, null));
            Core.settings.remove(key);
        }
        Fi dir = folder(profile);
        dir.mkdirs();
        for(Fi file : sectorFiles(dir)) file.delete();
        for(Fi file : sectorFiles(saveDirectory)) file.moveTo(dir.child(file.name()));
    }

    static void unstash(String profile){
        String prefix = stashPrefix + profile + ".";
        for(String key : Seq.with(Core.settings.keys()).select(k -> k.startsWith(prefix))){
            Core.settings.put(key.substring(prefix.length()), Core.settings.get(key, null));
            Core.settings.remove(key);
        }
        Fi dir = folder(profile);
        if(dir.exists()){
            for(Fi file : sectorFiles(dir)) file.moveTo(saveDirectory.child(file.name()));
        }
    }

    static void backupOnce(){
        if(Core.settings.getBool(backupKey, false)) return;
        Fi dir = dataDirectory.child("mindustry-survivors").child("campaign-backup");
        dir.mkdirs();
        StringBuilder keys = new StringBuilder();
        for(String key : Core.settings.keys()){
            if(campaignKey(key)) keys.append(key).append('=').append(Core.settings.get(key, null)).append('\n');
        }
        dir.child("settings.txt").writeString(keys.toString());
        for(Fi file : sectorFiles(saveDirectory)) file.copyTo(dir.child(file.name()));
        Core.settings.put(backupKey, true);
        Log.info("[Survivors] Backed up your campaign to @", dir.absolutePath());
    }

    public static void switchTo(String profile){
        if(profile.equals(live())) return;
        if(player.equals(live())) backupOnce();
        stash(live());
        unstash(profile);
        Core.settings.put(activeKey, profile);
        Core.settings.forceSave();
        reload();
        Log.info("[Survivors] Switched to the @ campaign", profile);
    }

    public static void deleteRun(){
        String prefix = stashPrefix + run + ".";
        for(String key : Seq.with(Core.settings.keys()).select(k -> k.startsWith(prefix))) Core.settings.remove(key);
        folder(run).deleteDirectory();
        Core.settings.forceSave();
    }

    //the game reads unlocks, research and sector data once at startup, so everything is reloaded from the swapped settings
    static void reload(){
        try{
            Field unlocked = UnlockableContent.class.getDeclaredField("unlocked");
            unlocked.setAccessible(true);
            for(var c : content.getContentMap()){
                for(var item : c){
                    if(item instanceof UnlockableContent u) unlocked.setBoolean(u, Core.settings.getBool(u.name + "-unlocked", false));
                }
            }
            Method universeLoad = universe.getClass().getDeclaredMethod("load");
            universeLoad.setAccessible(true);
            universeLoad.invoke(universe);
        }catch(Throwable e){
            Log.err("[Survivors] Could not reload campaign data", e);
        }

        for(TechNode node : mindustry.content.TechTree.all){
            node.setupRequirements(node.requirements);
        }
        for(Planet planet : content.planets()){
            planet.loadRules();
            planet.loadStats();
            for(Sector sector : planet.sectors){
                sector.save = null;
                sector.loadInfo();
            }
        }
        control.saves.load();
        for(Planet planet : content.planets()){
            if(planet.sectors.any()) planet.reloadMeshAsync();
        }
    }
}

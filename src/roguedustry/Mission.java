package roguedustry;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.maps.*;
import mindustry.type.*;

import static mindustry.Vars.*;

public class Mission{
    public final @Nullable SectorPreset preset;
    public final int seed, level;

    public Mission(SectorPreset preset){
        this.preset = preset;
        seed = level = 0;
    }

    public Mission(int seed, int level){
        this.preset = null;
        this.seed = seed;
        this.level = level;
    }

    public static Mission of(String key){
        if(key.startsWith("gen:")){
            String[] parts = key.split(":");
            return new Mission(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }
        return new Mission(content.sector(key));
    }

    public String key(){
        return preset != null ? preset.name : "gen:" + seed + ":" + level;
    }

    public String name(){
        return preset != null ? preset.localizedName : "Uncharted sector " + (seed % 1000);
    }

    public boolean attack(){
        return preset != null && preset.generator.map.rules().attackMode;
    }

    public int winWave(){
        return preset != null ? preset.captureWave : 20 + level * 3;
    }

    public float threat(){
        return preset != null ? preset.difficulty : 10f + level;
    }

    public String goal(){
        return attack() ? "Destroy the enemy base" : "Survive " + winWave() + " waves";
    }

    public Map map(){
        return preset != null ? preset.generator.map : MapGen.create(seed, name());
    }

    public Rules rules(){
        if(preset != null){
            Map map = preset.generator.map;
            Rules rules = map.applyRules(attack() ? Gamemode.attack : Gamemode.survival);
            Planets.serpulo.applyRules(rules, true);
            preset.rules.get(rules);
            return rules;
        }

        Rules rules = new Rules();
        Gamemode.survival.apply(rules);
        Planets.serpulo.applyRules(rules, true);
        rules.winWave = winWave();
        rules.waveTimer = true;
        rules.waveSpacing = 60f * Math.max(60f, 110f - level * 5f);
        rules.initialWaveSpacing = 60f * 150f;
        rules.loadout = ItemStack.list(Items.copper, 1500 + 500 * level, Items.lead, 1500 + 500 * level, Items.graphite, 500, Items.silicon, 500);

        MapGen layout = MapGen.layoutOf(seed);
        int groundSpawn = Point2.pack(layout.groundX, layout.groundY), navalSpawn = Point2.pack(layout.navalX, layout.navalY);
        float difficulty = Math.min(0.5f + level * 0.08f, 1.5f);
        Seq<SpawnGroup> spawns = new Seq<>();
        for(SpawnGroup group : Waves.generate(difficulty, new Rand(seed), false, false, false)){
            if(!group.type.flying) group.spawn = groundSpawn;
            spawns.add(group);
        }
        for(SpawnGroup group : Waves.generate(difficulty * 0.7f, new Rand(seed + 1), false, false, true)){
            if(group.type.naval){
                group.spawn = navalSpawn;
                spawns.add(group);
            }
        }
        rules.spawns = spawns;
        return rules;
    }
}

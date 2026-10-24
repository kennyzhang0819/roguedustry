package mindustrysurvivors;

import arc.*;
import arc.files.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class Arena{
    public static final String tag = "mindustry-survivors-run";
    public static final int lastWave = 50;
    static final float waveGap = 18.5f, firstWave = 3f, releaseTime = 3.5f;
    public static final float waveScale = 0.2f, enemyDamagePerWave = 0.04f;
    static final String script = "0.1:40;0.1:87;0.1:73,0.2:7,1.1:14;1.1:17,0.1:133,0.2:24;0.1:169,1.1:19;0.1:105,0.2:51,1.2:20;0.1:232,0.3:18;0.1:102,0.2:49,2.1:50;1.1:82,1.2:26,0.1:497,0.2:27,0.3:20;0.1:596,0.2:74,0.3:22,1.1:89,1.2:40;2.1:159,1.1:63,0.1:640;2.1:170,2.2:65;1.1:72,1.2:17,0.1:545,0.2:104,0.3:38;0.1:771,0.2:184,0.3:27,1.1:76,1.2:18,1.3:3;0.1:2038;1.1:937,1.2:245,1.3:15;2.1:448,2.2:129,2.3:31;0.1:567,0.2:225,1.1:235,1.2:90;0.1:1485,0.2:235,0.3:67;0.1:2068,0.3:104,1.3:35,2.2:245,2.3:70;0.1:991,0.2:177,0.3:46,0.4:1,2.1:227,1.1:303,1.2:55,1.3:14,2.2:41,2.3:11;2.1:207,1.1:276,1.2:55,1.3:16,1.4:1,2.2:42,2.3:12,0.1:905,0.2:180,0.3:53,0.4:1;2.1:687,2.2:154,2.3:50,2.4:1;0.1:713,0.2:176,0.3:65,0.4:2,2.1:236,1.1:236,1.2:59,1.3:22,1.4:1,2.2:58,2.3:22;0.1:658,0.2:181,0.3:76,0.4:2,2.1:150,1.1:201,1.2:55,1.3:23,1.4:1,2.2:42,2.3:17;2.1:134,1.1:178,1.2:55,1.3:26,1.4:1,2.2:41,2.3:19,0.1:585,0.2:179,0.3:84,0.4:3;2.1:434,2.2:149,2.3:79,2.4:3;0.1:442,0.2:166,0.3:100,0.4:4,2.1:146,1.1:146,1.2:56,1.3:34,1.4:2,2.2:56,2.3:33,2.4:1;1.1:428,1.2:181,1.3:123,1.4:6;0.1:352,0.2:164,0.3:125,0.4:7,2.1:80,1.1:107,1.2:51,1.3:38,1.4:2,2.2:38,2.3:29,2.4:2;2.1:70,1.1:94,1.2:49,1.3:42,1.4:3,2.2:37,2.3:32,2.4:2,0.1:306,0.2:159,0.3:137,0.4:9;2.1:224,2.2:131,2.3:127,2.4:10;0.1:226,0.2:145,0.3:158,0.4:14,2.1:74,1.1:74,1.2:48,1.3:53,1.4:4,2.2:48,2.3:53,2.4:4;0.1:302,0.2:100,0.3:50,0.4:4,2.1:75,1.1:75,1.2:25,1.3:10,2.2:25,2.3:10;0.1:1109,0.2:600,0.3:480,0.4:70,2.1:300,1.1:300,1.2:175,1.3:135,1.4:18,2.2:175,2.3:135,2.4:17;0.1:679,0.2:173,0.3:102,0.4:7,0.5:1,2.1:156,1.1:207,1.2:53,1.3:31,1.4:2,2.2:40,2.3:23,2.4:2;2.1:142,1.1:189,1.2:54,1.3:35,1.4:3,2.2:40,2.3:27,2.4:2,0.1:620,0.2:176,0.3:116,0.4:9,0.5:1;2.1:471,2.2:149,2.3:111,2.4:10,2.5:1;0.1:488,0.2:171,0.3:143,0.4:15,0.5:3,2.1:162,1.1:162,1.2:57,1.3:48,1.4:5,1.5:1,2.2:57,2.3:48,2.4:5;0.1:450,0.2:176,0.3:166,0.4:20,0.5:4,2.1:103,1.1:138,1.2:54,1.3:51,1.4:6,1.5:1,2.2:40,2.3:38,2.4:4;2.1:92,1.1:122,1.2:53,1.3:57,1.4:8,1.5:1,2.2:40,2.3:43,2.4:6,2.5:1,0.1:400,0.2:174,0.3:185,0.4:25,0.5:4;2.1:298,2.2:145,2.3:174,2.4:27,2.5:5;0.1:303,0.2:163,0.3:220,0.4:39,0.5:10,2.1:100,1.1:100,1.2:54,1.3:74,1.4:13,1.5:2,2.2:54,2.3:73,2.4:13,2.5:3;1.1:293,1.2:177,1.3:270,1.4:55,1.5:11;0.1:241,0.2:160,0.3:275,0.4:64,0.5:14,2.1:55,1.1:73,1.2:49,1.3:85,1.4:19,1.5:5,2.2:37,2.3:63,2.4:15,2.5:4;2.1:38,1.1:51,1.2:38,1.3:74,1.4:20,1.5:3,2.2:29,2.3:55,2.4:14,2.5:3,0.1:168,0.2:124,0.3:242,0.4:63,0.5:13;2.1:123,2.2:102,2.3:223,2.4:67,2.5:15;0.1:93,0.2:84,0.3:210,0.4:72,0.5:18,2.1:30,1.1:30,1.2:28,1.3:70,1.4:24,1.5:5,2.2:28,2.3:70,2.4:24,2.5:6;0.1:399,0.2:198,0.3:240,0.4:90,0.5:20,2.1:102,1.1:102,1.2:57,1.3:66,1.4:23,1.5:6,2.2:57,2.3:66,2.4:23,2.5:5;0.1:291,0.2:162,0.3:204,0.4:74,0.5:24,2.1:81,1.1:81,1.2:48,1.3:57,1.4:19,1.5:6,2.2:48,2.3:57,2.4:19,2.5:6";
    public static int[][] waves;
    public static final Seq<Family> families = new Seq<>();
    public static final ObjectIntMap<Block> tiers = new ObjectIntMap<>();
    public static final Seq<Block> buildable = new Seq<>();
    static final int[] tierWave = {0, 1, 10, 22, 34};

    static final IntSeq groundSpawns = new IntSeq(), waterSpawns = new IntSeq();
    static final Seq<Release> releasing = new Seq<>();
    public static final ObjectIntMap<Block> placed = new ObjectIntMap<>();
    static float recountTimer;
    public static final String difficultyKey = "mindustry-survivors-difficulty";
    public static StatusEffect coreBoost;
    static boolean won;

    public static class Family{
        public final String name;
        public final UnitType[] tiers;

        Family(String name, UnitType... tiers){
            this.name = name;
            this.tiers = tiers;
        }

        public boolean naval(){
            return tiers[0].naval;
        }

        public boolean air(){
            return tiers[0].flying;
        }
    }

    static class Release{
        final UnitType[] types;
        final boolean[] naval;
        final int[] total, left;
        final float rate;
        float credit;

        Release(UnitType[] types, boolean[] naval, int[] total){
            this.types = types;
            this.naval = naval;
            this.total = total;
            left = total.clone();
            int sum = 0;
            for(int n : total) sum += n;
            rate = sum / (releaseTime * 60f);
        }

        boolean done(){
            for(int n : left) if(n > 0) return false;
            return true;
        }
    }

    public static void load(){
        coreBoost = new StatusEffect("core-boost"){{
            permanent = true;
            show = false;
            healthMultiplier = 100f;
            damageMultiplier = 10f;
        }};

        families.addAll(
            new Family("Dagger", UnitTypes.dagger, UnitTypes.mace, UnitTypes.fortress, UnitTypes.scepter, UnitTypes.reign),
            new Family("Nova", UnitTypes.nova, UnitTypes.pulsar, UnitTypes.quasar, UnitTypes.vela, UnitTypes.corvus),
            new Family("Crawler", UnitTypes.crawler, UnitTypes.atrax, UnitTypes.spiroct, UnitTypes.arkyid, UnitTypes.toxopid),
            new Family("Flare", UnitTypes.flare, UnitTypes.horizon, UnitTypes.zenith, UnitTypes.antumbra, UnitTypes.eclipse),
            new Family("Risso", UnitTypes.risso, UnitTypes.minke, UnitTypes.bryde, UnitTypes.sei, UnitTypes.omura),
            new Family("Retusa", UnitTypes.retusa, UnitTypes.oxynoe, UnitTypes.cyerce, UnitTypes.aegires, UnitTypes.navanax)
        );

        String[] list = script.split(";");
        waves = new int[list.length][15];
        for(int w = 0; w < list.length; w++){
            for(String part : list[w].split(",")){
                int dot = part.indexOf('.'), colon = part.indexOf(':');
                int slot = Integer.parseInt(part.substring(0, dot)), tier = Integer.parseInt(part.substring(dot + 1, colon));
                waves[w][slot * 5 + tier - 1] += Integer.parseInt(part.substring(colon + 1));
            }
        }

        tier(1, Blocks.duo, Blocks.scatter, Blocks.scorch, Blocks.hail, Blocks.arc, Blocks.wave, Blocks.lancer);
        tier(2, Blocks.parallax, Blocks.swarmer, Blocks.salvo, Blocks.segment, Blocks.fuse);
        tier(3, Blocks.ripple, Blocks.cyclone, Blocks.tsunami);
        tier(4, Blocks.foreshadow, Blocks.spectre, Blocks.meltdown);
        tier(1, Blocks.copperWall, Blocks.copperWallLarge, Blocks.titaniumWall, Blocks.titaniumWallLarge, Blocks.mender);
        tier(2, Blocks.plastaniumWall, Blocks.plastaniumWallLarge, Blocks.thoriumWall, Blocks.thoriumWallLarge, Blocks.mendProjector, Blocks.forceProjector);
        tier(3, Blocks.phaseWall, Blocks.phaseWallLarge, Blocks.surgeWall, Blocks.surgeWallLarge);
    }

    static void tier(int tier, Block... blocks){
        for(Block block : blocks){
            tiers.put(block, tier);
            buildable.add(block);
        }
    }

    static final Seq<Runnable> shakeRestore = new Seq<>();
    static final ObjectSet<Object> shakeCut = new ObjectSet<>();
    static final float bigShake = 0.5f;

    //enemy unit types are shared with the normal game, so their shake is only cut while a run is on and put back after
    static void calmShake(boolean on){
        if(on == shakeRestore.any()) return;
        if(!on){
            shakeRestore.each(Runnable::run);
            shakeRestore.clear();
            shakeCut.clear();
            return;
        }
        for(Family family : families){
            for(int i = 0; i < family.tiers.length; i++){
                UnitType type = family.tiers[i];
                float scale = i < 3 ? 0f : bigShake;
                if(!shakeCut.add(type)) continue;
                float death = type.deathShake, land = type.mechLandShake, step = type.stepShake;
                type.deathShake = (death < 0f ? 3f + type.hitSize / 3f : death) * scale;
                type.mechLandShake = land * scale;
                type.stepShake = step * scale;
                shakeRestore.add(() -> {
                    type.deathShake = death;
                    type.mechLandShake = land;
                    type.stepShake = step;
                });
                for(Weapon weapon : type.weapons){
                    if(shakeCut.add(weapon)){
                        float shake = weapon.shake;
                        weapon.shake = shake * scale;
                        shakeRestore.add(() -> weapon.shake = shake);
                    }
                    calmBullet(weapon.bullet, scale);
                }
            }
        }
    }

    static void calmBullet(@Nullable BulletType b, float scale){
        if(b == null || !shakeCut.add(b)) return;
        float hit = b.hitShake, despawn = b.despawnShake;
        b.hitShake = hit * scale;
        b.despawnShake = despawn * scale;
        shakeRestore.add(() -> {
            b.hitShake = hit;
            b.despawnShake = despawn;
        });
        calmBullet(b.fragBullet, scale);
        if(b.intervalBullet != null) calmBullet(b.intervalBullet, scale);
        for(BulletType spawned : b.spawnBullets) calmBullet(spawned, scale);
    }

    public static boolean active(){
        return state.isGame() && state.rules.tags.getBool(tag);
    }

    public static boolean offered(Block block){
        return state.wave >= tierWave[tiers.get(block, 4)];
    }

    public static int cardSize(Block block){
        int area = block.size * block.size;
        if(block instanceof Wall) return Math.max(1, Math.round(128f / area));
        if(block instanceof Turret) return Math.max(1, Math.round(72f / area));
        return Math.max(1, Math.round(12f / area));
    }

    public static ObjectIntMap<Block> startingKit(){
        return new ObjectIntMap<>();
    }

    public static Fi saveFile(){
        return dataDirectory.child("mindustry-survivors").child("run." + saveExtension);
    }

    public static boolean saved(){
        return saveFile().exists();
    }

    static Rules rules(){
        Rules rules = new Rules();
        rules.tags.put(tag, "true");
        rules.waves = true;
        rules.waveTimer = true;
        rules.waveSpacing = waveGap * 60f;
        rules.initialWaveSpacing = firstWave * 60f;
        rules.spawns = new Seq<>();
        rules.infiniteResources = true;
        rules.instantBuild = true;
        rules.teams.get(rules.defaultTeam).cheat = true;
        rules.disableUnitCap = true;
        rules.blockWhitelist = true;
        rules.hideBannedBlocks = true;
        rules.fog = false;
        rules.lighting = false;
        rules.coreCapture = false;
        rules.canGameOver = false;
        rules.modeName = "Mindustry Survivors";
        return rules;
    }

    public static void start(long seed){
        MapGen.Result map = MapGen.generate((int)seed);
        Log.info("[Survivors] Map @ (seed @, @ tries), biomes @, @ ground and @ water spawn tiles", map.name, map.seed, map.tries, map.biomes, map.ground.size, map.water.size);
        ui.loadAnd(() -> {
            logic.reset();
            state.rules = rules();
            world.loadGenerator(MapGen.size, MapGen.size, tiles -> MapGen.write(tiles, map));
            Survivor.begin(map.coreX, map.coreY);
            state.rules.sector = null;
            Events.fire(new RulesLoadEvent(state.rules));
            logic.play();
            save();
            Events.fire(Trigger.newGame);
        });
    }

    public static void resume(){
        ui.loadAnd(() -> {
            try{
                net.reset();
                slot().load();
                state.rules.sector = null;
                state.set(State.playing);
            }catch(Throwable e){
                Log.err("[Survivors] Could not load the run", e);
                logic.reset();
                ui.showErrorMessage("The saved run could not be loaded.");
            }
        });
    }

    static mindustry.game.Saves.SaveSlot slot(){
        saveFile().parent().mkdirs();
        return control.saves.new SaveSlot(saveFile());
    }

    public static void save(){
        if(active() && !state.gameOver) slot().save();
    }

    public static void deleteSave(){
        saveFile().delete();
    }

    static void onLoaded(){
        groundSpawns.clear();
        waterSpawns.clear();
        releasing.clear();
        won = false;
        boolean run = state.rules.tags.getBool(tag);
        Chase.setup(run);
        if(!run) return;

        float sx = Survivor.startX(), sy = Survivor.startY();
        for(Tile tile : world.tiles){
            if(!MapGen.spawnBand(tile.x, tile.y, world.width(), world.height()) || tile.block() instanceof StaticWall) continue;
            if(tile.within(sx, sy, MapGen.spawnFromCore * tilesize)) continue;
            if(tile.floor().isLiquid) waterSpawns.add(tile.pos());
            else groundSpawns.add(tile.pos());
        }
        recount();
    }

    //enemies hit harder every wave, so a strong build still has to keep up late in the run
    static void rampDamage(){
        state.rules.teams.get(state.rules.waveTeam).unitDamageMultiplier = 1f + enemyDamagePerWave * Math.max(state.wave - 1, 0);
    }

    public static float enemyScale(Difficulty difficulty){
        return difficulty.enemySpawnMultiplier / Difficulty.eradication.enemySpawnMultiplier;
    }

    public static Difficulty difficulty(){
        return SurvivorsRun.current == null ? Difficulty.eradication : SurvivorsRun.current.difficulty;
    }

    public static void skipTo(int wave){
        if(!active()) return;
        for(Unit unit : state.rules.waveTeam.data().units.copy()) unit.remove();
        releasing.clear();
        state.rules.waveTimer = true;
        state.wave = Mathf.clamp(wave, 1, lastWave);
        logic.runWave();
        Log.info("[Survivors] Admin skipped to wave @", wave);
    }

    public static Family[] deal(long runId, int wave){
        Rand rand = new Rand(runId * 7919L + wave * 104729L);
        Seq<Family> pool = families.copy();
        Family[] out = new Family[3];
        if(wave <= 2){
            Seq<Family> walkers = pool.select(f -> !f.air() && !f.naval());
            out[0] = walkers.get(rand.random(walkers.size - 1));
            pool.remove(out[0]);
        }
        for(int i = 0; i < 3; i++){
            if(out[i] != null) continue;
            out[i] = pool.remove(rand.random(pool.size - 1));
        }
        return out;
    }

    static void onWave(){
        int wave = state.wave - 1;
        if(wave > lastWave || SurvivorsRun.current == null) return;
        rampDamage();
        release(wave, 1f);
        if(wave >= lastWave) state.rules.waveTimer = false;
    }

    public static void release(int wave, float share){
        if(wave < 1 || wave > lastWave) return;
        share *= enemyScale(difficulty()) * waveScale;
        Family[] dealt = deal(SurvivorsRun.current.id, wave);
        UnitType[] types = new UnitType[15];
        boolean[] naval = new boolean[15];
        int[] counts = new int[15];
        for(int i = 0; i < 15; i++){
            Family family = dealt[i / 5];
            types[i] = family.tiers[i % 5];
            naval[i] = family.naval();
            counts[i] = waves[wave - 1][i] <= 0 ? 0 : Math.max(1, Math.round(waves[wave - 1][i] * share));
        }
        releasing.add(new Release(types, naval, counts));
        Log.info("[Survivors] Wave @: @ (@ units)", wave, Seq.with(dealt).map(f -> f.name), java.util.Arrays.stream(counts).sum());
    }

    static void update(){
        calmShake(active());
        if(!active() || state.isPaused() || state.gameOver) return;

        for(int r = 0; r < releasing.size; r++){
            Release rel = releasing.get(r);
            rel.credit += rel.rate * Time.delta;
            while(rel.credit >= 1f && !rel.done()){
                rel.credit -= 1f;
                int best = -1;
                float most = -1f;
                for(int i = 0; i < rel.left.length; i++){
                    if(rel.left[i] <= 0) continue;
                    float share = rel.left[i] / (float)rel.total[i];
                    if(share > most){
                        most = share;
                        best = i;
                    }
                }
                rel.left[best]--;
                spawn(rel.types[best], rel.naval[best]);
            }
        }
        releasing.removeAll(Release::done);

        if((recountTimer += Time.delta) >= 60f){
            recountTimer = 0f;
            recount();
        }

        if(!won && state.wave > lastWave && releasing.isEmpty() && state.rules.waveTeam.data().units.isEmpty()){
            won = true;
            state.gameOver = true;
            Events.fire(new GameOverEvent(state.rules.defaultTeam));
        }
    }

    static @Nullable Tile ambushTile(boolean naval){
        Position goal = Chase.goal();
        if(goal == null) return null;
        for(int tries = 0; tries < 10; tries++){
            Tmp.v1.rnd(Mathf.random(10f, 16f) * tilesize).add(goal.getX(), goal.getY());
            Tile tile = world.tileWorld(Tmp.v1.x, Tmp.v1.y);
            if(tile != null && !tile.solid() && tile.floor().isLiquid == naval) return tile;
        }
        return null;
    }

    public static @Nullable Tile spawnTile(boolean naval){
        IntSeq list = naval && !waterSpawns.isEmpty() ? waterSpawns : groundSpawns;
        return list.isEmpty() ? null : world.tile(list.random());
    }

    static void spawn(UnitType type, boolean naval){
        Tile tile = Curses.on(Curses.ambush) && Mathf.chance(Curses.ambushChance) ? ambushTile(naval) : null;
        for(int tries = 0; tries < 8 && (tile == null || tile.build != null); tries++) tile = spawnTile(naval);
        if(tile == null || tile.build != null) return;
        Unit unit = type.create(state.rules.waveTeam);
        unit.set(tile.worldx() + Mathf.range(tilesize / 2f), tile.worldy() + Mathf.range(tilesize / 2f));
        unit.rotation = unit.angleTo(player.x, player.y);
        unit.add();
    }

    public static int available(Block block){
        SurvivorsRun run = SurvivorsRun.current;
        return run == null ? 0 : run.copies.get(block, 0) - placed.get(block, 0);
    }

    static Block planned(Building build){
        return build instanceof ConstructBuild cb ? (cb.activeDeconstruct ? null : cb.current) : build.block;
    }

    public static void recount(){
        SurvivorsRun run = SurvivorsRun.current;
        if(!active() || run == null) return;
        placed.clear();
        for(Building build : state.rules.defaultTeam.data().buildings){
            Block block = planned(build);
            if(block != null) placed.increment(block);
        }
        Seq<Block> allowed = buildable.select(b -> available(b) > 0);
        if(allowed.size != state.rules.bannedBlocks.size || !allowed.allMatch(state.rules.bannedBlocks::contains)){
            state.rules.bannedBlocks.clear();
            state.rules.bannedBlocks.addAll(allowed);
            if(!headless) ui.hudfrag.blockfrag.rebuild();
        }
    }

    static void lost(Building build){
        SurvivorsRun run = SurvivorsRun.current;
        if(run == null || build.team != state.rules.defaultTeam || TurretMods.willRebuild(build)) return;
        Block block = planned(build);
        if(block != null && run.copies.get(block, 0) > 0) run.copies.increment(block, -1);
    }

    static void register(){
        Events.on(WorldLoadEvent.class, e -> onLoaded());
        Events.on(WaveEvent.class, e -> {
            if(active()) onWave();
        });
        Events.on(BlockBuildBeginEvent.class, e -> {
            if(active() && !e.breaking && e.team == state.rules.defaultTeam) recount();
        });
        Events.on(BlockBuildEndEvent.class, e -> {
            if(active() && e.team == state.rules.defaultTeam) Core.app.post(Arena::recount);
        });
        Events.on(BlockDestroyEvent.class, e -> {
            if(!active() || e.tile.build == null) return;
            lost(e.tile.build);
            Core.app.post(Arena::recount);
        });
        Events.on(GameOverEvent.class, e -> {
            if(!active()) return;
            deleteSave();
            SurvivorsRun.finish(e.winner == state.rules.defaultTeam);
        });
        Events.run(Trigger.update, Arena::update);
    }
}

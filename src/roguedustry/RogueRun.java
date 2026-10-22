package roguedustry;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.maps.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock.*;
import mindustry.world.blocks.defense.turrets.LiquidTurret.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.production.Drill.*;

import static mindustry.Vars.*;

public class RogueRun{
    public static final String tag = "roguedustry";

    public static @Nullable RogueRun current;
    static boolean starting;

    public final Seq<Waves.Wave> waves = Waves.schedule();
    public final ObjectIntMap<Block> charges = new ObjectIntMap<>();
    public final IntMap<Block> paid = new IntMap<>();
    public final IntIntMap paidCopper = new IntIntMap();
    public Seq<Packs.Pack> shop = new Seq<>();
    public final Rand rand = new Rand();

    public int wave;
    public int level = 1, xp, pendingLevelUps;
    public boolean autoStart, won;
    public int inventoryVersion, shopVersion;

    final Seq<ActiveGroup> active = new Seq<>();
    float autoStartTimer, liquidTimer, drillTimer;
    int holdFrames;

    static class ActiveGroup{
        final Waves.Group group;
        float time;
        int spawned;

        ActiveGroup(Waves.Group group){
            this.group = group;
        }
    }

    public static boolean active(){
        return current != null && state.isGame() && state.rules.tags.containsKey(tag);
    }

    public static void start(Map map){
        Rules rules = map.applyRules(Gamemode.survival);
        rules.tags.put(tag, "true");

        //vanilla waves stay on so enemies target the core and the top-left wave panel works; this mod spawns the units
        rules.waves = true;
        rules.waveTimer = false;
        rules.waveSending = true;
        rules.winWave = Waves.schedule().size;
        rules.spawns = new Seq<>();
        rules.attackMode = false;
        rules.pvp = false;
        rules.fog = false;
        rules.possessionAllowed = false;
        rules.waveTeam = Team.crux;

        rules.blockWhitelist = true;
        rules.hideBannedBlocks = true;
        rules.bannedBlocks.clear();

        Rules.TeamRule player = rules.teams.get(rules.defaultTeam);
        player.cheat = true;
        player.infiniteResources = true;
        player.buildSpeedMultiplier = 4f;
        player.unitDamageMultiplier = 0f;
        rules.loadout = ItemStack.list(Items.copper, Packs.startingCopper);
        //removing a block would otherwise put its build cost into the core
        rules.deconstructRefundMultiplier = 0f;

        starting = true;
        control.playMap(map, rules);
    }

    void init(){
        //vanilla starts at 1; from 0, the panel shows the running wave and the win check fires after the last one
        state.wave = 0;
        //maps loaded from a file bring the game's default waves, which would spawn on top of ours
        state.rules.spawns = new Seq<>();
        removeOtherOres();
        rollShop();
        for(Packs.Entry e : Packs.startingKit()){
            addCharges(e.block, e.amount);
        }
        syncWhitelist();
    }

    void removeOtherOres(){
        for(Tile tile : world.tiles){
            if(tile.overlay() instanceof OreBlock && tile.overlay() != Blocks.oreCopper){
                tile.setOverlay(Blocks.air);
            }
        }
    }

    public int totalWaves(){
        return waves.size;
    }

    public boolean spawning(){
        return active.any();
    }

    public boolean canStartWave(){
        return !state.gameOver && state.wave < waves.size && !spawner.isSpawning() && state.enemies == 0;
    }

    public void startWave(){
        if(canStartWave()) logic.skipWave();
    }

    void onWave(){
        wave = state.wave;
        autoStartTimer = 0f;
        if(wave < 1 || wave > waves.size) return;
        rollShop();

        for(Waves.Group group : waves.get(wave - 1).groups){
            active.add(new ActiveGroup(group));
        }
        holdSpawning();
        Log.info("[Rogue] Wave @/@ started (@ units)", wave, waves.size, waves.get(wave - 1).totalUnits());
    }

    /** Keeps the game's spawning flag on while units are queued, so the next-wave button and win check can't fire between groups. */
    void holdSpawning(){
        Reflect.set(spawner, "spawning", true);
        holdFrames = 2;
    }

    void updateSpawning(){
        for(int i = active.size - 1; i >= 0; i--){
            ActiveGroup ag = active.get(i);
            ag.time += Time.delta;

            while(ag.spawned < ag.group.count && ag.time >= ag.group.delay + ag.group.spacing * ag.spawned){
                spawnUnit(ag.group, ag.spawned);
                ag.spawned++;
            }

            if(ag.spawned >= ag.group.count){
                active.remove(i);
            }
        }
    }

    void spawnUnit(Waves.Group group, int index){
        UnitType type = group.type;
        Seq<Tile> spawns = spawner.getSpawns();
        if(type.naval){
            spawns = spawns.select(t -> t.floor().isLiquid);
        }else if(!type.flying){
            Seq<Tile> land = spawns.select(t -> !t.floor().isDeep());
            if(land.any()) spawns = land;
        }
        if(spawns.isEmpty()) return;

        Tile spawn = spawns.get(index % spawns.size);
        float x = spawn.worldx(), y = spawn.worldy();

        if(type.flying){
            float margin = 40f;
            float angle = Angles.angle(world.width() / 2f, world.height() / 2f, spawn.x, spawn.y);
            float trns = Math.max(world.width(), world.height()) * Mathf.sqrt2 * tilesize;
            x = Mathf.clamp(world.width() * tilesize / 2f + Angles.trnsx(angle, trns), -margin, world.width() * tilesize + margin);
            y = Mathf.clamp(world.height() * tilesize / 2f + Angles.trnsy(angle, trns), -margin, world.height() * tilesize + margin);
        }

        Tmp.v1.rnd(Mathf.random(tilesize * 2f));
        Unit unit = type.spawn(state.rules.waveTeam, x + Tmp.v1.x, y + Tmp.v1.y);
        if(group.shields > 0f) unit.shield = group.shields;
        if(group.boss) unit.apply(StatusEffects.boss);
    }

    public int xpToNext(){
        return xpToNext(level);
    }

    public static int xpToNext(int level){
        return (int)(40 * Math.pow(level, 1.35));
    }

    public void gainXp(int amount){
        xp += amount;
        while(xp >= xpToNext()){
            xp -= xpToNext();
            level++;
            pendingLevelUps++;
            Log.info("[Rogue] Level up! Now level @", level);
        }
    }

    public void addCharges(Block block, int amount){
        charges.increment(block, amount);
        inventoryVersion++;
        syncWhitelist();
    }

    public int copper(){
        Building core = state.rules.defaultTeam.core();
        return core == null ? 0 : core.items.get(Items.copper);
    }

    boolean spendCopper(int amount){
        Building core = state.rules.defaultTeam.core();
        if(core == null || !core.items.has(Items.copper, amount)) return false;
        core.items.remove(Items.copper, amount);
        return true;
    }

    void giveCopper(int amount){
        Building core = state.rules.defaultTeam.core();
        if(core != null) core.items.add(Items.copper, Math.min(amount, core.getMaximumAccepted(Items.copper) - core.items.get(Items.copper)));
    }

    public void rollShop(){
        shop = Packs.roll(level, rand, Packs.shopSize);
        shopVersion++;
    }

    public boolean reroll(){
        if(!spendCopper(Packs.rerollPrice)) return false;
        rollShop();
        return true;
    }

    public boolean buy(Packs.Pack pack){
        if(!shop.contains(pack, true) || !spendCopper(Packs.price(pack))) return false;
        shop.remove(pack, true);
        shopVersion++;
        for(Packs.Entry e : pack.entries){
            addCharges(e.block, e.amount);
        }
        Log.info("[Rogue] Bought pack '@'", pack.name);
        return true;
    }

    public void choosePack(Packs.Pack pack){
        for(Packs.Entry e : pack.entries){
            addCharges(e.block, e.amount);
        }
        pendingLevelUps = Math.max(pendingLevelUps - 1, 0);
        Log.info("[Rogue] Picked pack '@'", pack.name);
    }

    void syncWhitelist(){
        if(!state.rules.tags.containsKey(tag)) return;

        state.rules.bannedBlocks.clear();
        for(var e : charges){
            if(e.value > 0) state.rules.bannedBlocks.add(e.key);
        }
        for(Block drill : Packs.drills().keys()){
            state.rules.bannedBlocks.add(drill);
        }
        //the game stops building anything that isn't whitelisted, so a block keeps its spot until its last paid copy is done
        for(var e : paid){
            if(world.tile(e.key) != null && world.tile(e.key).build instanceof ConstructBuild) state.rules.bannedBlocks.add(e.value);
        }

        if(!headless){
            if(control.input.block != null && charges.get(control.input.block, 0) <= 0){
                control.input.block = null;
            }
            ui.hudfrag.blockfrag.rebuild();
        }
    }

    void onBuildBegin(BlockBuildBeginEvent e){
        if(e.team != state.rules.defaultTeam || e.breaking || !(e.tile.build instanceof ConstructBuild cb)) return;

        if(cb.prevBuild != null){
            for(Building prev : cb.prevBuild){
                refund(prev.tile.pos());
            }
        }

        Block block = cb.current;
        int price = Packs.drills().get(block, 0);
        if(price > 0){
            if(spendCopper(price)){
                paidCopper.put(e.tile.pos(), price);
            }else{
                reject(e, "Not enough copper for " + block.localizedName + " (" + price + ")");
            }
        }else if(charges.get(block, 0) > 0){
            charges.increment(block, -1);
            paid.put(e.tile.pos(), block);
            inventoryVersion++;
            syncWhitelist();
        }else{
            reject(e, "No " + block.localizedName + " left");
        }
    }

    void reject(BlockBuildBeginEvent e, String message){
        Tile tile = e.tile;
        if(e.unit != null) e.unit.plans.remove(p -> p.x == tile.x && p.y == tile.y && !p.breaking);
        tile.remove();
        if(!headless) ui.showInfoToast(message, 2f);
    }

    void onBuildEnd(BlockBuildEndEvent e){
        if(e.team != state.rules.defaultTeam) return;
        if(e.breaking){
            refund(e.tile.pos());
        }else{
            syncWhitelist();
        }
    }

    void refund(int pos){
        Block block = paid.remove(pos);
        if(block != null){
            addCharges(block, 1);
        }
        int price = paidCopper.remove(pos, 0);
        if(price > 0) giveCopper(price);
    }

    void onDestroy(Tile tile){
        paidCopper.remove(tile.pos(), 0);
        if(paid.remove(tile.pos()) != null) syncWhitelist();
    }

    void update(){
        if(state.isPaused() || state.gameOver) return;

        updateSpawning();

        if(spawning()){
            holdSpawning();
        }else if(holdFrames > 0 && --holdFrames == 0){
            Reflect.set(spawner, "spawning", false);
        }

        if(autoStart && canStartWave()){
            autoStartTimer += Time.delta;
            if(autoStartTimer >= 60f * 3f) startWave();
        }

        if((drillTimer += Time.delta) >= 15f){
            drillTimer = 0f;
            collectOre();
        }

        //the cheat rule doesn't give liquid turrets ammo
        if((liquidTimer += Time.delta) >= 30f){
            liquidTimer = 0f;
            for(Building build : state.rules.defaultTeam.data().buildings){
                if(build instanceof LiquidTurretBuild lt && lt.liquids.get(Liquids.water) < lt.block.liquidCapacity){
                    lt.liquids.add(Liquids.water, lt.block.liquidCapacity - lt.liquids.get(Liquids.water));
                }
            }
        }
    }

    void collectOre(){
        Building core = state.rules.defaultTeam.core();
        if(core == null) return;
        int mined = 0;
        for(Building build : state.rules.defaultTeam.data().buildings){
            if(build instanceof DrillBuild drill && drill.items.total() > 0){
                mined += drill.items.get(Items.copper);
                drill.items.clear();
            }
        }
        if(mined > 0) giveCopper(mined);
    }

    static void registerEvents(){
        //playMap() sets the new rules only after WorldLoadEvent
        Events.run(Trigger.newGame, () -> {
            if(!starting || !state.rules.tags.containsKey(tag)) return;
            starting = false;
            current = new RogueRun();
            current.init();
        });

        Events.on(WorldLoadEvent.class, e -> {
            if(starting) return;
            current = null;
            if(state.rules.tags.containsKey(tag)){
                Log.info("[Rogue] Loaded a run save; starting a fresh run on this map");
                current = new RogueRun();
                current.init();
            }
        });

        Events.on(BlockBuildBeginEvent.class, e -> {
            if(active()) current.onBuildBegin(e);
        });

        Events.on(BlockBuildEndEvent.class, e -> {
            if(active()) current.onBuildEnd(e);
        });

        Events.on(BlockDestroyEvent.class, e -> {
            if(active()) current.onDestroy(e.tile);
        });

        Events.on(UnitDestroyEvent.class, e -> {
            if(active() && e.unit.team == state.rules.waveTeam){
                current.gainXp(Math.max(1, (int)(e.unit.maxHealth / 10f)));
            }
        });

        Events.on(WaveEvent.class, e -> {
            if(active()) current.onWave();
        });

        Events.on(GameOverEvent.class, e -> {
            if(!active()) return;
            current.won = e.winner == state.rules.defaultTeam;
            Log.info("[Rogue] Run over: @", current.won ? "victory" : "core destroyed");
        });

        //runs after Time.update(), which can clear the spawning flag, and before the win check
        Events.run(Trigger.afterGameUpdate, () -> {
            if(active()) current.update();
        });
    }
}

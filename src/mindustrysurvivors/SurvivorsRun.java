package mindustrysurvivors;

import arc.*;
import arc.func.*;
import arc.math.*;
import arc.scene.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.ui.fragments.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.ConstructBlock.*;
import mindustry.world.blocks.defense.ForceProjector.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.production.Drill.*;
import mindustry.world.blocks.production.SolidPump.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.blocks.storage.CoreBlock.*;
import mindustry.world.blocks.units.Reconstructor.*;
import mindustry.world.blocks.units.UnitFactory.*;

import java.io.*;
import java.util.*;
import mindustry.io.*;

import static mindustry.Vars.*;

public class SurvivorsRun{
    public static final String saveKey = "mindustry-survivors-run", lastStandTag = "mindustry-survivors-last-stand";

    public static @Nullable SurvivorsRun current;
    static final ObjectMap<TechNode, ItemStack[]> realCosts = new ObjectMap<>();
    static final float waveGapScale = 0.2f;
    static final ObjectFloatMap<UnitType> realBuildSpeed = new ObjectFloatMap<>();
    static final float coreBuildScale = 2f;
    static final ObjectMap<Planet, Boolean> realUnlocked = new ObjectMap<>();
    static final Seq<Planet> realLaunchTargets = new Seq<>();

    public long id;
    public boolean wireless;
    public int launches, picksTaken;
    public Difficulty difficulty = Difficulty.eradication;
    //levels past Eradication: each one has a curse budget and a smallest number of curses
    public int heat;
    public static final int maxHeat = 4;
    static final int[] heatBudget = {0, 4, 6, 8, 11}, heatMinCurses = {0, 2, 2, 3, 3};
    public final ObjectIntMap<Rewards.Reward> rewards = new ObjectIntMap<>();
    public final ObjectIntMap<Block> copies = new ObjectIntMap<>();
    public final Seq<Curses.Curse> curses = new Seq<>();
    public float xp;
    public int level, relicsTaken;
    boolean sectorLoaded;
    public boolean pendingCurse, pendingRelic, pendingGift;
    public int pendingModifiers;
    public final Seq<Rewards.Reward> modOffer = new Seq<>();
    public final boolean[] rerolled = new boolean[3];
    public long loadedAt;

    float effectTimer, unlockTimer, cleanupTimer;
    final ObjectFloatMap<Block> blockSpeed = new ObjectFloatMap<>(), blockHealth = new ObjectFloatMap<>();

    public static int heatBudget(int heat){
        return heatBudget[heat];
    }

    public static int heatMinCurses(int heat){
        return heatMinCurses[heat];
    }

    public static boolean exists(){
        return Core.settings.has(saveKey) && Arena.saved();
    }

    public static void startNew(){
        startNew(Difficulty.eradication);
    }

    public static void startNew(Difficulty difficulty){
        startNew(difficulty, 0);
    }

    public static void startNew(Difficulty difficulty, int heat){
        Arena.deleteSave();
        SurvivorsRun run = new SurvivorsRun();
        run.id = Math.abs(new Rand().nextLong());
        run.difficulty = heat > 0 ? Difficulty.eradication : difficulty;
        run.heat = heat;
        run.save();
        current = run;
        current.applyTweaks();
        Arena.start(run.id);
    }

    public static void resume(){
        current = load();
        if(current == null) return;
        current.applyTweaks();
        Arena.resume();
    }

    public static void finish(boolean won){
        Core.settings.remove(saveKey);
        Log.info("[Survivors] Run @ on wave @", won ? "won" : "lost", state.wave);
    }

    public static void enter(){
        current = load();
        if(current == null) return;
        Profile.switchTo(Profile.run);
        if(current.wireless){
            for(TechNode node : TechTree.all){
                if(node.rootNode != Planets.serpulo.techTree && node != Planets.serpulo.techTree) continue;
                realCosts.put(node, node.requirements);
                node.setupRequirements(ItemStack.empty);
            }
            control.checkAutoUnlocks();
        }
        lockSectors();
        setSerpuloOnly(true);
        Planets.serpulo.campaignRules.sectorInvasion = false;
        current.applyTweaks();
    }

    public static void leave(){
        for(var e : realCosts) e.key.requirements = e.value;
        realCosts.clear();
        TurretMods.restoreCosts();
        setFastBuilders(false);
        setSerpuloOnly(false);
        Profile.switchTo(Profile.player);
        Planets.serpulo.allowLaunchToNumbered = true;
        current = null;
    }

    public static void abandon(){
        Arena.deleteSave();
        Core.settings.remove(saveKey);
        current = null;
    }

    static void lockSectors(){
        Planets.serpulo.allowLaunchToNumbered = SectorPresets.planetaryTerminal.sector.isCaptured();
    }

    static void setFastBuilders(boolean fast){
        for(UnitType type : Seq.with(UnitTypes.alpha, UnitTypes.beta, UnitTypes.gamma)){
            if(!realBuildSpeed.containsKey(type)) realBuildSpeed.put(type, type.buildSpeed);
            type.buildSpeed = realBuildSpeed.get(type, 1f) * (fast ? coreBuildScale : 1f);
        }
    }

    static void setSerpuloOnly(boolean only){
        if(only && realUnlocked.isEmpty()){
            for(Planet p : content.planets()) realUnlocked.put(p, p.alwaysUnlocked);
            realLaunchTargets.set(Planets.serpulo.launchCandidates);
        }
        for(var e : realUnlocked) e.key.alwaysUnlocked = only ? e.key == Planets.serpulo && e.value : e.value;
        Planets.serpulo.launchCandidates.set(only ? realLaunchTargets.select(p -> p == Planets.serpulo) : realLaunchTargets);
        if(!only) realUnlocked.clear();
    }

    public void applyTweaks(){
        TurretMods.applyCosts();
        setFastBuilders(wireless);
    }

    public static boolean active(){
        return current != null && Arena.active();
    }

    public static final int maxRelics = 4, levelsPerRelic = 10;
    public static final boolean relicsOn = false;

    public static boolean captured(){
        return state.rules.sector != null && state.rules.sector.info.wasCaptured;
    }

    public static float xpFor(int level){
        return 5f + 10f * level + 0.4f * level * level;
    }

    public float xpProgress(){
        return Math.min(xp / xpFor(level), 1f);
    }

    void resetSector(){
        for(Curses.Curse m : curses) Curses.lifted(m);
        rewards.clear();
        copies.clear();
        curses.clear();
        clearOffer();
        xp = 0f;
        level = relicsTaken = picksTaken = pendingModifiers = 0;
        pendingRelic = pendingCurse = pendingGift = false;
        sectorLoaded = false;
    }

    void writeSector(DataOutput out) throws IOException{
        out.writeInt(4);
        out.writeFloat(xp);
        out.writeInt(level);
        out.writeInt(relicsTaken);
        out.writeBoolean(pendingRelic);
        out.writeInt(pendingModifiers);
        out.writeInt(picksTaken);
        out.writeInt(rewards.size);
        for(var e : rewards){
            out.writeUTF(e.key.id);
            out.writeInt(e.value);
        }
        out.writeInt(curses.size);
        for(Curses.Curse m : curses) out.writeUTF(m.id);
        out.writeInt(modOffer.size);
        for(Rewards.Reward r : modOffer) out.writeUTF(r.id);
        for(boolean b : rerolled) out.writeBoolean(b);
        out.writeBoolean(pendingGift);
        Curses.write(out);
        out.writeInt(copies.size);
        for(var e : copies){
            out.writeUTF(e.key.name);
            out.writeInt(e.value);
        }
    }

    void readSector(DataInput in) throws IOException{
        resetSector();
        int version = in.readInt();
        xp = in.readFloat();
        level = in.readInt();
        relicsTaken = in.readInt();
        pendingRelic = in.readBoolean();
        pendingModifiers = in.readInt();
        picksTaken = in.readInt();
        int count = in.readInt();
        for(int i = 0; i < count; i++){
            Rewards.Reward r = Rewards.get(in.readUTF());
            int n = in.readInt();
            if(r != null) rewards.put(r, n);
        }
        count = in.readInt();
        for(int i = 0; i < count; i++){
            Curses.Curse m = Curses.get(in.readUTF());
            if(m != null) curses.add(m);
        }
        count = in.readInt();
        for(int i = 0; i < count; i++){
            Rewards.Reward r = Rewards.get(in.readUTF());
            if(r != null) modOffer.add(r);
        }
        for(int i = 0; i < rerolled.length; i++) rerolled[i] = in.readBoolean();
        if(version >= 2) pendingGift = in.readBoolean();
        if(version >= 3) Curses.read(in);
        if(version >= 4){
            count = in.readInt();
            for(int i = 0; i < count; i++){
                Block block = content.block(in.readUTF());
                int n = in.readInt();
                if(block != null) copies.put(block, n);
            }
        }
        sectorLoaded = true;
    }

    long sectorSeed(){
        return id * 31L + (state.rules.sector == null ? 0 : state.rules.sector.id) * 7919L;
    }

    public static boolean wirelessRun(){
        return current != null && current.wireless && Profile.inRun();
    }

    public static boolean wirelessOn(){
        return active() && current.wireless;
    }

    public int stacks(Rewards.Reward reward){
        return rewards.get(reward, 0);
    }

    void onLaunch(){
        launches++;
        save();
        Log.info("[Survivors] Launched to @ (launch @)", state.rules.sector.name(), launches);
    }

    void onLoaded(){
        loadedAt = Time.millis();
        state.rules.disableUnitCap = true;
        if(!sectorLoaded){
            pendingRelic = relicsOn && relicOffer().any();
            copies.putAll(Arena.startingKit());
            curses.set(Curses.roll(heatBudget[heat], heatMinCurses[heat], new Rand(id * 7121L)));
            if(curses.any()) Log.info("[Survivors] Curses: @", curses.map(c -> c.name));
        }
        sectorLoaded = true;
        if(!relicsOn) pendingRelic = pendingCurse = false;
        Arena.recount();
        applyTweaks();
        if(!headless) ui.hudfrag.blockfrag.rebuild();
    }

    static boolean tutorial(@Nullable Sector sector){
        return sector != null && sector.preset != null && Seq.with(SectorPresets.groundZero, SectorPresets.frozenForest, SectorPresets.fungalPass, SectorPresets.frontier).contains(sector.preset);
    }

    //these maps run world processors that keep the map small and the waves off until the tutorial steps are done
    static void skipTutorial(){
        state.rules.objectives.clear();
        Seq<Building> processors = new Seq<>();
        for(Building build : Groups.build){
            if(build.block == Blocks.worldProcessor) processors.add(build);
        }
        for(Building build : processors) build.tile.remove();
        if(state.rules.limitMapArea) Call.setMapArea(0, 0, world.width(), world.height());
        state.rules.waves = true;
        state.rules.waveTimer = true;
        state.rules.waveSending = true;
    }

    static void hideHints(){
        try{
            var field = HintsFragment.class.getDeclaredField("group");
            field.setAccessible(true);
            Element group = (Element)field.get(ui.hints);
            Boolp shown = group.visibility;
            group.visibility = () -> !active() && (shown == null || shown.get());
        }catch(Throwable e){
            Log.err("[Survivors] Could not hide hints", e);
        }
    }

    public static boolean disabled(Block block){
        return block.category == Category.logic;
    }

    public Seq<Curses.Curse> curseOffer(){
        return Curses.offer(curses, new Rand(id * 4421L + launches * 104729L));
    }

    public void curse(@Nullable Curses.Curse curse){
        if(!pendingCurse) return;
        pendingCurse = false;
        if(curse != null && !curses.contains(curse)){
            curses.add(curse);
            Log.info("[Survivors] Enemies got '@'", curse.name);
        }
        save();
    }

    public Seq<Rewards.Reward> relicOffer(){
        return Rewards.rollRelics(rewards, curses.size, new Rand(sectorSeed() + relicsTaken * 104729L));
    }

    public Seq<Curses.Curse> relicCurses(){
        return Curses.offer(curses, new Rand(sectorSeed() * 3L + relicsTaken * 4421L));
    }

    public @Nullable Curses.Curse curseFor(Rewards.Reward relic){
        int i = relicOffer().indexOf(relic);
        Seq<Curses.Curse> curses = relicCurses();
        return i >= 0 && i < curses.size ? curses.get(i) : null;
    }

    public Seq<Rewards.Reward> modifierOffer(){
        if(modOffer.isEmpty() || modOffer.contains(r -> stacks(r) > 0)){
            modOffer.set(Rewards.rollModifiers(rewards, new Rand(sectorSeed() * 5L + picksTaken * 104729L)));
            Arrays.fill(rerolled, false);
            save();
        }
        return modOffer;
    }

    public boolean canReroll(int index){
        return index < modOffer.size && !rerolled[index] && Rewards.rerollChoices(rewards, modOffer, modOffer.get(index)).any();
    }

    public void reroll(int index){
        if(!canReroll(index)) return;
        Seq<Rewards.Reward> choices = Rewards.rerollChoices(rewards, modOffer, modOffer.get(index));
        modOffer.set(index, choices.random());
        rerolled[index] = true;
        save();
    }

    void clearOffer(){
        modOffer.clear();
        Arrays.fill(rerolled, false);
    }

    public void take(Rewards.Reward reward){
        if(reward.isRelic()){
            if(!pendingRelic) return;
            Curses.Curse curse = curseFor(reward);
            pendingRelic = false;
            relicsTaken++;
            if(curse != null && !curses.contains(curse)){
                curses.add(curse);
                Log.info("[Survivors] Enemies got '@'", curse.name);
            }
        }else{
            if(pendingModifiers <= 0) return;
            pendingModifiers--;
            picksTaken++;
            clearOffer();
        }
        if(reward.copyOf != null){
            copies.increment(reward.copyOf, Arena.cardSize(reward.copyOf));
            Arena.recount();
            Log.info("[Survivors] Took '@'", reward.name);
            return;
        }
        grant(reward);
    }

    void grant(Rewards.Reward reward){
        rewards.increment(reward);
        if(reward == Rewards.absolution){
            Rand rand = new Rand(id * 31L + launches);
            for(int i = 0; i < 2 && curses.any(); i++){
                Curses.Curse lifted = curses.remove(rand.random(curses.size - 1));
                Curses.lifted(lifted);
                Log.info("[Survivors] Curse lifted: '@'", lifted.name);
            }
        }
        if(reward == Rewards.gracePeriod) pendingGift = true;
        applyTweaks();
        save();
        Log.info("[Survivors] Took '@'", reward.name);
    }

    public void adminToggle(Rewards.Reward reward){
        if(stacks(reward) > 0){
            rewards.remove(reward);
            if(reward.effect != null){
                reward.setStacks(0);
                for(Unit unit : state.rules.defaultTeam.data().units) unit.unapply(reward.effect);
            }
            if(reward == Rewards.gracePeriod) pendingGift = false;
            if(reward.isRelic()) relicsTaken = Math.max(0, relicsTaken - 1);
            clearOffer();
            applyTweaks();
            save();
            Log.info("[Survivors] Admin removed '@'", reward.name);
        }else{
            if(reward.isRelic()) relicsTaken++;
            clearOffer();
            grant(reward);
        }
    }

    public void adminToggle(Curses.Curse curse){
        if(curses.remove(curse)){
            Curses.lifted(curse);
            Log.info("[Survivors] Admin lifted '@'", curse.name);
        }else{
            curses.add(curse);
            Log.info("[Survivors] Admin added '@'", curse.name);
        }
        save();
    }

    public static void adminCapture(){
        if(!active() || captured()) return;
        Call.sectorCapture();
        Seq<Unit> enemies = state.rules.waveTeam.data().units.copy();
        for(Unit unit : enemies) unit.remove();
        Log.info("[Survivors] Admin captured @", state.rules.sector.name());
    }

    public static void adminUnlockResearch(){
        for(TechNode node : TechTree.all){
            if(node.rootNode != Planets.serpulo.techTree && node != Planets.serpulo.techTree) continue;
            if(!(node.content instanceof Block block && disabled(block))) node.content.unlock();
        }
        lockSectors();
        Log.info("[Survivors] Admin unlocked all research");
    }

    public static Seq<SectorPreset> campaignOrder(){
        ObjectIntMap<SectorPreset> steps = new ObjectIntMap<>();
        Seq<SectorPreset> out = new Seq<>();
        for(TechNode node : TechTree.all){
            if(node.content instanceof SectorPreset preset && preset.planet == Planets.serpulo && !out.contains(preset)) out.add(preset);
        }
        Seq<SectorPreset> tree = out.copy();
        out.sort(Structs.comps(Structs.comparingInt(p -> campaignStep(p, steps)), Structs.comparingInt(tree::indexOf)));
        return out;
    }

    static int campaignStep(SectorPreset preset, ObjectIntMap<SectorPreset> steps){
        if(steps.containsKey(preset)) return steps.get(preset);
        steps.put(preset, 0);
        int step = 0;
        TechNode node = preset.techNode;
        if(node != null){
            if(node.parent != null && node.parent.content instanceof SectorPreset parent) step = Math.max(step, campaignStep(parent, steps) + 1);
            for(Objectives.Objective objective : node.objectives){
                if(objective instanceof Objectives.SectorComplete done) step = Math.max(step, campaignStep(done.preset, steps) + 1);
            }
        }
        steps.put(preset, step);
        return step;
    }

    public static void adminLand(SectorPreset preset){
        if(!active()) return;
        control.playSector(state.rules.sector, preset.sector);
    }

    public void skipPicks(){
        pendingRelic = false;
        pendingModifiers = 0;
        clearOffer();
        save();
    }

    void update(){
        if(state.isPaused() || state.gameOver) return;

        checkLastStand();

        if(wireless && (unlockTimer += Time.delta) >= 120f){
            unlockTimer = 0f;
            control.checkAutoUnlocks();
        }

        if((effectTimer += Time.delta) < 30f) return;
        effectTimer = 0f;

        for(var e : rewards) e.key.setStacks(e.value);
        for(Unit unit : state.rules.defaultTeam.data().units){
            for(var e : rewards){
                Rewards.Reward reward = e.key;
                if(reward.effect == null || unit.hasEffect(reward.effect)) continue;
                if(reward.coreUnits ? unit.spawnedByCore : reward.units.contains(unit.type)) unit.apply(reward.effect);
            }
        }

        blockSpeed.clear();
        blockHealth.clear();
        for(var e : rewards){
            for(Block block : e.key.blocks){
                if(e.key.speed > 0f) blockSpeed.put(block, blockSpeed.get(block, 1f) * (1f + e.key.speed * e.value));
                if(e.key.wallHealth > 0f) blockHealth.put(block, blockHealth.get(block, 1f) + e.key.wallHealth * e.value);
            }
        }

        float factories = stacks(Rewards.warFactory) > 0 ? 3f : 1f, mining = stacks(Rewards.oreMagnet) > 0 ? 3f : 1f;
        boolean heal = stacks(Rewards.livingMetal) > 0;

        for(Building build : state.rules.defaultTeam.data().buildings){
            float boost = blockSpeed.get(build.block, 1f) * TurretMods.speed(build) * BlockMods.speed(build) *
                (build instanceof UnitFactoryBuild || build instanceof ReconstructorBuild ? factories :
                build instanceof DrillBuild || build instanceof SolidPumpBuild ? mining :
                1f);
            if(boost > 1.001f) build.applyBoost(boost, 45f);

            float toughness = blockHealth.get(build.block, 1f);
            if(toughness > 1.001f || build.maxHealth > build.block.health + 0.5f) toughen(build, build.block.health * toughness);

            if(heal && build.damaged()) build.heal(build.maxHealth * 0.01f);
        }
    }

    static void toughen(Building build, float target){
        if(Math.abs(build.maxHealth - target) < 0.5f) return;
        //a reloaded save keeps the raised health but resets max health to the block's own
        build.health = build.health > build.maxHealth ? Math.min(build.health, target) : build.health * target / build.maxHealth;
        build.maxHealth = target;
    }

    void checkLastStand(){
        if(stacks(Rewards.lastStand) <= 0 || state.rules.tags.getBool(lastStandTag)) return;
        for(CoreBuild core : state.rules.defaultTeam.cores()){
            if(core.health >= core.maxHealth * 0.25f) continue;
            state.rules.tags.put(lastStandTag, "true");
            float radius = Rewards.lastStandRadius * tilesize;
            Seq<Unit> doomed = new Seq<>();
            Units.nearbyEnemies(core.team, core.x, core.y, radius, doomed::add);
            //removed instead of killed, so death curses like Volatile don't hit the core
            for(Unit unit : doomed){
                Fx.massiveExplosion.at(unit.x, unit.y);
                unit.remove();
            }
            Fx.spawnShockwave.at(core.x, core.y, radius);
            Sounds.explosionTitan.at(core);
            if(!headless) ui.announce("[#" + Rewards.Rarity.relic.color + "]Last Stand!");
            Log.info("[Survivors] Last Stand removed @ enemies", doomed.size);
            return;
        }
    }

    void prefab(Tile tile, Unit unit){
        if(!(tile.build instanceof ConstructBuild build) || build.current == null) return;
        Block block = build.current;
        CoreBuild core = unit.closestCore();
        float cost = state.rules.buildCostMultiplier;
        if(core == null || !core.items.has(block.requirements, cost)) return;

        Object config = null;
        for(BuildPlan plan : unit.plans()){
            if(plan.x == tile.x && plan.y == tile.y && plan.block == block) config = plan.config;
        }
        for(ItemStack stack : block.requirements) core.items.remove(stack.item, Math.round(stack.amount * cost));
        ConstructBlock.constructed(tile, block, unit, (byte)build.rotation, build.team, config);
    }

    void recycle(Unit unit){
        if(unit.spawnedByCore || unit.type.internal || unit instanceof TimedKillc || !Mathf.chance(0.5)) return;
        CoreBuild core = unit.closestCore();
        if(core == null) return;
        UnitType type = unit.type;
        Team team = unit.team;
        Core.app.post(() -> {
            if(!active() || !core.isValid()) return;
            Fx.spawn.at(BlockMods.spawnBeside(type, team, core));
        });
    }

    public static Seq<Item> giftChoices(){
        return content.items().select(i -> i.unlockedNow() && i.isOnPlanet(Planets.serpulo) && !i.isHidden());
    }

    public void gift(Seq<Item> items){
        if(!pendingGift) return;
        pendingGift = false;
        CoreBuild core = state.rules.defaultTeam.core();
        if(core == null) return;
        for(Item item : items) core.handleStack(item, Rewards.giftAmount, null);
        Log.info("[Survivors] Grace Period gave @", items.map(i -> i.name));
    }

    void scrap(Building build){
        if(build == null || build.team != state.rules.defaultTeam || build instanceof ConstructBuild || build instanceof CoreBuild) return;
        CoreBuild core = build.closestCore();
        if(core == null) return;
        for(ItemStack stack : build.block.requirements){
            int amount = Math.round(stack.amount * state.rules.buildCostMultiplier * 0.5f);
            core.items.add(stack.item, Math.max(0, Math.min(amount, core.getMaximumAccepted(stack.item) - core.items.get(stack.item))));
        }
    }

    public void save(){
        StringBuilder out = new StringBuilder();
        out.append("id=").append(id).append('\n');
        out.append("wireless=").append(wireless).append('\n');
        out.append("launches=").append(launches).append('\n');
        out.append("difficulty=").append(difficulty.name()).append('\n');
        out.append("heat=").append(heat).append('\n');
        Core.settings.put(saveKey, out.toString());
    }

    public static @Nullable SurvivorsRun load(){
        String text = Core.settings.getString(saveKey, null);
        if(text == null) return null;
        try{
            SurvivorsRun run = new SurvivorsRun();
            for(String line : text.split("\n")){
                int eq = line.indexOf('=');
                if(eq < 0) continue;
                String k = line.substring(0, eq), v = line.substring(eq + 1);
                switch(k){
                    case "id" -> run.id = Long.parseLong(v);
                    case "wireless" -> run.wireless = Boolean.parseBoolean(v);
                    case "launches" -> run.launches = Integer.parseInt(v);
                    case "difficulty" -> run.difficulty = Structs.find(Difficulty.all, d -> d.name().equals(v));
                    case "heat" -> run.heat = Mathf.clamp(Integer.parseInt(v), 0, maxHeat);
                }
            }
            return run;
        }catch(Throwable e){
            Log.err("[Survivors] Could not read the saved run", e);
            return null;
        }
    }

    static Seq<String> list(String v){
        return Seq.with(v.split(",")).select(s -> !s.isEmpty());
    }

    static void registerEvents(){
        SaveVersion.addCustomChunk("mindustry-survivors-sector", new SaveFileReader.CustomChunk(){
            @Override
            public boolean shouldWrite(){
                return current != null && Arena.active();
            }

            @Override
            public void write(DataOutput out) throws IOException{
                current.writeSector(out);
            }

            @Override
            public void read(DataInput in) throws IOException{
                if(current != null) current.readSector(in);
            }
        });

        //custom save chunks are read after this event, so a loaded sector's picks are not wiped
        Events.on(WorldLoadBeginEvent.class, e -> {
            if(current != null) current.resetSector();
        });

        Events.on(ClientLoadEvent.class, e -> {
            //a run's campaign is still live if the game was closed during a run
            if(Profile.inRun()) leave();
            hideHints();
        });

        Events.on(WaveEvent.class, e -> {
            if(wirelessOn()) state.wavetime *= waveGapScale;
        });

        Events.on(SectorLaunchEvent.class, e -> {
            if(current != null && Profile.inRun()) current.onLaunch();
        });

        Events.on(SectorCaptureEvent.class, e -> {
            if(current == null || !Profile.inRun()) return;
            lockSectors();
        });

        Events.on(WorldLoadEvent.class, e -> Core.app.post(() -> {
            if(active()) current.onLoaded();
        }));

        Events.on(BlockBuildBeginEvent.class, e -> {
            if(e.breaking || e.unit == null || e.team != state.rules.defaultTeam) return;
            if(active() && current.stacks(Rewards.prefab) > 0) Core.app.post(() -> {
                if(active()) current.prefab(e.tile, e.unit);
            });
        });

        Events.on(UnitDestroyEvent.class, e -> {
            if(!active()) return;
            if(e.unit.team == state.rules.defaultTeam && current.stacks(Rewards.recycle) > 0) current.recycle(e.unit);
        });

        Events.on(BlockDestroyEvent.class, e -> {
            if(active() && current.stacks(Rewards.scrapRebirth) > 0) current.scrap(e.tile.build);
        });

        Events.run(Trigger.afterGameUpdate, () -> {
            if(active()) current.update();
        });
    }
}

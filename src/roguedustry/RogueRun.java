package roguedustry;

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

import java.util.*;

import static mindustry.Vars.*;

public class RogueRun{
    public static final String saveKey = "roguedustry-run", graceTag = "roguedustry-grace", prefabTag = "roguedustry-prefab", lastStandTag = "roguedustry-last-stand";

    public static @Nullable RogueRun current;
    static final ObjectMap<TechNode, ItemStack[]> realCosts = new ObjectMap<>();
    static final ObjectIntMap<CoreBlock> realCapacity = new ObjectIntMap<>();
    static float realMonoMining = -1f;

    public long id;
    public int launches, picksTaken;
    public final ObjectIntMap<Rewards.Reward> rewards = new ObjectIntMap<>();
    public final Seq<Mutators.Mutator> mutators = new Seq<>();
    public final IntIntMap sectorPicks = new IntIntMap();
    public boolean pendingCurse, pendingRelic;
    public int pendingModifiers;
    public final Seq<Rewards.Reward> modOffer = new Seq<>();
    public final boolean[] rerolled = new boolean[3];
    public long loadedAt;

    float effectTimer, unlockTimer, cleanupTimer;
    final IntFloatMap lastShield = new IntFloatMap(), lastBuildup = new IntFloatMap();
    final ObjectFloatMap<Block> blockSpeed = new ObjectFloatMap<>(), blockHealth = new ObjectFloatMap<>();

    public static boolean exists(){
        return Core.settings.has(saveKey);
    }

    public static void startNew(){
        if(Profile.inRun()) leave();
        Profile.deleteRun();
        RogueRun run = new RogueRun();
        run.id = Math.abs(new Rand().nextLong());
        run.save();
        enter();
    }

    public static void enter(){
        current = load();
        if(current == null) return;
        Profile.switchTo(Profile.run);
        for(TechNode node : TechTree.all){
            if(node.rootNode != Planets.serpulo.techTree && node != Planets.serpulo.techTree) continue;
            realCosts.put(node, node.requirements);
            node.setupRequirements(ItemStack.empty);
        }
        control.checkAutoUnlocks();
        lockSectors();
        current.applyTweaks();
    }

    public static void leave(){
        for(var e : realCosts) e.key.requirements = e.value;
        realCosts.clear();
        setCoreSize(0);
        setMonoMining(0);
        Profile.switchTo(Profile.player);
        Planets.serpulo.allowLaunchToNumbered = true;
        current = null;
    }

    public static void abandon(){
        if(Profile.inRun()) leave();
        Profile.deleteRun();
        Core.settings.remove(saveKey);
        current = null;
    }

    static void lockSectors(){
        Planets.serpulo.allowLaunchToNumbered = SectorPresets.planetaryTerminal.sector.isCaptured();
    }

    static void setCoreSize(int stacks){
        for(Block block : content.blocks()){
            if(!(block instanceof CoreBlock core)) continue;
            if(!realCapacity.containsKey(core)) realCapacity.put(core, core.itemCapacity);
            core.itemCapacity = realCapacity.get(core) * (1 + stacks);
        }
    }

    static void setMonoMining(int stacks){
        if(realMonoMining < 0f) realMonoMining = UnitTypes.mono.mineSpeed;
        UnitTypes.mono.mineSpeed = realMonoMining * (1f + stacks);
    }

    void applyTweaks(){
        setCoreSize(stacks(Rewards.biggerCore));
        setMonoMining(stacks(Rewards.miningCrew));
    }

    public static boolean active(){
        return current != null && Profile.inRun() && state.isGame() && state.isCampaign();
    }

    public int stacks(Rewards.Reward reward){
        return rewards.get(reward, 0);
    }

    void onLaunch(){
        launches++;
        pendingRelic = Rewards.rollRelics(rewards, mutators.size, new Rand()).any();
        pendingCurse = curseOffer().any();
        save();
        Log.info("[Rogue] Launched to @ (launch @)", state.rules.sector.name(), launches);
    }

    void onLoaded(){
        loadedAt = Time.millis();
        state.rules.disableUnitCap = true;
        for(Block block : content.blocks()){
            if(disabled(block)) state.rules.bannedBlocks.add(block);
        }
        if(tutorial(state.rules.sector)) skipTutorial();
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
            Log.err("[Rogue] Could not hide hints", e);
        }
    }

    public static boolean disabled(Block block){
        return block.category == Category.logic;
    }

    public Seq<Mutators.Mutator> curseOffer(){
        return Mutators.offer(mutators, new Rand(id * 4421L + launches * 104729L));
    }

    public void curse(@Nullable Mutators.Mutator mutator){
        if(!pendingCurse) return;
        pendingCurse = false;
        if(mutator != null && !mutators.contains(mutator)){
            mutators.add(mutator);
            Log.info("[Rogue] Enemies got '@'", mutator.name);
        }
        save();
    }

    public Seq<Rewards.Reward> relicOffer(){
        return Rewards.rollRelics(rewards, mutators.size, new Rand(id * 7919L + launches * 104729L));
    }

    public Seq<Rewards.Reward> modifierOffer(){
        if(modOffer.isEmpty() || modOffer.contains(r -> stacks(r) > 0)){
            modOffer.set(Rewards.rollModifiers(rewards, new Rand(id * 6007L + picksTaken * 104729L)));
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
            pendingRelic = false;
        }else{
            if(pendingModifiers <= 0) return;
            pendingModifiers--;
            picksTaken++;
            clearOffer();
        }
        rewards.increment(reward);
        if(reward == Rewards.absolution){
            Rand rand = new Rand(id * 31L + launches);
            for(int i = 0; i < 2 && mutators.any(); i++){
                Mutators.Mutator lifted = mutators.remove(rand.random(mutators.size - 1));
                Mutators.lifted(lifted);
                Log.info("[Rogue] Curse lifted: '@'", lifted.name);
            }
        }
        applyTweaks();
        if(reward == Rewards.biggerCore){
            if(state.isGame()) for(CoreBuild core : state.rules.defaultTeam.cores()) core.onProximityUpdate();
        }
        save();
        Log.info("[Rogue] Took '@'", reward.name);
    }

    public void skipPicks(){
        pendingRelic = false;
        pendingModifiers = 0;
        clearOffer();
        save();
    }

    public int wavesCleared(){
        return state.enemies == 0 ? state.wave : Math.max(state.wave - 1, 0);
    }

    public int nextModifierWave(){
        return (sectorPicks.get(state.rules.sector.id, 0) + 1) * Rewards.wavesPerModifier;
    }

    void countPicks(){
        if(!state.rules.waves) return;
        int sector = state.rules.sector.id, earned = wavesCleared() / Rewards.wavesPerModifier, had = sectorPicks.get(sector, 0);
        if(earned <= had) return;
        pendingModifiers += earned - had;
        sectorPicks.put(sector, earned);
        save();
    }

    void update(){
        if(state.isPaused() || state.gameOver) return;

        updateShields();
        checkLastStand();

        if((unlockTimer += Time.delta) >= 120f){
            unlockTimer = 0f;
            control.checkAutoUnlocks();
        }

        if((effectTimer += Time.delta) < 30f) return;
        effectTimer = 0f;

        countPicks();

        if(stacks(Rewards.gracePeriod) > 0 && state.rules.waves && state.rules.waveTimer && state.wave <= 1 && !state.rules.tags.getBool(graceTag)){
            state.rules.tags.put(graceTag, "true");
            state.wavetime += 3f * Time.toMinutes;
        }

        for(var e : rewards) e.key.setStacks(e.value);
        for(Unit unit : state.rules.defaultTeam.data().units){
            for(var e : rewards){
                Rewards.Reward reward = e.key;
                if(reward.effect == null || unit.hasEffect(reward.effect)) continue;
                if(reward == Rewards.fieldCommander ? unit.spawnedByCore : reward.units.contains(unit.type)) unit.apply(reward.effect);
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

        Building core = state.rules.defaultTeam.core();
        float factories = stacks(Rewards.warFactory) > 0 ? 2f : 1f, mining = stacks(Rewards.oreMagnet) > 0 ? 3f : 1f;
        boolean link = stacks(Rewards.coreLink) > 0, heal = stacks(Rewards.livingMetal) > 0;

        for(Building build : state.rules.defaultTeam.data().buildings){
            float boost = blockSpeed.get(build.block, 1f) *
                (build instanceof UnitFactoryBuild || build instanceof ReconstructorBuild ? factories :
                build instanceof DrillBuild || build instanceof SolidPumpBuild ? mining :
                1f);
            if(boost > 1.001f) build.applyBoost(boost, 45f);

            float toughness = blockHealth.get(build.block, 1f);
            if(toughness > 1.001f) toughen(build, build.block.health * toughness);

            if(heal && build.damaged()) build.heal(build.maxHealth * 0.01f);

            if(link && core != null && build instanceof DrillBuild drill && drill.items.total() > 0){
                drill.items.each((item, amount) -> core.items.add(item, Math.min(amount, core.getMaximumAccepted(item) - core.items.get(item))));
                drill.items.clear();
            }
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
            //removed instead of killed, so death curses like Self Destruction don't hit the core
            for(Unit unit : doomed){
                Fx.massiveExplosion.at(unit.x, unit.y);
                unit.remove();
            }
            Fx.spawnShockwave.at(core.x, core.y, radius);
            Sounds.explosionTitan.at(core);
            if(!headless) ui.announce("[#" + Rewards.Rarity.relic.color + "]Last Stand!");
            Log.info("[Rogue] Last Stand removed @ enemies", doomed.size);
            return;
        }
    }

    void prefab(Tile tile, Unit unit){
        if(!(tile.build instanceof ConstructBuild build) || build.current == null) return;
        Block block = build.current;
        int used = state.rules.tags.getInt(prefabTag, 0);
        if(used >= Rewards.prefabCount || block instanceof Conveyor || block instanceof StackConveyor || block instanceof Duct) return;
        CoreBuild core = unit.closestCore();
        float cost = state.rules.buildCostMultiplier;
        if(core == null || !core.items.has(block.requirements, cost)) return;

        Object config = null;
        for(BuildPlan plan : unit.plans()){
            if(plan.x == tile.x && plan.y == tile.y && plan.block == block) config = plan.config;
        }
        for(ItemStack stack : block.requirements) core.items.remove(stack.item, Math.round(stack.amount * cost));
        state.rules.tags.put(prefabTag, String.valueOf(used + 1));
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
            Fx.spawn.at(type.spawn(team, core.x, core.y));
        });
    }

    void onEnemyDeath(Unit unit){
        if(unit.type.internal || unit instanceof TimedKillc || stacks(Rewards.plunder) <= 0) return;
        CoreBuild core = state.teams.closestCore(unit.x, unit.y, state.rules.defaultTeam);
        if(core == null) return;
        for(ItemStack stack : unit.type.getTotalRequirements()){
            int amount = Math.round(stack.amount * Rewards.plunderShare);
            core.items.add(stack.item, Math.max(0, Math.min(amount, core.getMaximumAccepted(stack.item) - core.items.get(stack.item))));
        }
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

    void updateShields(){
        int n = stacks(Rewards.hardenedShields);
        if(n <= 0) return;
        float keep = n / (1f + n);

        if((cleanupTimer += Time.delta) > 600f){
            cleanupTimer = 0f;
            lastShield.clear();
            lastBuildup.clear();
        }

        for(Unit unit : state.rules.defaultTeam.data().units){
            float last = lastShield.get(unit.id, unit.shield);
            if(unit.shield < last) unit.shield += (last - unit.shield) * keep;
            lastShield.put(unit.id, unit.shield);
        }
        for(Building build : state.rules.defaultTeam.data().buildings){
            if(build instanceof ForceBuild force){
                float last = lastBuildup.get(force.id, force.buildup);
                if(force.buildup > last) force.buildup -= (force.buildup - last) * keep;
                lastBuildup.put(force.id, force.buildup);
            }
        }
    }

    public void save(){
        StringBuilder out = new StringBuilder();
        out.append("id=").append(id).append('\n');
        out.append("launches=").append(launches).append('\n');
        out.append("curse=").append(pendingCurse).append('\n');
        out.append("relic=").append(pendingRelic).append('\n');
        out.append("modpicks=").append(pendingModifiers).append('\n');
        out.append("taken=").append(picksTaken).append('\n');
        out.append("modoffer=").append(modOffer.map(r -> r.id).toString(",")).append('\n');
        out.append("rerolled=");
        for(boolean b : rerolled) out.append(b ? '1' : '0');
        out.append('\n');
        out.append("mutators=").append(mutators.map(m -> m.id).toString(",")).append('\n');
        out.append("sectorpicks=");
        for(var e : sectorPicks) out.append(e.key).append(':').append(e.value).append(',');
        out.append('\n');
        out.append("rewards=");
        for(var e : rewards) out.append(e.key.id).append(':').append(e.value).append(',');
        out.append('\n');
        Core.settings.put(saveKey, out.toString());
    }

    public static @Nullable RogueRun load(){
        String text = Core.settings.getString(saveKey, null);
        if(text == null) return null;
        try{
            RogueRun run = new RogueRun();
            for(String line : text.split("\n")){
                int eq = line.indexOf('=');
                if(eq < 0) continue;
                String k = line.substring(0, eq), v = line.substring(eq + 1);
                switch(k){
                    case "id" -> run.id = Long.parseLong(v);
                    case "launches" -> run.launches = Integer.parseInt(v);
                    case "curse" -> run.pendingCurse = Boolean.parseBoolean(v);
                    case "relic" -> run.pendingRelic = Boolean.parseBoolean(v);
                    case "modpicks" -> run.pendingModifiers = Integer.parseInt(v);
                    case "taken" -> run.picksTaken = Integer.parseInt(v);
                    case "modoffer" -> {
                        for(String s : list(v)){
                            Rewards.Reward r = Rewards.get(s);
                            if(r != null) run.modOffer.add(r);
                        }
                    }
                    case "rerolled" -> {
                        for(int i = 0; i < Math.min(v.length(), run.rerolled.length); i++) run.rerolled[i] = v.charAt(i) == '1';
                    }
                    case "mutators" -> {
                        for(String s : list(v)){
                            Mutators.Mutator m = Mutators.get(s);
                            if(m != null) run.mutators.add(m);
                        }
                    }
                    case "sectorpicks" -> {
                        for(String s : list(v)){
                            String[] parts = s.split(":");
                            run.sectorPicks.put(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                        }
                    }
                    case "rewards" -> {
                        for(String s : list(v)){
                            String[] parts = s.split(":");
                            Rewards.Reward reward = Rewards.get(parts[0]);
                            if(reward != null) run.rewards.put(reward, Integer.parseInt(parts[1]));
                        }
                    }
                }
            }
            return run;
        }catch(Throwable e){
            Log.err("[Rogue] Could not read the saved run", e);
            return null;
        }
    }

    static Seq<String> list(String v){
        return Seq.with(v.split(",")).select(s -> !s.isEmpty());
    }

    static void registerEvents(){
        Events.on(ClientLoadEvent.class, e -> {
            //a run's campaign is still live if the game was closed during a run
            if(Profile.inRun()) leave();
            hideHints();
        });

        Events.on(SectorLaunchEvent.class, e -> {
            if(current != null && Profile.inRun()) current.onLaunch();
        });

        Events.on(SectorCaptureEvent.class, e -> {
            if(current != null && Profile.inRun()) lockSectors();
        });

        Events.on(WorldLoadEvent.class, e -> Core.app.post(() -> {
            if(active()) current.onLoaded();
        }));

        Events.on(UnitCreateEvent.class, e -> {
            if(active() && e.unit.team == state.rules.defaultTeam && current.stacks(Rewards.ironLegion) > 0 && Rewards.ironLegionType(e.unit.type)){
                e.unit.shield += e.unit.maxHealth * current.stacks(Rewards.ironLegion);
            }
        });

        Events.on(BlockBuildBeginEvent.class, e -> {
            if(e.breaking || e.unit == null || !e.unit.isPlayer() || e.team != state.rules.defaultTeam) return;
            if(active() && current.stacks(Rewards.prefab) > 0) Core.app.post(() -> {
                if(active()) current.prefab(e.tile, e.unit);
            });
        });

        Events.on(UnitDestroyEvent.class, e -> {
            if(!active()) return;
            if(e.unit.team == state.rules.defaultTeam && current.stacks(Rewards.recycle) > 0) current.recycle(e.unit);
            if(e.unit.team == state.rules.waveTeam) current.onEnemyDeath(e.unit);
        });

        Events.on(BlockDestroyEvent.class, e -> {
            if(active() && current.stacks(Rewards.scrapRebirth) > 0) current.scrap(e.tile.build);
        });

        Events.run(Trigger.afterGameUpdate, () -> {
            if(active()) current.update();
        });
    }
}

package roguedustry;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.maps.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.ForceProjector.*;
import mindustry.world.blocks.production.Drill.*;
import mindustry.world.blocks.units.*;
import mindustry.world.blocks.units.Reconstructor.*;
import mindustry.world.blocks.units.UnitFactory.*;

import static mindustry.Vars.*;

public class RogueRun{
    public static final String tag = "roguedustry", saveKey = "roguedustry-run", bestKey = "roguedustry-best";

    public static @Nullable RogueRun current;
    static boolean starting;

    public long id;
    public int missionsCleared, endless;
    public final ObjectSet<UnlockableContent> unlocked = new ObjectSet<>();
    public final ObjectSet<SectorPreset> cleared = new ObjectSet<>();
    public final ObjectIntMap<Rewards.Reward> rewards = new ObjectIntMap<>();
    public final Seq<Mutators.Mutator> mutators = new Seq<>();
    public @Nullable String mission, slot;
    public boolean over, pendingRelic;
    public int pendingModifiers, mapPicks, enemyStart, picksTaken;

    public int techVersion;
    float effectTimer, warnTimer, cleanupTimer;
    final IntFloatMap lastShield = new IntFloatMap(), lastBuildup = new IntFloatMap();

    public static RogueRun create(){
        RogueRun run = new RogueRun();
        run.id = Math.abs(new Rand().nextLong());
        run.unlocked.addAll(Tech.starting());
        Tech.autoUnlock(run);
        run.save();
        return run;
    }

    public static boolean active(){
        return current != null && state.isGame() && !current.over && String.valueOf(current.id).equals(state.rules.tags.get(tag));
    }

    public int stacks(Rewards.Reward reward){
        return rewards.get(reward, 0);
    }

    public @Nullable SectorPreset missionPreset(){
        return mission == null ? null : Mission.of(mission).preset;
    }

    public int terror(){
        return Mutators.terror(missionsCleared);
    }

    public Seq<Mutators.Mutator> mutatorsFor(Mission m){
        return Mutators.roll(terror(), id * 31L + m.key().hashCode() * 7919L + missionsCleared);
    }

    public Seq<Mission> missionChoices(){
        Seq<Mission> out = Tech.playableSectors(this).map(Mission::new);
        if(Tech.campaignBeaten(this)){
            Rand rand = new Rand(id + missionsCleared * 31L);
            for(int i = 0; i < 2; i++){
                out.add(new Mission(rand.nextInt(Integer.MAX_VALUE), endless));
            }
        }
        return out;
    }

    public void startMission(Mission m){
        mission = m.key();
        mutators.set(mutatorsFor(m));
        pendingRelic = Rewards.rollRelics(rewards, new Rand()).any();
        pendingModifiers = mapPicks = 0;
        enemyStart = 0;
        slot = null;
        save();

        Rules rules = m.rules();
        rules.tags.put(tag, String.valueOf(id));
        syncWhitelist(rules);

        starting = true;
        control.playMap(m.map(), rules);
    }

    void onMissionStarted(){
        Mission m = Mission.of(mission);
        //playMap() swaps in the wave list of the previous world, so generated maps set theirs again here
        if(m.preset == null) state.rules.spawns = m.rules().spawns;
        enemyStart = state.rules.waveTeam.data().buildings.size;
        if(!headless){
            //the game's "save on start" option makes a copy of the map's start, which would let a lost map be replayed
            if(control.saves.getCurrent() != null) control.saves.getCurrent().delete();
            slot = control.saves.addSave("Roguedustry - " + m.name()).file.name();
        }
        Log.info("[Rogue] Mission '@' started. Terror @, mutators: @", mission, terror(), mutators.map(x -> x.name));
        onMissionLoaded();
    }

    void onMissionLoaded(){
        markProduced();
        int count = Tech.autoUnlock(this);
        save();
        techVersion++;
        syncWhitelist(state.rules);
        if(count > 0 && !headless) ui.showInfoToast(count + " new tech tree nodes unlocked", 4f);
    }

    public @Nullable Saves.SaveSlot saveSlot(){
        return slot == null ? null : control.saves.getSaveSlots().find(s -> s.file.name().equals(slot));
    }

    void deleteSlot(){
        Saves.SaveSlot save = saveSlot();
        if(save != null) save.delete();
        slot = null;
    }

    void onGameOver(Team winner){
        if(over) return;
        boolean won = winner == state.rules.defaultTeam;
        deleteSlot();
        if(won){
            Mission m = Mission.of(mission);
            if(m.preset != null) cleared.add(m.preset);
            else endless++;
            missionsCleared++;
            mission = null;
            mutators.clear();
            pendingRelic = false;
            pendingModifiers = 0;
            Tech.autoUnlock(this);
            save();
            Log.info("[Rogue] Mission cleared. @ cleared so far", missionsCleared);
        }else{
            lose();
        }
        techVersion++;
    }

    public void lose(){
        over = true;
        deleteSlot();
        Core.settings.put(bestKey, Math.max(best(), missionsCleared));
        Core.settings.remove(saveKey);
        Log.info("[Rogue] Run over after @ missions", missionsCleared);
    }

    public Seq<Rewards.Reward> relicOffer(){
        return Rewards.rollRelics(rewards, new Rand(id * 7919L + missionsCleared * 104729L));
    }

    public Seq<Rewards.Reward> modifierOffer(){
        return Rewards.rollModifiers(rewards, new Rand(id * 6007L + picksTaken * 104729L));
    }

    public void take(Rewards.Reward reward){
        if(reward.isRelic()){
            if(!pendingRelic) return;
            pendingRelic = false;
        }else{
            if(pendingModifiers <= 0) return;
            pendingModifiers--;
            picksTaken++;
        }
        rewards.increment(reward);
        save();
        Log.info("[Rogue] Took '@'", reward.name);
    }

    public void skipPicks(){
        pendingRelic = false;
        pendingModifiers = 0;
        save();
    }

    void syncWhitelist(Rules rules){
        rules.blockWhitelist = true;
        rules.hideBannedBlocks = true;
        rules.bannedBlocks.clear();
        for(Block block : content.blocks()){
            if(block.techNode != null && unlocked.contains(block)) rules.bannedBlocks.add(block);
        }
        if(!headless && state.isGame()) ui.hudfrag.blockfrag.rebuild();
    }

    void markProduced(){
        boolean any = false;
        for(Building build : state.rules.defaultTeam.data().buildings){
            if(build.items != null){
                for(Item item : content.items()){
                    if(build.items.has(item) && unlocked.add(item)) any = true;
                }
            }
            if(build.liquids != null && build.liquids.currentAmount() > 0.01f && unlocked.add(build.liquids.current())) any = true;
        }
        if(any){
            int count = Tech.autoUnlock(this);
            if(count > 0 && !headless) ui.showInfoToast(count + " new tech tree nodes unlocked", 4f);
            save();
            techVersion++;
            syncWhitelist(state.rules);
        }
    }

    public float missionProgress(){
        if(state.rules.attackMode){
            return enemyStart <= 0 ? 0f : 1f - state.rules.waveTeam.data().buildings.size / (float)enemyStart;
        }
        return state.rules.winWave <= 0 ? 0f : state.wave / (float)state.rules.winWave;
    }

    void update(){
        if(state.isPaused() || state.gameOver) return;

        updateShields();

        if((effectTimer += Time.delta) < 30f) return;
        effectTimer = 0f;
        warnTimer -= 30f;

        markProduced();

        float progress = missionProgress();
        while(mapPicks < Rewards.modifiersPerMap && progress >= (mapPicks + 1f) / (Rewards.modifiersPerMap + 1f)){
            mapPicks++;
            pendingModifiers++;
            save();
        }

        Building core = state.rules.defaultTeam.core();
        float all = stacks(Rewards.overclock) > 0 ? 1.5f : 1f;
        float duos = 1f + 0.5f * stacks(Rewards.duoDrills);
        boolean link = stacks(Rewards.coreLink) > 0, heal = stacks(Rewards.livingMetal) > 0;

        for(Building build : state.rules.defaultTeam.data().buildings){
            float boost = build.block == Blocks.duo ? all * duos : all;
            if(boost > 1.001f) build.applyBoost(boost, 45f);

            if(heal && build.damaged()) build.heal(build.maxHealth * 0.01f);

            if(link && core != null && build instanceof DrillBuild drill && drill.items.total() > 0){
                drill.items.each((item, amount) -> core.items.add(item, Math.min(amount, core.getMaximumAccepted(item) - core.items.get(item))));
                drill.items.clear();
            }

            UnitType making = build instanceof UnitFactoryBuild f && f.currentPlan >= 0 ? ((UnitFactory)f.block).plans.get(f.currentPlan).unit :
                build instanceof ReconstructorBuild r && r.constructing() ? r.upgrade(r.payload.unit.type) : null;
            if(making != null && !Tech.usable(this, making)){
                ((UnitBlock.UnitBuild)build).progress = 0f;
                if(warnTimer <= 0f && !headless){
                    warnTimer = 60f * 10f;
                    ui.showInfoToast(making.localizedName + " isn't researched yet", 3f);
                }
            }
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

    public static int best(){
        return Core.settings.getInt(bestKey, 0);
    }

    public void save(){
        StringBuilder out = new StringBuilder();
        out.append("id=").append(id).append('\n');
        out.append("missions=").append(missionsCleared).append('\n');
        out.append("endless=").append(endless).append('\n');
        out.append("relic=").append(pendingRelic).append('\n');
        out.append("modpicks=").append(pendingModifiers).append('\n');
        out.append("mappicks=").append(mapPicks).append('\n');
        out.append("taken=").append(picksTaken).append('\n');
        out.append("enemystart=").append(enemyStart).append('\n');
        if(mission != null) out.append("mission=").append(mission).append('\n');
        if(slot != null) out.append("slot=").append(slot).append('\n');
        out.append("mutators=").append(mutators.map(m -> m.id).toString(",")).append('\n');
        out.append("unlocked=").append(unlocked.toSeq().map(RogueRun::key).toString(",")).append('\n');
        out.append("cleared=").append(cleared.toSeq().map(c -> c.name).toString(",")).append('\n');
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
                    case "missions" -> run.missionsCleared = Integer.parseInt(v);
                    case "endless" -> run.endless = Integer.parseInt(v);
                    case "relic" -> run.pendingRelic = Boolean.parseBoolean(v);
                    case "modpicks" -> run.pendingModifiers = Integer.parseInt(v);
                    case "mappicks" -> run.mapPicks = Integer.parseInt(v);
                    case "taken" -> run.picksTaken = Integer.parseInt(v);
                    case "enemystart" -> run.enemyStart = Integer.parseInt(v);
                    case "mission" -> run.mission = v;
                    case "slot" -> run.slot = v;
                    case "mutators" -> {
                        for(String s : list(v)){
                            Mutators.Mutator m = Mutators.get(s);
                            if(m != null) run.mutators.add(m);
                        }
                    }
                    case "unlocked" -> {
                        for(String s : list(v)){
                            UnlockableContent c = fromKey(s);
                            if(c != null) run.unlocked.add(c);
                        }
                    }
                    case "cleared" -> {
                        for(String s : list(v)){
                            SectorPreset p = content.sector(s);
                            if(p != null) run.cleared.add(p);
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

    static String key(UnlockableContent c){
        return c.getContentType().name() + "/" + c.name;
    }

    static @Nullable UnlockableContent fromKey(String key){
        String[] parts = key.split("/", 2);
        if(parts.length < 2) return null;
        try{
            return content.getByName(ContentType.valueOf(parts[0]), parts[1]) instanceof UnlockableContent u ? u : null;
        }catch(IllegalArgumentException e){
            return null;
        }
    }

    static void registerEvents(){
        Events.run(Trigger.newGame, () -> {
            if(!starting || current == null || !state.rules.tags.containsKey(tag)) return;
            starting = false;
            current.onMissionStarted();
        });

        //playMap() sets the new rules only after this event, so fresh missions are handled on newGame instead
        Events.on(WorldLoadEvent.class, e -> {
            if(starting || !state.rules.tags.containsKey(tag)) return;
            RogueRun saved = load();
            String runId = state.rules.tags.get(tag);
            current = null;
            //the loaded slot is only known after this event, so the check waits a frame
            Core.app.post(() -> {
                Saves.SaveSlot loaded = control.saves.getCurrent();
                if(saved != null && String.valueOf(saved.id).equals(runId) && saved.mission != null && loaded != null && loaded.file.name().equals(saved.slot)){
                    current = saved;
                    current.onMissionLoaded();
                }else{
                    Log.info("[Rogue] This save can't continue a run");
                    if(!headless) ui.showInfoToast("Only a run's own save can continue it. This is a normal game.", 5f);
                }
            });
        });

        Events.on(GameOverEvent.class, e -> {
            if(active()) current.onGameOver(e.winner);
        });

        Events.on(UnitCreateEvent.class, e -> {
            if(active() && e.unit.team == state.rules.defaultTeam && current.stacks(Rewards.ironLegion) > 0 && Rewards.ironLegionType(e.unit.type)){
                e.unit.shield += e.unit.maxHealth;
            }
        });

        Events.run(Trigger.afterGameUpdate, () -> {
            if(active()) current.update();
        });
    }
}

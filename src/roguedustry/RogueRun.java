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
import mindustry.world.blocks.defense.turrets.BaseTurret.*;
import mindustry.world.blocks.production.Drill.*;
import mindustry.world.blocks.production.GenericCrafter.*;
import mindustry.world.blocks.units.*;
import mindustry.world.blocks.units.Reconstructor.*;
import mindustry.world.blocks.units.UnitFactory.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class RogueRun{
    public static final String tag = "roguedustry", saveKey = "roguedustry-run", bestKey = "roguedustry-best";

    public static @Nullable RogueRun current;
    static boolean starting;

    public long id;
    public int missionsCleared, endless, pendingPicks;
    public final ObjectSet<UnlockableContent> unlocked = new ObjectSet<>();
    public final ObjectSet<SectorPreset> cleared = new ObjectSet<>();
    public final ObjectMap<UnlockableContent, ItemSeq> progress = new ObjectMap<>();
    public final ItemSeq bank = new ItemSeq();
    public final ObjectIntMap<Modifiers.Modifier> modifiers = new ObjectIntMap<>();
    public @Nullable String mission;
    public boolean over;

    public int techVersion;
    float effectTimer, warnTimer;

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

    public int stacks(Modifiers.Modifier mod){
        return modifiers.get(mod, 0);
    }

    public @Nullable SectorPreset missionPreset(){
        return mission == null ? null : Mission.of(mission).preset;
    }

    public int available(Item item){
        Building core = active() ? state.rules.defaultTeam.core() : null;
        return bank.get(item) + (core == null ? 0 : core.items.get(item));
    }

    public void spend(Item item, int amount){
        int fromBank = Math.min(amount, bank.get(item));
        bank.remove(item, fromBank);
        amount -= fromBank;
        Building core = state.rules.defaultTeam.core();
        if(amount > 0 && active() && core != null) core.items.remove(item, amount);
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
        save();

        Rules rules = m.rules();
        rules.tags.put(tag, String.valueOf(id));
        Modifiers.apply(modifiers, rules);
        syncWhitelist(rules);

        starting = true;
        control.playMap(m.map(), rules);
    }

    void onMissionLoaded(){
        Log.info("[Rogue] Mission '@' started", mission);
        Mission m = Mission.of(mission);
        //playMap() swaps in the wave list of the previous world, so generated maps set theirs again here
        if(m.preset == null) state.rules.spawns = m.rules().spawns;
        markProduced();
        if(Tech.autoUnlock(this)) save();
        syncWhitelist(state.rules);
    }

    void onGameOver(Team winner){
        if(over) return;
        boolean won = winner == state.rules.defaultTeam;
        if(won){
            Mission m = Mission.of(mission);
            if(m.preset != null) cleared.add(m.preset);
            else endless++;
            missionsCleared++;
            pendingPicks++;
            Building core = state.rules.defaultTeam.core();
            if(core != null) core.items.each(bank::add);
            mission = null;
            Tech.autoUnlock(this);
            save();
            Log.info("[Rogue] Mission cleared. @ cleared so far", missionsCleared);
        }else{
            over = true;
            Core.settings.put(bestKey, Math.max(best(), missionsCleared));
            Core.settings.remove(saveKey);
            Log.info("[Rogue] Run over after @ missions", missionsCleared);
        }
        techVersion++;
    }

    public void pickModifier(Modifiers.Modifier mod){
        if(pendingPicks <= 0) return;
        modifiers.increment(mod);
        pendingPicks--;
        save();
        Log.info("[Rogue] Picked modifier '@'", mod.name);
    }

    public Seq<Modifiers.Modifier> modifierOffer(){
        return Modifiers.roll(modifiers, new Rand(id * 7919L + missionsCleared * 104729L + pendingPicks));
    }

    public boolean research(TechTree.TechNode node){
        boolean done = Tech.contribute(this, node);
        save();
        techVersion++;
        if(active()) syncWhitelist(state.rules);
        return done;
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
            Tech.autoUnlock(this);
            save();
            techVersion++;
            syncWhitelist(state.rules);
        }
    }

    void update(){
        if(state.isPaused() || state.gameOver) return;

        if((effectTimer += Time.delta) < 30f) return;
        effectTimer = 0f;
        warnTimer -= 30f;

        markProduced();

        float drills = (1f + 0.3f * stacks(Modifiers.deepDrills)) * (stacks(Modifiers.greed) > 0 ? 1.6f : 1f);
        float crafters = 1f + 0.25f * stacks(Modifiers.overclock);
        float belts = 1f + 0.5f * stacks(Modifiers.expressBelts);
        float turrets = 1f + 0.15f * stacks(Modifiers.rapidFire);
        float all = stacks(Modifiers.overtime) > 0 ? 1.2f : 1f;

        for(Building build : state.rules.defaultTeam.data().buildings){
            float boost = all;
            if(build instanceof DrillBuild) boost *= drills;
            else if(build instanceof GenericCrafterBuild) boost *= crafters;
            else if(build instanceof BaseTurretBuild) boost *= turrets;
            else if(build.block.group == BlockGroup.transportation) boost *= belts;
            if(boost > 1.001f) build.applyBoost(boost, 45f);

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

    public static int best(){
        return Core.settings.getInt(bestKey, 0);
    }

    public void save(){
        StringBuilder out = new StringBuilder();
        out.append("id=").append(id).append('\n');
        out.append("missions=").append(missionsCleared).append('\n');
        out.append("endless=").append(endless).append('\n');
        out.append("picks=").append(pendingPicks).append('\n');
        if(mission != null) out.append("mission=").append(mission).append('\n');
        out.append("unlocked=").append(unlocked.toSeq().map(RogueRun::key).toString(",")).append('\n');
        out.append("cleared=").append(cleared.toSeq().map(c -> c.name).toString(",")).append('\n');
        out.append("bank=").append(items(bank)).append('\n');
        out.append("modifiers=");
        for(var e : modifiers) out.append(e.key.id).append(':').append(e.value).append(',');
        out.append('\n');
        for(var e : progress) out.append("progress.").append(key(e.key)).append('=').append(items(e.value)).append('\n');
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
                    case "picks" -> run.pendingPicks = Integer.parseInt(v);
                    case "mission" -> run.mission = v;
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
                    case "bank" -> readItems(v, run.bank);
                    case "modifiers" -> {
                        for(String s : list(v)){
                            String[] parts = s.split(":");
                            Modifiers.Modifier mod = Modifiers.get(parts[0]);
                            if(mod != null) run.modifiers.put(mod, Integer.parseInt(parts[1]));
                        }
                    }
                    default -> {
                        if(k.startsWith("progress.")){
                            UnlockableContent c = fromKey(k.substring("progress.".length()));
                            if(c != null) readItems(v, run.progress.get(c, ItemSeq::new));
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

    static String items(ItemSeq seq){
        StringBuilder out = new StringBuilder();
        seq.each((item, amount) -> out.append(item.name).append(':').append(amount).append(','));
        return out.toString();
    }

    static void readItems(String v, ItemSeq out){
        for(String s : list(v)){
            String[] parts = s.split(":");
            Item item = content.item(parts[0]);
            if(item != null) out.add(item, Integer.parseInt(parts[1]));
        }
    }

    static void registerEvents(){
        Events.run(Trigger.newGame, () -> {
            if(!starting || current == null || !state.rules.tags.containsKey(tag)) return;
            starting = false;
            current.onMissionLoaded();
        });

        //playMap() sets the new rules only after this event, so fresh missions are handled on newGame instead
        Events.on(WorldLoadEvent.class, e -> {
            if(starting || !state.rules.tags.containsKey(tag)) return;
            RogueRun saved = load();
            if(saved != null && String.valueOf(saved.id).equals(state.rules.tags.get(tag)) && saved.mission != null){
                current = saved;
                Core.app.post(current::onMissionLoaded);
            }else{
                Log.info("[Rogue] This save belongs to a run that has ended");
            }
        });

        Events.on(GameOverEvent.class, e -> {
            if(active()) current.onGameOver(e.winner);
        });

        Events.on(UnitDestroyEvent.class, e -> {
            if(!active() || e.unit.team != state.rules.waveTeam) return;
            int bounty = current.stacks(Modifiers.bounty);
            Building core = state.rules.defaultTeam.core();
            if(bounty > 0 && core != null){
                int amount = Math.min((int)(e.unit.maxHealth / 20f * bounty), core.getMaximumAccepted(Items.copper) - core.items.get(Items.copper));
                if(amount > 0) core.items.add(Items.copper, amount);
            }
        });

        Events.run(Trigger.update, () -> {
            if(active()) current.update();
        });
    }
}

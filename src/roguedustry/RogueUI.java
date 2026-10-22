package roguedustry;

import arc.*;
import arc.graphics.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.TechTree.*;
import mindustry.core.GameState.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;

public class RogueUI{
    public @Nullable BaseDialog menuDialog, missionDialog, researchDialog, pickDialog, overDialog;
    public Seq<Modifiers.Modifier> offered = new Seq<>();

    public void init(){
        ui.menufrag.addButton("Roguedustry", Icon.play, this::showMenu);
        buildHud();

        //the game's own game-over window opens 3 seconds after the end; ours replaces it during a run
        ui.restart.shown(() -> Core.app.post(() -> {
            RogueRun run = RogueRun.current;
            if(run == null || !String.valueOf(run.id).equals(state.rules.tags.get(RogueRun.tag))) return;
            ui.restart.hide(null);
            if(run.over) showRunOver();
            else if(run.pendingPicks > 0) showPick();
            else showMissions();
        }));
    }

    static RogueRun run(){
        return RogueRun.current;
    }

    public void showMenu(){
        if(run() == null || run().over) RogueRun.current = RogueRun.load();

        menuDialog = new BaseDialog("Roguedustry");
        Table c = menuDialog.cont;
        c.defaults().pad(4f);

        if(run() == null){
            c.add("Play through the campaign maps in order, starting with a locked tech tree.").row();
            c.add("Research with the items you collect. Each cleared map lets you pick a permanent modifier.").row();
            c.add("Lose a map and the run is over. After the last campaign map, the run goes on with random maps.").color(Color.lightGray).row();
            c.add("Your normal campaign is not affected.").color(Color.lightGray).padBottom(10f).row();
            if(RogueRun.best() > 0) c.add("Best run: " + RogueRun.best() + " maps cleared").color(Pal.accent).row();
            c.button("Start a new run", Icon.play, () -> {
                RogueRun.current = RogueRun.create();
                menuDialog.hide();
                showMissions();
            }).size(320f, 64f);
        }else{
            c.add("Run in progress: " + run().missionsCleared + " maps cleared").color(Pal.accent).row();
            c.add(modifierSummary()).color(Color.lightGray).wrap().width(600f).padBottom(10f).row();
            c.button("Continue", Icon.play, () -> {
                menuDialog.hide();
                if(run().pendingPicks > 0) showPick();
                else showMissions();
            }).size(320f, 64f).row();
            c.button("Abandon this run", Icon.trash, () -> ui.showConfirm("Abandon run", "This run will be lost. Are you sure?", () -> {
                Core.settings.remove(RogueRun.saveKey);
                RogueRun.current = null;
                menuDialog.hide();
                showMenu();
            })).size(320f, 50f);
        }

        menuDialog.addCloseButton();
        menuDialog.show();
    }

    String modifierSummary(){
        if(run().modifiers.isEmpty()) return "No modifiers yet.";
        StringBuilder out = new StringBuilder("Modifiers: ");
        boolean first = true;
        for(var e : run().modifiers){
            if(!first) out.append(", ");
            first = false;
            out.append(e.key.name);
            if(e.value > 1) out.append(" x").append(e.value);
        }
        return out.toString();
    }

    public void showMissions(){
        RogueRun run = run();
        missionDialog = new BaseDialog("Choose your next map");
        Table c = missionDialog.cont;

        c.add("Maps cleared: " + run.missionsCleared).color(Pal.accent).padBottom(8f).row();

        if(run.mission != null){
            Mission m = Mission.of(run.mission);
            c.add("You left " + m.name() + " before finishing it. Replay it from the start,").row();
            c.add("or load its save from Load Game to continue where you were.").color(Color.lightGray).padBottom(8f).row();
            c.button("Replay " + m.name(), Icon.play, () -> start(m)).size(360f, 64f).row();
        }else{
            c.pane(p -> {
                p.defaults().pad(6f);
                int i = 0;
                for(Mission m : run.missionChoices()){
                    p.table(Styles.black6, card -> {
                        card.margin(10f).left().defaults().left();
                        card.add(m.name()).color(Pal.accent).row();
                        card.add(m.goal()).row();
                        card.add("Threat " + Strings.autoFixed(m.threat(), 1)).color(Color.lightGray).row();
                        card.button("Play", Icon.play, () -> start(m)).size(180f, 50f).padTop(6f);
                    }).width(300f);
                    if(++i % 3 == 0) p.row();
                }
            }).maxHeight(360f).row();

            Seq<TechNode> locked = Tech.lockedSectors(run);
            if(locked.any()){
                c.add("Still locked").color(Color.lightGray).padTop(12f).row();
                c.pane(p -> {
                    p.left().defaults().left().pad(2f);
                    for(TechNode node : locked){
                        p.add("[lightgray]" + node.content.localizedName + ": []needs " + Tech.missingText(run, node)).wrap().width(760f).row();
                    }
                }).maxHeight(180f).width(780f).row();
            }
        }

        missionDialog.buttons.defaults().size(220f, 60f);
        missionDialog.buttons.button("Research", Icon.tree, this::showResearch);
        missionDialog.buttons.button("Main menu", Icon.exit, () -> {
            missionDialog.hide();
            if(state.isGame()) logic.reset();
        });
        missionDialog.show();
    }

    void start(Mission m){
        if(missionDialog != null) missionDialog.hide();
        run().startMission(m);
    }

    public void showResearch(){
        RogueRun run = run();
        researchDialog = new BaseDialog("Research");
        Table list = new Table();
        int[] version = {-1};
        list.update(() -> {
            if(run() == null || version[0] == run().techVersion) return;
            version[0] = run().techVersion;
            rebuildResearch(list);
        });

        researchDialog.cont.add("Items you saved from cleared maps are used first, then items in your core.").color(Color.lightGray).row();
        researchDialog.cont.pane(list).grow();
        researchDialog.addCloseButton();

        boolean pause = state.isGame() && !state.gameOver;
        researchDialog.shown(() -> {
            if(pause) state.set(State.paused);
        });
        researchDialog.hidden(() -> {
            if(pause && state.isGame() && !state.gameOver) state.set(State.playing);
        });
        researchDialog.show();
    }

    void rebuildResearch(Table table){
        RogueRun run = run();
        table.clear();
        table.top().left().defaults().left().pad(3f);

        Seq<TechNode> ready = Tech.researchable(run);
        ready.sort(n -> n.depth);
        table.add("Ready to research (" + ready.size + ")").color(Pal.accent).row();
        for(TechNode node : ready){
            table.table(Styles.black6, row -> {
                row.left().margin(6f);
                row.image(node.content.uiIcon).size(40f);
                row.add(node.content.localizedName).width(200f).padLeft(8f).left();
                row.table(costs -> {
                    costs.left();
                    for(ItemStack stack : Tech.cost(run, node)){
                        costs.image(stack.item.uiIcon).size(24f).padLeft(8f);
                        costs.label(() -> {
                            int paid = Tech.paid(run, node, stack.item);
                            boolean enough = paid + run.available(stack.item) >= stack.amount;
                            return (paid >= stack.amount ? "[accent]" : enough ? "" : "[scarlet]") + paid + "/" + stack.amount;
                        }).padLeft(3f);
                    }
                }).width(440f).left();
                row.button("Research", () -> run.research(node)).size(130f, 44f).disabled(b -> !Tech.canContribute(run, node));
            }).growX().row();
        }

        Seq<TechNode> locked = Tech.lockedNext(run);
        if(locked.any()){
            table.add("Needs something else first").color(Color.lightGray).padTop(12f).row();
            for(TechNode node : locked){
                table.table(row -> {
                    row.left();
                    row.image(node.content.uiIcon).size(32f).color(Color.gray);
                    row.add("[lightgray]" + node.content.localizedName + ": needs " + Tech.missingText(run, node)).wrap().width(700f).padLeft(8f);
                }).growX().row();
            }
        }
    }

    public void showPick(){
        RogueRun run = run();
        offered = run.modifierOffer();
        pickDialog = new BaseDialog("Map cleared!");
        pickDialog.cont.add("Pick a modifier. It lasts for the rest of the run.").color(Pal.accent).padBottom(12f).row();
        pickDialog.cont.table(row -> {
            for(int i = 0; i < offered.size; i++){
                Modifiers.Modifier mod = offered.get(i);
                int index = i;
                row.button(card -> {
                    card.top().margin(12f);
                    card.add(mod.name).color(mod.risky ? Pal.remove : Pal.accent).padBottom(6f).row();
                    if(run.stacks(mod) > 0) card.add("You have " + run.stacks(mod) + " of " + mod.max).color(Color.lightGray).padBottom(6f).row();
                    card.add(mod.description).wrap().width(220f).row();
                }, Styles.defaultb, () -> choose(index)).size(260f, 220f).pad(8f);
            }
        });
        if(offered.isEmpty()){
            pickDialog.cont.add("You have every modifier already.").row();
            pickDialog.buttons.button("Continue", () -> {
                run.pendingPicks = 0;
                run.save();
                pickDialog.hide();
                showMissions();
            }).size(220f, 60f);
        }
        pickDialog.show();
    }

    public void choose(int index){
        if(pickDialog == null || index >= offered.size) return;
        Modifiers.Modifier mod = offered.get(index);
        //a second click during the close animation must not pick again
        offered = new Seq<>();
        run().pickModifier(mod);
        pickDialog.hide();
        if(run().pendingPicks > 0) showPick();
        else showMissions();
    }

    void showRunOver(){
        overDialog = new BaseDialog("Run over");
        overDialog.cont.add("Your core was destroyed.").row();
        overDialog.cont.add("Maps cleared: " + run().missionsCleared).color(Pal.accent).row();
        overDialog.cont.add("Best run: " + RogueRun.best()).color(Color.lightGray).row();
        overDialog.buttons.button("Main menu", Icon.exit, () -> {
            overDialog.hide();
            RogueRun.current = null;
            logic.reset();
        }).size(220f, 60f);
        overDialog.show();
    }

    void buildHud(){
        Table panel = new Table(Styles.black6);
        panel.name = "rogue-hud";
        panel.visible(() -> RogueRun.active() && ui.hudfrag.shown());
        panel.left().margin(6f);
        panel.defaults().pad(2f).left();

        panel.label(() -> {
            if(!RogueRun.active() || run().mission == null) return "";
            Mission m = Mission.of(run().mission);
            return "[accent]" + m.name() + "[]  " + m.goal();
        }).row();
        panel.label(() -> RogueRun.active() ? "[lightgray]Maps cleared: " + run().missionsCleared + "   Modifiers: " + run().modifiers.size : "").row();
        float[] timer = {0f};
        panel.button("Research", Icon.tree, Styles.cleart, this::showResearch).height(36f).growX()
            .update(b -> {
                if(!RogueRun.active() || (timer[0] += Time.delta) < 30f) return;
                timer[0] = 0f;
                int ready = Tech.researchable(run()).count(n -> Tech.canAfford(run(), n));
                b.setText(ready > 0 ? "Research (" + ready + " ready)" : "Research");
            });

        Table waves = ui.hudGroup.find("waves");
        if(waves != null){
            waves.row();
            waves.add(panel).growX().left().padTop(4f);
        }else{
            ui.hudGroup.fill(t -> t.top().left().marginTop(130f).add(panel).width(330f));
        }
    }
}

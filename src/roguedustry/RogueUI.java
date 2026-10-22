package roguedustry;

import arc.*;
import arc.graphics.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.core.GameState.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.maps.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;

public class RogueUI{
    public @Nullable BaseDialog levelDialog, shopDialog;
    public Seq<Packs.Pack> offered = new Seq<>();

    public void init(){
        ui.menufrag.addButton("Roguedustry", Icon.play, this::showMapPicker);
        buildHud();

        Events.run(Trigger.update, this::update);
    }

    public static Seq<Map> playableMaps(){
        return maps.all().select(m -> m.spawns > 0 && !m.tags.get("rules", "").contains("\"attackMode\":true"));
    }

    void showMapPicker(){
        BaseDialog dialog = new BaseDialog("Roguedustry");
        Seq<Map> list = playableMaps();

        dialog.cont.add("Survive " + Waves.schedule().size + " waves. Kills give XP. Each level up: pick 1 of 3 packs.").color(Pal.accent).row();
        dialog.cont.add("Drills mine copper straight into the core. Spend it on packs in the shop.").color(Color.lightGray).row();
        dialog.cont.button("New random map", Icon.refresh, () -> {
            dialog.hide();
            RogueRun.start(MapGen.create());
        }).size(300f, 60f).pad(8f).row();
        dialog.cont.add("Or play a game map:").color(Color.lightGray).padTop(8f).row();

        dialog.cont.pane(p -> {
            int i = 0;
            for(Map map : list){
                p.button(map.name(), () -> {
                    dialog.hide();
                    RogueRun.start(map);
                }).size(220f, 50f).pad(4f);
                if(++i % 4 == 0) p.row();
            }
        }).growX();

        dialog.addCloseButton();
        dialog.show();
    }

    void buildHud(){
        Table panel = new Table(Styles.black6);
        panel.name = "rogue-hud";
        panel.visible(() -> RogueRun.active() && ui.hudfrag.shown());
        panel.left().margin(6f);
        panel.defaults().pad(2f).left();

        panel.table(top -> {
            top.left();
            top.label(() -> RogueRun.active() ? "[accent]Level " + run().level : "").padRight(12f);
            top.check("Auto waves", v -> run().autoStart = v).update(cb -> {
                if(RogueRun.active()) cb.setChecked(run().autoStart);
            });
        }).growX().row();

        panel.add(new Bar(
            () -> RogueRun.active() ? "XP " + run().xp + " / " + run().xpToNext() : "",
            () -> Pal.accent,
            () -> RogueRun.active() ? run().xp / (float)run().xpToNext() : 0f
        )).height(18f).growX().row();

        Table inventory = new Table();
        int[] version = {-1};
        inventory.update(() -> {
            if(!RogueRun.active() || version[0] == run().inventoryVersion) return;
            version[0] = run().inventoryVersion;
            rebuildInventory(inventory);
        });
        panel.add(inventory).growX().row();

        panel.table(shop -> {
            shop.left();
            shop.image(Items.copper.uiIcon).size(24f);
            shop.label(() -> RogueRun.active() ? "" + run().copper() : "").padLeft(4f).minWidth(60f);
            shop.button("Shop", Icon.add, Styles.cleart, this::showShop).height(36f).growX().padLeft(8f);
        }).growX();

        Table waves = ui.hudGroup.find("waves");
        if(waves != null){
            waves.row();
            waves.add(panel).growX().left().padTop(4f);
        }else{
            ui.hudGroup.fill(t -> t.top().left().marginTop(130f).add(panel).width(330f));
        }
    }

    void rebuildInventory(Table table){
        table.clear();
        int i = 0;
        for(var e : run().charges){
            if(e.value <= 0) continue;
            table.image(e.key.uiIcon).size(28f).padLeft(6f).tooltip(e.key.localizedName);
            table.add("x" + e.value).padLeft(2f);
            if(++i % 5 == 0) table.row();
        }
        if(i == 0) table.add("[lightgray]No charges - level up for more").pad(4f);
    }

    void update(){
        if(!RogueRun.active() || state.gameOver) return;

        if(run().pendingLevelUps > 0 && (levelDialog == null || !levelDialog.isShown())){
            showLevelUp();
        }
    }

    void showLevelUp(){
        RogueRun run = run();
        offered = Packs.roll(run.level - run.pendingLevelUps + 1, run.rand);

        levelDialog = new BaseDialog("Level up!");
        levelDialog.cont.add("Choose one pack").color(Pal.accent).padBottom(12f).row();
        levelDialog.cont.table(row -> {
            for(int i = 0; i < offered.size; i++){
                Packs.Pack pack = offered.get(i);
                int index = i;
                row.button(card -> {
                    card.top().margin(12f);
                    packCard(card, pack);
                }, Styles.defaultb, () -> choose(index)).size(260f, 300f).pad(8f);
            }
        });

        levelDialog.shown(() -> state.set(State.paused));
        levelDialog.hidden(() -> {
            if(state.isGame() && !state.gameOver && (shopDialog == null || !shopDialog.isShown())) state.set(State.playing);
        });
        levelDialog.show();
    }

    public void showShop(){
        if(!RogueRun.active() || (shopDialog != null && shopDialog.isShown())) return;

        shopDialog = new BaseDialog("Shop");
        Table cards = new Table();
        int[] version = {-1};
        cards.update(() -> {
            if(!RogueRun.active() || version[0] == run().shopVersion) return;
            version[0] = run().shopVersion;
            rebuildShop(cards);
        });

        shopDialog.cont.table(top -> {
            top.image(Items.copper.uiIcon).size(32f);
            top.label(() -> RogueRun.active() ? run().copper() + " copper" : "").padLeft(6f);
        }).padBottom(6f).row();
        shopDialog.cont.add("New stock every wave. Stronger packs unlock as you level up.").color(Color.lightGray).padBottom(8f).row();
        shopDialog.cont.add(cards).row();
        shopDialog.cont.add(drillPrices()).color(Color.lightGray).padTop(8f).row();

        shopDialog.buttons.button("Reroll (" + Packs.rerollPrice + " copper)", Icon.refresh, () -> {
            if(!run().reroll()) ui.showInfoToast("Not enough copper", 2f);
        }).size(260f, 64f).disabled(b -> !RogueRun.active() || run().copper() < Packs.rerollPrice);
        shopDialog.addCloseButton();

        shopDialog.shown(() -> state.set(State.paused));
        shopDialog.hidden(() -> {
            if(state.isGame() && !state.gameOver && (levelDialog == null || !levelDialog.isShown())) state.set(State.playing);
        });
        shopDialog.show();
    }

    void rebuildShop(Table table){
        table.clear();
        Seq<Packs.Pack> stock = run().shop;
        if(stock.isEmpty()) table.add("[lightgray]Sold out. New stock next wave.").pad(20f);

        for(Packs.Pack pack : stock){
            int price = Packs.price(pack);
            table.table(Styles.black6, card -> {
                card.top().margin(12f);
                packCard(card, pack);
                card.add().growY().row();
                card.button("Buy  " + price, Icon.add, () -> run().buy(pack))
                    .disabled(b -> !RogueRun.active() || run().copper() < price).growX().height(50f);
            }).size(240f, 300f).pad(6f);
        }
    }

    static String drillPrices(){
        StringBuilder out = new StringBuilder("Drills (always in the build menu):");
        for(var e : Packs.drills()){
            out.append("  ").append(e.key.localizedName).append(" ").append(e.value);
        }
        return out.toString();
    }

    static void packCard(Table card, Packs.Pack pack){
        card.add(pack.name).color(tierColor(pack.tier)).padBottom(4f).row();
        card.add("Tier " + (pack.tier + 1)).color(Color.lightGray).padBottom(10f).row();
        for(Packs.Entry e : pack.entries){
            card.table(line -> {
                line.image(e.block.uiIcon).size(40f);
                line.add("x" + e.amount + "  " + e.block.localizedName).padLeft(8f).left();
            }).left().pad(3f).row();
        }
    }

    public void choose(int index){
        if(levelDialog == null || index >= offered.size) return;
        Packs.Pack pack = offered.get(index);
        //a second click during the close animation must not pick again
        offered = new Seq<>();
        run().choosePack(pack);
        levelDialog.hide();
    }

    static Color tierColor(int tier){
        return switch(tier){
            case 0 -> Color.white;
            case 1 -> Pal.accent;
            case 2 -> Pal.lancerLaser;
            default -> Pal.remove;
        };
    }

    static RogueRun run(){
        return RogueRun.current;
    }
}

package mindustrysurvivors;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.func.*;
import arc.math.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.ui.fragments.*;
import mindustry.ui.fragments.MenuFragment.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;

import static mindustry.Vars.*;

public class RogueUI{
    public @Nullable BaseDialog menuDialog, rewardDialog;
    static final Color curseColor = Color.valueOf("b07ae8");
    static final String adminKey = "mindustry-survivors-admin", heatKey = "mindustry-survivors-heat";
    public Seq<Runnable> offered = new Seq<>();

    public void init(){
        ui.menufrag.addButton(new MenuButton("Mindustry Survivors", Icon.play, this::showMenu));
        buildHud();
        Events.run(Trigger.update, this::update);
    }

    static RogueRun run(){
        return RogueRun.current;
    }

    static java.lang.reflect.Field buildMenu;
    static boolean buildMenuFailed;
    static @Nullable Boolp buildMenuShown;

    //the game rebuilds its build menu from time to time and sets its visibility again, so the menu is hidden every frame
    static void hideBuildMenu(){
        if(buildMenuFailed) return;
        try{
            if(buildMenu == null){
                buildMenu = PlacementFragment.class.getDeclaredField("toggler");
                buildMenu.setAccessible(true);
            }
            Table menu = (Table)buildMenu.get(ui.hudfrag.blockfrag);
            if(menu != null && menu.visibility != buildMenuShown){
                Boolp shown = menu.visibility;
                buildMenuShown = () -> !Arena.active() && (shown == null || shown.get());
                menu.visibility = buildMenuShown;
            }
        }catch(Exception e){
            buildMenuFailed = true;
            Log.err("[Rogue] Could not hide the build menu", e);
        }
    }

    void update(){
        hideBuildMenu();
        if(!RogueRun.active()) return;
        //the game shows the sector name for 5 seconds after the landing animation, over any dialog
        if(renderer.isCutscene()) run().loadedAt = Time.millis();
        if(state.gameOver || Core.scene.hasDialog() || Time.timeSinceMillis(run().loadedAt) < (state.rules.sector == null ? 1000 : 5500)) return;
        if(run().pendingGift) showGift();
        else if(run().pendingRelic) showReward(true);
        else if(run().pendingCurse) showCurse();
        else if(run().pendingModifiers > 0) showLevelUp();
    }

    public void showMenu(){
        menuDialog = new BaseDialog("");
        Table c = menuDialog.cont;
        c.defaults().pad(4f);

        TextureRegion title = Core.atlas.find("mindustry-survivors-title");
        if(Core.atlas.isFound(title)) c.image(title).size(Math.min(title.width, 900f), Math.min(title.width, 900f) * title.height / title.width).padBottom(6f).row();
        else c.add("MINDUSTRY SURVIVORS").fontScale(2.5f).color(Pal.accent).padBottom(4f).row();
        c.add("Made by Zerkka").color(Color.lightGray).padBottom(18f).row();

        String[] picked = {Core.settings.getString(Arena.difficultyKey, Difficulty.eradication.name())};
        int[] heat = {Mathf.clamp(Core.settings.getInt(heatKey, 0), 0, RogueRun.maxHeat)};
        c.button(b -> b.label(() -> "Difficulty: " + coloredDifficulty(picked[0], heat[0])), Styles.defaultb, () -> {
            BaseDialog pick = new BaseDialog("Difficulty");
            pick.cont.defaults().size(360f, 56f).pad(3f);
            for(Difficulty difficulty : Difficulty.all){
                pick.cont.button(coloredDifficulty(difficulty.name(), 0), Styles.togglet, () -> {
                    picked[0] = difficulty.name();
                    heat[0] = 0;
                    Core.settings.put(Arena.difficultyKey, picked[0]);
                    Core.settings.put(heatKey, 0);
                    pick.hide();
                }).checked(b -> heat[0] == 0 && picked[0].equals(difficulty.name())).row();
            }
            for(int h = 1; h <= RogueRun.maxHeat; h++){
                int level = h;
                pick.cont.button(coloredDifficulty(Difficulty.eradication.name(), h), Styles.togglet, () -> {
                    heat[0] = level;
                    Core.settings.put(heatKey, level);
                    pick.hide();
                }).checked(b -> heat[0] == level).row();
            }
            pick.cont.add("[#" + curseColor + "]Extra random curses are added.").wrap().width(360f).height(-1f).padTop(6f).row();
            pick.addCloseButton();
            overMenu(pick);
            pick.show();
        }).size(360f, 50f).padBottom(14f).row();

        if(RogueRun.exists()){
            c.table(row -> {
                row.button("Continue run", Icon.play, () -> {
                    menuDialog.hide();
                    RogueRun.resume();
                }).size(292f, 64f);
                row.button(Icon.trash, Styles.defaulti, () -> ui.showConfirm("Delete save", "This run will be lost. Are you sure?", () -> {
                    RogueRun.abandon();
                    menuDialog.hide();
                    showMenu();
                })).size(64f, 64f).padLeft(4f).tooltip("Delete this save");
            }).row();
        }
        Runnable start = () -> {
            menuDialog.hide();
            Difficulty difficulty = Structs.find(Difficulty.all, d -> d.name().equals(picked[0]));
            RogueRun.startNew(difficulty == null ? Difficulty.eradication : difficulty, heat[0]);
        };
        c.button("New Game", Icon.play, () -> {
            if(RogueRun.exists()) ui.showConfirm("New run", "Your current run will be lost. Are you sure?", start);
            else start.run();
        }).size(360f, 64f).row();

        c.button("Info & Database", Icon.book, this::showDatabase).size(360f, 50f).row();

        c.check("Admin tools", Core.settings.getBool(adminKey, false), v -> Core.settings.put(adminKey, v)).padTop(10f).row();

        menuDialog.addCloseButton();
        menuDialog.show();
    }

    static final String[] difficultyColors = {"7ce07c", "b8e07c", "ffd37f", "ff9a4f", "f25555"};

    //greener is easier, redder is harder, and the cursed levels past Eradication are purple like their curses
    static String coloredDifficulty(String name, int heat){
        if(heat > 0) return "[#" + curseColor + "]" + Difficulty.eradication.localized() + " +" + heat + "[]";
        Difficulty difficulty = Structs.find(Difficulty.all, d -> d.name().equals(name));
        if(difficulty == null) difficulty = Difficulty.eradication;
        return "[#" + difficultyColors[Math.min(difficulty.ordinal(), difficultyColors.length - 1)] + "]" + difficulty.localized() + "[]";
    }

    public void showDatabase(){
        BaseDialog dialog = new BaseDialog("Info & Database");
        Seq<BaseTurret> turrets = new Seq<>();
        for(Block block : Weapons.steps.keys()){
            if(block instanceof BaseTurret turret) turrets.add(turret);
        }
        turrets.sort(t -> Weapons.intro.containsKey(t) ? 1 : 0);
        int[] tab = {0};
        BaseTurret[] shown = {turrets.isEmpty() ? null : turrets.first()};

        Table body = new Table();
        Runnable[] fill = {null};
        fill[0] = () -> {
            body.clear();
            body.top();
            if(tab[0] == 0){
                body.pane(Styles.noBarPane, this::infoPage).width(900f).growY().top();
            }else if(tab[0] == 1){
                body.pane(Styles.noBarPane, side -> {
                    side.top().defaults().size(240f, 50f).padBottom(2f);
                    for(BaseTurret turret : turrets){
                        side.button(b -> {
                            b.left().margin(6f);
                            b.image(turret.uiIcon).size(36f).padRight(8f);
                            b.add(turret.localizedName);
                        }, Styles.togglet, () -> {
                            shown[0] = turret;
                            fill[0].run();
                        }).checked(b -> shown[0] == turret).row();
                    }
                }).width(250f).growY().top().padRight(12f);
                body.pane(Styles.noBarPane, detail -> turretDetail(detail, shown[0])).width(640f).growY().top();
            }else if(tab[0] == 2){
                body.pane(Styles.noBarPane, this::passiveList).width(900f).growY().top();
            }else{
                body.pane(Styles.noBarPane, this::curseList).width(900f).growY().top();
            }
        };

        Table c = dialog.cont;
        c.table(tabs -> {
            String[] names = {"Info", "Turrets", "Passives", "Curses"};
            for(int i = 0; i < names.length; i++){
                int index = i;
                tabs.button(names[i], Styles.togglet, () -> {
                    tab[0] = index;
                    fill[0].run();
                }).checked(b -> tab[0] == index).size(200f, 50f).pad(2f);
            }
        }).padBottom(10f).row();
        c.add(body).grow();
        fill[0].run();

        dialog.addCloseButton();
        pauseWhileShown(dialog);
        overMenu(dialog);
        dialog.show();
    }

    void overMenu(BaseDialog dialog){
        BaseDialog menu = menuDialog;
        if(menu != null && menu.isShown()){
            menu.hide(null);
            dialog.hidden(() -> menu.show(Core.scene, null));
        }
    }

    void infoPage(Table t){
        t.top().left().defaults().left().padBottom(8f);
        t.add("How to play").color(Pal.accent).row();
        for(String line : new String[]{
            "Survive " + Arena.lastWave + " waves on a random map. Enemies chase you from every side.",
            "Kill enemies and pick up their gems to level up.",
            "Turrets orbit you and fire with you. They have been reworked and balanced.",
            "Up to " + Survivor.maxPassives + " passives boost you."
        }){
            t.add("[accent]-[] " + line).wrap().width(860f).row();
        }
    }

    static void turretDetail(Table t, @Nullable BaseTurret turret){
        t.top().left();
        if(turret == null) return;
        float dps = Weapons.levelOneDps(turret);
        t.table(head -> {
            head.left();
            head.image(turret.uiIcon).size(64f).padRight(12f);
            head.table(name -> {
                name.left();
                name.add(turret.localizedName).color(Pal.accent).left().row();
                name.add((Weapons.intro.containsKey(turret) ? "Support turret" : "Weapon") + (dps > 0f ? ", about " + Math.round(dps) + " damage per second at level 1" : "")).color(Color.lightGray).left();
            });
        }).left().padBottom(10f).row();
        t.add(Survivor.intro(turret)).wrap().width(620f).left().padBottom(6f).row();
        t.add("Every level after the first also adds +" + Math.round(Weapons.levelDamage * 100) + "% damage.").color(Color.lightGray).wrap().width(620f).left().padBottom(10f).row();

        t.table(rows -> {
            rows.left().defaults().left().pad(3f);
            UnlockableContent ammo = Weapons.firstAmmo(turret);
            levelRow(rows, 1, ammo, ammo == null ? "Joins your weapons" : "Joins your weapons with " + ammo.localizedName + " ammo");
            for(int level = 2; level <= Weapons.maxLevel; level++){
                Weapons.Step step = Weapons.step(turret, level);
                if(step != null) levelRow(rows, level, step.icon, step.text);
            }
        }).left();
    }

    static void levelRow(Table rows, int level, @Nullable UnlockableContent icon, String text){
        rows.add("Lv " + level).color(level == Weapons.maxLevel ? Pal.accent : Color.lightGray).width(60f);
        if(icon != null) rows.image(icon.uiIcon).size(28f);
        else rows.add().size(28f);
        rows.add(text).wrap().width(520f).padLeft(6f).row();
    }

    void curseList(Table t){
        t.top().left();
        t.add("Curses make the enemy stronger. They only come with " + Difficulty.eradication.localized() + " +1 to +4: when the run starts, a few are picked at random, and higher levels get more and harsher ones, never more than " + Curses.maxRolled + ". Each curse is worth points by how harsh it is. Your curses show under the level bar.").wrap().width(860f).color(Color.lightGray).left().padBottom(10f).row();
        t.table(rows -> {
            rows.left().defaults().left().pad(4f);
            for(Curses.Curse curse : Curses.all){
                rows.image(curse.icon()).size(40f);
                rows.add(curse.name).color(curseColor).width(220f).padLeft(6f);
                rows.add(curse.cost + (curse.cost == 1 ? " point" : " points")).color(Color.lightGray).width(90f);
                rows.add(curse.description).wrap().width(480f).row();
            }
        }).left();
    }

    void passiveList(Table t){
        t.top().left();
        t.add("You can hold up to " + Survivor.maxPassives + " passives.").color(Color.lightGray).left().padBottom(10f).row();
        t.table(rows -> {
            rows.left().defaults().left().pad(4f);
            for(Survivor.Passive passive : Survivor.passives){
                rows.image(passive.icon.uiIcon).size(40f);
                rows.add(passive.name).color(modifierColor).width(300f).padLeft(6f);
                StringBuilder text = new StringBuilder(passive.step(1));
                for(int l = 2; l <= passive.maxLevel && passive.steps.length > 1; l++) text.append("\n[lightgray]").append(l).append(": ").append(passive.step(l));
                rows.add(text.toString()).wrap().width(400f);
                rows.add(passive.maxLevel + (passive.maxLevel == 1 ? " level" : " levels")).color(Color.lightGray).width(100f).row();
            }
        }).left();
    }

    static void curseIcons(Table table, Seq<Curses.Curse> list, float size, int perRow){
        table.clear();
        table.left();
        if(list.isEmpty()){
            table.add("[lightgray]none");
            return;
        }
        int i = 0;
        for(Curses.Curse m : list){
            table.image(m.icon()).size(size).pad(2f).tooltip(m.name + ": " + m.description);
            if(++i % perRow == 0) table.row();
        }
    }

    static void pauseWhileShown(BaseDialog dialog){
        boolean pause = state.isGame() && !state.gameOver;
        dialog.shown(() -> {
            if(pause) state.set(State.paused);
        });
        dialog.hidden(() -> {
            if(pause && state.isGame() && !state.gameOver) state.set(State.playing);
        });
    }

    public void showCurse(){
        RogueRun run = run();
        Seq<Curses.Curse> curses = run.curseOffer();
        if(curses.isEmpty()){
            run.curse(null);
            return;
        }
        offered = curses.map(m -> () -> run.curse(m));

        rewardDialog = new BaseDialog("[#" + curseColor + "]Curse");
        Table c = rewardDialog.cont;
        c.add("Pick a [#" + curseColor + "]curse[].").padBottom(8f).row();

        c.table(row -> {
            for(int i = 0; i < curses.size; i++){
                Curses.Curse curse = curses.get(i);
                int index = i;
                row.button(card -> {
                    card.top().margin(12f);
                    card.image(curse.icon()).size(64f).padBottom(6f).row();
                    card.add(curse.name).color(curseColor).wrap().width(220f).padBottom(8f).get().setAlignment(Align.center);
                    card.row();
                    card.add(curse.description).wrap().width(220f).row();
                }, Styles.defaultb, () -> choose(index)).size(260f, 260f).pad(8f);
            }
        });
        pauseWhileShown(rewardDialog);
        rewardDialog.show();
    }

    public void showReward(boolean relic){
        RogueRun run = run();
        if((relic ? run.relicOffer() : run.modifierOffer()).isEmpty()){
            run.skipPicks();
            return;
        }

        rewardDialog = new BaseDialog(relic ? "[#" + Rewards.Rarity.relic.color + "]Relic" : "Level " + run.level);
        Table c = rewardDialog.cont;
        String word = relic ? "[#" + Rewards.Rarity.relic.color + "]relic[]. Each one comes with a [#" + curseColor + "]curse[]" : "card";
        c.add("Pick a " + word + ".").padBottom(8f).row();

        Table row = c.table().get();
        Runnable[] fill = {null};
        fill[0] = () -> {
            row.clear();
            Seq<Rewards.Reward> rewards = relic ? run.relicOffer() : run.modifierOffer();
            offered = rewards.map(r -> () -> run.take(r));
            for(int i = 0; i < rewards.size; i++){
                Rewards.Reward reward = rewards.get(i);
                int index = i;
                row.table(col -> {
                    col.button(card -> {
                        card.top().margin(12f);
                        if(reward.hasIcon()) card.image(reward.icon()).size(64f).padBottom(6f).row();
                        card.add(reward.rarity.label).color(reward.rarity.color).padBottom(4f).row();
                        card.add(reward.name).color(reward.rarity.color).padBottom(8f).row();
                        card.add(reward.description).wrap().width(220f).row();
                        Curses.Curse curse = relic ? run.curseFor(reward) : null;
                        if(curse != null){
                            card.image().color(curseColor).height(2f).growX().pad(10f, 0f, 10f, 0f).row();
                            card.table(head -> {
                                head.image(curse.icon()).size(32f).padRight(6f);
                                head.add(curse.name).color(curseColor);
                            }).padBottom(4f).row();
                            card.add(curse.description).wrap().width(220f).color(Color.lightGray).row();
                        }
                    }, Styles.defaultb, () -> choose(index)).size(260f, relic ? 420f : 300f).row();
                    if(!relic){
                        col.button("Reroll", Icon.refresh, () -> {
                            run.reroll(index);
                            fill[0].run();
                        }).disabled(b -> !run.canReroll(index)).size(260f, 50f).padTop(6f);
                    }
                }).pad(8f).top();
            }
        };
        fill[0].run();
        pauseWhileShown(rewardDialog);
        rewardDialog.show();
    }

    public void showLevelUp(){
        if(Survivor.offer.isEmpty()) Survivor.offer.set(Survivor.roll());
        rewardDialog = new BaseDialog("Level up!");
        Table c = rewardDialog.cont;
        c.add("Pick one.").padBottom(8f).row();
        offered = Survivor.offer.map(choice -> () -> Survivor.take(choice));
        c.table(row -> {
            for(int i = 0; i < Survivor.offer.size; i++){
                Survivor.Choice choice = Survivor.offer.get(i);
                int index = i;
                int next = choice.nextLevel();
                Color color = choice.gun != null ? Pal.accent : choice.passive != null ? modifierColor : Pal.heal;
                row.button(card -> {
                    card.top().margin(12f);
                    card.image(choice.icon()).size(64f).padBottom(6f).row();
                    card.add(choice.name()).color(color).wrap().width(210f).padBottom(4f).get().setAlignment(Align.center);
                    card.row();
                    card.add(next == 0 ? "" : next == 1 ? "New!" : "Level " + next).color(next == 1 ? Pal.accent : Color.lightGray).padBottom(8f).row();
                    TextureRegion stepIcon = choice.stepIcon();
                    card.table(line -> {
                        if(stepIcon != null) line.image(stepIcon).size(32f).padRight(8f).top();
                        line.add(choice.description()).wrap().width(stepIcon != null ? 160f : 200f);
                    });
                }, Styles.defaultb, () -> choose(index)).size(240f, 260f).pad(8f).top();
            }
        }).row();
        c.table(t -> loadout(t, 48f)).padTop(10f);
        pauseWhileShown(rewardDialog);
        rewardDialog.show();
    }

    static final Color modifierColor = Color.valueOf("5c9cff");

    static void loadout(Table t, float size){
        t.left();
        t.table(row -> {
            for(int i = 0; i < Survivor.maxWeapons; i++){
                Survivor.Gun gun = i < Survivor.ownedGuns.size ? Survivor.ownedGuns.get(i) : null;
                slot(row, gun == null ? null : gun.turret.uiIcon, gun == null ? 0 : Survivor.gunLevel.get(gun, 1), gun == null ? "Empty weapon slot" : gun.turret.localizedName, size);
            }
        }).left().row();
        t.table(row -> {
            for(int i = 0; i < Survivor.maxPassives; i++){
                Survivor.Passive passive = i < Survivor.ownedPassives.size ? Survivor.ownedPassives.get(i) : null;
                slot(row, passive == null ? null : passive.icon.uiIcon, passive == null ? 0 : Survivor.level(passive), passive == null ? "Empty passive slot" : passive.name + "\n[lightgray]" + passive.step(Survivor.level(passive)), size);
            }
        }).left();
    }

    static void slot(Table row, @Nullable TextureRegion icon, int level, String tip, float size){
        Table badge = new Table();
        badge.bottom().right();
        if(level > 0) badge.add(String.valueOf(level)).style(Styles.outlineLabel);
        Table back = new Table(Tex.pane);
        if(icon != null) back.image(icon).size(size * 0.7f);
        row.stack(back, badge).size(size).pad(2f).tooltip(tip);
    }

    public void showGift(){
        RogueRun run = run();
        Seq<Item> choices = RogueRun.giftChoices();
        int needed = Math.min(Rewards.giftChoices, choices.size);
        Seq<Item> picked = new Seq<>();
        rewardDialog = new BaseDialog("[#" + Rewards.Rarity.relic.color + "]Grace Period");
        Table c = rewardDialog.cont;
        c.add("Pick " + needed + " resources. You get " + Rewards.giftAmount + " of each.").padBottom(8f).row();
        c.table(grid -> {
            int i = 0;
            for(Item item : choices){
                grid.button(b -> {
                    b.image(item.uiIcon).size(40f).row();
                    b.add(item.localizedName).padTop(4f);
                }, Styles.clearTogglei, () -> {
                    if(picked.contains(item)) picked.remove(item);
                    else if(picked.size < needed) picked.add(item);
                }).checked(b -> picked.contains(item)).size(120f, 90f).pad(4f);
                if(++i % 6 == 0) grid.row();
            }
        }).row();
        c.button("Take", Icon.ok, () -> {
            run.gift(picked);
            rewardDialog.hide();
        }).disabled(b -> picked.size < needed).size(240f, 60f).padTop(10f);
        pauseWhileShown(rewardDialog);
        rewardDialog.show();
    }

    public void choose(int index){
        if(rewardDialog == null || index >= offered.size) return;
        Runnable pick = offered.get(index);
        //a second click during the close animation must not pick again
        offered = new Seq<>();
        pick.run();
        rewardDialog.hide();
    }

    public void showAdmin(){
        RogueRun run = run();
        if(!RogueRun.active()) return;
        BaseDialog dialog = new BaseDialog("Admin");
        Table c = dialog.cont;

        c.table(row -> {
            row.defaults().size(220f, 54f).pad(4f);
            row.button("+1 modifier pick", Icon.add, () -> {
                run.pendingModifiers++;
                dialog.hide();
            }).disabled(b -> RogueRun.captured());
            if(RogueRun.relicsOn) row.button("+1 relic pick", Icon.add, () -> {
                run.pendingRelic = true;
                dialog.hide();
            }).disabled(b -> RogueRun.captured() || run.pendingRelic);
            row.button("Skip to a wave", Icon.right, () -> showWaves(dialog));
            row.button("+1 level", Icon.up, () -> {
                run.level++;
                run.xp = 0f;
                run.pendingModifiers++;
                if(RogueRun.relicsOn && run.level % RogueRun.levelsPerRelic == 0 && run.relicsTaken < RogueRun.maxRelics) run.pendingRelic = true;
                dialog.hide();
            }).disabled(b -> RogueRun.captured());
        }).padBottom(8f).row();
        c.add("Tap to add or remove.").color(Color.lightGray).padBottom(4f).row();

        c.pane(list -> {
            list.left().defaults().left().pad(2f);
            if(RogueRun.relicsOn) adminRewards(list, run, "Relics", "[#" + Rewards.Rarity.relic.color + "]", true);
            adminRewards(list, run, "Modifiers", "[accent]", false);
            if(!RogueRun.relicsOn) return;
            list.add("[#" + curseColor + "]Curses").padTop(10f).row();
            list.table(row -> {
                row.left();
                int i = 0;
                for(Curses.Curse curse : Curses.all){
                    adminCell(row, curse.icon(), curse.name, curseColor, () -> run.curses.contains(curse), () -> run.adminToggle(curse));
                    if(++i % adminPerRow == 0) row.row();
                }
            }).row();
        }).maxHeight(Core.graphics.getHeight() / Scl.scl() * 0.6f).width(1100f);

        dialog.addCloseButton();
        pauseWhileShown(dialog);
        dialog.show();
    }

    static final int adminPerRow = 7;

    static void adminRewards(Table list, RogueRun run, String title, String color, boolean relics){
        list.add(color + title).padTop(relics ? 0f : 10f).row();
        list.table(row -> {
            row.left();
            int i = 0;
            for(Rewards.Reward reward : Rewards.all){
                if(reward.isRelic() != relics || reward.copyOf != null) continue;
                adminCell(row, reward.hasIcon() ? reward.icon() : Icon.box.getRegion(), reward.name, reward.rarity.color, () -> run.stacks(reward) > 0, () -> run.adminToggle(reward));
                if(++i % adminPerRow == 0) row.row();
            }
        }).row();
    }

    static void adminCell(Table row, TextureRegion icon, String name, Color color, Boolp owned, Runnable toggle){
        row.button(b -> {
            b.image(icon).size(40f).row();
            b.add(name).color(color).wrap().width(136f).padTop(4f).get().setAlignment(Align.center);
        }, Styles.clearTogglei, toggle).checked(b -> owned.get()).size(150f, 100f).pad(2f);
    }

    void showWaves(BaseDialog admin){
        BaseDialog dialog = new BaseDialog("Skip to a wave");
        dialog.cont.add("Enemies on the map are removed and the wave you pick is sent now.").color(Color.lightGray).padBottom(8f).row();
        dialog.cont.table(grid -> {
            for(int w = 1; w <= Arena.lastWave; w++){
                int wave = w;
                grid.button(String.valueOf(w), Styles.cleart, () -> {
                    dialog.hide();
                    admin.hide();
                    Arena.skipTo(wave);
                }).size(80f, 56f).pad(2f);
                if(w % 10 == 0) grid.row();
            }
        });
        dialog.addCloseButton();
        dialog.show();
    }

    void showSectors(BaseDialog admin){
        BaseDialog dialog = new BaseDialog("Go to a sector");
        dialog.cont.pane(list -> {
            list.defaults().size(360f, 50f).pad(3f);
            int i = 0;
            for(SectorPreset preset : RogueRun.campaignOrder()){
                Sector sector = preset.sector;
                list.button((sector.isCaptured() ? "[lightgray]" : "") + (++i) + ". " + preset.localizedName, sector.isCaptured() ? Icon.ok : Icon.play, Styles.cleart, () -> {
                    if(sector == state.rules.sector) return;
                    dialog.hide();
                    admin.hide();
                    RogueRun.adminLand(preset);
                }).disabled(b -> sector == state.rules.sector).row();
            }
        }).maxHeight(Core.graphics.getHeight() / Scl.scl() * 0.7f);
        dialog.addCloseButton();
        dialog.show();
    }

    void buildHud(){
        Table panel = new Table(Tex.wavepane);
        panel.name = "rogue-hud";
        panel.visible(() -> RogueRun.active() && ui.hudfrag.shown());
        panel.left();
        panel.defaults().growX();
        panel.add(new Bar(() -> run() == null ? "" : RogueRun.captured() ? "Sector captured" : "Level " + (run().level + 1), () -> Pal.accent, () -> run() == null ? 0f : RogueRun.captured() ? 1f : run().xpProgress())).height(24f).pad(4f).row();
        Table cursed = new Table();
        String[] lastCurses = {""};
        cursed.update(() -> {
            RogueRun run = run();
            String key = run == null ? "" : run.curses.toString();
            if(key.equals(lastCurses[0])) return;
            lastCurses[0] = key;
            cursed.clear();
            cursed.left();
            if(run == null || run.curses.isEmpty()) return;
            cursed.add("[#" + curseColor + "]Curses").padRight(6f);
            for(Curses.Curse curse : run.curses){
                cursed.image(curse.icon()).size(28f).pad(2f).tooltip("[#" + curseColor + "]" + curse.name + "[]\n[lightgray]" + curse.description);
            }
        });
        panel.add(cursed).left().pad(2f);
        panel.row();
        Table admin = new Table();
        boolean[] shown = {false};
        admin.update(() -> {
            boolean on = Core.settings.getBool(adminKey, false);
            if(on == shown[0] && admin.hasChildren() == on) return;
            shown[0] = on;
            admin.clear();
            if(on) admin.button("Admin", Icon.wrench, Styles.cleart, this::showAdmin).growX().height(40f);
        });
        panel.add(admin).growX();
        panel.row();
        Table copies = new Table();
        String[] last = {""};
        copies.update(() -> {
            StringBuilder key = new StringBuilder();
            for(Survivor.Gun gun : Survivor.ownedGuns) key.append(gun.turret.name).append(Survivor.gunLevel.get(gun, 1));
            for(Survivor.Passive p : Survivor.ownedPassives) key.append(p.id).append(Survivor.level(p));
            if(key.toString().equals(last[0])) return;
            last[0] = key.toString();
            copies.clear();
            loadout(copies, 36f);
        });
        panel.add(copies).left().pad(2f);

        Table waves = ui.hudGroup.find("waves");
        if(waves != null){
            waves.row();
            waves.add(panel).growX().left().padTop(4f);
        }else{
            ui.hudGroup.fill(t -> t.top().left().marginTop(130f).add(panel).width(330f));
        }
    }
}

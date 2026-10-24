package mindustrysurvivors;

import arc.*;
import arc.files.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;

import static mindustry.Vars.*;

public class AutoTest{
    final String shotDir = OS.env("SURVIVORS_SHOTS") == null ? "survivors-shots" : OS.env("SURVIVORS_SHOTS");
    @Nullable String pendingShot;
    boolean continued;

    public static boolean enabled(){
        return System.getenv("SURVIVORS_AUTOTEST") != null;
    }

    public void begin(){
        Log.info("[Survivors-Test] Autotest started");
        Core.settings.put("backgroundpause", false);

        Events.run(Trigger.uiDrawEnd, () -> {
            if(pendingShot != null){
                Fi file = Core.files.absolute(shotDir).child(pendingShot + ".png");
                ScreenUtils.saveScreenshot(file);
                Log.info("[Survivors-Test] Screenshot @", file.absolutePath());
                pendingShot = null;
            }
        });

        later(60f * 8f, () -> {
            Log.info("[Survivors-Test] RESULT timeout");
            Core.app.exit();
        });

        String mode = System.getenv("SURVIVORS_AUTOTEST");
        if("crash".equals(mode)){
            later(2f, () -> {
                Core.settings.put("serpulo-test-marker", "player");
                SurvivorsRun.startNew();
                Log.info("[Survivors-Test] Quitting during a run: live=@", Profile.live());
                Core.app.exit();
            });
            return;
        }
        if("endless".equals(mode)){
            later(2f, () -> SurvivorsRun.startNew());
            Events.run(Trigger.newGame, () -> later(5f, this::endless));
            return;
        }
        if("damage".equals(mode)){
            later(2f, () -> SurvivorsRun.startNew(Difficulty.eradication, 0));
            Events.run(Trigger.newGame, () -> later(4f, this::damage));
            return;
        }
        if("chase".equals(mode)){
            Chase.stressTest(this);
            return;
        }
        if("wireless".equals(mode)){
            later(2f, () -> {
                SurvivorsRun.startNew();
            });
            Events.run(Trigger.newGame, () -> later(10f, this::wireless));
            return;
        }
        if("arena".equals(mode)){
            later(1f, () -> {
                Core.settings.put(SurvivorsUI.adminKey, true);
                MindustrySurvivorsMod.ui.showMenu();
            });
            later(2f, () -> shot("01-difficulty"));
            later(2.2f, () -> {
                Element button = Core.scene.find(e -> e instanceof Label l && l.getText().toString().startsWith("Difficulty:"));
                if(button != null && button.parent != null) button.parent.fireClick();
            });
            later(3f, () -> shot("01-difficulty-pick"));
            later(3.2f, () -> Core.scene.getDialog().hide());
            later(3.5f, () -> MindustrySurvivorsMod.ui.showDatabase());
            later(4.3f, () -> shot("01-database-info"));
            later(4.6f, () -> clickTab("Turrets"));
            later(5.3f, () -> shot("01-database-turrets"));
            later(5.6f, () -> clickTab("Passives"));
            later(6.3f, () -> shot("01-database-passives"));
            later(6.6f, () -> Core.scene.getDialog().hide());
            later(6.8f, () -> MindustrySurvivorsMod.ui.showSettings(0));
            later(7.4f, () -> shot("01-settings"));
            later(7.6f, () -> clickTab("Admin"));
            later(8.1f, () -> shot("01-settings-admin"));
            later(8.3f, () -> Core.scene.getDialog().hide());
            later(8.6f, () -> {
                Core.scene.getDialog().hide();
                SurvivorsRun.startNew(Difficulty.eradication, 4);
            });
            Events.run(Trigger.newGame, () -> {
                if(!continued) later(7f, this::arena);
            });
            return;
        }
        if("turrets".equals(mode)){
            later(2f, () -> {
                SurvivorsRun.startNew();
            });
            Events.run(Trigger.newGame, () -> later(10f, this::turrets));
            return;
        }
        if("blocks".equals(mode)){
            later(2f, () -> {
                SurvivorsRun.startNew();
            });
            Events.run(Trigger.newGame, () -> later(10f, this::blocks));
            return;
        }
        if("bench".equals(mode)){
            later(2f, () -> {
                SurvivorsRun.startNew();
            });
            Events.run(Trigger.newGame, () -> later(10f, this::bench));
            return;
        }
        if("drills".equals(mode)){
            later(2f, () -> {
                SurvivorsRun.startNew();
            });
            Events.run(Trigger.newGame, () -> later(10f, this::drills));
            return;
        }
        if("curses".equals(mode)){
            later(2f, () -> {
                SurvivorsRun.startNew();
            });
            Events.run(Trigger.newGame, () -> later(10f, this::curses));
            return;
        }
        if("restore".equals(mode)){
            later(2f, () -> {
                Log.info("[Survivors-Test] RESULT after restart: live=@ playerMarker=@", Profile.live(), Core.settings.getString("serpulo-test-marker", "missing"));
                Core.app.exit();
            });
            return;
        }

        later(2f, () -> {
            Core.settings.put("serpulo-test-marker", "player");
            saveDirectory.child("sector-serpulo-999.msav").writeString("player save");
            SurvivorsRun.startNew();
        });

        Events.run(Trigger.newGame, () -> {
            if(!continued) later(12f, this::onSectorLoaded);
        });
    }

    void onSectorLoaded(){
        SurvivorsRun run = SurvivorsRun.current;
        SurvivorsUI rui = MindustrySurvivorsMod.ui;
        Log.info("[Survivors-Test] Sector loaded. active=@ launches=@ relicPending=@ unitCap=@ processorBanned=@ duoUnlocked=@",
            SurvivorsRun.active(), run.launches, run.pendingRelic, mindustry.entities.Units.getCap(state.rules.defaultTeam),
            state.rules.bannedBlocks.contains(Blocks.microProcessor), Blocks.duo.unlocked());
        shot("01-relic");

        later(0.7f, () -> rui.choose(0));
        later(1.4f, () -> {
            Log.info("[Survivors-Test] After relic: relics=@ curses=@ level=@", run.relicsTaken, run.curses.map(m -> m.name), run.level);
            Unit fake = UnitTypes.fortress.create(state.rules.waveTeam);
            for(int i = 0; i < 3; i++) Survivor.addXp(fake.maxHealth);
            Log.info("[Survivors-Test] After XP: level=@ pendingModifiers=@ xp=@", run.level, run.pendingModifiers, (int)run.xp);
            run.pendingModifiers = 1;
        });
        later(2.8f, () -> shot("03-modifier"));
        later(3.4f, () -> rui.choose(0));
        later(3.8f, () -> {
            run.curses.set(Curses.all);
            logic.runWave();
        });
        later(5.5f, () -> {
            Seq<Unit> enemies = state.rules.waveTeam.data().units.copy();
            Log.info("[Survivors-Test] enemies=@ speed=@ longRange=@", enemies.size, enemies.count(u -> u.hasEffect(Curses.speedEffect)), enemies.count(u -> u.controller() instanceof Curses.LongRangeAI));
            shot("04-hud");
        });
        later(7f, () -> {
            control.saves.getCurrent().save();
            logic.reset();
        });
        later(9f, () -> {
            Log.info("[Survivors-Test] Back in menu: live=@ playerMarker=@ runSectorKept=@",
                Profile.live(), Core.settings.getString("serpulo-test-marker", "missing"), Profile.sectorFiles(Profile.folder(Profile.run)).map(f -> f.name()));
            continued = true;
            SurvivorsRun.enter();
            control.playSector(SectorPresets.groundZero.sector);
        });
        later(16f, () -> {
            SurvivorsRun r = SurvivorsRun.current;
            Log.info("[Survivors-Test] Continued: live=@ groundZeroSave=@ rewards=@ curses=@ level=@", Profile.live(), SectorPresets.groundZero.sector.save != null,
                r.rewards.keys().toSeq().map(x -> x.name), r.curses.size, r.level);
            Logic.sectorCapture();
            Log.info("[Survivors-Test] After capture: rewards=@ curses=@ pending=@ waves=@", r.rewards.size, r.curses.size, r.pendingModifiers + (r.pendingRelic ? 1 : 0), state.rules.waves);
            logic.reset();
            SurvivorsRun.leave();
            Log.info("[Survivors-Test] RESULT live=@ playerMarker=@ playerSave=@", Profile.live(), Core.settings.getString("serpulo-test-marker", "missing"), saveDirectory.child("sector-serpulo-999.msav").exists() ? saveDirectory.child("sector-serpulo-999.msav").readString() : "missing");
            Core.app.exit();
        });
    }

    void arena(){
        SurvivorsRun run = SurvivorsRun.current;
        SurvivorsUI rui = MindustrySurvivorsMod.ui;
        Unit me = player.unit();
        Log.info("[Survivors-Test] Curses rolled at Eradication +@: @", run.heat, run.curses.map(c -> c.name));
        Log.info("[Survivors-Test] Arena: active=@ size=@x@ ground=@ water=@ cores=@ player=@ at @,@ weapons=@ allowed=@",
            SurvivorsRun.active(), world.width(), world.height(), Arena.groundSpawns.size, Arena.waterSpawns.size, state.rules.defaultTeam.cores().size,
            me == null ? "none" : me.type.name, me == null ? -1 : me.tileX(), me == null ? -1 : me.tileY(), Survivor.ownedGuns.map(g -> g.turret.name), state.rules.bannedBlocks.size);
        shot("02-start");
        later(1f, () -> {
            Survivor.Choice a = new Survivor.Choice(), b = new Survivor.Choice(), c = new Survivor.Choice();
            a.passive = Survivor.amount;
            b.passive = Survivor.armor;
            c.gun = Survivor.guns.find(g -> g.turret == Blocks.spectre);
            Survivor.Choice d = new Survivor.Choice();
            d.bonus = true;
            Survivor.offer.set(Seq.with(a, b, c, d));
            run.pendingModifiers = 1;
        });
        later(2f, () -> {
            Log.info("[Survivors-Test] Level-up: dialog=@ offer=@", Core.scene.hasDialog(), Survivor.offer.map(Survivor.Choice::name));
            shot("03-levelup");
        });
        later(3f, () -> {
            rui.choose(0);
            for(int i = 0; i < 7; i++) Survivor.give(Survivor.guns.find(g -> g.turret == Blocks.lancer));
            for(int i = 0; i < 9; i++) Survivor.give(Survivor.guns.find(g -> g.turret == Blocks.hail));
            for(int i = 0; i < 5; i++) Survivor.give(Survivor.guns.find(g -> g.turret == Blocks.duo));
            Survivor.give(Survivor.bulwark);
            for(int i = 0; i < 5; i++) Survivor.give(Survivor.guns.find(g -> g.turret == Blocks.parallax));
            for(int i = 0; i < 8; i++) Survivor.give(Survivor.guns.find(g -> g.turret == Blocks.segment));
            for(int i = 0; i < 3; i++) Survivor.give(Survivor.guns.find(g -> g.turret == Blocks.wave));
            Survivor.give(Survivor.aura);
            Survivor.give(Survivor.lifesteal);
            Survivor.give(Survivor.spark);
            for(int i = 0; i < 3; i++) Survivor.give(Survivor.shield);
            Log.info("[Survivors-Test] Loadout: weapons=@ passives=@ mounts=@", Survivor.ownedGuns.map(g -> g.turret.name + ":" + Survivor.gunLevel.get(g, 0)),
                Survivor.ownedPassives.map(p -> p.name + ":" + Survivor.level(p)), player.unit().mounts.length);
            logic.runWave();
            logic.runWave();
        });
        later(3.6f, () -> renderer.setScale(renderer.maxScale()));
        later(4.3f, () -> {
            int before = Survivor.gemValue.size;
            Survivor.magX.set(0, player.unit().x);
            Survivor.magY.set(0, player.unit().y);
            later(0.3f, () -> {
                int flying = 0;
                for(int i = 0; i < Survivor.gemPulled.size; i++) if(Survivor.gemPulled.get(i)) flying++;
                Log.info("[Survivors-Test] Magnet: gems on map @ before; 0.3s after pickup @ left, @ of them flying to you, dialog=@", before, Survivor.gemValue.size, flying, Core.scene.hasDialog());
            });
        });
        later(5f, () -> {
            Unit u = player.unit();
            float[] before = new float[u.mounts.length];
            for(int i = 0; i < u.mounts.length; i++) before[i] = u.rotation + u.mounts[i].rotation;
            u.rotation += 120f;
            later(0.2f, () -> {
                StringBuilder out = new StringBuilder();
                for(int i = 2; i < u.mounts.length; i++) out.append(Math.round(Angles.angleDist(before[i], u.rotation + u.mounts[i].rotation))).append(u.mounts[i].target == null ? "" : "(aiming)").append(" ");
                Log.info("[Survivors-Test] Turret aim change after turning 120 degrees: @", out);
            });
        });
        later(3.7f, () -> {
            Unit u = player.unit();
            int[] values = {1, 1, 3, 8, 12, 40};
            for(int i = 0; i < values.length; i++){
                Tmp.v1.trns(i * 60f, 60f);
                Survivor.gemX.add(u.x + Tmp.v1.x);
                Survivor.gemY.add(u.y + Tmp.v1.y);
                Survivor.gemValue.add(values[i]);
                Survivor.gemPulled.add(false);
            }
            Survivor.kitX.add(u.x + 70f, u.x - 70f);
            Survivor.kitY.add(u.y + 30f, u.y - 30f);
            Survivor.magX.add(u.x);
            Survivor.magY.add(u.y - 75f);
            for(int i = 0; i < 20; i++){
                Survivor.gemX.add(u.x + Mathf.range(900f));
                Survivor.gemY.add(u.y + Mathf.range(900f));
                Survivor.gemValue.add(1);
                Survivor.gemPulled.add(false);
            }
        });
        later(4f, () -> shot("03b-turrets"));
        later(7f, () -> {
            Unit u = player.unit();
            Log.info("[Survivors-Test] Fighting: wave=@ enemies=@ gems=@ xp=@ level=@ health=@/@ bullets=@", state.wave, state.rules.waveTeam.data().units.size,
                Survivor.gemValue.size, (int)run.xp, run.level, (int)u.health, (int)u.maxHealth, Groups.bullet.size());
            int i = 0;
            for(Unit e : state.rules.waveTeam.data().units){
                if(i >= 8) break;
                Tmp.v1.trns(i++ * 45f, 80f);
                e.set(u.x + Tmp.v1.x, u.y + Tmp.v1.y);
            }
        });
        later(9.5f, () -> {
            SurvivorsRun.current.pendingModifiers = 0;
            if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
            Unit u = player.unit();
            for(int i = 0; i < 12; i++){
                Tmp.v1.trns(i * 30f, 120f);
                (i % 2 == 0 ? UnitTypes.flare : UnitTypes.horizon).spawn(state.rules.waveTeam, u.x + Tmp.v1.x, u.y + Tmp.v1.y).apply(StatusEffects.invincible, 600f);
            }
            u.health = u.maxHealth;
            later(2.4f, () -> Log.info("[Survivors-Test] Air attack: health=@/@ shield=@ abilities=@ 2.4s after 6 flares and 6 horizons arrived, enemy bullets shot down by Segment @", (int)player.unit().health, (int)player.unit().maxHealth, (int)player.unit().shield, player.unit().abilities.length, Survivor.shotDown));
        });
        later(9.5f, () -> renderer.setScale(renderer.maxScale()));
        later(10f, () -> {
            shot("04-fight");
            Log.info("[Survivors-Test] Your bullets in flight while not shooting: @ (loadout @)", Groups.bullet.count(b -> b.owner == player.unit()), Survivor.ownedGuns.map(g -> g.turret.name));
            Log.info("[Survivors-Test] After fight: enemies=@ gems=@ kits=@ xp=@ level=@ pending=@ kills=@ boats=@ boatsOnLand=@", state.rules.waveTeam.data().units.size,
                Survivor.gemValue.size, Survivor.kitX.size, (int)run.xp, run.level, run.pendingModifiers, state.stats.enemyUnitsDestroyed,
                state.rules.waveTeam.data().units.count(e -> e.type.naval), state.rules.waveTeam.data().units.count(e -> e.type.naval && !e.floorOn().isLiquid));
        });
        later(12f, () -> {
            if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
            Arena.skipTo(20);
        });
        later(14f, () -> {
            Log.info("[Survivors-Test] After skip: wave=@ enemies=@", state.wave, state.rules.waveTeam.data().units.size);
            String before = Survivor.ownedGuns.map(g -> g.turret.name + ":" + Survivor.gunLevel.get(g, 0)).toString();
            Arena.save();
            continued = true;
            logic.reset();
            SurvivorsRun.resume();
            later(6f, () -> {
                Log.info("[Survivors-Test] Continued: active=@ wave=@ player=@ weapons=@ (before @) mounts=@",
                    SurvivorsRun.active(), state.wave, player.unit().type.name, Survivor.ownedGuns.map(g -> g.turret.name + ":" + Survivor.gunLevel.get(g, 0)), before, player.unit().mounts.length);
                shot("05-continued");
                Log.info("[Survivors-Test] Shake in a run: dagger death @ fortress step @ scepter death @ fortress shot @", UnitTypes.dagger.deathShake, UnitTypes.fortress.stepShake, UnitTypes.scepter.deathShake, UnitTypes.fortress.weapons.first().bullet.hitShake);
                later(1f, () -> {
                    logic.reset();
                    later(0.5f, () -> {
                        Log.info("[Survivors-Test] Shake after the run: dagger death @ fortress step @ scepter death @ fortress shot @", UnitTypes.dagger.deathShake, UnitTypes.fortress.stepShake, UnitTypes.scepter.deathShake, UnitTypes.fortress.weapons.first().bullet.hitShake);
                        MindustrySurvivorsMod.ui.showMenu();
                        later(1f, () -> shot("06-menu"));
                        later(2f, () -> {
                            Log.info("[Survivors-Test] RESULT done");
                            Core.app.exit();
                        });
                    });
                });
            });
        });
    }

    final Seq<Unit> dummies = new Seq<>();
    float dummyX, dummyY;
    boolean firing;

    void damage(){
        state.rules.waveTimer = false;
        Events.run(Trigger.update, () -> {
            for(Unit e : state.rules.waveTeam.data().units.copy()) if(!dummies.contains(e)) e.remove();
            Arena.releasing.clear();
            for(int i = 0; i < dummies.size; i++){
                Unit d = dummies.get(i);
                d.set(dummyX + (i % 3 - 1) * 14f, dummyY + (i / 3 - 1) * 14f);
                d.vel.setZero();
            }
        });
        Events.run(Trigger.uiDrawEnd, () -> {
            if(!firing || player.unit() == null) return;
            player.shooting = true;
            player.mouseX = dummyX;
            player.mouseY = dummyY;
            player.unit().aim(dummyX, dummyY);
            player.unit().isShooting = true;
        });
        Seq<Runnable> steps = new Seq<>();
        for(Block block : Weapons.steps.keys()){
            if(!(block instanceof Turret turret) || Weapons.support(turret)) continue;
            for(int level : new int[]{1, 10}){
                steps.add(() -> measure(turret, level));
            }
        }
        float t = 0f;
        for(Runnable step : steps){
            later(t, step);
            t += 4.5f;
        }
        later(t + 1f, () -> {
            Log.info("[Survivors-Test] RESULT done");
            Core.app.exit();
        });
    }

    void measure(Turret turret, int level){
        Unit me = player.unit();
        me.apply(StatusEffects.invincible, 60f * 60f);
        me.health = me.maxHealth;
        Survivor.ownedGuns.clear();
        Survivor.gunLevel.clear();
        Survivor.Gun gun = Survivor.guns.find(g -> g.turret == turret);
        for(int i = 0; i < level; i++) Survivor.give(gun);
        for(Unit d : dummies) d.remove();
        dummies.clear();
        dummyX = me.x + Math.min(80f, Weapons.reach(turret) * 0.7f);
        dummyY = me.y;
        for(int i = 0; i < 9; i++){
            Unit d = UnitTypes.dagger.spawn(state.rules.waveTeam, dummyX, dummyY);
            d.maxHealth = d.health = 1e7f;
            d.armor = 0f;
            d.apply(StatusEffects.disarmed, 60f * 60f);
            dummies.add(d);
        }
        firing = true;
        float seconds = 3f;
        later(0.5f, () -> {
            for(Unit d : dummies) d.health = d.maxHealth;
        });
        if(level == 10 && Weapons.erekir.contains(turret)) later(2f, () -> shot("damage-" + turret.name));
        later(0.5f + seconds, () -> {
            float total = 0f;
            for(Unit d : dummies) total += d.maxHealth - d.health;
            float center = dummies.get(4).maxHealth - dummies.get(4).health;
            firing = false;
            Log.info("[Survivors-Test] Damage @ level @: @ per second to the group, @ per second to the middle dummy, @ tiles away", turret.name, level, (int)(total / seconds), (int)(center / seconds), (int)((dummyX - me.x) / 8f));
        });
    }

    void endless(){
        Arena.skipTo(Arena.lastWave);
        later(1f, () -> {
            for(Unit unit : state.rules.waveTeam.data().units.copy()) unit.remove();
            Arena.releasing.clear();
        });
        later(2.5f, () -> {
            var dialog = Core.scene.getDialog();
            Log.info("[Survivors-Test] After wave @ cleared: dialog=@ gameOver=@", Arena.lastWave, dialog == null ? "none" : dialog.title.getText(), state.gameOver);
            shot("endless-choice");
        });
        later(3.5f, () -> {
            if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
            Arena.startEndless();
            for(int wave : new int[]{51, 60, 61}){
                Arena.skipTo(wave);
                var team = state.rules.teams.get(state.rules.waveTeam);
                Log.info("[Survivors-Test] Endless wave @: plays wave @, cycle @, enemy health x@ damage x@", wave, Arena.scriptWave(wave), Arena.cycle(wave), team.unitHealthMultiplier, Strings.fixed(team.unitDamageMultiplier, 2));
            }
        });
        later(5f, () -> {
            Log.info("[Survivors-Test] Endless running: wave=@ enemies=@ gameOver=@ endless=@", state.wave, state.rules.waveTeam.data().units.size, state.gameOver, Arena.endless());
            Log.info("[Survivors-Test] RESULT endless done");
            Core.app.exit();
        });
    }

    void wireless(){
        SurvivorsRun.current.pendingRelic = false;
        SurvivorsRun.current.pendingCurse = false;
        if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        float first = state.wavetime;
        logic.runWave();
        Log.info("[Survivors-Test] Wireless run: lighting=@ weather=@ alphaBuild=@ on=@ firstWaveIn=@s nextGap=@s spawnMultiplier=@ erekirOpen=@ launchTargets=@", state.rules.lighting, renderer.drawWeather, UnitTypes.alpha.buildSpeed, SurvivorsRun.wirelessOn(), (int)(first / 60f), (int)(state.wavetime / 60f),
            state.getPlanet().campaignRules.difficulty.enemySpawnMultiplier, Planets.erekir.alwaysUnlocked, Planets.serpulo.launchCandidates);
        Team team = state.rules.defaultTeam;
        Building core = team.core();
        Tile drillTile = near(core, t -> t.overlay() == Blocks.oreCopper && Build.validPlace(Blocks.mechanicalDrill, team, t.x, t.y, 0));
        if(drillTile != null) drillTile.setNet(Blocks.mechanicalDrill, team, 0);
        Tile duoTile = near(core, t -> Build.validPlace(Blocks.duo, team, t.x, t.y, 0) && t.dst(core) > 6 * tilesize);
        duoTile.setNet(Blocks.duo, team, 0);
        Wireless.pool.put(Liquids.water, 500f);
        int copper = core.items.get(Items.copper);
        Log.info("[Survivors-Test] Wireless start: objectives=@ processors=@ conveyorBanned=@ conduitBanned=@ pumpBanned=@ drill=@ copper=@",
            state.rules.objectives.all.size, Groups.build.count(b -> b.block == Blocks.worldProcessor), state.rules.bannedBlocks.contains(Blocks.conveyor), state.rules.bannedBlocks.contains(Blocks.conduit), state.rules.bannedBlocks.contains(Blocks.mechanicalPump), drillTile != null, copper);

        later(6f, () -> {
            var duo = (ItemTurret.ItemTurretBuild)duoTile.build;
            Log.info("[Survivors-Test] Wireless after 6s: drillItems=@ duoAmmo=@ duoWater=@ poolWater=@ coreCopperChange=@",
                drillTile == null ? -1 : drillTile.build.items.total(), duo.totalAmmo, duo.liquids.get(Liquids.water), Wireless.pool.get(Liquids.water, 0f), core.items.get(Items.copper) - copper);
            Core.camera.position.set(duo.x, duo.y);
            MindustrySurvivorsMod.wirelessUI.show(duo);
        });
        later(7f, () -> shot("wireless"));
        later(7.5f, () -> {
            MindustrySurvivorsMod.wirelessUI.hide();
            SurvivorsRun.current.pendingGift = true;
        });
        later(9f, () -> shot("gift"));
        later(9.5f, () -> {
            Seq<Item> pick = SurvivorsRun.giftChoices().select(i -> i != Items.copper).add(Items.copper).copy();
            pick.truncate(Math.min(3, pick.size));
            SurvivorsRun.current.gift(pick);
            var giftCore = state.rules.defaultTeam.core();
            Log.info("[Survivors-Test] Gift: picked=@ amounts=@ capacity=@", pick.map(i -> i.name), pick.map(i -> giftCore.items.get(i)), giftCore.storageCapacity);
            if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        });
        later(10f, () -> {
            SurvivorsRun run = SurvivorsRun.current;
            run.adminToggle(Rewards.livingMetal);
            run.adminToggle(Curses.all.first());
            Core.settings.put(SurvivorsUI.adminKey, true);
            MindustrySurvivorsMod.ui.showAdmin();
            Log.info("[Survivors-Test] Admin: relic=@ curse=@", run.stacks(Rewards.livingMetal), run.curses.contains(Curses.all.first()));
        });
        later(11f, () -> shot("admin"));
        later(11.5f, () -> {
            SurvivorsRun run = SurvivorsRun.current;
            run.adminToggle(Rewards.livingMetal);
            run.adminToggle(Curses.all.first());
            Log.info("[Survivors-Test] Admin removed: relic=@ curse=@", run.stacks(Rewards.livingMetal), run.curses.contains(Curses.all.first()));
            Log.info("[Survivors-Test] Campaign order: @", SurvivorsRun.campaignOrder().map(p -> p.name));
            MindustrySurvivorsMod.ui.showSectors((mindustry.ui.dialogs.BaseDialog)Core.scene.getDialog());
        });
        later(12.5f, () -> shot("sectors"));
        later(13f, () -> {
            Log.info("[Survivors-Test] RESULT wireless done");
            Core.app.exit();
        });
    }

    void turrets(){
        SurvivorsRun run = SurvivorsRun.current;
        run.pendingRelic = false;
        if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        int duoCost = Blocks.duo.requirements[0].amount;
        for(Rewards.Reward r : Rewards.all) if(!r.isRelic()) run.rewards.put(r, 1);
        run.applyTweaks();
        Team enemy = state.rules.waveTeam, team = state.rules.defaultTeam;
        Building core = team.core();
        core.items.add(Items.copper, 3000);
        core.items.add(Items.graphite, 1000);
        core.items.add(Items.coal, 1000);
        core.items.add(Items.lead, 1000);
        core.items.add(Items.scrap, 1000);
        float cx = core.x, cy = core.y;

        Tile duoA = near(core, t -> Build.validPlace(Blocks.duo, team, t.x, t.y, 0) && Build.validPlace(Blocks.duo, team, t.x + 1, t.y, 0) && Build.validPlace(Blocks.duo, team, t.x + 2, t.y, 0) && t.dst(core) > 5 * tilesize);
        for(int i = 0; i < 3; i++) world.tile(duoA.x + i, duoA.y).setNet(Blocks.duo, team, 0);
        Tile scatter = near(core, t -> Build.validPlace(Blocks.scatter, team, t.x, t.y, 0) && t.dst(duoA) > 5 * tilesize && t.dst(core) > 5 * tilesize);
        scatter.setNet(Blocks.scatter, team, 0);
        Tile scorch = near(core, t -> Build.validPlace(Blocks.scorch, team, t.x, t.y, 0) && t.dst(duoA) > 5 * tilesize && t.dst(scatter) > 5 * tilesize && t.dst(core) > 5 * tilesize);
        scorch.setNet(Blocks.scorch, team, 0);
        Tile hail = near(core, t -> Build.validPlace(Blocks.hail, team, t.x, t.y, 0) && t.dst(duoA) > 5 * tilesize && t.dst(scatter) > 5 * tilesize && t.dst(scorch) > 5 * tilesize && t.dst(core) > 5 * tilesize);
        hail.setNet(Blocks.hail, team, 0);
        Log.info("[Survivors-Test] Turrets: duoCost @ -> @, scorchHealth=@/@", duoCost, Blocks.duo.requirements[0].amount, (int)scorch.build.maxHealth, (int)Blocks.scorch.health);

        Unit groundTarget = UnitTypes.fortress.spawn(enemy, scatter.worldx() + 60f, scatter.worldy());
        Unit flyer = UnitTypes.flare.spawn(enemy, scatter.worldx() + 40f, scatter.worldy() + 40f);
        Unit duoTarget = UnitTypes.fortress.spawn(enemy, duoA.worldx() + 50f, duoA.worldy() + 40f);
        Unit burner = UnitTypes.dagger.spawn(enemy, scorch.worldx() + 30f, scorch.worldy());
        Unit bystander = UnitTypes.dagger.spawn(enemy, scorch.worldx() + 40f, scorch.worldy() + 16f);
        Unit hailTarget = UnitTypes.fortress.spawn(enemy, hail.worldx() + 200f, hail.worldy());
        for(Unit u : new Unit[]{groundTarget, flyer, duoTarget, burner, bystander, hailTarget}){
            u.apply(StatusEffects.unmoving, 60f * 30f);
            u.apply(StatusEffects.disarmed, 60f * 30f);
        }
        float groundStart = groundTarget.health;

        later(4f, () -> {
            Building d = duoA.build;
            Log.info("[Survivors-Test] Duo: phalanxGroup=@ gatlingSpeed=@ duoTargetHealth=@/@", TurretMods.groups.get(d.id, 0), TurretMods.speed(d), (int)duoTarget.health, (int)duoTarget.maxHealth);
            Log.info("[Survivors-Test] Scatter: groundTarget lost=@ flyerDead=@", (int)(groundStart - groundTarget.health), flyer.dead || !flyer.isAdded());
            Log.info("[Survivors-Test] Scorch: burnerDead=@ bystanderBurning=@", burner.dead || !burner.isAdded(), bystander.hasEffect(StatusEffects.burning));
            Log.info("[Survivors-Test] Hail: shots=@ fields=@ hailTargetHealth=@/@", ((mindustry.world.blocks.defense.turrets.Turret.TurretBuild)hail.build).totalShots, TurretMods.fields.size, (int)hailTarget.health, (int)hailTarget.maxHealth);
            Log.info("[Survivors-Test] Inferno: scorchHealth=@/@", (int)scorch.build.maxHealth, (int)Blocks.scorch.health);
            scorch.build.kill();
            scatter.remove();
            for(int i = 0; i < 3; i++) world.tile(duoA.x + i, duoA.y).remove();
            ((mindustry.world.blocks.defense.turrets.Turret.TurretBuild)hail.build).totalShots = 9;
            Unit meteorTarget = UnitTypes.fortress.spawn(enemy, hail.worldx() - 120f, hail.worldy() - 60f);
            meteorTarget.apply(StatusEffects.unmoving, 60f * 30f);
            meteorTarget.apply(StatusEffects.disarmed, 60f * 30f);
        });
        for(float t = 4.5f; t < 9f; t += 0.5f) later(t, () -> { if(Groups.bullet.contains(b -> b.type == TurretMods.meteorType)) Log.info("[Survivors-Test] Meteor in flight"); });
        later(5f, () -> Log.info("[Survivors-Test] Phoenix down: tile=@", scorch.build == null ? "empty" : scorch.build.block.name));
        later(9f, () -> {
            Core.camera.position.set(hail.worldx(), hail.worldy());
            shot("turrets");
        });
        later(16f, () -> {
            Log.info("[Survivors-Test] Phoenix after 11s: tile=@ team=@", scorch.build == null ? "empty" : scorch.build.block.name, scorch.build == null ? "-" : scorch.build.team);
            Log.info("[Survivors-Test] Hail later: shots=@ hailTargetHealth=@/@", ((mindustry.world.blocks.defense.turrets.Turret.TurretBuild)hail.build).totalShots, (int)hailTarget.health, (int)hailTarget.maxHealth);
            Log.info("[Survivors-Test] RESULT turrets done");
            Core.app.exit();
        });
    }

    final Seq<Tile> used = new Seq<>();

    Tile spot(Building core, Block block, float minDst){
        Team team = state.rules.defaultTeam;
        Tile t = near(core, c -> Build.validPlace(block, team, c.x, c.y, 0) && c.dst(core) > minDst * tilesize && !used.contains(u -> u.dst(c) < 7f * tilesize));
        if(t == null) t = near(core, c -> Build.validPlace(block, team, c.x, c.y, 0) && !used.contains(u -> u.dst(c) < 5f * tilesize));
        if(t == null) t = near(core, c -> free(c, block) && c.dst(core) > minDst * tilesize && !used.contains(u -> u.dst(c) < 7f * tilesize));
        used.add(t);
        t.setNet(block, team, 0);
        if(t.build != null && t.build.power != null && t.build.power.graph != null) t.build.power.graph.producers.add(Curses.generator);
        return t;
    }

    static boolean free(Tile c, Block block){
        int off = -(block.size - 1) / 2;
        for(int x = 0; x < block.size; x++){
            for(int y = 0; y < block.size; y++){
                Tile t = world.tile(c.x + off + x, c.y + off + y);
                if(t == null || t.block() != Blocks.air || t.floor().isDeep() || t.solid() || state.rules.defaultTeam.core().within(t, 5f * tilesize)) return false;
            }
        }
        return true;
    }

    Unit foe(UnitType type, Tile at, float dx, float dy, boolean armed){
        Unit u = type.spawn(state.rules.waveTeam, at.worldx() + dx, at.worldy() + dy);
        u.apply(StatusEffects.unmoving, 60f * 40f);
        if(!armed) u.apply(StatusEffects.disarmed, 60f * 40f);
        return u;
    }

    void blocks(){
        SurvivorsRun run = SurvivorsRun.current;
        run.pendingRelic = false;
        if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        int copperCost = Blocks.copperWall.requirements[0].amount;
        float copperTime = Blocks.copperWall.buildTime;
        for(Rewards.Reward r : Rewards.all) if(!r.isRelic()) run.rewards.put(r, 1);
        run.applyTweaks();
        Building core = state.rules.defaultTeam.core();
        for(Item item : content.items()) core.items.add(item, 2000);
        Log.info("[Survivors-Test] Walls: copper cost @ -> @, build time @ -> @, scrap @, thorium @", copperCost, Blocks.copperWall.requirements[0].amount, (int)copperTime, (int)Blocks.copperWall.buildTime, Blocks.scrapWall.requirements[0].amount, Blocks.thoriumWall.requirements[0].amount);

        Tile copper = spot(core, Blocks.copperWall, 6f);
        Unit copperFoe = foe(UnitTypes.dagger, copper, 30f, 0f, true);
        Tile plast = spot(core, Blocks.plastaniumWall, 6f);
        Unit stuck = foe(UnitTypes.dagger, plast, 9f, 0f, true);
        Tile thorium = spot(core, Blocks.thoriumWall, 6f);
        Unit irradiated = foe(UnitTypes.fortress, thorium, 20f, 0f, false);
        Tile phase = spot(core, Blocks.phaseWall, 6f);
        Unit pushed = foe(UnitTypes.dagger, phase, 8f, 0f, false);
        pushed.unapply(StatusEffects.unmoving);
        float pushedX = pushed.x, pushedY = pushed.y;
        Tile surge = spot(core, Blocks.surgeWall, 6f);
        Unit zapped = foe(UnitTypes.fortress, surge, 30f, 0f, false);
        Tile scrap = spot(core, Blocks.scrapWall, 6f);
        Tile titanium = spot(core, Blocks.titaniumWall, 6f);
        Unit shredded = foe(UnitTypes.fortress, titanium, 20f, 0f, false);
        Tile thorium2 = spot(core, Blocks.thoriumWall, 6f);
        Unit nuked = foe(UnitTypes.fortress, thorium2, 40f, 0f, false);

        Tile mender = spot(core, Blocks.mendProjector, 6f);
        Tile patient = world.tile(mender.x + 3, mender.y);
        patient.setNet(Blocks.copperWall, state.rules.defaultTeam, 0);
        Tile victim = world.tile(mender.x, mender.y + 3);
        victim.setNet(Blocks.duo, state.rules.defaultTeam, 0);
        Tile force = spot(core, Blocks.forceProjector, 6f);
        Unit shooter = foe(UnitTypes.fortress, force, 130f, 0f, true);
        Unit inside = foe(UnitTypes.fortress, force, 0f, 30f, false);

        Tile water = spot(core, Blocks.waterExtractor, 6f);
        Unit soaked = foe(UnitTypes.dagger, water, 30f, 0f, false);
        Tile cultivator = spot(core, Blocks.cultivator, 6f);
        Unit spored = foe(UnitTypes.dagger, cultivator, 30f, 0f, false);
        Tile oil = spot(core, Blocks.oilExtractor, 6f);
        Unit tarred = foe(UnitTypes.fortress, oil, 30f, 0f, false);

        later(1.5f, () -> {
            Log.info("[Survivors-Test] Health: scrapWall=@ titaniumWall=@ thoriumWall=@ (normal @/@/@)", (int)scrap.build.maxHealth, (int)titanium.build.maxHealth, (int)thorium.build.maxHealth,
                (int)Blocks.scrapWall.health, (int)Blocks.titaniumWall.health, (int)Blocks.thoriumWall.health);
            patient.build.health = patient.build.maxHealth * 0.1f;
            BlockMods.scrapDeaths = 19;
            scrap.build.kill();
            titanium.build.kill();
            thorium2.build.kill();
            victim.build.kill();
            BlockMods.growCrawler(cultivator.build);
            Building f = force.build;
            Log.info("[Survivors-Test] Force before break: broken=@ power=@", ((mindustry.world.blocks.defense.ForceProjector.ForceBuild)f).broken, f.power.status);
            for(int i = 0; i < 5; i++) UnitTypes.dagger.weapons.first().bullet.create(shooter, state.rules.waveTeam, f.x + 140f, f.y + i * 4f, 180f);
        });
        later(3f, () -> {
            Log.info("[Survivors-Test] Copper: foeHealth=@/@ (thorns+pillbox)", (int)copperFoe.health, (int)copperFoe.maxHealth);
            Log.info("[Survivors-Test] Plastanium: stuck=@ ; Thorium radiation: fortress=@/@ ; Phase: moved=@ ; Surge: fortress=@/@",
                stuck.hasEffect(StatusEffects.unmoving), (int)irradiated.health, (int)irradiated.maxHealth, (int)Mathf.dst(pushedX, pushedY, pushed.x, pushed.y), (int)zapped.health, (int)zapped.maxHealth);
            Log.info("[Survivors-Test] Destroyed: titanium tile=@ shrapnel fortress=@/@ ; critical mass fortress=@/@ ; golem fortresses=@",
                titanium.block().name, (int)shredded.health, (int)shredded.maxHealth, (int)nuked.health, (int)nuked.maxHealth, state.rules.defaultTeam.data().units.count(u -> u.type == UnitTypes.fortress));
            Log.info("[Survivors-Test] Triage: patient=@/@ ; Fungal: crawlers=@", (int)patient.build.health, (int)patient.build.maxHealth, state.rules.defaultTeam.data().units.count(u -> u.type == UnitTypes.crawler));
            Log.info("[Survivors-Test] Mirror: reflected=@ ; shooterHealth=@/@", BlockMods.mirrored, (int)shooter.health, (int)shooter.maxHealth);
            ((mindustry.world.blocks.defense.ForceProjector.ForceBuild)force.build).buildup = 1e7f;
            Log.info("[Survivors-Test] Production: wet=@ spores=@ tarred=@", soaked.hasEffect(StatusEffects.wet), spored.hasEffect(StatusEffects.sporeSlowed), tarred.hasEffect(StatusEffects.tarred));
            tarred.damage(1f);
            Events.fire(new UnitDamageEvent().set(tarred, Bullets.placeholder.create(core, state.rules.defaultTeam, tarred.x, tarred.y, 0f)));
        });
        later(4f, () -> Log.info("[Survivors-Test] Backlash: inside fortress=@/@ ; Firewall: burning=@", (int)inside.health, (int)inside.maxHealth, tarred.hasEffect(StatusEffects.burning)));
        later(5f, () -> {
            Core.camera.position.set(mender.worldx(), mender.worldy());
            shot("blocks");
        });
        later(8.5f, () -> {
            Log.info("[Survivors-Test] Rebuilt after 7s: scrap=@ duo=@", scrap.block().name, victim.block().name);
            Log.info("[Survivors-Test] RESULT blocks done");
            Core.app.exit();
        });
    }

    @Nullable Tile onOre(Building core, Block drill){
        Team team = state.rules.defaultTeam;
        Tile t = near(core, c -> Build.validPlace(drill, team, c.x, c.y, 0) && !used.contains(u -> u.dst(c) < (drill.size + 3) * tilesize));
        if(t == null) return null;
        used.add(t);
        t.setNet(drill, team, 0);
        if(t.build.power != null && t.build.power.graph != null) t.build.power.graph.producers.add(Curses.generator);
        return t;
    }

    void drills(){
        SurvivorsRun run = SurvivorsRun.current;
        run.pendingRelic = false;
        if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        Building core = state.rules.defaultTeam.core();
        Tile mech = onOre(core, Blocks.mechanicalDrill), pneu = onOre(core, Blocks.pneumaticDrill), laser = onOre(core, Blocks.laserDrill);
        later(1f, () -> {
            var m = (mindustry.world.blocks.production.Drill.DrillBuild)mech.build;
            int before = m.dominantItems;
            for(Rewards.Reward r : Seq.with(Rewards.wideBore, Rewards.doubleHaul, Rewards.deepVein, Rewards.prospector, Rewards.overpressure)) run.rewards.put(r, 1);
            run.applyTweaks();
            int copper = core.items.get(Items.copper), titanium = core.items.get(Items.titanium);
            later(1.5f, () -> Log.info("[Survivors-Test] Wide Bore: ore counted @ -> @", before, m.dominantItems));
            later(8f, () -> {
                var p = (mindustry.world.blocks.production.Drill.DrillBuild)pneu.build;
                Log.info("[Survivors-Test] Deep Vein: pneumatic mines @ ; core titanium +@ ; core copper +@", p.dominantItem, core.items.get(Items.titanium) - titanium, core.items.get(Items.copper) - copper);
                Log.info("[Survivors-Test] Double Haul and Prospector bonus keys=@ ; laser=@", BlockMods.bonusItems.size, laser == null ? "no ore spot" : ((mindustry.world.blocks.production.Drill.DrillBuild)laser.build).dominantItem);
                Log.info("[Survivors-Test] RESULT drills done");
                Core.app.exit();
            });
        });
    }

    void bench(){
        SurvivorsRun.current.pendingRelic = false;
        if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        state.rules.waveTimer = false;
        Tile spawn = spawner.getSpawns().first();
        Building core = state.rules.defaultTeam.core();
        Core.camera.position.set((spawn.worldx() + core.x) / 2f, (spawn.worldy() + core.y) / 2f);
        Timer.schedule(() -> Core.app.post(() -> {
            SurvivorsRun r = SurvivorsRun.current;
            if(r == null) return;
            r.pendingModifiers = 0;
            r.pendingRelic = false;
            r.pendingGift = false;
            if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        }), 0f, 0.2f);
        if(OS.env("BENCH_NOPHYS") != null){
            UnitTypes.dagger.physics = false;
            UnitTypes.flare.physics = false;
        }
        boolean offscreen = OS.env("BENCH_OFFSCREEN") != null;
        if(offscreen) Timer.schedule(() -> Core.app.post(() -> Core.camera.position.set(-5000f, -5000f)), 0f, 0.1f);
        int[] steps = {1000, 3000, 6000, 10000, 20000};
        float t = 0f;
        for(int step : steps){
            float at = t;
            later(at, () -> {
                int have = Groups.unit.count(u -> u.team == state.rules.waveTeam);
                for(int i = have; i < step; i++){
                    Tmp.v1.rnd(Mathf.random(30f * tilesize));
                    Unit u = (i % 2 == 0 ? UnitTypes.dagger : UnitTypes.flare).spawn(state.rules.waveTeam, spawn.worldx() + Tmp.v1.x, spawn.worldy() + Tmp.v1.y);
                    u.apply(StatusEffects.disarmed, 60f * 600f);
                }
                Log.info("[Survivors-Test] Bench spawned: group=@ team=@", Groups.unit.size(), state.rules.waveTeam.data().units.size);
                later(1f, () -> Log.info("[Survivors-Test] Bench 1s later: group=@ team=@ daggers=@ flares=@", Groups.unit.size(), state.rules.waveTeam.data().units.size,
                    Groups.unit.count(u -> u.type == UnitTypes.dagger), Groups.unit.count(u -> u.type == UnitTypes.flare)));
            });
            later(at + 4f, () -> {
                long start = Time.nanos();
                int frames = Core.graphics.getFrameId() > 0 ? (int)Core.graphics.getFrameId() : 0;
                later(3f, () -> {
                    float seconds = Time.timeSinceNanos(start) / 1e9f;
                    int count = (int)Core.graphics.getFrameId() - frames;
                    Log.info("[Survivors-Test] Bench: units=@ paused=@ fps=@ avgFrameMs=@ bullets=@", Groups.unit.size(), state.isPaused(), (int)(count / seconds), (int)(seconds * 1000f / Math.max(count, 1)), Groups.bullet.size());
                    if(step == steps[steps.length - 1]){
                        shot("bench");
                        later(1f, () -> {
                            Log.info("[Survivors-Test] RESULT bench done");
                            Core.app.exit();
                        });
                    }
                });
            });
            t += 8f;
        }
    }

    void curses(){
        SurvivorsRun run = SurvivorsRun.current;
        run.pendingRelic = false;
        if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        run.curses.set(Seq.with(Curses.hungryMechs, Curses.mechVirus, Curses.overshields, Curses.armoredSwarms, Curses.leadership, Curses.shieldTowers, Curses.ambush, Curses.speedFreaks));
        Team enemy = state.rules.waveTeam, team = state.rules.defaultTeam;
        Building core = team.core();
        Tile spawn = spawner.getSpawns().first();
        float sx = spawn.worldx(), sy = spawn.worldy();

        Unit armored = UnitTypes.dagger.spawn(enemy, sx, sy);
        Unit eater = UnitTypes.mace.spawn(enemy, sx + 16f, sy);
        Unit[] prey = {UnitTypes.dagger.spawn(enemy, sx + 24f, sy), UnitTypes.dagger.spawn(enemy, sx + 24f, sy + 8f)};
        Unit leader = UnitTypes.scepter.spawn(enemy, sx - 40f, sy);
        Unit follower = UnitTypes.dagger.spawn(enemy, sx - 40f, sy + 30f);
        Unit shielded = UnitTypes.flare.spawn(enemy, sx + 200f, sy - 200f);
        Unit slowed = UnitTypes.dagger.spawn(enemy, sx + 40f, sy + 40f);

        Tile duoTile = near(core, t -> Build.validPlace(Blocks.duo, team, t.x, t.y, 0) && t.dst(core) > 6 * tilesize);
        duoTile.setNet(Blocks.duo, team, 0);
        Tile lancerTile = near(core, t -> Build.validPlace(Blocks.lancer, team, t.x, t.y, 0) && t.dst(core) > 9 * tilesize && t.dst(duoTile) > 4 * tilesize);
        lancerTile.setNet(Blocks.lancer, team, 0);
        Tile virusTile = near(core, t -> Build.validPlace(Blocks.duo, team, t.x, t.y, 0) && t.dst(core) > 12 * tilesize && t.dst(duoTile) > 4 * tilesize && t.dst(lancerTile) > 4 * tilesize);
        virusTile.setNet(Blocks.duo, team, 0);

        later(0.5f, () -> {
            Log.info("[Survivors-Test] Armored: dagger armor=@", armored.armor);
            eater.apply(Curses.hungryEffect);
            for(Unit p : prey) p.set(eater.x + 10f, eater.y);
            shielded.shield = 0f;
            slowed.apply(StatusEffects.slow, 600f);
            Unit carrier = UnitTypes.dagger.spawn(enemy, virusTile.worldx() + 8f, virusTile.worldy());
            Curses.carriers.add(carrier.id);
            later(0.3f, carrier::kill);
        });
        later(1f, () -> shielded.shield += 10f);
        later(1.5f, () -> {
            float before = follower.health;
            follower.damage(100f);
            Curses.capHit(follower);
            Log.info("[Survivors-Test] Leadership: guarded=@ hit100 lost=@ ; Overshields: +10 became @ ; Speedy: slowed speedMult=@",
                Curses.guarded.containsKey(follower.id), before - follower.health, shielded.shield, slowed.speedMultiplier());
            duoTile.build.kill();
            lancerTile.build.kill();
            Curses.raiseTower();
        });
        later(4f, () -> {
            Building cDuo = duoTile.build, cLancer = lancerTile.build;
            Log.info("[Survivors-Test] Hungry: meals=@ maceMaxHealth=@ preyLeft=@", Curses.meals.get(eater.id, 0), (int)eater.maxHealth, prey[0].isAdded() || prey[1].isAdded());
            Log.info("[Survivors-Test] Virus: infected=@ duoHealth=@/@", Curses.infected.contains(virusTile.build == null ? -1 : virusTile.build.pos()), virusTile.build == null ? 0 : (int)virusTile.build.health, virusTile.build == null ? 0 : (int)virusTile.build.maxHealth);
            Log.info("[Survivors-Test] Conquest: duo=@ ammo=@ lancer=@ power=@ efficiency=@",
                cDuo == null ? "none" : cDuo.team, cDuo instanceof ItemTurret.ItemTurretBuild t ? t.totalAmmo : -1,
                cLancer == null ? "none" : cLancer.team, cLancer == null || cLancer.power == null ? -1 : cLancer.power.status, cLancer == null ? -1 : cLancer.efficiency);
            Log.info("[Survivors-Test] Shield Towers: towers=@", Curses.towers.size);
            Curses.towers.each(p -> {
                var f = (mindustry.world.blocks.defense.ForceProjector.ForceBuild)world.build(p);
                Log.info("[Survivors-Test] Tower at @,@ broken=@ health=@ efficiency=@", f.tileX(), f.tileY(), f.broken, (int)f.maxHealth, f.efficiency);
                Core.camera.position.set(f.x, f.y);
            });
        });
        later(5f, () -> shot("curses"));
        later(6f, () -> {
            Log.info("[Survivors-Test] RESULT curses done");
            Core.app.exit();
        });
    }

    static @Nullable Tile near(Building core, arc.func.Boolf<Tile> pred){
        Tile best = null;
        for(int dx = -45; dx <= 45; dx++){
            for(int dy = -45; dy <= 45; dy++){
                Tile t = world.tile(core.tileX() + dx, core.tileY() + dy);
                if(t != null && pred.get(t) && (best == null || t.dst(core) < best.dst(core))) best = t;
            }
        }
        return best;
    }

    static void clickTab(String name){
        Element tab = Core.scene.find(e -> e instanceof TextButton b && name.equals(b.getText().toString()));
        if(tab != null) tab.fireClick();
    }

    void shot(String name){
        pendingShot = name;
    }

    static void later(float seconds, Runnable run){
        Timer.schedule(() -> Core.app.post(run), seconds);
    }
}

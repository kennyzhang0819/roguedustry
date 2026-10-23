package roguedustry;

import arc.*;
import arc.files.*;
import arc.math.*;
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
    final String shotDir = OS.env("ROGUE_SHOTS") == null ? "rogue-shots" : OS.env("ROGUE_SHOTS");
    @Nullable String pendingShot;
    boolean continued;

    public static boolean enabled(){
        return System.getenv("ROGUE_AUTOTEST") != null;
    }

    public void begin(){
        Log.info("[Rogue-Test] Autotest started");
        Core.settings.put("backgroundpause", false);

        Events.run(Trigger.uiDrawEnd, () -> {
            if(pendingShot != null){
                Fi file = Core.files.absolute(shotDir).child(pendingShot + ".png");
                ScreenUtils.saveScreenshot(file);
                Log.info("[Rogue-Test] Screenshot @", file.absolutePath());
                pendingShot = null;
            }
        });

        later(60f * 5f, () -> {
            Log.info("[Rogue-Test] RESULT timeout");
            Core.app.exit();
        });

        String mode = System.getenv("ROGUE_AUTOTEST");
        if("crash".equals(mode)){
            later(2f, () -> {
                Core.settings.put("serpulo-test-marker", "player");
                RogueRun.startNew();
                Log.info("[Rogue-Test] Quitting during a run: live=@", Profile.live());
                Core.app.exit();
            });
            return;
        }
        if("wireless".equals(mode)){
            later(0.5f, () -> RoguedustryMod.ui.showModes());
            later(1f, () -> shot("modes"));
            later(2f, () -> {
                Core.scene.getDialog().hide();
                RogueRun.startNew(true);
                control.playSector(SectorPresets.groundZero.sector);
            });
            Events.run(Trigger.newGame, () -> later(10f, this::wireless));
            return;
        }
        if("turrets".equals(mode)){
            later(2f, () -> {
                RogueRun.startNew(true);
                control.playSector(SectorPresets.groundZero.sector);
            });
            Events.run(Trigger.newGame, () -> later(10f, this::turrets));
            return;
        }
        if("blocks".equals(mode)){
            later(2f, () -> {
                RogueRun.startNew(true);
                control.playSector(SectorPresets.groundZero.sector);
            });
            Events.run(Trigger.newGame, () -> later(10f, this::blocks));
            return;
        }
        if("curses".equals(mode)){
            later(2f, () -> {
                RogueRun.startNew(true);
                control.playSector(SectorPresets.groundZero.sector);
            });
            Events.run(Trigger.newGame, () -> later(10f, this::curses));
            return;
        }
        if("restore".equals(mode)){
            later(2f, () -> {
                Log.info("[Rogue-Test] RESULT after restart: live=@ playerMarker=@", Profile.live(), Core.settings.getString("serpulo-test-marker", "missing"));
                Core.app.exit();
            });
            return;
        }

        later(2f, () -> {
            Core.settings.put("serpulo-test-marker", "player");
            saveDirectory.child("sector-serpulo-999.msav").writeString("player save");
            RogueRun.startNew();
            Log.info("[Rogue-Test] In run: live=@ playerMarkerHidden=@ playerSaveHidden=@ copperUnlocked=@ duoCost=@ numberedLocked=@",
                Profile.live(), !Core.settings.has("serpulo-test-marker"), !saveDirectory.child("sector-serpulo-999.msav").exists(), Items.copper.unlocked(), Blocks.duo.techNode.requirements.length, !Planets.serpulo.allowLaunchToNumbered);
            control.playSector(SectorPresets.groundZero.sector);
        });

        Events.run(Trigger.newGame, () -> {
            if(!continued) later(12f, this::onSectorLoaded);
        });
    }

    void onSectorLoaded(){
        RogueRun run = RogueRun.current;
        RogueUI rui = RoguedustryMod.ui;
        Log.info("[Rogue-Test] Sector loaded. active=@ launches=@ relicPending=@ unitCap=@ processorBanned=@ duoUnlocked=@",
            RogueRun.active(), run.launches, run.pendingRelic, mindustry.entities.Units.getCap(state.rules.defaultTeam),
            state.rules.bannedBlocks.contains(Blocks.microProcessor), Blocks.duo.unlocked());
        shot("01-relic");

        later(0.7f, () -> rui.choose(0));
        later(1.4f, () -> {
            Log.info("[Rogue-Test] After relic: relics=@ curses=@ level=@", run.relicsTaken, run.curses.map(m -> m.name), run.level);
            Unit fake = UnitTypes.fortress.create(state.rules.waveTeam);
            for(int i = 0; i < 3; i++) run.gainXp(fake);
            Log.info("[Rogue-Test] After XP: level=@ pendingModifiers=@ xp=@", run.level, run.pendingModifiers, (int)run.xp);
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
            Log.info("[Rogue-Test] enemies=@ speed=@ longRange=@", enemies.size, enemies.count(u -> u.hasEffect(Curses.speedEffect)), enemies.count(u -> u.controller() instanceof Curses.LongRangeAI));
            shot("04-hud");
        });
        later(6f, rui::showRunInfo);
        later(6.5f, () -> shot("05-run"));
        later(7f, () -> {
            rui.infoDialog.hide();
            control.saves.getCurrent().save();
            logic.reset();
        });
        later(9f, () -> {
            Log.info("[Rogue-Test] Back in menu: live=@ playerMarker=@ runSectorKept=@",
                Profile.live(), Core.settings.getString("serpulo-test-marker", "missing"), Profile.sectorFiles(Profile.folder(Profile.run)).map(f -> f.name()));
            continued = true;
            RogueRun.enter();
            control.playSector(SectorPresets.groundZero.sector);
        });
        later(16f, () -> {
            RogueRun r = RogueRun.current;
            Log.info("[Rogue-Test] Continued: live=@ groundZeroSave=@ rewards=@ curses=@ level=@", Profile.live(), SectorPresets.groundZero.sector.save != null,
                r.rewards.keys().toSeq().map(x -> x.name), r.curses.size, r.level);
            Logic.sectorCapture();
            Log.info("[Rogue-Test] After capture: rewards=@ curses=@ pending=@ waves=@", r.rewards.size, r.curses.size, r.pendingModifiers + (r.pendingRelic ? 1 : 0), state.rules.waves);
            logic.reset();
            RogueRun.leave();
            Log.info("[Rogue-Test] RESULT live=@ playerMarker=@ playerSave=@", Profile.live(), Core.settings.getString("serpulo-test-marker", "missing"), saveDirectory.child("sector-serpulo-999.msav").exists() ? saveDirectory.child("sector-serpulo-999.msav").readString() : "missing");
            Core.app.exit();
        });
    }

    void wireless(){
        RogueRun.current.pendingRelic = false;
        RogueRun.current.pendingCurse = false;
        if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        float first = state.wavetime;
        logic.runWave();
        Log.info("[Rogue-Test] Wireless run: alphaBuild=@ on=@ firstWaveIn=@s nextGap=@s spawnMultiplier=@ erekirOpen=@ launchTargets=@", UnitTypes.alpha.buildSpeed, RogueRun.wirelessOn(), (int)(first / 60f), (int)(state.wavetime / 60f),
            state.getPlanet().campaignRules.difficulty.enemySpawnMultiplier, Planets.erekir.alwaysUnlocked, Planets.serpulo.launchCandidates);
        Team team = state.rules.defaultTeam;
        Building core = team.core();
        Tile drillTile = near(core, t -> t.overlay() == Blocks.oreCopper && Build.validPlace(Blocks.mechanicalDrill, team, t.x, t.y, 0));
        if(drillTile != null) drillTile.setNet(Blocks.mechanicalDrill, team, 0);
        Tile duoTile = near(core, t -> Build.validPlace(Blocks.duo, team, t.x, t.y, 0) && t.dst(core) > 6 * tilesize);
        duoTile.setNet(Blocks.duo, team, 0);
        Wireless.pool.put(Liquids.water, 500f);
        int copper = core.items.get(Items.copper);
        Log.info("[Rogue-Test] Wireless start: objectives=@ processors=@ conveyorBanned=@ conduitBanned=@ pumpBanned=@ drill=@ copper=@",
            state.rules.objectives.all.size, Groups.build.count(b -> b.block == Blocks.worldProcessor), state.rules.bannedBlocks.contains(Blocks.conveyor), state.rules.bannedBlocks.contains(Blocks.conduit), state.rules.bannedBlocks.contains(Blocks.mechanicalPump), drillTile != null, copper);

        later(6f, () -> {
            var duo = (ItemTurret.ItemTurretBuild)duoTile.build;
            Log.info("[Rogue-Test] Wireless after 6s: drillItems=@ duoAmmo=@ duoWater=@ poolWater=@ coreCopperChange=@",
                drillTile == null ? -1 : drillTile.build.items.total(), duo.totalAmmo, duo.liquids.get(Liquids.water), Wireless.pool.get(Liquids.water, 0f), core.items.get(Items.copper) - copper);
            Core.camera.position.set(duo.x, duo.y);
            RoguedustryMod.wirelessUI.show(duo);
        });
        later(7f, () -> shot("wireless"));
        later(7.5f, () -> {
            RoguedustryMod.wirelessUI.hide();
            RogueRun.current.pendingGift = true;
        });
        later(9f, () -> shot("gift"));
        later(9.5f, () -> {
            Seq<Item> pick = RogueRun.giftChoices().select(i -> i != Items.copper).add(Items.copper).copy();
            pick.truncate(Math.min(3, pick.size));
            RogueRun.current.gift(pick);
            var giftCore = state.rules.defaultTeam.core();
            Log.info("[Rogue-Test] Gift: picked=@ amounts=@ capacity=@", pick.map(i -> i.name), pick.map(i -> giftCore.items.get(i)), giftCore.storageCapacity);
            if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        });
        later(10f, () -> {
            RogueRun run = RogueRun.current;
            run.adminToggle(Rewards.livingMetal);
            run.adminToggle(Curses.all.first());
            Core.settings.put(RogueUI.adminKey, true);
            RoguedustryMod.ui.showAdmin();
            Log.info("[Rogue-Test] Admin: relic=@ curse=@", run.stacks(Rewards.livingMetal), run.curses.contains(Curses.all.first()));
        });
        later(11f, () -> shot("admin"));
        later(11.5f, () -> {
            RogueRun run = RogueRun.current;
            run.adminToggle(Rewards.livingMetal);
            run.adminToggle(Curses.all.first());
            Log.info("[Rogue-Test] Admin removed: relic=@ curse=@", run.stacks(Rewards.livingMetal), run.curses.contains(Curses.all.first()));
            Log.info("[Rogue-Test] Campaign order: @", RogueRun.campaignOrder().map(p -> p.name));
            RoguedustryMod.ui.showSectors((mindustry.ui.dialogs.BaseDialog)Core.scene.getDialog());
        });
        later(12.5f, () -> shot("sectors"));
        later(13f, () -> {
            Log.info("[Rogue-Test] RESULT wireless done");
            Core.app.exit();
        });
    }

    void turrets(){
        RogueRun run = RogueRun.current;
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
        Log.info("[Rogue-Test] Turrets: duoCost @ -> @, scorchHealth=@/@", duoCost, Blocks.duo.requirements[0].amount, (int)scorch.build.maxHealth, (int)Blocks.scorch.health);

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
            Log.info("[Rogue-Test] Duo: phalanxGroup=@ gatlingSpeed=@ duoTargetHealth=@/@", TurretMods.groups.get(d.id, 0), TurretMods.speed(d), (int)duoTarget.health, (int)duoTarget.maxHealth);
            Log.info("[Rogue-Test] Scatter: groundTarget lost=@ flyerDead=@", (int)(groundStart - groundTarget.health), flyer.dead || !flyer.isAdded());
            Log.info("[Rogue-Test] Scorch: burnerDead=@ bystanderBurning=@", burner.dead || !burner.isAdded(), bystander.hasEffect(StatusEffects.burning));
            Log.info("[Rogue-Test] Hail: shots=@ fields=@ hailTargetHealth=@/@", ((mindustry.world.blocks.defense.turrets.Turret.TurretBuild)hail.build).totalShots, TurretMods.fields.size, (int)hailTarget.health, (int)hailTarget.maxHealth);
            Log.info("[Rogue-Test] Inferno: scorchHealth=@/@", (int)scorch.build.maxHealth, (int)Blocks.scorch.health);
            scorch.build.kill();
            scatter.remove();
            for(int i = 0; i < 3; i++) world.tile(duoA.x + i, duoA.y).remove();
            ((mindustry.world.blocks.defense.turrets.Turret.TurretBuild)hail.build).totalShots = 9;
            Unit meteorTarget = UnitTypes.fortress.spawn(enemy, hail.worldx() - 120f, hail.worldy() - 60f);
            meteorTarget.apply(StatusEffects.unmoving, 60f * 30f);
            meteorTarget.apply(StatusEffects.disarmed, 60f * 30f);
        });
        for(float t = 4.5f; t < 9f; t += 0.5f) later(t, () -> { if(Groups.bullet.contains(b -> b.type == TurretMods.meteorType)) Log.info("[Rogue-Test] Meteor in flight"); });
        later(5f, () -> Log.info("[Rogue-Test] Phoenix down: tile=@", scorch.build == null ? "empty" : scorch.build.block.name));
        later(9f, () -> {
            Core.camera.position.set(hail.worldx(), hail.worldy());
            shot("turrets");
        });
        later(16f, () -> {
            Log.info("[Rogue-Test] Phoenix after 11s: tile=@ team=@", scorch.build == null ? "empty" : scorch.build.block.name, scorch.build == null ? "-" : scorch.build.team);
            Log.info("[Rogue-Test] Hail later: shots=@ hailTargetHealth=@/@", ((mindustry.world.blocks.defense.turrets.Turret.TurretBuild)hail.build).totalShots, (int)hailTarget.health, (int)hailTarget.maxHealth);
            Log.info("[Rogue-Test] RESULT turrets done");
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
        RogueRun run = RogueRun.current;
        run.pendingRelic = false;
        if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        int copperCost = Blocks.copperWall.requirements[0].amount;
        float copperTime = Blocks.copperWall.buildTime;
        for(Rewards.Reward r : Rewards.all) if(!r.isRelic()) run.rewards.put(r, 1);
        run.applyTweaks();
        Building core = state.rules.defaultTeam.core();
        for(Item item : content.items()) core.items.add(item, 2000);
        Log.info("[Rogue-Test] Walls: copper cost @ -> @, build time @ -> @, scrap @, thorium @", copperCost, Blocks.copperWall.requirements[0].amount, (int)copperTime, (int)Blocks.copperWall.buildTime, Blocks.scrapWall.requirements[0].amount, Blocks.thoriumWall.requirements[0].amount);

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

        Tile mech = spot(core, Blocks.mechanicalDrill, 6f);
        Unit rockTarget = foe(UnitTypes.fortress, mech, 30f, 0f, false);
        Tile laserDrill = spot(core, Blocks.laserDrill, 6f);
        Unit laserTarget = foe(UnitTypes.fortress, laserDrill, 40f, 0f, false);
        Tile blast = spot(core, Blocks.blastDrill, 6f);
        Unit blown = foe(UnitTypes.dagger, blast, 30f, 0f, false);
        blown.unapply(StatusEffects.unmoving);
        float blownX = blown.x, blownY = blown.y;
        Tile water = spot(core, Blocks.waterExtractor, 6f);
        Unit soaked = foe(UnitTypes.dagger, water, 30f, 0f, false);
        Tile cultivator = spot(core, Blocks.cultivator, 6f);
        Unit spored = foe(UnitTypes.dagger, cultivator, 30f, 0f, false);
        Tile oil = spot(core, Blocks.oilExtractor, 6f);
        Unit tarred = foe(UnitTypes.fortress, oil, 30f, 0f, false);

        later(1.5f, () -> {
            Log.info("[Rogue-Test] Health: scrapWall=@ titaniumWall=@ thoriumWall=@ (normal @/@/@)", (int)scrap.build.maxHealth, (int)titanium.build.maxHealth, (int)thorium.build.maxHealth,
                (int)Blocks.scrapWall.health, (int)Blocks.titaniumWall.health, (int)Blocks.thoriumWall.health);
            patient.build.health = patient.build.maxHealth * 0.1f;
            BlockMods.scrapDeaths = 19;
            scrap.build.kill();
            titanium.build.kill();
            thorium2.build.kill();
            victim.build.kill();
            BlockMods.growCrawler(cultivator.build);
            Building f = force.build;
            Log.info("[Rogue-Test] Force before break: broken=@ power=@", ((mindustry.world.blocks.defense.ForceProjector.ForceBuild)f).broken, f.power.status);
            for(int i = 0; i < 5; i++) UnitTypes.dagger.weapons.first().bullet.create(shooter, state.rules.waveTeam, f.x + 140f, f.y + i * 4f, 180f);
        });
        later(3f, () -> {
            Log.info("[Rogue-Test] Copper: foeHealth=@/@ (thorns+pillbox)", (int)copperFoe.health, (int)copperFoe.maxHealth);
            Log.info("[Rogue-Test] Plastanium: stuck=@ ; Thorium radiation: fortress=@/@ ; Phase: moved=@ ; Surge: fortress=@/@",
                stuck.hasEffect(StatusEffects.unmoving), (int)irradiated.health, (int)irradiated.maxHealth, (int)Mathf.dst(pushedX, pushedY, pushed.x, pushed.y), (int)zapped.health, (int)zapped.maxHealth);
            Log.info("[Rogue-Test] Destroyed: titanium tile=@ shrapnel fortress=@/@ ; critical mass fortress=@/@ ; golem fortresses=@",
                titanium.block().name, (int)shredded.health, (int)shredded.maxHealth, (int)nuked.health, (int)nuked.maxHealth, state.rules.defaultTeam.data().units.count(u -> u.type == UnitTypes.fortress));
            Log.info("[Rogue-Test] Triage: patient=@/@ ; Fungal: crawlers=@", (int)patient.build.health, (int)patient.build.maxHealth, state.rules.defaultTeam.data().units.count(u -> u.type == UnitTypes.crawler));
            Log.info("[Rogue-Test] Mirror: reflected=@ ; shooterHealth=@/@", BlockMods.mirrored, (int)shooter.health, (int)shooter.maxHealth);
            ((mindustry.world.blocks.defense.ForceProjector.ForceBuild)force.build).buildup = 1e7f;
            Log.info("[Rogue-Test] Production: rock fortress=@/@ laser fortress=@/@ blown=@ wet=@ spores=@ tarred=@",
                (int)rockTarget.health, (int)rockTarget.maxHealth, (int)laserTarget.health, (int)laserTarget.maxHealth, (int)Mathf.dst(blownX, blownY, blown.x, blown.y),
                soaked.hasEffect(StatusEffects.wet), spored.hasEffect(StatusEffects.sporeSlowed), tarred.hasEffect(StatusEffects.tarred));
            tarred.damage(1f);
            Events.fire(new UnitDamageEvent().set(tarred, Bullets.placeholder.create(core, state.rules.defaultTeam, tarred.x, tarred.y, 0f)));
        });
        later(4f, () -> Log.info("[Rogue-Test] Backlash: inside fortress=@/@ ; Firewall: burning=@", (int)inside.health, (int)inside.maxHealth, tarred.hasEffect(StatusEffects.burning)));
        later(5f, () -> {
            Core.camera.position.set(mender.worldx(), mender.worldy());
            shot("blocks");
        });
        later(8.5f, () -> {
            Log.info("[Rogue-Test] Rebuilt after 7s: scrap=@ duo=@", scrap.block().name, victim.block().name);
            Log.info("[Rogue-Test] RESULT blocks done");
            Core.app.exit();
        });
    }

    void curses(){
        RogueRun run = RogueRun.current;
        run.pendingRelic = false;
        if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        run.curses.set(Seq.with(Curses.hungryMechs, Curses.mechVirus, Curses.overshields, Curses.armoredSwarms, Curses.leadership, Curses.shieldTowers, Curses.conquest, Curses.speedFreaks));
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
            Log.info("[Rogue-Test] Armored: dagger armor=@", armored.armor);
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
            Log.info("[Rogue-Test] Leadership: guarded=@ hit100 lost=@ ; Overshields: +10 became @ ; Speedy: slowed speedMult=@",
                Curses.guarded.containsKey(follower.id), before - follower.health, shielded.shield, slowed.speedMultiplier());
            duoTile.build.kill();
            lancerTile.build.kill();
            Curses.raiseTower();
        });
        later(4f, () -> {
            Building cDuo = duoTile.build, cLancer = lancerTile.build;
            Log.info("[Rogue-Test] Hungry: meals=@ maceMaxHealth=@ preyLeft=@", Curses.meals.get(eater.id, 0), (int)eater.maxHealth, prey[0].isAdded() || prey[1].isAdded());
            Log.info("[Rogue-Test] Virus: infected=@ duoHealth=@/@", Curses.infected.contains(virusTile.build == null ? -1 : virusTile.build.pos()), virusTile.build == null ? 0 : (int)virusTile.build.health, virusTile.build == null ? 0 : (int)virusTile.build.maxHealth);
            Log.info("[Rogue-Test] Conquest: duo=@ ammo=@ lancer=@ power=@ efficiency=@",
                cDuo == null ? "none" : cDuo.team, cDuo instanceof ItemTurret.ItemTurretBuild t ? t.totalAmmo : -1,
                cLancer == null ? "none" : cLancer.team, cLancer == null || cLancer.power == null ? -1 : cLancer.power.status, cLancer == null ? -1 : cLancer.efficiency);
            Log.info("[Rogue-Test] Shield Towers: towers=@", Curses.towers.size);
            Curses.towers.each(p -> {
                var f = (mindustry.world.blocks.defense.ForceProjector.ForceBuild)world.build(p);
                Log.info("[Rogue-Test] Tower at @,@ broken=@ health=@ efficiency=@", f.tileX(), f.tileY(), f.broken, (int)f.maxHealth, f.efficiency);
                Core.camera.position.set(f.x, f.y);
            });
        });
        later(5f, () -> shot("curses"));
        later(6f, () -> {
            Log.info("[Rogue-Test] RESULT curses done");
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

    void shot(String name){
        pendingShot = name;
    }

    static void later(float seconds, Runnable run){
        Timer.schedule(() -> Core.app.post(run), seconds);
    }
}

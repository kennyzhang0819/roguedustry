package mindustrysurvivors;

import arc.*;
import arc.func.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ai.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;

import static mindustry.Vars.*;

public class Chase{
    public static int field = -1, amphibious = -1, landBoatId = -1;
    static final float landSpeed = 0.5f;
    static final ObjectMap<UnitType, Prov<? extends Unit>> realConstructors = new ObjectMap<>();
    static final ObjectIntMap<UnitType> realPathTypes = new ObjectIntMap<>();
    static final ObjectMap<UnitType, Boolean> realScorch = new ObjectMap<>();
    static final int searchRadius = 40;
    static final float closeRange = 3f * tilesize;
    static float fixTimer;
    public static UnitType walker;
    static final float gunRange = 1.3f, gunBulletSpeed = 1.5f, gunReload = 1.25f, gunDamage = 8f;
    static final ObjectMap<UnitType, boolean[]> realTargets = new ObjectMap<>();
    static final ObjectMap<BulletType, boolean[]> realHits = new ObjectMap<>();

    public static void load(){
        landBoatId = EntityMapping.register("mindustry-survivors-land-boat", LandBoat::new);
        walker = new UnitType("core-walker"){{
            constructor = Walker::new;
            localizedName = "Walker";
            isEnemy = false;
            alwaysUnlocked = true;
            canBoost = false;
            speed = 1.25f;
            hitSize = 8f;
            health = 5000f;
            mechFrontSway = 0.55f;
            for(Weapon weapon : UnitTypes.beta.weapons){
                Weapon copy = weapon.copy();
                copy.bullet = weapon.bullet.copy();
                copy.reload /= gunReload;
                copy.bullet.damage *= gunDamage;
                copy.bullet.speed *= gunBulletSpeed;
                copy.bullet.lifetime *= gunRange / gunBulletSpeed;
                copy.bullet.range *= gunRange;
                weapons.add(copy);
            }
        }

            @Override
            public void createIcons(mindustry.graphics.MultiPacker packer){
            }

            @Override
            public void load(){
                super.load();
                try{
                    for(var f : UnitType.class.getFields()){
                        if(f.getType() == TextureRegion.class || f.getType() == TextureRegion[].class) f.set(this, f.get(UnitTypes.nova));
                    }
                }catch(Exception e){
                    Log.err("[Survivors] Could not copy the walker sprites", e);
                }
            }
        };
    }

    static void setup(boolean run){
        ObjectSet<BulletType> done = new ObjectSet<>();
        for(Arena.Family family : Arena.families){
            for(UnitType type : family.tiers){
                if(!realScorch.containsKey(type)) realScorch.put(type, type.createScorch);
                type.createScorch = !run && realScorch.get(type);
                if(type.naval){
                    if(!realConstructors.containsKey(type)){
                        realConstructors.put(type, type.constructor);
                        realPathTypes.put(type, type.flowfieldPathType);
                    }
                    type.constructor = run ? LandBoat::new : realConstructors.get(type);
                    type.flowfieldPathType = run ? amphibious : realPathTypes.get(type);
                }
                if(!realTargets.containsKey(type)) realTargets.put(type, new boolean[]{type.targetAir, type.targetGround});
                boolean[] real = realTargets.get(type);
                type.targetAir = run || real[0];
                type.targetGround = run || real[1];
                for(Weapon weapon : type.weapons) hitsAll(weapon.bullet, run, done);
            }
        }
    }

    static void hitsAll(@Nullable BulletType bullet, boolean run, ObjectSet<BulletType> done){
        if(bullet == null || !done.add(bullet)) return;
        if(!realHits.containsKey(bullet)) realHits.put(bullet, new boolean[]{bullet.collidesAir, bullet.collidesGround});
        boolean[] real = realHits.get(bullet);
        bullet.collidesAir = run || real[0];
        bullet.collidesGround = run || real[1];
        hitsAll(bullet.fragBullet, run, done);
        hitsAll(bullet.intervalBullet, run, done);
        for(BulletType other : bullet.spawnBullets) hitsAll(other, run, done);
    }

    public static @Nullable Position goal(){
        Unit unit = player.unit();
        return unit != null && unit.isValid() && !unit.dead && unit.team == state.rules.defaultTeam ? unit : null;
    }

    //only reported to the game's input, which otherwise offers to respawn at a core; the real flag would despawn the walker whenever the player lets go of it
    public static class Walker extends MechUnit{
        @Override
        public boolean spawnedByCore(){
            return true;
        }

        @Override
        public void rawDamage(float amount){
            if(!Survivor.dodges()) super.rawDamage(amount);
        }
    }

    //boats in a run also cross land at half speed, so the player can't just stay away from the water
    public static class LandBoat extends UnitWaterMove{
        @Override
        public int classId(){
            return landBoatId;
        }

        @Override
        public EntityCollisions.SolidPred solidity(){
            return isFlying() ? null : EntityCollisions::solid;
        }

        @Override
        public boolean onSolid(){
            return EntityCollisions.solid(tileX(), tileY());
        }

        @Override
        public float floorSpeedMultiplier(){
            Floor on = floorOn();
            return on.isLiquid ? (on.shallow ? 1f : 1.3f) * speedMultiplier : landSpeed * speedMultiplier;
        }
    }

    public static class PlayerField extends Pathfinder.Flowfield{
        public PlayerField(){
            refreshRate = 200;
        }

        @Override
        protected void getPositions(IntSeq out){
            Position goal = goal();
            int x = World.toTile(goal == null ? Survivor.startX() : goal.getX()), y = World.toTile(goal == null ? Survivor.startY() : goal.getY());
            //paths only spread out from targets this unit type can stand on, so boats need the nearest water instead
            //a path that ever has no target is never refreshed again, so the goal's own tile stays as a fallback
            for(int r = 0; r <= searchRadius && out.isEmpty(); r++){
                for(int dx = -r; dx <= r; dx++){
                    for(int dy = -r; dy <= r; dy++){
                        if(Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                        int tx = x + dx, ty = y + dy;
                        if(tx < 0 || ty < 0 || tx >= world.width() || ty >= world.height()) continue;
                        int pos = world.packArray(tx, ty);
                        if(passable(pos)) out.add(pos);
                    }
                }
            }
            if(out.isEmpty()) out.add(world.packArray(x, y));
        }
    }

    public static class ChaseAI extends AIController{
        @Override
        public void updateMovement(){
            Position goal = goal();
            if(goal == null) return;

            //bombers and strafers only shoot above a minimum speed, so they must keep circling instead of parking on the player
            if(unit.type.flying && unit.type.circleTarget && goal instanceof Teamc t){
                target = t;
                circleAttack(unit.type.circleTargetRadius);
            }else if(unit.type.flying || unit.within(goal, closeRange)){
                moveTo(goal, unit.type.flying ? unit.type.range * 0.5f : unit.hitSize);
            }else{
                pathfind(field);
            }

            if(unit.type.canBoost && unit.elevation > 0.001f && !unit.onSolid()){
                unit.elevation = Mathf.approachDelta(unit.elevation, 0f, unit.type.descentSpeed);
            }

            faceTarget();
        }

        @Override
        public Teamc findMainTarget(float x, float y, float range, boolean air, boolean ground){
            Unit me = player.unit();
            if(me != null && me.isValid() && me.team != unit.team && (me.isFlying() ? air : ground) && me.within(x, y, range + me.hitSize / 2f)) return me;
            return super.findMainTarget(x, y, range, air, ground);
        }
    }

    static boolean chases(Unit unit){
        return Arena.active() && unit.team == state.rules.waveTeam && !unit.isPlayer();
    }

    static void install(){
        for(UnitType type : content.units()){
            var real = type.controller;
            type.controller = u -> chases(u) ? new ChaseAI() : real.get(u);
        }
    }

    static void update(){
        if(!Arena.active() || (fixTimer += Time.delta) < 30f) return;
        fixTimer = 0f;
        for(Unit unit : state.rules.waveTeam.data().units){
            if(!(unit.controller() instanceof ChaseAI) && chases(unit)) unit.controller(new ChaseAI());
        }
    }

    static void register(){
        Pathfinder.fieldTypes.add(PlayerField::new);
        field = Pathfinder.fieldTypes.size - 1;
        Pathfinder.costTypes.add((team, tile) ->
            (((PathTile.team(tile) == team && !PathTile.teamPassable(tile)) || PathTile.team(tile) == 0) && PathTile.solid(tile)) ? -1 : 1 +
            PathTile.health(tile) * 5 +
            (PathTile.nearSolid(tile) ? 2 : 0) +
            (PathTile.liquid(tile) ? 0 : 1));
        amphibious = Pathfinder.costTypes.size - 1;
        Events.on(ClientLoadEvent.class, e -> install());
        Events.run(Trigger.update, Chase::update);
    }

    static final int[] stressSteps = {500, 1000, 2000, 3000, 5000};
    static float angle, updateNs, drawNs;
    static long tickStart, drawStart;
    static int updates, draws;
    static boolean offscreen;
    static final ObjectMap<Unit, Vec2> lastSeen = new ObjectMap<>();

    static void stressTest(AutoTest test){
        AutoTest.later(2f, SurvivorsRun::startNew);
        Events.run(Trigger.newGame, () -> AutoTest.later(6f, () -> stress(test)));
    }

    static void stress(AutoTest test){
        UnitType[] stressTypes = {UnitTypes.dagger, UnitTypes.dagger, UnitTypes.atrax, UnitTypes.nova, UnitTypes.flare, UnitTypes.flare, UnitTypes.risso};
        state.rules.waveTimer = false;
        Timer.schedule(() -> Core.app.post(() -> {
            SurvivorsRun r = SurvivorsRun.current;
            if(r == null) return;
            r.pendingModifiers = 0;
            r.pendingRelic = false;
            if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        }), 0f, 0.2f);

        Unit me0 = player.unit();
        Log.info("[Survivors-Test] Chase setup: core unit=@ flying=@ physics=@ fortress hits air=@ flare hits ground=@",
            me0 == null ? "none" : me0.type.name, me0 != null && me0.isFlying(), UnitTypes.dagger.physics,
            UnitTypes.fortress.weapons.first().bullet.collidesAir, UnitTypes.flare.targetGround && UnitTypes.flare.weapons.first().bullet.collidesGround);
        float cx = world.unitWidth() / 2f, cy = world.unitHeight() / 2f, radius = 70f * tilesize;
        Events.run(Trigger.update, () -> {
            Unit me = player.unit();
            if(me == null || !Arena.active()) return;
            angle += 2.2f / radius * Time.delta;
            float nx = cx + Mathf.cos(angle) * radius, ny = cy + Mathf.sin(angle) * radius;
            if(me.canPass(World.toTile(nx), World.toTile(ny))) me.set(nx, ny);
            me.vel.setZero();
            me.health = me.maxHealth;
            if(offscreen) Core.camera.position.set(-5000f, -5000f);
            else Core.camera.position.set(me);
        });
        Events.run(Trigger.beforeGameUpdate, () -> tickStart = Time.nanos());
        Events.run(Trigger.afterGameUpdate, () -> {
            updateNs += Time.timeSinceNanos(tickStart);
            updates++;
        });
        Events.run(Trigger.preDraw, () -> drawStart = Time.nanos());
        Events.run(Trigger.postDraw, () -> {
            drawNs += Time.timeSinceNanos(drawStart);
            draws++;
        });

        float t = 0f;
        for(int step : stressSteps){
            AutoTest.later(t, () -> {
                int have = state.rules.waveTeam.data().units.size;
                for(int i = have; i < step; i++){
                    UnitType type = stressTypes[i % stressTypes.length];
                    if(type.naval && Arena.waterSpawns.isEmpty()) type = UnitTypes.dagger;
                    Tile tile = Arena.spawnTile(type.naval);
                    if(tile == null) continue;
                    Unit u = type.spawn(state.rules.waveTeam, tile.worldx() + Mathf.range(tilesize / 2f), tile.worldy() + Mathf.range(tilesize / 2f));
                    u.apply(StatusEffects.disarmed, 60f * 600f);
                }
                Log.info("[Survivors-Test] Chase spawned: enemies=@ chasing=@", state.rules.waveTeam.data().units.size,
                    state.rules.waveTeam.data().units.count(u -> u.controller() instanceof ChaseAI));
            });
            AutoTest.later(t + 3f, Chase::sample);
            AutoTest.later(t + 3f, Chase::resetTimers);
            if(step == 2000) AutoTest.later(t + 5f, () -> test.shot("chase-2k"));
            AutoTest.later(t + 6f, () -> {
                report(step, "onscreen");
                sample();
                offscreen = true;
                resetTimers();
            });
            AutoTest.later(t + 9f, () -> {
                report(step, "offscreen");
                offscreen = false;
                if(step == stressSteps[stressSteps.length - 1]){
                    test.shot("chase-5k");
                    AutoTest.later(1f, () -> {
                        Log.info("[Survivors-Test] RESULT chase done");
                        Core.app.exit();
                    });
                }
            });
            t += 10f;
        }
    }

    static long frameStart, frameId;

    static void resetTimers(){
        updateNs = drawNs = 0f;
        updates = draws = 0;
        frameStart = Time.nanos();
        frameId = Core.graphics.getFrameId();
    }

    static void sample(){
        lastSeen.clear();
        for(Unit u : state.rules.waveTeam.data().units) lastSeen.put(u, new Vec2(u.x, u.y));
    }

    static void report(int step, String view){
        float seconds = Time.timeSinceNanos(frameStart) / 1e9f;
        long frames = Core.graphics.getFrameId() - frameId;
        Position goal = goal();
        int ground = 0, air = 0, stuck = 0, stuckBoats = 0, boatsOnLand = 0, closer = 0, tracked = 0;
        float groundDst = 0f, airDst = 0f;
        for(Unit u : state.rules.waveTeam.data().units){
            float dst = u.dst(goal);
            if(u.isFlying()){
                air++;
                airDst += dst;
            }else{
                ground++;
                groundDst += dst;
            }
            if(u.type.naval && !u.floorOn().isLiquid) boatsOnLand++;
            Vec2 was = lastSeen.get(u);
            if(was == null) continue;
            tracked++;
            if(was.dst(goal) > dst) closer++;
            if(!u.isFlying() && was.within(u.x, u.y, tilesize / 2f) && dst > 8f * tilesize){
                if(u.type.naval) stuckBoats++;
                else stuck++;
            }
        }
        Pathfinder.Flowfield f = pathfinder.getField(state.rules.waveTeam, Pathfinder.costGround, field);
        Log.info("[Survivors-Test] Chase @ @: enemies=@ fps=@ updateMs=@ drawMs=@ groundTiles=@ airTiles=@ closer=@% stuckGround=@ stuckBoats=@ boatsOnLand=@ fieldReady=@",
            step, view, ground + air, (int)(frames / seconds),
            Strings.fixed(updateNs / Math.max(updates, 1) / 1e6f, 2), Strings.fixed(drawNs / Math.max(draws, 1) / 1e6f, 2),
            (int)(groundDst / Math.max(ground, 1) / tilesize), (int)(airDst / Math.max(air, 1) / tilesize),
            tracked == 0 ? 0 : closer * 100 / tracked, stuck, stuckBoats, boatsOnLand, f.hasCompleteWeights());
    }
}

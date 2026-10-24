package mindustrysurvivors;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.ConstructBlock.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.defense.turrets.Turret.*;

import static mindustry.Vars.*;

public class TurretMods{
    static final float gatlingMax = 5f, gatlingRamp = 60f * 6f, gatlingIdle = 60f * 2f, phalanxBonus = 0.1f, phoenixTime = 60f * 10f,
        fieldTime = 60f * 8f, fieldRadius = 2.5f * 8f, wildfireRadius = 3f * 8f;
    static final int phalanxMax = 20, meteorEvery = 10, fieldMax = 30;

    public static BulletType meteorType;
    static final ObjectMap<BulletType, BulletType> groundFlak = new ObjectMap<>();
    static final ObjectMap<Block, ItemStack[]> realCost = new ObjectMap<>();
    static final ObjectFloatMap<Block> realBuildTime = new ObjectFloatMap<>();

    static final IntSet seenBullets = new IntSet(), skyfallen = new IntSet();
    static final Seq<Tracked> tracked = new Seq<>();
    static final IntFloatMap firing = new IntFloatMap(), idle = new IntFloatMap();
    public static final IntIntMap groups = new IntIntMap();
    public static final IntMap<Rebuild> rebuilding = new IntMap<>();
    public static final Seq<float[]> fields = new Seq<>();
    static float cleanupTimer, groupTimer, fieldTimer;

    static class Tracked{
        Bullet bullet;
        int id;
        Block block;
        float x, y;
    }

    static class Rebuild{
        Block block;
        Team team;
        int rotation;
        float progress, time;
    }

    public static void load(){
        meteorType = new ArtilleryBulletType(1.6f, 0f, "shell"){{
            lifetime = 160f;
            width = height = 34f;
            splashDamage = 900f;
            splashDamageRadius = 6f * 8f;
            knockback = 2f;
            collidesTiles = false;
            frontColor = Pal.lightishOrange;
            backColor = Pal.lightOrange;
            trailColor = Pal.lightOrange;
            trailWidth = 6f;
            trailLength = 14;
            hitEffect = Fx.massiveExplosion;
            despawnEffect = Fx.massiveExplosion;
            hitShake = despawnShake = 8f;
            status = StatusEffects.burning;
            statusDuration = 60f * 6f;
        }};
        for(BulletType type : ((ItemTurret)Blocks.scatter).ammoTypes.values().toSeq()){
            BulletType copy = type.copy();
            copy.collidesGround = true;
            groundFlak.put(type, copy);
        }
    }

    static boolean has(Rewards.Reward reward){
        return RogueRun.active() && RogueRun.current.stacks(reward) > 0;
    }

    static float sum(Block block, Func<Rewards.Reward, Float> field){
        float total = 0f;
        if(!RogueRun.active()) return total;
        for(var e : RogueRun.current.rewards){
            if(e.key.blocks.contains(block)) total += field.get(e.key) * e.value;
        }
        return total;
    }

    public static float speed(Building build){
        float mult = 1f;
        if(build.block == Blocks.duo){
            if(has(Rewards.gatling)) mult *= 1f + (gatlingMax - 1f) * Math.min(firing.get(build.id, 0f) / gatlingRamp, 1f);
            if(has(Rewards.phalanx)) mult *= 1f + phalanxBonus * (groups.get(build.id, 1) - 1);
        }
        return mult;
    }

    public static void applyCosts(){
        restoreCosts();
        if(!RogueRun.active() && (RogueRun.current == null || !Profile.inRun())) return;
        for(var e : RogueRun.current.rewards){
            if(e.key.buildTime > 0f){
                for(Block block : e.key.blocks){
                    if(!realBuildTime.containsKey(block)) realBuildTime.put(block, block.buildTime);
                    block.buildTime = realBuildTime.get(block, block.buildTime) * (1f - e.key.buildTime);
                }
            }
            if(e.key.cost <= 0f) continue;
            for(Block block : e.key.blocks){
                if(!realCost.containsKey(block)) realCost.put(block, block.requirements);
                ItemStack[] scaled = new ItemStack[block.requirements.length];
                for(int i = 0; i < scaled.length; i++){
                    ItemStack s = block.requirements[i];
                    scaled[i] = new ItemStack(s.item, Math.max(1, Math.round(s.amount * (1f - e.key.cost))));
                }
                block.requirements = scaled;
            }
        }
    }

    public static void restoreCosts(){
        for(var e : realCost) e.key.requirements = e.value;
        realCost.clear();
        for(var e : realBuildTime) e.key.buildTime = e.value;
        realBuildTime.clear();
    }

    static void update(){
        Team team = state.rules.defaultTeam;

        if((cleanupTimer += Time.delta) > 600f){
            cleanupTimer = 0f;
            seenBullets.clear();
        }
        for(Bullet b : Groups.bullet){
            if(b.team != team || b.time > Time.delta * 1.5f || !(b.owner instanceof TurretBuild t) || !seenBullets.add(b.id)) continue;
            fired(b, t);
        }
        for(int i = tracked.size - 1; i >= 0; i--){
            Tracked t = tracked.get(i);
            if(!t.bullet.isAdded() || t.bullet.id != t.id){
                ended(t);
                tracked.remove(i);
            }else{
                t.x = t.bullet.x;
                t.y = t.bullet.y;
            }
        }

        boolean gatling = has(Rewards.gatling), groundOn = has(Rewards.groundFlak);
        for(Building build : team.data().buildings){
            if(!(build instanceof TurretBuild t)) continue;
            Turret block = (Turret)t.block;
            if(gatling && block == Blocks.duo){
                if(t.isShooting() && t.target != null){
                    firing.put(t.id, firing.get(t.id, 0f) + Time.delta);
                    idle.put(t.id, 0f);
                }else if(idle.increment(t.id, 0f, Time.delta) > gatlingIdle){
                    firing.put(t.id, 0f);
                }
            }
            float range = sum(block, r -> r.range);
            boolean ground = groundOn && block == Blocks.scatter;
            if((range > 0f || ground) && (t.target == null || !t.isShooting())){
                boolean air = block.targetAir, gr = block.targetGround || ground;
                Teamc target = Units.closestTarget(team, t.x, t.y, block.range * (1f + range), u -> u.checkTarget(air, gr), b -> gr);
                if(target != null) t.target = target;
            }
        }

        if(has(Rewards.phalanx) && (groupTimer += Time.delta) >= 30f){
            groupTimer = 0f;
            linkDuos(team);
        }

        updateRebuilds();
        updateFields();
    }

    static void fired(Bullet b, TurretBuild t){
        Block block = t.block;
        float damage = 1f + sum(block, r -> r.damage);
        if(block == Blocks.duo && has(Rewards.phalanx)) damage *= 1f + phalanxBonus * (groups.get(t.id, 1) - 1);
        b.damage *= damage;

        float range = sum(block, r -> r.range);
        if(range > 0f){
            if(b.type.scaleLife){
                b.lifetime = b.type.lifetime * Mathf.clamp(Mathf.dst(b.x, b.y, b.aimX, b.aimY) / Math.max(b.type.range, 1f), 0.05f, 1f + range);
            }else{
                b.lifetime *= 1f + range;
            }
        }

        if(block == Blocks.scatter && has(Rewards.groundFlak)) b.type = groundFlak.get(b.type, b.type);

        if(block == Blocks.hail && has(Rewards.meteor) && t.totalShots % meteorEvery == 0){
            float dst = Mathf.dst(b.x, b.y, b.aimX, b.aimY);
            meteorType.create(t, t.team, b.x, b.y, Angles.angle(b.x, b.y, b.aimX, b.aimY), -1f, 1f, Mathf.clamp(dst / meteorType.range, 0.1f, 2f), null, null, b.aimX, b.aimY);
            b.remove();
            return;
        }

        if(sum(block, r -> r.splash) > 0f || (block == Blocks.hail && has(Rewards.shockField))){
            Tracked tr = new Tracked();
            tr.bullet = b;
            tr.id = b.id;
            tr.block = block;
            tr.x = b.x;
            tr.y = b.y;
            tracked.add(tr);
        }
    }

    static void ended(Tracked t){
        Team team = state.rules.defaultTeam;
        BulletType type = t.bullet.type;
        float splash = sum(t.block, r -> r.splash);
        if(splash > 0f && type.splashDamage > 0f){
            Damage.damage(team, t.x, t.y, type.splashDamageRadius, type.splashDamage * splash, type.collidesAir, type.collidesGround);
        }
        if(t.block == Blocks.hail && has(Rewards.shockField)){
            if(fields.size >= fieldMax) fields.remove(0);
            fields.add(new float[]{t.x, t.y, fieldTime});
        }
    }

    static void updateFields(){
        if(fields.isEmpty() || (fieldTimer += Time.delta) < 20f) return;
        for(int i = fields.size - 1; i >= 0; i--){
            float[] f = fields.get(i);
            f[2] -= fieldTimer;
            if(f[2] <= 0f){
                fields.remove(i);
                continue;
            }
            Tmp.v1.rnd(Mathf.random(fieldRadius));
            Fx.hitLancer.at(f[0] + Tmp.v1.x, f[1] + Tmp.v1.y);
            Units.nearbyEnemies(state.rules.defaultTeam, f[0], f[1], fieldRadius, u -> {
                if(u.isFlying()) return;
                Lightning.create(state.rules.defaultTeam, Pal.lancerLaser, 14f, f[0], f[1], Angles.angle(f[0], f[1], u.x, u.y), Math.max(3, (int)(u.dst(f[0], f[1]) / 8f) + 2));
            });
        }
        fieldTimer = 0f;
    }

    static void linkDuos(Team team){
        groups.clear();
        IntSet visited = new IntSet();
        Seq<Building> group = new Seq<>(), queue = new Seq<>();
        for(Building start : team.data().buildings){
            if(start.block != Blocks.duo || visited.contains(start.id)) continue;
            group.clear();
            queue.clear();
            queue.add(start);
            visited.add(start.id);
            while(queue.any()){
                Building b = queue.pop();
                group.add(b);
                for(Building near : b.proximity){
                    if(near.block == Blocks.duo && near.team == team && visited.add(near.id)) queue.add(near);
                }
            }
            int size = Math.min(group.size, phalanxMax);
            for(Building b : group) groups.put(b.id, size);
        }
    }

    public static boolean phoenixClaims(Building build){
        return build.block == Blocks.scorch && build.team == state.rules.defaultTeam && has(Rewards.phoenix);
    }

    public static boolean willRebuild(Building build){
        return phoenixClaims(build) || BlockMods.resurrects(build);
    }

    public static void scheduleRebuild(Tile tile, Block block, Team team, int rotation, float time){
        Rebuild r = new Rebuild();
        r.block = block;
        r.team = team;
        r.rotation = rotation;
        r.time = time;
        rebuilding.put(tile.pos(), r);
    }

    static void destroyed(Building build){
        if(!phoenixClaims(build)) return;
        float x = build.x, y = build.y;
        Damage.damage(build.team, x, y, 4f * tilesize, 120f, true, true);
        Units.nearbyEnemies(build.team, x, y, 4f * tilesize, u -> u.apply(StatusEffects.burning, 60f * 8f));
        Fx.massiveExplosion.at(x, y);
        Fx.fireballsmoke.at(x, y);
        scheduleRebuild(build.tile, build.block, build.team, build.rotation, phoenixTime);
    }

    //the scaffold keeps the spot taken while the Scorch is down; if it is broken too, it comes back with the same progress
    static void updateRebuilds(){
        if(rebuilding.isEmpty()) return;
        IntSeq keys = new IntSeq();
        for(var e : rebuilding) keys.add(e.key);
        for(int i = 0; i < keys.size; i++){
            int pos = keys.get(i);
            Tile tile = world.tile(pos);
            Rebuild r = rebuilding.get(pos);
            if(tile == null){
                rebuilding.remove(pos);
                continue;
            }
            if(tile.build == null){
                if(!Build.validPlace(r.block, r.team, tile.x, tile.y, r.rotation, false, false) && tile.block() != Blocks.air) continue;
                tile.setBlock(ConstructBlock.get(r.block.size), r.team, r.rotation);
                if(tile.build instanceof ConstructBuild c) c.setConstruct(Blocks.air, r.block);
                continue;
            }
            if(!(tile.build instanceof ConstructBuild c) || c.current != r.block){
                rebuilding.remove(pos);
                continue;
            }
            r.progress += Time.delta;
            c.progress = Math.min(r.progress / r.time, 0.999f);
            if(r.progress >= r.time){
                rebuilding.remove(pos);
                ConstructBlock.constructFinish(tile, r.block, null, (byte)r.rotation, r.team, null);
                Fx.spawnShockwave.at(tile.worldx(), tile.worldy(), 20f);
            }
        }
    }

    static void enemyDied(Unit unit){
        if(skyfallen.remove(unit.id) && has(Rewards.skyfall)){
            Damage.damage(state.rules.defaultTeam, unit.x, unit.y, 3f * tilesize + unit.hitSize / 2f, unit.maxHealth, false, true);
            Fx.massiveExplosion.at(unit.x, unit.y);
            Effect.shake(4f, 20f, unit.x, unit.y);
        }
        if(has(Rewards.wildfire) && unit.hasEffect(StatusEffects.burning)){
            Fx.fireballsmoke.at(unit.x, unit.y);
            Fx.blastExplosion.at(unit.x, unit.y);
            Units.nearbyEnemies(state.rules.defaultTeam, unit.x, unit.y, wildfireRadius, u -> {
                u.apply(StatusEffects.burning, 60f * 6f);
                u.damage(15f);
                Fx.fire.at(u.x, u.y);
            });
        }
    }

    public static void reset(){
        tracked.clear();
        seenBullets.clear();
        firing.clear();
        idle.clear();
        groups.clear();
        rebuilding.clear();
        fields.clear();
        skyfallen.clear();
    }

    static void registerEvents(){
        Events.run(Trigger.afterGameUpdate, () -> {
            if(RogueRun.active() && !state.isPaused()) update();
        });

        Events.on(WorldLoadBeginEvent.class, e -> reset());

        Events.on(BlockDestroyEvent.class, e -> {
            if(RogueRun.active() && e.tile.build != null) destroyed(e.tile.build);
        });

        Events.on(BlockBuildBeginEvent.class, e -> {
            if(e.breaking) rebuilding.remove(e.tile.pos());
        });

        Events.on(UnitBulletDestroyEvent.class, e -> {
            if(!RogueRun.active() || e.unit == null || e.bullet == null || !e.unit.isFlying() || e.unit.team != state.rules.waveTeam) return;
            if(e.bullet.owner instanceof Building b && b.block == Blocks.scatter && b.team == state.rules.defaultTeam) skyfallen.add(e.unit.id);
        });

        Events.on(UnitDestroyEvent.class, e -> {
            if(RogueRun.active() && e.unit.team == state.rules.waveTeam) enemyDied(e.unit);
        });

        Events.on(UnitDamageEvent.class, e -> {
            if(!RogueRun.active() || e.unit.team != state.rules.waveTeam || !has(Rewards.inferno)) return;
            if(e.bullet != null && e.bullet.owner instanceof Building b && b.block == Blocks.scorch && b.team == state.rules.defaultTeam){
                e.unit.apply(StatusEffects.burning, 60f * 8f);
            }
        });
    }
}

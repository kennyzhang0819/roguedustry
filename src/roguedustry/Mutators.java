package roguedustry;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.defense.turrets.Turret.*;

import static mindustry.Vars.*;

public class Mutators{

    public static class Mutator{
        public final String id, name, description;
        public final int points;

        Mutator(String id, String name, int points, String description){
            this.id = id;
            this.name = name;
            this.points = points;
            this.description = description;
        }
    }

    public static final Mutator
    speedFreaks = new Mutator("speed-freaks", "Speed Freaks", 2, "Enemy units move twice as fast."),
    lifeLeech = new Mutator("life-leech", "Life Leech", 2, "Enemies heal when they deal damage. Extra healing becomes a shield."),
    longRange = new Mutator("long-range", "Long Range", 3, "Enemy units and turrets shoot from 50% farther away."),
    justDie = new Mutator("just-die", "Just Die!", 4, "Enemy units come back to life once, 3 seconds after they die.");

    public static final Seq<Mutator> all = Seq.with(speedFreaks, lifeLeech, longRange, justDie);

    static final float rangeMultiplier = 1.5f, leech = 0.5f, reviveDelay = 60f * 3f;

    public static StatusEffect speedEffect;

    static final IntSet revived = new IntSet(), freshBullets = new IntSet();
    static final ObjectFloatMap<BulletType> savedBulletRange = new ObjectFloatMap<>();
    static float savedRange, savedMaxRange, cleanupTimer;

    public static void load(){
        speedEffect = new StatusEffect("speed-freak"){{
            speedMultiplier = 2f;
            permanent = true;
            color = Color.valueOf("ff7b5c");
        }};
    }

    public static @Nullable Mutator get(String id){
        return all.find(m -> m.id.equals(id));
    }

    public static int terror(int missionsCleared){
        return missionsCleared;
    }

    public static int budget(int terror){
        return terror * 2;
    }

    public static Seq<Mutator> roll(int terror, long seed){
        Rand rand = new Rand(seed);
        Seq<Mutator> pool = all.copy(), out = new Seq<>();
        int left = budget(terror);
        while(pool.any()){
            Mutator m = pool.remove(rand.random(pool.size - 1));
            if(m.points <= left){
                out.add(m);
                left -= m.points;
            }
        }
        return out;
    }

    static boolean on(Mutator m){
        return RogueRun.active() && RogueRun.current.mutators.contains(m);
    }

    static void update(){
        boolean speed = on(speedFreaks), range = on(longRange);
        if(!speed && !range) return;

        for(Unit unit : state.rules.waveTeam.data().units){
            if(speed && !unit.hasEffect(speedEffect)) unit.apply(speedEffect);
            if(range && unit.controller() instanceof AIController ai && !(ai instanceof CommandAI) && !(ai instanceof LongRangeAI)){
                unit.controller(new LongRangeAI(ai));
            }
        }

        if(!range) return;

        if((cleanupTimer += Time.delta) > 600f){
            cleanupTimer = 0f;
            freshBullets.clear();
        }
        for(Bullet b : Groups.bullet){
            if(b.team != state.rules.waveTeam || b.time > Time.delta * 1.5f || b.type.speed < 0.1f || !freshBullets.add(b.id)) continue;
            if(b.type.scaleLife){
                b.lifetime = b.type.lifetime * Mathf.clamp(Mathf.dst(b.originX, b.originY, b.aimX, b.aimY) / Math.max(b.type.range, 1f), 0.1f, rangeMultiplier);
            }else{
                b.lifetime *= rangeMultiplier;
            }
        }

        for(var build : state.rules.waveTeam.data().buildings){
            if(build instanceof TurretBuild t && (t.target == null || !t.target.isAdded())){
                Turret block = (Turret)t.block;
                t.target = mindustry.entities.Units.closestTarget(t.team, t.x, t.y, t.range() * rangeMultiplier,
                    u -> u.checkTarget(block.targetAir, block.targetGround), b -> block.targetGround);
            }
        }
    }

    static void widen(UnitType type){
        savedRange = type.range;
        savedMaxRange = type.maxRange;
        type.range *= rangeMultiplier;
        type.maxRange *= rangeMultiplier;
        for(Weapon weapon : type.weapons){
            if(!savedBulletRange.containsKey(weapon.bullet)){
                savedBulletRange.put(weapon.bullet, weapon.bullet.range);
                weapon.bullet.range *= rangeMultiplier;
            }
        }
    }

    static void restore(UnitType type){
        type.range = savedRange;
        type.maxRange = savedMaxRange;
        for(var e : savedBulletRange){
            e.key.range = e.value;
        }
        savedBulletRange.clear();
    }

    //unit and bullet ranges are shared with the player's units, so they are only widened while one enemy unit thinks
    static class LongRangeAI extends AIController{
        final AIController inner;

        LongRangeAI(AIController inner){
            this.inner = inner;
        }

        @Override
        public void unit(Unit unit){
            super.unit(unit);
            inner.unit(unit);
        }

        @Override
        public void updateUnit(){
            widen(unit.type);
            try{
                inner.updateUnit();
            }finally{
                restore(unit.type);
            }
        }

        @Override
        public void removed(Unit unit){
            inner.removed(unit);
        }

        @Override
        public boolean isLogicControllable(){
            return inner.isLogicControllable();
        }
    }

    static void onDeath(Unit unit){
        if(!on(justDie) || unit.team != state.rules.waveTeam || !unit.isEnemy() || revived.remove(unit.id)) return;
        UnitType type = unit.type;
        float x = unit.x, y = unit.y;
        String run = state.rules.tags.get(RogueRun.tag);
        Time.run(reviveDelay, () -> {
            if(!on(justDie) || state.gameOver || !run.equals(state.rules.tags.get(RogueRun.tag))) return;
            Fx.spawnShockwave.at(x, y, 20f);
            Unit back = type.spawn(state.rules.waveTeam, x, y);
            revived.add(back.id);
        });
    }

    static void onHit(Bullet bullet, float damage){
        if(!on(lifeLeech) || bullet.team != state.rules.waveTeam || damage <= 0f) return;
        float amount = damage * leech;
        if(bullet.owner instanceof Unit u && u.isValid()){
            float heal = Math.min(amount, u.maxHealth - u.health);
            u.heal(heal);
            u.shield = Math.min(u.shield + amount - heal, u.maxHealth * 0.5f);
        }else if(bullet.owner instanceof Building b && b.isValid()){
            b.heal(amount);
        }
    }

    static void registerEvents(){
        Events.on(UnitDestroyEvent.class, e -> onDeath(e.unit));

        Events.on(UnitDamageEvent.class, e -> {
            if(e.unit.team == state.rules.defaultTeam) onHit(e.bullet, e.bullet.damage);
        });

        Events.on(BuildDamageEvent.class, e -> {
            if(e.build.team == state.rules.defaultTeam) onHit(e.source, e.source.type.buildingDamage(e.source));
        });

        Events.on(WorldLoadEvent.class, e -> revived.clear());

        Events.run(Trigger.afterGameUpdate, () -> {
            if(RogueRun.active()) update();
        });
    }
}

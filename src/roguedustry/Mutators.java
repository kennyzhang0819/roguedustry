package roguedustry;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import arc.graphics.g2d.*;
import java.lang.reflect.*;
import java.util.*;
import mindustry.ai.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.abilities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.blocks.units.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.defense.turrets.Turret.*;

import static mindustry.Vars.*;

public class Mutators{

    public static class Mutator{
        public final String id, name, description;

        Mutator(String id, String name, String description){
            this.id = id;
            this.name = name;
            this.description = description;
        }

        public TextureRegion icon(){
            return Core.atlas.find("roguedustry-" + id);
        }
    }

    public static final Mutator
    speedFreaks = new Mutator("speed-freaks", "Speed Freaks", "Enemy units move twice as fast."),
    lifeLeech = new Mutator("life-leech", "Life Leech", "Enemies heal when they deal damage. Extra healing becomes a shield."),
    longRange = new Mutator("long-range", "Long Range", "Enemy units and turrets shoot from 50% farther away."),
    justDie = new Mutator("just-die", "Just Die!", "Enemy units come back to life once, 3 seconds after they die."),
    selfDestruction = new Mutator("self-destruction", "Self Destruction", "Enemy units explode when they die, damaging your nearby structures and units."),
    mutuallyAssured = new Mutator("mutually-assured-destruction", "Mutually Assured Destruction", "Enemy bosses set off a huge explosion shortly after they die."),
    walkingInfested = new Mutator("walking-infested", "Walking Infested", "Enemy ground units release the first unit of their line when they die. Bigger units release more."),
    alienIncubation = new Mutator("alien-incubation", "Alien Incubation", "Enemy air units release the first unit of their line when they die. Bigger units release more."),
    barrier = new Mutator("barrier", "Barrier", "The first time an enemy unit is hit, it gets a personal shield worth half its health for 5 seconds."),
    avenger = new Mutator("avenger", "Avenger", "When an enemy dies, nearby enemies grow bigger, faster, tougher and stronger. Stacks up to 5 times."),
    inspiration = new Mutator("inspiration", "Inspiration", "Enemy bosses make nearby enemies move, shoot and hit harder."),
    evasiveManeuvers = new Mutator("evasive-maneuvers", "Evasive Maneuvers", "Damaged enemy units teleport a few tiles forward. Each unit can do this every 4 seconds."),
    transmutation = new Mutator("transmutation", "Transmutation", "Enemy units that hit you have a small chance to turn into the next, stronger unit of their line."),
    aggressiveDeployment = new Mutator("aggressive-deployment", "Aggressive Deployment", "An extra enemy group, half the size of the last wave, arrives between waves.");

    public static final Seq<Mutator> all = Seq.with(
        speedFreaks, lifeLeech, longRange, justDie,
        selfDestruction, mutuallyAssured, walkingInfested, alienIncubation,
        barrier, avenger, inspiration, evasiveManeuvers, transmutation, aggressiveDeployment
    );

    static final float rangeMultiplier = 1.5f, leech = 0.5f, reviveDelay = 60f * 3f, transmuteChance = 0.002f;
    static final int maxAvenger = 5;

    public static StatusEffect speedEffect, inspiredEffect;
    public static final StatusEffect[] avengerEffects = new StatusEffect[maxAvenger];

    static final IntSet revived = new IntSet(), freshBullets = new IntSet(), released = new IntSet(), barrierUsed = new IntSet();
    static final IntIntMap avengerStacks = new IntIntMap();
    static final IntFloatMap lastJump = new IntFloatMap();
    static final ObjectFloatMap<BulletType> savedBulletRange = new ObjectFloatMap<>();
    static final ObjectMap<UnitType, UnitType> previous = new ObjectMap<>(), nextTier = new ObjectMap<>();
    static float savedRange, savedMaxRange, cleanupTimer, auraTimer, deployTimer;
    static boolean deployed;

    public static void load(){
        speedEffect = new StatusEffect("speed-freak"){{
            speedMultiplier = 2f;
            permanent = true;
            color = Color.valueOf("ff7b5c");
        }};
        inspiredEffect = new StatusEffect("inspired"){{
            speedMultiplier = 1.3f;
            reloadMultiplier = 1.3f;
            damageMultiplier = 1.15f;
            color = Color.valueOf("ffd37f");
        }};
        for(int i = 0; i < maxAvenger; i++){
            float bonus = 0.1f * (i + 1), grow = 1f + 0.08f * (i + 1);
            avengerEffects[i] = new StatusEffect("avenger-" + (i + 1)){{
                speedMultiplier = reloadMultiplier = damageMultiplier = healthMultiplier = 1f + bonus;
                permanent = true;
                color = Color.scarlet;
            }

                @Override
                public void update(Unit unit, StatusEntry entry){
                    super.update(unit, entry);
                    Grow ability = Grow.of(unit);
                    ability.scale = grow;
                    unit.hitSize = unit.type.hitSize * grow;
                }

                @Override
                public void draw(Unit unit, float time){
                    Grow.begin(unit);
                }
            };
        }
    }

    static void buildFamilies(){
        if(!previous.isEmpty()) return;
        for(var block : content.blocks()){
            if(block instanceof Reconstructor r){
                for(UnitType[] pair : r.upgrades){
                    previous.put(pair[1], pair[0]);
                    nextTier.put(pair[0], pair[1]);
                }
            }
        }
    }

    static UnitType firstOfLine(UnitType type){
        buildFamilies();
        while(previous.containsKey(type)) type = previous.get(type);
        return type;
    }

    static int tier(UnitType type){
        buildFamilies();
        int tier = 1;
        while(previous.containsKey(type)){
            type = previous.get(type);
            tier++;
        }
        return tier;
    }

    public static @Nullable Mutator get(String id){
        return all.find(m -> m.id.equals(id));
    }

    public static Seq<Mutator> offer(Seq<Mutator> owned, Rand rand){
        Seq<Mutator> pool = all.select(m -> !owned.contains(m)), out = new Seq<>();
        while(out.size < 3 && pool.any()){
            out.add(pool.remove(rand.random(pool.size - 1)));
        }
        return out;
    }

    static void lifted(Mutator m){
        if(m == speedFreaks && state.isGame()){
            for(Unit unit : state.rules.waveTeam.data().units) unit.unapply(speedEffect);
        }
    }

    static boolean on(Mutator m){
        return RogueRun.active() && RogueRun.current.mutators.contains(m);
    }

    static void update(){
        updateDeployment();

        if(on(inspiration) && (auraTimer += Time.delta) >= 30f){
            auraTimer = 0f;
            for(Unit boss : state.rules.waveTeam.data().units){
                if(!boss.hasEffect(StatusEffects.boss)) continue;
                Units.nearby(state.rules.waveTeam, boss.x, boss.y, 12f * tilesize, u -> u.apply(inspiredEffect, 60f));
            }
        }

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
                t.target = Units.closestTarget(t.team, t.x, t.y, t.range() * rangeMultiplier,
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
            if(!on(longRange)){
                inner.updateUnit();
                return;
            }
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

    static void updateDeployment(){
        if(!on(aggressiveDeployment) || deployed || state.wave < 1 || !state.rules.waves) return;
        float wait = state.rules.waveTimer ? state.rules.waveSpacing * 0.5f : 60f * 45f;
        if((deployTimer += Time.delta) < wait) return;
        deployed = true;

        int count = 0;
        for(SpawnGroup group : state.rules.spawns){
            int amount = Mathf.ceil(group.getSpawned(state.wave - 1) / 2f);
            for(Tile spawn : spawner.getSpawns()){
                if(group.spawn != -1 && group.spawn != spawn.pos()) continue;
                for(int i = 0; i < amount; i++){
                    Tmp.v1.rnd(tilesize * 2f);
                    spawner.spawnUnit(group, spawn.worldx() + Tmp.v1.x, spawn.worldy() + Tmp.v1.y);
                    count++;
                }
            }
        }
        if(count > 0 && !headless) ui.showInfoToast("[scarlet]Aggressive Deployment:[] " + count + " extra enemies incoming", 4f);
    }

    static void onWave(){
        deployTimer = 0f;
        deployed = false;
    }

    static void later(float delay, Runnable action){
        var sector = state.rules.sector;
        Time.run(delay, () -> {
            if(RogueRun.active() && !state.gameOver && state.rules.sector == sector) action.run();
        });
    }

    static void onDeath(Unit unit){
        if(!RogueRun.active() || unit.team != state.rules.waveTeam || !unit.isEnemy()) return;
        UnitType type = unit.type;
        float x = unit.x, y = unit.y;
        boolean child = released.remove(unit.id), wasRevived = revived.remove(unit.id);
        avengerStacks.remove(unit.id, 0);

        if(on(justDie) && !wasRevived){
            later(reviveDelay, () -> {
                Fx.spawnShockwave.at(x, y, 20f);
                revived.add(type.spawn(state.rules.waveTeam, x, y).id);
            });
        }

        if(on(selfDestruction)){
            float radius = unit.hitSize / 2f + 2f * tilesize;
            Damage.damage(state.rules.waveTeam, x, y, radius, 15f + unit.maxHealth * 0.2f);
            (unit.hitSize > 20f ? Fx.massiveExplosion : Fx.blastExplosion).at(x, y);
        }

        if(on(mutuallyAssured) && unit.hasEffect(StatusEffects.boss)){
            Fx.spawnShockwave.at(x, y, 12f * tilesize);
            float damage = unit.maxHealth * 0.5f;
            later(90f, () -> {
                Damage.damage(state.rules.waveTeam, x, y, 12f * tilesize, damage);
                Fx.reactorExplosion.at(x, y);
                Effect.shake(6f, 30f, x, y);
            });
        }

        boolean releases = !child && (unit.isFlying() ? on(alienIncubation) : on(walkingInfested));
        if(releases){
            UnitType first = firstOfLine(type);
            int amount = Math.min(tier(type), 4);
            for(int i = 0; i < amount; i++){
                Tmp.v1.rnd(unit.hitSize / 2f);
                Unit spawned = first.spawn(state.rules.waveTeam, x + Tmp.v1.x, y + Tmp.v1.y);
                released.add(spawned.id);
            }
        }

        if(on(avenger)){
            Units.nearby(state.rules.waveTeam, x, y, 8f * tilesize, other -> {
                int stacks = avengerStacks.get(other.id, 0);
                if(stacks >= maxAvenger) return;
                if(stacks > 0) other.unapply(avengerEffects[stacks - 1]);
                other.apply(avengerEffects[stacks]);
                avengerStacks.put(other.id, stacks + 1);
            });
        }
    }

    static void onEnemyHit(Unit unit){
        if(on(barrier) && barrierUsed.add(unit.id)){
            float grant = unit.maxHealth * 0.5f;
            unit.shield += grant;
            Fx.shieldApply.at(unit.x, unit.y, 0f, unit);
            later(60f * 5f, () -> {
                if(unit.isValid()) unit.shield = Math.max(0f, unit.shield - grant);
            });
        }

        if(on(evasiveManeuvers) && unit.isValid() && Time.time - lastJump.get(unit.id, -9999f) > 60f * 4f){
            lastJump.put(unit.id, Time.time);
            float fromX = unit.x, fromY = unit.y;
            if(unit.isFlying()){
                Building core = state.rules.defaultTeam.core();
                if(core == null) return;
                Tmp.v1.set(core).sub(unit).limit(5f * tilesize);
                unit.set(unit.x + Tmp.v1.x, unit.y + Tmp.v1.y);
            }else{
                Tile tile = unit.tileOn();
                if(tile == null) return;
                var field = pathfinder.getField(unit.team, unit.type.flowfieldPathType, Pathfinder.fieldCore);
                for(int i = 0; i < 5; i++){
                    Tile next = field.getNextTile(tile);
                    if(next == null || next == tile) break;
                    tile = next;
                }
                unit.set(tile.worldx(), tile.worldy());
            }
            unit.snapInterpolation();
            Fx.teleportOut.at(fromX, fromY, unit.hitSize / 2f);
            Fx.teleportOut.at(unit.x, unit.y, unit.hitSize / 2f);
        }
    }

    static void onEnemyAttack(Unit unit){
        if(!on(transmutation) || !unit.isValid() || !Mathf.chance(transmuteChance)) return;
        buildFamilies();
        UnitType next = nextTier.get(unit.type);
        if(next == null) return;
        float fraction = unit.health / unit.maxHealth;
        Unit upgraded = next.spawn(state.rules.waveTeam, unit.x, unit.y);
        upgraded.rotation = unit.rotation;
        upgraded.health = upgraded.maxHealth * fraction;
        Fx.spawnShockwave.at(unit.x, unit.y, upgraded.hitSize * 2f);
        unit.remove();
    }

    //units have no size of their own, so a grown unit's sprites are stretched around its center right after it is drawn
    static class Grow extends Ability{
        static Field verts, offset, sorted;
        static @Nullable Unit drawing;
        static int start;

        float scale = 1f;

        {
            display = false;
        }

        static Grow of(Unit unit){
            for(Ability a : unit.abilities){
                if(a instanceof Grow g) return g;
            }
            Grow g = new Grow();
            Ability[] next = Arrays.copyOf(unit.abilities, unit.abilities.length + 1);
            next[next.length - 1] = g;
            unit.abilities = next;
            return g;
        }

        static boolean ready(){
            if(!(Core.batch instanceof SpriteBatch)) return false;
            try{
                if(verts == null){
                    verts = SpriteBatch.class.getDeclaredField("requestVerts");
                    offset = SpriteBatch.class.getDeclaredField("requestVertOffset");
                    sorted = SpriteBatch.class.getDeclaredField("sort");
                    verts.setAccessible(true);
                    offset.setAccessible(true);
                    sorted.setAccessible(true);
                }
                return sorted.getBoolean(Core.batch);
            }catch(Throwable e){
                return false;
            }
        }

        static void begin(Unit unit){
            drawing = null;
            if(!ready()) return;
            try{
                start = offset.getInt(Core.batch);
                drawing = unit;
            }catch(Throwable ignored){
            }
        }

        @Override
        public void draw(Unit unit){
            if(drawing != unit || scale == 1f) return;
            drawing = null;
            try{
                int end = offset.getInt(Core.batch);
                float[] v = (float[])verts.get(Core.batch);
                for(int i = start; i < end; i += SpriteBatch.VERTEX_SIZE){
                    v[i] = unit.x + (v[i] - unit.x) * scale;
                    v[i + 1] = unit.y + (v[i + 1] - unit.y) * scale;
                }
            }catch(Throwable ignored){
            }
        }

        @Override
        public Ability copy(){
            return new Grow();
        }
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
            if(!RogueRun.active()) return;
            if(e.unit.team == state.rules.defaultTeam){
                onHit(e.bullet, e.bullet.damage);
                if(e.bullet.owner instanceof Unit attacker && attacker.team == state.rules.waveTeam) onEnemyAttack(attacker);
            }else if(e.unit.team == state.rules.waveTeam){
                onEnemyHit(e.unit);
            }
        });

        Events.on(BuildDamageEvent.class, e -> {
            if(!RogueRun.active() || e.build.team != state.rules.defaultTeam) return;
            onHit(e.source, e.source.type.buildingDamage(e.source));
            if(e.source.owner instanceof Unit attacker && attacker.team == state.rules.waveTeam) onEnemyAttack(attacker);
        });

        Events.on(WaveEvent.class, e -> onWave());

        Events.on(WorldLoadEvent.class, e -> {
            revived.clear();
            released.clear();
            barrierUsed.clear();
            avengerStacks.clear();
            lastJump.clear();
            onWave();
        });

        Events.run(Trigger.afterGameUpdate, () -> {
            if(RogueRun.active()) update();
        });
    }
}

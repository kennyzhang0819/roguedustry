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
import mindustry.ctype.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.blocks.units.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.defense.turrets.Turret.*;

import static mindustry.Vars.*;

public class Curses{

    public static class Curse{
        public final String id, name, description;

        Curse(String id, String name, String description){
            this.id = id;
            this.name = name;
            this.description = description;
        }

        public TextureRegion icon(){
            return Core.atlas.find("roguedustry-" + id);
        }
    }

    public static final Curse
    speedFreaks = new Curse("speed-freaks", "Speedy", "Enemy units move twice as fast and can't be slowed."),
    lifeLeech = new Curse("life-leech", "Bloodthirst", "Enemies heal from the damage they deal."),
    longRange = new Curse("long-range", "Far Sight", "Enemies shoot 50% farther."),
    justDie = new Curse("just-die", "Reconstruction", "Enemy units revive once."),
    selfDestruction = new Curse("self-destruction", "Volatile", "Enemy units explode when they die."),
    mutuallyAssured = new Curse("mutually-assured-destruction", "Dead Man's Switch", "Tier 4 and Tier 5 enemies blow up after they die."),
    alienIncubation = new Curse("alien-incubation", "Mitosis", "Enemy units spawn Tier 1 units when they die."),
    barrier = new Curse("barrier", "Reactive Plating", "Enemy units get a short shield when first hit."),
    avenger = new Curse("avenger", "Grudge", "Each enemy death makes nearby enemies stronger."),
    inspiration = new Curse("inspiration", "Warlord", "Tier 4 and Tier 5 enemies make nearby enemies stronger."),
    evasiveManeuvers = new Curse("evasive-maneuvers", "Blink", "Damaged enemy units teleport forward."),
    transmutation = new Curse("transmutation", "Evolution", "Enemy units that hit you may turn into the next tier."),
    aggressiveDeployment = new Curse("aggressive-deployment", "Reinforcements", "Extra enemies arrive between waves."),
    hungryMechs = new Curse("hungry-mechs", "Hungry Mechs", "Some enemy units eat their allies and grow huge."),
    mechVirus = new Curse("mech-virus", "Mech Virus", "Killing an infected enemy infects your nearest turret, which then wastes away."),
    overshields = new Curse("overshields", "Overshields", "Enemy shields are 5 times as strong."),
    armoredSwarms = new Curse("armored-swarms", "Armored Swarms", "Tier 1 to 3 enemy units get 5 armor."),
    leadership = new Curse("leadership", "Leadership", "Enemies near a Tier 4 or Tier 5 enemy take at most 10 damage from any one hit."),
    shieldTowers = new Curse("shield-towers", "Shield Towers", "Enemy shield towers keep appearing along the enemy's path."),
    conquest = new Curse("conquest", "Conquest", "Turrets the enemy destroys come back on the enemy's side.");

    public static final Seq<Curse> all = Seq.with(
        speedFreaks, lifeLeech, longRange, justDie,
        selfDestruction, mutuallyAssured, alienIncubation,
        barrier, avenger, inspiration, evasiveManeuvers, transmutation, aggressiveDeployment,
        hungryMechs, mechVirus, overshields, armoredSwarms, leadership, shieldTowers, conquest
    );

    static final float rangeMultiplier = 1.5f, leech = 0.5f, reviveDelay = 1f, transmuteChance = 0.002f;
    static final float hungryChance = 0.05f, hungryReach = 6f * 8f, virusChance = 0.01f, virusDrain = 0.05f, virusJump = 5f * 8f,
        overshieldScale = 5f, leadershipCap = 10f, leadershipRange = 15f * 8f, bigShield = 1e7f,
        towerPeriod = 60f * 22.5f, towerHealth = 2000f, towerDome = 1000f, towerGrowth = 1.1f, conquestHealth = 0.6f;
    static final float armoredArmor = 5f;
    static final int hungryMaxMeals = 20, towerMax = 20;
    static final int maxAvenger = 5;

    public static StatusEffect speedEffect, inspiredEffect, hungryEffect, infectedEffect;
    public static final IntSet seen = new IntSet(), carriers = new IntSet(), towers = new IntSet(), conquered = new IntSet(), infected = new IntSet();
    public static final IntIntMap meals = new IntIntMap();
    public static final IntFloatMap enemyShield = new IntFloatMap(), guarded = new IntFloatMap(), towerBuildup = new IntFloatMap(), towerScale = new IntFloatMap();
    static float spawnTimer, towerTimer, virusTimer, hungryTimer, leaderTimer;
    //a hidden power source, added to the power network of each building the curses place, so nothing else of the enemy's gets free power
    public static final Building generator = new Building(){
        @Override
        public float getPowerProduction(){
            return 1e6f;
        }
    };
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
        hungryEffect = new StatusEffect("hungry"){{
            permanent = true;
            color = Color.valueOf("ff8cdb");
        }

            @Override
            public void update(Unit unit, StatusEntry entry){
                super.update(unit, entry);
                Grow.of(unit).scale = 1f + 0.05f * meals.get(unit.id, 0);
            }

            @Override
            public void draw(Unit unit, float time){
                Grow.begin(unit);
            }
        };
        infectedEffect = new StatusEffect("infected"){{
            permanent = true;
            color = Color.valueOf("84f491");
            effect = Fx.sapped;
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

    public static @Nullable Curse get(String id){
        return all.find(m -> m.id.equals(id));
    }

    public static Seq<Curse> offer(Seq<Curse> owned, Rand rand){
        Seq<Curse> pool = all.select(m -> !owned.contains(m)), out = new Seq<>();
        while(out.size < 3 && pool.any()){
            out.add(pool.remove(rand.random(pool.size - 1)));
        }
        return out;
    }

    static void lifted(Curse m){
        if(!state.isGame()) return;
        if(m == speedFreaks){
            for(Unit unit : state.rules.waveTeam.data().units) unit.unapply(speedEffect);
        }
        if(m == shieldTowers) removeAll(towers);
        if(m == conquest) removeAll(conquered);
        if(m == mechVirus) infected.clear();
        if(m == leadership){
            for(Unit unit : state.rules.waveTeam.data().units) unguard(unit);
            guarded.clear();
        }
    }

    static void removeAll(IntSet positions){
        IntSeq list = positions.iterator().toArray();
        for(int i = 0; i < list.size; i++){
            Building b = world.build(list.get(i));
            if(b != null && b.team == state.rules.waveTeam) b.kill();
        }
        positions.clear();
    }

    public static void write(java.io.DataOutput out) throws java.io.IOException{
        for(IntSet set : new IntSet[]{towers, conquered, infected}){
            out.writeInt(set.size);
            IntSet.IntSetIterator it = set.iterator();
            while(it.hasNext) out.writeInt(it.next());
        }
    }

    public static void read(java.io.DataInput in) throws java.io.IOException{
        for(IntSet set : new IntSet[]{towers, conquered, infected}){
            set.clear();
            int n = in.readInt();
            for(int i = 0; i < n; i++) set.add(in.readInt());
        }
    }

    static void onSpawned(Unit unit){
        if(on(armoredSwarms) && tier(unit.type) <= 3) unit.armor += armoredArmor;
        if(on(hungryMechs) && Mathf.chance(hungryChance) && !unit.hasEffect(StatusEffects.boss)) unit.apply(hungryEffect);
        if(on(mechVirus) && Mathf.chance(virusChance)){
            carriers.add(unit.id);
            unit.apply(infectedEffect);
        }
    }

    static void updateNewCurses(){
        Team enemy = state.rules.waveTeam;
        for(Unit unit : enemy.data().units){
            if(seen.add(unit.id)) onSpawned(unit);
        }

        if(on(hungryMechs) && (hungryTimer += Time.delta) >= 60f){
            hungryTimer = 0f;
            for(Unit eater : enemy.data().units.copy()){
                if(!eater.isValid() || !eater.hasEffect(hungryEffect) || meals.get(eater.id, 0) >= hungryMaxMeals) continue;
                Unit prey = Units.closest(enemy, eater.x, eater.y, hungryReach + eater.hitSize / 2f, u -> u != eater && !u.hasEffect(hungryEffect) && !u.hasEffect(StatusEffects.boss) && u.isFlying() == eater.isFlying() && tier(u.type) <= tier(eater.type));
                if(prey == null) continue;
                float gain = prey.maxHealth * 2f;
                eater.maxHealth += gain;
                eater.health += gain;
                eater.applyDynamicStatus().damageMultiplier += prey.type.health / eater.type.health;
                meals.increment(eater.id);
                Fx.dynamicExplosion.at(prey.x, prey.y, prey.hitSize / 16f);
                prey.remove();
            }
        }

        if(on(mechVirus) && (virusTimer += Time.delta) >= 60f){
            virusTimer = 0f;
            IntSeq list = infected.iterator().toArray();
            for(int i = 0; i < list.size; i++){
                int pos = list.get(i);
                Building b = world.build(pos);
                if(b == null || b.team != state.rules.defaultTeam || !(b instanceof TurretBuild)){
                    infected.remove(pos);
                    Tile tile = world.tile(pos);
                    if(tile != null) infectNear(tile.worldx(), tile.worldy());
                    continue;
                }
                Fx.sapped.at(b.x, b.y);
                b.damage(b.maxHealth * virusDrain);
            }
        }

        if(on(overshields)){
            if((spawnTimer += Time.delta) > 600f){
                spawnTimer = 0f;
                enemyShield.clear();
            }
            for(Unit unit : enemy.data().units){
                if(guarded.containsKey(unit.id)) continue;
                float last = enemyShield.get(unit.id, unit.shield);
                if(unit.shield > last) unit.shield = last + (unit.shield - last) * overshieldScale;
                enemyShield.put(unit.id, unit.shield);
            }
        }

        if(on(leadership)){
            if((leaderTimer += Time.delta) >= 15f){
                leaderTimer = 0f;
                Seq<Unit> leaders = enemy.data().units.select(Curses::isLeader);
                for(Unit unit : enemy.data().units){
                    boolean near = !isLeader(unit) && leaders.contains(l -> l.within(unit, leadershipRange));
                    if(near && !guarded.containsKey(unit.id)){
                        guarded.put(unit.id, Math.max(unit.shield, 0f));
                        unit.shield = bigShield;
                    }else if(!near && guarded.containsKey(unit.id)){
                        unguard(unit);
                    }
                }
            }
            for(Unit unit : enemy.data().units){
                if(guarded.containsKey(unit.id)) capHit(unit);
            }
        }

        if(on(shieldTowers) && state.rules.waves && !RogueRun.captured() && (towerTimer += Time.delta) >= towerPeriod){
            towerTimer = 0f;
            raiseTower();
        }

        supply(towers);
        supply(conquered);
        IntSeq list = towers.iterator().toArray();
        for(int i = 0; i < list.size; i++){
            int pos = list.get(i);
            if(!(world.build(pos) instanceof ForceProjector.ForceBuild force) || force.team != enemy){
                towers.remove(pos);
                continue;
            }
            float last = towerBuildup.get(pos, force.buildup), scale = towerScale.get(pos, 1f) * (on(overshields) ? overshieldScale : 1f);
            if(force.buildup > last) force.buildup = last + (force.buildup - last) / scale;
            towerBuildup.put(pos, force.buildup);
        }
    }

    static boolean isLeader(Unit unit){
        return tier(unit.type) >= 4;
    }

    //a hit can kill before any event fires, so guarded units carry a huge shield and their real damage is taken from it with a cap
    public static void capHit(Unit unit){
        float lost = bigShield - unit.shield;
        unit.shield = bigShield;
        if(lost <= 0.001f) return;
        float take = Math.min(lost, leadershipCap), real = guarded.get(unit.id, 0f), absorbed = Math.min(real, take);
        guarded.put(unit.id, real - absorbed);
        unit.health -= take - absorbed;
        if(unit.health <= 0f && !unit.dead) unit.kill();
    }

    static void unguard(Unit unit){
        if(!guarded.containsKey(unit.id)) return;
        unit.shield = guarded.get(unit.id, 0f);
        guarded.remove(unit.id, 0f);
    }

    //buildings the curses place for the enemy have no power or ammo of their own, and only they get it
    static void supply(IntSet positions){
        IntSeq list = positions.iterator().toArray();
        for(int i = 0; i < list.size; i++){
            Building b = world.build(list.get(i));
            if(b == null || b.team != state.rules.waveTeam){
                positions.remove(list.get(i));
                continue;
            }
            if(b.power != null && b.power.graph != null && !b.power.graph.producers.contains(generator, true)) b.power.graph.producers.add(generator);
            if(b instanceof ItemTurret.ItemTurretBuild t && t.totalAmmo < ((ItemTurret)t.block).maxAmmo / 2){
                Item ammo = ((ItemTurret)t.block).ammoTypes.keys().toSeq().peek();
                t.handleStack(ammo, 10, null);
            }
            if(b instanceof LiquidTurret.LiquidTurretBuild t && t.liquids.currentAmount() < t.block.liquidCapacity / 2f){
                Liquid ammo = ((LiquidTurret)t.block).ammoTypes.keys().toSeq().first();
                t.handleLiquid(t, ammo, t.block.liquidCapacity - t.liquids.get(ammo));
            }
        }
    }

    public static void raiseTower(){
        if(towers.size >= towerMax) return;
        Team enemy = state.rules.waveTeam;
        Block block = Blocks.forceProjector;
        Seq<Unit> walkers = enemy.data().units.select(u -> !u.isFlying());
        for(int tries = 0; tries < 30; tries++){
            float ox, oy;
            if(walkers.any()){
                Unit u = walkers.random();
                ox = u.x;
                oy = u.y;
            }else if(Arena.spawnTile(false) != null){
                Tile s = Arena.spawnTile(false);
                ox = s.worldx();
                oy = s.worldy();
            }else return;
            Tmp.v1.rnd(Mathf.random(3f, 12f) * tilesize);
            int x = mindustry.core.World.toTile(ox + Tmp.v1.x), y = mindustry.core.World.toTile(oy + Tmp.v1.y);
            if(!Build.validPlace(block, enemy, x, y, 0, false, true)) continue;
            Tile tile = world.tile(x, y);
            tile.setNet(block, enemy, 0);
            if(tile.build == null) continue;
            float scale = Math.min((float)Math.pow(towerGrowth, Math.max(0, state.wave - 1)), 100f);
            tile.build.maxHealth = towerHealth * scale;
            tile.build.health = tile.build.maxHealth;
            towers.add(tile.build.pos());
            towerScale.put(tile.build.pos(), towerDome * scale / ((ForceProjector)block).shieldHealth);
            Fx.spawnShockwave.at(tile.build.x, tile.build.y, 40f);
            return;
        }
    }

    static void infectNear(float x, float y){
        Building best = null;
        for(Building b : state.rules.defaultTeam.data().buildings){
            if(b instanceof TurretBuild && !infected.contains(b.pos()) && b.within(x, y, virusJump + b.hitSize() / 2f) && (best == null || b.dst(x, y) < best.dst(x, y))) best = b;
        }
        if(best == null) return;
        infected.add(best.pos());
        Fx.sapExplosion.at(best.x, best.y);
    }

    static void conquer(Building build){
        if(!on(conquest) || !(build instanceof TurretBuild) || build.team != state.rules.defaultTeam || TurretMods.willRebuild(build)) return;
        Tile tile = build.tile;
        Block block = build.block;
        int rotation = build.rotation;
        Core.app.post(() -> {
            if(!RogueRun.active() || tile.build != null) return;
            tile.setNet(block, state.rules.waveTeam, rotation);
            if(tile.build == null) return;
            tile.build.health = tile.build.maxHealth * conquestHealth;
            conquered.add(tile.build.pos());
            Fx.spawnShockwave.at(tile.build.x, tile.build.y, 30f);
        });
    }

    static boolean on(Curse m){
        return RogueRun.active() && RogueRun.current.curses.contains(m);
    }

    static void update(){
        updateDeployment();

        if(on(inspiration) && (auraTimer += Time.delta) >= 30f){
            auraTimer = 0f;
            for(Unit boss : state.rules.waveTeam.data().units){
                if(!isLeader(boss)) continue;
                Units.nearby(state.rules.waveTeam, boss.x, boss.y, 12f * tilesize, u -> u.apply(inspiredEffect, 60f));
            }
        }

        updateNewCurses();

        boolean speed = on(speedFreaks), range = on(longRange);
        if(!speed && !range) return;

        for(Unit unit : state.rules.waveTeam.data().units){
            if(speed && !unit.hasEffect(speedEffect)) unit.apply(speedEffect);
            if(speed) unslow(unit);
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

    //slowing statuses keep their other effects, only the slowdown is cancelled
    static void unslow(Unit unit){
        float slow = 1f;
        for(StatusEffect e : content.statusEffects()){
            if(e.speedMultiplier < 1f && unit.hasEffect(e)) slow *= e.speedMultiplier;
        }
        if(slow < 0.999f) unit.applyDynamicStatus().speedMultiplier = 1f / slow;
        else if(unit.hasEffect(StatusEffects.dynamic)) unit.applyDynamicStatus().speedMultiplier = 1f;
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
        for(int n : Arena.waves[Math.min(state.wave - 2, Arena.lastWave - 1)]) count += n == 0 ? 0 : Math.max(1, Math.round(n * 0.5f));
        Arena.release(state.wave - 1, 0.5f);
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
        meals.remove(unit.id, 0);
        guarded.remove(unit.id, 0f);
        if(carriers.remove(unit.id) && on(mechVirus)) infectNear(x, y);

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

        if(on(mutuallyAssured) && isLeader(unit)){
            Fx.spawnShockwave.at(x, y, 12f * tilesize);
            float damage = unit.maxHealth * 0.5f;
            later(90f, () -> {
                Damage.damage(state.rules.waveTeam, x, y, 12f * tilesize, damage);
                Fx.reactorExplosion.at(x, y);
                Effect.shake(6f, 30f, x, y);
            });
        }

        if(!child && on(alienIncubation)){
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
        if(bullet.owner instanceof Unit u && u.isValid() && !guarded.containsKey(u.id)){
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
                if(guarded.containsKey(e.unit.id)) capHit(e.unit);
                onEnemyHit(e.unit);
            }
        });

        Events.on(BuildDamageEvent.class, e -> {
            if(!RogueRun.active() || e.build.team != state.rules.defaultTeam) return;
            onHit(e.source, e.source.type.buildingDamage(e.source));
            if(e.source.owner instanceof Unit attacker && attacker.team == state.rules.waveTeam) onEnemyAttack(attacker);
        });

        Events.on(WaveEvent.class, e -> onWave());

        Events.on(BlockDestroyEvent.class, e -> {
            if(RogueRun.active() && e.tile.build != null) conquer(e.tile.build);
        });

        Events.on(WorldLoadBeginEvent.class, e -> {
            towers.clear();
            conquered.clear();
            infected.clear();
        });

        Events.on(WorldLoadEvent.class, e -> {
            revived.clear();
            released.clear();
            barrierUsed.clear();
            avengerStacks.clear();
            lastJump.clear();
            seen.clear();
            carriers.clear();
            meals.clear();
            enemyShield.clear();
            guarded.clear();
            towerBuildup.clear();
            onWave();
        });

        Events.run(Trigger.afterGameUpdate, () -> {
            if(RogueRun.active()) update();
        });
    }
}

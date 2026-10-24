package mindustrysurvivors;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import arc.math.geom.*;
import mindustry.entities.*;
import mindustry.ctype.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.io.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;

import java.io.*;

import static mindustry.Vars.*;

public class Survivor{
    public static final int maxWeapons = 6, maxPassives = 6, weaponLevels = Weapons.maxLevel, maxGems = 400;
    public static final String startTag = "mindustry-survivors-start";
    public static final float xpRate = 1.25f;
    static final float baseMagnet = 5f * tilesize, orbitSpeed = 0.6f, healAmount = 0.3f, turretSize = 12f;
    static Item[] gemItems;
    static final float[] gemSizes = {6f, 7.5f, 9f};

    public static final Seq<Gun> guns = new Seq<>();
    public static final Seq<Passive> passives = new Seq<>();
    public static Passive might, armor, vitality, recovery, cooldown, area, velocity, amount, swiftness, magnet, growth, luck, evasion, lifesteal, aura;
    public static StatusEffect stats;
    static final float auraDps = 60f, maxedDamage = 0.05f;
    static float maxedBonus;
    static float auraTimer;

    public static final Seq<Gun> ownedGuns = new Seq<>();
    public static final ObjectIntMap<Gun> gunLevel = new ObjectIntMap<>();
    public static final Seq<Passive> ownedPassives = new Seq<>();
    public static final ObjectIntMap<Passive> passiveLevel = new ObjectIntMap<>();
    public static final Seq<Choice> offer = new Seq<>();
    static final FloatSeq gemX = new FloatSeq(), gemY = new FloatSeq(), kitX = new FloatSeq(), kitY = new FloatSeq(), magX = new FloatSeq(), magY = new FloatSeq();
    static final float kitChance = 0.015f, kitHeal = 0.5f, magnetChance = 0.001f;
    static final int maxKits = 3, maxMagnets = 3;
    static final Color magnetGlow = Color.valueOf("f25555");
    static final IntSeq gemValue = new IntSeq();
    static final BoolSeq gemPulled = new BoolSeq();
    static @Nullable Seq<Weapon> ownWeapons;
    static boolean spawned;
    static float orbit;
    static final float[] aim = new float[maxWeapons], pointReload = new float[maxWeapons], jammed = new float[maxWeapons];
    static int shotDown;
    static final Seq<Unit>[] held = new Seq[maxWeapons];
    static{
        for(int i = 0; i < held.length; i++) held[i] = new Seq<>();
    }

    public static class Gun{
        public final BaseTurret turret;
        public final @Nullable BulletType bullet;

        Gun(BaseTurret turret, @Nullable BulletType bullet){
            this.turret = turret;
            this.bullet = bullet;
        }
    }

    public static class Passive{
        public final String id, name;
        public final String[] steps;
        public final UnlockableContent icon;
        public final int maxLevel;

        Passive(String id, Block icon, String step, int maxLevel){
            this(id, icon, maxLevel, step);
        }

        //a passive with one text adds that effect again every level; one with several texts gives a different effect at each level
        Passive(String id, Block icon, int maxLevel, String... steps){
            this.id = id;
            this.icon = icon;
            this.name = icon.localizedName;
            this.steps = steps;
            this.maxLevel = maxLevel;
            passives.add(this);
        }

        public String step(int level){
            return steps[Mathf.clamp(level - 1, 0, steps.length - 1)];
        }
    }

    public static class Choice{
        public @Nullable Gun gun;
        public @Nullable Passive passive;

        public TextureRegion icon(){
            return gun != null ? gun.turret.uiIcon : passive != null ? passive.icon.uiIcon : Icon.add.getRegion();
        }

        public @Nullable TextureRegion stepIcon(){
            if(gun == null) return null;
            int next = nextLevel();
            if(next == 1){
                UnlockableContent ammo = Weapons.firstAmmo(gun.turret);
                return ammo == null ? null : ammo.uiIcon;
            }
            Weapons.Step step = Weapons.step(gun.turret, next);
            return step == null || step.icon == null ? null : step.icon.uiIcon;
        }

        public String name(){
            return gun != null ? gun.turret.localizedName : passive != null ? passive.name : "Repair";
        }

        public int nextLevel(){
            return gun != null ? gunLevel.get(gun, 0) + 1 : passive != null ? passiveLevel.get(passive, 0) + 1 : 0;
        }

        public String description(){
            int next = nextLevel();
            if(gun != null){
                if(next > 1) return Weapons.step(gun.turret, next).text + "\n[lightgray]+" + Math.round(Weapons.levelDamage * 100) + "% damage";
                return intro(gun.turret);
            }
            if(passive != null) return passive.step(next);
            return "Restores " + (int)(healAmount * 100) + "% of your health." + (level(luck) >= 5 ? " +" + Math.round(maxedDamage * 100) + "% damage for the rest of the run." : "");
        }
    }

    public static String intro(BaseTurret turret){
        if(Weapons.intro.containsKey(turret)) return Weapons.intro.get(turret);
        UnlockableContent ammo = Weapons.firstAmmo(turret);
        return "Orbits you and fires " + (ammo == null ? "" : ammo.localizedName + " ammo ") + "wherever you shoot.";
    }

    public static void load(){
        stats = new StatusEffect("survivor-stats"){{
            permanent = true;
            show = false;
        }};
        might = new Passive("might", Blocks.battery, "+10% damage", 5);
        cooldown = new Passive("cooldown", Blocks.overdriveProjector, "-8% reload time", 5);
        area = new Passive("area", Blocks.impactReactor, "+10% blast radius", 5);
        amount = new Passive("amount", Blocks.multiplicativeReconstructor, 5, "+1 shot for every weapon", "+1 shot for every weapon", "-10% reload time for every weapon", "Every hit goes through 1 more enemy", "+20% damage for every weapon");
        swiftness = new Passive("swiftness", Blocks.titaniumConveyor, "+15% move speed", 5);
        velocity = new Passive("velocity", Blocks.massDriver, "+12% range and +10% bullet speed", 5);
        evasion = new Passive("evasion", Blocks.phaseWeaver, "6% chance to dodge a hit", 5);
        magnet = new Passive("magnet", Blocks.unloader, "+40% pickup range", 5);
        armor = new Passive("armor", Blocks.forceProjector, "+4 armor", 5);
        vitality = new Passive("vitality", Blocks.thoriumWall, "+40% max health", 5);
        recovery = new Passive("recovery", Blocks.repairPoint, "Heals 0.5% of your health per second", 5);
        lifesteal = new Passive("lifesteal", Blocks.mendProjector, "Heals you for 1% of the damage you deal", 5);
        aura = new Passive("aura", Blocks.thoriumReactor, 5, "Burns enemies within 8 tiles for 60 damage per second", "+1.5 tiles and +60 damage per second");
        growth = new Passive("growth", Blocks.cultivator, "+8% experience", 5);
        luck = new Passive("luck", Blocks.separator, 5, "50% chance of a fourth choice", "Always a fourth choice", "Thorium and magnets drop twice as often", "Every level-up heals you 10%", "Once your build is maxed, every level-up adds +5% damage for the rest of the run");
        Weapons.load();
        gemItems = new Item[]{Items.titanium, Items.plastanium, Items.surgeAlloy};
    }

    static void collectGuns(){
        if(guns.any()) return;
        for(var block : Arena.buildable){
            if(!(block instanceof BaseTurret turret) || !Weapons.steps.containsKey(turret)) continue;
            BulletType bullet = Weapons.ammo(turret, null);
            if(bullet != null || Weapons.support(turret)) guns.add(new Gun(turret, bullet));
        }
    }

    public static int level(Passive passive){
        return passiveLevel.get(passive, 0);
    }

    public static boolean has(Gun gun){
        return gunLevel.get(gun, 0) > 0;
    }

    static Weapon build(Gun gun, int level){
        Weapons.Spec spec = Weapons.spec(gun.turret, level);
        spec.range += 0.12f * level(velocity);
        if(!(gun.turret instanceof Turret t)){
            //support turrets that fire no bullets; their effect runs in supportUpdate
            Weapon w = new Weapon(gun.turret.name);
            w.bullet = Bullets.placeholder;
            w.controllable = false;
            w.autoTarget = false;
            w.aiControllable = false;
            w.mirror = false;
            w.rotate = true;
            w.rotateSpeed = 100000f;
            w.recoilTime = 10f;
            w.init();
            w.region = w.heatRegion = w.cellRegion = w.outlineRegion = Core.atlas.find("mindustry-survivors-none");
            return w;
        }
        spec.speed += 0.1f * level(velocity);
        if(level(amount) >= 3) spec.reload *= 0.9f;
        if(level(amount) >= 4) spec.pierce += 1;
        if(level(amount) >= 5) spec.damage += 0.2f;
        spec.blast += 0.1f * level(area);
        BulletType b = Weapons.make(t, spec);
        b.lifesteal = 0.01f * level(lifesteal);

        Weapon w = new Weapon(t.name);
        w.bullet = b;
        w.controllable = true;
        w.autoTarget = false;
        w.aiControllable = false;
        w.mirror = false;
        w.alternate = false;
        w.rotate = true;
        w.top = true;
        w.rotateSpeed = 100000f;
        w.reload = t.reload / Weapons.ammo(t, spec.ammo).reloadMultiplier / Weapons.globalFireRate * spec.reload * spec.cool;
        w.recoil = t.recoil;
        w.recoilTime = w.reload;
        w.shootY = t.shootY * scale(t);
        w.shootCone = Math.max(t.shootCone, 10f);
        w.inaccuracy = t.inaccuracy;
        w.velocityRnd = t.velocityRnd;
        w.shootSound = t.shootSound;
        w.chargeSound = t.chargeSound;
        w.continuous = b instanceof ContinuousBulletType;
        w.shoot = t.shoot.copy();
        w.shoot.shots += spec.shots + Math.min(level(amount), 2);
        w.init();
        w.region = w.heatRegion = w.cellRegion = w.outlineRegion = Core.atlas.find("mindustry-survivors-none");
        return w;
    }

    static float scale(BaseTurret t){
        return Math.min(1f, turretSize / (t.size * tilesize));
    }

    public static void rebuild(){
        if(ownWeapons == null) ownWeapons = Chase.walker.weapons.copy();
        Chase.walker.weapons.set(ownWeapons);
        for(Weapon w : ownWeapons) w.bullet.lifesteal = 0.01f * level(lifesteal);
        for(Gun gun : ownedGuns) Chase.walker.weapons.add(build(gun, gunLevel.get(gun, 1)));
        Unit me = player.unit();
        if(me != null && me.type == Chase.walker) me.setupWeapons(Chase.walker);
    }

    static void reset(){
        ownedGuns.clear();
        gunLevel.clear();
        ownedPassives.clear();
        passiveLevel.clear();
        offer.clear();
        gemX.clear();
        gemY.clear();
        gemValue.clear();
        gemPulled.clear();
        kitX.clear();
        kitY.clear();
        maxedBonus = 0f;
        magX.clear();
        magY.clear();
        spawned = false;
        if(ownWeapons != null) Chase.walker.weapons.set(ownWeapons);
    }

    public static void begin(int x, int y){
        collectGuns();
        reset();
        state.rules.tags.put(startTag, x + "," + y);
    }

    static void give(@Nullable Gun gun){
        if(gun == null) return;
        if(!has(gun)) ownedGuns.add(gun);
        gunLevel.increment(gun);
        rebuild();
    }

    static void give(Passive passive){
        if(level(passive) == 0) ownedPassives.add(passive);
        passiveLevel.increment(passive);
        rebuild();
    }

    public static float startX(){
        String[] pos = state.rules.tags.get(startTag, (world.width() / 2) + "," + (world.height() / 2)).split(",");
        return Integer.parseInt(pos[0]) * tilesize;
    }

    public static float startY(){
        String[] pos = state.rules.tags.get(startTag, (world.width() / 2) + "," + (world.height() / 2)).split(",");
        return Integer.parseInt(pos[1]) * tilesize;
    }

    public static Seq<Choice> roll(){
        Seq<Choice> pool = new Seq<>();
        for(Gun gun : guns){
            int lvl = gunLevel.get(gun, 0);
            if(lvl > 0 ? lvl < weaponLevels : ownedGuns.size < maxWeapons){
                Choice c = new Choice();
                c.gun = gun;
                pool.add(c);
            }
        }
        for(Passive passive : passives){
            int lvl = level(passive);
            if(lvl > 0 ? lvl < passive.maxLevel : ownedPassives.size < maxPassives){
                Choice c = new Choice();
                c.passive = passive;
                pool.add(c);
            }
        }
        pool.shuffle();
        int count = level(luck) >= 2 || (level(luck) == 1 && Mathf.chance(0.5f)) ? 4 : 3;
        Seq<Choice> out = pool.select(c -> true);
        out.truncate(Math.min(count, out.size));
        if(out.isEmpty()) out.add(new Choice());
        return out;
    }

    public static void take(Choice choice){
        RogueRun run = RogueRun.current;
        if(run == null || run.pendingModifiers <= 0) return;
        run.pendingModifiers--;
        offer.clear();
        if(choice.gun != null) give(choice.gun);
        else if(choice.passive != null) give(choice.passive);
        else{
            Unit me = player.unit();
            if(me != null && !me.dead) me.heal(me.maxHealth * healAmount);
            if(level(luck) >= 5) maxedBonus += maxedDamage;
        }
        Log.info("[Rogue] Took '@' (level @)", choice.name(), choice.gun != null ? gunLevel.get(choice.gun, 0) : choice.passive != null ? level(choice.passive) : 0);
    }

    public static void drop(Unit unit){
        float luckDrops = level(luck) >= 3 ? 2f : 1f;
        if(kitX.size < maxKits && Mathf.chance(kitChance * luckDrops)){
            kitX.add(unit.x + Mathf.range(4f));
            kitY.add(unit.y + Mathf.range(4f));
        }else if(magX.size < maxMagnets && Mathf.chance(magnetChance * luckDrops)){
            magX.add(unit.x + Mathf.range(4f));
            magY.add(unit.y + Mathf.range(4f));
        }
        int value = Math.max(1, Math.round(unit.maxHealth / 130f / Arena.enemyScale(Arena.difficulty())));
        if(gemValue.size >= maxGems){
            int i = Mathf.random(gemValue.size - 1);
            gemValue.incr(i, value);
            return;
        }
        gemX.add(unit.x);
        gemY.add(unit.y);
        gemValue.add(value);
        gemPulled.add(false);
    }

    static void addXp(float amount){
        RogueRun run = RogueRun.current;
        if(run == null) return;
        run.xp += amount * xpRate * (1f + 0.08f * level(growth));
        while(run.xp >= RogueRun.xpFor(run.level)){
            run.xp -= RogueRun.xpFor(run.level);
            run.level++;
            run.pendingModifiers++;
            Unit me = player.unit();
            if(level(luck) >= 4 && me != null && !me.dead) me.heal(me.maxHealth * 0.1f);
        }
    }

    static void update(){
        if(!Arena.active() || state.gameOver) return;

        Unit me = player.unit();
        if(me == null || me.dead || !me.isValid() || me.type != Chase.walker){
            Unit found = state.rules.defaultTeam.data().units.find(u -> u.type == Chase.walker && !u.dead);
            if(found != null){
                player.unit(found);
                spawned = true;
                rebuild();
            }else if(!spawned){
                Unit u = Chase.walker.create(state.rules.defaultTeam);
                u.set(startX(), startY());
                u.rotation = 90f;
                u.add();
                player.unit(u);
                spawned = true;
                rebuild();
            }else{
                state.gameOver = true;
                Events.fire(new GameOverEvent(state.rules.waveTeam));
            }
            return;
        }

        if(state.isPaused()) return;

        stats.damageMultiplier = 1f + 0.1f * level(might) + maxedBonus;
        stats.healthMultiplier = 1f + 0.4f * level(vitality);
        stats.reloadMultiplier = 1f / (1f - 0.08f * level(cooldown));
        stats.speedMultiplier = 1f + 0.15f * level(swiftness);
        if(!me.hasEffect(stats)) me.apply(stats);
        me.armor = Chase.walker.armor + 4f * level(armor);
        if(level(recovery) > 0) me.heal(me.maxHealth * 0.005f * level(recovery) * Time.delta / 60f);
        if(level(aura) > 0 && (auraTimer += Time.delta) >= 20f){
            auraTimer = 0f;
            Damage.damage(me.team, me.x, me.y, auraRadius(), auraDps * level(aura) / 3f, true, true);
        }

        orbit += orbitSpeed * Time.delta;
        int own = ownWeapons == null ? 0 : ownWeapons.size, n = Chase.walker.weapons.size - own;
        for(int i = 0; i < n; i++){
            Weapon w = Chase.walker.weapons.get(own + i);
            BaseTurret t = ownedGuns.get(i).turret;
            float radius = 8f + t.size * tilesize * scale(t) / 2f + n;
            Tmp.v1.trns(orbit + i * 360f / n - (me.rotation - 90f), radius);
            w.x = Tmp.v1.x;
            w.y = Tmp.v1.y;
            //a mount's angle is kept relative to the unit, so it is set from a world angle each frame to ignore which way the player faces
            if(own + i < me.mounts.length){
                var mount = me.mounts[own + i];
                float mx = me.x + Angles.trnsx(me.rotation - 90f, w.x, w.y), my = me.y + Angles.trnsy(me.rotation - 90f, w.x, w.y);
                if(Weapons.support(t)) supportUpdate(me, i, ownedGuns.get(i), mx, my);
                else aim[i] = Angles.angle(mx, my, me.aimX(), me.aimY());
                mount.rotation = aim[i] - me.rotation;
            }
        }

        float reach = baseMagnet * (1f + 0.4f * level(magnet));
        for(int i = kitX.size - 1; i >= 0; i--){
            float x = kitX.get(i), y = kitY.get(i), dst = Mathf.dst(x, y, me.x, me.y);
            if(dst <= me.hitSize){
                me.heal(me.maxHealth * kitHeal);
                Fx.healWaveDynamic.at(me.x, me.y, 20f);
                kitX.removeIndex(i);
                kitY.removeIndex(i);
            }else if(dst <= reach){
                Tmp.v1.set(me.x - x, me.y - y).setLength(Math.min(dst, (3f + me.speed()) * Time.delta));
                kitX.set(i, x + Tmp.v1.x);
                kitY.set(i, y + Tmp.v1.y);
            }
        }
        for(int i = magX.size - 1; i >= 0; i--){
            float x = magX.get(i), y = magY.get(i), dst = Mathf.dst(x, y, me.x, me.y);
            if(dst <= me.hitSize){
                for(int g = 0; g < gemPulled.size; g++) gemPulled.set(g, true);
                Fx.spawnShockwave.at(me.x, me.y, 60f);
                magX.removeIndex(i);
                magY.removeIndex(i);
            }else if(dst <= reach){
                Tmp.v1.set(me.x - x, me.y - y).setLength(Math.min(dst, (3f + me.speed()) * Time.delta));
                magX.set(i, x + Tmp.v1.x);
                magY.set(i, y + Tmp.v1.y);
            }
        }
        for(int i = gemValue.size - 1; i >= 0; i--){
            float x = gemX.get(i), y = gemY.get(i);
            float dst = Mathf.dst(x, y, me.x, me.y);
            if(dst <= me.hitSize){
                addXp(gemValue.get(i));
                gemX.removeIndex(i);
                gemY.removeIndex(i);
                gemValue.removeIndex(i);
                gemPulled.removeIndex(i);
                continue;
            }
            if(gemPulled.get(i) || dst <= reach){
                gemPulled.set(i, true);
                Tmp.v1.set(me.x - x, me.y - y).setLength(Math.min(dst, (3f + me.speed() + dst * 0.02f) * Time.delta));
                gemX.set(i, x + Tmp.v1.x);
                gemY.set(i, y + Tmp.v1.y);
            }
        }
    }

    static void draw(){
        if(!Arena.active()) return;
        drawTurrets();
        Unit me = player.unit();
        if(level(aura) > 0 && me != null && !me.dead){
            Draw.z(Layer.groundUnit - 1f);
            Lines.stroke(1.5f, Tmp.c1.set(Pal.lightOrange).a(0.25f + Mathf.absin(Time.time, 8f, 0.2f)));
            Lines.circle(me.x, me.y, auraRadius());
            Draw.reset();
        }
        Draw.z(Layer.groundUnit - 1f);
        float pad = 16f;
        float left = Core.camera.position.x - Core.camera.width / 2f - pad, right = left + Core.camera.width + pad * 2f;
        float bottom = Core.camera.position.y - Core.camera.height / 2f - pad, top = bottom + Core.camera.height + pad * 2f;
        float pulse = 0.6f + Mathf.absin(Time.time, 10f, 0.4f);
        for(int i = 0; i < kitX.size; i++){
            float x = kitX.get(i), y = kitY.get(i) + Mathf.absin(Time.time + i * 17f, 9f, 1.5f);
            if(x < left || x > right || y < bottom || y > top) continue;
            Fill.light(x, y, 20, 26f * pulse, Tmp.c1.set(Pal.heal).a(0.75f * pulse), Tmp.c2.set(Pal.heal).a(0f));
            Draw.color();
            float size = 15f + Mathf.absin(Time.time, 10f, 2f);
            Draw.rect(Items.thorium.fullIcon, x, y, size, size);
        }
        for(int i = 0; i < magX.size; i++){
            float x = magX.get(i), y = magY.get(i) + Mathf.absin(Time.time + i * 13f, 9f, 1.5f);
            if(x < left || x > right || y < bottom || y > top) continue;
            Fill.light(x, y, 20, 26f * pulse, Tmp.c1.set(magnetGlow).a(0.75f * pulse), Tmp.c2.set(magnetGlow).a(0f));
            Draw.color();
            float size = 15f + Mathf.absin(Time.time, 10f, 2f);
            Draw.rect(Core.atlas.find("mindustry-survivors-magnet"), x, y, size, size);
        }
        for(int i = 0; i < gemValue.size; i++){
            float x = gemX.get(i), y = gemY.get(i) + Mathf.absin(Time.time + i * 11f, 7f, 1f);
            if(x < left || x > right || y < bottom || y > top) continue;
            int v = gemValue.get(i), k = v < 5 ? 0 : v < 25 ? 1 : 2;
            float r = gemSizes[k];
            Item item = gemItems[k];
            Fill.light(x, y, 12, r * 1.4f, Tmp.c1.set(item.color).a(0.5f), Tmp.c2.set(item.color).a(0f));
            Draw.color();
            Draw.rect(item.fullIcon, x, y, r, r);
        }
        Draw.reset();
    }

    //tractor beams grab enemies near the player's aim while they shoot; point defense shoots down enemy bullets on its own
    static void supportUpdate(Unit me, int i, Gun gun, float mx, float my){
        Weapons.Spec spec = Weapons.spec(gun.turret, gunLevel.get(gun, 1));
        spec.range += 0.12f * level(velocity);
        float range = gun.turret.range * spec.range * Weapons.globalRange;
        int beams = 1 + spec.shots + Math.min(level(amount), 2);
        held[i].clear();
        if(Time.time < jammed[i]) return;

        if(gun.turret instanceof TractorBeamTurret tractor){
            if(!me.isShooting()) return;
            Seq<Unit> near = new Seq<>();
            Units.nearbyEnemies(me.team, mx, my, range, near::add);
            near.sort(u -> u.dst2(me.aimX(), me.aimY()));
            float damage = Weapons.niche.get(tractor, 0f) * spec.damage * Weapons.globalDamage * (1f + 0.1f * level(might)) / 60f * Time.delta;
            for(int b = 0; b < beams && b < near.size; b++){
                Unit target = near.get(b);
                held[i].add(target);
                target.damageContinuousPierce(damage);
                target.apply(StatusEffects.slow, 30f);
                if(spec.disarm) target.apply(StatusEffects.disarmed, 30f);
                Tmp.v1.set(target).sub(me).setLength((tractor.force * 0.15f + (1f - target.dst(mx, my) / range) * tractor.scaledForce * 0.15f) * spec.force * Time.delta);
                target.impulseNet(Tmp.v1);
            }
            if(held[i].any()) aim[i] = Angles.angle(mx, my, held[i].first().x, held[i].first().y);
        }else if(gun.turret instanceof PointDefenseTurret point){
            float reload = point.reload * spec.reload / Weapons.globalFireRate / (1f - 0.08f * level(cooldown));
            if((pointReload[i] += Time.delta) < reload) return;
            Seq<Bullet> shots = Groups.bullet.intersect(mx - range, my - range, range * 2f, range * 2f).select(b -> b.team != me.team && b.type.hittable && b.within(mx, my, range));
            if(shots.isEmpty()) return;
            pointReload[i] = 0f;
            shots.sort(b -> b.dst2(mx, my));
            float stops = point.bulletDamage * spec.damage;
            for(int b = 0; b < beams && b < shots.size; b++){
                Bullet target = shots.get(b);
                float angle = Angles.angle(mx, my, target.x, target.y);
                if(b == 0) aim[i] = angle;
                point.beamEffect.at(mx, my, angle, point.color, new Vec2().set(target));
                point.hitEffect.at(target.x, target.y, point.color);
                if(target.damage() > stops) target.damage(target.damage() - stops);
                else target.remove();
                if(spec.salvage > 0f) me.heal(me.maxHealth * spec.salvage);
                shotDown++;
            }
            point.shootSound.at(mx, my, Mathf.random(0.9f, 1.1f));
        }
    }

    static float auraRadius(){
        return (8f + 1.5f * (level(aura) - 1)) * tilesize;
    }

    //an orbiting turret picked at random stops firing for a while
    public static void jam(float ticks){
        Unit me = player.unit();
        if(ownedGuns.isEmpty() || me == null || me.dead) return;
        int own = ownWeapons == null ? 0 : ownWeapons.size, i = Mathf.random(ownedGuns.size - 1);
        jammed[i] = Time.time + ticks;
        if(own + i < me.mounts.length) me.mounts[own + i].reload = Math.max(me.mounts[own + i].reload, ticks);
        Fx.sapExplosion.at(me.x, me.y);
    }

    public static boolean dodges(){
        return Mathf.chance(0.06f * level(evasion));
    }

    static void drawTurrets(){
        Unit me = player.unit();
        if(me == null || me.dead || me.type != Chase.walker) return;
        int own = ownWeapons == null ? 0 : ownWeapons.size;
        for(int i = 0; i < ownedGuns.size && own + i < me.mounts.length; i++){
            var mount = me.mounts[own + i];
            BaseTurret t = ownedGuns.get(i).turret;
            float scl = scale(t), rot = me.rotation - 90f + mount.rotation, recoil = t instanceof Turret tt ? tt.recoil : 0f;
            float x = me.x + Angles.trnsx(me.rotation - 90f, mount.weapon.x, mount.weapon.y), y = me.y + Angles.trnsy(me.rotation - 90f, mount.weapon.x, mount.weapon.y);
            TextureRegion top = Core.atlas.find(t.name + "-preview", t.name);
            Draw.z(Layer.groundUnit + 1.1f);
            Tmp.v1.trns(rot, 0f, -mount.recoil * recoil * scl);
            Draw.rect(top, x + Tmp.v1.x, y + Tmp.v1.y, top.width * Draw.scl * scl, top.height * Draw.scl * scl, rot);
            if(t instanceof TractorBeamTurret tractor && i < held.length){
                Draw.z(Layer.bullet);
                Draw.mixcol(tractor.laserColor, Mathf.absin(4f, 0.6f));
                for(Unit target : held[i]) Drawf.laser(tractor.laser, tractor.laserStart, tractor.laserEnd, x, y, target.x, target.y, tractor.laserWidth * scl);
                Draw.mixcol();
            }
        }
        Draw.reset();
    }

    static void write(DataOutput out) throws IOException{
        out.writeInt(2);
        out.writeInt(ownedGuns.size);
        for(Gun gun : ownedGuns){
            out.writeUTF(gun.turret.name);
            out.writeInt(gunLevel.get(gun, 1));
        }
        out.writeInt(ownedPassives.size);
        for(Passive passive : ownedPassives){
            out.writeUTF(passive.id);
            out.writeInt(level(passive));
        }
        out.writeFloat(maxedBonus);
    }

    static void read(DataInput in) throws IOException{
        collectGuns();
        reset();
        int version = in.readInt();
        int count = in.readInt();
        for(int i = 0; i < count; i++){
            String name = in.readUTF();
            int lvl = in.readInt();
            Gun gun = guns.find(g -> g.turret.name.equals(name));
            if(gun == null) continue;
            ownedGuns.add(gun);
            gunLevel.put(gun, lvl);
        }
        count = in.readInt();
        for(int i = 0; i < count; i++){
            String id = in.readUTF();
            int lvl = in.readInt();
            Passive passive = passives.find(p -> p.id.equals(id));
            if(passive == null) continue;
            ownedPassives.add(passive);
            passiveLevel.put(passive, lvl);
        }
        if(version >= 2) maxedBonus = in.readFloat();
        rebuild();
    }

    static void register(){
        SaveVersion.addCustomChunk("mindustry-survivors-survivor", new SaveFileReader.CustomChunk(){
            @Override
            public boolean shouldWrite(){
                return Arena.active();
            }

            @Override
            public void write(DataOutput out) throws IOException{
                Survivor.write(out);
            }

            @Override
            public void read(DataInput in) throws IOException{
                Survivor.read(in);
            }
        });
        Events.on(WorldLoadBeginEvent.class, e -> reset());
        Events.on(UnitDestroyEvent.class, e -> {
            if(Arena.active() && e.unit.team == state.rules.waveTeam) drop(e.unit);
        });
        Events.run(Trigger.update, Survivor::update);
        Events.run(Trigger.draw, Survivor::draw);
    }
}

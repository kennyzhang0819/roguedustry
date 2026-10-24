package mindustrysurvivors;

import arc.func.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.entities.bullet.*;
import mindustry.type.*;
import mindustry.type.unit.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;

public class Weapons{
    public static final int maxLevel = 10;
    //global turret balance: multipliers on top of the base game's numbers, the same for every turret, before each turret's own balance factor
    public static float globalDamage = 1f, globalFireRate = 2f, globalRange = 1f;
    public static final float levelDamage = 0.2f;
    public static final ObjectFloatMap<Block> niche = new ObjectFloatMap<>();
    //extra starting range for turrets whose base game range is too short to keep enemies off the player
    public static final ObjectFloatMap<Block> baseRange = new ObjectFloatMap<>();
    public static final float liquidPush = 2f;
    public static final OrderedMap<Block, Seq<Step>> steps = new OrderedMap<>();
    public static final ObjectMap<Block, String> intro = new ObjectMap<>();
    static final ObjectMap<Block, UnlockableContent> startAmmo = new ObjectMap<>();
    public static final ObjectSet<Block> erekir = new ObjectSet<>(), helpers = new ObjectSet<>();
    static final ObjectFloatMap<Block> slower = new ObjectFloatMap<>();
    public static float missileDamage = 1f;

    public static class Spec{
        public @Nullable UnlockableContent ammo;
        public float damage = 1f, reload = 1f, cool = 1f, range = 1f, speed = 1f, blast = 1f, burn = 1f, force = 1f, puddle = 1f, homing, knock, salvage;
        public int shots, pierce, frag, lightning, fire;
        public @Nullable StatusEffect status;
    }

    public static class Step{
        public final String text;
        public final @Nullable UnlockableContent icon;
        final Cons<Spec> apply;

        Step(String text, @Nullable UnlockableContent icon, Cons<Spec> apply){
            this.text = text;
            this.icon = icon;
            this.apply = apply;
        }
    }

    public static Spec spec(Block turret, int level){
        Spec spec = new Spec();
        spec.damage += levelDamage * (level - 1);
        Seq<Step> list = steps.get(turret, Seq::new);
        for(int i = 0; i < level - 1 && i < list.size; i++) list.get(i).apply.get(spec);
        return spec;
    }

    public static @Nullable Step step(Block turret, int level){
        Seq<Step> list = steps.get(turret);
        return list == null || level < 2 || level - 2 >= list.size ? null : list.get(level - 2);
    }

    public static @Nullable BulletType ammo(BaseTurret turret, @Nullable UnlockableContent item){
        if(item == null) item = firstAmmo(turret);
        if(turret instanceof ItemTurret it && it.ammoTypes.size > 0) return item instanceof Item i && it.ammoTypes.containsKey(i) ? it.ammoTypes.get(i) : it.ammoTypes.values().toSeq().first();
        if(turret instanceof LiquidTurret lt && lt.ammoTypes.size > 0) return item instanceof Liquid l && lt.ammoTypes.containsKey(l) ? lt.ammoTypes.get(l) : lt.ammoTypes.values().toSeq().first();
        if(turret instanceof ContinuousLiquidTurret cl && cl.ammoTypes.size > 0) return item instanceof Liquid l && cl.ammoTypes.containsKey(l) ? cl.ammoTypes.get(l) : cl.ammoTypes.values().toSeq().first();
        if(turret instanceof PowerTurret power) return power.shootType;
        if(turret instanceof ContinuousTurret cont) return cont.shootType;
        return null;
    }

    public static @Nullable UnlockableContent firstAmmo(BaseTurret turret){
        UnlockableContent start = startAmmo.get(turret);
        if(turret instanceof ItemTurret it && start instanceof Item i && it.ammoTypes.containsKey(i)) return start;
        if(turret instanceof LiquidTurret lt && start instanceof Liquid l && lt.ammoTypes.containsKey(l)) return start;
        if(turret instanceof ContinuousLiquidTurret cl && start instanceof Liquid l && cl.ammoTypes.containsKey(l)) return start;
        if(turret instanceof ContinuousLiquidTurret cl && cl.ammoTypes.size > 0) return cl.ammoTypes.keys().toSeq().first();
        if(turret instanceof ItemTurret it && it.ammoTypes.size > 0) return it.ammoTypes.keys().toSeq().first();
        if(turret instanceof LiquidTurret lt && lt.ammoTypes.size > 0) return lt.ammoTypes.keys().toSeq().first();
        return null;
    }

    public static boolean support(BaseTurret turret){
        return turret instanceof TractorBeamTurret || turret instanceof PointDefenseTurret;
    }

    public static float hitDamage(BulletType b){
        float hit = (b instanceof ArtilleryBulletType ? 0f : b.damage) + b.splashDamage;
        if(b.fragBullet != null) hit += b.fragBullets * (b.fragBullet.damage + b.fragBullet.splashDamage);
        if(b.lightning > 0) hit += b.lightning * (b.lightningDamage < 0f ? b.damage : b.lightningDamage);
        if(b.intervalBullet != null) hit += b.lifetime / b.bulletInterval * b.intervalBullets * (b.intervalBullet.damage + b.intervalBullet.splashDamage);
        if(b.fragBullet != null && b.fragBullet.spawnUnit != null) hit += b.fragBullets * hitDamage(b.fragBullet);
        if(b.spawnUnit != null) for(Weapon w : b.spawnUnit.weapons) hit += hitDamage(w.bullet);
        return hit;
    }

    public static float gameDps(BaseTurret block){
        if(block instanceof TractorBeamTurret tractor) return tractor.damage * 60f;
        if(!(block instanceof Turret turret)) return 0f;
        BulletType b = ammo(turret, null);
        if(turret instanceof ContinuousTurret){
            float interval = b instanceof ContinuousBulletType c ? c.damageInterval : b instanceof PointLaserBulletType pl ? pl.damageInterval : 5f;
            return b.damage * 60f / interval;
        }
        float reload = turret.reload * slower.get(turret, 1f) / b.reloadMultiplier;
        if(b instanceof ContinuousBulletType c) return c.damage * 60f / c.damageInterval * b.lifetime / (b.lifetime + reload);
        return hitDamage(b) * turret.shoot.shots * 60f / reload;
    }

    //each turret's level 1 damage per second, picked by its niche: short range hits hard, long range and splash hit softer
    public static float balance(BaseTurret turret){
        float dps = gameDps(turret);
        return dps <= 0f ? 1f : niche.get(turret, dps) / dps;
    }

    public static float levelOneDps(BaseTurret turret){
        if(support(turret)) return niche.get(turret, 0f) * globalDamage;
        return (gameDps(turret) > 0f ? gameDps(turret) * balance(turret) : niche.get(turret, 0f)) * globalDamage * globalFireRate;
    }

    static @Nullable BulletType fragPiece(){
        BulletType plast = ((ItemTurret)Blocks.ripple).ammoTypes.get(Items.plastanium);
        if(plast != null && plast.fragBullet != null) return plast.fragBullet;
        BulletType any = ((ItemTurret)Blocks.ripple).ammoTypes.values().toSeq().find(b -> b.fragBullet != null);
        return any == null ? null : any.fragBullet;
    }

    static BulletType scaled(BulletType source, float dmg, int depth){
        BulletType b = source.copy();
        b.damage *= dmg;
        b.splashDamage *= dmg;
        if(b.lightningDamage > 0f) b.lightningDamage *= dmg;
        b.collidesAir = b.collidesGround = true;
        b.hitShake = b.despawnShake = 0f;
        if(depth < 3 && b.fragBullet != null) b.fragBullet = scaled(b.fragBullet, dmg, depth + 1);
        if(depth < 3 && b.intervalBullet != null) b.intervalBullet = scaled(b.intervalBullet, dmg, depth + 1);
        return b;
    }

    public static BulletType make(Turret turret, Spec s){
        BulletType base = ammo(turret, s.ammo);
        float dmg = s.damage * balance(turret) * globalDamage, rng = s.range * globalRange * baseRange.get(turret, 1f);
        //a continuous beam or flame does not fire faster with the global fire rate, so it hits that much harder instead
        if(turret instanceof ContinuousTurret) dmg *= globalFireRate;
        float reach = (turret.range + base.rangeChange) * rng;
        BulletType b = scaled(base, dmg, 0);
        if(b.spawnUnit != null) missileDamage = dmg;
        //liquid sprays do no damage in the base game, so a support turret's damage comes straight from its niche
        if(gameDps(turret) <= 0f && niche.get(turret, 0f) > 0f){
            b.damage = niche.get(turret, 0f) * (turret.reload / base.reloadMultiplier) / 60f / turret.shoot.shots * s.damage * globalDamage;
        }
        if(b.splashDamageRadius > 0f) b.splashDamageRadius *= s.blast;
        b.speed *= s.speed;
        b.lifetime *= rng / s.speed;
        if(b instanceof LaserBulletType l) l.length *= rng;
        if(b instanceof ContinuousBulletType c) c.length *= rng;
        if(b instanceof ShrapnelBulletType sh) sh.length *= rng;
        if(b instanceof RailBulletType r) r.length *= rng;
        if(b instanceof LightningBulletType) b.lightningLength = Math.round(b.lightningLength * rng);
        if(b.rangeOverride > 0f) b.rangeOverride *= rng;
        b.range = reach;
        if(s.pierce > 0 && !(b.pierce && b.pierceCap < 0)){
            b.pierce = true;
            b.pierceCap = Math.max(b.pierceCap, 1) + s.pierce;
        }
        b.homingPower += s.homing;
        b.knockback = (b.knockback + s.knock) * s.force * (turret instanceof LiquidTurret ? liquidPush : 1f);
        if(b instanceof LiquidBulletType liquid) liquid.puddleSize *= s.puddle;
        if(s.frag > 0 && (b.fragBullet != null || fragPiece() != null)){
            if(b.fragBullet == null){
                b.fragBullet = fragPiece().copy();
                b.fragBullet.damage *= dmg;
                b.fragBullet.splashDamage *= dmg;
            }
            b.fragBullets += s.frag;
        }
        if(s.lightning > 0){
            b.lightning += s.lightning;
            if(b.lightningDamage <= 0f) b.lightningDamage = 15f * dmg;
            if(b.lightningLength <= 0) b.lightningLength = 8;
        }
        //Afflict's orb only bursts when it runs out, far behind the crowd it flew through, so here it bursts on every hit
        if(turret == Blocks.afflict) b.fragOnHit = true;
        if(s.status != null) b.status = s.status;
        b.statusDuration *= s.burn;
        if(s.fire > 0){
            b.incendAmount += s.fire;
            b.incendChance = Math.max(b.incendChance, 0.5f);
            b.incendSpread = Math.max(b.incendSpread, 8f);
        }
        //Scorch's flame bullets are invisible and its flame effect has a fixed length, so a trail shows how far the fire really reaches
        if(turret == Blocks.scorch){
            b.trailEffect = Fx.hitFlameSmall;
            b.trailChance = 0.6f;
        }
        return b;
    }

    static Step loads(UnlockableContent item, String text){
        return new Step(item.localizedName + " ammo: " + text, item, s -> s.ammo = item);
    }

    static Step beams(int n){
        return new Step("+" + n + (n == 1 ? " beam" : " beams"), null, s -> s.shots += n);
    }

    static Step streams(int n){
        return new Step("+" + n + (n == 1 ? " stream" : " streams"), null, s -> s.shots += n);
    }

    static Step force(int pct, String what){
        return new Step("+" + pct + "% " + what, null, s -> s.force += pct / 100f);
    }

    static Step lasts(float times, String what){
        return new Step(what + " lasts " + (times == 2f ? "twice" : (int)times + " times") + " as long", null, s -> s.burn *= times);
    }

    static Step water(){
        return new Step("Water cooling: -30% reload time", Liquids.water, s -> s.cool = Math.min(s.cool, 0.7f));
    }

    static Step cryo(){
        return new Step("Cryofluid cooling: -50% reload time", Liquids.cryofluid, s -> s.cool = Math.min(s.cool, 0.5f));
    }

    static Step damage(int pct){
        return new Step("+" + pct + "% damage", null, s -> s.damage += pct / 100f);
    }

    static Step shots(int n){
        return new Step("+" + n + (n == 1 ? " shot" : " shots"), null, s -> s.shots += n);
    }

    static Step reload(int pct){
        return new Step("-" + pct + "% reload time", null, s -> s.reload *= 1f - pct / 100f);
    }

    static Step range(int pct){
        return new Step("+" + pct + "% range", null, s -> s.range += pct / 100f);
    }

    static Step blast(int pct){
        return new Step("+" + pct + "% blast radius", null, s -> s.blast += pct / 100f);
    }

    static Step pierce(int n){
        return new Step("Hits go through " + n + " more " + (n == 1 ? "enemy" : "enemies"), null, s -> s.pierce += n);
    }

    static Step step(String text, Cons<Spec> apply){
        return new Step(text, null, apply);
    }

    static void define(Block turret, float dps, Step... list){
        steps.put(turret, Seq.with(list));
        niche.put(turret, dps);
    }

    public static float reach(BaseTurret turret){
        return turret.range * globalRange * baseRange.get(turret, 1f);
    }

    public static float reload(Turret turret, @Nullable UnlockableContent ammo){
        return turret.reload * slower.get(turret, 1f) / ammo(turret, ammo).reloadMultiplier / globalFireRate;
    }

    public static void load(){
        helpers.addAll(Blocks.wave, Blocks.tsunami, Blocks.parallax, Blocks.segment);
        baseRange.put(Blocks.scorch, 1.75f);
        baseRange.put(Blocks.fuse, 1.07f);
        baseRange.put(Blocks.arc, 1.45f);
        baseRange.put(Blocks.wave, 1.3f);
        //other mods can replace a turret's whole ammo list, so each turret's starting ammo is named here instead of taken from the list
        startAmmo.putAll(Blocks.duo, Items.copper, Blocks.scatter, Items.scrap, Blocks.scorch, Items.coal, Blocks.hail, Items.graphite,
            Blocks.wave, Liquids.water, Blocks.swarmer, Items.blastCompound, Blocks.salvo, Items.copper, Blocks.tsunami, Liquids.water,
            Blocks.fuse, Items.titanium, Blocks.ripple, Items.graphite, Blocks.cyclone, Items.metaglass, Blocks.foreshadow, Items.surgeAlloy,
            Blocks.spectre, Items.graphite);
        define(Blocks.wave, 60f,
            streams(1),
            loads(Liquids.oil, "tars enemies, slowing them and making fire burn them much harder"),
            lasts(3f, "Tar"),
            range(40),
            step("Oil Slick: much bigger puddles that slow enemies and catch fire", s -> s.puddle *= 3f),
            streams(1),
            force(60, "knockback"),
            streams(1),
            step("Ignite: sprays set the ground alight", s -> s.fire += 2)
        );
        define(Blocks.tsunami, 90f,
            force(50, "knockback"),
            loads(Liquids.cryofluid, "freezes enemies, slowing them and weakening their armor"),
            lasts(3f, "Freezing"),
            streams(1),
            range(30),
            force(80, "knockback"),
            streams(1),
            lasts(2f, "Freezing"),
            step("Tidal Wave: +2 streams and +100% knockback", s -> {
                s.shots += 2;
                s.force += 1f;
            })
        );
        define(Blocks.parallax, 60f,
            beams(1),
            force(40, "push"),
            range(30),
            step("Overload: +1 beam and +30% range", s -> {
                s.shots += 1;
                s.range += 0.3f;
            }),
            beams(1),
            damage(100),
            force(60, "push"),
            beams(1),
            step("Singularity: +2 beams and +100% damage", s -> {
                s.shots += 2;
                s.damage += 1f;
            })
        );
        define(Blocks.segment, 0f,
            reload(30),
            beams(1),
            range(40),
            step("Hardened: each beam stops twice as much bullet damage", s -> s.damage += 1f),
            beams(1),
            reload(30),
            step("Salvage: every bullet shot down heals 0.3% of your health", s -> s.salvage += 0.003f),
            beams(1),
            step("Aegis: +2 beams and -30% reload time", s -> {
                s.shots += 2;
                s.reload *= 0.7f;
            })
        );
        intro.put(Blocks.wave, "Orbits you and sprays Water wherever you shoot, soaking and pushing enemies.");
        intro.put(Blocks.tsunami, "Orbits you and blasts Water wherever you shoot, shoving enemies far back.");
        intro.put(Blocks.parallax, "Orbits you and, while you shoot, grabs the enemy nearest your aim, slowing it and pushing it away.");
        intro.put(Blocks.segment, "Orbits you and shoots down enemy bullets on its own.");

        define(Blocks.duo, 220f,
            shots(1),
            loads(Items.graphite, "heavy rounds, double damage"),
            damage(50),
            water(),
            loads(Items.silicon, "homing rounds that fire 50% faster"),
            shots(1),
            pierce(2),
            cryo(),
            step("Swarm Doctrine: +50% damage and -33% reload time", s -> {
                s.damage += 0.5f;
                s.reload *= 0.67f;
            })
        );
        define(Blocks.scatter, 170f,
            loads(Items.lead, "stronger flak"),
            shots(1),
            blast(40),
            loads(Items.metaglass, "flak bursts into glass shards"),
            water(),
            damage(50),
            shots(1),
            cryo(),
            step("Flak Wall: fires twice as fast and hits twice as hard", s -> {
                s.reload *= 0.5f;
                s.damage += 1f;
            })
        );
        define(Blocks.scorch, 450f,
            range(50),
            damage(50),
            shots(1),
            loads(Items.pyratite, "hotter flames"),
            step("Burns last twice as long", s -> s.burn *= 2f),
            range(50),
            step("Wildfire: flames set the ground alight", s -> s.fire += 2),
            shots(1),
            step("Inferno: +100% damage", s -> s.damage += 1f)
        );
        define(Blocks.hail, 150f,
            range(50),
            loads(Items.silicon, "shells home in on enemies"),
            shots(1),
            blast(50),
            loads(Items.pyratite, "burning shells"),
            water(),
            shots(1),
            step("Shock Field: each shell throws 3 lightning bolts", s -> s.lightning += 3),
            step("Meteor: double damage and blast radius", s -> {
                s.damage += 1f;
                s.blast += 1f;
            })
        );
        define(Blocks.arc, 340f,
            range(40),
            damage(50),
            shots(1),
            water(),
            step("Static: bolts slow enemies down", s -> s.status = StatusEffects.electrified),
            range(50),
            new Step("Surge-charged: +60% damage", Items.surgeAlloy, s -> s.damage += 0.6f),
            cryo(),
            step("Tesla: +2 bolts", s -> s.shots += 2)
        );
        define(Blocks.lancer, 230f,
            damage(50),
            water(),
            shots(1),
            range(40),
            pierce(4),
            step("Arc discharge: each laser throws 2 lightning bolts", s -> s.lightning += 2),
            cryo(),
            shots(1),
            step("Overload: +100% damage", s -> s.damage += 1f)
        );
        define(Blocks.swarmer, 160f,
            shots(1),
            loads(Items.pyratite, "burning missiles"),
            blast(40),
            water(),
            loads(Items.surgeAlloy, "missiles throw lightning"),
            shots(2),
            step("Sharper guidance: missiles turn much harder", s -> s.homing += 0.08f),
            cryo(),
            step("Warheads: +100% damage", s -> s.damage += 1f)
        );
        define(Blocks.salvo, 210f,
            loads(Items.graphite, "heavy rounds"),
            shots(1),
            loads(Items.silicon, "homing rounds that fire faster"),
            water(),
            loads(Items.pyratite, "burning rounds with small blasts"),
            shots(1),
            loads(Items.thorium, "armor-piercing rounds"),
            cryo(),
            step("Barrage: +2 shots and +50% damage", s -> {
                s.shots += 2;
                s.damage += 0.5f;
            })
        );
        define(Blocks.fuse, 500f,
            shots(1),
            range(30),
            water(),
            loads(Items.thorium, "heavier shrapnel"),
            damage(60),
            shots(1),
            step("Blowback: hits throw enemies back", s -> s.knock += 3f),
            cryo(),
            step("Shredder: +100% damage", s -> s.damage += 1f)
        );
        define(Blocks.ripple, 140f,
            shots(1),
            loads(Items.silicon, "shells home in on enemies"),
            loads(Items.pyratite, "burning shells"),
            water(),
            loads(Items.blastCompound, "huge blasts"),
            shots(2),
            loads(Items.plastanium, "shells burst into 15 fragments"),
            cryo(),
            step("Carpet Bombing: +100% damage", s -> s.damage += 1f)
        );
        define(Blocks.cyclone, 180f,
            damage(50),
            loads(Items.blastCompound, "big explosive flak"),
            water(),
            loads(Items.plastanium, "flak bursts into fragments"),
            shots(1),
            loads(Items.surgeAlloy, "flak throws lightning"),
            blast(40),
            cryo(),
            step("Storm: +1 shot and +50% damage", s -> {
                s.shots += 1;
                s.damage += 0.5f;
            })
        );
        define(Blocks.foreshadow, 110f,
            water(),
            damage(50),
            step("Shatter: hits burst into 6 fragments", s -> s.frag += 6),
            cryo(),
            shots(1),
            step("Chain Lightning: hits throw 4 lightning bolts", s -> s.lightning += 4),
            reload(25),
            damage(75),
            shots(1)
        );
        define(Blocks.spectre, 170f,
            loads(Items.thorium, "armor-piercing rounds"),
            damage(50),
            water(),
            loads(Items.pyratite, "burning rounds with blasts"),
            shots(1),
            pierce(3),
            cryo(),
            damage(75),
            shots(1)
        );
        define(Blocks.meltdown, 230f,
            water(),
            damage(50),
            range(30),
            cryo(),
            damage(50),
            range(30),
            step("Scorched Earth: the beam sets the ground alight", s -> s.fire += 2),
            damage(50),
            step("Core Breach: +100% damage", s -> s.damage += 1f)
        );
        loadErekir();
    }

    static void loadErekir(){
        erekir.addAll(Blocks.breach, Blocks.diffuse, Blocks.sublimate, Blocks.titan, Blocks.disperse, Blocks.afflict, Blocks.lustre, Blocks.scathe, Blocks.smite, Blocks.malign);
        startAmmo.putAll(Blocks.breach, Items.beryllium, Blocks.diffuse, Items.graphite, Blocks.sublimate, Liquids.ozone, Blocks.titan, Items.thorium,
            Blocks.disperse, Items.tungsten, Blocks.scathe, Items.carbide, Blocks.smite, Items.surgeAlloy);
        slower.put(Blocks.malign, 16f);
        slower.put(Blocks.afflict, 4f);
        slower.put(Blocks.disperse, 2f);
        baseRange.put(Blocks.scathe, 0.3f);
        intro.put(Blocks.sublimate, "Orbits you and breathes a long flame wherever you shoot, burning through enemies.");
        intro.put(Blocks.lustre, "Orbits you and holds a burning laser point wherever you aim.");
        intro.put(Blocks.scathe, "Orbits you and launches a missile that flies to your aim and blows up a whole crowd.");
        intro.put(Blocks.afflict, "Orbits you and fires a slow electric orb that sprays sparks and bursts at the end.");
        intro.put(Blocks.malign, "Orbits you and fires homing charges that burst into lasers.");

        define(Blocks.breach, 210f,
            pierce(1),
            water(),
            loads(Items.tungsten, "harder rounds that go through more enemies and reach further"),
            damage(50),
            shots(1),
            cryo(),
            range(30),
            loads(Items.carbide, "slow, huge rounds that split into 3 piercing shards"),
            step("Breakthrough: +1 shot and +50% damage", s -> {
                s.shots += 1;
                s.damage += 0.5f;
            })
        );
        define(Blocks.diffuse, 600f,
            force(50, "knockback"),
            range(25),
            loads(Items.silicon, "pellets home in on enemies"),
            water(),
            pierce(1),
            loads(Items.oxide, "heavy pellets, more than twice the damage"),
            reload(40),
            cryo(),
            step("Buckshot: +100% damage and +100% knockback", s -> {
                s.damage += 1f;
                s.force += 1f;
            })
        );
        define(Blocks.sublimate, 500f,
            range(20),
            damage(40),
            pierce(1),
            step("Burning: the flame sets enemies on fire", s -> s.status = StatusEffects.burning),
            loads(Liquids.cyanogen, "a hotter, longer flame that goes through more enemies"),
            range(20),
            step("Wildfire: the flame sets the ground alight", s -> s.fire += 2),
            damage(60),
            step("Plasma Torch: +100% damage", s -> s.damage += 1f)
        );
        define(Blocks.titan, 110f,
            blast(30),
            water(),
            shots(1),
            loads(Items.oxide, "huge blasts that leave a field hurting enemies for a while"),
            range(20),
            cryo(),
            shots(1),
            loads(Items.carbide, "heavy shells that burst into 12 fragments"),
            step("Siege: +100% damage and +30% blast radius", s -> {
                s.damage += 1f;
                s.blast += 0.3f;
            })
        );
        define(Blocks.disperse, 300f,
            shots(1),
            water(),
            step("Proximity: shots home in on enemies", s -> s.homing += 0.05f),
            damage(50),
            pierce(1),
            cryo(),
            loads(Items.surgeAlloy, "shots throw lightning as they fly"),
            shots(1),
            step("Flak Storm: -30% reload time and +50% damage", s -> {
                s.reload *= 0.7f;
                s.damage += 0.5f;
            })
        );
        define(Blocks.afflict, 200f,
            water(),
            step("Charged: +10 sparks when the orb bursts", s -> s.frag += 10),
            damage(50),
            shots(1),
            step("Thunder: the orb throws 4 lightning bolts when it hits", s -> s.lightning += 4),
            cryo(),
            pierce(2),
            step("Overcharge: +10 sparks and +50% damage", s -> {
                s.frag += 10;
                s.damage += 0.5f;
            }),
            step("Tempest: +1 orb and +50% damage", s -> {
                s.shots += 1;
                s.damage += 0.5f;
            })
        );
        define(Blocks.lustre, 550f,
            damage(30),
            damage(40),
            step("Searing: the laser sets enemies on fire", s -> s.status = StatusEffects.burning),
            damage(40),
            range(20),
            step("Melting: the laser melts enemies, weakening their armor", s -> s.status = StatusEffects.melting),
            damage(60),
            damage(60),
            step("Focus: +100% damage", s -> s.damage += 1f)
        );
        define(Blocks.scathe, 100f,
            reload(20),
            water(),
            shots(1),
            loads(Items.phaseFabric, "slow missiles that carry a force field and blast a wider area"),
            reload(20),
            cryo(),
            shots(1),
            loads(Items.surgeAlloy, "missiles split into 5 smaller missiles and throw lightning"),
            step("Doomsday: +1 missile and +50% damage", s -> {
                s.shots += 1;
                s.damage += 0.5f;
            })
        );
        define(Blocks.smite, 110f,
            water(),
            pierce(2),
            damage(50),
            step("Thunder: each hit throws 2 lightning bolts", s -> s.lightning += 2),
            cryo(),
            range(20),
            shots(1),
            damage(50),
            step("Judgement: +100% damage", s -> s.damage += 1f)
        );
        define(Blocks.malign, 250f,
            water(),
            damage(40),
            step("Hunter: charges home in much harder", s -> s.homing += 0.1f),
            shots(1),
            cryo(),
            step("Overload: each charge throws 3 lightning bolts", s -> s.lightning += 3),
            damage(50),
            shots(1),
            step("Abyss: +100% damage", s -> s.damage += 1f)
        );
    }
}

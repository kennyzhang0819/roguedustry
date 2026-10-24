package mindustrysurvivors;

import arc.func.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.entities.bullet.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;

public class Weapons{
    public static final int maxLevel = 10;
    //global turret balance: multipliers on top of the base game's numbers, the same for every turret, before each turret's own balance factor
    public static float globalDamage = 1f, globalFireRate = 2f, globalRange = 1f;
    public static final float levelDamage = 0.2f;
    public static final ObjectFloatMap<Block> niche = new ObjectFloatMap<>();
    public static final OrderedMap<Block, Seq<Step>> steps = new OrderedMap<>();
    public static final ObjectMap<Block, String> intro = new ObjectMap<>();
    static final ObjectMap<Block, UnlockableContent> startAmmo = new ObjectMap<>();

    public static class Spec{
        public @Nullable UnlockableContent ammo;
        public float damage = 1f, reload = 1f, cool = 1f, range = 1f, speed = 1f, blast = 1f, burn = 1f, force = 1f, puddle = 1f, homing, knock, salvage;
        public boolean disarm;
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
        if(turret instanceof PowerTurret power) return power.shootType;
        if(turret instanceof ContinuousTurret cont) return cont.shootType;
        return null;
    }

    public static @Nullable UnlockableContent firstAmmo(BaseTurret turret){
        UnlockableContent start = startAmmo.get(turret);
        if(turret instanceof ItemTurret it && start instanceof Item i && it.ammoTypes.containsKey(i)) return start;
        if(turret instanceof LiquidTurret lt && start instanceof Liquid l && lt.ammoTypes.containsKey(l)) return start;
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
        return hit;
    }

    public static float gameDps(BaseTurret block){
        if(block instanceof TractorBeamTurret tractor) return tractor.damage * 60f;
        if(!(block instanceof Turret turret)) return 0f;
        BulletType b = ammo(turret, null);
        float reload = turret.reload / b.reloadMultiplier;
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

    public static BulletType make(Turret turret, Spec s){
        BulletType base = ammo(turret, s.ammo);
        BulletType b = base.copy();
        float dmg = s.damage * balance(turret) * globalDamage, rng = s.range * globalRange;
        float reach = turret.range * rng;
        b.damage *= dmg;
        b.splashDamage *= dmg;
        //liquid sprays do no damage in the base game, so a support turret's damage comes straight from its niche
        if(gameDps(turret) <= 0f && niche.get(turret, 0f) > 0f){
            b.damage = niche.get(turret, 0f) * (turret.reload / base.reloadMultiplier) / 60f / turret.shoot.shots * s.damage * globalDamage;
        }
        if(b.lightningDamage > 0f) b.lightningDamage *= dmg;
        if(b.fragBullet != null){
            b.fragBullet = b.fragBullet.copy();
            b.fragBullet.damage *= dmg;
            b.fragBullet.splashDamage *= dmg;
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
        b.knockback = (b.knockback + s.knock) * s.force;
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
        if(s.status != null) b.status = s.status;
        b.statusDuration *= s.burn;
        if(s.fire > 0){
            b.incendAmount += s.fire;
            b.incendChance = Math.max(b.incendChance, 0.5f);
            b.incendSpread = Math.max(b.incendSpread, 8f);
        }
        b.hitShake = b.despawnShake = 0f;
        if(b.fragBullet != null) b.fragBullet.hitShake = b.fragBullet.despawnShake = 0f;
        b.collidesAir = b.collidesGround = true;
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

    public static void load(){
        //other mods can replace a turret's whole ammo list, so each turret's starting ammo is named here instead of taken from the list
        startAmmo.putAll(Blocks.duo, Items.copper, Blocks.scatter, Items.scrap, Blocks.scorch, Items.coal, Blocks.hail, Items.graphite,
            Blocks.wave, Liquids.water, Blocks.swarmer, Items.blastCompound, Blocks.salvo, Items.copper, Blocks.tsunami, Liquids.water,
            Blocks.fuse, Items.titanium, Blocks.ripple, Items.graphite, Blocks.cyclone, Items.metaglass, Blocks.foreshadow, Items.surgeAlloy,
            Blocks.spectre, Items.graphite);
        define(Blocks.wave, 40f,
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
        define(Blocks.tsunami, 60f,
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
        define(Blocks.parallax, 30f,
            beams(1),
            force(40, "push"),
            range(30),
            step("Disrupt: held enemies can't shoot", s -> s.disarm = true),
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
        intro.put(Blocks.parallax, "Orbits you and, while you shoot, grabs the enemy nearest your aim, pushing it away and slowing it.");
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
        define(Blocks.fuse, 400f,
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
    }
}

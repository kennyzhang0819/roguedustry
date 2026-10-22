package roguedustry;

import arc.math.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.Rules.*;
import mindustry.type.*;

public class Modifiers{

    public interface RuleChange{
        void apply(Rules rules, TeamRule player, TeamRule enemy, int stacks);
    }

    public static class Modifier{
        public final String id, name, description;
        public final int max;
        public final boolean risky;
        final RuleChange rules;

        Modifier(String id, String name, int max, boolean risky, String description, RuleChange rules){
            this.id = id;
            this.name = name;
            this.max = max;
            this.risky = risky;
            this.description = description;
            this.rules = rules;
        }
    }

    public static final Modifier
    quickHands = new Modifier("quick-hands", "Quick Hands", 3, false, "Blocks build 50% faster.",
        (r, p, e, n) -> p.buildSpeedMultiplier *= 1f + 0.5f * n),
    reinforced = new Modifier("reinforced", "Reinforced", 3, false, "Your blocks have 25% more health.",
        (r, p, e, n) -> p.blockHealthMultiplier *= 1f + 0.25f * n),
    hotBarrels = new Modifier("hot-barrels", "Hot Barrels", 3, false, "Your turrets deal 20% more damage.",
        (r, p, e, n) -> p.blockDamageMultiplier *= 1f + 0.2f * n),
    frugal = new Modifier("frugal", "Frugal", 3, false, "Blocks cost 15% less to build.",
        (r, p, e, n) -> r.buildCostMultiplier *= Mathf.pow(0.85f, n)),
    salvage = new Modifier("salvage", "Full Salvage", 1, false, "Removing a block gives back everything it cost.",
        (r, p, e, n) -> r.deconstructRefundMultiplier = 1f),
    sunny = new Modifier("sunny", "Clear Skies", 2, false, "Solar panels make twice the power.",
        (r, p, e, n) -> r.solarMultiplier *= 1f + n),
    assembly = new Modifier("assembly", "Assembly Line", 3, false, "Unit factories work 40% faster.",
        (r, p, e, n) -> p.unitBuildSpeedMultiplier *= 1f + 0.4f * n),
    veterans = new Modifier("veterans", "Veterans", 3, false, "Your units have 20% more health and deal 20% more damage.",
        (r, p, e, n) -> {
            p.unitHealthMultiplier *= 1f + 0.2f * n;
            p.unitDamageMultiplier *= 1f + 0.2f * n;
        }),
    cheapUnits = new Modifier("cheap-units", "Mass Production", 3, false, "Units cost 20% less.",
        (r, p, e, n) -> p.unitCostMultiplier *= Mathf.pow(0.8f, n)),
    supplyDrop = new Modifier("supply-drop", "Supply Drop", 3, false, "Start every mission with 800 more copper and lead.",
        (r, p, e, n) -> {
            addLoadout(r, Items.copper, 800 * n);
            addLoadout(r, Items.lead, 800 * n);
        }),
    scholar = new Modifier("scholar", "Scholar", 2, false, "Research costs 25% less.", null),
    deepDrills = new Modifier("deep-drills", "Deep Drills", 3, false, "Drills work 30% faster.", null),
    overclock = new Modifier("overclock", "Overclocked Factories", 3, false, "Factories that make items or liquids work 25% faster.", null),
    expressBelts = new Modifier("express-belts", "Express Belts", 2, false, "Conveyors, junctions and bridges move items 50% faster.", null),
    rapidFire = new Modifier("rapid-fire", "Rapid Fire", 3, false, "Turrets fire 15% faster.", null),
    bounty = new Modifier("bounty", "Bounty", 3, false, "Killed enemies drop copper into your core.", null),
    glassCannon = new Modifier("glass-cannon", "Glass Cannon", 1, true, "Turrets deal 50% more damage, but your blocks have 30% less health.",
        (r, p, e, n) -> {
            p.blockDamageMultiplier *= 1.5f;
            p.blockHealthMultiplier *= 0.7f;
        }),
    greed = new Modifier("greed", "Greed", 1, true, "Drills work 60% faster, but enemies have 25% more health.",
        (r, p, e, n) -> e.unitHealthMultiplier *= 1.25f),
    overtime = new Modifier("overtime", "Overtime", 1, true, "Everything you build works 20% faster, but enemy waves come 25% sooner.",
        (r, p, e, n) -> r.waveSpacing *= 0.75f);

    public static final Seq<Modifier> all = Seq.with(
        quickHands, reinforced, hotBarrels, frugal, salvage, sunny, assembly, veterans, cheapUnits, supplyDrop,
        scholar, deepDrills, overclock, expressBelts, rapidFire, bounty, glassCannon, greed, overtime
    );

    public static Modifier get(String id){
        return all.find(m -> m.id.equals(id));
    }

    static void addLoadout(Rules rules, Item item, int amount){
        ItemStack stack = rules.loadout.find(s -> s.item == item);
        if(stack == null) rules.loadout.add(new ItemStack(item, amount));
        else stack.amount += amount;
    }

    public static void apply(ObjectIntMap<Modifier> owned, Rules rules){
        TeamRule player = rules.teams.get(rules.defaultTeam), enemy = rules.teams.get(rules.waveTeam);
        for(var e : owned){
            if(e.key.rules != null && e.value > 0) e.key.rules.apply(rules, player, enemy, e.value);
        }
    }

    public static Seq<Modifier> roll(ObjectIntMap<Modifier> owned, Rand rand){
        Seq<Modifier> pool = all.select(m -> owned.get(m, 0) < m.max), out = new Seq<>();
        while(out.size < 3 && pool.any()){
            out.add(pool.remove(rand.random(pool.size - 1)));
        }
        return out;
    }
}

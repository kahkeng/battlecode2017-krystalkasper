package x_Base;

import battlecode.common.RobotController;
import battlecode.common.RobotType;
import battlecode.common.Team;

public strictfp enum StrategyFeature {
    SCOUT_DISTANCE_ATTACK("scout_distance_attack", false), GARDENER_PLANT_NEAR_ARCHON("gardener_plant_near_archon",
            false), IMPROVED_COMBAT1("improved_combat1",
                    true), LUMBERJACK_FOCUS("lumberjack_focus", true), COMBAT_SNIPE_BASES("combat_snipe_bases",
                            true), GARDENER_FARM_TRIANGLE("gardener_farm_triangle",
                                    true), COMBAT_SPRAY1("combat_spray1", true), COMBAT_DODGE1("combat_dodge1",
                                            false), COMBAT_DODGE2("combat_dodge2",
                                                    true), COMBAT_COUNTER_DODGE("combat_counter_dodge",
                                                            true), COMBAT_BROADCAST("combat_broadcast",
                                                                    true), COMBAT_UNSEEN_DEFENSE(
                                                                            "combat_unseen_defense",
                                                                            true), COMBAT_LAST_SENSED(
                                                                                    "combat_last_sensed",
                                                                                    true), COMBAT_SPRAY_TANK(
                                                                                            "combat_spray_tank",
                                                                                            true), COMBAT_IGNORE_ENEMY_TREES(
                                                                                                    "combat_ignore_enemy_trees",
                                                                                                    true);

    public final String codename;
    private boolean enabled, emitted;

    private StrategyFeature(final String codename, final boolean enabledByDefault) {
        this.codename = codename;
        this.emitted = false;
        this.enabled = enabledByDefault;
    }

    public static final void initialize(RobotController rc) {
        final Team team = rc.getTeam();
        final String property = System.getProperty("bc.testing.strategy-features-" + team.name().toLowerCase());
        if (property != null && property.length() > 0) {
            if (rc.getRoundNum() == 1 && rc.getType() == RobotType.ARCHON) {
                // rc.addMatchObservation(team + ":" + property);
                System.out.println("StrategyFeature: team-" + team + "=" + property);
            }
            for (final StrategyFeature feature : StrategyFeature.values()) {
                if (property.indexOf("," + feature.codename) >= 0) {
                    feature.enabled = true;
                } else if (property.indexOf(",!" + feature.codename) >= 0) {
                    feature.enabled = false;
                }
            }
        }
    }

    public final boolean enabled() {
        if (!emitted) {
            emitted = true;
            System.out.println("StrategyFeature: " + codename + "=" + enabled);
        }
        return enabled;
    }

}

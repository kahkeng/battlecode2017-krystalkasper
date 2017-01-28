package x_Base;

import battlecode.common.RobotController;
import battlecode.common.RobotType;
import battlecode.common.Team;

public strictfp enum StrategyFeature {
    SCOUT_DISTANCE_ATTACK("scout_distance_attack", false), GARDENER_PLANT_NEAR_ARCHON("gardener_plant_near_archon",
            false), IMPROVED_COMBAT1("improved_combat1",
                    true), LUMBERJACK_FOCUS("lumberjack_focus", true), COMBAT_SNIPE_BASES("combat_snipe_bases", false);

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
        if (property != null) {
            if (rc.getRoundNum() == 1 && rc.getType() == RobotType.ARCHON) {
                // rc.addMatchObservation(team + ":" + property);
                System.out.println("StrategyFeature: team-" + team + "=" + property);
            }
            for (final StrategyFeature feature : StrategyFeature.values()) {
                feature.enabled = property.indexOf("," + feature.codename) >= 0;
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

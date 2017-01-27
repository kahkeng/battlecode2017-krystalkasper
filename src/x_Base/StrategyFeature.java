package x_Base;

import battlecode.common.RobotController;
import battlecode.common.RobotType;
import battlecode.common.Team;

public strictfp enum StrategyFeature {
    SCOUT_DISTANCE_ATTACK("scout_distance_attack"), GARDENER_PLANT_NEAR_ARCHON("gardener_plant_near_archon");

    public final String codename;
    private boolean enabled, emitted;

    private StrategyFeature(final String codename) {
        this.codename = codename;
        this.emitted = false;
    }

    public static final void initialize(RobotController rc) {
        final Team team = rc.getTeam();
        final String property = System.getProperty("bc.testing.strategy-features-" + team.name().toLowerCase());
        if (rc.getRoundNum() == 1 && rc.getType() == RobotType.ARCHON) {
            // rc.addMatchObservation(team + ":" + property);
            System.out.println("StrategyFeature: team-" + team + "=" + property);
        }
        for (final StrategyFeature feature : StrategyFeature.values()) {
            feature.enabled = property != null && property.indexOf("," + feature.codename) >= 0;
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

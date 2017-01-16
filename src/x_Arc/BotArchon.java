package x_Arc;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import x_Base.Formations;

public strictfp class BotArchon extends x_Base.BotArchon {

    /** Hire a gardener if none in this radius. */
    public static final float GARDENER_RADIUS = 7.0f;

    public final Formations formation;

    public BotArchon(final RobotController rc) {
        super(rc);
        formation = new Formations(this);
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();
                // Messaging.broadcastArchonLocation(this);
                hireGardenersIfNoneAround();

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

    public final void hireGardenersIfNoneAround() throws GameActionException {
        final RobotInfo[] robots = rc.senseNearbyRobots(GARDENER_RADIUS, myTeam);
        boolean found = false;
        for (final RobotInfo robot : robots) {
            if (robot.type != RobotType.GARDENER) {
                continue;
            }
            found = true;
            break;
        }
        if (!found) {
            tryHireGardener(formation.getArcDir(myLoc).opposite());
        }
    }

}

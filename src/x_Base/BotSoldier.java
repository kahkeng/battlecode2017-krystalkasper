package x_Base;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import x_Arc.BotArcBase;

public strictfp class BotSoldier extends BotArcBase {

    /** Patrol at this distance from arc and at this step. */
    public static final float PATROL_RADIUS = 5.0f;

    public BotSoldier(final RobotController rc) {
        super(rc);
        DEBUG = true;
        radianStep = formation.getRadianStep(myLoc, PATROL_RADIUS);
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();

                if (!attackEnemies()) {
                    patrolAlongArc();
                }

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    public final boolean attackEnemies() throws GameActionException {
        return Combat.seekAndAttackAndSurroundEnemy(this);
    }

    public final void patrolAlongArc() throws GameActionException {
        final MapLocation arcLoc = getArcLoc();
        final MapLocation patrolLoc = arcLoc.add(arcDirection.opposite(), PATROL_RADIUS);
        if (myLoc.distanceTo(patrolLoc) > PATROL_RADIUS) {
            // move towards arcLoc if possible
            if (!tryMove(patrolLoc)) {
                reverseArcDirection();
            }
            return;
        }
        advanceArcDirection();
    }

}

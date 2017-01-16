package x_Arc;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import x_Base.Formations;

public strictfp class BotArcBase extends x_Base.BotBase {

    /** Patrol at this distance from arc and at this step. */
    public static final float PATROL_RADIUS = 5.0f;

    /** Flee at this distance from arc and at this step. */
    public static final float FLEE_RADIUS = 4.0f;

    public final Formations formation;
    public static Direction arcDirection;
    public static float radianStep; // positive is rotating clockwise relative to enemy base

    public BotArcBase(final RobotController rc) {
        super(rc);
        formation = new Formations(this);
        myLoc = rc.getLocation();
        arcDirection = formation.getArcDir(myLoc);
        radianStep = formation.getRadianStep(myLoc, FLEE_RADIUS); // default radian step
    }

    public final void advanceArcDirection() {
        arcDirection = arcDirection.rotateRightRads(radianStep * 2);
    }

    public final void reverseArcDirection() {
        radianStep = -radianStep;
        advanceArcDirection();
    }

    public final MapLocation getArcLoc() {
        return formation.getArcLoc(arcDirection);
    }

    public final MapLocation getNextArcLoc() {
        return formation.getArcLoc(arcDirection.rotateRightRads(radianStep * 2));
    }

    public final void patrolAlongArc() throws GameActionException {
        final MapLocation arcLoc = getArcLoc();
        final boolean isOutside = myLoc.distanceTo(formation.getArcCenter()) > formation.separation;
        final MapLocation patrolLoc = arcLoc.add(isOutside ? arcDirection : arcDirection.opposite(), PATROL_RADIUS);
        if (myLoc.distanceTo(patrolLoc) > PATROL_RADIUS) {
            // move towards arcLoc if possible
            if (!tryMove(patrolLoc)) {
                reverseArcDirection();
            }
            return;
        }
        advanceArcDirection();
    }

    public final void fleeFromEnemyAlongArc(final MapLocation enemyLoc) throws GameActionException {
        final MapLocation arcCenter = formation.getArcCenter();
        final Direction currArcDirection = arcCenter.directionTo(myLoc);
        final Direction enemyArcDirection = arcCenter.directionTo(enemyLoc);
        if (currArcDirection.radiansBetween(enemyArcDirection) * radianStep < 0) {
            radianStep = -radianStep;
        }
        if (arcDirection.radiansBetween(currArcDirection) * radianStep < 0) {
            advanceArcDirection();
        }
        final MapLocation arcLoc = getArcLoc();
        final MapLocation fleeLoc = arcLoc.add(arcDirection, FLEE_RADIUS);
        rc.setIndicatorDot(fleeLoc, 255, 0, 255);
        if (myLoc.distanceTo(fleeLoc) > FLEE_RADIUS) {
            if (!tryMove(fleeLoc)) {
                randomlyJitter();
            }
        } else {
            advanceArcDirection();
            fleeFromEnemyAlongArc(enemyLoc);
        }
    }

}

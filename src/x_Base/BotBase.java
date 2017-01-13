package x_Base;

import battlecode.common.BulletInfo;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotType;
import battlecode.common.Team;

public class BotBase {

    public static final int MAX_ENEMY_ROBOTS = 10; // in message broadcasts

    public final RobotController rc;
    public final Team myTeam;
    public final Team enemyTeam;
    public final MapLocation[] myInitialArchonLocs;
    public final int numInitialArchons;
    public final RobotType myType;
    public final int myID;

    public BotBase(final RobotController rc) {
        this.rc = rc;
        myTeam = rc.getTeam();
        enemyTeam = myTeam.opponent();
        myInitialArchonLocs = rc.getInitialArchonLocations(myTeam);
        numInitialArchons = myInitialArchonLocs.length;
        myType = rc.getType();
        myID = rc.getID();
        StrategyFeature.initialize(rc);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir
     *            The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    public final boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir, myType.strideRadius, 20, 3);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param dir
     *            The intended direction of movement
     * @param moveDistance
     *            The intended distance of movement
     * @param degreeOffset
     *            Spacing between checked directions (degrees)
     * @param checksPerSide
     *            Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    public final boolean tryMove(Direction dir, float moveDistance, float degreeOffset, int checksPerSide)
            throws GameActionException {

        // First, try intended direction
        if (rc.canMove(dir, moveDistance)) {
            rc.move(dir, moveDistance);
            return true;
        }

        // Now try a bunch of similar angles
        int currentCheck = 1;

        while (currentCheck <= checksPerSide) {
            // Try the offset of the left side
            final float offset = degreeOffset * currentCheck;
            final Direction leftDir = dir.rotateLeftDegrees(offset);
            if (rc.canMove(leftDir, moveDistance)) {
                rc.move(leftDir, moveDistance);
                return true;
            }
            // Try the offset on the right side
            final Direction rightDir = dir.rotateRightDegrees(offset);
            if (rc.canMove(rightDir, moveDistance)) {
                rc.move(rightDir, moveDistance);
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    /**
     * A slightly more complicated example function, this returns true if the given bullet is on a collision course with
     * the current robot. Doesn't take into account objects between the bullet and this robot.
     *
     * @param bullet
     *            The bullet in question
     * @return True if the line of the bullet's path intersects with this robot's current position.
     */
    public final boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();

        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI / 2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with
        // propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float) Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }
}

package x_Base;

import battlecode.common.BulletInfo;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotType;
import battlecode.common.Team;
import battlecode.common.TreeInfo;
import x_Base.TangentBugNavigator.ObstacleInfo;

public strictfp class BotBase {

    public static final float MAX_BULLET_STASH = 1000.0f;
    public static final int MAX_ENEMY_ROBOTS = 10; // in message broadcasts
    public static final float ARCHON_SHAKE_DISTANCE = 10.0f;
    public static final float FLEE_DISTANCE = 5.0f;
    public static boolean DEBUG = false;

    public final RobotController rc;
    public final Team myTeam;
    public final Team enemyTeam;
    public final MapLocation[] myInitialArchonLocs;
    public final MapLocation[] enemyInitialArchonLocs;
    public final int numInitialArchons;
    public final RobotType myType;
    public final int myID;
    public final MapEdges mapEdges;
    public final TangentBugNavigator nav;
    public final Formations formation;
    public final Meta meta;

    public static MapLocation homeArchon = null;
    public static MapLocation myLoc = null;
    public static final MapLocation[] broadcastedEnemies = new MapLocation[BotBase.MAX_ENEMY_ROBOTS + 1];
    public static boolean preferRight = false;
    public static float lastRoundBullets;
    public static float bulletsDelta;

    public BotBase(final RobotController rc) {
        this.rc = rc;
        myTeam = rc.getTeam();
        enemyTeam = myTeam.opponent();
        myInitialArchonLocs = rc.getInitialArchonLocations(myTeam);
        enemyInitialArchonLocs = rc.getInitialArchonLocations(myTeam.opponent());
        numInitialArchons = myInitialArchonLocs.length;
        myType = rc.getType();
        myID = rc.getID();
        mapEdges = new MapEdges(this);
        nav = new TangentBugNavigator(this);
        formation = new Formations(this);
        meta = new Meta(this);
        lastRoundBullets = rc.getTeamBullets();
        bulletsDelta = 0;
        myLoc = rc.getLocation();
    }

    public final void startLoop() throws GameActionException {
        myLoc = rc.getLocation();
        shakeTrees();
        final float bullets = rc.getTeamBullets();
        if (rc.getRoundNum() == rc.getRoundLimit() || rc.getTeamVictoryPoints()
                + bullets / rc.getVictoryPointCost() >= GameConstants.VICTORY_POINTS_TO_WIN) {
            rc.donate(bullets);
        } else if (bullets > MAX_BULLET_STASH) {
            rc.donate((int) ((bullets - MAX_BULLET_STASH) / rc.getVictoryPointCost())
                    * rc.getVictoryPointCost());
        }
        bulletsDelta = bullets - lastRoundBullets;
        lastRoundBullets = rc.getTeamBullets(); // use current figure in case we donated above
        mapEdges.detectMapEdges();
        Messaging.processBroadcastedMapEdges(this);
    }

    public final void findHomeArchon() throws GameActionException {
        final MapLocation[] myArchons = Messaging.readArchonLocation(this);
        homeArchon = null;
        float minDistance = 0;
        for (final MapLocation archon : myArchons) {
            if (archon == null) {
                continue;
            }
            final float distance = archon.distanceTo(myLoc);
            if (homeArchon == null || distance < minDistance) {
                homeArchon = archon;
                minDistance = distance;
            }
        }
    }

    public final TreeInfo archonFindTreesToShake() throws GameActionException {
        TreeInfo nearestTree = null;
        float nearestDist = 0;
        Direction dir = Direction.NORTH;
        for (int i = 0; i < 12; i++) {
            final ObstacleInfo obstacle = Combat.whichRobotOrTreeWillObjectCollideWith(this,
                    dir, ARCHON_SHAKE_DISTANCE, myType.bodyRadius);
            if (obstacle != null && !obstacle.isRobot) {
                final TreeInfo tree = rc.senseTree(obstacle.id);
                if (tree.containedBullets > 0) {
                    final float dist = myLoc.distanceTo(tree.location);
                    if (nearestTree == null || dist < nearestDist) {
                        nearestTree = tree;
                        nearestDist = dist;
                    }
                }
            }
            dir = dir.rotateRightDegrees(30.0f);
        }
        return nearestTree;
    }

    public final TreeInfo scoutFindTreesToShake() throws GameActionException {
        TreeInfo nearestTree = null;
        float nearestDist = 0;
        final TreeInfo[] trees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        for (final TreeInfo tree : trees) {
            if (tree.containedBullets > 0) {
                final float dist = myLoc.distanceTo(tree.location);
                if (nearestTree == null || dist < nearestDist) {
                    nearestTree = tree;
                    nearestDist = dist;
                }
            }
        }
        return nearestTree;
    }

    public final void shakeTrees() throws GameActionException {
        final TreeInfo[] trees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        for (final TreeInfo tree : trees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
                return;
            }
        }
    }

    public final void fleeFromEnemy(final MapLocation enemyLoc) throws GameActionException {
        final Direction enemyDir = enemyLoc.directionTo(myLoc);
        final MapLocation fleeLoc = myLoc.add(enemyDir, FLEE_DISTANCE);
        final MapLocation fleeLoc2;
        if (mapEdges.distanceFromCorner(fleeLoc) < FLEE_DISTANCE * 3) {
            final MapLocation fleeLocL = myLoc.add(enemyDir.rotateLeftDegrees(45.0f), FLEE_DISTANCE);
            final MapLocation fleeLocR = myLoc.add(enemyDir.rotateRightDegrees(45.0f), FLEE_DISTANCE);
            if (mapEdges.distanceFromCorner(fleeLocL) > mapEdges.distanceFromCorner(fleeLocR)) {
                fleeLoc2 = fleeLocL;
            } else {
                fleeLoc2 = fleeLocR;
            }
        } else {
            fleeLoc2 = fleeLoc;
        }
        nav.setDestination(fleeLoc2);
        if (!tryMove(nav.getNextLocation())) {
            randomlyJitter();
        }
    }

    public final boolean tryBuildRobot(final RobotType buildType, Direction dir) throws GameActionException {
        final int numTries = 12;
        final float degreeDelta = 360.0f / numTries;
        int i = 0;
        while (i < numTries && !rc.canBuildRobot(buildType, dir)) {
            i += 1;
            final int sign = (i % 2) * 2 - 1;
            dir = dir.rotateRightDegrees(degreeDelta * i * sign);
        }
        if (i < numTries) {
            rc.buildRobot(buildType, dir);
            return true;
        }
        return false;
    }

    public final boolean tryHireGardener(Direction dir) throws GameActionException {
        final int numTries = 12;
        final float degreeDelta = 360.0f / numTries;
        int i = 0;
        while (i < numTries && !rc.canHireGardener(dir)) {
            i += 1;
            final int sign = (i % 2) * 2 - 1;
            dir = dir.rotateRightDegrees(degreeDelta * i * sign);
        }
        if (i < numTries) {
            rc.hireGardener(dir);
            return true;
        }
        return false;
    }

    public final boolean tryHireGardenerWithSpace(Direction dir) throws GameActionException {
        final int numTries = 12;
        final float degreeDelta = 360.0f / numTries;
        for (int i = 0; i < numTries; i++) {
            if (rc.canHireGardener(dir)) {
                final MapLocation gardenerLoc = myLoc.add(dir,
                        myType.bodyRadius + RobotType.GARDENER.bodyRadius + 0.01f);
                int canBuild = 0;
                final float buildRadius = RobotType.SOLDIER.bodyRadius;
                Direction dir2 = dir;
                for (int j = 0; j < 6; j++) {
                    final MapLocation buildLoc = gardenerLoc.add(dir2,
                            RobotType.GARDENER.bodyRadius + buildRadius + 0.01f);
                    if (rc.senseNearbyRobots(buildLoc, buildRadius, null).length == 0
                            && rc.senseNearbyTrees(buildLoc, buildRadius, null).length == 0) {
                        canBuild += 1;
                        break;
                    }
                    dir2 = dir2.rotateRightDegrees(60.0f);
                }
                if (canBuild > 0) {
                    break;
                }
            }
            final int sign = (i % 2) * 2 - 1;
            dir = dir.rotateRightDegrees(degreeDelta * i * sign);
        }
        if (rc.canHireGardener(dir)) {
            rc.hireGardener(dir);
            return true;
        }
        return false;
    }

    public final boolean tryMove(final MapLocation loc) throws GameActionException {
        return tryMove(loc, myType.strideRadius);
    }

    public final boolean tryMove(final MapLocation loc, final float moveDistance) throws GameActionException {
        if (rc.hasMoved() || loc == null) {
            return false;
        }
        // First, try intended direction
        final float locDistance = myLoc.distanceTo(loc);
        if (locDistance <= moveDistance && rc.canMove(loc)) {
            rc.move(loc);
            startLoop();
            return true;
        }
        // At this point, either locDistance is further than moveDistance, or loc is occupied
        final Direction dir = myLoc.directionTo(loc);
        return tryMove(dir, Math.min(moveDistance, locDistance), 15, 6);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir
     *            The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    public final boolean tryMove(final Direction dir) throws GameActionException {
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
    public final boolean tryMove(final Direction dir, final float moveDistance, final float degreeOffset,
            final int checksPerSide)
            throws GameActionException {

        if (rc.hasMoved()) {
            return false;
        }

        // First, try intended direction
        if (rc.canMove(dir, moveDistance)) {
            rc.move(dir, moveDistance);
            startLoop();
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
                startLoop();
                return true;
            }
            // Try the offset on the right side
            final Direction rightDir = dir.rotateRightDegrees(offset);
            if (rc.canMove(rightDir, moveDistance)) {
                rc.move(rightDir, moveDistance);
                startLoop();
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    public final void randomlyJitter() throws GameActionException {
        float radius = myType.strideRadius;
        while (!rc.hasMoved() && !tryMove(Util.randomDirection(), radius, 20, 18)) {
            radius /= 2;
        }
    }

    /**
     * A slightly more complicated example function, this returns true if the given bullet is on a collision course with
     * the current robot. Doesn't take into account objects between the bullet and this robot.
     *
     * @param bullet
     *            The bullet in question
     * @return True if the line of the bullet's path intersects with this robot's current position.
     */
    public final boolean willCollideWithMe(final BulletInfo bullet) {
        final MapLocation bulletLocation = bullet.location;
        final float theta = bullet.dir.radiansBetween(bulletLocation.directionTo(myLoc));

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI / 2) {
            return false;
        }

        return ((float) Math.abs(bulletLocation.distanceTo(myLoc) * Math.sin(theta)) <= rc.getType().bodyRadius);
    }
}

package x_Base;

import battlecode.common.BulletInfo;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;
import battlecode.common.TreeInfo;
import x_Base.TangentBugNavigator.ObstacleInfo;

public strictfp class BotBase {

    public static final float MAX_BULLET_STASH = 1000.0f;
    public static final float ARCHON_SHAKE_DISTANCE = 10.0f;
    public static final float FLEE_DISTANCE = 5.0f;
    public static final float BROADCAST_WATER_TREE_THRESHOLD = GameConstants.BULLET_TREE_MAX_HEALTH - 20.0f;
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
    public static MapLocation lastBaseLoc = null;
    public static final MapLocation[] broadcastedPriorityEnemies = new MapLocation[Messaging.MAX_PRIORITY_ENEMY_ROBOTS
            + 1];
    public static final MapLocation[] broadcastedEnemies = new MapLocation[Messaging.MAX_ENEMY_ROBOTS + 1];
    public static final MapLocation[] broadcastedEnemyGardeners = new MapLocation[Messaging.MAX_ENEMY_GARDENERS + 1];
    public static final MapLocation[] broadcastedNeutralTrees = new MapLocation[Messaging.MAX_NEUTRAL_TREES + 1];
    public static final MapLocation[] broadcastedMyTrees = new MapLocation[Messaging.MAX_MY_TREES + 1];
    public static final float[] broadcastedMyTreesHealth = new float[Messaging.MAX_MY_TREES + 1];
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
        if (rc.getRoundNum() == rc.getRoundLimit() - 1 || rc.getTeamVictoryPoints() + bullets /
                rc.getVictoryPointCost() >= GameConstants.VICTORY_POINTS_TO_WIN) {
            rc.donate(bullets);
        } else if (bullets > MAX_BULLET_STASH) {
            rc.donate((int) ((bullets - MAX_BULLET_STASH) / rc.getVictoryPointCost())
                    * rc.getVictoryPointCost());
        } else if (rc.getRoundNum() >= 500 && Messaging.getNumSurvivingArchons(this) == 0
                && rc.getTreeCount() == 0 && bullets > GameConstants.BULLET_TREE_COST + 10.0f) {
            // no gardener or unable to spawn tree
            rc.donate((int) (bullets / rc.getVictoryPointCost())
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
            final ObstacleInfo obstacle = TangentBugNavigator.whichRobotOrTreeWillObjectCollideWith(this,
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
        final TreeInfo[] trees = rc
                .senseNearbyTrees(myType.bodyRadius + GameConstants.INTERACTION_DIST_FROM_EDGE + 0.01f, Team.NEUTRAL);
        for (final TreeInfo tree : trees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
                return;
            }
        }
    }

    public final void broadcastMyTrees(final TreeInfo[] trees) throws GameActionException {
        for (final TreeInfo tree : trees) {
            if (tree.health >= BROADCAST_WATER_TREE_THRESHOLD) {
                continue;
            }
            Messaging.broadcastMyTree(this, tree);
        }
    }

    public final MapLocation getNearestArchon() throws GameActionException {
        final MapLocation[] myArchons = Messaging.readArchonLocation(this);
        MapLocation nearestArchon = null;
        float nearestDist = 0;
        for (final MapLocation myArchon : myArchons) {
            if (myArchon == null) {
                continue;
            }
            final float dist = myArchon.distanceTo(myLoc);
            if (nearestArchon == null || dist < nearestDist) {
                nearestArchon = myArchon;
                nearestDist = dist;
            }
        }
        if (nearestArchon == null) {
            for (final MapLocation myArchon : myInitialArchonLocs) {
                final float dist = myArchon.distanceTo(myLoc);
                if (nearestArchon == null || dist < nearestDist) {
                    nearestArchon = myArchon;
                    nearestDist = dist;
                }
            }
        }
        return nearestArchon;
    }

    public final void moveTowardsTreeBorder() throws GameActionException {
        // If I don't see any of my own bullet trees, head towards nearest archon
        final TreeInfo[] myTrees = rc.senseNearbyTrees(-1, myTeam);
        final MapLocation nearestArchon = getNearestArchon();
        if (myTrees.length == 0) {
            nav.setDestination(nearestArchon);
            if (!tryMove(nav.getNextLocation())) {
                randomlyJitter();
            }
            return;
        }

        final MapLocation enemyLoc = Messaging.getLastEnemyLocation(this);
        if (enemyLoc != null) {
            nav.setDestination(enemyLoc);
            if (!tryMove(nav.getNextLocation())) {
                randomlyJitter();
            }
            return;
        }

        // Get centroid of our trees
        float x = 0, y = 0;
        for (final TreeInfo tree : myTrees) {
            x += tree.location.x;
            y += tree.location.y;
        }
        final MapLocation centroid = new MapLocation(x / myTrees.length, y / myTrees.length);

        final MapLocation moveLoc = centroid.add(nearestArchon.directionTo(centroid), myType.sensorRadius - 1.0f);
        if (!tryMove(moveLoc)) {
            randomlyJitter();
        }

    }

    public final void moveTowardsTreeBorder2() throws GameActionException {
        // If I don't see any of my own bullet trees, head towards last base loc to
        // find nearest gardener, else archon
        final TreeInfo[] myTrees = rc.senseNearbyTrees(-1, myTeam);

        if (myTrees.length == 0) {
            if (lastBaseLoc == null || myLoc.distanceTo(lastBaseLoc) <= 4.0f) {
                MapLocation baseLoc = null; // either archon or gardener
                final RobotInfo[] friendlies = rc.senseNearbyRobots(-1, myTeam);
                outer: for (final RobotInfo friendly : friendlies) {
                    switch (friendly.type) {
                    case GARDENER:
                        baseLoc = friendly.location;
                        break outer;
                    default:
                        break;
                    }
                }
                if (baseLoc == null) {
                    baseLoc = getNearestArchon();
                    lastBaseLoc = null;
                } else {
                    lastBaseLoc = baseLoc;
                }

                final float baseDist = myLoc.distanceTo(baseLoc);
                if (baseDist > 6.0f) {
                    nav.setDestination(baseLoc);
                    if (!tryMove(nav.getNextLocation())) {
                        randomlyJitter();
                    }
                } else if (baseDist < 4.0f) {
                    nav.setDestination(baseLoc.add(baseLoc.directionTo(myLoc), 5.0f));
                    if (!tryMove(nav.getNextLocation())) {
                        randomlyJitter();
                    }
                } else {
                    randomlyJitter();
                }
            } else {
                nav.setDestination(lastBaseLoc);
                if (!tryMove(nav.getNextLocation())) {
                    randomlyJitter();
                }
            }
            return;
        }

        final MapLocation enemyLoc = Messaging.getLastEnemyLocation(this);
        if (enemyLoc != null) {
            nav.setDestination(enemyLoc);
            if (!tryMove(nav.getNextLocation())) {
                randomlyJitter();
            }
            return;
        }

        // Get centroid of our trees
        float x = 0, y = 0;
        for (final TreeInfo tree : myTrees) {
            x += tree.location.x;
            y += tree.location.y;
        }
        final MapLocation centroid = new MapLocation(x / myTrees.length, y / myTrees.length);

        final MapLocation nearestArchon = getNearestArchon();
        final MapLocation moveLoc = centroid.add(nearestArchon.directionTo(centroid), myType.sensorRadius - 1.0f);
        if (!tryMove(moveLoc)) {
            randomlyJitter();
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
        // nav.setDestination(fleeLoc2);
        // TODO: flee should only look at trees?
        if (!tryMove(fleeLoc2)) {
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
                    if (!mapEdges.isOffMap(buildLoc) && rc.senseNearbyRobots(buildLoc, buildRadius, null).length == 0
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

    public final boolean canMove(final Direction dir) {
        if (myType == RobotType.TANK && rc.senseNearbyTrees(myLoc.add(dir), myType.bodyRadius, myTeam).length > 0) {
            return false;
        }
        return rc.canMove(dir);
    }

    public final boolean canMove(final Direction dir, final float distance) {
        if (myType == RobotType.TANK
                && rc.senseNearbyTrees(myLoc.add(dir, distance), myType.bodyRadius, myTeam).length > 0) {
            return false;
        }
        return rc.canMove(dir, distance);
    }

    public final boolean canMove(final MapLocation loc) {
        if (myType == RobotType.TANK && rc.senseNearbyTrees(loc, myType.bodyRadius, myTeam).length > 0) {
            return false;
        }
        return rc.canMove(loc);
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
        if (locDistance <= moveDistance && canMove(loc)) {
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
        if (canMove(dir, moveDistance)) {
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
            if (canMove(leftDir, moveDistance)) {
                rc.move(leftDir, moveDistance);
                startLoop();
                return true;
            }
            // Try the offset on the right side
            final Direction rightDir = dir.rotateRightDegrees(offset);
            if (canMove(rightDir, moveDistance)) {
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

    public final Direction getDodgeDirection(final BulletInfo[] bullets) {
        // Pick the nearest bullet that will collide with me
        BulletInfo nearestBullet = null;
        float nearestDist = 0;
        for (final BulletInfo bullet : bullets) {
            if (!willCollideWithMe(bullet)) {
                continue;
            }
            final float dist = myLoc.distanceTo(bullet.location);
            if (nearestBullet == null || dist < nearestDist) {
                nearestBullet = bullet;
                nearestDist = dist;
                break; // bullets are in distance order
            }
        }
        if (nearestBullet != null) {
            final Direction bulletToMeDir = nearestBullet.location.directionTo(myLoc);
            final Direction rotateDir;
            if (bulletToMeDir.radiansBetween(nearestBullet.dir) > 0) {
                rotateDir = bulletToMeDir.rotateRightDegrees(45.0f);
            } else {
                rotateDir = bulletToMeDir.rotateLeftDegrees(45.0f);
            }
            return rotateDir;
        }
        return null;
    }
}

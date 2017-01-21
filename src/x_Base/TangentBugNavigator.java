package x_Base;

import java.util.HashSet;
import java.util.Set;

import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.TreeInfo;

public strictfp class TangentBugNavigator {

    private static final boolean DEBUG = false;
    private static final int MAX_WALLS = 100;
    private static final float EPS = 0.02f;

    boolean preferRight;

    static enum State {
        TOWARD_GOAL, FOLLOW_OBSTACLE,
    }

    final BotBase bot;
    final RobotController rc;
    MapLocation destination;
    State state;
    MapLocation lastObstacleLoc; // location of last obstacle met
    MapLocation lastMetObstacleLoc; // location where last met obstacle, not obstacle itself
    float lastMetObstacleDist; // distance from lastMetObstacleLoc to destLoc
    FollowWallPoint followWallPoint = null; // point where we are following wall
    final FollowWallPoint[] pointsList = new FollowWallPoint[MAX_WALLS + 1];
    final ObstacleInfo[] sensedObstacles = new ObstacleInfo[MAX_WALLS + 1];
    int pointsSize = 0;
    RobotInfo[] nearbyRobots;
    TreeInfo[] nearbyTrees;

    public TangentBugNavigator(final BotBase bot) {
        this.bot = bot;
        this.rc = bot.rc;
        preferRight = true;
        reset();
    }

    public void setDestination(MapLocation destination) {
        if (this.destination != null && !this.destination.equals(destination)) {
            this.destination = destination;
            reset();
        }
    }

    private void reset() {
        lastMetObstacleLoc = null;
        lastMetObstacleDist = 0;
        followWallPoint = null;
        state = State.TOWARD_GOAL;
    }

    private final ObstacleInfo hasObstacleToward(final MapLocation currLoc, final MapLocation destLoc,
            final float maxDist) {
        final float destDistance = Math.min(currLoc.distanceTo(destLoc), maxDist);
        final ObstacleInfo obstacle = Combat.whichRobotOrTreeWillObjectCollideWith(bot, currLoc.directionTo(destLoc),
                destDistance, bot.myType.bodyRadius, nearbyRobots, nearbyTrees);
        return obstacle;
    }

    public MapLocation getNextLocation() {
        final MapLocation currLoc = rc.getLocation();
        final MapLocation destLoc = destination;
        if (currLoc.equals(destLoc)) { // TODO
            return null;
        }
        nearbyRobots = rc.senseNearbyRobots();
        nearbyTrees = rc.senseNearbyTrees();

        // Bug 2 algorithm: https://www.cs.cmu.edu/~motionplanning/lecture/Chap2-Bug-Alg_howie.pdf
        // but missing some stuff about m-line checks, but seems to work
        for (;;) {
            // Maximum sensing range. This should reduce if we are close to destination or if we have ever been close
            float maxDist = Math.min(bot.myType.sensorRadius, currLoc.distanceTo(destination));
            // maxDist = Math.max(2, maxDist);

            switch (state) {
            case TOWARD_GOAL: {
                if (followWallPoint != null) {
                    throw new RuntimeException("followWallPoint should be null: " + followWallPoint);
                }
                final ObstacleInfo obstacleInfo = hasObstacleToward(currLoc, destLoc, maxDist);
                if (obstacleInfo != null) {
                    state = State.FOLLOW_OBSTACLE;
                    followWallPoint = new FollowWallPoint(destination, new ObstacleInfo(currLoc, bot.myType.bodyRadius),
                            obstacleInfo, preferRight, true);
                    lastObstacleLoc = obstacleInfo.location;
                    lastMetObstacleLoc = obstacleInfo.location.add(obstacleInfo.location.directionTo(currLoc),
                            obstacleInfo.radius + bot.myType.bodyRadius);
                    lastMetObstacleDist = lastMetObstacleLoc.distanceTo(destLoc);
                    if (DEBUG) {
                        Debug.debug_print(bot,
                                "met obstacle " + lastObstacleLoc + " followWallPoint=" + followWallPoint);
                    }
                    continue;
                }
                if (DEBUG) {
                    Debug.debug_print(bot, "returning1 " + destLoc);
                }
                return destLoc;
            }
            case FOLLOW_OBSTACLE: {
                if (followWallPoint == null) {
                    throw new RuntimeException("followWallPoint should not be null");
                }
                // TODO: should we check that we reached the m-line first?
                // TODO: also check that way to goal is unimpeded?
                if (currLoc.distanceTo(destLoc) < lastMetObstacleDist) {
                    if (DEBUG) {
                        Debug.debug_print(bot,
                                "leaving obstacle at " + currLoc + " followWallPoint=" + followWallPoint);
                    }
                    state = State.TOWARD_GOAL;
                    followWallPoint = null;
                    continue;
                }
                computeSubsequentWallPoints(followWallPoint, maxDist);
                if (DEBUG) {
                    for (int i = 0; i < pointsSize; i++) {
                        Debug.debug_print(bot, "  wp=" + pointsList[i]);
                    }
                }

                // Search through wall points in reverse order to find first one that has a greedy accurate path
                // to get to the edge location of the wall
                // TODO: do we need to go step by step in case we overshoot the goal?
                for (int i = pointsSize - 1; i >= 0; i--) {
                    final FollowWallPoint candidate = pointsList[i];
                    final MapLocation edgeLoc = candidate.getEdgeLoc(currLoc, this);
                    boolean isClear = canPathTowardsLocation(edgeLoc);
                    if (DEBUG) {
                        Debug.debug_print(bot, " candidate wp " + candidate + " isClear=" + isClear + " edgeLoc="
                                + edgeLoc);
                    }
                    if (isClear) {
                        if (DEBUG) {
                            Debug.debug_print(bot, " changing wp from " + followWallPoint + " to " + candidate);
                        }
                        followWallPoint = candidate;
                        break;
                    }
                }
                final MapLocation edgeLoc = followWallPoint.getEdgeLoc(currLoc, this);
                if (DEBUG) {
                    Debug.debug_print(bot,
                            "returning2 " + edgeLoc + " followWallPoint=" + followWallPoint);
                }
                return edgeLoc;
            }
            }
            break;
        }
        return null;

    }

    /**
     * Determine if we can greedily navigate towards destLoc, inclusive of whether destLoc is traversable. We need to be
     * within sensing range of destLoc for this to work.
     */
    private final boolean canPathTowardsLocation(final MapLocation destLoc) {
        final MapLocation currLoc = bot.myLoc;
        final float destDistance = currLoc.distanceTo(destLoc);
        if (destDistance > bot.myType.sensorRadius) {
            return false;
        }
        if (Combat.willObjectCollideWithRobot(bot, currLoc.directionTo(destLoc), destDistance, bot.myType.bodyRadius,
                nearbyRobots)) {
            return false;
        }
        if (Combat.willObjectCollideWithTree(bot, currLoc.directionTo(destLoc), destDistance, bot.myType.bodyRadius,
                nearbyTrees)) {
            return false;
        }
        return true;
    }

    private final int getObstaclesAroundObstacle(final ObstacleInfo obstacle, final ObstacleInfo[] obstacles) {
        int size = 0;
        final float senseRadius = obstacle.radius + bot.myType.bodyRadius * 2 + EPS;
        final RobotInfo[] robots = rc.senseNearbyRobots(obstacle.location, senseRadius, null);
        final TreeInfo[] trees = rc.senseNearbyTrees(obstacle.location, senseRadius, null);
        for (final RobotInfo robot : robots) {
            obstacles[size++] = new ObstacleInfo(robot.location, robot.getRadius());
        }
        for (final TreeInfo tree : trees) {
            obstacles[size++] = new ObstacleInfo(tree.location, tree.radius);
        }
        return size;
    }

    /**
     * Return list of all wall points in connected order that are within maxDist away.
     */
    private final void computeSubsequentWallPoints(final FollowWallPoint startPoint, final float maxDist) {
        pointsSize = 0;

        if (DEBUG) {
            Debug.debug_print(bot, " getting wp start=" + startPoint + " maxDist=" + maxDist);
        }
        final MapLocation currLoc = bot.myLoc;

        final Set<ObstacleInfo> seen = new HashSet<ObstacleInfo>();
        seen.add(startPoint.obstacle);
        ObstacleInfo currObstacle = startPoint.obstacle;
        boolean found = true;
        boolean madeLoop = false;
        outer: while (found) {
            found = false;
            final float senseRadius = currObstacle.radius + bot.myType.bodyRadius * 2 + EPS;
            if (!rc.canSenseAllOfCircle(currObstacle.location, senseRadius)) {
                break;
            }
            final int size = getObstaclesAroundObstacle(currObstacle, sensedObstacles);
            // pick the obstacle that has smallest angle
            final Direction obstacleDir = currObstacle.location.directionTo(currLoc);
            ObstacleInfo nextObstacle = null;
            float smallestAngle = 0;
            for (int i = 0; i < size; i++) {
                final ObstacleInfo obstacle = sensedObstacles[i];
                final float radBetween = obstacleDir
                        .radiansBetween(currObstacle.location.directionTo(obstacle.location));
                final float radBetween2;
                if (radBetween < 0) {
                    radBetween2 = radBetween + (float) Math.PI * 2;
                } else {
                    radBetween2 = radBetween;
                }
                if (nextObstacle == null || radBetween2 < smallestAngle) {
                    nextObstacle = obstacle;
                    smallestAngle = radBetween2;
                }
            }
            if (nextObstacle != null) {
                if (seen.contains(nextObstacle)) {
                    // we've made a loop around an existing obstacle
                    if (DEBUG) {
                        Debug.debug_print(bot, "  made loop around");
                    }
                    madeLoop = true;
                    break outer;
                }
                seen.add(nextObstacle);
                final FollowWallPoint nextPoint = new FollowWallPoint(destination, currObstacle, nextObstacle,
                        preferRight, false);
                pointsList[pointsSize++] = nextPoint;
                found = true;
                currObstacle = nextObstacle;
                if (DEBUG) {
                    Debug.debug_print(bot, " added " + nextPoint);
                }
                continue outer;
            }
        }

        if (madeLoop) {
            // Only take the list up to and including the furthest distance location.

            float furthestDist = currLoc.distanceTo(startPoint.obstacle.location);
            int furthestIndex = -1;
            for (int i = 0; i < pointsSize; i++) {
                final FollowWallPoint wp = pointsList[i];
                final float dist = currLoc.distanceTo(wp.obstacle.location);
                if (dist > furthestDist) {
                    furthestDist = dist;
                    furthestIndex = i;
                }
            }
            if (DEBUG) {
                Debug.debug_print(bot,
                        "  currLoc=" + currLoc + " furthestDist=" + furthestDist + " furthestIndex=" + furthestIndex);
                Debug.debug_print(bot, "  trimming return list size from " + pointsSize + " to " + (furthestIndex + 1));
            }
            pointsSize = furthestIndex + 1;
        }
    }

    /**
     * Class representing point where we are following the wall.
     * 
     * Defined by a previousLoc and an obstacleLoc, so that we have a sense of direction that we are traversing along
     * the wall edge. If we are meeting the wall for the first time, previousLoc would be initialized with currentLoc.
     */
    static class FollowWallPoint {
        final MapLocation intendedDestination;
        final ObstacleInfo previousObstacle;
        final ObstacleInfo obstacle;
        final boolean preferRight, firstTime;

        FollowWallPoint(final MapLocation intendedDestination, final ObstacleInfo previousObstacle,
                final ObstacleInfo obstacle, final boolean preferRight, final boolean firstTime) {
            this.intendedDestination = intendedDestination;
            this.previousObstacle = previousObstacle;
            this.obstacle = obstacle;
            this.preferRight = preferRight;
            this.firstTime = firstTime;
        }

        /**
         * Return location at edge of wall. This might change depending on current location, if we are at a sharp edge
         * (number of orthogonal adjacent obstacles is 1). This might also not return a traversable location.
         */
        final MapLocation getEdgeLoc(final MapLocation currLoc, TangentBugNavigator nav) {
            // TODO: improve this
            // For simplicity, just take 90 degrees off to side off this obstacle
            final Direction obsDir = currLoc.directionTo(obstacle.location);
            final MapLocation edgeLoc;
            if (preferRight) {
                edgeLoc = obstacle.location.add(obsDir.rotateRightDegrees(90f),
                        obstacle.radius + nav.bot.myType.bodyRadius);
            } else {
                edgeLoc = obstacle.location.add(obsDir.rotateLeftDegrees(90f),
                        obstacle.radius + nav.bot.myType.bodyRadius);
            }
            return edgeLoc;
        }

        @Override
        public String toString() {
            return "FollowWallPoint(" + previousObstacle + "->" + obstacle + ")";
        }
    }

    static class ObstacleInfo {
        final MapLocation location;
        final float radius;

        ObstacleInfo(final MapLocation location, final float radius) {
            this.location = location;
            this.radius = radius;
        }

        @Override
        public int hashCode() {
            return location.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ObstacleInfo)) {
                return false;
            }
            final ObstacleInfo p = (ObstacleInfo) o;
            return p.location.equals(location) && p.radius == radius;
        }

        @Override
        public String toString() {
            return "ObstacleInfo(" + location + ", " + radius + ")";
        }

    }
}

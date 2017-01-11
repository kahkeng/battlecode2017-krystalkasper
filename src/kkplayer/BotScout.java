package kkplayer;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public strictfp class BotScout extends BotBase {

    static MapLocation homeArchon = null;
    static MapLocation myLoc = null;

    public BotScout(final RobotController rc) {
        super(rc);
    }

    public final void run() throws GameActionException {
        while (true) {
            try {
                myLoc = rc.getLocation();

                if (!seekAndAttackEnemy()) {
                    findHomeArchon();
                    if (homeArchon != null) {
                        final Direction d = homeArchon.directionTo(myLoc);
                        tryMove(d);
                    }
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Scout Exception");
                e.printStackTrace();
            }
        }
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

    public final boolean seekAndAttackEnemy() throws GameActionException {
        // See if enemy within sensor range
        final RobotInfo[] robots = rc.senseNearbyRobots(-1, enemyTeam);
        RobotInfo nearestRobot = null;
        float minDistance = 0;
        for (final RobotInfo robot : robots) {
            final float distance = robot.location.distanceTo(myLoc) - robot.getRadius() - myType.bodyRadius;
            if (nearestRobot == null || distance < minDistance) {
                nearestRobot = robot;
                minDistance = distance;
            }
        }
        if (nearestRobot != null) {
            final Direction enemyDir = myLoc.directionTo(nearestRobot.location);
            final float moveDist = Math.min(minDistance - 0.01f, myType.strideRadius);
            if (moveDist > 0 && rc.canMove(enemyDir, moveDist)) {
                rc.move(enemyDir, moveDist);
            }
            if (rc.canFireSingleShot() && minDistance <= 1.05 * myType.bodyRadius) {
                rc.fireSingleShot(enemyDir);
            }
            return true;
        }
        // Else head towards known broadcasted enemies
        return false;
    }

}

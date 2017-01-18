package x_Harasser;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import x_Base.Util;

public strictfp class BotGardener extends x_Duck16.BotGardener {

    public BotGardener(final RobotController rc) {
        super(rc);
    }

    public void run() throws GameActionException {
        try {
            earlyGame();
        } catch (Exception e) {
            System.out.println("Gardener Exception");
            e.printStackTrace();
        }
        int lowDeltaCount = 0;
        while (true) {
            try {
                startLoop();
                // Messaging.broadcastGardener(this);
                // final MapLocation[] myArchons = Messaging.readArchonLocation(this);
                if (bulletsDelta >= 0.0f && bulletsDelta < 10.0f) {
                    lowDeltaCount += 1;
                }
                if (lowDeltaCount > 3) {
                    becomeHarasser();
                }

                waterTrees();
                final MapLocation enemyLoc = buildCombatUnitsIfNeeded();
                if (enemyLoc != null) {
                    fleeFromEnemyAlongArc(enemyLoc);
                } else {
                    plantTreesInArc(0);
                    buildLumberjacksIfNeeded();
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    public final void becomeHarasser() throws GameActionException {
        final MapLocation randomLoc = myLoc.add(Util.randomDirection(), 10.0f);
        final int round = rc.getRoundNum();
        while (true) {
            startLoop();
            if (myLoc.distanceTo(randomLoc) <= 2.0f || rc.getRoundNum() - round >= 15) {
                harassmentLoop(false);
            } else {
                tryMove(randomLoc);
            }
            Clock.yield();
        }
    }

}

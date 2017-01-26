package x_Combat;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.TreeInfo;
import x_Base.Combat;
import x_Base.Messaging;

public strictfp class BotScout extends x_Seeding.BotScout {

    public static int archonID = 0;
    public static MapLocation randomTarget = null;
    public static int lastSawEnemy = 0;

    public BotScout(final RobotController rc) {
        super(rc);
        DEBUG = true;
    }

    public void run() throws GameActionException {
        archonID = (rc.getRoundNum() / 50) % numInitialArchons;
        while (true) {
            try {
                startLoop();
                Messaging.broadcastScout(this);

                if (Combat.avoidEnemy(this)) {
                    lastSawEnemy = rc.getRoundNum();
                } else {
                    final TreeInfo tree = scoutFindTreesToShake();
                    if (tree != null) {
                        if (!tryMove(tree.location)) {
                            randomlyJitter();
                        }
                    } else if (randomTarget != null) {
                        if (myLoc.distanceTo(randomTarget) <= 6.0f) {
                            randomTarget = null;
                        } else {
                            tryMove(randomTarget);
                        }
                    } else {
                        patrolEnemyArchonLocs(0);
                    }
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Scout Exception");
                e.printStackTrace();
            }
        }
    }

}

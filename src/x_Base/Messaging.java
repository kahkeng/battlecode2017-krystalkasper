package x_Base;

import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.TreeInfo;

public strictfp class Messaging {

    // max number of items, used for declaring arrays in BotBase
    public static final int MAX_PRIORITY_ENEMY_ROBOTS = 10;
    public static final int MAX_ENEMY_ROBOTS = 10;
    public static final int MAX_ENEMY_GARDENERS = 10;
    public static final int MAX_NEUTRAL_TREES = 10;
    public static final int MAX_MY_TREES = 20;

    /** Number of fields for archon data. */
    public static final int FIELDS_ARCHON = 3;
    public static final int FIELDS_GARDENER = 1;
    public static final int FIELDS_HARASSER = 1;
    public static final int FIELDS_SCOUT = 1;
    public static final int FIELDS_PRIORITY_ENEMY_ROBOTS = 3;
    public static final int FIELDS_ENEMY_ROBOTS = 3;
    public static final int FIELDS_ENEMY_GARDENERS = 3;
    public static final int FIELDS_NEUTRAL_TREES = 3;
    public static final int FIELDS_MY_TREES = 4;

    // first 4 fields are for map edges
    public static final int OFFSET_MIN_X = 0;
    public static final int OFFSET_MAX_X = 1;
    public static final int OFFSET_MIN_Y = 2;
    public static final int OFFSET_MAX_Y = 3;

    /** Channel offset for archon data. */
    public static final int OFFSET_ARCHON_START = 4;
    public static final int OFFSET_ARCHON_END = OFFSET_ARCHON_START
            + GameConstants.NUMBER_OF_ARCHONS_MAX * FIELDS_ARCHON;
    public static final int OFFSET_GARDENER_START = OFFSET_ARCHON_END;
    public static final int OFFSET_GARDENER_END = OFFSET_GARDENER_START + BotArchon.MAX_GARDENERS * FIELDS_GARDENER;
    public static final int OFFSET_HARASSER_START = OFFSET_GARDENER_END;
    public static final int OFFSET_HARASSER_END = OFFSET_HARASSER_START
            + x_Streets.BotGardener.MAX_HARASSERS * FIELDS_HARASSER;
    public static final int OFFSET_SCOUT_START = OFFSET_HARASSER_END;
    public static final int OFFSET_SCOUT_END = OFFSET_SCOUT_START + BotArchon.MAX_SCOUTS * FIELDS_SCOUT;
    public static final int OFFSET_PRIORITY_ENEMY_ROBOTS_START = OFFSET_SCOUT_END;
    public static final int OFFSET_PRIORITY_ENEMY_ROBOTS_END = OFFSET_PRIORITY_ENEMY_ROBOTS_START
            + MAX_ENEMY_ROBOTS * FIELDS_ENEMY_ROBOTS;
    public static final int OFFSET_ENEMY_ROBOTS_START = OFFSET_PRIORITY_ENEMY_ROBOTS_END;
    public static final int OFFSET_ENEMY_ROBOTS_END = OFFSET_ENEMY_ROBOTS_START
            + MAX_ENEMY_ROBOTS * FIELDS_ENEMY_ROBOTS;
    public static final int OFFSET_ENEMY_GARDENERS_START = OFFSET_ENEMY_ROBOTS_END;
    public static final int OFFSET_ENEMY_GARDENERS_END = OFFSET_ENEMY_GARDENERS_START
            + MAX_ENEMY_GARDENERS * FIELDS_ENEMY_GARDENERS;
    public static final int OFFSET_NEUTRAL_TREES_START = OFFSET_ENEMY_GARDENERS_END;
    public static final int OFFSET_NEUTRAL_TREES_END = OFFSET_NEUTRAL_TREES_START
            + MAX_NEUTRAL_TREES * FIELDS_NEUTRAL_TREES;
    public static final int OFFSET_MY_TREES_START = OFFSET_NEUTRAL_TREES_END;
    public static final int OFFSET_MY_TREES_END = OFFSET_MY_TREES_START
            + MAX_MY_TREES * FIELDS_MY_TREES;

    public static final int BITSHIFT = 12; // larger than max rounds of 3000
    public static final int BITMASK = (1 << BITSHIFT) - 1;

    public static final int getHeartbeat(final BotBase bot) {
        return (bot.myID << BITSHIFT) + bot.rc.getRoundNum();
    }

    public static final int getRobotHeartbeat(final BotBase bot, final RobotInfo robot) {
        return (robot.ID << BITSHIFT) + bot.rc.getRoundNum();
    }

    public static final int getTreeHeartbeat(final BotBase bot, final TreeInfo tree) {
        return (tree.ID << BITSHIFT) + bot.rc.getRoundNum();
    }

    public static final int getPotentialHeartbeat(final BotBase bot, final int futureRounds) {
        return bot.rc.getRoundNum() + futureRounds;
    }

    public static final int getRoundFromHeartbeat(final int heartbeat) {
        if (heartbeat == 0) {
            return -100;
        } else {
            return heartbeat & BITMASK;
        }
    }

    public static final int getIDFromHeartbeat(final int heartbeat) {
        return heartbeat >> BITSHIFT;
    }

    public static final void broadcastMapMinX(final BotBase bot) throws GameActionException {
        bot.rc.broadcastFloat(OFFSET_MIN_X, bot.mapEdges.minX + 1);
    }

    public static final void broadcastMapMaxX(final BotBase bot) throws GameActionException {
        bot.rc.broadcastFloat(OFFSET_MAX_X, bot.mapEdges.maxX + 1);
    }

    public static final void broadcastMapMinY(final BotBase bot) throws GameActionException {
        bot.rc.broadcastFloat(OFFSET_MIN_Y, bot.mapEdges.minY + 1);
    }

    public static final void broadcastMapMaxY(final BotBase bot) throws GameActionException {
        bot.rc.broadcastFloat(OFFSET_MAX_Y, bot.mapEdges.maxY + 1);
    }

    public static final void processBroadcastedMapEdges(final BotBase bot) throws GameActionException {
        if (!bot.mapEdges.foundMinX) {
            final float value = bot.rc.readBroadcastFloat(OFFSET_MIN_X);
            if (value > 0) {
                bot.mapEdges.minX = (value - 1);
                bot.mapEdges.foundMinX = true;
            }
        }
        if (!bot.mapEdges.foundMaxX) {
            final float value = bot.rc.readBroadcastFloat(OFFSET_MAX_X);
            if (value > 0) {
                bot.mapEdges.maxX = (value - 1);
                bot.mapEdges.foundMaxX = true;
            }
        }
        if (!bot.mapEdges.foundMinY) {
            final float value = bot.rc.readBroadcastFloat(OFFSET_MIN_Y);
            if (value > 0) {
                bot.mapEdges.minY = (value - 1);
                bot.mapEdges.foundMinY = true;
            }
        }
        if (!bot.mapEdges.foundMaxY) {
            final float value = bot.rc.readBroadcastFloat(OFFSET_MAX_Y);
            if (value > 0) {
                bot.mapEdges.maxY = (value - 1);
                bot.mapEdges.foundMaxY = true;
            }
        }
    }

    public static final void broadcastArchonLocation(final BotArchon bot)
            throws GameActionException {
        final MapLocation loc = bot.rc.getLocation();
        final int channel = OFFSET_ARCHON_START + bot.myArchonID * FIELDS_ARCHON;
        bot.rc.broadcast(channel, getHeartbeat(bot));
        bot.rc.broadcastFloat(channel + 1, loc.x);
        bot.rc.broadcastFloat(channel + 2, loc.y);
    }

    public static final MapLocation[] readArchonLocation(final BotBase bot)
            throws GameActionException {
        final MapLocation[] ret = new MapLocation[bot.numInitialArchons];
        final int threshold;
        if (bot.myType == RobotType.ARCHON) {
            threshold = bot.rc.getRoundNum() - 2; // additional -1 in case bytecode limit exceeded
        } else { // robots execute in spawn order, so other robots definitely go after the archons have gone
            threshold = bot.rc.getRoundNum() - 1; // additional -1 in case bytecode limit exceeded
        }
        // we could make these broadcasts less frequent later.
        for (int i = bot.myInitialArchonLocs.length - 1; i >= 0; i--) {
            final int channel = OFFSET_ARCHON_START + i * FIELDS_ARCHON;
            final int heartbeat = bot.rc.readBroadcast(channel);
            if (getRoundFromHeartbeat(heartbeat) < threshold) {
                ret[i] = null; // archon is dead (or went over bytecode limit)
            } else {
                ret[i] = new MapLocation(bot.rc.readBroadcastFloat(channel + 1),
                        bot.rc.readBroadcastFloat(channel + 2));
            }
        }
        return ret;
    }

    public static final int getNumSurvivingArchons(final BotBase bot) throws GameActionException {
        int count = 0;
        final int threshold;
        if (bot.myType == RobotType.ARCHON) {
            threshold = bot.rc.getRoundNum() - 2; // additional -1 in case bytecode limit exceeded
        } else { // robots execute in spawn order, so other robots definitely go after the archons have gone
            threshold = bot.rc.getRoundNum() - 1; // additional -1 in case bytecode limit exceeded
        }
        // we could make these broadcasts less frequent later.
        for (int i = bot.myInitialArchonLocs.length - 1; i >= 0; i--) {
            final int channel = OFFSET_ARCHON_START + i * FIELDS_ARCHON;
            final int heartbeat = bot.rc.readBroadcast(channel);
            if (getRoundFromHeartbeat(heartbeat) < threshold) {
                continue;
            }
            count += 1;
        }
        return count;
    }

    public static final void broadcastGardener(final BotBase bot) throws GameActionException {
        final int threshold = bot.rc.getRoundNum();
        int channel = OFFSET_GARDENER_START;
        while (channel < OFFSET_GARDENER_END && getRoundFromHeartbeat(bot.rc.readBroadcast(channel)) >= threshold) {
            channel += FIELDS_GARDENER;
        }
        if (channel < OFFSET_GARDENER_END) {
            bot.rc.broadcast(channel, getHeartbeat(bot));
        }
    }

    public static final void broadcastPotentialGardener(final BotBase bot) throws GameActionException {
        final int threshold = bot.rc.getRoundNum();
        int channel = OFFSET_GARDENER_START;
        while (channel < OFFSET_GARDENER_END && getRoundFromHeartbeat(bot.rc.readBroadcast(channel)) >= threshold) {
            channel += FIELDS_GARDENER;
        }
        if (channel < OFFSET_GARDENER_END) {
            bot.rc.broadcast(channel, getPotentialHeartbeat(bot, 1));
        }
    }

    public static final int getNumGardeners(final BotBase bot) throws GameActionException {
        final int threshold = bot.rc.getRoundNum() - 1; // additional -1 in case bytecode limit exceeded
        int count = 0;
        int channel = OFFSET_GARDENER_START;
        while (channel < OFFSET_GARDENER_END && getRoundFromHeartbeat(bot.rc.readBroadcast(channel)) >= threshold) {
            count += 1;
            channel += FIELDS_GARDENER;
        }
        return count;
    }

    public static final void broadcastHarasser(final BotBase bot) throws GameActionException {
        final int threshold = bot.rc.getRoundNum();
        int channel = OFFSET_HARASSER_START;
        while (channel < OFFSET_HARASSER_END && getRoundFromHeartbeat(bot.rc.readBroadcast(channel)) >= threshold) {
            channel += FIELDS_HARASSER;
        }
        if (channel < OFFSET_HARASSER_END) {
            bot.rc.broadcast(channel, getHeartbeat(bot));
        }
    }

    public static final int getNumHarassers(final BotBase bot) throws GameActionException {
        final int threshold = bot.rc.getRoundNum() - 1; // additional -1 in case bytecode limit exceeded
        int count = 0;
        int channel = OFFSET_HARASSER_START;
        while (channel < OFFSET_HARASSER_END && getRoundFromHeartbeat(bot.rc.readBroadcast(channel)) >= threshold) {
            count += 1;
            channel += FIELDS_HARASSER;
        }
        return count;
    }

    public static final int getHarasserIDs(final int[] results, final BotBase bot) throws GameActionException {
        final int threshold = bot.rc.getRoundNum() - 1; // additional -1 in case bytecode limit exceeded
        int count = 0;
        int channel = OFFSET_HARASSER_START;
        while (channel < OFFSET_HARASSER_END) {
            final int heartbeat = bot.rc.readBroadcast(channel);
            if (getRoundFromHeartbeat(heartbeat) < threshold) {
                break;
            }
            results[count++] = getIDFromHeartbeat(heartbeat);
            channel += FIELDS_HARASSER;
        }
        return count;
    }

    public static final void broadcastScout(final BotBase bot) throws GameActionException {
        final int threshold = bot.rc.getRoundNum();
        int channel = OFFSET_SCOUT_START;
        while (channel < OFFSET_SCOUT_END && getRoundFromHeartbeat(bot.rc.readBroadcast(channel)) >= threshold) {
            channel += FIELDS_SCOUT;
        }
        if (channel < OFFSET_SCOUT_END) {
            bot.rc.broadcast(channel, getHeartbeat(bot));
        }
    }

    public static final void broadcastPotentialScout(final BotBase bot) throws GameActionException {
        final int threshold = bot.rc.getRoundNum();
        int channel = OFFSET_SCOUT_START;
        while (channel < OFFSET_SCOUT_END && getRoundFromHeartbeat(bot.rc.readBroadcast(channel)) >= threshold) {
            channel += FIELDS_SCOUT;
        }
        if (channel < OFFSET_SCOUT_END) {
            bot.rc.broadcast(channel, getPotentialHeartbeat(bot, 20));
        }
    }

    public static final int getNumScouts(final BotBase bot) throws GameActionException {
        final int threshold = bot.rc.getRoundNum() - 1; // additional -1 in case bytecode limit exceeded
        int count = 0;
        int channel = OFFSET_SCOUT_START;
        while (channel < OFFSET_SCOUT_END && getRoundFromHeartbeat(bot.rc.readBroadcast(channel)) >= threshold) {
            count += 1;
            channel += FIELDS_SCOUT;
        }
        return count;
    }

    public static final void broadcastEnemyRobot(final BotBase bot, final RobotInfo enemyRobot)
            throws GameActionException {
        final int threshold = bot.rc.getRoundNum() - 1; // additional -1 in case bytecode limit exceeded
        int channel = OFFSET_ENEMY_ROBOTS_START;
        while (channel < OFFSET_ENEMY_ROBOTS_END) {
            final int heartbeat = bot.rc.readBroadcast(channel);
            if (getIDFromHeartbeat(heartbeat) == enemyRobot.ID || getRoundFromHeartbeat(heartbeat) < threshold) {
                break;
            }
            channel += FIELDS_ENEMY_ROBOTS;
        }
        if (channel < OFFSET_ENEMY_ROBOTS_END) {
            bot.rc.broadcast(channel, getRobotHeartbeat(bot, enemyRobot));
            bot.rc.broadcastFloat(channel + 1, enemyRobot.location.x);
            bot.rc.broadcastFloat(channel + 2, enemyRobot.location.y);
        }
    }

    public static final int getEnemyRobots(final MapLocation[] results, final BotBase bot) throws GameActionException {
        final int threshold = bot.rc.getRoundNum() - 1; // additional -1 in case bytecode limit exceeded
        int channel = OFFSET_ENEMY_ROBOTS_START;
        int count = 0;
        while (channel < OFFSET_ENEMY_ROBOTS_END) {
            final int heartbeat = bot.rc.readBroadcast(channel);
            if (getRoundFromHeartbeat(heartbeat) < threshold) {
                break;
            }
            results[count++] = new MapLocation(bot.rc.readBroadcastFloat(channel + 1),
                    bot.rc.readBroadcastFloat(channel + 2));
            channel += FIELDS_ENEMY_ROBOTS;
        }
        return count;
    }

    public static final void broadcastPriorityEnemyRobot(final BotBase bot, final RobotInfo enemyRobot)
            throws GameActionException {
        final int threshold = bot.rc.getRoundNum() - 1; // additional -1 in case bytecode limit exceeded
        int channel = OFFSET_PRIORITY_ENEMY_ROBOTS_START;
        while (channel < OFFSET_PRIORITY_ENEMY_ROBOTS_END) {
            final int heartbeat = bot.rc.readBroadcast(channel);
            if (getIDFromHeartbeat(heartbeat) == enemyRobot.ID || getRoundFromHeartbeat(heartbeat) < threshold) {
                break;
            }
            channel += FIELDS_PRIORITY_ENEMY_ROBOTS;
        }
        if (channel < OFFSET_PRIORITY_ENEMY_ROBOTS_END) {
            bot.rc.broadcast(channel, getRobotHeartbeat(bot, enemyRobot));
            bot.rc.broadcastFloat(channel + 1, enemyRobot.location.x);
            bot.rc.broadcastFloat(channel + 2, enemyRobot.location.y);
        }
    }

    public static final int getPriorityEnemyRobots(final MapLocation[] results, final BotBase bot)
            throws GameActionException {
        final int threshold = bot.rc.getRoundNum() - 1; // additional -1 in case bytecode limit exceeded
        int channel = OFFSET_PRIORITY_ENEMY_ROBOTS_START;
        int count = 0;
        while (channel < OFFSET_PRIORITY_ENEMY_ROBOTS_END) {
            final int heartbeat = bot.rc.readBroadcast(channel);
            if (getRoundFromHeartbeat(heartbeat) < threshold) {
                break;
            }
            results[count++] = new MapLocation(bot.rc.readBroadcastFloat(channel + 1),
                    bot.rc.readBroadcastFloat(channel + 2));
            channel += FIELDS_PRIORITY_ENEMY_ROBOTS;
        }
        return count;
    }

    public static final MapLocation getLastEnemyLocation(final BotBase bot) throws GameActionException {
        int channel = OFFSET_ENEMY_ROBOTS_START;
        final int heartbeat = bot.rc.readBroadcast(channel);
        if (heartbeat == 0) {
            return null;
        }
        return new MapLocation(bot.rc.readBroadcastFloat(channel + 1),
                bot.rc.readBroadcastFloat(channel + 2));
    }

    public static final void broadcastEnemyGardener(final BotBase bot, final RobotInfo enemyRobot)
            throws GameActionException {
        final int threshold = bot.rc.getRoundNum() - 1; // additional -1 in case bytecode limit exceeded
        int channel = OFFSET_ENEMY_GARDENERS_START;
        while (channel < OFFSET_ENEMY_GARDENERS_END) {
            final int heartbeat = bot.rc.readBroadcast(channel);
            if (getIDFromHeartbeat(heartbeat) == enemyRobot.ID || getRoundFromHeartbeat(heartbeat) < threshold) {
                break;
            }
            channel += FIELDS_ENEMY_GARDENERS;
        }
        if (channel < OFFSET_ENEMY_GARDENERS_END) {
            bot.rc.broadcast(channel, getRobotHeartbeat(bot, enemyRobot));
            bot.rc.broadcastFloat(channel + 1, enemyRobot.location.x);
            bot.rc.broadcastFloat(channel + 2, enemyRobot.location.y);
        }
    }

    public static final int getEnemyGardeners(final MapLocation[] results, final BotBase bot)
            throws GameActionException {
        final int threshold = bot.rc.getRoundNum() - 1; // additional -1 in case bytecode limit exceeded
        int channel = OFFSET_ENEMY_GARDENERS_START;
        int count = 0;
        while (channel < OFFSET_ENEMY_GARDENERS_END) {
            final int heartbeat = bot.rc.readBroadcast(channel);
            if (getRoundFromHeartbeat(heartbeat) < threshold) {
                break;
            }
            results[count++] = new MapLocation(bot.rc.readBroadcastFloat(channel + 1),
                    bot.rc.readBroadcastFloat(channel + 2));
            channel += FIELDS_ENEMY_GARDENERS;
        }
        return count;
    }

    public static final void broadcastNeutralTree(final BotBase bot, final TreeInfo neutralTree)
            throws GameActionException {
        final int threshold = bot.rc.getRoundNum() - 1; // additional -1 in case bytecode limit exceeded
        int channel = OFFSET_NEUTRAL_TREES_START;
        while (channel < OFFSET_NEUTRAL_TREES_END) {
            final int heartbeat = bot.rc.readBroadcast(channel);
            if (getIDFromHeartbeat(heartbeat) == neutralTree.ID || getRoundFromHeartbeat(heartbeat) < threshold) {
                break;
            }
            channel += FIELDS_NEUTRAL_TREES;
        }
        if (channel < OFFSET_NEUTRAL_TREES_END) {
            bot.rc.broadcast(channel, getTreeHeartbeat(bot, neutralTree));
            bot.rc.broadcastFloat(channel + 1, neutralTree.location.x);
            bot.rc.broadcastFloat(channel + 2, neutralTree.location.y);
        }
    }

    public static final int getNeutralTrees(final MapLocation[] results, final BotBase bot) throws GameActionException {
        final int threshold = bot.rc.getRoundNum() - 1; // additional -1 in case bytecode limit exceeded
        int channel = OFFSET_NEUTRAL_TREES_START;
        int count = 0;
        while (channel < OFFSET_NEUTRAL_TREES_END) {
            final int heartbeat = bot.rc.readBroadcast(channel);
            if (getRoundFromHeartbeat(heartbeat) < threshold) {
                break;
            }
            results[count++] = new MapLocation(bot.rc.readBroadcastFloat(channel + 1),
                    bot.rc.readBroadcastFloat(channel + 2));
            channel += FIELDS_NEUTRAL_TREES;
        }
        return count;
    }

    public static final void broadcastMyTree(final BotBase bot, final TreeInfo myTree)
            throws GameActionException {
        final int threshold = bot.rc.getRoundNum() - 1; // additional -1 in case bytecode limit exceeded
        int channel = OFFSET_MY_TREES_START;
        while (channel < OFFSET_MY_TREES_END) {
            final int heartbeat = bot.rc.readBroadcast(channel);
            if (getIDFromHeartbeat(heartbeat) == myTree.ID || getRoundFromHeartbeat(heartbeat) < threshold) {
                break;
            }
            channel += FIELDS_MY_TREES;
        }
        if (channel < OFFSET_MY_TREES_END) {
            bot.rc.broadcast(channel, getTreeHeartbeat(bot, myTree));
            bot.rc.broadcastFloat(channel + 1, myTree.location.x);
            bot.rc.broadcastFloat(channel + 2, myTree.location.y);
            bot.rc.broadcastFloat(channel + 3, myTree.health);
        }
    }

    public static final int getMyTrees(final MapLocation[] results, final float[] healths, final BotBase bot)
            throws GameActionException {
        final int threshold = bot.rc.getRoundNum() - 1; // additional -1 in case bytecode limit exceeded
        int channel = OFFSET_MY_TREES_START;
        int count = 0;
        while (channel < OFFSET_MY_TREES_END) {
            final int heartbeat = bot.rc.readBroadcast(channel);
            if (getRoundFromHeartbeat(heartbeat) < threshold) {
                break;
            }
            results[count] = new MapLocation(bot.rc.readBroadcastFloat(channel + 1),
                    bot.rc.readBroadcastFloat(channel + 2));
            healths[count] = bot.rc.readBroadcastFloat(channel + 3);
            count++;
            channel += FIELDS_MY_TREES;
        }
        return count;
    }
}

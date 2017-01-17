package x_Base;

import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

public strictfp class Messaging {

    /** Number of fields for archon data. */
    public static final int FIELDS_ARCHON = 3;
    public static final int FIELDS_GARDENER = 1;
    public static final int FIELDS_ENEMY_ROBOTS = 3;

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
    public static final int OFFSET_ENEMY_ROBOTS_START = OFFSET_GARDENER_END;
    public static final int OFFSET_ENEMY_ROBOTS_END = OFFSET_ENEMY_ROBOTS_START
            + BotBase.MAX_ENEMY_ROBOTS * FIELDS_ENEMY_ROBOTS;

    public static final int BITSHIFT = 12; // larger than max rounds of 3000
    public static final int BITMASK = (1 << BITSHIFT) - 1;

    public static final int getHeartbeat(final BotBase bot) {
        return (bot.myID << BITSHIFT) + bot.rc.getRoundNum();
    }

    public static final int getRobotHeartbeat(final BotBase bot, final RobotInfo robot) {
        return (robot.ID << BITSHIFT) + bot.rc.getRoundNum();
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
        bot.rc.broadcast(OFFSET_MIN_X, (int) (bot.mapEdges.minX * 100) + 1);
    }

    public static final void broadcastMapMaxX(final BotBase bot) throws GameActionException {
        bot.rc.broadcast(OFFSET_MAX_X, (int) (bot.mapEdges.maxX * 100) + 1);
    }

    public static final void broadcastMapMinY(final BotBase bot) throws GameActionException {
        bot.rc.broadcast(OFFSET_MIN_Y, (int) (bot.mapEdges.minY * 100) + 1);
    }

    public static final void broadcastMapMaxY(final BotBase bot) throws GameActionException {
        bot.rc.broadcast(OFFSET_MAX_Y, (int) (bot.mapEdges.maxY * 100) + 1);
    }

    public static final void processBroadcastedMapEdges(final BotBase bot) throws GameActionException {
        if (!bot.mapEdges.foundMinX) {
            final int value = bot.rc.readBroadcast(OFFSET_MIN_X);
            if (value > 0) {
                bot.mapEdges.minX = (value - 1) * 0.01f;
                bot.mapEdges.foundMinX = true;
            }
        }
        if (!bot.mapEdges.foundMaxX) {
            final int value = bot.rc.readBroadcast(OFFSET_MAX_X);
            if (value > 0) {
                bot.mapEdges.maxX = (value - 1) * 0.01f;
                bot.mapEdges.foundMaxX = true;
            }
        }
        if (!bot.mapEdges.foundMinY) {
            final int value = bot.rc.readBroadcast(OFFSET_MIN_Y);
            if (value > 0) {
                bot.mapEdges.minY = (value - 1) * 0.01f;
                bot.mapEdges.foundMinY = true;
            }
        }
        if (!bot.mapEdges.foundMaxY) {
            final int value = bot.rc.readBroadcast(OFFSET_MAX_Y);
            if (value > 0) {
                bot.mapEdges.maxY = (value - 1) * 0.01f;
                bot.mapEdges.foundMaxY = true;
            }
        }
    }

    public static final void broadcastArchonLocation(final BotArchon bot)
            throws GameActionException {
        final MapLocation loc = bot.rc.getLocation();
        final int channel = OFFSET_ARCHON_START + bot.myArchonID * FIELDS_ARCHON;
        bot.rc.broadcast(channel, getHeartbeat(bot));
        bot.rc.broadcast(channel + 1, (int) (loc.x * 100));
        bot.rc.broadcast(channel + 2, (int) (loc.y * 100));
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
                ret[i] = new MapLocation(bot.rc.readBroadcast(channel + 1) * 0.01f,
                        bot.rc.readBroadcast(channel + 2) * 0.01f);
            }
        }
        return ret;
    }

    public static final void broadcastGardener(final BotGardener bot) throws GameActionException {
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
            bot.rc.broadcast(channel + 1, (int) (enemyRobot.location.x * 100));
            bot.rc.broadcast(channel + 2, (int) (enemyRobot.location.y * 100));
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
            results[count++] = new MapLocation(bot.rc.readBroadcast(channel + 1) * 0.01f,
                    bot.rc.readBroadcast(channel + 2) * 0.01f);
            channel += FIELDS_ENEMY_ROBOTS;
        }
        return count;
    }
}

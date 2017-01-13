package x_Base;

import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotType;

public strictfp class Messaging {

    /** Number of fields for archon data. */
    public static final int FIELDS_ARCHON = 3;
    public static final int FIELDS_GARDENER = 1;

    /** Channel offset for archon data. */
    public static final int OFFSET_ARCHON = 0;
    public static final int OFFSET_GARDENER = GameConstants.NUMBER_OF_ARCHONS_MAX * FIELDS_ARCHON;

    public static final int BITSHIFT = 12; // larger than max rounds of 3000
    public static final int BITMASK = (1 << BITSHIFT) - 1;

    public static final int getHeartbeat(final BotBase bot) {
        return (bot.myID << BITSHIFT) + bot.rc.getRoundNum();
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

    public static final void broadcastArchonLocation(final BotArchon bot)
            throws GameActionException {
        final MapLocation loc = bot.rc.getLocation();
        final int channel = OFFSET_ARCHON + bot.myArchonID * FIELDS_ARCHON;
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
            final int channel = OFFSET_ARCHON + i * FIELDS_ARCHON;
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
        int channel = OFFSET_GARDENER;
        while (getRoundFromHeartbeat(bot.rc.readBroadcast(channel)) >= threshold) {
            channel += FIELDS_GARDENER;
        }
        bot.rc.broadcast(channel, getHeartbeat(bot));
    }

    public static final void broadcastPotentialGardener(final BotBase bot) throws GameActionException {
        final int threshold = bot.rc.getRoundNum();
        int channel = OFFSET_GARDENER;
        while (getRoundFromHeartbeat(bot.rc.readBroadcast(channel)) >= threshold) {
            channel += FIELDS_GARDENER;
        }
        bot.rc.broadcast(channel, getPotentialHeartbeat(bot, 1));
    }

    public static final int numGardener(final BotBase bot) throws GameActionException {
        final int threshold = bot.rc.getRoundNum() - 1; // additional -1 in case bytecode limit exceeded
        int count = 0;
        int channel = OFFSET_GARDENER;
        while (getRoundFromHeartbeat(bot.rc.readBroadcast(channel)) >= threshold) {
            count += 1;
            channel += FIELDS_GARDENER;
        }
        return count;
    }
}

package x_Base;

public strictfp class Meta {
    final BotBase bot;

    public Meta(final BotBase bot) {
        this.bot = bot;
    }

    public final boolean isShortGame() {
        if (bot.rc.getRoundLimit() <= 1600 || bot.formation.separation < 40f) {
            return true;
        }
        return false;
    }

    public final boolean isLongGame() {
        return !isShortGame();
    }
}

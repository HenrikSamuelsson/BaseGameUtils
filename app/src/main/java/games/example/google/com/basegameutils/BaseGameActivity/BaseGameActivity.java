package games.example.google.com.basegameutils.BaseGameActivity;

import android.support.v4.app.FragmentActivity;

/**
 * Created by Henrik Samuelsson on 2014-08-24.
 */
public abstract class BaseGameActivity extends FragmentActivity implements
        GameHelper.GameHelpleListener {

    // The game helper object. This class is mainly a wrapper around this object.
    protected GameHelper mHelper;

    // We expose these constants here because we do not want users of this class to have to know
    // about GameHelper at all.
    public static final int CLIENT_GAMES = GameHelper.CLIENT_GAMES;
}

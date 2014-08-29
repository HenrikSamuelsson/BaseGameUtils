package games.example.google.com.basegameutils.BaseGameActivity;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

/**
 * Created by Henrik Samuelsson on 2014-08-24.
 */
public class GameHelper implements GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener {

    static final String TAG = "GameHelper";

    /** Listener for events indicating success or failure of sign-in attempts. */
    public interface GameHelperListener {

        /** Called when sign-in fails. */
        void onSignInFailed();

        /** Called when sign-in succeeds. */
        void onSignInSucceeded();
    }

    // Configuration done?
    private boolean mSetupDone = false;

    // Are we currently connecting?
    private boolean mConnecting = false;

    // Are we expecting the result of a resolution flow?
    boolean mExpectingResolution = false;

    // Was the sign-in flow cancelled when we tried it?
    // If true, we know not to try again automatically.
    boolean mSignInCancelled = false;

    /**
     * The Activity we are bound to. We need to keep a reference to the Activity because some games
     * methods requires an Activity (a Context won't do). We are careful not to leak these
     * references: we release them onStop().
     */
    Activity mActivity = null;

    // App context.
    Context mAppContext = null;

    // Request code we use when invoking other Activities to complete the sign-in flow.
    final static int RC_RESOLVE = 9001;

    // Request code when invoking Activities whose result we don't care about.
    final static int RC_UNUSED = 9002;

    // Google API client builder used to create a GoogleApiClient.
    GoogleApiClient.Builder mGoogleApiClientBuilder = null;

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }
}
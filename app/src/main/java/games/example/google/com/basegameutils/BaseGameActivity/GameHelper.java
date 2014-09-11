/*
 * Copyright (C) 2013 Google Inc.
 * Copyright (C) 2014 SamDiDe.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package games.example.google.com.basegameutils.BaseGameActivity;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.Api;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;
import com.google.android.gms.plus.Plus;

import static com.google.android.gms.games.Games.*;
import static com.google.android.gms.plus.Plus.*;

/**
 * Created by Henrik Samuelsson on 2014-08-24.
 */
public class GameHelper implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    static final String TAG = "GameHelper";

    /**
     * Listener for events indicating success or failure of sign-in attempts.
     */
    public interface GameHelperListener {

        /**
         * Called when sign-in fails.
         */
        void onSignInFailed();

        /**
         * Called when sign-in succeeds.
         */
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

    // API options to use when adding each API, null for none.
    GamesOptions mGamesApiOptions = GamesOptions.builder().build();
    PlusOptions mPlusApiOptions = null;
    Api.ApiOptions.NoOptions mAppStateApiOptions = null;

    // Google API client object that we manage.
    GoogleApiClient mGoogleApiClient = null;

    // Client request flags.
    public final static int CLIENT_NONE = 0x00;
    public final static int CLIENT_GAMEMS = 0x01;
    public final static int CLIENT_PLUS = 0x02;
    public final static int CLIENT_APPSTATE = 0x04;
    public final static int CLIENT_SNAPSHOT = 0x08;
    public final static int CLIENT_ALL = CLIENT_GAMEMS | CLIENT_PLUS | CLIENT_APPSTATE |
            CLIENT_SNAPSHOT;

    // What clients we requested, setup as bit flag representation.
    int mRequestedClients = CLIENT_NONE;

    /*
     * Whether to automatically try to sign in on onStart(). We only set this to true when the
     * sign-in process fails or the user explicitly signs out. We set it back to false when the
     * user initiates the sign in process.
     */
    boolean mConnectOnStart = true;

    /*
     * Whether user has specifically requested that the sign-in process begin. If false, then
     * we are in the automatic sign-in attempt that we try once the Actiwity is started -- if true,
     * then the user has already clicked a "Sign-in" button or similar.
     */
    boolean mUserInitiatedSignIn = false;

    // The connection result we got from our last attempt to sign-in.
    ConnectionResult mConnectionResult = null;

    // Eventual error that happened during sign-in.
    SignInFailureReason mSignInFailureReason = null;

    // Show error dialog boxes or not?
    boolean mShowErrorDialogs = true;

    // Print debug logs?
    boolean mDebugLog = false;

    Handler mHandler;

    /*
     * If we got an invitation when we connected to the game client, it is here.
     * Otherwise, it is null.
     */
    Invitation mInvitation;

    /*
     * If we got turn-based match when we connected to the game client, it is here.
     * Otherwise, it is null.
     */
    TurnBasedMatch mTurnBasedMatch;

    /*
     * If we have incoming requests when we connected to the game client, they are here.
     * Otherwise it is null.
     */
    ArrayList<GameRequest> mRequest;

    // Listener
    GameHelperListener mListener = null;

    // Should we start the flow to sign the user in automatically on startup?
    // If so, up to how many times in the life of the application?
    static final int DEFAULT_MAX_SIGN_IN_ATTEMPTS = 3;
    int mMaxAutoSignInAttempts = DEFAULT_MAX_SIGN_IN_ATTEMPTS;

    /**
     * Constructs a GameHelper object, initially tied to the given Activity.
     * After constructing this object, call @link{setup} from the onCreate()
     * method of your Activity.
     *
     * @param activity
     *          the activity that the object constructed shall be tied to
     * @param clientsToUse
     *          the API clients to use, depending the on CLIENTS_* flags settings
     */
    public GameHelper(Activity activity, int clientsToUse) {
        mActivity = activity;
        mAppContext = activity.getApplicationContext();
        mRequestedClients = clientsToUse;
        mHandler = new Handler();
    }

    /**
     * Sets the maximum number of automatic sign-in attempts to be made on
     * application startup. This maximum is over the lifetime of the application
     * (it is stored in a SharedPreferences file). So, for example, if you
     * specify 2, then it means that the user will be prompted to sign in on app
     * startup the first time and, if they cancel, a second time the next time
     * the app starts, and, if they cancel that one, never again. Set to 0 if
     * you do not want the user to be prompted to sign in on application
     * startup.
     *
     * @param max
     *      number of occasions a user will be requested to sign in
     */
    public void setMaxAutoSignAttempts(int max) {
        mMaxAutoSignInAttempts = max;
    }

    void assertConfigured(String operation) {
        if( !mSetupDone ) {
            String error = "GameHelper error: Operation attempted without setup:"
                    + operation
                    + ". The setup() method must be called before attempting any other operation";
            logError(error);
            throw new IllegalStateException(error);
        }
    }

    private void doApiOptionPreCheck() {
        if (mGoogleApiClientBuilder != null) {
            String error = "GameHelper: you cannot call set*ApiOptions after the client " +
                    "builder has been created. Call it before calling createApiClientBuilder() " +
                    "or setup().";
            logError(error);
            throw new IllegalStateException(error);
        }
    }

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

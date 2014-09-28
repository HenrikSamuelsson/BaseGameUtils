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
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.google.android.gms.appstate.AppStateManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.Api;
import com.google.android.gms.common.api.Api.ApiOptions.NoOptions;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.Multiplayer;
import com.google.android.gms.games.multiplayer.turnbased.TurnBasedMatch;
import com.google.android.gms.games.request.GameRequest;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.Plus.PlusOptions;

import java.util.ArrayList;

import static com.google.android.gms.games.Games.*;


/**
 * Created by Henrik Samuelsson on 2014-08-24.
 */
public class GameHelper implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    static final String TAG = "GameHelper";

    private final String GAMEHELPER_SHARED_PREFS = "GAMEHELPER_SHARED_PREFS";
    private final String KEY_SIGN_IN_CANCELLATIONS = "KEY_SIGN_IN_CANCELLATIONS";

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
    public final static int CLIENT_GAMES = 0x01;
    public final static int CLIENT_PLUS = 0x02;
    public final static int CLIENT_APPSTATE = 0x04;
    public final static int CLIENT_SNAPSHOT = 0x08;
    public final static int CLIENT_ALL = CLIENT_GAMES | CLIENT_PLUS | CLIENT_APPSTATE |
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
    ArrayList<GameRequest> mRequests;

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

    /**
     * Sets the options to pass when setting up the Games API. Call before
     * setup().
     */
    public void setGamesApiOptions(GamesOptions options) {
        doApiOptionsPreCheck();
        mGamesApiOptions = options;
    }

    /**
     * Sets the options to pass when setting up the Plus API. Call before setup().
     * @param options Plus API options to be used
     */
    public void setPlusApiOptions(PlusOptions options) {
        doApiOptionPreCheck();
        mPlusApiOptions = options;
    }

    /**
     * Sets the options to pass when setting up the AppState API. Call before setup().
     * @param options AppState api options
     */
    public void setAppStateApiOptions(NoOptions options) {
        doApiOptionPreCheck();
        mAppStateApiOptions = options;
    }

    /**
     * Creates a GoogleApiClient.Builder for use with @link{#setup}. Normally, you do not have to
     * do this; use this method only if you need to make nonstandard setup (e.g adding extra scopes
     * for other APIs) on the GooglApiClient.Builder before calling @link{#setup}.
     */
    public GoogleApiClient.Builder createApiClentBuilder() {
        if(mSetupDone) {
            String error = "GameHelper: You called GameHelper.createApiClientBuilder() after "
                    + "calling setup. You can only get a client builder BEFORE performing setup";
            logError(error);
            throw new IllegalStateException(error);
        }

        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(mActivity, this, this);

        if (0 != (mRequestedClients & CLIENT_GAMES)) {
            builder.addApi(Games.API, mGamesApiOptions);
            builder.addScope(Games.SCOPE_GAMES);
        }

        if (0 != (mRequestedClients & CLIENT_PLUS)) {
            builder.addApi(Plus.API);
            builder.addScope(Plus.SCOPE_PLUS_LOGIN);
        }

        if (0 != (mRequestedClients & CLIENT_APPSTATE)) {
            builder.addApi(AppStateManager.API);
            builder.addScope(AppStateManager.SCOPE_APP_STATE);
        }

        if (0 != (mRequestedClients & CLIENT_SNAPSHOT)) {
            builder.addApi(Drive.API);
            builder.addScope(Drive.SCOPE_APPFOLDER);
        }

        mGoogleApiClientBuilder = builder;
        return builder;
    }

    /**
     * Performs setup on this GameHelper object. Call this from onCreate() method of your Activity.
     * This will create the clients and do a few other initialization tasks. Next, call
     * &link{#onStart} from the onStart() method of your Activity.
     *
     * @param listener
     *          The listener to be notified of sign-in events.
     */
    public void setup(GameHelperListener listener) {
        if (mSetupDone) {
            String error = "GameHelper: You cannot call GameHelper.setup() more than once!";
            logError(error);
            throw new IllegalStateException(error);
        }
        mListener = listener;
        debugLog("Setup - Requested clients: mRequestedClients ");

        if(mGoogleApiClientBuilder == null) {
            createApiClentBuilder();
        }

        mGoogleApiClient = mGoogleApiClientBuilder.build();
        mGoogleApiClientBuilder = null;
        mSetupDone = true;
    }

    /**
     * Getter for the GoogleApiClient object. @link{setup} must have been called before this method
     * can be used.
     */
    public GoogleApiClient getApiClient() {
        if (mGoogleApiClient == null) {
            throw new IllegalStateException("No GoogleApiClient. Did you call setup()?");
        }
        return mGoogleApiClient;
    }

    /** Returns whether or not the user is signed in. */
    public boolean isSignedIn() {
        return mGoogleApiClient != null && mGoogleApiClient.isConnected();
    }

    /** Returns whether or not we are currently connecting. */
    public boolean isConnecting() {
        return mConnecting;
    }

    /**
     * Returns whether or not there was a (non-recoverable) error during the
     * sign-in process.
     */
    public boolean hasSignInError() {
        return mSignInFailureReason != null;
    }

    /**
     * Returns the error that happened during the sign-in process, null if
     * no error occurred.
     */
    public SignInFailureReason getSignInError() {
        return mSignInFailureReason;
    }

    /** Set whether to show error dialogs or not. */
    public void setShowErrorDialogs(boolean show) {
        mShowErrorDialogs = show;
    }

    /** Call this method from your Activity's onStart(). */
    public void onStart(Activity act) {
        mActivity = act;
        mAppContext = act.getApplicationContext();

        debugLog("onStart");
        assertConfigured("onStart");

        if (mConnectOnStart) {
            if (mGoogleApiClient.isConnected()) {
                Log.w(TAG, "GameHelper: client was already connected on onStart()");
            } else {
                debugLog("Connecting client.");
                mConnecting = true;
                mGoogleApiClient.connect();
            }
        } else {
            debugLog("Not attempting to connect because mConnectOnStart = false.");
            debugLog("Instead, reporting a sign-in failure");
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    notifyListener(false);
                }
            }, 1000);
        }
    }

    /** Call this method from your Activity's onStop(). */
    public void onStop() {
        debugLog("onStop");
        assertConfigured("onStop");
        if (mGoogleApiClient.isConnected()) {
            debugLog("Disconnecting client due to onStop");
            mGoogleApiClient.disconnect();
        } else {
            mDebugLog("Client already disconnected when we got onStop");
        }
        mConnecting = false;
        mExpectingResolution = false;

        // Let go of the Activity reference
        mActivity = null;
    }

    /**
     * Returns the invitation ID received through an invitation notification. This
     * should be called from your GomeHelperListener´s
     * {@link GameHelperListener#onSignInSucceeded} method, to check if there´s an
     * invitation available. In that case, accept the invitation.
     *
     * @return The id of the invitation, or null if none was received.
     */
    public String getInvitationId() {
        if (!mGoogleApiClient.isConnected()) {
            Log.w(TAG, "Warning: getInvitationId() should only be called when signed in, "
            + "that is, after getting onSignInSuceeded()" );
        }
        return mInvitation == null ? null : mInvitation.getInvitationId();
    }

    public boolean hasInvitation() {
        return mInvitation != null;
    }

    public boolean hasTurnBasedMatch() {
        return mTurnBasedMatch != null;
    }

    public boolean hasRequest() {
        return mRequests != null;
    }

    public void clearInvitation() {
        mInvitation = null;
    }

    public void clearTurnBasedMatch() {
        mTurnBasedMatch = null;
    }

    public void clearRequests() {
        mRequests = null;
    }

    /**
     * Returns the TurnedBasedMatch received through an invitation notification. This should be
     * called from your GameHelperListener´s {@link GameHelperListener#onSignInSucceeded} method,
     * to check if there´s a match available.
     *
     * @return The match, or null if none was received.
     */
    public TurnBasedMatch getTurnBasedMatch() {
        if (!mGoogleApiClient.isConnected()) {
            Log.w(TAG, "Warning: getTurnBasedMatch() should only be called when signed in, "
            + "that is. after getting onSignInSuceeded().");
        }
        return mTurnBasedMatch;
    }

    /**
     * Returns the requests received through the onConnected bundle. This should be called from
     * your GameHelperListener's {@link GameHelperListener#onSignInSucceeded()} method, to check
     * if there are incoming requests that must be handled.
     *
     * @return The requests, or null if none were received.
     */
    public ArrayList<GameRequest> getRequests() {
        if (!mGoogleApiClient.isConnected()) {
            Log.w(TAG, "Warning: getRequests() should only be called when signed in, "
            + "that is after getting onSignInSuceeded().");
        }
        return mRequests;
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    /**
     * Called when successfully obtain a connection to a client.
     */
    @Override
    public void onConnected(Bundle bundle) {
        debugLog("onConnected: connected!");

        if(bundle != null) {
            debugLog("onConnected: connection information bundle provided, checking for invite");
            Invitation inv = bundle.getParcelable(Multiplayer.EXTRA_INVITATION);
            if(inv != null && inv.getInvitationId() != null) {
                debugLog("onConnected: found a room invite.");
                debugLog("onConnected: invitation ID = " + mInvitation.getInvitationId());
                mInvitation = inv;
            }

            // Check if there are any requests pending?
            mRequests = Games.Requests.getGameRequestsFromBundle(bundle);
            if (!mRequests.isEmpty()) {
                // We have requests in onConnected's information bundle.
                debugLog("onConnected: found " + mRequests.size() + " request(s)");
            }

            debugLog("onConnected: checking for turn based match game information");
            mTurnBasedMatch = bundle.getParcelable(Multiplayer.EXTRA_TURN_BASED_MATCH);
        }

        succeedSignIn();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    /** Enables debug logging. */
    public void enableDebugLog(boolean enabled) {
        mDebugLog = enabled;
        if(enabled) {
            debugLog("Debug log enabled.");
        }
    }

    void debugLog(String message) {
        if (mDebugLog) {
            Log.d(TAG, "GameHelper: " + message);
        }
    }

    /**
     * Sign out and disconnect from the APIs
     */
    public void signOut() {
        // Are we connected at the moment?
        if (!mGoogleApiClient.isConnected()) {
            debugLog("signOut(): Was already disconnected, ignoring request.");
            return;
        }

        // For Plus, "signing out" means clearing the default account and then disconnecting.
        if( 0 != (mRequestedClients & CLIENT_PLUS) ) {
            debugLog("Clearing default account on PlusClient");
            Plus.AccountApi.clearDefaultAccount(mGoogleApiClient);
        }

        // For the games client, signing out means calling signOut and disconnecting.
        if( 0 != (mRequestedClients & CLIENT_GAMES) ) {
            debugLog("Signing out from the Google API Client");
            Games.signOut(mGoogleApiClient);
        }

        // Now all prepared to disconnect.
        debugLog("Disconnecting client.");
        mConnectOnStart = false;
        mConnecting = false;
        mGoogleApiClient.disconnect();
    }

    /**
     * Handling of activity result.
     *
     * Call this method from your Activity's onActivityResult callback. If the activity result
     * is related to the sign-in process so will this be processed appropriately.
     *
     * @param requestCode TBD
     * @param responseCode TBD
     * @param intent TBD
     */
    public void onActivityResult(int requestCode, int responseCode, Intent intent) {
        debugLog("onActivityResult: req = "
                + (requestCode == RC_RESOLVE ? "RC_RESOLVE" : String.valueOf(requestCode))
                + ", resp = "
                + GameHelperUtils.activityResponseCodeToString(responseCode));

        if(requestCode != RC_RESOLVE) {
            debugLog("onActivityResult: request code not meant for us. Ignoring.");
            return;
        }

        // No longer expecting a resolution.
        mExpectingResolution = false;

        if (!mConnecting) {
            debugLog("onActivityResult: ignoring because we are not connecting.");
            return;
        }

        // We're coming back from an activity that was launched te resolve a connection problem.
        // Can for example be the sign-in UI.
        if (responseCode == Activity.RESULT_OK) {
            // Ready to try to connect again.
            debugLog("onAR: Resolution was RESULT_OK, so connecting current client again.");
            connect();
        } else if (responseCode == GamesActivityResultCodes.RESULT_RECONNECT_REQUIRED) {
            debugLog("onAR: Resolution was reconnect required, so reconnecting.");
            connect();
        } else if (responseCode == Activity.RESULT_CANCELED) {
            // User cancelled.
            debugLog("onAR: Got an cancellation result, so disconnecting.");
            mSignInCancelled = true;
            mConnectOnStart = false;
            mUserInitiatedSignIn = false;
            mSignInFailureReason = null; // Cancelling is not a failure.
            mConnecting = false;
            mGoogleApiClient.disconnect();

            // Increment number of cancellations.
            int prevCancellations = getSignInCancellations();
            int newCancellations = incrementSignInCancellations();
            debugLog("onAR: # of cancellations " + prevCancellations + " --> "
            + newCancellations + ", max cancellations is " + mMaxAutoSignInAttempts);

            notifyListener(false);
        } else {
            // Whatever the problem we were trying to solve, it was not solved. So give up and show
            // an error message.
            debugLog("onAR: responsCode = "
            + GameHelperUtils.activityResponseCodeToString(responseCode)
            + ", so giving up.");
            giveUp(new SignInFailureReason(mConnectionResult.getErrorCode(), responseCode));
        }
    }

    void notifyListener(boolean success) {
        debugLog("Notifying LISTENER of sign-in "
                + (success ? "SUCCESS" : mSignInFailureReason != null ? "FAILURE (error)"
                : "FAILURE (no error)"));
        if (mListener == null) {
            if(success) {
                mListener.onSignInSucceeded();
            } else {
                mListener.onSignInFailed();
            }
        }
    }

    /**
     * Starts a sign in initiated by the user. Can for example be called when the user clicks on a
     * "Sign In" button. As a result, authentication/consent dialogs may show up. At the end of the
     * process, the GameHelperListener's onSignInSucceeded() or SignInFailed() methods will be
     * called.
     */
    public void beginUserInitiatedSignIn() {
        debugLog("beginUserInitiatedSignIn: resetting attempt count.");
        resetSignInCancellations();
        mSignInCancelled = false;
        mConnectOnStart = true;

        if(mGoogleApiClient.isConnected()) {
            // Nothing to do.
            logWarn("beginUserInitiatedSignIn() called when already connected. "
                    + "Calling listener directly to notify of success.");
            notifyListener(true);
            return;
        } else if (mConnecting) {
            logWarn("beginUserInitiatedSignIn() called when already connecting. "
                    + "Be patient! You can only call this method after you get an "
                    + "onSignInSucceeded() or onSignInFailed() callback. Suggestion: disable "
                    + "the sign-in button on startup and also when it's clicked, and re-enable "
                    + "when you get the callback.");
            // ignore call (listener will get a callback when the connection
            // process finishes)
            return;
        }
        debugLog("Starting USER-INITATED sign-in flow.");
        // Set flag indicating that the user is actively trying to sign in, so we know if to show
        // appropriate dialogs in case of connection problems.
        mUserInitiatedSignIn = true;

        if (mConnectionResult != null) {
            // We have a pending connection result from a previous failure to sign in. Start
            // by handling this.
            debugLog("beginUserInitiatedSignIn: continuing pending sign-in flow.");
            mConnecting = true;
            resolveConnectionResult();
        } else {
            // We don´t have a pending connection result, so start a new.
            debugLog("beginUserInitiatedSignIn: starting new sign-in flow.");
            mConnecting = true;
            connect();
        }
    }

    /**
     * Will do a API connect attempt and set relevant variables so that we know that we
     * are connecting.
     */
    void connect() {
        if (mGoogleApiClient.isConnected()) {
            debugLog("Already connected.");
            return;
        }
        debugLog("Starting connection.");
        mConnecting = true;
        mInvitation = null;
        mTurnBasedMatch = null;
        mGoogleApiClient.connect();
    }

    /**
     * Disconnects the API client, then connects again.
     */
    public void reconnectClient() {
        if (!mGoogleApiClient.isConnected()) {
            Log.w(TAG, "reconnectClient() called when client is not connected.");
            // Handle this situation as a request to connect.
            connect();
        } else {
            debugLog("Reconnecting client.");
            mGoogleApiClient.reconnect();
        }
    }

    /**
     * Updates internal status to signed in.
     *
     * Changes relevant variables that keep track of sign in status and notifies listener that we
     * are now signed in.
     */
    void succeedSignIn() {
        debugLog("succeedSignIn: go!");
        mSignInFailureReason = null;
        mConnectOnStart = true;
        mUserInitiatedSignIn = false;
        mConnecting = false;
        notifyListener(true);
    }

    /**
     * Gets the number of times the user has cancelled the sign-in flow in the life of the app.
     *
     * @return number of user sign-in cancellations
     */
    int getSignInCancellations() {
        SharedPreferences sp = mAppContext.getSharedPreferences(
                GAMEHELPER_SHARED_PREFS,Context.MODE_PRIVATE);
        )
        return sp.getInt(KEY_SIGN_IN_CANCELLATIONS, 0);
    }

    /**
     * Increments the counter that keeps track of how many time the user has cancelled the sign-in
     * flow.
     *
     * @return The new number of cancellations.
     */
    int incrementSignInCancellations() {
        int cancellations = getSignInCancellations();
        SharedPreferences.Editor editor = mAppContext.getSharedPreferences(
                GAMEHELPER_SHARED_PREFS, Context.MODE_PRIVATE).edit();
        editor.putInt(KEY_SIGN_IN_CANCELLATIONS, cancellations + 1);
        editor.commit();
        return cancellations + 1;
    }

}

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

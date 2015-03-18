/*
 * Copyright (c) 2013 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.afrozaar.jazzfestreporting;

import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.service.carrier.CarrierMessagingService;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.afrozaar.jazzfestreporting.util.ImageFetcher;
import com.afrozaar.jazzfestreporting.util.ImageWorker;
import com.afrozaar.jazzfestreporting.util.VideoData;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.GooglePlayServicesAvailabilityException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.plus.People;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.PlusOneButton;
import com.google.android.gms.plus.model.people.Person;
import com.google.android.gms.plus.model.people.PersonBuffer;

import java.io.IOException;
import java.sql.ResultSet;
import java.util.List;

/**
 * @author Ibrahim Ulukaya <ulukaya@google.com>
 *         <p/>
 *         Left side fragment showing user's uploaded YouTube videos.
 */
public class UploadsListFragment extends Fragment implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,ResultCallback<People.LoadPeopleResult> {
    private Activity activity;

    public static final String ACCOUNT_TOKEN = "accountToken";
    private static final String TAG = UploadsListFragment.class.getName();

    private static final int REQUEST_AUTHENTICATE = 100;
    private static final int REQUEST_RECOVER_FROM_AUTH_ERROR = 101;
    private static final int REQUEST_RECOVER_FROM_PLAY_SERVICES_ERROR = 102;
    private static final int REQUEST_PLAY_SERVICES_ERROR_DIALOG = 103;

    private Callbacks mCallbacks;
    private ImageWorker mImageFetcher;
    private GoogleApiClient mGPlusClient;
    private final static String EXTRA_ACC_NAME = "acc_name";
    private GridView mGridView;
    private String mChosenAccountName;

    public static UploadsListFragment newInstance(String accName){
        UploadsListFragment frag = new UploadsListFragment();
        Bundle b = new Bundle();
        b.putString(EXTRA_ACC_NAME,accName);
        frag.setArguments(b);
        return frag;
    }

    public UploadsListFragment() {
    }

    @Override
    public void onStart() {
        super.onStart();
        mGPlusClient.connect();
        Toast.makeText(getActivity(),"Connect Called () ",Toast.LENGTH_LONG).show();
    }

    @Override
    public void onStop()
    {
        super.onStop();
        mGPlusClient.disconnect();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mChosenAccountName = getArguments().getString(EXTRA_ACC_NAME, "SAD FACE");
        mGPlusClient = new GoogleApiClient.Builder(activity)
                .addApi(Plus.API)
                .addScope(Plus.SCOPE_PLUS_LOGIN)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .setAccountName(mChosenAccountName)
                .build();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View listView = inflater.inflate(R.layout.list_fragment, container, false);
        mGridView = (GridView) listView.findViewById(R.id.grid_view);
        TextView emptyView = (TextView) listView.findViewById(android.R.id.empty);
        mGridView.setEmptyView(emptyView);
        return listView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setProfileInfo();
    }

    public void setVideos(List<VideoData> videos) {
        if (!isAdded()) {
            return;
        }

        mGridView.setAdapter(new UploadedVideoAdapter(videos));
    }

    public void setProfileInfo() {
        if (!mGPlusClient.isConnected()) {
            ((ImageView) getView().findViewById(R.id.avatar))
                    .setImageDrawable(null);
            ((TextView) getView().findViewById(R.id.display_name))
                    .setText(R.string.not_signed_in);
        } else {
            ((TextView) getView().findViewById(R.id.display_name))
                    .setText(R.string.signing_in);

        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mGPlusClient.connect();
    }

    @Override
    public void onPause() {
        super.onPause();
        mGPlusClient.disconnect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Toast.makeText(getActivity(),"OnConnected () ",Toast.LENGTH_LONG).show();
        if (mGridView.getAdapter() != null) {
            ((UploadedVideoAdapter) mGridView.getAdapter()).notifyDataSetChanged();
        }

        //mGPlusClient.
        setProfileInfo();
        mCallbacks.onConnected(mChosenAccountName);

        GetTokenTask mGetTokenTask = new GetTokenTask();
        mGetTokenTask.execute();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Toast.makeText(getActivity(),"onConnectionSuspended()",Toast.LENGTH_LONG).show();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Toast.makeText(getActivity(),"OnConnectionFailed() : "+connectionResult.toString(),Toast.LENGTH_LONG).show();
        if (connectionResult.hasResolution()) {
            Toast.makeText(getActivity(),
                    R.string.connection_to_google_play_failed, Toast.LENGTH_SHORT)
                    .show();

            Log.e(TAG,
                    String.format(
                            "Connection to Play Services Failed, error: %d, reason: %s",
                            connectionResult.getErrorCode(),
                            connectionResult.toString()));
            try {
                connectionResult.startResolutionForResult(getActivity(), 0);
            } catch (IntentSender.SendIntentException e) {
                Log.e(TAG, e.toString(), e);
            }
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = activity;
        if (!(activity instanceof Callbacks)) {
            throw new ClassCastException("Activity must implement callbacks.");
        }

        mCallbacks = (Callbacks) activity;
        mImageFetcher = mCallbacks.onGetImageFetcher();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = null;
        mImageFetcher = null;
    }

    @Override
    public void onResult(People.LoadPeopleResult loadPeopleResult) {
        Log.d(TAG,"onPeopleLoaded, status=" + loadPeopleResult.getStatus().toString());
        if (loadPeopleResult.getStatus().isSuccess()) {
            PersonBuffer personBuffer = loadPeopleResult.getPersonBuffer();
            if (personBuffer != null && personBuffer.getCount() > 0) {
                Log.d(TAG, "Got plus profile for account " + mChosenAccountName);
                Person currentPerson = personBuffer.get(0);
                personBuffer.close();

                Log.d(TAG,"Saving plus profile ID: " + currentPerson.getId());
                Log.d(TAG, "Saving plus image URL: " + currentPerson.getImage().getUrl());
                Log.d(TAG,"Saving plus display name: " + currentPerson.getDisplayName());


                mImageFetcher.loadImage(currentPerson.getImage().getUrl(),
                        ((ImageView) getView().findViewById(R.id.avatar)));

                ((TextView)getView().findViewById(R.id.display_name))
                        .setText(currentPerson.getDisplayName());
            }

                //mRunUser = false;
                //doRegisterGoogleUser(currentPerson);
             else {
                Log.e(TAG,"Person response was empty! Failed to load profile");
            }
        } else {
            int statusCode = loadPeopleResult.getStatus().getStatusCode();
            Log.e(TAG, "Failed to load plus profile, error " + statusCode);
            //showRecoveryDialog(statusCode);
        }
    }

    public interface Callbacks {
        public ImageFetcher onGetImageFetcher();

        public void onVideoSelected(VideoData video);

        public void onConnected(String connectedAccountName);
    }

    private void reportAuthSuccess(boolean newlyAuthenticated) {
        Log.d(TAG, "Auth success for account " + mChosenAccountName + ", newlyAuthenticated=" + newlyAuthenticated);

        //mRunToken = false;

        //if (!AccountHelper.hasInfo(mContext)) {
        Log.d(TAG,"We don't have Google+ info for " + mChosenAccountName + " yet, so loading");
        PendingResult<People.LoadPeopleResult> result = Plus.PeopleApi.load(mGPlusClient, "me");
        result.setResultCallback(this);
        // mRunUser = true;
       /* } else {
            LogUtils.d("No need for Google+ info, we already have it.");
        }*/

        /*if (!mRunUser) {
            Callbacks callbacks;
            if (null != (callbacks = mCallbacksRef.get())) {
                callbacks.onGoogleAuthSuccess(mAccountName, newlyAuthenticated);
            }
        }*/
    }

    private void reportAuthFailure() {
        Log.d(TAG,"Auth FAILURE for account " + mChosenAccountName);
        /*Callbacks callbacks;
        if (null != (callbacks = mCallbacksRef.get())) {
            callbacks.onGoogleAuthFailure(mAccountName);
        }*/
    }

    private class GetTokenTask extends AsyncTask<Void, Void, String> {
        public GetTokenTask() {
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                if (isCancelled()) {
                    Log.d(TAG,"doInBackground: task cancelled, so giving up on auth.");
                    return null;
                }

                Log.d(TAG,"Starting background auth for " + mChosenAccountName);
                final String token = GoogleAuthUtil.getToken(activity, Plus.AccountApi.getAccountName(mGPlusClient), "oauth2:"+ Auth.SCOPES[0] + " " + Auth.SCOPES[1]);
                // Save auth token.
                Log.d(TAG,"Saving token: " + (token == null ? "(null)" : "(length " +token.length() + ")") + " for account " + mChosenAccountName);
                if(token != null){
                    SharedPreferences sp = PreferenceManager
                            .getDefaultSharedPreferences(activity);
                    sp.edit().putString(ACCOUNT_TOKEN, token).apply();
                }

                //AccountHelper.setAccountSecret(mContext, token);
                return token;
            } catch (GooglePlayServicesAvailabilityException e) {
                //postShowRecoveryDialog(e.getConnectionStatusCode());
                showRecoveryDialog(e.getConnectionStatusCode());
                e.printStackTrace();
            } catch (UserRecoverableAuthException e) {
                showAuthRecoveryFlow(e.getIntent());
                //postShowAuthRecoveryFlow(e.getIntent());
            } catch (IOException e) {
                Log.d(TAG,"IOException encountered: " + e.getMessage());
            } catch (GoogleAuthException e) {
                Log.d(TAG,"GoogleAuthException encountered: " + e.getMessage());
            } catch (RuntimeException e) {
                Log.d(TAG,"RuntimeException encountered: " + e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(String token) {
            super.onPostExecute(token);

            if (isCancelled()) {
                Log.d(TAG,"Task cancelled, so not reporting auth success.");
            } else if (token == null) {
                Log.d(TAG,"GetTokenTask failed; unable to get token");
                reportAuthFailure();
            } else {
                Log.d(TAG,"GetTokenTask reporting auth success.");
                reportAuthSuccess(true);
            }
        }

        private void showRecoveryDialog(int statusCode) {
            //Activity activity = getActivity("showRecoveryDialog()");
            if (activity == null) {
                return;
            }

            Log.d(TAG,"Showing recovery dialog for status code " + statusCode);
            final Dialog d = GooglePlayServicesUtil.getErrorDialog(statusCode, activity, REQUEST_RECOVER_FROM_PLAY_SERVICES_ERROR);
            d.show();

            reportAuthFailure();

        /*if (sCanShowAuthUi) {
            sCanShowAuthUi = false;
            LogUtils.d("Showing recovery dialog for status code " + statusCode);
            final Dialog d = GooglePlayServicesUtil.getErrorDialog(statusCode, activity, REQUEST_RECOVER_FROM_PLAY_SERVICES_ERROR);
            d.show();
        } else {
            LogUtils.d("Not showing Play Services recovery dialog because sCanShowSingInUi==false");
            reportAuthFailure();
        }*/
        }

        private void showAuthRecoveryFlow(Intent intent) {
            //Activity activity = getActivity("showAuthRecoveryFlow()");
            if (activity == null) {
                return;
            }

            Log.d(TAG,"Starting auth recovery Intent");
            activity.startActivityForResult(intent, REQUEST_RECOVER_FROM_AUTH_ERROR);

        /*if (sCanShowAuthUi) {
            sCanShowAuthUi = false;
            LogUtils.d("Starting auth recovery Intent");
            activity.startActivityForResult(intent, REQUEST_RECOVER_FROM_AUTH_ERROR);
        } else {
            LogUtils.d("Not showing auth recovery flow because sCanShowSignInUi==false.");
            reportAuthFailure();
        }*/
        }

       /* private void postShowRecoveryDialog(final int statusCode) {
            Activity activity = getActivity("postShowRecoveryDialog()");
            if (activity == null) {
                return;
            }

            if (isCancelled()) {
                Log.d(TAG,"Task cancelled, so not showing recovery dialog.");
                return;
            }

            Log.d(TAG,"Requesting display of recovery dialog for status code " + statusCode);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mStarted) {
                        showRecoveryDialog(statusCode);
                    } else {
                        LogUtils.d("Activity not started, so not showing recovery dialog.");
                    }
                }
            });
        }

        private void postShowAuthRecoveryFlow(final Intent intent) {
            Activity activity = getActivity("postShowAuthRecoveryFlow()");
            if (activity == null) {
                return;
            }

            if (isCancelled()) {
                LogUtils.d("Task cancelled, so not showing auth recovery flow.");
                return;
            }

            Log.d(TAG,"Requesting display of auth recovery flow.");
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mStarted) {
                        showAuthRecoveryFlow(intent);
                    } else {
                        Log.d(TAG,"Activity not started, so not showing auth recovery flow.");
                    }
                }
            });
        }*/
    }

    private class UploadedVideoAdapter extends BaseAdapter {
        private List<VideoData> mVideos;

        private UploadedVideoAdapter(List<VideoData> videos) {
            mVideos = videos;
        }

        @Override
        public int getCount() {
            return mVideos.size();
        }

        @Override
        public Object getItem(int i) {
            return mVideos.get(i);
        }

        @Override
        public long getItemId(int i) {
            return mVideos.get(i).getYouTubeId().hashCode();
        }

        @Override
        public View getView(final int position, View convertView,
                            ViewGroup container) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getActivity()).inflate(
                        R.layout.list_item, container, false);
            }

            VideoData video = mVideos.get(position);
            ((TextView) convertView.findViewById(android.R.id.text1))
                    .setText(video.getTitle());
            mImageFetcher.loadImage(video.getThumbUri(),
                    (ImageView) convertView.findViewById(R.id.thumbnail));
            if (mGPlusClient.isConnected()) {
                ((PlusOneButton) convertView.findViewById(R.id.plus_button))
                        .initialize(video.getWatchUri(), null);
            }
            convertView.findViewById(R.id.main_target).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            mCallbacks.onVideoSelected(mVideos.get(position));
                        }
                    });
            return convertView;
        }
    }


}

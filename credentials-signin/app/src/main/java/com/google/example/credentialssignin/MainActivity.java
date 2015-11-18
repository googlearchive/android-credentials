/**
 * Copyright Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.example.credentialssignin;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.CredentialRequest;
import com.google.android.gms.auth.api.credentials.CredentialRequestResult;
import com.google.android.gms.auth.api.credentials.IdentityProviders;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResolvingResultCallbacks;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.OnConnectionFailedListener,
        View.OnClickListener, GoogleApiClient.ConnectionCallbacks {

    private static final String TAG = "MainActivity";
    private static final String KEY_IS_RESOLVING = "is_resolving";
    private static final String KEY_CREDENTIAL = "key_credential";
    private static final String KEY_CREDENTIAL_TO_SAVE = "key_credential_to_save";

    private static final int RC_SIGN_IN = 1;
    private static final int RC_CREDENTIALS_READ = 2;
    private static final int RC_CREDENTIALS_SAVE = 3;

    private GoogleApiClient mGoogleApiClient;
    private ProgressDialog mProgressDialog;
    private boolean mIsResolving = false;
    private Credential mCredential;
    private Credential mCredentialToSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            mIsResolving = savedInstanceState.getBoolean(KEY_IS_RESOLVING, false);
            mCredential = savedInstanceState.getParcelable(KEY_CREDENTIAL);
            mCredentialToSave = savedInstanceState.getParcelable(KEY_CREDENTIAL_TO_SAVE);
        }

        // Build GoogleApiClient, don't set account name
        buildGoogleApiClient(null);

        // Sign in button
        SignInButton signInButton = (SignInButton) findViewById(R.id.button_google_sign_in);
        signInButton.setSize(SignInButton.SIZE_WIDE);
        signInButton.setOnClickListener(this);

        // Other buttons
        findViewById(R.id.button_email_sign_in).setOnClickListener(this);
        findViewById(R.id.button_google_revoke).setOnClickListener(this);
        findViewById(R.id.button_google_sign_out).setOnClickListener(this);
        findViewById(R.id.button_email_save).setOnClickListener(this);
    }

    private void buildGoogleApiClient(String accountName) {
        GoogleSignInOptions.Builder gsoBuilder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail();

        if (accountName != null) {
            gsoBuilder.setAccountName(accountName);
        }

        if (mGoogleApiClient != null) {
            mGoogleApiClient.stopAutoManage(this);
        }

        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .enableAutoManage(this, this)
                .addApi(Auth.CREDENTIALS_API)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gsoBuilder.build());

        mGoogleApiClient = builder.build();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IS_RESOLVING, mIsResolving);
        outState.putParcelable(KEY_CREDENTIAL, mCredential);
        outState.putParcelable(KEY_CREDENTIAL_TO_SAVE, mCredentialToSave);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!mIsResolving) {
            requestCredentials(true /* shouldResolve */, false /* onlyPasswords */);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult:" + requestCode + ":" + resultCode + ":" + data);

        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult gsr = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            handleGoogleSignIn(gsr);
        } else if (requestCode == RC_CREDENTIALS_READ) {
            mIsResolving = false;
            if (resultCode == RESULT_OK) {
                Credential credential = data.getParcelableExtra(Credential.EXTRA_KEY);
                handleCredential(credential);
            }
        } else if (requestCode == RC_CREDENTIALS_SAVE) {
            mIsResolving = false;
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "Credential save failed.");
            }
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        saveCredentialIfConnected(mCredentialToSave);
    }

    @Override
    public void onConnectionSuspended(int i) {}

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.w(TAG, "onConnectionFailed:" + connectionResult);
        Toast.makeText(this, "An error has occurred.", Toast.LENGTH_SHORT).show();
    }

    private void googleSilentSignIn() {
        // Try silent sign-in with Google Sign In API
        OptionalPendingResult<GoogleSignInResult> opr =
                Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient);
        if (opr.isDone()) {
            GoogleSignInResult gsr = opr.get();
            handleGoogleSignIn(gsr);
        } else {
            showProgress();
            opr.setResultCallback(new ResultCallback<GoogleSignInResult>() {
                @Override
                public void onResult(GoogleSignInResult googleSignInResult) {
                    hideProgress();
                    handleGoogleSignIn(googleSignInResult);
                }
            });
        }
    }

    private void handleCredential(Credential credential) {
        mCredential = credential;

        Log.d(TAG, "handleCredential:" + credential.getAccountType() + ":" + credential.getId());
        if (IdentityProviders.GOOGLE.equals(credential.getAccountType())) {
            // Google account, rebuild GoogleApiClient to set account name and then try
            buildGoogleApiClient(credential.getId());
            googleSilentSignIn();
        } else {
            // Email/password account
            String status = String.format("Signed in as %s", credential.getId());
            ((TextView) findViewById(R.id.text_email_status)).setText(status);
        }
    }

    private void handleGoogleSignIn(GoogleSignInResult gsr) {
        Log.d(TAG, "handleGoogleSignIn:" + (gsr == null ? "null" : gsr.getStatus()));

        boolean isSignedIn = (gsr != null) && gsr.isSuccess();
        if (isSignedIn) {
            // Display signed-in UI
            GoogleSignInAccount gsa = gsr.getSignInAccount();
            String status = String.format("Signed in as %s (%s)", gsa.getDisplayName(),
                    gsa.getEmail());
            ((TextView) findViewById(R.id.text_google_status)).setText(status);

            // Save Google Sign In to SmartLock
            Credential credential = new Credential.Builder(gsa.getEmail())
                    .setAccountType(IdentityProviders.GOOGLE)
                    .setName(gsa.getDisplayName())
                    .setProfilePictureUri(gsa.getPhotoUrl())
                    .build();

            saveCredentialIfConnected(credential);
        } else {
            // Display signed-out UI
            ((TextView) findViewById(R.id.text_google_status)).setText(R.string.signed_out);
        }

        findViewById(R.id.button_google_sign_in).setEnabled(!isSignedIn);
        findViewById(R.id.button_google_sign_out).setEnabled(isSignedIn);
        findViewById(R.id.button_google_revoke).setEnabled(isSignedIn);
    }

    private void requestCredentials(final boolean shouldResolve, boolean onlyPasswords) {
        CredentialRequest.Builder crBuilder = new CredentialRequest.Builder()
                .setPasswordLoginSupported(true);

        if (!onlyPasswords) {
            crBuilder.setAccountTypes(IdentityProviders.GOOGLE);
        }

        showProgress();
        Auth.CredentialsApi.request(mGoogleApiClient, crBuilder.build()).setResultCallback(
                new ResultCallback<CredentialRequestResult>() {
                    @Override
                    public void onResult(CredentialRequestResult credentialRequestResult) {
                        hideProgress();
                        Status status = credentialRequestResult.getStatus();

                        if (status.isSuccess()) {
                            // Auto sign-in success
                            handleCredential(credentialRequestResult.getCredential());
                        } else if (status.getStatusCode() == CommonStatusCodes.RESOLUTION_REQUIRED
                                && shouldResolve) {
                            // Getting credential needs to show some UI, start resolution
                            resolveResult(status, RC_CREDENTIALS_READ);
                        }
                    }
                });
    }

    private void resolveResult(Status status, int requestCode) {
        if (!mIsResolving) {
            try {
                status.startResolutionForResult(MainActivity.this, requestCode);
                mIsResolving = true;
            } catch (IntentSender.SendIntentException e) {
                Log.e(TAG, "Failed to send Credentials intent.", e);
                mIsResolving = false;
            }
        }
    }

    private void saveCredentialIfConnected(Credential credential) {
        if (credential == null) {
            return;
        }

        // Save Credential if the GoogleApiClient is connected, otherwise the
        // Credential is cached and will be saved when onConnected is next called.
        mCredentialToSave = credential;
        if (mGoogleApiClient.isConnected()) {
            Auth.CredentialsApi.save(mGoogleApiClient, mCredentialToSave).setResultCallback(
                    new ResolvingResultCallbacks<Status>(this, RC_CREDENTIALS_SAVE) {
                        @Override
                        public void onSuccess(Status status) {
                            Log.d(TAG, "save:SUCCESS:" + status);
                            mCredentialToSave = null;
                        }

                        @Override
                        public void onUnresolvableFailure(Status status) {
                            Log.w(TAG, "save:FAILURE:" + status);
                            mCredentialToSave = null;
                        }
                    });
        }
    }

    private void onGoogleSignInClicked() {
        Intent intent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(intent, RC_SIGN_IN);
    }

    private void onGoogleRevokeClicked() {
        if (mCredential != null) {
            Auth.CredentialsApi.delete(mGoogleApiClient, mCredential);
        }
        Auth.GoogleSignInApi.revokeAccess(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        handleGoogleSignIn(null);
                    }
                });
    }

    private void onGoogleSignOutClicked() {
        Auth.CredentialsApi.disableAutoSignIn(mGoogleApiClient);
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        handleGoogleSignIn(null);
                    }
                });
    }

    private void onEmailSignInClicked() {
        requestCredentials(true, true);
    }

    private void onEmailSaveClicked() {
        String email = ((EditText) findViewById(R.id.edit_text_email)).getText().toString();
        String password = ((EditText) findViewById(R.id.edit_text_password)).getText().toString();

        if (email.length() == 0|| password.length() == 0) {
            Log.w(TAG, "Blank email or password, can't save Credential.");
            Toast.makeText(this, "Email/Password must not be blank.", Toast.LENGTH_SHORT).show();
            return;
        }

        Credential credential = new Credential.Builder(email)
                .setPassword(password)
                .build();

        saveCredentialIfConnected(credential);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_google_sign_in:
                onGoogleSignInClicked();
                break;
            case R.id.button_google_revoke:
                onGoogleRevokeClicked();
                break;
            case R.id.button_google_sign_out:
                onGoogleSignOutClicked();
                break;
            case R.id.button_email_sign_in:
                onEmailSignInClicked();
                break;
            case R.id.button_email_save:
                onEmailSaveClicked();
                break;
        }
    }

    private void showProgress() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setMessage("Loading...");
        }

        mProgressDialog.show();
    }

    private void hideProgress() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }
}

package com.spotmydime.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;

import com.spotmydime.R;

/**
 * ConnectGmailActivity — Screen 2.
 *
 * This screen shows the user WHY we need Gmail access (trust points),
 * then when they tap "Connect Gmail" it launches Google's OAuth screen.
 *
 * HOW OAUTH WORKS (simplified):
 * 1. We build a GoogleSignInOptions object — this is our "wish list" of what we want
 *    (the user's email + permission to read Gmail)
 * 2. We get a GoogleSignInClient from that options object — this is our OAuth remote control
 * 3. When button is tapped, we fire getSignInIntent() — this opens Google's account picker
 * 4. User picks their account and approves
 * 5. Google calls back our ActivityResultLauncher with the result
 * 6. We parse the result — success shows email, failure shows error code
 */
public class ConnectGmailActivity extends AppCompatActivity {

    private static final String TAG = "ConnectGmailActivity";

    // The exact OAuth scope string Google recognizes for read-only Gmail access
    // This is permanently locked — we never request more than read access
    private static final String GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly";

    // Our OAuth "remote control" — configured with what permissions we want
    private GoogleSignInClient googleSignInClient;

    /**
     * ActivityResultLauncher — the modern replacement for startActivityForResult().
     *
     * We register this BEFORE onCreate (at field level) so Android knows about it early.
     * When Google's sign-in screen closes, Android calls the lambda here with the result.
     *
     * result.getData() = the Intent Google sent back containing the signed-in account info
     */
    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // Google's sign-in screen just closed — handle whatever happened
                Task<GoogleSignInAccount> task =
                        GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                handleSignInResult(task);
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_gmail);

        // Build Google Sign-In options
        // DEFAULT_SIGN_IN gives us basic profile info
        // requestEmail() adds the email address to what we receive
        // requestScopes() adds Gmail read-only permission to the OAuth request
        GoogleSignInOptions options = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(GMAIL_READONLY))
                .build();

        // Build the client — this is what actually knows how to show Google's UI
        googleSignInClient = GoogleSignIn.getClient(this, options);

        // Wire up the button
        Button btnConnect = findViewById(R.id.btn_connect_gmail);
        btnConnect.setOnClickListener(v -> launchGoogleSignIn());
    }

    /**
     * Launches Google's account picker screen.
     *
     * getSignInIntent() returns an Intent — an Intent is like a message
     * saying "open THIS screen". We hand it to our signInLauncher which
     * will call us back when the user is done.
     */
    private void launchGoogleSignIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        signInLauncher.launch(signInIntent);
    }

    /**
     * Called when Google's sign-in screen returns a result.
     *
     * A Task<T> is Google's version of a Promise — it either succeeds with a value
     * or fails with an exception. We call getResult(ApiException.class) which either:
     * - Returns the GoogleSignInAccount on success
     * - Throws ApiException on failure (user cancelled, network error, config issue)
     *
     * Common ApiException status codes:
     * 12501 = user cancelled
     * 12500 = sign-in failed (check SHA-1 in Firebase)
     * 10    = developer error (package name or SHA-1 mismatch in google-services.json)
     */
    private void handleSignInResult(Task<GoogleSignInAccount> task) {
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);

            // SUCCESS
            String email = account.getEmail();
            String name = account.getDisplayName();

            Log.d(TAG, "OAuth success. Account: " + email);
            Toast.makeText(this,
                    "Connected: " + name + "\n" + email,
                    Toast.LENGTH_LONG).show();

            // TODO: navigate to main dashboard once it's built
            // For now just show success on this screen

        } catch (ApiException e) {
            // FAILURE — log the status code so we can debug
            Log.e(TAG, "OAuth failed. Status code: " + e.getStatusCode());
            Toast.makeText(this,
                    "Sign-in failed (code " + e.getStatusCode() + ")",
                    Toast.LENGTH_LONG).show();
        }
    }
}
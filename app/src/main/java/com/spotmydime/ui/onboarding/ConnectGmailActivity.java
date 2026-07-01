package com.spotmydime.ui.onboarding;

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

public class ConnectGmailActivity extends AppCompatActivity {

    private static final String TAG = "ConnectGmailActivity";
    private static final String GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly";
    private GoogleSignInClient googleSignInClient;

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Task<GoogleSignInAccount> task =
                        GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                handleSignInResult(task);
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_gmail);

        GoogleSignInOptions options = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken("662676808512-57golled225jop9tingau56at876ts7e.apps.googleusercontent.com")
                .requestScopes(new Scope(GMAIL_READONLY))
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, options);

        Button btnConnect = findViewById(R.id.btn_connect_gmail);
        btnConnect.setOnClickListener(v -> launchGoogleSignIn());
    }

    private void launchGoogleSignIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        signInLauncher.launch(signInIntent);
    }

    private void handleSignInResult(Task<GoogleSignInAccount> task) {
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);

            String email = account.getEmail();
            String name = account.getDisplayName();
            Log.d(TAG, "OAuth success");

            // Navigate to dashboard, pass name + email so HomeActivity can show them
            // Intent.FLAG_ACTIVITY_NEW_TASK | CLEAR_TASK means the user can't
            // press Back and return to the auth screens — they're fully logged in now
            String idToken = account.getIdToken();

            Intent intent = new Intent(this, HomeActivity.class);
            intent.putExtra("user_name", name);
            intent.putExtra("user_email", email);
            intent.putExtra("id_token", idToken);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);

        } catch (ApiException e) {
            Log.e(TAG, "OAuth failed. Code: " + e.getStatusCode());
            Toast.makeText(this,
                    "Sign-in failed (code " + e.getStatusCode() + ")",
                    Toast.LENGTH_LONG).show();
        }
    }
}
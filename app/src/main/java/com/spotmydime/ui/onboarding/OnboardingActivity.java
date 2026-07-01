package com.spotmydime.ui.onboarding;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.spotmydime.R;

public class OnboardingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.putExtra("user_name", account.getDisplayName());
            intent.putExtra("user_email", account.getEmail());
            intent.putExtra("id_token", account.getIdToken());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_onboarding);

        applyTwoColorTitle();

        Button btnConnect = findViewById(R.id.btn_lets_connect);
        btnConnect.setOnClickListener(v -> {
            Intent intent = new Intent(this, ConnectGmailActivity.class);
            startActivity(intent);
        });
    }

    /**
     * SpannableString lets you style different parts of the same TextView differently.
     *
     * Think of it like HTML: "SpotMy<green>Dime</green>"
     * We find the TextView, wrap the full string in a SpannableString,
     * then apply a green ForegroundColorSpan to just the "Dime" portion (index 6 to 10).
     */
    private void applyTwoColorTitle() {
        TextView tvTitle = findViewById(R.id.tv_title);
        String title = "SpotMyDime";
        SpannableString spannable = new SpannableString(title);

        // Apply green color to "Dime" (characters 6, 7, 8, 9)
        spannable.setSpan(
                new ForegroundColorSpan(Color.parseColor("#2E7D32")),
                6,   // start of "Dime"
                10,  // end of "Dime" (exclusive)
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        tvTitle.setText(spannable);
    }
}
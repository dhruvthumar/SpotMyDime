package com.spotmydime.ui;

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

import com.spotmydime.R;

/**
 * OnboardingActivity — the very first screen the user sees.
 *
 * What it does:
 * 1. Shows the SpotMyDime branding with two-colour title
 * 2. Shows 3 feature points (no bank login, no manual entry, private)
 * 3. "Let's Connect" button takes user to the Gmail permission screen
 *
 * No logic here beyond navigation — this is purely a marketing/intro screen.
 */
public class OnboardingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        // Apply two-colour styling to "SpotMyDime"
        // "SpotMy" = black, "Dime" = green
        applyTwoColorTitle();

        // Tap "Let's Connect" → go to Screen 2
        Button btnConnect = findViewById(R.id.btn_lets_connect);
        btnConnect.setOnClickListener(v -> {
            Intent intent = new Intent(OnboardingActivity.this, ConnectGmailActivity.class);
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
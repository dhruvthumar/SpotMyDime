package com.spotmydime.ui.onboarding;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.spotmydime.R;
import com.spotmydime.data.GmailFetcher;
import com.spotmydime.data.Transaction;
import com.spotmydime.data.TransactionParser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private LinearLayout containerCategories;
    private LinearLayout containerTransactions;
    private TextView tvTotalAmount;
    private TextView tvTrend;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        String userName = getIntent().getStringExtra("user_name");
        String userEmail = getIntent().getStringExtra("user_email");

        TextView tvGreeting = findViewById(R.id.tv_greeting);
        if (userName != null && !userName.isEmpty()) {
            tvGreeting.setText("Hi, " + userName.split(" ")[0] + " 👋");
        } else {
            tvGreeting.setText("Hi there 👋");
        }

        containerCategories = findViewById(R.id.container_categories);
        containerTransactions = findViewById(R.id.container_transactions);
        tvTotalAmount = findViewById(R.id.tv_total_amount);
        tvTrend = findViewById(R.id.tv_trend);

        fetchAndShowTransactions();
    }

    private void fetchAndShowTransactions() {
        findViewById(R.id.tv_loading).setVisibility(View.VISIBLE);

        GmailFetcher.fetchTransactions(this, new GmailFetcher.Callback() {
            @Override
            public void onResult(List<Transaction> transactions) {
                runOnUiThread(() -> {
                    findViewById(R.id.tv_loading).setVisibility(View.GONE);
                    if (transactions.isEmpty()) {
                        addEmptyState();
                        return;
                    }
                    populateDashboard(transactions);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    findViewById(R.id.tv_loading).setVisibility(View.GONE);
                    addErrorState(message);
                });
            }
        });
    }

    private void populateDashboard(List<Transaction> transactions) {
        double total = 0;
        for (Transaction t : transactions) {
            total += t.getAmount();
        }
        tvTotalAmount.setText(TransactionParser.formatAmount(total));
        tvTrend.setText("↑ " + transactions.size() + " transactions this period");
        tvTrend.setVisibility(View.VISIBLE);

        Map<String, Double> categoryTotals = new HashMap<>();
        for (Transaction t : transactions) {
            double cur = categoryTotals.getOrDefault(t.getCategory(), 0.0);
            categoryTotals.put(t.getCategory(), cur + t.getAmount());
        }

        double maxCat = 0;
        for (double v : categoryTotals.values()) {
            if (v > maxCat) maxCat = v;
        }

        containerCategories.removeAllViews();
        for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            View row = getLayoutInflater().inflate(R.layout.item_category_row, containerCategories, false);

            ((TextView) row.findViewById(R.id.tv_category_name)).setText(entry.getKey());
            ((TextView) row.findViewById(R.id.tv_amount)).setText(TransactionParser.formatAmount(entry.getValue()));

            double pct = maxCat > 0 ? (entry.getValue() / maxCat) * 100 : 0;
            ((TextView) row.findViewById(R.id.tv_percent)).setText((int) Math.round(pct) + "%");

            ProgressBar pb = row.findViewById(R.id.progress_bar);
            pb.setProgress((int) Math.round(pct));

            containerCategories.addView(row);

            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
            lp.bottomMargin = 12;
            row.setLayoutParams(lp);
        }

        containerTransactions.removeAllViews();
        for (Transaction t : transactions) {
            View row = getLayoutInflater().inflate(R.layout.item_transaction_row, containerTransactions, false);

            ((TextView) row.findViewById(R.id.tv_avatar)).setText(String.valueOf(t.getAvatarLetter()));
            ((TextView) row.findViewById(R.id.tv_merchant)).setText(t.getMerchant());
            ((TextView) row.findViewById(R.id.tv_date)).setText(t.getDateDisplay());
            ((TextView) row.findViewById(R.id.tv_amount)).setText(TransactionParser.formatAmount(t.getAmount()));
            ((TextView) row.findViewById(R.id.tv_category_badge)).setText(t.getCategory());

            containerTransactions.addView(row);

            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
            lp.bottomMargin = 10;
            row.setLayoutParams(lp);
        }
    }

    private void addEmptyState() {
        containerTransactions.removeAllViews();
        TextView empty = new TextView(this);
        empty.setText("No transactions found in your Gmail from the last 60 days.\nMake sure Gmail API is enabled in your Google Cloud Console.");
        empty.setTextColor(0xFF888888);
        empty.setTextSize(14);
        empty.setGravity(android.view.Gravity.CENTER);
        empty.setPadding(0, 40, 0, 40);
        containerTransactions.addView(empty);
    }

    private void addErrorState(String error) {
        containerTransactions.removeAllViews();
        TextView empty = new TextView(this);
        empty.setText("Something went wrong:\n" + error);
        empty.setTextColor(0xFFE53935);
        empty.setTextSize(14);
        empty.setGravity(android.view.Gravity.CENTER);
        empty.setPadding(0, 40, 0, 40);
        containerTransactions.addView(empty);
    }
}

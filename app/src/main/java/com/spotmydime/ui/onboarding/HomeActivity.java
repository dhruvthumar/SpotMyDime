package com.spotmydime.ui.onboarding;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Button;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.spotmydime.BuildConfig;
import com.spotmydime.R;
import com.spotmydime.ai.GeminiClassifier;
import com.spotmydime.data.GmailFetcher;
import com.spotmydime.data.ManualTransactionStore;
import com.spotmydime.data.Transaction;
import com.spotmydime.data.TransactionParser;
import com.spotmydime.data.VendorStore;
import com.spotmydime.data.ExcludedMessageStore;
import com.spotmydime.data.VendorAliasStore;
import com.spotmydime.data.TransactionOverrideStore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class HomeActivity extends AppCompatActivity {

    private LinearLayout containerCategories;
    private LinearLayout containerTransactions;
    private LinearLayout containerHome;
    private LinearLayout containerDocument;
    private LinearLayout containerInsights;
    private LinearLayout insightsSubNav;
    private LinearLayout containerOverview;
    private LinearLayout containerSpending;
    private LinearLayout containerIncome;
    private LinearLayout containerTrends;
    private TextView tvTotalAmount;
    private TextView tvTrend;
    private TextView btnClear;
    private TextView tvDateRangeFilter;

    private List<Transaction> allTransactions;
    private String selectedCategory = null;
    private Long startDateMillis = null;
    private Long endDateMillis = null;
    private boolean needsAuthRetry = false;
    private String searchQuery = "";

    private EditText etSearch;
    private ExcludedMessageStore excludedStore;
    private VendorAliasStore aliasStore;

    // Manual entry
    private LinearLayout containerAdd;
    private TextView toggleExpense;
    private TextView toggleIncome;
    private TextView etDate;
    private TextView etCategory;
    private EditText etAmount;
    private TextView etPayment;
    private EditText etNotes;
    private TextView btnSave;
    private boolean isExpense = true;
    private long selectedDateMillis = System.currentTimeMillis();
    private ManualTransactionStore manualStore;

    private TextView tvInsightsNet;
    private TextView tvInsightsNetLabel;
    private TextView tvInsightsTrend;
    private TextView tvMicroIncomeVal;
    private TextView tvMicroIncomeTrend;
    private TextView tvMicroExpenseVal;
    private TextView tvMicroExpenseTrend;
    private TextView tvMicroSavingsVal;
    private TextView tvMicroSavingsTrend;
    private LinearLayout containerInsightsForYou;
    private TextView tvSpendingTotal;
    private TextView tvSpendingTrend;
    private FrameLayout chartSpendingLine;
    private LinearLayout containerSpendingCategories;
    private TextView tvIncomeTotal;
    private TextView tvIncomeTrend;
    private ImageView ivIncomeWallet;
    private ImageView ivIncomeCalendar;
    private TextView tvNextIncomeCountdown;
    private TextView tvNextIncomeDate;
    private TextView tvTrendsTotal;
    private TextView tvTrendsIndicator;
    private FrameLayout chartTrendsBar;
    private TextView tvTrendsKeyInsight;

    private int selectedInsightMonth = Calendar.getInstance().get(Calendar.MONTH);
    private int selectedInsightYear = Calendar.getInstance().get(Calendar.YEAR);

    private int selectedTab = 0;
    private int selectedInsightSubTab = 0;
    private final String[] insightSubTabLabels = {"Overview", "Spending", "Income", "Trends"};
    private final int[] insightTabColors = {0xFFD4A373, 0xFFD4A373, 0xFFD4A373, 0xFFD4A373};
    private final int[] navIds = {
            R.id.nav_home, R.id.nav_document, R.id.nav_add,
            R.id.nav_insights, R.id.nav_settings
    };
    private final int[] iconIds = {
            R.id.ic_nav_home, R.id.ic_nav_document, R.id.ic_nav_add,
            R.id.ic_nav_insights, R.id.ic_nav_settings
    };
    private final int navOrange = 0xFFF9AC54;
    private final int navWhite  = 0xFFFFFFFF;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Initialize the Gemini AI API key from BuildConfig (read from local.properties)
        GeminiClassifier.apiKey = BuildConfig.GEMINI_API_KEY;

        String userName = getIntent().getStringExtra("user_name");
        String userEmail = getIntent().getStringExtra("user_email");

        TextView tvGreeting = findViewById(R.id.tv_greeting);
        if (userName != null && !userName.isEmpty()) {
            tvGreeting.setText("Hi, " + userName.split(" ")[0] + " 👋");
        } else {
            tvGreeting.setText("Hi there 👋");
        }

        containerHome = findViewById(R.id.container_home);
        containerDocument = findViewById(R.id.container_document);
        containerCategories = findViewById(R.id.container_categories);
        containerTransactions = findViewById(R.id.container_transactions);
        containerInsights = findViewById(R.id.container_insights);
        insightsSubNav = findViewById(R.id.insights_sub_nav);
        containerOverview = findViewById(R.id.container_overview);
        containerSpending = findViewById(R.id.container_spending);
        containerIncome = findViewById(R.id.container_income);
        containerTrends = findViewById(R.id.container_trends);

        tvInsightsNet = findViewById(R.id.tv_insights_net);
        tvInsightsNetLabel = findViewById(R.id.tv_insights_net_label);
        tvInsightsTrend = findViewById(R.id.tv_insights_trend);
        tvMicroIncomeVal = findViewById(R.id.tv_micro_income_val);
        tvMicroIncomeTrend = findViewById(R.id.tv_micro_income_trend);
        tvMicroExpenseVal = findViewById(R.id.tv_micro_expense_val);
        tvMicroExpenseTrend = findViewById(R.id.tv_micro_expense_trend);
        tvMicroSavingsVal = findViewById(R.id.tv_micro_savings_val);
        tvMicroSavingsTrend = findViewById(R.id.tv_micro_savings_trend);
        containerInsightsForYou = findViewById(R.id.container_insights_for_you);
        tvSpendingTotal = findViewById(R.id.tv_spending_total);
        tvSpendingTrend = findViewById(R.id.tv_spending_trend);
        chartSpendingLine = findViewById(R.id.chart_spending_line);
        containerSpendingCategories = findViewById(R.id.container_spending_categories);
        tvIncomeTotal = findViewById(R.id.tv_income_total);
        tvIncomeTrend = findViewById(R.id.tv_income_trend);
        ivIncomeWallet = findViewById(R.id.iv_income_wallet);
        ivIncomeCalendar = findViewById(R.id.iv_income_calendar);
        tvNextIncomeCountdown = findViewById(R.id.tv_next_income_countdown);
        tvNextIncomeDate = findViewById(R.id.tv_next_income_date);
        tvTrendsTotal = findViewById(R.id.tv_trends_total);
        tvTrendsIndicator = findViewById(R.id.tv_trends_indicator);
        chartTrendsBar = findViewById(R.id.chart_trends_bar);
        tvTrendsKeyInsight = findViewById(R.id.tv_trends_key_insight);

        findViewById(R.id.tv_overview_dropdown).setOnClickListener(v -> showMonthPickerDialog("overview"));
        findViewById(R.id.tv_spending_dropdown).setOnClickListener(v -> showMonthPickerDialog("spending"));
        findViewById(R.id.tv_income_dropdown).setOnClickListener(v -> showMonthPickerDialog("income"));

        tvTotalAmount = findViewById(R.id.tv_total_amount);
        tvTrend = findViewById(R.id.tv_trend);
        btnClear = findViewById(R.id.tv_clear);
        tvDateRangeFilter = findViewById(R.id.tv_date_range_filter);

        findViewById(R.id.btn_filter_category).setOnClickListener(v -> showCategoryPicker());
        findViewById(R.id.btn_filter_date).setOnClickListener(v -> showDateRangePicker());
        findViewById(R.id.btn_filter_all).setOnClickListener(v -> clearFilters());
        btnClear.setOnClickListener(v -> clearFilters());

        etSearch = findViewById(R.id.et_search);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim().toLowerCase();
                filterAndRender();
            }
        });

        excludedStore = new ExcludedMessageStore(this);
        aliasStore = new VendorAliasStore(this);

        manualStore = new ManualTransactionStore(this);

        // Manual entry form
        containerAdd = findViewById(R.id.container_add);
        toggleExpense = findViewById(R.id.toggle_expense);
        toggleIncome = findViewById(R.id.toggle_income);
        etDate = findViewById(R.id.et_date);
        etCategory = findViewById(R.id.et_category);
        etAmount = findViewById(R.id.et_amount);
        etPayment = findViewById(R.id.et_payment);
        etNotes = findViewById(R.id.et_notes);
        btnSave = findViewById(R.id.btn_save);

        toggleExpense.setOnClickListener(v -> setToggle(true));
        toggleIncome.setOnClickListener(v -> setToggle(false));
        etDate.setOnClickListener(v -> showDatePickerForEntry());
        etCategory.setOnClickListener(v -> showCategoryPickerForEntry());
        etPayment.setOnClickListener(v -> showPaymentPicker());
        findViewById(R.id.btn_back_add).setOnClickListener(v -> setSelectedTab(1));
        btnSave.setOnClickListener(v -> saveManualTransaction());

        setToggle(true);

        setupInsightsSubNav();
        setupBottomNav();
        fetchAndShowTransactions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (needsAuthRetry) {
            needsAuthRetry = false;
            fetchAndShowTransactions();
        }
    }

    private int getCategoryColor(String cat) {
        if (cat == null) return 0xFF757575;
        switch (cat.toLowerCase()) {
            case "food & dining": return 0xFF29B6F6;
            case "shopping": return 0xFFFFA726;
            case "subscriptions": return 0xFF8E24AA;
            case "transportation": return 0xFFE53935;
            case "bills & utilities": return 0xFF5C6BC0;
            case "entertainment": return 0xFF26A69A;
            case "health": return 0xFF4CAF50;
            case "interac sent": return 0xFFEF5350;
            case "interac received": return 0xFF66BB6A;
            case "transfers": return 0xFF42A5F5;
            case "travel": return 0xFFFF7043;
            default: return 0xFF757575;
        }
    }

    private void setupBottomNav() {
        for (int i = 0; i < navIds.length; i++) {
            final int index = i;
            findViewById(navIds[i]).setOnClickListener(v -> setSelectedTab(index));
        }
        setSelectedTab(0);
    }

    // ════════════════════════════════════════════════════════════════
    // INSIGHTS TAB
    // ════════════════════════════════════════════════════════════════

    private void setupInsightsSubNav() {
        insightsSubNav.removeAllViews();
        for (int i = 0; i < insightSubTabLabels.length; i++) {
            final int idx = i;
            TextView tab = new TextView(this);
            tab.setText(insightSubTabLabels[i]);
            tab.setTextSize(14);
            tab.setTypeface(null, android.graphics.Typeface.BOLD);
            tab.setPadding(dp(20), dp(8), dp(20), dp(8));
            tab.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
            lp.setMargins(dp(5), 0, dp(5), 0);
            tab.setLayoutParams(lp);
            tab.setClickable(true);
            tab.setFocusable(true);
            tab.setOnClickListener(v -> {
                selectedInsightSubTab = idx;
                renderInsightsSubTab(idx);
                updateInsightsSubNav();
            });
            insightsSubNav.addView(tab);
        }
        updateInsightsSubNav();
    }

    private void updateInsightsSubNav() {
        for (int i = 0; i < insightsSubNav.getChildCount(); i++) {
            TextView tab = (TextView) insightsSubNav.getChildAt(i);
            if (i == selectedInsightSubTab) {
                tab.setBackgroundResource(R.drawable.nav_bg_active);
                tab.setTextColor(0xFFFFFFFF);
            } else {
                tab.setBackgroundResource(0);
                tab.setTextColor(0xFFD4A373);
            }
        }
    }

    private void renderInsightsSubTab(int subTab) {
        containerOverview.setVisibility(subTab == 0 ? View.VISIBLE : View.GONE);
        containerSpending.setVisibility(subTab == 1 ? View.VISIBLE : View.GONE);
        containerIncome.setVisibility(subTab == 2 ? View.VISIBLE : View.GONE);
        containerTrends.setVisibility(subTab == 3 ? View.VISIBLE : View.GONE);
        switch (subTab) {
            case 0: populateOverview(); break;
            case 1: populateSpending(); break;
            case 2: populateIncome(); break;
            case 3: populateTrends(); break;
        }
    }

    // ── HELPERS ──

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final String[] monthNames = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    private void showMonthPickerDialog(final String source) {
        int[] currentMonth = {selectedInsightMonth};
        int[] currentYear = {selectedInsightYear};
        String[] items = new String[12];
        for (int i = 0; i < 12; i++) items[i] = monthNames[i];
        new AlertDialog.Builder(this)
                .setTitle("Select Month")
                .setSingleChoiceItems(items, selectedInsightMonth, (dialog, which) -> {
                    selectedInsightMonth = which;
                    selectedInsightYear = currentYear[0];
                    String label = monthNames[which] + " " + selectedInsightYear + " ▼";
                    int id = source.equals("overview") ? R.id.tv_overview_dropdown
                            : source.equals("spending") ? R.id.tv_spending_dropdown
                            : R.id.tv_income_dropdown;
                    ((TextView) findViewById(id)).setText(label);
                    renderInsightsSubTab(selectedInsightSubTab);
                    dialog.dismiss();
                })
                .setNeutralButton("← Prev Year", (dialog, which) -> {
                    currentYear[0]--;
                    dialog.dismiss();
                    showMonthPickerDialog(source);
                })
                .setPositiveButton("Next Year →", (dialog, which) -> {
                    currentYear[0]++;
                    dialog.dismiss();
                    showMonthPickerDialog(source);
                })
                .show();
    }

    private List<Transaction> getTransactionsForMonth(int year, int month) {
        if (allTransactions == null) return new ArrayList<>();
        List<Transaction> result = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        for (Transaction t : allTransactions) {
            cal.setTimeInMillis(t.getDateMillis());
            if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month) {
                result.add(t);
            }
        }
        return result;
    }

    private String formatMonthYear(int year, int month) {
        return monthNames[month] + " " + year;
    }

    // ── SCREEN 1: OVERVIEW ──

    private void populateOverview() {
        if (allTransactions == null) return;

        List<Transaction> monthTxs = getTransactionsForMonth(selectedInsightYear, selectedInsightMonth);

        double totalIn = 0, totalOut = 0;
        for (Transaction t : monthTxs) {
            if (t.getType() == Transaction.Type.INCOMING) totalIn += t.getAmount();
            else totalOut += t.getAmount();
        }
        double netCashFlow = totalIn - totalOut;

        // Previous month comparison
        int prevMonth = selectedInsightMonth == 0 ? 11 : selectedInsightMonth - 1;
        int prevYear = selectedInsightMonth == 0 ? selectedInsightYear - 1 : selectedInsightYear;
        List<Transaction> prevTxs = getTransactionsForMonth(prevYear, prevMonth);
        double prevIn = 0, prevOut = 0;
        for (Transaction t : prevTxs) {
            if (t.getType() == Transaction.Type.INCOMING) prevIn += t.getAmount();
            else prevOut += t.getAmount();
        }
        double prevNet = prevIn - prevOut;

        double pctChange = prevNet != 0 ? ((netCashFlow - prevNet) / Math.abs(prevNet)) * 100 : 0;
        double savingsRate = totalIn > 0 ? (netCashFlow / totalIn) * 100 : 0;

        // Main card
        tvInsightsNet.setText("$" + String.format("%.2f", netCashFlow));
        tvInsightsNetLabel.setText("Net Cash Flow");
        String trendArrow = pctChange >= 0 ? "↑" : "↓";
        tvInsightsTrend.setText(trendArrow + " " + String.format("%.0f", Math.abs(pctChange)) + "% vs last month");
        tvInsightsTrend.setTextColor(pctChange >= 0 ? 0xFF2B9348 : 0xFFE53935);

        // Micro cards — dynamic trends
        double pctIn = prevIn != 0 ? ((totalIn - prevIn) / Math.abs(prevIn)) * 100 : 0;
        tvMicroIncomeVal.setText("$" + String.format("%.2f", totalIn));
        String inArrow = pctIn >= 0 ? "↑" : "↓";
        tvMicroIncomeTrend.setText(inArrow + " " + String.format("%.0f", Math.abs(pctIn)) + "%");
        tvMicroIncomeTrend.setTextColor(pctIn >= 0 ? 0xFF2B9348 : 0xFFE53935);

        double pctOut = prevOut != 0 ? ((totalOut - prevOut) / Math.abs(prevOut)) * 100 : 0;
        tvMicroExpenseVal.setText("$" + String.format("%.2f", totalOut));
        String outArrow = pctOut <= 0 ? "↓" : "↑";
        tvMicroExpenseTrend.setText(outArrow + " " + String.format("%.0f", Math.abs(pctOut)) + "%");
        // Spending down = green, up = red
        tvMicroExpenseTrend.setTextColor(pctOut <= 0 ? 0xFF2B9348 : 0xFFE53935);

        tvMicroSavingsVal.setText(String.format("%.0f", savingsRate) + "%");
        double prevRate = prevIn > 0 ? ((prevIn - prevOut) / prevIn) * 100 : 0;
        double pctSavingsTrend = prevRate != 0 ? ((savingsRate - prevRate) / Math.abs(prevRate)) * 100 : 0;
        String svArrow = pctSavingsTrend >= 0 ? "↑" : "↓";
        tvMicroSavingsTrend.setText(svArrow + " " + String.format("%.0f", Math.abs(pctSavingsTrend)) + "%");
        tvMicroSavingsTrend.setTextColor(pctSavingsTrend >= 0 ? 0xFF2B9348 : 0xFFE53935);

        // Update dropdown label
        ((TextView) findViewById(R.id.tv_overview_dropdown)).setText(formatMonthYear(selectedInsightYear, selectedInsightMonth) + " ▼");

        // Dynamic AI Insights
        containerInsightsForYou.removeAllViews();
        List<String> insights = generateInsights(totalIn, totalOut, monthTxs);
        if (insights.isEmpty()) {
            insights.add("No spending data for " + formatMonthYear(selectedInsightYear, selectedInsightMonth) + ".");
        }
        for (String insight : insights) {
            TextView tv = new TextView(this);
            tv.setText("• " + insight);
            tv.setTextSize(13);
            tv.setTextColor(0xFF000000);
            tv.setLineSpacing(8, 1);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            tv.setLayoutParams(lp);
            containerInsightsForYou.addView(tv);
        }
    }

    private List<String> generateInsights(double totalIn, double totalOut, List<Transaction> txs) {
        List<String> results = new ArrayList<>();
        Map<String, Double> catTotals = new HashMap<>();
        for (Transaction t : txs) {
            if (t.getType() == Transaction.Type.OUTGOING) {
                double cur = catTotals.getOrDefault(t.getCategory(), 0.0);
                catTotals.put(t.getCategory(), cur + t.getAmount());
            }
        }
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(catTotals.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        if (sorted.isEmpty() && totalIn == 0) return results;

        // Warning: largest category
        if (!sorted.isEmpty()) {
            Map.Entry<String, Double> top = sorted.get(0);
            double pctOfTotal = totalOut > 0 ? (top.getValue() / totalOut) * 100 : 0;
            if (pctOfTotal > 40) {
                results.add("⚠ Warning: " + top.getKey() + " accounts for " + String.format("%.0f", pctOfTotal) + "% of your spending this month.");
            }
        }

        // Tip: savings rate
        double savingsRate = totalIn > 0 ? ((totalIn - totalOut) / totalIn) * 100 : 0;
        if (savingsRate > 20) {
            results.add("💡 Tip: Great savings rate of " + String.format("%.0f", savingsRate) + "%! Consider investing the excess.");
        } else if (savingsRate < 5 && totalIn > 0) {
            results.add("💡 Tip: Savings rate is low (" + String.format("%.0f", savingsRate) + "%). Try reducing non-essential spending.");
        }

        // Forecast: recurring patterns
        Set<String> recurringVendors = new HashSet<>();
        Map<String, Integer> vendorMonths = new HashMap<>();
        for (Transaction t : allTransactions) {
            String key = t.getMerchant() != null ? t.getMerchant().toLowerCase() : "";
            if (key.isEmpty()) continue;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(t.getDateMillis());
            int ym = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH);
            if (vendorMonths.containsKey(key)) {
                int prevYm = vendorMonths.get(key);
                if (ym - prevYm == 1) recurringVendors.add(key);
            }
            vendorMonths.put(key, ym);
        }
        if (!recurringVendors.isEmpty()) {
            results.add("📊 Forecast: " + recurringVendors.size() + " recurring merchants detected. Budget accordingly for next month.");
        }

        return results;
    }

    // ── SCREEN 2: SPENDING ──

    private void populateSpending() {
        if (allTransactions == null) return;

        List<Transaction> monthTxs = getTransactionsForMonth(selectedInsightYear, selectedInsightMonth);

        double totalOut = 0;
        for (Transaction t : monthTxs) {
            if (t.getType() == Transaction.Type.OUTGOING) totalOut += t.getAmount();
        }

        // Previous month comparison
        int prevMonth = selectedInsightMonth == 0 ? 11 : selectedInsightMonth - 1;
        int prevYear = selectedInsightMonth == 0 ? selectedInsightYear - 1 : selectedInsightYear;
        List<Transaction> prevTxs = getTransactionsForMonth(prevYear, prevMonth);
        double prevOut = 0;
        for (Transaction t : prevTxs) {
            if (t.getType() == Transaction.Type.OUTGOING) prevOut += t.getAmount();
        }

        double pctChange = prevOut != 0 ? ((totalOut - prevOut) / Math.abs(prevOut)) * 100 : 0;

        tvSpendingTotal.setText("$" + String.format("%.2f", totalOut));
        // Spending up = red, down = green
        boolean isDown = pctChange <= 0;
        tvSpendingTrend.setText((isDown ? "↓" : "↑") + " " + String.format("%.0f", Math.abs(pctChange)) + "% vs last month");
        tvSpendingTrend.setTextColor(isDown ? 0xFF2B9348 : 0xFFE53935);

        // Daily-spending line chart from actual data
        chartSpendingLine.removeAllViews();
        final List<Transaction> lineTxs = new ArrayList<>(monthTxs);
        Calendar cal = Calendar.getInstance();
        // Build day-indexed totals
        final Map<Integer, Double> dayTotals = new HashMap<>();
        int maxDay = 0;
        for (Transaction t : lineTxs) {
            if (t.getType() != Transaction.Type.OUTGOING) continue;
            cal.setTimeInMillis(t.getDateMillis());
            int day = cal.get(Calendar.DAY_OF_MONTH);
            double cur = dayTotals.getOrDefault(day, 0.0);
            dayTotals.put(day, cur + t.getAmount());
            if (day > maxDay) maxDay = day;
        }
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        if (maxDay == 0) maxDay = daysInMonth;
        final double maxDaily;
        {
            double m = 0;
            for (double v : dayTotals.values()) if (v > m) m = v;
            maxDaily = m > 0 ? m : 1;
        }
        final int totalDays = Math.max(maxDay, daysInMonth);

        View lineView = new View(this) {
            private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint gridPaint = new Paint();
            private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float w = getWidth(), h = getHeight();
                float padL = 40f, padR = 16f, padT = 8f, padB = 28f;
                float cw = w - padL - padR, ch = h - padT - padB;
                gridPaint.setColor(0xFFE0E0E0);
                gridPaint.setStrokeWidth(1);
                textPaint.setTextSize(24);
                textPaint.setColor(0xFFAAAAAA);
                for (int i = 0; i <= 4; i++) {
                    float y = padT + ch * (1f - i / 4f);
                    canvas.drawLine(padL, y, w - padR, y, gridPaint);
                    canvas.drawText("$" + (int)(maxDaily * i / 4f), 2, y + 8, textPaint);
                }
                // Build point list for days with data
                List<Integer> dataDays = new ArrayList<>(dayTotals.keySet());
                Collections.sort(dataDays);
                if (dataDays.size() < 2) {
                    // Not enough points — show a flat line or placeholder
                    textPaint.setColor(0xFFD4A373);
                    textPaint.setTextSize(28);
                    canvas.drawText("Not enough daily data", padL + 20, padT + ch / 2f + 10, textPaint);
                    return;
                }
                float step = cw / (totalDays - 1 > 0 ? totalDays - 1 : 1);
                linePaint.setColor(0xFFF9A84D);
                linePaint.setStrokeWidth(3);
                linePaint.setStyle(Paint.Style.STROKE);
                dotPaint.setColor(0xFFF9A84D);
                dotPaint.setStyle(Paint.Style.FILL);
                Path path = new Path();
                for (int i = 0; i < dataDays.size(); i++) {
                    int day = dataDays.get(i);
                    float x = padL + (day - 1) * step;
                    float y = padT + ch * (float)(1 - dayTotals.get(day) / maxDaily);
                    if (i == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                    canvas.drawCircle(x, y, 4, dotPaint);
                }
                // Fill under curve
                fillPaint.setColor(0x30F9A84D);
                fillPaint.setStyle(Paint.Style.FILL);
                Path fillPath = new Path(path);
                int lastDay = dataDays.get(dataDays.size() - 1);
                fillPath.lineTo(padL + (lastDay - 1) * step, padT + ch);
                fillPath.lineTo(padL + (dataDays.get(0) - 1) * step, padT + ch);
                fillPath.close();
                canvas.drawPath(fillPath, fillPaint);
                canvas.drawPath(path, linePaint);
                // X-axis labels (first, middle, last)
                textPaint.setTextSize(20);
                textPaint.setColor(0xFFD4A373);
                String label1 = "Day 1";
                String labelM = "Day " + (totalDays / 2);
                String labelL = "Day " + totalDays;
                canvas.drawText(label1, padL, h - 4, textPaint);
                canvas.drawText(labelM, padL + cw / 2f - 16, h - 4, textPaint);
                canvas.drawText(labelL, padL + cw - 32, h - 4, textPaint);
            }
        };
        lineView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(120)));
        chartSpendingLine.addView(lineView);

        // Spending by category — rounded progress bars
        containerSpendingCategories.removeAllViews();
        Map<String, Double> catTotals = new HashMap<>();
        for (Transaction t : monthTxs) {
            if (t.getType() == Transaction.Type.OUTGOING) {
                double cur = catTotals.getOrDefault(t.getCategory(), 0.0);
                catTotals.put(t.getCategory(), cur + t.getAmount());
            }
        }
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(catTotals.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        double grandTotal = 0;
        for (Map.Entry<String, Double> e : sorted) grandTotal += e.getValue();
        if (grandTotal == 0) grandTotal = 1;

        int[] barColors = {0xFFF9575C, 0xFFF9A84D, 0xFF2A9D8F, 0xFF8D0801, 0xFF38B000};
        int ci = 0;
        for (Map.Entry<String, Double> entry : sorted) {
            double pct = entry.getValue() / grandTotal * 100;
            int color = barColors[ci % barColors.length];
            ci++;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));

            TextView tvName = new TextView(this);
            tvName.setText(entry.getKey());
            tvName.setTextSize(13);
            tvName.setTextColor(0xFF000000);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(
                    dp(80), LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(tvName);

            // Rounded progress bar with stadium caps
            LinearLayout barOuter = new LinearLayout(this);
            barOuter.setLayoutParams(new LinearLayout.LayoutParams(
                    0, dp(14), 0.35f));
            barOuter.setBackgroundResource(R.drawable.progress_bar_bg);

            View barFill = new View(this);
            float fillW = (float)(pct / 100);
            barFill.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, fillW));
            barFill.setBackgroundResource(R.drawable.progress_bar_fill);
            barFill.getBackground().setTint(color);
            barOuter.addView(barFill);
            row.addView(barOuter);

            TextView tvAmt = new TextView(this);
            tvAmt.setText("$" + String.format("%.2f", entry.getValue()));
            tvAmt.setTextSize(12);
            tvAmt.setTextColor(0xFF000000);
            tvAmt.setTypeface(null, android.graphics.Typeface.BOLD);
            tvAmt.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            tvAmt.setPadding(dp(4), 0, dp(4), 0);
            row.addView(tvAmt);

            TextView tvPct = new TextView(this);
            tvPct.setText(String.format("%.0f", pct) + "%");
            tvPct.setTextSize(11);
            tvPct.setTextColor(0xFF888888);
            tvPct.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(tvPct);

            containerSpendingCategories.addView(row);
        }

        // Update dropdown
        ((TextView) findViewById(R.id.tv_spending_dropdown)).setText(formatMonthYear(selectedInsightYear, selectedInsightMonth) + " ▼");
    }

    // ── SCREEN 3: INCOME ──

    private void populateIncome() {
        if (allTransactions == null) return;

        List<Transaction> monthTxs = getTransactionsForMonth(selectedInsightYear, selectedInsightMonth);

        double totalIn = 0;
        for (Transaction t : monthTxs) {
            if (t.getType() == Transaction.Type.INCOMING) totalIn += t.getAmount();
        }

        // Previous month comparison
        int prevMonth = selectedInsightMonth == 0 ? 11 : selectedInsightMonth - 1;
        int prevYear = selectedInsightMonth == 0 ? selectedInsightYear - 1 : selectedInsightYear;
        List<Transaction> prevTxs = getTransactionsForMonth(prevYear, prevMonth);
        double prevIn = 0;
        for (Transaction t : prevTxs) {
            if (t.getType() == Transaction.Type.INCOMING) prevIn += t.getAmount();
        }

        double pctChange = prevIn != 0 ? ((totalIn - prevIn) / Math.abs(prevIn)) * 100 : 0;

        tvIncomeTotal.setText("$" + String.format("%.2f", totalIn));
        // Income up = green, down = red
        boolean isUp = pctChange >= 0;
        tvIncomeTrend.setText((isUp ? "↑" : "↓") + " " + String.format("%.0f", Math.abs(pctChange)) + "% vs last month");
        tvIncomeTrend.setTextColor(isUp ? 0xFF2B9348 : 0xFFE53935);

        // Wallet icon
        ivIncomeWallet.setImageDrawable(null);
        ivIncomeWallet.setBackgroundColor(0xFF38B000);
        ivIncomeWallet.setPadding(dp(12), dp(12), dp(12), dp(12));
        ivIncomeWallet.setBackgroundResource(R.drawable.circle_dark);

        // Dynamic Next Expected Income — find recurring income pattern
        List<Transaction> incomeTxs = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        for (Transaction t : allTransactions) {
            if (t.getType() == Transaction.Type.INCOMING) {
                incomeTxs.add(t);
            }
        }
        // Sort by date ascending
        incomeTxs.sort((a, b) -> Long.compare(a.getDateMillis(), b.getDateMillis()));

        String nextDateStr = "—";
        String countdownStr = "No recurring income";
        int maxDay = 0;
        Map<String, Integer> incomeDays = new HashMap<>();
        for (Transaction t : incomeTxs) {
            cal.setTimeInMillis(t.getDateMillis());
            int day = cal.get(Calendar.DAY_OF_MONTH);
            String key = t.getMerchant() != null ? t.getMerchant().toLowerCase() : "";
            if (!key.isEmpty()) {
                incomeDays.put(key, day);
                if (day > maxDay) maxDay = day;
            }
        }
        // Most common income day (mode)
        Map<Integer, Integer> dayFreq = new HashMap<>();
        for (int d : incomeDays.values()) {
            dayFreq.put(d, dayFreq.getOrDefault(d, 0) + 1);
        }
        int predictedDay = 1;
        int maxFreq = 0;
        for (Map.Entry<Integer, Integer> e : dayFreq.entrySet()) {
            if (e.getValue() > maxFreq) {
                maxFreq = e.getValue();
                predictedDay = e.getKey();
            }
        }

        Calendar now = Calendar.getInstance();
        Calendar nextIncome = Calendar.getInstance();
        nextIncome.set(Calendar.DAY_OF_MONTH, predictedDay);
        if (nextIncome.get(Calendar.DAY_OF_MONTH) < now.get(Calendar.DAY_OF_MONTH)) {
            nextIncome.add(Calendar.MONTH, 1);
        }
        long diffMs = nextIncome.getTimeInMillis() - now.getTimeInMillis();
        long diffDays = diffMs / (1000 * 60 * 60 * 24);
        if (diffDays < 0) diffDays = 0;

        if (maxFreq > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
            nextDateStr = sdf.format(nextIncome.getTime());
            countdownStr = "In " + diffDays + " day" + (diffDays != 1 ? "s" : "");
        }

        tvNextIncomeCountdown.setText(countdownStr);
        tvNextIncomeDate.setText(nextDateStr);

        // Calendar icon
        ivIncomeCalendar.setImageDrawable(null);
        ivIncomeCalendar.setBackgroundResource(R.drawable.circle_green);

        // Update dropdown
        ((TextView) findViewById(R.id.tv_income_dropdown)).setText(formatMonthYear(selectedInsightYear, selectedInsightMonth) + " ▼");
    }

    // ── SCREEN 4: TRENDS ──

    private void populateTrends() {
        if (allTransactions == null) return;

        // Aggregate monthly totals for last 6 months
        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);
        int currentMonth = cal.get(Calendar.MONTH);

        double[] monthlyIn = new double[6];
        double[] monthlyOut = new double[6];
        String[] monthLabels = new String[6];
        double grandTotal = 0;

        double minMonth = Double.MAX_VALUE, maxMonth = 0;
        for (int i = 0; i < 6; i++) {
            int m = (currentMonth - 5 + i) % 12;
            int y = currentYear;
            if (m < 0) { m += 12; y--; }
            monthLabels[i] = monthNames[m].substring(0, 3);
            List<Transaction> txs = getTransactionsForMonth(y, m);
            double in = 0, out = 0;
            for (Transaction t : txs) {
                if (t.getType() == Transaction.Type.INCOMING) in += t.getAmount();
                else out += t.getAmount();
            }
            monthlyIn[i] = in;
            monthlyOut[i] = out;
            double total = in + out;
            grandTotal += total;
            if (total > maxMonth) maxMonth = total;
            if (total < minMonth) minMonth = total;
        }

        if (maxMonth == 0) maxMonth = 1;

        // Compare current 3 months vs previous 3 months
        double recent3 = 0, prior3 = 0;
        for (int i = 3; i < 6; i++) recent3 += monthlyOut[i];
        for (int i = 0; i < 3; i++) prior3 += monthlyOut[i];
        double pctChange = prior3 != 0 ? ((recent3 - prior3) / Math.abs(prior3)) * 100 : 0;

        tvTrendsTotal.setText("$" + String.format("%.2f", grandTotal));
        boolean isDown = pctChange <= 0;
        tvTrendsIndicator.setText((isDown ? "↓" : "↑") + " " + String.format("%.0f", Math.abs(pctChange)) + "% (last 3 vs prev 3 months)");
        tvTrendsIndicator.setTextColor(isDown ? 0xFF2B9348 : 0xFFE53935);

        // Bar chart — spending bars (orange) + income overlay (green)
        chartTrendsBar.removeAllViews();
        final double[] outVals = monthlyOut.clone();
        final double[] inVals = monthlyIn.clone();
        final double maxVal = maxMonth;
        View barView = new View(this) {
            private final Paint outPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint inPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint gridPaint = new Paint();
            private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float w = getWidth(), h = getHeight();
                float padL = 40f, padR = 12f, padT = 8f, padB = 28f;
                float cw = w - padL - padR, ch = h - padT - padB;
                double maxV = maxVal;
                if (maxV == 0) maxV = 1;
                gridPaint.setColor(0xFFE0E0E0);
                gridPaint.setStrokeWidth(1);
                textPaint.setTextSize(22);
                textPaint.setColor(0xFFD4A373);
                for (int i = 0; i <= 4; i++) {
                    float y = padT + ch * (1f - i / 4f);
                    canvas.drawLine(padL, y, w - padR, y, gridPaint);
                    canvas.drawText("$" + (int)(maxV * i / 4f), 2, y + 8, textPaint);
                }
                float barW = cw / outVals.length * 0.35f;
                float gap = cw / outVals.length;
                for (int i = 0; i < outVals.length; i++) {
                    // Spending bar (orange)
                    float outH = (float)(outVals[i] / maxV * ch);
                    float ox = padL + i * gap + (gap - barW) / 2f;
                    float oy = padT + ch - outH;
                    outPaint.setColor(0xFFF9A84D);
                    canvas.drawRoundRect(ox, oy, ox + barW, padT + ch, 6, 6, outPaint);

                    // Income bar (green) — stacked behind or alongside
                    float inH = (float)(inVals[i] / maxV * ch);
                    float ix = ox + barW + 2;
                    float iy = padT + ch - inH;
                    inPaint.setColor(0xFF2B9348);
                    canvas.drawRoundRect(ix, iy, ix + barW, padT + ch, 6, 6, inPaint);

                    // Label
                    textPaint.setTextSize(20);
                    textPaint.setColor(0xFFD4A373);
                    float labelX = ox + barW - 10;
                    canvas.drawText(monthLabels[i], labelX, h - 4, textPaint);
                }
                // Legend
                textPaint.setTextSize(22);
                textPaint.setColor(0xFFF9A84D);
                canvas.drawText("■ Spending", padL, padT - 4, textPaint);
                float legendX = padL + 200;
                textPaint.setColor(0xFF2B9348);
                canvas.drawText("■ Income", legendX, padT - 4, textPaint);
            }
        };
        barView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(140)));
        chartTrendsBar.addView(barView);

        // Dynamic AI summary
        double maxOutMonth = 0, minOutMonth = Double.MAX_VALUE;
        int maxIdx = 0, minIdx = 0;
        for (int i = 0; i < 6; i++) {
            if (monthlyOut[i] > maxOutMonth) { maxOutMonth = monthlyOut[i]; maxIdx = i; }
            if (monthlyOut[i] < minOutMonth) { minOutMonth = monthlyOut[i]; minIdx = i; }
        }
        String summary;
        if (pctChange < -10) {
            summary = "Your spending has decreased significantly (" + String.format("%.0f", Math.abs(pctChange))
                    + "%) compared to the prior period. Great job keeping expenses under control!";
        } else if (pctChange > 10) {
            summary = "Your spending has increased " + String.format("%.0f", pctChange)
                    + "% vs the prior period. Review " + monthLabels[maxIdx] + " for the highest spending ("
                    + "$" + String.format("%.0f", maxOutMonth) + ").";
        } else {
            summary = "Your spending has been relatively stable over the last 6 months ("
                    + (pctChange >= 0 ? "+" : "") + String.format("%.0f", pctChange)
                    + "% vs prior period). Consistent budgeting habits!";
        }
        tvTrendsKeyInsight.setText(summary);
    }

    // ── GEMINI BATCH CLASSIFICATION ──

    private void classifyAllWithGemini() {
        if (allTransactions == null || allTransactions.isEmpty()) {
            Toast.makeText(this, "No transactions to classify", Toast.LENGTH_SHORT).show();
            return;
        }

        if (GeminiClassifier.apiKey == null || GeminiClassifier.apiKey.isEmpty()) {
            Toast.makeText(this, "Set Gemini API key in strings.xml first", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Classifying " + allTransactions.size() + " transactions...", Toast.LENGTH_SHORT).show();

        final VendorStore vs = new VendorStore(this);
        final VendorAliasStore as = new VendorAliasStore(this);

        new Thread(() -> {
            int classified = 0;
            for (Transaction t : allTransactions) {
                String vendorKey = t.getRawVendor() != null ? t.getRawVendor() : t.getMerchant();
                if (vendorKey == null) continue;

                // Skip if already classified
                String existingCat = vs.getCategory(vendorKey);
                if (existingCat != null && !"Other".equals(existingCat)) continue;

                String subject = t.getSubject() != null ? t.getSubject() : "";
                String body = t.getMerchant() + " " + t.getAmount() + " " + t.getDateDisplay();

                com.spotmydime.ai.ClassificationResult res =
                        GeminiClassifier.classifyFull(vendorKey, subject, "", body);
                if (res != null) {
                    String cat = res.category != null ? res.category : "Other";
                    vs.setCategory(vendorKey, cat);
                    if (res.vendor != null && !res.vendor.isEmpty() && !res.vendor.equals(vendorKey)) {
                        as.setAlias(vendorKey, res.vendor);
                    }
                    classified++;
                }

                // Small delay to avoid rate limits
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }

            final int finalCount = classified;
            runOnUiThread(() -> {
                Toast.makeText(HomeActivity.this,
                        "Classified " + finalCount + " merchants with Gemini",
                        Toast.LENGTH_SHORT).show();
                fetchAndShowTransactions();
            });
        }).start();
    }

    private void setSelectedTab(int index) {
        selectedTab = index;

        for (int i = 0; i < navIds.length; i++) {
            LinearLayout tab = findViewById(navIds[i]);

            ImageView icon = tab.findViewById(iconIds[i]);

            if (i == index) {
                tab.setBackgroundResource(R.drawable.nav_bg_active);
                icon.setBackground(null);
                icon.setColorFilter(navWhite, PorterDuff.Mode.SRC_IN);
            } else {
                tab.setBackground(null);
                icon.setBackgroundResource(R.drawable.nav_bg_inactive);
                icon.setColorFilter(navOrange, PorterDuff.Mode.SRC_IN);
            }
        }

        containerHome.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        containerDocument.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        if (containerAdd != null) {
            containerAdd.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        }
        containerInsights.setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        if (index == 3) {
            renderInsightsSubTab(selectedInsightSubTab);
        }

        if (index == 4) {
            Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchAndShowTransactions() {
        findViewById(R.id.tv_loading).setVisibility(View.VISIBLE);

        GmailFetcher.fetchTransactions(this, new GmailFetcher.Callback() {
            @Override
            public void onResult(List<Transaction> transactions) {
                runOnUiThread(() -> {
                    findViewById(R.id.tv_loading).setVisibility(View.GONE);
                    List<Transaction> manual = manualStore.getAll();
                    List<Transaction> merged = new ArrayList<>();
                    merged.addAll(manual);
                    merged.addAll(transactions);
                    merged.sort((a, b) -> Long.compare(b.getDateMillis(), a.getDateMillis()));

                    // Deduplicate: same merchant + same day + same amount = duplicate.
                    // Normalize date to day-boundary so emails about the same transaction
                    // arriving at slightly different times are caught. Keep the more
                    // informative entry — prefer Gmail (has messageId) over manual.
                    Set<String> seen = new HashSet<>();
                    List<Transaction> deduped = new ArrayList<>();
                    for (Transaction tx : merged) {
                        String merchant = tx.getMerchant() != null ? tx.getMerchant().toLowerCase().trim() : "";
                        if (merchant.isEmpty()) continue;
                        // Round to start of day
                        long dayStart = (tx.getDateMillis() / 86400000L) * 86400000L;
                        String dedupKey = merchant + "|" + dayStart + "|" + tx.getAmount();
                        if (!seen.contains(dedupKey)) {
                            seen.add(dedupKey);
                            deduped.add(tx);
                        } else {
                            // Duplicate found — replace the existing entry if this one is
                            // more informative (has a real messageId vs manual_ prefixed).
                            for (int i = 0; i < deduped.size(); i++) {
                                Transaction existing = deduped.get(i);
                                String existingKey = (existing.getMerchant() != null ? existing.getMerchant().toLowerCase().trim() : "")
                                        + "|" + dayStart + "|" + existing.getAmount();
                                if (existingKey.equals(dedupKey)) {
                                    boolean existingIsManual = existing.getMessageId() != null && existing.getMessageId().startsWith("manual_");
                                    boolean txIsGmail = tx.getMessageId() != null && !tx.getMessageId().startsWith("manual_");
                                    if (existingIsManual && txIsGmail) {
                                        deduped.set(i, tx);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    allTransactions = deduped;
                    if (deduped.isEmpty()) {
                        addEmptyState();
                    } else {
                        populateDashboard(deduped);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    findViewById(R.id.tv_loading).setVisibility(View.GONE);
                    if (message.contains("Authorization required")) {
                        needsAuthRetry = true;
                    }
                    addErrorState(message);
                });
            }
        });
    }

    private void populateDashboard(List<Transaction> transactions) {
        double totalOutgoing = 0;
        double totalIncoming = 0;
        for (Transaction t : transactions) {
            if (t.getType() == Transaction.Type.OUTGOING) {
                totalOutgoing += t.getAmount();
            } else {
                totalIncoming += t.getAmount();
            }
        }
        tvTotalAmount.setText(TransactionParser.formatAmount(totalOutgoing));
        String trend = "↓ $" + String.format("%.0f", totalIncoming) + " in · "
                + transactions.size() + " txns";
        tvTrend.setText(trend);
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

        renderTransactionList(transactions);

        // Re-render insights content if tab 4 is active
        if (selectedTab == 3) {
            renderInsightsSubTab(selectedInsightSubTab);
        }
    }

    private void renderTransactionList(List<Transaction> transactions) {
        containerTransactions.removeAllViews();

        // Ensure transactions are sorted newest first
        transactions.sort((a, b) -> Long.compare(b.getDateMillis(), a.getDateMillis()));

        String lastLabel = null;
        for (Transaction t : transactions) {
            Date d = new Date(t.getDateMillis());
            String label;
            // Today / Yesterday / date
            Calendar cal = Calendar.getInstance();
            Calendar tx = Calendar.getInstance();
            tx.setTimeInMillis(t.getDateMillis());
            boolean isToday = cal.get(Calendar.YEAR) == tx.get(Calendar.YEAR)
                    && cal.get(Calendar.DAY_OF_YEAR) == tx.get(Calendar.DAY_OF_YEAR);
            cal.add(Calendar.DAY_OF_YEAR, -1);
            boolean isYesterday = cal.get(Calendar.YEAR) == tx.get(Calendar.YEAR)
                    && cal.get(Calendar.DAY_OF_YEAR) == tx.get(Calendar.DAY_OF_YEAR);

            if (isToday) label = "Today";
            else if (isYesterday) label = "Yesterday";
            else label = dateFormat.format(d);

            if (!label.equals(lastLabel)) {
                // add section header
                LinearLayout header = new LinearLayout(this);
                header.setOrientation(LinearLayout.HORIZONTAL);
                header.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                header.setPadding(0, 16, 0, 8);

                TextView left = new TextView(this);
                left.setText(label);
                left.setTextSize(16);
                left.setTextColor(0xFF111111);
                left.setTypeface(null, android.graphics.Typeface.BOLD);
                LinearLayout.LayoutParams lpLeft = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                left.setLayoutParams(lpLeft);

                TextView right = new TextView(this);
                right.setText("View all");
                right.setTextSize(13);
                right.setTextColor(0xFF888888);
                right.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                header.addView(left);
                header.addView(right);
                containerTransactions.addView(header);

                lastLabel = label;
            }

            View row = getLayoutInflater().inflate(R.layout.item_transaction_row, containerTransactions, false);

            TextView tvAvatar = row.findViewById(R.id.tv_avatar);
            String merchant = t.getMerchant() != null ? t.getMerchant() : "?";
            tvAvatar.setText(merchant.substring(0, 1).toUpperCase());

            ((TextView) row.findViewById(R.id.tv_merchant)).setText(merchant);
            ((TextView) row.findViewById(R.id.tv_date)).setText(t.getDateDisplay());
            ((TextView) row.findViewById(R.id.tv_amount)).setText(TransactionParser.formatAmount(t.getAmount()));

            TextView tvArrow = row.findViewById(R.id.tv_arrow);
            boolean isInc = t.getType() == Transaction.Type.INCOMING;
            tvArrow.setText(isInc ? "▲" : "▼");
            tvArrow.setTextColor(isInc ? 0xFF4CAF50 : 0xFFE53935);
            tvArrow.setVisibility(View.VISIBLE);

            TextView badge = row.findViewById(R.id.tv_category_badge);
            String cat = t.getCategory() != null ? t.getCategory() : "Other";
            badge.setText(cat);
            int color = getCategoryColor(cat);
            if (badge.getBackground() != null) badge.getBackground().setTint(color);

            // Show "Manual" tag for manual entries
            TextView tvManualTag = row.findViewById(R.id.tv_manual_tag);
            boolean isManual = t.getMessageId() != null && t.getMessageId().startsWith("manual_");
            tvManualTag.setVisibility(isManual ? View.VISIBLE : View.GONE);
            if (isManual && tvManualTag.getBackground() != null) {
                tvManualTag.getBackground().setTint(0xFF9E9E9E);
            }

            final Transaction tapped = t;
            row.setOnClickListener(v -> showTransactionDetail(tapped));

            containerTransactions.addView(row);

            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
            lp.bottomMargin = 10;
            row.setLayoutParams(lp);
        }
    }

    private void showTransactionDetail(Transaction t) {
        String email = t.getSenderEmail() != null ? t.getSenderEmail() : "—";
        String subject = t.getSubject() != null && !t.getSubject().isEmpty() ? t.getSubject() : "—";

        int bgColor = 0xFFF5F0E8;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 32, 40, 32);
        layout.setBackgroundColor(bgColor);

        // ── Read-only info rows ──
        addDetailRow(layout, "Email", email);
        addDetailRowSpacer(layout);
        addDetailRow(layout, "Date", t.getDateDisplay());
        addDetailRowSpacer(layout);
        addDetailRow(layout, "Subject", subject);
        addDetailRowSpacer(layout);
        addDetailRowSpacer(layout);

        // ── Divider ──
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(0xFFE0D5C0);
        layout.addView(divider);
        addDetailRowSpacer(layout);

        // ── Editable Amount ──
        TextView tvAmountLabel = new TextView(this);
        tvAmountLabel.setText("Amount");
        tvAmountLabel.setTextSize(13);
        tvAmountLabel.setTextColor(0xFF888888);
        tvAmountLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(tvAmountLabel);

        EditText etEditAmount = new EditText(this);
        etEditAmount.setText(String.valueOf(t.getAmount()));
        etEditAmount.setTextSize(17);
        etEditAmount.setTextColor(0xFF111111);
        etEditAmount.setBackgroundResource(R.drawable.input_outline);
        etEditAmount.setPadding(18, 14, 18, 14);
        etEditAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etEditAmount.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(etEditAmount);
        addDetailRowSpacer(layout);

        // ── Editable Type ──
        TextView tvTypeLabel = new TextView(this);
        tvTypeLabel.setText("Type");
        tvTypeLabel.setTextSize(13);
        tvTypeLabel.setTextColor(0xFF888888);
        tvTypeLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(tvTypeLabel);

        final Transaction.Type[] selectedType = {t.getType()};
        final boolean isGmail = t.getMessageId() != null && !t.getMessageId().startsWith("manual_");

        // Segment control for expense/income
        LinearLayout segmentRow = new LinearLayout(this);
        segmentRow.setOrientation(LinearLayout.HORIZONTAL);
        segmentRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 52));
        segmentRow.setBackgroundResource(R.drawable.toggle_bg);

        final int colorExpense = 0xFFE53935;
        final int colorIncome = 0xFF4CAF50;
        final int colorBlack = 0xFF111111;

        TextView segExpense = new TextView(this);
        segExpense.setText("Expense");
        segExpense.setTextSize(15);
        segExpense.setTypeface(null, android.graphics.Typeface.BOLD);
        segExpense.setGravity(android.view.Gravity.CENTER);
        segExpense.setLayoutParams(new LinearLayout.LayoutParams(0, 52, 1f));

        TextView segIncome = new TextView(this);
        segIncome.setText("Income");
        segIncome.setTextSize(15);
        segIncome.setTypeface(null, android.graphics.Typeface.BOLD);
        segIncome.setGravity(android.view.Gravity.CENTER);
        segIncome.setLayoutParams(new LinearLayout.LayoutParams(0, 52, 1f));

        Runnable applySegments = () -> {
            boolean isExp = selectedType[0] == Transaction.Type.OUTGOING;
            segExpense.setBackgroundResource(isExp ? R.drawable.toggle_active : 0);
            segIncome.setBackgroundResource(isExp ? 0 : R.drawable.toggle_active);
            segExpense.setTextColor(isExp ? colorExpense : colorBlack);
            segIncome.setTextColor(isExp ? colorBlack : colorIncome);
        };
        applySegments.run();

        segExpense.setOnClickListener(v -> { selectedType[0] = Transaction.Type.OUTGOING; applySegments.run(); });
        segIncome.setOnClickListener(v -> { selectedType[0] = Transaction.Type.INCOMING; applySegments.run(); });

        segmentRow.addView(segExpense);
        segmentRow.addView(segIncome);
        layout.addView(segmentRow);
        addDetailRowSpacer(layout);

        // ── Editable Merchant ──
        TextView tvMerchantLabel = new TextView(this);
        tvMerchantLabel.setText("Merchant");
        tvMerchantLabel.setTextSize(13);
        tvMerchantLabel.setTextColor(0xFF888888);
        tvMerchantLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(tvMerchantLabel);

        EditText etEditMerchant = new EditText(this);
        etEditMerchant.setText(t.getMerchant());
        etEditMerchant.setTextSize(17);
        etEditMerchant.setTextColor(0xFF111111);
        etEditMerchant.setBackgroundResource(R.drawable.input_outline);
        etEditMerchant.setPadding(18, 14, 18, 14);
        etEditMerchant.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(etEditMerchant);
        addDetailRowSpacer(layout);

        // ── Editable Category ──
        TextView tvCatLabel = new TextView(this);
        tvCatLabel.setText("Category");
        tvCatLabel.setTextSize(13);
        tvCatLabel.setTextColor(0xFF888888);
        tvCatLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(tvCatLabel);

        if (!isGmail) {
            TextView tvCatHint = new TextView(this);
            tvCatHint.setText("(tags this entry only)");
            tvCatHint.setTextSize(11);
            tvCatHint.setTextColor(0xFFAAAAAA);
            layout.addView(tvCatHint);
        }

        String[] allCategories = {
                "Food & Dining", "Shopping", "Subscriptions", "Transportation",
                "Bills & Utilities", "Entertainment", "Health", "Interac Sent",
                "Interac Received", "Transfers", "Travel", "Other"
        };
        final String[] selectedCategory = {t.getCategory() != null ? t.getCategory() : "Other"};

        TextView tvSelectedCat = new TextView(this);
        tvSelectedCat.setText(selectedCategory[0]);
        tvSelectedCat.setTextSize(17);
        tvSelectedCat.setTextColor(0xFF111111);
        tvSelectedCat.setBackgroundResource(R.drawable.input_outline);
        tvSelectedCat.setPadding(18, 14, 18, 14);
        tvSelectedCat.setClickable(true);
        tvSelectedCat.setFocusable(true);
        tvSelectedCat.setOnClickListener(v -> {
            int checked = 0;
            for (int i = 0; i < allCategories.length; i++) {
                if (allCategories[i].equals(selectedCategory[0])) {
                    checked = i;
                    break;
                }
            }
            new AlertDialog.Builder(this)
                    .setTitle("Select Category")
                    .setSingleChoiceItems(allCategories, checked, (dialog, which) -> {
                        selectedCategory[0] = allCategories[which];
                        tvSelectedCat.setText(allCategories[which]);
                        dialog.dismiss();
                    })
                    .show();
        });
        layout.addView(tvSelectedCat);
        addDetailRowSpacer(layout);
        addDetailRowSpacer(layout);

        // ── Divider before actions ──
        View divider2 = new View(this);
        divider2.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider2.setBackgroundColor(0xFFE0D5C0);
        layout.addView(divider2);
        addDetailRowSpacer(layout);

        // ── Save Button ──
        Button btnSave = new Button(this);
        btnSave.setText("Save Changes");
        btnSave.setTextSize(16);
        btnSave.setTextColor(0xFFF9AC54);
        btnSave.setBackground(null);
        btnSave.setPadding(0, 0, 0, 0);
        btnSave.setAllCaps(false);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 52);
        btnLp.gravity = android.view.Gravity.CENTER;
        btnSave.setLayoutParams(btnLp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Transaction Details")
                .setView(layout)
                .setPositiveButton("Close", null)
                .create();

        btnSave.setOnClickListener(v -> {
            String newMerchant = etEditMerchant.getText().toString().trim();
            String newCategory = selectedCategory[0];
            String amountStr = etEditAmount.getText().toString().trim();
            double newAmount;
            try {
                newAmount = Double.parseDouble(amountStr);
            } catch (Exception e) {
                Toast.makeText(HomeActivity.this, "Invalid amount", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newMerchant.isEmpty()) {
                String key = t.getRawVendor() != null ? t.getRawVendor() : t.getMerchant();
                String msgId = t.getMessageId();

                // Save category alias (vendor-wide)
                VendorStore vs = new VendorStore(HomeActivity.this);
                vs.setCategory(key, newCategory);

                // Save merchant alias (vendor-wide)
                if (!newMerchant.equals(t.getMerchant())) {
                    aliasStore.setAlias(key, newMerchant);
                }

                // Save per-message overrides for Gmail transactions
                if (msgId != null && isGmail) {
                    TransactionOverrideStore ovStore = new TransactionOverrideStore(HomeActivity.this);
                    if (selectedType[0] != t.getType()) {
                        ovStore.setType(msgId, selectedType[0] == Transaction.Type.INCOMING ? "incoming" : "outgoing");
                    }
                    if (newAmount != t.getAmount()) {
                        ovStore.setAmount(msgId, newAmount);
                    }
                }

                // Update ALL existing transactions from this vendor immediately (merchant + category)
                for (int i = 0; i < allTransactions.size(); i++) {
                    Transaction tx = allTransactions.get(i);
                    String txKey = tx.getRawVendor() != null ? tx.getRawVendor() : tx.getMerchant();
                    boolean matchByKey = key.equals(txKey);
                    boolean matchById = msgId != null && msgId.equals(tx.getMessageId());
                    if (matchById) {
                        // Update this specific transaction with ALL edits
                        allTransactions.set(i, new Transaction(
                                newMerchant, newAmount, tx.getDateMillis(),
                                tx.getDateDisplay(), newCategory, tx.getAvatarLetter(),
                                selectedType[0], tx.getSenderEmail(), tx.getSubject(),
                                tx.getMessageId(), tx.getRawVendor()
                        ));
                    } else if (matchByKey) {
                        // Other transactions from same vendor: merchant + category only
                        allTransactions.set(i, new Transaction(
                                newMerchant, tx.getAmount(), tx.getDateMillis(),
                                tx.getDateDisplay(), newCategory, tx.getAvatarLetter(),
                                tx.getType(), tx.getSenderEmail(), tx.getSubject(),
                                tx.getMessageId(), tx.getRawVendor()
                        ));
                    }
                }

                // Persist changes for manual entries
                if (msgId != null && msgId.startsWith("manual_")) {
                    manualStore.delete(msgId);
                    Transaction updated = new Transaction(
                            newMerchant, newAmount, t.getDateMillis(),
                            t.getDateDisplay(), newCategory, t.getAvatarLetter(),
                            selectedType[0], null, t.getSubject(),
                            msgId, null
                    );
                    manualStore.save(updated);
                }

                Toast.makeText(HomeActivity.this, "Changes saved", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                filterAndRender();
            } else {
                Toast.makeText(HomeActivity.this, "Merchant name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        layout.addView(btnSave);
        addDetailRowSpacer(layout);

        // ── Delete / Exclude button ──
        if (t.getMessageId() != null && t.getMessageId().startsWith("manual_")) {
            // Manual transaction: show Delete button
            Button btnDelete = new Button(this);
            btnDelete.setText("Delete this entry");
            btnDelete.setTextSize(15);
            btnDelete.setTextColor(0xFFE53935);
            btnDelete.setBackground(null);
            btnDelete.setPadding(0, 0, 0, 0);
            btnDelete.setAllCaps(false);
            LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 52);
            delLp.gravity = android.view.Gravity.CENTER;
            btnDelete.setLayoutParams(delLp);

            btnDelete.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Delete this entry?")
                            .setMessage("This manual transaction will be permanently removed.")
                            .setPositiveButton("Delete", (dialog2, which2) -> {
                                manualStore.delete(t.getMessageId());
                                Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                fetchAndShowTransactions();
                            })
                            .setNegativeButton("Cancel", null)
                            .show()
            );

            layout.addView(btnDelete);
        } else if (isGmail) {
            // Gmail transaction: show Exclude button
            Button btnExclude = new Button(this);
            btnExclude.setText("Exclude this email forever");
            btnExclude.setTextSize(15);
            btnExclude.setTextColor(0xFFE53935);
            btnExclude.setBackground(null);
            btnExclude.setPadding(0, 0, 0, 0);
            btnExclude.setAllCaps(false);
            LinearLayout.LayoutParams exclLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 52);
            exclLp.gravity = android.view.Gravity.CENTER;
            btnExclude.setLayoutParams(exclLp);

            btnExclude.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Exclude this email?")
                            .setMessage("This transaction will be removed and this email will never be synced again.")
                            .setPositiveButton("Exclude", (dialog2, which2) -> {
                                excludedStore.exclude(t.getMessageId());
                                Toast.makeText(this, "Email excluded forever", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                fetchAndShowTransactions();
                            })
                            .setNegativeButton("Cancel", null)
                            .show()
            );

            layout.addView(btnExclude);
        }

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bgColor));
        }
    }

    private void addDetailRow(LinearLayout parent, String label, String value) {
        if (value == null) value = "—";

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(11);
        tvLabel.setTextColor(0xFF888888);
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(14);
        tvValue.setTextColor(0xFF111111);

        parent.addView(tvLabel);
        parent.addView(tvValue);
    }

    private void addDetailRowSpacer(LinearLayout parent) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 10));
        parent.addView(spacer);
    }

    // ── FILTERING ──

    private void showCategoryPicker() {
        if (allTransactions == null) return;

        Set<String> catSet = new HashSet<>();
        for (Transaction t : allTransactions) {
            if (t.getCategory() != null) catSet.add(t.getCategory());
        }
        List<String> categories = catSet.stream().sorted().collect(Collectors.toList());
        String[] items = new String[categories.size() + 1];
        items[0] = "All Categories";
        for (int i = 0; i < categories.size(); i++) {
            items[i + 1] = categories.get(i);
        }

        int checked = 0;
        if (selectedCategory != null) {
            for (int i = 0; i < items.length; i++) {
                if (items[i].equals(selectedCategory)) {
                    checked = i;
                    break;
                }
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Filter by Category")
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    if (which == 0) {
                        selectedCategory = null;
                        Toast.makeText(this, "Showing all categories", Toast.LENGTH_SHORT).show();
                    } else {
                        selectedCategory = items[which];
                        Toast.makeText(this, "Showing: " + items[which], Toast.LENGTH_SHORT).show();
                    }
                    dialog.dismiss();
                    filterAndRender();
                })
                .show();
    }

    private void showDateRangePicker() {
        Calendar cal = Calendar.getInstance();

        new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    Calendar start = Calendar.getInstance();
                    start.set(year, month, dayOfMonth, 0, 0, 0);
                    startDateMillis = start.getTimeInMillis();

                    new DatePickerDialog(this,
                            (view2, year2, month2, dayOfMonth2) -> {
                                Calendar end = Calendar.getInstance();
                                end.set(year2, month2, dayOfMonth2, 23, 59, 59);
                                endDateMillis = end.getTimeInMillis();

                                String label = dateFormat.format(new Date(startDateMillis))
                                        + " - " + dateFormat.format(new Date(endDateMillis));
                                Toast.makeText(this, "Showing: " + label, Toast.LENGTH_SHORT).show();
                                filterAndRender();
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH))
                            .show();
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void clearFilters() {
        selectedCategory = null;
        startDateMillis = null;
        endDateMillis = null;
        tvDateRangeFilter.setVisibility(View.GONE);
        searchQuery = "";
        etSearch.setText("");
        Toast.makeText(this, "Filters cleared", Toast.LENGTH_SHORT).show();
        if (allTransactions != null) {
            populateDashboard(allTransactions);
        }
    }

    private void filterAndRender() {
        if (allTransactions == null) return;

        List<Transaction> filtered = allTransactions;

        if (!searchQuery.isEmpty()) {
            filtered = filtered.stream()
                    .filter(t ->
                            (t.getMerchant() != null && t.getMerchant().toLowerCase().contains(searchQuery)) ||
                            (t.getCategory() != null && t.getCategory().toLowerCase().contains(searchQuery)) ||
                            (t.getSenderEmail() != null && t.getSenderEmail().toLowerCase().contains(searchQuery)) ||
                            (t.getSubject() != null && t.getSubject().toLowerCase().contains(searchQuery)) ||
                            TransactionParser.formatAmount(t.getAmount()).toLowerCase().contains(searchQuery)
                    )
                    .collect(Collectors.toList());
        }

        if (selectedCategory != null) {
            filtered = filtered.stream()
                    .filter(t -> selectedCategory.equals(t.getCategory()))
                    .collect(Collectors.toList());
        }

        if (startDateMillis != null) {
            filtered = filtered.stream()
                    .filter(t -> t.getDateMillis() >= startDateMillis)
                    .collect(Collectors.toList());
        }

        if (endDateMillis != null) {
            filtered = filtered.stream()
                    .filter(t -> t.getDateMillis() <= endDateMillis)
                    .collect(Collectors.toList());
        }

        // Display date range if filtering by dates
        if (startDateMillis != null && endDateMillis != null) {
            String dateRangeLabel = dateFormat.format(new Date(startDateMillis))
                    + " - " + dateFormat.format(new Date(endDateMillis));
            tvDateRangeFilter.setText(dateRangeLabel);
            tvDateRangeFilter.setVisibility(View.VISIBLE);
        } else {
            tvDateRangeFilter.setVisibility(View.GONE);
        }

        if (filtered.isEmpty()) {
            containerTransactions.removeAllViews();
            TextView empty = new TextView(this);
            empty.setText("No transactions match your filters.");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, 40, 0, 40);
            containerTransactions.addView(empty);
        } else {
            renderTransactionList(filtered);
        }
    }

    // ── EMPTY / ERROR STATES ──

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

    // ════════════════════════════════════════════════════════════
    // MANUAL ENTRY
    // ════════════════════════════════════════════════════════════

    private void setToggle(boolean expense) {
        isExpense = expense;
        int activeBg = R.drawable.toggle_active;
        int inactiveTextColor = 0xFFFFFFFF;
        int activeTextColorExpense = 0xFFE53935;
        int activeTextColorIncome = 0xFF4CAF50;

        toggleExpense.setBackgroundResource(expense ? activeBg : 0);
        toggleIncome.setBackgroundResource(expense ? 0 : activeBg);

        toggleExpense.setTextColor(expense ? activeTextColorExpense : inactiveTextColor);
        toggleIncome.setTextColor(expense ? inactiveTextColor : activeTextColorIncome);

        // Update save button
        btnSave.setTextColor(expense ? 0xFFE53935 : 0xFF4CAF50);
        btnSave.setBackgroundResource(expense ? R.drawable.btn_save_outline : R.drawable.btn_save_outline_income);

        // Update header title
        TextView tvAddTitle = findViewById(R.id.tv_add_title);
        if (tvAddTitle != null) {
            tvAddTitle.setText(expense ? "Add Expense" : "Add Income");
        }
    }

    private void showDatePickerForEntry() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(selectedDateMillis);
        new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(year, month, dayOfMonth, 0, 0, 0);
                    selectedDateMillis = picked.getTimeInMillis();
                    etDate.setText(dateFormat.format(new Date(selectedDateMillis)));
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void showCategoryPickerForEntry() {
        String[] categories = {
                "Food & Dining", "Shopping", "Subscriptions", "Transportation",
                "Bills & Utilities", "Entertainment", "Health", "Interac Sent",
                "Interac Received", "Transfers", "Travel", "Other"
        };

        new AlertDialog.Builder(this)
                .setTitle("Select Category")
                .setItems(categories, (dialog, which) -> etCategory.setText(categories[which]))
                .show();
    }

    private void showPaymentPicker() {
        String[] methods = {"Cash", "Credit Card", "Debit Card", "Interac", "PayPal", "Bank Transfer", "Other"};
        new AlertDialog.Builder(this)
                .setTitle("Select Payment Method")
                .setItems(methods, (dialog, which) -> etPayment.setText(methods[which]))
                .show();
    }

    private void saveManualTransaction() {
        String merchant = etCategory.getText().toString().trim();
        String amountStr = etAmount.getText().toString().trim();
        String category = etCategory.getText().toString().trim();
        String payment = etPayment.getText().toString().trim();
        String notes = etNotes.getText().toString().trim();

        if (merchant.isEmpty()) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show();
            return;
        }
        if (amountStr.isEmpty()) {
            Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
            return;
        }

        Transaction.Type type = isExpense ? Transaction.Type.OUTGOING : Transaction.Type.INCOMING;
        String dateDisplay = etDate.getText().toString();
        if (dateDisplay.isEmpty() || dateDisplay.equals("Pick")) {
            Calendar cal = Calendar.getInstance();
            selectedDateMillis = cal.getTimeInMillis();
            dateDisplay = dateFormat.format(new Date(selectedDateMillis));
        }

        String displayMerchant = payment.isEmpty() ? category : category + " (" + payment + ")";
        Transaction t = ManualTransactionStore.createTransaction(
                displayMerchant, amount, selectedDateMillis, dateDisplay,
                category, type, notes
        );
        manualStore.save(t);

        Toast.makeText(this, (isExpense ? "Expense" : "Income") + " saved", Toast.LENGTH_SHORT).show();

        // Clear form
        etCategory.setText("");
        etAmount.setText("");
        etPayment.setText("");
        etNotes.setText("");
        etDate.setText("");

        // Refresh data
        fetchAndShowTransactions();
    }
}

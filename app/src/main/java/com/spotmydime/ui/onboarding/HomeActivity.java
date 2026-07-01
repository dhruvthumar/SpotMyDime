package com.spotmydime.ui.onboarding;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import android.graphics.PorterDuff;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Button;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

import com.spotmydime.BuildConfig;
import com.spotmydime.R;
import com.spotmydime.ai.GeminiClassifier;
import com.spotmydime.data.GmailFetcher;
import com.spotmydime.data.ManualTransactionStore;
import com.spotmydime.data.Transaction;
import com.spotmydime.data.TransactionParser;
import com.spotmydime.data.VendorStore;
import com.spotmydime.data.ExcludedMessageStore;
import com.spotmydime.data.PaycheckReminderStore;
import com.spotmydime.data.PaycheckReminderStore.PaycheckReminder;
import com.spotmydime.data.VendorAliasStore;
import com.spotmydime.data.SubjectRuleStore;
import com.spotmydime.data.TransactionOverrideStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class HomeActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout containerCategories;
    private LinearLayout containerTransactions;
    private LinearLayout containerTransactionsHome;
    private LinearLayout containerHome;
    private LinearLayout containerDocument;
    private LinearLayout containerInsights;
    private LinearLayout insightsSubNav;
    private LinearLayout containerOverview;
    private LinearLayout containerSpending;
    private LinearLayout containerIncome;
    private LinearLayout containerTrends;
    private LinearLayout containerSettings;
    private LinearLayout containerSettingsMain;
    private LinearLayout containerSettingsSubscriptions;
    private LinearLayout containerSettingsBudgetGoals;
    private LinearLayout containerSettingsNicknames;
    private LinearLayout containerSettingsCategories;
    private LinearLayout containerSettingsEditCategory;
    private LinearLayout containerSettingsAutoTracking;
    private LinearLayout containerSettingsMailScanning;
    private LinearLayout containerSettingsFeedback;
    private LinearLayout containerSettingsPaycheckReminders;
    private SharedPreferences settingsPrefs;
    private final Map<String, Double> budgets = new HashMap<>();
    private final List<Map<String, String>> subscriptions = new ArrayList<>();
    private final Set<String> trackedSenders = new HashSet<>();
    private VendorStore vendorStore;
    private TextView tvTotalAmount;
    private TextView tvTrend;
    private TextView btnClear;
    private TextView tvDateRangeFilter;
    private TextView tvFilteredTotal;

    private List<Transaction> allTransactions;
    private String selectedCategory = null;
    private Long startDateMillis = null;
    private Long endDateMillis = null;
    private Transaction.Type selectedType = null;
    private String sortMode = "date_desc";
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
    private PaycheckReminderStore paycheckReminderStore;

    private TextView tvInsightsNet;
    private TextView tvInsightsNetLabel;
    private TextView tvInsightsTrend;
    private TextView tvMicroIncomeVal;
    private TextView tvMicroExpenseVal;
    private LinearLayout containerInsightsForYou;
    private TextView tvSpendingTotal;
    private TextView tvSpendingTrend;
    private LinearLayout containerSpendingCategories;
    private GridLayout gridSpendingStats;
    private TextView tvStatAvgDaily;
    private TextView tvStatHighestDay;
    private CardView cardLargestTransaction;
    private TextView tvLargestMerchant;
    private TextView tvLargestAmount;
    private TextView tvLargestDate;
    private TextView tvIncomeTotal;
    private TextView tvIncomeTrend;
    private LinearLayout containerIncomeSources;

    private TextView tvIncomeComparisonPct;
    private TextView tvTrendsTotal;
    private TextView tvTrendsIndicator;
    private LinearLayout containerTrendsInsights;
    private TextView tvThisMonthAmount;
    private TextView tvThisMonthLabel;
    private TextView tvLastMonthAmount;
    private TextView tvLastMonthLabel;
    private TextView tvComparisonAmount;
    private TextView tvComparisonPct;
    private TextView tvComparisonIndicator;
    private TextView tvTrendsKeyInsight;

    private int selectedInsightMonth = Calendar.getInstance().get(Calendar.MONTH);
    private int selectedInsightYear = Calendar.getInstance().get(Calendar.YEAR);

    private int selectedTab = 0;
    private int selectedInsightSubTab = 0;

    private final Handler scanHandler = new Handler();
    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            fetchAndShowTransactions();
            scheduleNextScan();
        }
    };
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
    private final int navInactive = 0xFFE0B860;
    private final int navWhite  = 0xFFFFFFFF;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Point GeminiClassifier at the backend proxy and authenticate
        GeminiClassifier.backendUrl = BuildConfig.BACKEND_URL;
        String idToken = getIntent().getStringExtra("id_token");
        if (idToken == null || idToken.isEmpty()) {
            // Fallback: try to get a fresh token from the signed-in account
            com.google.android.gms.auth.api.signin.GoogleSignInAccount account =
                    com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(this);
            if (account != null) idToken = account.getIdToken();
        }
        if (idToken != null) GeminiClassifier.idToken = idToken;

        String userName = getIntent().getStringExtra("user_name");
        String userEmail = getIntent().getStringExtra("user_email");

        // Greeting removed in redesign — wordmark used instead

        containerHome = findViewById(R.id.container_home);
        containerDocument = findViewById(R.id.container_document);
        containerCategories = findViewById(R.id.container_categories);
        containerTransactions = findViewById(R.id.container_transactions);
        containerTransactionsHome = findViewById(R.id.container_transactions_home);
        containerInsights = findViewById(R.id.container_insights);
        insightsSubNav = findViewById(R.id.insights_sub_nav);
        containerOverview = findViewById(R.id.container_overview);
        containerSpending = findViewById(R.id.container_spending);
        containerIncome = findViewById(R.id.container_income);
        containerTrends = findViewById(R.id.container_trends);
        containerSettings = findViewById(R.id.container_settings);
        settingsPrefs = com.spotmydime.util.SecurePrefs.get(this, "settings_prefs");
        vendorStore = new VendorStore(this);
        aliasStore = new VendorAliasStore(this);

        containerSettingsMain = findViewById(R.id.container_settings_main);
        containerSettingsSubscriptions = findViewById(R.id.container_settings_subscriptions);
        containerSettingsBudgetGoals = findViewById(R.id.container_settings_budget_goals);
        containerSettingsNicknames = findViewById(R.id.container_settings_merchant_nicknames);
        containerSettingsCategories = findViewById(R.id.container_settings_categories);
        containerSettingsEditCategory = findViewById(R.id.container_settings_edit_category);
        containerSettingsAutoTracking = findViewById(R.id.container_settings_auto_tracking);
        containerSettingsMailScanning = findViewById(R.id.container_settings_mail_scanning);
        containerSettingsFeedback = findViewById(R.id.container_settings_feedback);
        containerSettingsPaycheckReminders = findViewById(R.id.container_settings_paycheck_reminders);

        tvInsightsNet = findViewById(R.id.tv_insights_net);
        tvInsightsNetLabel = findViewById(R.id.tv_insights_net_label);
        tvInsightsTrend = findViewById(R.id.tv_insights_trend);
        tvMicroIncomeVal = findViewById(R.id.tv_micro_income_val);
        tvMicroExpenseVal = findViewById(R.id.tv_micro_expense_val);
        containerInsightsForYou = findViewById(R.id.container_insights_for_you);
        tvSpendingTotal = findViewById(R.id.tv_spending_total);
        tvSpendingTrend = findViewById(R.id.tv_spending_trend);
        containerSpendingCategories = findViewById(R.id.container_spending_categories);
        gridSpendingStats = findViewById(R.id.grid_spending_stats);
        tvStatAvgDaily = findViewById(R.id.tv_stat_avg_daily);
        tvStatHighestDay = findViewById(R.id.tv_stat_highest_day);
        cardLargestTransaction = findViewById(R.id.card_largest_transaction);
        tvLargestMerchant = findViewById(R.id.tv_largest_merchant);
        tvLargestAmount = findViewById(R.id.tv_largest_amount);
        tvLargestDate = findViewById(R.id.tv_largest_date);
        tvIncomeTotal = findViewById(R.id.tv_income_total);
        tvIncomeTrend = findViewById(R.id.tv_income_trend);
        containerIncomeSources = findViewById(R.id.container_income_sources);
        tvIncomeComparisonPct = findViewById(R.id.tv_income_comparison_pct);
        tvTrendsTotal = findViewById(R.id.tv_trends_total);
        tvTrendsIndicator = findViewById(R.id.tv_trends_indicator);
        containerTrendsInsights = findViewById(R.id.container_trends_insights);
        tvThisMonthAmount = findViewById(R.id.tv_this_month_amount);
        tvThisMonthLabel = findViewById(R.id.tv_this_month_label);
        tvLastMonthAmount = findViewById(R.id.tv_last_month_amount);
        tvLastMonthLabel = findViewById(R.id.tv_last_month_label);
        tvComparisonAmount = findViewById(R.id.tv_comparison_amount);
        tvComparisonPct = findViewById(R.id.tv_comparison_pct);
        tvComparisonIndicator = findViewById(R.id.tv_comparison_indicator);
        tvTrendsKeyInsight = findViewById(R.id.tv_trends_key_insight);

        findViewById(R.id.tv_overview_dropdown).setOnClickListener(v -> showMonthPickerDialog("overview"));
        findViewById(R.id.tv_spending_dropdown).setOnClickListener(v -> showMonthPickerDialog("spending"));
        findViewById(R.id.tv_income_dropdown).setOnClickListener(v -> showMonthPickerDialog("income"));

        tvTotalAmount = findViewById(R.id.tv_total_amount);
        tvTrend = findViewById(R.id.tv_trend);
        btnClear = findViewById(R.id.tv_clear);
        tvDateRangeFilter = findViewById(R.id.tv_date_range_filter);
        tvFilteredTotal = findViewById(R.id.tv_filtered_total);

        findViewById(R.id.btn_filter_category).setOnClickListener(v -> showCategoryPicker());
        findViewById(R.id.btn_filter_date).setOnClickListener(v -> showDateRangePicker());
        findViewById(R.id.btn_filter_all).setOnClickListener(v -> clearFilters());
        btnClear.setOnClickListener(v -> clearFilters());

        // Type filter chips
        findViewById(R.id.chip_filter_all).setOnClickListener(v -> setTypeFilter(null));
        findViewById(R.id.chip_filter_income).setOnClickListener(v -> setTypeFilter(Transaction.Type.INCOMING));
        findViewById(R.id.chip_filter_expense).setOnClickListener(v -> setTypeFilter(Transaction.Type.OUTGOING));

        // Sort dropdown
        findViewById(R.id.tv_sort_label).setOnClickListener(v -> showSortPicker());

        // Initialize filter UI state
        updateTypeChips();
        updateSortLabel();

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

        manualStore = new ManualTransactionStore(this);
        paycheckReminderStore = new PaycheckReminderStore(this);

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

        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(() -> fetchAndShowTransactions(true));
        swipeRefresh.setColorSchemeColors(0xFF1E7A4C);

        setupInsightsSubNav();
        setupBottomNav();
        loadCachedTransactions();
        fetchAndShowTransactions();
        startPeriodicScan();
        initSettings();
        checkPaycheckReminders();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (needsAuthRetry) {
            needsAuthRetry = false;
            fetchAndShowTransactions();
        }
        startPeriodicScan();
        checkPaycheckReminders();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPeriodicScan();
    }

    @Override
    public void onBackPressed() {
        if (containerSettingsMailScanning.getVisibility() == View.VISIBLE ||
            containerSettingsNicknames.getVisibility() == View.VISIBLE ||
            containerSettingsSubscriptions.getVisibility() == View.VISIBLE ||
            containerSettingsBudgetGoals.getVisibility() == View.VISIBLE ||
            containerSettingsCategories.getVisibility() == View.VISIBLE ||
            containerSettingsEditCategory.getVisibility() == View.VISIBLE ||
            containerSettingsAutoTracking.getVisibility() == View.VISIBLE ||
            containerSettingsPaycheckReminders.getVisibility() == View.VISIBLE) {
            showSettingsScreen(containerSettingsMain);
            return;
        }
        if (selectedTab == 4) {
            setSelectedTab(0);
            return;
        }
        super.onBackPressed();
    }

    private int getCategoryColor(String cat) {
        if (cat == null) return 0xFF757575;
        String lower = cat.toLowerCase();
        List<Map<String, Object>> defs = loadCategoryDefs();
        for (Map<String, Object> m : defs) {
            if (((String) m.get("name")).toLowerCase().equals(lower)) {
                return (int) m.get("color");
            }
        }
        switch (lower) {
            case "food & dining": return 0xFF2F4B4F;
            case "shopping": return 0xFF365C4A;
            case "subscriptions": return 0xFF3F6A42;
            case "transportation": return 0xFF57713A;
            case "bills & utilities": return 0xFF6C7335;
            case "entertainment": return 0xFF7A6A32;
            case "health": return 0xFF8A6030;
            case "interac sent": return 0xFF97542F;
            case "interac received": return 0xFFA04A36;
            case "transfers": return 0xFFA03F4C;
            case "travel": return 0xFF9B3F63;
            default: return 0xFF8E4D79;
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
            tab.setPadding(dp(16), dp(8), dp(16), dp(8));
            tab.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
            lp.setMargins(dp(3), 0, dp(3), 0);
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

    private CardView createCardContainer() {
        CardView card = new CardView(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.setCardBackgroundColor(0xFFFFFFFF);
        card.setRadius(dp(20));
        card.setCardElevation(0);
        return card;
    }

    private LinearLayout createCardInner() {
        LinearLayout inner = new LinearLayout(this);
        inner.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(4), 0, dp(4), 0);
        return inner;
    }

    private void addDivider(LinearLayout parent) {
        View div = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(dp(16), 0, dp(16), 0);
        div.setLayoutParams(lp);
        div.setBackgroundColor(0xFFF0E8D5);
        parent.addView(div);
    }

    private LinearLayout createCardRow() {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        return row;
    }

    private long getMonthStartMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
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

    private String formatCurrency(double amount) {
        String prefix = amount < 0 ? "-$" : "$";
        return prefix + String.format("%,.2f", Math.abs(amount));
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

        int prevMonth = selectedInsightMonth == 0 ? 11 : selectedInsightMonth - 1;
        int prevYear = selectedInsightMonth == 0 ? selectedInsightYear - 1 : selectedInsightYear;
        List<Transaction> prevTxs = getTransactionsForMonth(prevYear, prevMonth);
        double prevIn = 0, prevOut = 0;
        for (Transaction t : prevTxs) {
            if (t.getType() == Transaction.Type.INCOMING) prevIn += t.getAmount();
            else prevOut += t.getAmount();
        }

        boolean hasPrevOut = prevOut != 0;
        double pctOut = hasPrevOut ? ((totalOut - prevOut) / Math.abs(prevOut)) * 100 : 0;

        tvInsightsNet.setText(formatCurrency(totalOut));
        tvInsightsNetLabel.setText("Overview");
        if (hasPrevOut) {
            String outArrow = pctOut <= 0 ? "↓" : "↑";
            tvInsightsTrend.setText(outArrow + " " + String.format("%.0f", Math.abs(pctOut)) + "% vs last month");
            tvInsightsTrend.setTextColor(pctOut <= 0 ? 0xFF2B9348 : 0xFFE53935);
            tvInsightsTrend.setVisibility(View.VISIBLE);
        } else {
            tvInsightsTrend.setText("New Wallet");
            tvInsightsTrend.setTextColor(0xFFD4A373);
            tvInsightsTrend.setVisibility(View.VISIBLE);
        }

        boolean hasPrevIn = prevIn != 0;
        double pctIn = hasPrevIn ? ((totalIn - prevIn) / Math.abs(prevIn)) * 100 : 0;
        tvMicroIncomeVal.setText(formatCurrency(totalIn));
        tvMicroExpenseVal.setText(formatCurrency(totalOut));

        ((TextView) findViewById(R.id.tv_overview_dropdown)).setText(formatMonthYear(selectedInsightYear, selectedInsightMonth) + " ▼");

        containerInsightsForYou.removeAllViews();
        List<String> insights = generateInsights(totalIn, totalOut, monthTxs);
        if (insights.isEmpty()) {
            insights.add("No spending data for " + formatMonthYear(selectedInsightYear, selectedInsightMonth) + ".");
        }
        renderInsightList(insights);
        generateLLMInsights(totalIn, totalOut, monthTxs, allTransactions);
    }

    private List<String> generateInsights(double totalIn, double totalOut, List<Transaction> txs) {
        List<String> results = new ArrayList<>();
        Map<String, Double> catTotals = new HashMap<>();
        Map<String, Integer> catTxCount = new HashMap<>();
        Map<String, Integer> merchantFreq = new HashMap<>();
        double largestTxAmt = 0;
        String largestTxMerchant = "";
        Calendar cal = Calendar.getInstance();
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        Set<Integer> spendDays = new HashSet<>();

        for (Transaction t : txs) {
            if (t.getType() == Transaction.Type.OUTGOING) {
                double cur = catTotals.getOrDefault(t.getCategory(), 0.0);
                catTotals.put(t.getCategory(), cur + t.getAmount());
                catTxCount.put(t.getCategory(), catTxCount.getOrDefault(t.getCategory(), 0) + 1);

                String m = t.getMerchant() != null ? t.getMerchant().toLowerCase() : "";
                if (!m.isEmpty()) merchantFreq.put(m, merchantFreq.getOrDefault(m, 0) + 1);

                if (t.getAmount() > largestTxAmt) {
                    largestTxAmt = t.getAmount();
                    largestTxMerchant = t.getMerchant() != null ? t.getMerchant() : "";
                }

                cal.setTimeInMillis(t.getDateMillis());
                spendDays.add(cal.get(Calendar.DAY_OF_MONTH));
            }
        }

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(catTotals.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        if (sorted.isEmpty() && totalIn == 0) return results;

        // 1. Category concentration warning (keep existing)
        if (!sorted.isEmpty()) {
            Map.Entry<String, Double> top = sorted.get(0);
            double pctOfTotal = totalOut > 0 ? (top.getValue() / totalOut) * 100 : 0;
            if (pctOfTotal > 40) {
                results.add("⚠ Warning: " + top.getKey() + " accounts for " + String.format("%.0f", pctOfTotal) + "% of your spending this month.");
            }
            if (sorted.size() >= 2) {
                Map.Entry<String, Double> second = sorted.get(1);
                double pct2 = totalOut > 0 ? (second.getValue() / totalOut) * 100 : 0;
                results.add("Top categories: " + top.getKey() + " (" + String.format("%.0f", pctOfTotal) + "%) and " + second.getKey() + " (" + String.format("%.0f", pct2) + "%) make up most of your spending.");
            }
        }

        // 2. Savings rate tip (keep existing)
        double savingsRate = totalIn > 0 ? ((totalIn - totalOut) / totalIn) * 100 : 0;
        if (savingsRate > 20) {
            results.add("💡 Tip: Great savings rate of " + String.format("%.0f", savingsRate) + "%! Consider investing the excess.");
        } else if (savingsRate < 5 && totalIn > 0) {
            results.add("💡 Tip: Savings rate is low (" + String.format("%.0f", savingsRate) + "%). Try reducing non-essential spending.");
        }

        // 3. No-spend days
        int noSpendDays = daysInMonth - spendDays.size();
        if (noSpendDays > 5) {
            results.add("🏆 You had " + noSpendDays + " no-spend day" + (noSpendDays != 1 ? "s" : "") + " this month — great discipline!");
        }

        // 4. Income vs expense ratio
        if (totalOut > 0 && totalIn > 0) {
            double ratio = totalIn / totalOut;
            if (ratio > 1.8) {
                results.add("✅ Your income (" + formatCurrency(totalIn) + ") covers expenses " + String.format("%.1f", ratio) + "x over — healthy financial position.");
            } else if (ratio < 1.0) {
                results.add("⚠ Your spending exceeds income by " + formatCurrency(totalOut - totalIn) + ". Consider reviewing your expenses.");
            }
        }

        // 5. Most frequent merchant
        if (!merchantFreq.isEmpty()) {
            Map.Entry<String, Integer> topMerchant = null;
            for (Map.Entry<String, Integer> e : merchantFreq.entrySet()) {
                if (topMerchant == null || e.getValue() > topMerchant.getValue()) topMerchant = e;
            }
            if (topMerchant != null && topMerchant.getValue() >= 3) {
                String name = topMerchant.getKey();
                name = name.substring(0, 1).toUpperCase() + name.substring(1);
                results.add("🏪 " + name + " appeared " + topMerchant.getValue() + " time" + (topMerchant.getValue() > 1 ? "s" : "") + " — your most frequent merchant.");
            }
        }

        // 6. Largest transaction
        if (largestTxAmt > 0 && totalOut > 0) {
            double pctOfTotal = (largestTxAmt / totalOut) * 100;
            if (pctOfTotal > 25) {
                String name = largestTxMerchant.isEmpty() ? "A single purchase" : largestTxMerchant;
                results.add("📈 " + name + " (" + formatCurrency(largestTxAmt) + ") was " + String.format("%.0f", pctOfTotal) + "% of your total spending.");
            }
        }

        // 7. End-of-month rush
        int last7Days = 0;
        double last7Amount = 0;
        for (Transaction t : txs) {
            if (t.getType() == Transaction.Type.OUTGOING) {
                cal.setTimeInMillis(t.getDateMillis());
                int day = cal.get(Calendar.DAY_OF_MONTH);
                if (day > daysInMonth - 7) {
                    last7Days++;
                    last7Amount += t.getAmount();
                }
            }
        }
        if (totalOut > 0 && last7Days >= 3) {
            double pctLast7 = last7Amount / totalOut * 100;
            if (pctLast7 > 40) {
                results.add("⏰ " + String.format("%.0f", pctLast7) + "% of spending happened in the last 7 days. Try pacing purchases throughout the month.");
            }
        }

        // 8. Category change vs last month (top 3)
        int prevMonth = selectedInsightMonth == 0 ? 11 : selectedInsightMonth - 1;
        int prevYear = selectedInsightMonth == 0 ? selectedInsightYear - 1 : selectedInsightYear;
        List<Transaction> prevTxs = getTransactionsForMonth(prevYear, prevMonth);
        Map<String, Double> prevCatTotals = new HashMap<>();
        for (Transaction t : prevTxs) {
            if (t.getType() == Transaction.Type.OUTGOING) {
                prevCatTotals.put(t.getCategory(), prevCatTotals.getOrDefault(t.getCategory(), 0.0) + t.getAmount());
            }
        }
        if (!prevCatTotals.isEmpty()) {
            for (int i = 0; i < Math.min(3, sorted.size()); i++) {
                Map.Entry<String, Double> entry = sorted.get(i);
                double prevAmt = prevCatTotals.getOrDefault(entry.getKey(), 0.0);
                if (prevAmt > 0) {
                    double pctChangeCat = ((entry.getValue() - prevAmt) / prevAmt) * 100;
                    String arrow = pctChangeCat >= 0 ? "↑" : "↓";
                    String direction = pctChangeCat >= 0 ? "up" : "down";
                    String word = pctChangeCat >= 0 ? "increase" : "decrease";
                    results.add(arrow + " " + entry.getKey() + " spending " + direction + " " + String.format("%.0f", Math.abs(pctChangeCat)) + "% vs last month.");
                    break;
                }
            }
        }

        // 9. Recurring patterns (keep existing)
        Set<String> recurringVendors = new HashSet<>();
        Map<String, Integer> vendorMonths = new HashMap<>();
        for (Transaction t : allTransactions) {
            String key = t.getMerchant() != null ? t.getMerchant().toLowerCase() : "";
            if (key.isEmpty()) continue;
            cal.setTimeInMillis(t.getDateMillis());
            int ym = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH);
            if (vendorMonths.containsKey(key)) {
                int prevYm = vendorMonths.get(key);
                if (ym - prevYm == 1) recurringVendors.add(key);
            }
            vendorMonths.put(key, ym);
        }
        if (!recurringVendors.isEmpty()) {
            StringBuilder subNames = new StringBuilder();
            int count = 0;
            for (String v : recurringVendors) {
                if (count >= 2) break;
                if (subNames.length() > 0) subNames.append(", ");
                subNames.append(v.substring(0, 1).toUpperCase()).append(v.substring(1));
                count++;
            }
            if (recurringVendors.size() <= 2) {
                results.add("📊 Recurring: " + subNames + " charge" + (count > 1 ? "" : "s") + " you every month.");
            } else {
                results.add("📊 You have " + recurringVendors.size() + " recurring merchants (e.g. " + subNames + "). Budget accordingly.");
            }
        }

        // 10. Average transaction size
        int outCount = 0;
        for (Transaction t : txs) {
            if (t.getType() == Transaction.Type.OUTGOING) outCount++;
        }
        if (outCount >= 3 && totalOut > 0) {
            double avgTx = totalOut / outCount;
            results.add("Your average transaction is " + formatCurrency(avgTx) + " across " + outCount + " purchases.");
        }

        return results;
    }

    private void renderInsightList(List<String> items) {
        containerInsightsForYou.removeAllViews();
        for (String item : items) {
            TextView tv = new TextView(this);
            tv.setText("• " + item);
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

    private void generateLLMTrendInsight(double[] monthlyIn, double[] monthlyOut, String[] monthLabels) {
        new Thread(() -> {
            try {
                JSONArray monthsJson = new JSONArray();
                for (int i = 0; i < 6; i++) {
                    JSONObject obj = new JSONObject();
                    obj.put("month", monthLabels[i]);
                    obj.put("spending", monthlyOut[i]);
                    obj.put("income", monthlyIn[i]);
                    monthsJson.put(obj);
                }
                String prompt = "You are a personal finance analyst. Analyze this 6-month trend data and write ONE concise sentence "
                        + "(max 120 characters) that summarizes the key takeaway. Be specific with numbers when relevant.\n\n"
                        + "Monthly data (last 6 months):\n" + monthsJson.toString(2) + "\n\n"
                        + "Rules:\n"
                        + "- If spending trend is declining significantly, praise the user.\n"
                        + "- If spending is rising sharply, warn them.\n"
                        + "- If stable, just state so with the overall figure.\n"
                        + "- Return ONLY the sentence, no quotes, no labels.";
                String result = GeminiClassifier.generateText(prompt);
                if (result != null) {
                    String trimmed = result.trim();
                    if (!trimmed.isEmpty()) {
                        runOnUiThread(() -> tvTrendsKeyInsight.setText(trimmed));
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void generateLLMInsights(double totalIn, double totalOut, List<Transaction> monthTxs, List<Transaction> allTxs) {
        new Thread(() -> {
            try {
                JSONArray txsJson = new JSONArray();
                for (Transaction t : monthTxs) {
                    JSONObject obj = new JSONObject();
                    obj.put("merchant", t.getMerchant());
                    obj.put("amount", t.getAmount());
                    obj.put("category", t.getCategory());
                    obj.put("type", t.getType() == Transaction.Type.INCOMING ? "incoming" : "outgoing");
                    obj.put("date", t.getDateDisplay());
                    txsJson.put(obj);
                }
                String prompt = "You are a personal finance insights engine. Analyze this user's transaction data for the current month.\n\n"
                        + "Total Income: $" + String.format("%.2f", totalIn) + "\n"
                        + "Total Spending: $" + String.format("%.2f", totalOut) + "\n"
                        + "Transactions JSON:\n" + txsJson.toString(2) + "\n\n"
                        + "Generate exactly 3 concise bullet-point insights (no numbering, no markdown, each on its own line):\n"
                        + "1. A warning if spending exceeds income or a category dominates (>40% of spending).\n"
                        + "2. A tip about saving or budgeting based on the data.\n"
                        + "3. A forecast based on recurring patterns.\n"
                        + "If there is very little data (<3 transactions), just say 'Not enough data for AI insights.'\n"
                        + "Be specific — use actual dollar amounts and percentages from the data.";
                String result = GeminiClassifier.generateText(prompt);
                if (result != null) {
                    String[] lines = result.split("\n");
                    List<String> insights = new ArrayList<>();
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) insights.add(trimmed);
                    }
                    if (!insights.isEmpty()) {
                        runOnUiThread(() -> appendInsights(insights));
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void appendInsights(List<String> items) {
        for (String item : items) {
            TextView tv = new TextView(this);
            tv.setText("• " + item);
            tv.setTextSize(13);
            tv.setTextColor(0xFF555555);
            tv.setLineSpacing(8, 1);
            tv.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            tv.setLayoutParams(lp);
            containerInsightsForYou.addView(tv);
        }
    }

    // ── SCREEN 2: SPENDING ──

    private void populateSpending() {
        if (allTransactions == null) return;

        List<Transaction> monthTxs = getTransactionsForMonth(selectedInsightYear, selectedInsightMonth);

        double totalOut = 0;
        int noSpendDays = 0;
        Map<Integer, Double> dayTotals = new HashMap<>();
        Map<String, Double> catTotals = new HashMap<>();
        Transaction largestTx = null;
        Calendar cal = Calendar.getInstance();

        for (Transaction t : monthTxs) {
            if (t.getType() == Transaction.Type.OUTGOING) {
                totalOut += t.getAmount();
                double cur = catTotals.getOrDefault(t.getCategory(), 0.0);
                catTotals.put(t.getCategory(), cur + t.getAmount());
                cal.setTimeInMillis(t.getDateMillis());
                int day = cal.get(Calendar.DAY_OF_MONTH);
                double dcur = dayTotals.getOrDefault(day, 0.0);
                dayTotals.put(day, dcur + t.getAmount());
                if (largestTx == null || t.getAmount() > largestTx.getAmount()) {
                    largestTx = t;
                }
            }
        }

        int daysInMonth = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH);
        int trackedDays = dayTotals.size();
        noSpendDays = daysInMonth - trackedDays;

        double avgDaily = trackedDays > 0 ? totalOut / trackedDays : 0;
        double highestDay = 0;
        for (double v : dayTotals.values()) {
            if (v > highestDay) highestDay = v;
        }

        int prevMonth = selectedInsightMonth == 0 ? 11 : selectedInsightMonth - 1;
        int prevYear = selectedInsightMonth == 0 ? selectedInsightYear - 1 : selectedInsightYear;
        List<Transaction> prevTxs = getTransactionsForMonth(prevYear, prevMonth);
        double prevOut = 0;
        for (Transaction t : prevTxs) {
            if (t.getType() == Transaction.Type.OUTGOING) prevOut += t.getAmount();
        }

        boolean hasPrevOut = prevOut != 0;
        double pctChange = hasPrevOut ? ((totalOut - prevOut) / Math.abs(prevOut)) * 100 : 0;

        tvSpendingTotal.setText(formatCurrency(totalOut));
        if (hasPrevOut) {
            boolean isDown = pctChange <= 0;
            tvSpendingTrend.setText((isDown ? "↓" : "↑") + " " + String.format("%.0f", Math.abs(pctChange)) + "% vs last month");
            tvSpendingTrend.setTextColor(isDown ? 0xFF2B9348 : 0xFFE53935);
        } else {
            tvSpendingTrend.setText("New Wallet");
            tvSpendingTrend.setTextColor(0xFFD4A373);
        }

        tvStatAvgDaily.setText(formatCurrency(avgDaily));
        tvStatHighestDay.setText(formatCurrency(highestDay));

        if (largestTx != null) {
            cardLargestTransaction.setVisibility(View.VISIBLE);
            tvLargestMerchant.setText(largestTx.getMerchant());
            tvLargestAmount.setText(formatCurrency(largestTx.getAmount()));
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
            tvLargestDate.setText(sdf.format(new Date(largestTx.getDateMillis())));
        } else {
            cardLargestTransaction.setVisibility(View.GONE);
        }

        containerSpendingCategories.removeAllViews();
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
            tvName.setSingleLine(true);
            tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(
                    dp(100), LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(tvName);

            LinearLayout barOuter = new LinearLayout(this);
            int barHeight = dp(14);
            LinearLayout.LayoutParams outerLp = new LinearLayout.LayoutParams(
                    0, barHeight, 0.35f);
            barOuter.setLayoutParams(outerLp);
            android.graphics.drawable.GradientDrawable bgShape = new android.graphics.drawable.GradientDrawable();
            bgShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bgShape.setCornerRadius(barHeight / 2f);
            bgShape.setColor(0xFFF0E8D5);
            barOuter.setBackground(bgShape);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                barOuter.setClipToOutline(true);
            }

            View barFill = new View(this);
            float fillW = (float)(pct / 100);
            barFill.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, fillW));
            barFill.setBackgroundColor(color);
            barOuter.addView(barFill);
            row.addView(barOuter);

            TextView tvAmt = new TextView(this);
            tvAmt.setText(formatCurrency(entry.getValue()));
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

        ((TextView) findViewById(R.id.tv_spending_dropdown)).setText(formatMonthYear(selectedInsightYear, selectedInsightMonth) + " ▼");
    }

    // ── SCREEN 3: INCOME ──

    private void populateIncome() {
        if (allTransactions == null) return;

        List<Transaction> monthTxs = getTransactionsForMonth(selectedInsightYear, selectedInsightMonth);

        double totalIn = 0;
        Map<String, Double> incomeBySource = new LinkedHashMap<>();
        for (Transaction t : monthTxs) {
            if (t.getType() == Transaction.Type.INCOMING) {
                totalIn += t.getAmount();
                String source = t.getMerchant() != null && !t.getMerchant().isEmpty() ? t.getMerchant() : "Other";
                incomeBySource.put(source, incomeBySource.getOrDefault(source, 0.0) + t.getAmount());
            }
        }

        int prevMonth = selectedInsightMonth == 0 ? 11 : selectedInsightMonth - 1;
        int prevYear = selectedInsightMonth == 0 ? selectedInsightYear - 1 : selectedInsightYear;
        List<Transaction> prevTxs = getTransactionsForMonth(prevYear, prevMonth);
        double prevIn = 0;
        for (Transaction t : prevTxs) {
            if (t.getType() == Transaction.Type.INCOMING) prevIn += t.getAmount();
        }

        boolean hasPrevIn = prevIn != 0;
        double pctChange = hasPrevIn ? ((totalIn - prevIn) / Math.abs(prevIn)) * 100 : 0;

        tvIncomeTotal.setText(formatCurrency(totalIn));
        if (hasPrevIn) {
            boolean isUp = pctChange >= 0;
            tvIncomeTrend.setText((isUp ? "↑" : "↓") + " " + String.format("%.0f", Math.abs(pctChange)) + "% vs last month");
            tvIncomeTrend.setTextColor(isUp ? 0xFF2B9348 : 0xFFE53935);
        } else {
            tvIncomeTrend.setText("New Wallet");
            tvIncomeTrend.setTextColor(0xFFD4A373);
        }

        if (hasPrevIn) {
            tvIncomeComparisonPct.setVisibility(View.VISIBLE);
            boolean isUp = pctChange >= 0;
            String arrow = isUp ? "↑" : "↓";
            tvIncomeComparisonPct.setText(arrow + " " + String.format("%.1f", Math.abs(pctChange)) + "% vs last month");
            tvIncomeComparisonPct.setTextColor(isUp ? 0xFF2B9348 : 0xFFE53935);
        } else {
            tvIncomeComparisonPct.setVisibility(View.GONE);
        }

        containerIncomeSources.removeAllViews();
        List<Map.Entry<String, Double>> sortedSources = new ArrayList<>(incomeBySource.entrySet());
        sortedSources.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
        for (Map.Entry<String, Double> entry : sortedSources) {
            LinearLayout sourceRow = new LinearLayout(this);
            sourceRow.setOrientation(LinearLayout.HORIZONTAL);
            sourceRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
            sourceRow.setPadding(0, dp(6), 0, dp(6));

            ImageView dot = new ImageView(this);
            int dotSize = dp(10);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
            dotLp.setMargins(0, dp(4), dp(8), 0);
            dot.setLayoutParams(dotLp);
            dot.setImageResource(R.drawable.circle_green);
            sourceRow.addView(dot);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvSrcName = new TextView(this);
            tvSrcName.setText(entry.getKey());
            tvSrcName.setTextSize(14);
            tvSrcName.setTextColor(0xFF000000);
            tvSrcName.setTypeface(null, android.graphics.Typeface.BOLD);
            textCol.addView(tvSrcName);

            double pctOfTotal = totalIn > 0 ? entry.getValue() / totalIn * 100 : 0;
            TextView tvSrcPct = new TextView(this);
            tvSrcPct.setText(String.format("%.0f%% of income", pctOfTotal));
            tvSrcPct.setTextSize(11);
            tvSrcPct.setTextColor(0xFF888888);
            textCol.addView(tvSrcPct);

            sourceRow.addView(textCol);

            TextView tvSrcAmt = new TextView(this);
            tvSrcAmt.setText(formatCurrency(entry.getValue()));
            tvSrcAmt.setTextSize(14);
            tvSrcAmt.setTextColor(0xFF2B9348);
            tvSrcAmt.setTypeface(null, android.graphics.Typeface.BOLD);
            tvSrcAmt.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            sourceRow.addView(tvSrcAmt);

            containerIncomeSources.addView(sourceRow);
        }


        ((TextView) findViewById(R.id.tv_income_dropdown)).setText(formatMonthYear(selectedInsightYear, selectedInsightMonth) + " ▼");
    }

    // ── SCREEN 4: TRENDS ──

    private void populateTrends() {
        if (allTransactions == null) return;

        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);
        int currentMonth = cal.get(Calendar.MONTH);

        double[] monthlyIn = new double[6];
        double[] monthlyOut = new double[6];
        String[] monthLabels = new String[6];
        double grandTotal = 0;

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
            grandTotal += (in - out);
        }

        double recent3 = 0, prior3 = 0;
        for (int i = 3; i < 6; i++) recent3 += monthlyOut[i];
        for (int i = 0; i < 3; i++) prior3 += monthlyOut[i];
        boolean hasPrior3 = prior3 != 0;
        double pctChange = hasPrior3 ? ((recent3 - prior3) / Math.abs(prior3)) * 100 : 0;

        tvTrendsTotal.setText(formatCurrency(grandTotal));
        if (hasPrior3) {
            boolean isDown = pctChange <= 0;
            tvTrendsIndicator.setText((isDown ? "↓" : "↑") + " " + String.format("%.0f", Math.abs(pctChange)) + "% (last 3 vs prev 3 months)");
            tvTrendsIndicator.setTextColor(isDown ? 0xFF2B9348 : 0xFFE53935);
        } else {
            tvTrendsIndicator.setText("New Wallet");
            tvTrendsIndicator.setTextColor(0xFFD4A373);
        }

        // Month comparison: this month vs last month
        double thisMonthOut = monthlyOut[5];
        double lastMonthOut = monthlyOut[4];
        double diff = thisMonthOut - lastMonthOut;
        boolean hasLastMonth = lastMonthOut != 0;
        double compPct = hasLastMonth ? (diff / Math.abs(lastMonthOut)) * 100 : 0;

        SimpleDateFormat sdf = new SimpleDateFormat("MMM yyyy", Locale.US);
        cal.set(Calendar.MONTH, currentMonth);
        tvThisMonthAmount.setText(formatCurrency(thisMonthOut));
        tvThisMonthLabel.setText(sdf.format(cal.getTime()));
        cal.add(Calendar.MONTH, -1);
        tvLastMonthAmount.setText(formatCurrency(lastMonthOut));
        tvLastMonthLabel.setText(sdf.format(cal.getTime()));

        if (hasLastMonth) {
            String compArrow = diff <= 0 ? "↓" : "↑";
            tvComparisonAmount.setText(formatCurrency(Math.abs(diff)));
            tvComparisonPct.setText("(" + String.format("%.1f", Math.abs(compPct)) + "%)");
            tvComparisonIndicator.setText(compArrow + " vs last month");
            tvComparisonIndicator.setTextColor(diff <= 0 ? 0xFF2B9348 : 0xFFE53935);
        } else {
            tvComparisonAmount.setText("—");
            tvComparisonPct.setText("");
            tvComparisonIndicator.setText("No prior data");
            tvComparisonIndicator.setTextColor(0xFFD4A373);
        }

        // Insight cards
        containerTrendsInsights.removeAllViews();
        String[][] trendInsights = buildTrendInsights(monthlyIn, monthlyOut, monthLabels, hasPrior3, pctChange);
        for (String[] insight : trendInsights) {
            CardView card = new CardView(this);
            card.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            card.setCardElevation(dp(2));
            card.setRadius(dp(8));
            card.setCardBackgroundColor(0xFFF5F0E1);
            card.setUseCompatPadding(true);
            LinearLayout.LayoutParams cardLp = (LinearLayout.LayoutParams) card.getLayoutParams();
            cardLp.setMargins(0, 0, 0, dp(8));
            card.setLayoutParams(cardLp);

            LinearLayout cardInner = new LinearLayout(this);
            cardInner.setOrientation(LinearLayout.VERTICAL);
            cardInner.setPadding(dp(12), dp(10), dp(12), dp(10));
            cardInner.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView tvLabel = new TextView(this);
            tvLabel.setText(insight[0]);
            tvLabel.setTextSize(15);
            tvLabel.setTextColor(0xFF000000);
            tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            cardInner.addView(tvLabel);

            TextView tvBody = new TextView(this);
            tvBody.setText(insight[1]);
            tvBody.setTextSize(13);
            tvBody.setTextColor(0xFF555555);
            tvBody.setPadding(0, dp(4), 0, 0);
            cardInner.addView(tvBody);

            card.addView(cardInner);
            containerTrendsInsights.addView(card);
        }

        // Key insight text
        double maxOutMonth = 0;
        int maxIdx = 0;
        int nonZeroMonths = 0;
        for (int i = 0; i < 6; i++) {
            if (monthlyOut[i] > maxOutMonth) { maxOutMonth = monthlyOut[i]; maxIdx = i; }
            if (monthlyOut[i] > 0) nonZeroMonths++;
        }
        String summary;
        if (nonZeroMonths == 0) {
            summary = "No spending data available for the last 6 months.";
        } else if (nonZeroMonths == 1) {
            summary = "Your only spending this period was in " + monthLabels[maxIdx]
                    + " (" + formatCurrency(maxOutMonth) + "). Add more data to see trends.";
        } else if (pctChange < -10 && hasPrior3) {
            summary = "Your spending has decreased significantly (" + String.format("%.0f", Math.abs(pctChange))
                    + "%) compared to the prior period. Great job controlling expenses!";
        } else if (pctChange > 10 && hasPrior3) {
            summary = "Your spending has increased " + String.format("%.0f", pctChange)
                    + "% vs the prior period. Check " + monthLabels[maxIdx] + " for the highest spending ("
                    + formatCurrency(maxOutMonth) + ").";
        } else if (hasPrior3) {
            summary = "Your spending has been relatively stable over 6 months ("
                    + (pctChange >= 0 ? "+" : "") + String.format("%.0f", pctChange)
                    + "% vs prior period). Consistent habits!";
        } else {
            summary = "Highest spending was in " + monthLabels[maxIdx]
                    + " (" + formatCurrency(maxOutMonth) + ").";
        }
        tvTrendsKeyInsight.setText(summary);
        generateLLMTrendInsight(monthlyIn, monthlyOut, monthLabels);
    }

    private String[][] buildTrendInsights(double[] monthlyIn, double[] monthlyOut, String[] monthLabels, boolean hasPrior3, double pctChange) {
        List<String[]> insights = new ArrayList<>();

        double maxOutMonth = 0, maxInMonth = 0;
        int maxOutIdx = 0, maxInIdx = 0;
        int spendMonths = 0, incomeMonths = 0;
        for (int i = 0; i < 6; i++) {
            if (monthlyOut[i] > maxOutMonth) { maxOutMonth = monthlyOut[i]; maxOutIdx = i; }
            if (monthlyIn[i] > maxInMonth) { maxInMonth = monthlyIn[i]; maxInIdx = i; }
            if (monthlyOut[i] > 0) spendMonths++;
            if (monthlyIn[i] > 0) incomeMonths++;
        }

        if (spendMonths > 0) {
            insights.add(new String[]{"Highest Spending Month", monthLabels[maxOutIdx] + " had the most spending at "
                    + formatCurrency(maxOutMonth) + "."});
        }
        if (incomeMonths > 0) {
            insights.add(new String[]{"Best Income Month", monthLabels[maxInIdx] + " had the highest income at "
                    + formatCurrency(maxInMonth) + "."});
        }
        if (hasPrior3) {
            double recentIn = 0, priorIn = 0;
            for (int i = 3; i < 6; i++) recentIn += monthlyIn[i];
            for (int i = 0; i < 3; i++) priorIn += monthlyIn[i];
            double inPct = priorIn > 0 ? ((recentIn - priorIn) / Math.abs(priorIn)) * 100 : 0;
            String inTrend = inPct >= 0 ? "increasing" : "decreasing";
            insights.add(new String[]{"Income Trend", "Income has been " + inTrend + " (" + String.format("%+.1f", inPct) + "% over the last 3 months)."});
        }
        if (spendMonths >= 2) {
            insights.add(new String[]{"Spending Consistency", "You had spending in " + spendMonths + " of the last 6 months."});
        }

        return insights.toArray(new String[0][]);
    }

    // ── GEMINI BATCH CLASSIFICATION ──

    private void classifyAllWithGemini() {
        if (allTransactions == null || allTransactions.isEmpty()) {
            Toast.makeText(this, "No transactions to classify", Toast.LENGTH_SHORT).show();
            return;
        }

        if (GeminiClassifier.backendUrl == null || GeminiClassifier.backendUrl.isEmpty()) {
            Toast.makeText(this, "Backend URL not configured", Toast.LENGTH_SHORT).show();
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
                tab.setBackgroundResource(R.drawable.nav_tab_circle_active);
                icon.setColorFilter(navWhite, PorterDuff.Mode.SRC_IN);
            } else {
                tab.setBackgroundResource(R.drawable.nav_tab_circle_inactive);
                icon.setColorFilter(navInactive, PorterDuff.Mode.SRC_IN);
            }
        }

        containerHome.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        containerDocument.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        if (containerAdd != null) {
            containerAdd.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        }
        containerInsights.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        containerSettings.setVisibility(index == 4 ? View.VISIBLE : View.GONE);

        if (index == 3) {
            renderInsightsSubTab(selectedInsightSubTab);
        }
    }

    // ── Periodic scanning ───────────────────────────────────────────────

    private void startPeriodicScan() {
        boolean autoSync = settingsPrefs.getBoolean("auto_sync", true);
        if (autoSync) {
            scheduleNextScan();
        }
    }

    private void stopPeriodicScan() {
        scanHandler.removeCallbacks(scanRunnable);
    }

    private void scheduleNextScan() {
        scanHandler.removeCallbacks(scanRunnable);
        String freq = settingsPrefs.getString("scan_frequency", "Every 1 hour");
        long delayMs;
        switch (freq) {
            case "Every 30 min": delayMs = 30 * 60 * 1000L; break;
            case "Every 1 hour": delayMs = 60 * 60 * 1000L; break;
            case "Every 3 hours": delayMs = 3 * 60 * 60 * 1000L; break;
            case "Every 6 hours": delayMs = 6 * 60 * 60 * 1000L; break;
            case "Daily": delayMs = 24 * 60 * 60 * 1000L; break;
            default: delayMs = 60 * 60 * 1000L;
        }
        scanHandler.postDelayed(scanRunnable, delayMs);
    }

    private void fetchAndShowTransactions() {
        fetchAndShowTransactions(false);
    }

    private void fetchAndShowTransactions(boolean isPullRefresh) {
        if (!isPullRefresh) {
            findViewById(R.id.tv_loading).setVisibility(View.VISIBLE);
        }

        GmailFetcher.fetchTransactions(this, new GmailFetcher.Callback() {
            @Override
            public void onResult(List<Transaction> transactions) {
                runOnUiThread(() -> {
                    findViewById(R.id.tv_loading).setVisibility(View.GONE);
                    List<Transaction> manual = manualStore.getAll();

                    // ── Merge with previously known transactions ──
                    // GmailFetcher now does INCREMENTAL sync: on a normal refresh
                    // it only returns messages newer than the last sync (often an
                    // EMPTY list if nothing new arrived). Previously GmailFetcher
                    // always returned the full history, so merging with just
                    // `manual` was safe. That's no longer true — we must also
                    // bring in whatever was already known (in-memory if available,
                    // else the on-disk cache) or a normal "nothing new" refresh
                    // would wipe out every previously seen transaction.
                    List<Transaction> previouslyKnown = (allTransactions != null)
                            ? allTransactions
                            : loadTransactionsFromCache();
                    if (previouslyKnown == null) previouslyKnown = new ArrayList<>();

                    // Strip from previouslyKnown any manual transactions that
                    // have been deleted (i.e. no longer present in fresh `manual` list)
                    Set<String> manualIds = new HashSet<>();
                    for (Transaction m : manual) {
                        if (m.getMessageId() != null) manualIds.add(m.getMessageId());
                    }
                    List<Transaction> known = new ArrayList<>();
                    for (Transaction pk : previouslyKnown) {
                        if (pk.getMessageId() != null && pk.getMessageId().startsWith("manual_")) {
                            if (manualIds.contains(pk.getMessageId())) {
                                known.add(pk);
                            }
                        } else {
                            known.add(pk);
                        }
                    }

                    List<Transaction> merged = new ArrayList<>();
                    merged.addAll(manual);
                    merged.addAll(known);
                    merged.addAll(transactions);
                    merged.sort((a, b) -> Long.compare(b.getDateMillis(), a.getDateMillis()));

                    // Deduplicate: same merchant + same day + same amount = duplicate.
                    // Normalize date to day-boundary so emails about the same transaction
                    // arriving at slightly different times are caught. Keep the more
                    // informative entry — prefer Gmail (has messageId) over manual.
                    // Also dedupe directly on messageId when present — this is the
                    // strongest signal (exact same email) and matters more now that
                    // the same message can legitimately appear in both
                    // `previouslyKnown` and a fresh `transactions` result (e.g. the
                    // sync watermark's overlap window re-fetching the last day).
                    Set<String> seenIds = new HashSet<>();
                    Set<String> seen = new HashSet<>();
                    List<Transaction> deduped = new ArrayList<>();
                    for (Transaction tx : merged) {
                        String msgId = tx.getMessageId();
                        if (msgId != null && !msgId.isEmpty() && !msgId.startsWith("manual_")) {
                            if (seenIds.contains(msgId)) continue;
                            seenIds.add(msgId);
                        }

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

                    // Cross-vendor dedup: amount + day + type — catches different
                    // senders notifying about the same underlying transaction
                    // (e.g. bank alert + Interac notification).
                    List<Transaction> crossDeduped = new ArrayList<>();
                    Set<String> seenCross = new HashSet<>();
                    for (Transaction t : deduped) {
                        long dayBucket = t.getDateMillis() / (24L * 60 * 60 * 1000);
                        String amtKey  = String.format(Locale.US, "%.2f", t.getAmount());
                        String typeKey = t.getType().name();
                        String key     = amtKey + "|" + dayBucket + "|" + typeKey;

                        if (!seenCross.contains(key)) {
                            seenCross.add(key);
                            crossDeduped.add(t);
                        } else {
                            for (int di = 0; di < crossDeduped.size(); di++) {
                                Transaction existing = crossDeduped.get(di);
                                long existingDay  = existing.getDateMillis() / (24L * 60 * 60 * 1000);
                                String existingAmt = String.format(Locale.US, "%.2f", existing.getAmount());
                                String existingTypeKey = existing.getType().name();
                                String existingKey = existingAmt + "|" + existingDay + "|" + existingTypeKey;

                                if (existingKey.equals(key)) {
                                    int existingLen = existing.getSubject() != null ? existing.getSubject().length() : 0;
                                    int newLen      = t.getSubject()        != null ? t.getSubject().length()        : 0;
                                    if (newLen > existingLen) {
                                        crossDeduped.set(di, t);
                                    }
                                    break;
                                }
                            }
                        }
                    }

                    List<Transaction> filtered = new ArrayList<>();
                    for (Transaction tx : crossDeduped) {
                        if (tx.getMessageId() != null && !tx.getMessageId().startsWith("manual_")
                                && excludedStore.isExcluded(tx.getMessageId())) {
                            continue;
                        }
                        if (!isInPausedRange(tx.getDateMillis())) {
                            filtered.add(tx);
                        }
                    }
                    allTransactions = filtered;
                    saveTransactionsToCache(filtered);
                    if (filtered.isEmpty()) {
                        addEmptyState();
                    } else {
                        populateDashboard(filtered);
                    }
                    swipeRefresh.setRefreshing(false);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    findViewById(R.id.tv_loading).setVisibility(View.GONE);
                    if (message.contains("Authorization required")) {
                        needsAuthRetry = true;
                    }
                    if (allTransactions != null && !allTransactions.isEmpty()) {
                        showOfflineBanner(message);
                    } else {
                        addErrorState(message);
                    }
                    swipeRefresh.setRefreshing(false);
                });
            }
        });
    }

    private void loadCachedTransactions() {
        List<Transaction> cached = loadTransactionsFromCache();
        if (cached != null && !cached.isEmpty()) {
            allTransactions = cached;
            populateDashboard(cached);
        }
    }

    private int lightenColor(int color, float factor) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.min(255, (int) (r + (255 - r) * factor));
        g = Math.min(255, (int) (g + (255 - g) * factor));
        b = Math.min(255, (int) (b + (255 - b) * factor));
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private int getMatteTextColor(int bgColor) {
        int r = (bgColor >> 16) & 0xFF;
        int g = (bgColor >> 8) & 0xFF;
        int b = bgColor & 0xFF;
        double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
        return luminance > 160 ? 0xFF4A3F35 : 0xFFF5F0E8;
    }

    private int getHomeBarColor(String cat) {
        return getCategoryColor(cat);
    }

    private int[] getHomeChipColors(String cat) {
        int bg = getCategoryColor(cat);
        int text = getMatteTextColor(bg);
        int lightBg = lightenColor(bg, 0.55f);
        return new int[]{lightBg, text};
    }

    private void populateDashboard(List<Transaction> transactions) {
        // ── Current month filtering ──
        long monthStart = getMonthStartMillis();
        Calendar cal = Calendar.getInstance();
        int currentMonth = cal.get(Calendar.MONTH);
        String[] monthNames = {"January","February","March","April","May","June","July",
                "August","September","October","November","December"};

        List<Transaction> monthTxs = new ArrayList<>();
        for (Transaction t : transactions) {
            if (t.getDateMillis() >= monthStart) {
                monthTxs.add(t);
            }
        }

        double totalOutgoing = 0, totalIncoming = 0;
        for (Transaction t : monthTxs) {
            if (t.getType() == Transaction.Type.OUTGOING) totalOutgoing += t.getAmount();
            else totalIncoming += t.getAmount();
        }
        double netCashFlow = totalIncoming - totalOutgoing;

        ((TextView) findViewById(R.id.tv_month_label)).setText(monthNames[currentMonth]);
        tvTotalAmount.setText(TransactionParser.formatAmount(netCashFlow));

        // ── Trend vs last month ──
        long lastMonthStart = getMonthStartMillis();
        Calendar prevCal = Calendar.getInstance();
        prevCal.setTimeInMillis(lastMonthStart);
        prevCal.add(Calendar.MONTH, -1);
        long prevMonthStart = prevCal.getTimeInMillis();
        double prevNet = 0;
        for (Transaction t : transactions) {
            if (t.getDateMillis() >= prevMonthStart && t.getDateMillis() < monthStart) {
                if (t.getType() == Transaction.Type.INCOMING) prevNet += t.getAmount();
                else prevNet -= t.getAmount();
            }
        }
        ImageView ivArrow = findViewById(R.id.iv_trend_arrow);
        TextView tvTrendText = findViewById(R.id.tv_trend);
        if (prevNet != 0) {
            double pctChange = ((netCashFlow - prevNet) / Math.abs(prevNet)) * 100;
            ivArrow.setVisibility(View.VISIBLE);
            tvTrendText.setVisibility(View.VISIBLE);
            if (pctChange >= 0) {
                ivArrow.setImageResource(R.drawable.ic_arrow_up);
                tvTrendText.setText(String.format("%.0f%% vs last month", Math.abs(pctChange)));
                tvTrendText.setTextColor(0xFF1E7A4C);
            } else {
                ivArrow.setImageResource(R.drawable.ic_arrow_down);
                tvTrendText.setText(String.format("%.0f%% vs last month", Math.abs(pctChange)));
                tvTrendText.setTextColor(0xFFE53935);
            }
        } else {
            ivArrow.setVisibility(View.GONE);
            tvTrendText.setVisibility(View.GONE);
        }

        // ── Per-category spending (expenses only) ──
        Map<String, Double> categoryExpenses = new HashMap<>();
        double totalExpenses = 0;
        for (Transaction t : monthTxs) {
            if (t.getType() == Transaction.Type.OUTGOING) {
                String cat = t.getCategory();
                if (cat == null) cat = "Other";
                categoryExpenses.put(cat, categoryExpenses.getOrDefault(cat, 0.0) + t.getAmount());
                totalExpenses += t.getAmount();
            }
        }

        // Sort categories by amount descending, take top 5 (only real data, no $0 padding)
        List<Map.Entry<String, Double>> sortedCats = new ArrayList<>(categoryExpenses.entrySet());
        sortedCats.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<Map.Entry<String, Double>> top5 = sortedCats.subList(0, Math.min(5, sortedCats.size()));

        double maxCat = 0;
        for (Map.Entry<String, Double> e : top5) {
            if (e.getValue() > maxCat) maxCat = e.getValue();
        }

        // ── Income / Expense summary ──
        ((TextView) findViewById(R.id.tv_income_amount)).setText(TransactionParser.formatAmount(totalIncoming));
        ((TextView) findViewById(R.id.tv_expense_amount)).setText(TransactionParser.formatAmount(totalOutgoing));

        // ── Top 5 categories rows ──
        containerCategories.removeAllViews();
        for (int i = 0; i < top5.size(); i++) {
            Map.Entry<String, Double> entry = top5.get(i);
            View row = getLayoutInflater().inflate(R.layout.item_category_row, containerCategories, false);

            ((TextView) row.findViewById(R.id.tv_category_name)).setText(entry.getKey());

            double pct = maxCat > 0 ? (entry.getValue() / maxCat) * 100 : 0;
            int pctInt = (int) Math.round(pct);
            String amtStr = TransactionParser.formatAmount(entry.getValue());

            ((TextView) row.findViewById(R.id.tv_amount)).setText(amtStr);
            ((TextView) row.findViewById(R.id.tv_percent)).setText(pctInt + "%");

            ProgressBar pb = row.findViewById(R.id.progress_bar);
            pb.setProgressDrawable(ContextCompat.getDrawable(this, R.drawable.progress_bar_pill));
            pb.setProgress(pctInt);
            int barColor = getCategoryColor(entry.getKey());
            pb.setProgressTintList(ColorStateList.valueOf(barColor));
            pb.setProgressBackgroundTintList(ColorStateList.valueOf(0xFFF5E6CC));

            containerCategories.addView(row);
        }

        // ── Top 5 recent transactions ──
        renderHomeTransactions(monthTxs);

        // ── Populate full transactions tab ──
        filterAndRender();

        // Re-render insights if needed
        if (selectedTab == 3) {
            renderInsightsSubTab(selectedInsightSubTab);
        }
    }

    private void renderHomeTransactions(List<Transaction> transactions) {
        containerTransactionsHome.removeAllViews();
        transactions.sort((a, b) -> Long.compare(b.getDateMillis(), a.getDateMillis()));
        int count = 0;
        for (Transaction t : transactions) {
            if (count >= 5) break;
            count++;

            View row = getLayoutInflater().inflate(R.layout.item_transaction_row, containerTransactionsHome, false);

            String merchant = t.getMerchant() != null ? t.getMerchant() : "?";
            ((TextView) row.findViewById(R.id.tv_avatar)).setText(merchant.substring(0, 1).toUpperCase());

            ImageView ivArrow = row.findViewById(R.id.iv_transaction_arrow);
            if (t.getType() == Transaction.Type.INCOMING) {
                ivArrow.setImageResource(R.drawable.ic_arrow_up);
                ivArrow.requestLayout();
                ivArrow.setVisibility(View.VISIBLE);
            } else {
                ivArrow.setImageResource(R.drawable.ic_arrow_down);
                ivArrow.requestLayout();
                ivArrow.setVisibility(View.VISIBLE);
            }

            ((TextView) row.findViewById(R.id.tv_merchant)).setText(merchant);

            Date d = new Date(t.getDateMillis());
            String dateStr = new SimpleDateFormat("MMM dd • h:mm a", Locale.US).format(d);
            ((TextView) row.findViewById(R.id.tv_date)).setText(dateStr);

            ((TextView) row.findViewById(R.id.tv_amount)).setText(TransactionParser.formatAmount(t.getAmount()));

            TextView badge = row.findViewById(R.id.tv_category_badge);
            String cat = t.getCategory() != null ? t.getCategory() : "Other";
            badge.setText(cat);
            int[] chipColors = getHomeChipColors(cat);
            badge.setTextColor(chipColors[1]);
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setCornerRadius(dp(14));
            gd.setColor(chipColors[0]);
            badge.setBackground(gd);

            final Transaction tapped = t;
            row.setOnClickListener(v -> showTransactionDetail(tapped));

            containerTransactionsHome.addView(row);

            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
            lp.bottomMargin = dp(16);
            row.setLayoutParams(lp);
        }
    }

    private void renderTransactionList(List<Transaction> transactions) {
        containerTransactions.removeAllViews();

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

            String merchant = t.getMerchant() != null ? t.getMerchant() : "?";
            String cat = t.getCategory() != null ? t.getCategory() : "Other";

            View row = getLayoutInflater().inflate(R.layout.item_transaction_row, containerTransactions, false);

            ((TextView) row.findViewById(R.id.tv_avatar)).setText(merchant.substring(0, 1).toUpperCase());

            ImageView ivArrow = row.findViewById(R.id.iv_transaction_arrow);
            if (t.getType() == Transaction.Type.INCOMING) {
                ivArrow.setImageResource(R.drawable.ic_arrow_up);
                ivArrow.requestLayout();
                ivArrow.setVisibility(View.VISIBLE);
            } else {
                ivArrow.setImageResource(R.drawable.ic_arrow_down);
                ivArrow.requestLayout();
                ivArrow.setVisibility(View.VISIBLE);
            }
            ((TextView) row.findViewById(R.id.tv_merchant)).setText(merchant);
            ((TextView) row.findViewById(R.id.tv_date)).setText(new SimpleDateFormat("MMM dd • h:mm a", Locale.US).format(d));
            ((TextView) row.findViewById(R.id.tv_amount)).setText(TransactionParser.formatAmount(t.getAmount()));

            TextView badge = row.findViewById(R.id.tv_category_badge);
            badge.setText(cat);
            int[] chipColors = getHomeChipColors(cat);
            badge.setTextColor(chipColors[1]);
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setCornerRadius(dp(14));
            gd.setColor(chipColors[0]);
            badge.setBackground(gd);

            final Transaction tapped = t;
            row.setOnClickListener(v -> showTransactionDetail(tapped));

            containerTransactions.addView(row);

            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
            lp.bottomMargin = 16;
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
                String senderEmail = t.getSenderEmail();
                String txSubject = t.getSubject();

                // Save subject rule (sender + subject keywords → merchant + category)
                SubjectRuleStore subjectRuleStore = new SubjectRuleStore(HomeActivity.this);
                if (senderEmail != null && txSubject != null) {
                    subjectRuleStore.setRule(senderEmail, txSubject, newMerchant, newCategory);
                } else {
                    // No sender/subject (manual transactions) — fall back to vendor-wide alias
                    VendorStore vs = new VendorStore(HomeActivity.this);
                    vs.setCategory(key, newCategory);
                    if (!newMerchant.equals(t.getMerchant())) {
                        aliasStore.setAlias(key, newMerchant);
                    }
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

                // Build subject-keywords for matching other transactions
                List<String> ruleKeywords = (senderEmail != null && txSubject != null)
                        ? SubjectRuleStore.extractKeywords(txSubject) : Collections.emptyList();

                // Update matching transactions (merchant + category)
                for (int i = 0; i < allTransactions.size(); i++) {
                    Transaction tx = allTransactions.get(i);
                    String txKey = tx.getRawVendor() != null ? tx.getRawVendor() : tx.getMerchant();
                    boolean matchById = msgId != null && msgId.equals(tx.getMessageId());

                    // Match by subject keywords when available, otherwise fall back to vendor key
                    boolean matchBySubject = false;
                    if (!ruleKeywords.isEmpty() && senderEmail != null) {
                        String txSender = tx.getSenderEmail();
                        String txSubj = tx.getSubject();
                        if (txSender != null && txSender.equals(senderEmail) && txSubj != null) {
                            List<String> txKws = SubjectRuleStore.extractKeywords(txSubj);
                            matchBySubject = txKws.containsAll(ruleKeywords);
                        }
                    }
                    boolean matchByKey = ruleKeywords.isEmpty() && key.equals(txKey);

                    if (matchById) {
                        // Update this specific transaction with ALL edits
                        allTransactions.set(i, new Transaction(
                                newMerchant, newAmount, tx.getDateMillis(),
                                tx.getDateDisplay(), newCategory, tx.getAvatarLetter(),
                                selectedType[0], tx.getSenderEmail(), tx.getSubject(),
                                tx.getMessageId(), tx.getRawVendor()
                        ));
                    } else if (matchBySubject || matchByKey) {
                        // Other transactions matching the same subject rule or vendor key
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
                populateDashboard(allTransactions);
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

    private void setTypeFilter(Transaction.Type type) {
        selectedType = type;
        updateTypeChips();
        filterAndRender();
    }

    private void updateTypeChips() {
        TextView chipAll = findViewById(R.id.chip_filter_all);
        TextView chipIncome = findViewById(R.id.chip_filter_income);
        TextView chipExpense = findViewById(R.id.chip_filter_expense);

        int activeBg = R.drawable.nav_bg_active;
        int inactiveBg = R.drawable.bg_chip_outline;
        int activeTextColor = 0xFFFFFFFF;
        int incomeTextColor = 0xFF2B9348;
        int expenseTextColor = 0xFFE53935;
        int inactiveTextColor = 0xFF888888;

        chipAll.setBackgroundResource(selectedType == null ? activeBg : inactiveBg);
        chipAll.setTextColor(selectedType == null ? activeTextColor : inactiveTextColor);

        chipIncome.setBackgroundResource(selectedType == Transaction.Type.INCOMING ? activeBg : inactiveBg);
        chipIncome.setTextColor(selectedType == Transaction.Type.INCOMING ? activeTextColor : incomeTextColor);

        chipExpense.setBackgroundResource(selectedType == Transaction.Type.OUTGOING ? activeBg : inactiveBg);
        chipExpense.setTextColor(selectedType == Transaction.Type.OUTGOING ? activeTextColor : expenseTextColor);
    }

    private void showSortPicker() {
        String[] options = {"Newest", "Oldest", "Highest Amount", "Lowest Amount"};
        String[] values = {"date_desc", "date_asc", "amount_desc", "amount_asc"};
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(sortMode)) { checked = i; break; }
        }
        new AlertDialog.Builder(this)
                .setTitle("Sort Transactions")
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    sortMode = values[which];
                    updateSortLabel();
                    dialog.dismiss();
                    filterAndRender();
                })
                .show();
    }

    private void updateSortLabel() {
        TextView tvSort = findViewById(R.id.tv_sort_label);
        switch (sortMode) {
            case "amount_desc": tvSort.setText("Highest Amount"); break;
            case "amount_asc": tvSort.setText("Lowest Amount"); break;
            case "date_asc": tvSort.setText("Oldest"); break;
            case "date_desc": default: tvSort.setText("Newest"); break;
        }
    }

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
        selectedType = null;
        sortMode = "date_desc";
        tvDateRangeFilter.setVisibility(View.GONE);
        searchQuery = "";
        etSearch.setText("");
        updateTypeChips();
        updateSortLabel();
        Toast.makeText(this, "Filters cleared", Toast.LENGTH_SHORT).show();
        if (allTransactions != null) {
            populateDashboard(allTransactions);
        }
    }

    private void filterAndRender() {
        if (allTransactions == null) return;

        List<Transaction> filtered = allTransactions;

        // Apply search filter
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

        // Apply category filter
        if (selectedCategory != null) {
            filtered = filtered.stream()
                    .filter(t -> selectedCategory.equals(t.getCategory()))
                    .collect(Collectors.toList());
        }

        // Apply date range filter
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

        // Apply type filter (All / Income / Expense)
        if (selectedType != null) {
            filtered = filtered.stream()
                    .filter(t -> t.getType() == selectedType)
                    .collect(Collectors.toList());
        }

        // Apply sort
        switch (sortMode) {
            case "amount_desc":
                filtered.sort((a, b) -> Double.compare(b.getAmount(), a.getAmount()));
                break;
            case "amount_asc":
                filtered.sort((a, b) -> Double.compare(a.getAmount(), b.getAmount()));
                break;
            case "date_asc":
                filtered.sort((a, b) -> Long.compare(a.getDateMillis(), b.getDateMillis()));
                break;
            case "date_desc":
            default:
                filtered.sort((a, b) -> Long.compare(b.getDateMillis(), a.getDateMillis()));
                break;
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

        // Display filtered total
        double total = 0;
        for (Transaction t : filtered) {
            if (t.getType() == Transaction.Type.INCOMING) total += t.getAmount();
            else total -= t.getAmount();
        }
        String sign = total >= 0 ? "+" : "";
        tvFilteredTotal.setText(sign + TransactionParser.formatAmount(total));
        if (selectedType == Transaction.Type.INCOMING) {
            tvFilteredTotal.setTextColor(0xFF2B9348);
        } else if (selectedType == Transaction.Type.OUTGOING) {
            tvFilteredTotal.setTextColor(0xFFE53935);
        } else {
            tvFilteredTotal.setTextColor(total >= 0 ? 0xFF2B9348 : 0xFFE53935);
        }
        tvFilteredTotal.setVisibility(View.VISIBLE);

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
        empty.setText("No transactions found in your Gmail from the last 60 days.");
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

    private void showOfflineBanner(String error) {
        View banner = getLayoutInflater().inflate(R.layout.item_offline_banner, containerTransactions, false);
        TextView tvBanner = banner.findViewById(R.id.tv_offline_banner);
        tvBanner.setText("No internet connection. Showing cached data.");
        containerTransactions.removeAllViews();
        containerTransactions.addView(banner, 0);
        // Re-render cached transactions below the banner
        filterAndRender();
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

        // If income was added, offer to set up a paycheck reminder
        if (type == Transaction.Type.INCOMING) {
            promptPaycheckReminder(displayMerchant, amount, selectedDateMillis, category, notes);
        }

        // Clear form
        etCategory.setText("");
        etAmount.setText("");
        etPayment.setText("");
        etNotes.setText("");
        etDate.setText("");

        // Refresh data
        fetchAndShowTransactions();
    }

    private void promptPaycheckReminder(String merchant, double amount, long dateMillis,
                                        String category, String notes) {
        new AlertDialog.Builder(this)
                .setTitle("Set Paycheck Reminder?")
                .setMessage("Would you like to be reminded to add this paycheck again after 15 days or 1 month?")
                .setPositiveButton("After 15 days", (dialog, which) -> {
                    PaycheckReminder r = PaycheckReminder.create(
                            merchant, amount, dateMillis, 15, category, notes
                    );
                    paycheckReminderStore.save(r);
                    Toast.makeText(this, "Reminder set for 15 days", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("After 1 month", (dialog, which) -> {
                    PaycheckReminder r = PaycheckReminder.create(
                            merchant, amount, dateMillis, 30, category, notes
                    );
                    paycheckReminderStore.save(r);
                    Toast.makeText(this, "Reminder set for 1 month", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("No, thanks", null)
                .show();
    }

    // ── SETTINGS ──

    private String editingCategoryName;
    private int editingCategoryColor;

    private void initSettings() {
        setupSettingsProfile();
        setupSettingsMainMenu();
        setupSettingsBackButtons();
        setupSettingsEditCategory();
        setupSettingsColorPicker();
        setupFeedbackScreen();
        showSettingsScreen(containerSettingsMain);
    }

    private void clearAppData() {
        settingsPrefs.edit().clear().apply();
        excludedStore.clear();
        vendorStore.clear();
        aliasStore.clear();
        manualStore.clear();
        paycheckReminderStore.clear();
        new com.spotmydime.data.TransactionOverrideStore(this).clear();
        new com.spotmydime.data.AiResultCache(this).clear();
        new com.spotmydime.data.SyncStateStore(this).clear();
        new com.spotmydime.data.SubjectRuleStore(this).clear();
    }

    private void setupSettingsProfile() {
        String userName = getIntent().getStringExtra("user_name");
        String userEmail = getIntent().getStringExtra("user_email");
        if (userName == null || userName.isEmpty()) userName = "User Name";
        if (userEmail == null || userEmail.isEmpty()) userEmail = "user@email.com";
        ((TextView) findViewById(R.id.tv_settings_name)).setText(userName);
        ((TextView) findViewById(R.id.tv_settings_email)).setText(userEmail);

        findViewById(R.id.btn_settings_sign_out).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Sign Out")
                    .setMessage("Are you sure you want to sign out? All data on this device will be cleared.")
                    .setPositiveButton("Sign Out", (dialog, which) -> {
                        stopPeriodicScan();
                        clearAppData();
                        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestEmail()
                                .build();
                        GoogleSignInClient client = GoogleSignIn.getClient(this, gso);
                        client.signOut().addOnCompleteListener(task -> {
                            Intent intent = new Intent(this, OnboardingActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        ImageView ivAvatar = findViewById(R.id.iv_settings_avatar);
        int size = dp(56);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xFF2B9348);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bg);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xFFFFFFFF);
        text.setTextSize(size * 0.4f);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        float y = -(text.descent() + text.ascent()) / 2f;
        String initial = userName.isEmpty() ? "?" : String.valueOf(userName.charAt(0)).toUpperCase();
        canvas.drawText(initial, size / 2f, size / 2f + y, text);
        ivAvatar.setImageBitmap(bmp);
    }

    private void setupSettingsMainMenu() {
        findViewById(R.id.btn_settings_merchant_nicknames).setOnClickListener(v -> {
            loadSettingsNicknames();
            showSettingsScreen(containerSettingsNicknames);
        });
        findViewById(R.id.btn_settings_mail_scanning).setOnClickListener(v -> {
            loadSettingsMailScanning();
            showSettingsScreen(containerSettingsMailScanning);
        });
        findViewById(R.id.btn_settings_subscriptions).setOnClickListener(v -> {
            loadSettingsSubscriptions();
            showSettingsScreen(containerSettingsSubscriptions);
        });
        findViewById(R.id.btn_settings_categories).setOnClickListener(v -> {
            loadSettingsCategories();
            showSettingsScreen(containerSettingsCategories);
        });
        findViewById(R.id.btn_settings_budget_goals).setOnClickListener(v -> {
            loadSettingsBudgetGoals();
            showSettingsScreen(containerSettingsBudgetGoals);
        });
        findViewById(R.id.btn_settings_paycheck_reminders).setOnClickListener(v -> {
            loadPaycheckReminders();
            showSettingsScreen(containerSettingsPaycheckReminders);
        });
        findViewById(R.id.btn_settings_notifications).setOnClickListener(v -> {
            Toast.makeText(this, "Notifications - coming soon", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btn_settings_feedback).setOnClickListener(v -> showSettingsScreen(containerSettingsFeedback));
        findViewById(R.id.btn_settings_how_it_works).setOnClickListener(v -> showSupportDialog("How It Works",
                "SpotMyDime is a personal finance assistant that connects to your Gmail inbox to automatically detect and track your spending.\n\n"
                        + "Here is how it works in three simple steps:\n\n"
                        + "1. Connect Your Gmail\n"
                        + "Grant SpotMyDime read-only access to your Gmail account. We only scan emails that match transaction-related keywords "
                        + "(like \"receipt\", \"invoice\", \"payment confirmation\") — nothing else leaves your device.\n\n"
                        + "2. Automatic Transaction Detection\n"
                        + "Our on-device AI reads the subject and body of matching emails to extract key transaction details: "
                        + "merchant name, amount, date, category, and payment method. All processing happens locally on your device.\n\n"
                        + "3. Insights & Budgeting\n"
                        + "Transactions are organized into categories, visualized with charts on your Insights dashboard, "
                        + "and analyzed for recurring subscriptions. You can add expenses manually, set budget goals, "
                        + "and view your net cash flow at a glance.\n\n"
                        + "Your financial data never leaves your phone. No uploads, no servers, no third parties."));
        findViewById(R.id.btn_settings_privacy_policy).setOnClickListener(v -> showSupportDialog("Privacy Policy",
                "Last updated: June 2026\n\n"
                        + "SpotMyDime takes your privacy seriously. This policy describes how your information is handled.\n\n"
                        + "1. Data Storage\n"
                        + "All transaction data extracted from your emails is stored exclusively on your device in local storage. "
                        + "We do not operate cloud servers, databases, or remote storage of any kind.\n\n"
                        + "2. Gmail Access\n"
                        + "SpotMyDime requests read-only access to your Gmail account via OAuth 2.0. "
                        + "We only scan emails whose subject lines match keywords you have configured. "
                        + "We never read, store, or transmit unrelated emails.\n\n"
                        + "3. No Data Sharing\n"
                        + "SpotMyDime does not collect, share, sell, or transmit any personal or financial information "
                        + "to third parties. There are no analytics SDKs, no ad networks, and no tracking code.\n\n"
                        + "4. AI Processing\n"
                        + "All AI-based transaction extraction runs entirely on-device using a local language model. "
                        + "No email content or transaction data is sent to any external AI service.\n\n"
                        + "5. Your Control\n"
                        + "You can revoke Gmail access at any time from Google's account settings. "
                        + "Deleting the app removes all locally stored data.\n\n"
                        + "If you have questions, contact us at spotmydime.app@gmail.com."));
        findViewById(R.id.btn_settings_about).setOnClickListener(v -> showSupportDialog("About SpotMyDime",
                "Version 1.0.0\n\n"
                        + "SpotMyDime is a smart, privacy-first expense tracker that helps you take control of your finances. "
                        + "Built with a focus on local processing and data privacy, it uses on-device AI to automatically "
                        + "detect and categorize transactions from your Gmail inbox.\n\n"
                        + "Key Features:\n"
                        + "• Automatic transaction detection from email receipts\n"
                        + "• Smart AI categorization into spending categories\n"
                        + "• Interactive spending insights and trends\n"
                        + "• Recurring subscription detection\n"
                        + "• Custom budget goals and tracking\n"
                        + "• Merchant nickname management\n"
                        + "• Manual expense and income entry\n\n"
                        + "Developed with care for your financial well-being.\n"
                        + "Contact: spotmydime.app@gmail.com"));
    }

    private void setupSettingsBackButtons() {
        findViewById(R.id.btn_settings_back).setOnClickListener(v -> setSelectedTab(0));
        findViewById(R.id.btn_subscriptions_back).setOnClickListener(v -> showSettingsScreen(containerSettingsMain));
        findViewById(R.id.btn_budget_back).setOnClickListener(v -> showSettingsScreen(containerSettingsMain));
        findViewById(R.id.btn_nicknames_back).setOnClickListener(v -> showSettingsScreen(containerSettingsMain));
        findViewById(R.id.btn_categories_back).setOnClickListener(v -> showSettingsScreen(containerSettingsMain));
        findViewById(R.id.btn_edit_category_back).setOnClickListener(v -> showSettingsScreen(containerSettingsCategories));
        findViewById(R.id.btn_auto_tracking_back).setOnClickListener(v -> showSettingsScreen(containerSettingsMain));
        findViewById(R.id.btn_mail_scanning_back).setOnClickListener(v -> showSettingsScreen(containerSettingsMain));
        findViewById(R.id.btn_feedback_back).setOnClickListener(v -> showSettingsScreen(containerSettingsMain));
        findViewById(R.id.btn_paycheck_reminders_back).setOnClickListener(v -> showSettingsScreen(containerSettingsMain));
    }

    private void showSettingsScreen(LinearLayout target) {
        containerSettingsMain.setVisibility(target == containerSettingsMain ? View.VISIBLE : View.GONE);
        containerSettingsSubscriptions.setVisibility(target == containerSettingsSubscriptions ? View.VISIBLE : View.GONE);
        containerSettingsBudgetGoals.setVisibility(target == containerSettingsBudgetGoals ? View.VISIBLE : View.GONE);
        containerSettingsNicknames.setVisibility(target == containerSettingsNicknames ? View.VISIBLE : View.GONE);
        containerSettingsCategories.setVisibility(target == containerSettingsCategories ? View.VISIBLE : View.GONE);
        containerSettingsEditCategory.setVisibility(target == containerSettingsEditCategory ? View.VISIBLE : View.GONE);
        containerSettingsAutoTracking.setVisibility(target == containerSettingsAutoTracking ? View.VISIBLE : View.GONE);
        containerSettingsMailScanning.setVisibility(target == containerSettingsMailScanning ? View.VISIBLE : View.GONE);
        containerSettingsFeedback.setVisibility(target == containerSettingsFeedback ? View.VISIBLE : View.GONE);
        containerSettingsPaycheckReminders.setVisibility(target == containerSettingsPaycheckReminders ? View.VISIBLE : View.GONE);
    }

    private void showSupportDialog(String title, String message) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xFFFCF8F2);
        layout.setPadding(dp(40), dp(28), dp(40), dp(28));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(0xFF000000);
        tvTitle.setTextSize(22);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(android.view.Gravity.CENTER);
        layout.addView(tvTitle);

        TextView tvMsg = new TextView(this);
        tvMsg.setText(message);
        tvMsg.setTextColor(0xFF333333);
        tvMsg.setTextSize(14);
        tvMsg.setLineSpacing(dp(4), 1);
        tvMsg.setPadding(0, dp(20), 0, dp(24));
        layout.addView(tvMsg);

        TextView btnGotIt = new TextView(this);
        btnGotIt.setText("Got it");
        btnGotIt.setTextColor(0xFFF9A84D);
        btnGotIt.setTextSize(16);
        btnGotIt.setTypeface(null, android.graphics.Typeface.BOLD);
        btnGotIt.setGravity(android.view.Gravity.CENTER);
        btnGotIt.setBackgroundColor(0xFFFFFFFF);
        btnGotIt.setPadding(0, dp(14), 0, dp(14));
        btnGotIt.setClickable(true);
        btnGotIt.setFocusable(true);
        btnGotIt.setForeground(ContextCompat.getDrawable(this, R.drawable.input_outline));
        layout.addView(btnGotIt);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setView(layout).create();
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFFFCF8F2));
        btnGotIt.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void setupFeedbackScreen() {
        final String[] features = {
                "General Feedback",
                "Home Dashboard",
                "Transactions",
                "Add Expense/Income",
                "Insights & Charts",
                "Settings & Categories",
                "Email Scanning",
                "Subscriptions",
                "Budget Goals",
                "Merchant Nicknames",
                "Other"
        };
        final int[] selectedFeatureIndex = {0};
        TextView tvFeature = findViewById(R.id.tv_feedback_feature);
        tvFeature.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Select Feature")
                    .setSingleChoiceItems(features, selectedFeatureIndex[0], (dialog, which) -> {
                        selectedFeatureIndex[0] = which;
                        tvFeature.setText(features[which]);
                        tvFeature.setTextColor(0xFF111111);
                        dialog.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        findViewById(R.id.btn_feedback_send).setOnClickListener(v -> {
            String subject = ((EditText) findViewById(R.id.et_feedback_subject)).getText().toString().trim();
            String message = ((EditText) findViewById(R.id.et_feedback_message)).getText().toString().trim();
            String feature = features[selectedFeatureIndex[0]];
            if (subject.isEmpty()) {
                Toast.makeText(this, "Please enter a subject", Toast.LENGTH_SHORT).show();
                return;
            }
            if (message.isEmpty()) {
                Toast.makeText(this, "Please write a message", Toast.LENGTH_SHORT).show();
                return;
            }
            String fullSubject = "[SpotMyDime Feedback - " + feature + "] " + subject;
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:"));
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"spotmydime.app@gmail.com"});
            intent.putExtra(Intent.EXTRA_SUBJECT, fullSubject);
            intent.putExtra(Intent.EXTRA_TEXT, message + "\n\n---\nSent via SpotMyDime v1.0.0");
            try {
                startActivity(Intent.createChooser(intent, "Send feedback via"));
            } catch (Exception e) {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadSettingsSubscriptions() {
        LinearLayout list = findViewById(R.id.container_subscriptions_list);
        list.removeAllViews();

        List<View> rows = new ArrayList<>();
        List<Map<String, String>> detected = detectRecurringSubscriptions();
        for (Map<String, String> sub : detected) {
            rows.add(createSubscriptionRow(sub));
        }

        String manualJson = settingsPrefs.getString("manual_subscriptions", "[]");
        try {
            JSONArray arr = new JSONArray(manualJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Map<String, String> m = new HashMap<>();
                m.put("name", o.getString("name"));
                m.put("amount", o.getString("amount"));
                m.put("frequency", o.getString("frequency"));
                m.put("nextDate", o.getString("nextDate"));
                rows.add(createSubscriptionRow(m));
            }
        } catch (Exception ignored) {}

        if (rows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No subscriptions detected yet.\nAs recurring transactions are found,\nthey will appear here.");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            list.addView(empty);
        } else {
            CardView container = createCardContainer();
            LinearLayout inner = createCardInner();
            for (int i = 0; i < rows.size(); i++) {
                inner.addView(rows.get(i));
                if (i < rows.size() - 1) {
                    addDivider(inner);
                }
            }
            container.addView(inner);
            list.addView(container);
        }

        findViewById(R.id.btn_add_subscription).setOnClickListener(v -> showAddSubscriptionDialog());
    }

    private List<Map<String, String>> detectRecurringSubscriptions() {
        Map<String, List<Double>> merchantAmounts = new HashMap<>();
        Map<String, List<Long>> merchantDates = new HashMap<>();

        if (allTransactions != null) {
            for (Transaction t : allTransactions) {
                if (t.getType() == Transaction.Type.INCOMING) continue;
                String name = t.getMerchant();
                if (name == null || name.isEmpty()) continue;
                String key = name.toLowerCase().trim();
                merchantAmounts.computeIfAbsent(key, k -> new ArrayList<>()).add(t.getAmount());
                merchantDates.computeIfAbsent(key, k -> new ArrayList<>()).add(t.getDateMillis());
            }
        }

        Set<String> excluded = loadExcludedSubscriptions();
        List<Map<String, String>> result = new ArrayList<>();
        for (String key : merchantAmounts.keySet()) {
            if (excluded.contains(key)) continue;

            List<Double> amounts = merchantAmounts.get(key);
            List<Long> dates = merchantDates.get(key);
            if (amounts.size() < 2) continue;

            double avgAmount = 0;
            for (double a : amounts) avgAmount += a;
            avgAmount /= amounts.size();

            double maxDev = 0;
            for (double a : amounts) maxDev = Math.max(maxDev, Math.abs(a - avgAmount));
            if (maxDev > avgAmount * 0.5) continue;

            Collections.sort(dates);

            // Check same day-of-month appears in 2+ different months
            Set<Integer> dayOfMonthSet = new HashSet<>();
            Set<String> monthYearSet = new HashSet<>();
            for (long d : dates) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(d);
                int day = cal.get(Calendar.DAY_OF_MONTH);
                // Allow 1 day variance (e.g. weekend shifting)
                dayOfMonthSet.add(day);
                dayOfMonthSet.add(day - 1);
                dayOfMonthSet.add(day + 1);
                String my = cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.MONTH);
                monthYearSet.add(my);
            }

            if (monthYearSet.size() < 2) continue;

            // Only count as recurring if same approximate day across months
            boolean sameDayPattern = false;
            int[] checkDays = {0, 0, 0}; // count for day, day-1, day+1
            for (long d : dates) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(d);
                int day = cal.get(Calendar.DAY_OF_MONTH);
                for (int di = -1; di <= 1; di++) {
                    if (dayOfMonthSet.contains(day + di)) {
                        checkDays[di + 1]++;
                    }
                }
            }
            int maxCount = 0;
            for (int c : checkDays) maxCount = Math.max(maxCount, c);
            if (maxCount < 2) continue;
            sameDayPattern = true;

            if (!sameDayPattern) continue;

            long minGap = Long.MAX_VALUE;
            long totalGap = 0;
            int gapCount = 0;
            for (int i = 1; i < dates.size(); i++) {
                long gap = dates.get(i) - dates.get(i - 1);
                if (gap < minGap) minGap = gap;
                totalGap += gap;
                gapCount++;
            }
            long avgGap = gapCount > 0 ? totalGap / gapCount : 0;
            if (avgGap < 20 * 86400000L) continue;

            String freq;
            if (avgGap > 300 * 86400000L) freq = "Yearly";
            else if (avgGap > 80 * 86400000L) freq = "Quarterly";
            else if (avgGap > 45 * 86400000L) freq = "Bi-monthly";
            else if (avgGap > 25 * 86400000L) freq = "Monthly";
            else if (avgGap > 12 * 86400000L) freq = "Bi-weekly";
            else freq = "Weekly";

            long lastDate = dates.get(dates.size() - 1);
            long nextDate = lastDate + avgGap;
            String nextDisplay = new SimpleDateFormat("MM-dd, yyyy", Locale.US).format(new Date(nextDate));

            Map<String, String> sub = new HashMap<>();
            sub.put("name", key.substring(0, 1).toUpperCase() + key.substring(1));
            sub.put("amount", String.format("%.2f", avgAmount));
            sub.put("frequency", freq);
            sub.put("nextDate", nextDisplay);
            sub.put("key", key);
            result.add(sub);
        }

        // ── Also include single-occurrence transactions categorized as "Subscriptions" ──
        Set<String> alreadyAdded = new HashSet<>();
        for (Map<String, String> sub : result) alreadyAdded.add(sub.get("key"));

        if (allTransactions != null) {
            for (Transaction t : allTransactions) {
                if (t.getType() == Transaction.Type.INCOMING) continue;
                String name = t.getMerchant();
                if (name == null || name.isEmpty()) continue;
                String key = name.toLowerCase().trim();
                if (excluded.contains(key)) continue;
                if (alreadyAdded.contains(key)) continue;

                String cat = t.getCategory();
                if (cat == null || !cat.equalsIgnoreCase("Subscriptions")) continue;

                // Found a single-occurrence subscription-categorized transaction
                Map<String, String> sub = new HashMap<>();
                sub.put("name", key.substring(0, 1).toUpperCase() + key.substring(1));
                sub.put("amount", String.format("%.2f", t.getAmount()));
                sub.put("frequency", "Monthly");
                sub.put("nextDate", t.getDateDisplay());
                sub.put("key", key);
                result.add(sub);
                alreadyAdded.add(key);
            }
        }

        result.sort((a, b) -> {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("MM-dd, yyyy", Locale.US);
                Date da = sdf.parse(a.get("nextDate"));
                Date db = sdf.parse(b.get("nextDate"));
                return da.compareTo(db);
            } catch (Exception e) { return 0; }
        });

        return result;
    }

    private LinearLayout createSubscriptionRow(Map<String, String> sub) {
        LinearLayout row = createCardRow();
        ImageView icon = new ImageView(this);
        int iconSize = dp(36);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        Bitmap bmp = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF8E24AA);
        c.drawCircle(iconSize / 2f, iconSize / 2f, iconSize / 2f, p);
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(0xFFFFFFFF);
        tp.setTextSize(iconSize * 0.4f);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setFakeBoldText(true);
        float y = -(tp.descent() + tp.ascent()) / 2f;
        String initial = sub.get("name").substring(0, 1).toUpperCase();
        c.drawText(initial, iconSize / 2f, iconSize / 2f + y, tp);
        icon.setImageBitmap(bmp);
        row.addView(icon);
        LinearLayout textCol = new LinearLayout(this);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(10), 0, 0, 0);
        TextView tvName = new TextView(this);
        tvName.setText(sub.get("name"));
        tvName.setTextSize(15);
        tvName.setTextColor(0xFF000000);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(tvName);
        TextView tvDetail = new TextView(this);
        tvDetail.setText("$" + sub.get("amount") + "/" + sub.get("frequency").toLowerCase() + " · Next: " + sub.get("nextDate"));
        tvDetail.setTextSize(12);
        tvDetail.setTextColor(0xFF888888);
        textCol.addView(tvDetail);
        row.addView(textCol);
        TextView btnDelete = new TextView(this);
        btnDelete.setText("Delete");
        btnDelete.setTextSize(13);
        btnDelete.setTextColor(0xFFE53935);
        btnDelete.setTypeface(null, android.graphics.Typeface.BOLD);
        btnDelete.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnDelete.setClickable(true);
        btnDelete.setFocusable(true);
        final String subKey = sub.containsKey("key") ? sub.get("key") : null;
        final String subName = sub.get("name");
        final String subAmount = sub.get("amount");
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Subscription")
                    .setMessage("Remove \"" + subName + "\"? It will not be detected again.")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        if (subKey != null) {
                            saveExcludedSubscription(subKey);
                        } else {
                            removeManualSubscription(subName, subAmount);
                        }
                        loadSettingsSubscriptions();
                        Toast.makeText(this, "Subscription removed", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        row.addView(btnDelete);
        return row;
    }

    private void showAddSubscriptionDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(16));
        EditText etName = new EditText(this);
        etName.setHint("Service name (e.g. Netflix)");
        etName.setTextSize(14);
        etName.setBackgroundResource(R.drawable.input_outline);
        etName.setPadding(dp(16), dp(12), dp(16), dp(12));
        layout.addView(etName);
        EditText etAmount = new EditText(this);
        etAmount.setHint("Amount (e.g. 15.99)");
        etAmount.setTextSize(14);
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etAmount.setBackgroundResource(R.drawable.input_outline);
        etAmount.setPadding(dp(16), dp(12), dp(16), dp(12));
        android.view.ViewGroup.MarginLayoutParams mp = (android.view.ViewGroup.MarginLayoutParams) etAmount.getLayoutParams();
        if (mp != null) mp.topMargin = dp(12);
        else {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(12);
            etAmount.setLayoutParams(lp);
        }
        layout.addView(etAmount);
        String[] frequencies = {"Weekly", "Bi-weekly", "Monthly", "Bi-monthly", "Quarterly", "Yearly"};
        final int[] selectedFreq = {2};
        new AlertDialog.Builder(this)
                .setTitle("Add Subscription")
                .setView(layout)
                .setSingleChoiceItems(frequencies, selectedFreq[0], (dialog, which) -> selectedFreq[0] = which)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String amtStr = etAmount.getText().toString().trim();
                    if (name.isEmpty() || amtStr.isEmpty()) {
                        Toast.makeText(this, "Both fields required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double amt;
                    try { amt = Double.parseDouble(amtStr); } catch (Exception e) {
                        Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String freq = frequencies[selectedFreq[0]];
                    long now = System.currentTimeMillis();
                    long intervalMs;
                    switch (selectedFreq[0]) {
                        case 0: intervalMs = 7L * 86400000L; break;
                        case 1: intervalMs = 14L * 86400000L; break;
                        case 2: intervalMs = 30L * 86400000L; break;
                        case 3: intervalMs = 60L * 86400000L; break;
                        case 4: intervalMs = 91L * 86400000L; break;
                        case 5: intervalMs = 365L * 86400000L; break;
                        default: intervalMs = 30L * 86400000L;
                    }
                    String nextDate = new SimpleDateFormat("MM-dd, yyyy", Locale.US).format(new Date(now + intervalMs));
                    String json = settingsPrefs.getString("manual_subscriptions", "[]");
                    try {
                        JSONArray arr = new JSONArray(json);
                        JSONObject o = new JSONObject();
                        o.put("name", name);
                        o.put("amount", String.format("%.2f", amt));
                        o.put("frequency", freq);
                        o.put("nextDate", nextDate);
                        arr.put(o);
                        settingsPrefs.edit().putString("manual_subscriptions", arr.toString()).apply();
                    } catch (Exception ignored) {}
                    loadSettingsSubscriptions();
                    Toast.makeText(this, "Subscription added", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeManualSubscription(String name, String amount) {
        String json = settingsPrefs.getString("manual_subscriptions", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            JSONArray updated = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String oName = o.optString("name", "");
                String oAmt = o.optString("amount", "");
                if (!oName.equals(name) || !oAmt.equals(amount)) {
                    updated.put(o);
                }
            }
            settingsPrefs.edit().putString("manual_subscriptions", updated.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void loadSettingsBudgetGoals() {
        LinearLayout list = findViewById(R.id.container_budget_list);
        list.removeAllViews();
        Map<String, Double> defs = loadBudgetDefs();
        budgets.clear();
        if (defs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No budget goals set.\nTap + to add one.");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            list.addView(empty);
        } else {
            CardView container = createCardContainer();
            LinearLayout inner = createCardInner();
            int ci = 0;
            int[] catColors = {0xFF29B6F6, 0xFFFFA726, 0xFF8E24AA, 0xFFE53935, 0xFF5C6BC0, 0xFF26A69A, 0xFF4CAF50, 0xFFEF5350};
            int total = defs.size();
            for (Map.Entry<String, Double> entry : defs.entrySet()) {
                budgets.put(entry.getKey(), entry.getValue());
                final String cat = entry.getKey();
                final double budget = entry.getValue();
                final int color = catColors[ci % catColors.length];
                inner.addView(createBudgetRow(cat, budget, color));
                if (ci < total - 1) {
                    addDivider(inner);
                }
                ci++;
            }
            container.addView(inner);
            list.addView(container);
        }
        findViewById(R.id.btn_add_budget).setOnClickListener(v -> showAddBudgetDialog());
    }

    private void showAddBudgetDialog() {
        List<Map<String, Object>> catDefs = loadCategoryDefs();
        String[] catNames = new String[catDefs.size()];
        for (int i = 0; i < catDefs.size(); i++) {
            catNames[i] = (String) catDefs.get(i).get("name");
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(16));
        final int[] selectedCat = {0};
        TextView tvCat = new TextView(this);
        tvCat.setText("Category: " + catNames[0]);
        tvCat.setTextSize(15);
        tvCat.setTextColor(0xFF111111);
        tvCat.setBackgroundResource(R.drawable.input_outline);
        tvCat.setPadding(dp(16), dp(12), dp(16), dp(12));
        tvCat.setClickable(true);
        tvCat.setFocusable(true);
        tvCat.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Select Category")
                    .setItems(catNames, (dialog, which) -> {
                        selectedCat[0] = which;
                        tvCat.setText("Category: " + catNames[which]);
                    })
                    .show();
        });
        layout.addView(tvCat);
        EditText etAmount = new EditText(this);
        etAmount.setHint("Budget amount ($)");
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etAmount.setTextSize(15);
        etAmount.setBackgroundResource(R.drawable.input_outline);
        etAmount.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        etAmount.setLayoutParams(lp);
        layout.addView(etAmount);
        new AlertDialog.Builder(this)
                .setTitle("Add Budget Goal")
                .setView(layout)
                .setPositiveButton("Add", (dialog, which) -> {
                    String amtStr = etAmount.getText().toString().trim();
                    if (amtStr.isEmpty()) {
                        Toast.makeText(this, "Enter a budget amount", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double amt;
                    try { amt = Double.parseDouble(amtStr); } catch (Exception e) {
                        Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String, Double> defs = loadBudgetDefs();
                    defs.put(catNames[selectedCat[0]], amt);
                    saveBudgetDefs(defs);
                    loadSettingsBudgetGoals();
                    Toast.makeText(this, "Budget goal added", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditBudgetDialog(String category, double currentBudget) {
        EditText etAmount = new EditText(this);
        etAmount.setText(String.format("%.0f", currentBudget));
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etAmount.setTextSize(15);
        etAmount.setBackgroundResource(R.drawable.input_outline);
        etAmount.setPadding(dp(16), dp(12), dp(16), dp(12));
        new AlertDialog.Builder(this)
                .setTitle("Edit Budget: " + category)
                .setView(etAmount)
                .setPositiveButton("Save", (dialog, which) -> {
                    String amtStr = etAmount.getText().toString().trim();
                    if (amtStr.isEmpty()) {
                        Toast.makeText(this, "Enter a budget amount", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double amt;
                    try { amt = Double.parseDouble(amtStr); } catch (Exception e) {
                        Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String, Double> defs = loadBudgetDefs();
                    defs.put(category, amt);
                    saveBudgetDefs(defs);
                    loadSettingsBudgetGoals();
                    Toast.makeText(this, "Budget updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteBudgetDialog(String category) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Budget")
                .setMessage("Remove budget goal for \"" + category + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    Map<String, Double> defs = loadBudgetDefs();
                    defs.remove(category);
                    saveBudgetDefs(defs);
                    budgets.remove(category);
                    loadSettingsBudgetGoals();
                    Toast.makeText(this, "Budget removed", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private LinearLayout createBudgetRow(String category, double budget, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setClickable(true);
        row.setFocusable(true);
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setForeground(ContextCompat.getDrawable(this, tv.resourceId));
        row.setOnClickListener(v -> showEditBudgetDialog(category, budget));
        row.setOnLongClickListener(v -> {
            showDeleteBudgetDialog(category);
            return true;
        });
        LinearLayout topRow = new LinearLayout(this);
        topRow.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView tvCat = new TextView(this);
        tvCat.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvCat.setText(category);
        tvCat.setTextSize(14);
        tvCat.setTextColor(0xFF000000);
        tvCat.setTypeface(null, android.graphics.Typeface.BOLD);
        topRow.addView(tvCat);
        TextView tvBudget = new TextView(this);
        tvBudget.setText("$" + String.format("%,.0f", budget));
        tvBudget.setTextSize(14);
        tvBudget.setTextColor(0xFF000000);
        tvBudget.setTypeface(null, android.graphics.Typeface.BOLD);
        topRow.addView(tvBudget);
        row.addView(topRow);
        LinearLayout barOuter = new LinearLayout(this);
        int barHeight = dp(10);
        barOuter.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, barHeight));
        android.graphics.drawable.GradientDrawable bgShape = new android.graphics.drawable.GradientDrawable();
        bgShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bgShape.setCornerRadius(barHeight / 2f);
        bgShape.setColor(0xFFF0E8D5);
        barOuter.setBackground(bgShape);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            barOuter.setClipToOutline(true);
        }
        double spent = 0;
        if (allTransactions != null) {
            long monthStart = getMonthStartMillis();
            for (Transaction t : allTransactions) {
                if (t.getDateMillis() >= monthStart && t.getType() == Transaction.Type.OUTGOING) {
                    String tCat = t.getCategory();
                    if (tCat != null && tCat.equalsIgnoreCase(category)) {
                        spent += t.getAmount();
                    }
                }
            }
        }
        float fillPct = (float) Math.min(spent / budget, 1.0);
        View fill = new View(this);
        fill.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, fillPct));
        fill.setBackgroundColor(color);
        barOuter.addView(fill);
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - fillPct));
        barOuter.addView(spacer);
        row.addView(barOuter);
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setPadding(0, dp(4), 0, 0);
        TextView tvSpent = new TextView(this);
        tvSpent.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvSpent.setText("$" + String.format("%,.0f", spent) + " spent");
        tvSpent.setTextSize(11);
        tvSpent.setTextColor(0xFF888888);
        bottomRow.addView(tvSpent);
        TextView tvRemaining = new TextView(this);
        double remaining = budget - spent;
        tvRemaining.setText("$" + String.format("%,.0f", remaining) + " left");
        tvRemaining.setTextSize(11);
        tvRemaining.setTextColor(remaining >= 0 ? 0xFF2B9348 : 0xFFE53935);
        tvRemaining.setTypeface(null, android.graphics.Typeface.BOLD);
        bottomRow.addView(tvRemaining);
        row.addView(bottomRow);
        return row;
    }

    private void loadSettingsNicknames() {
        LinearLayout list = findViewById(R.id.container_nicknames_list);
        list.removeAllViews();
        Map<String, String> aliases = aliasStore.getAll();

        Set<String> merchantNames = new LinkedHashSet<>();
        if (allTransactions != null) {
            for (Transaction t : allTransactions) {
                String name = t.getRawVendor() != null ? t.getRawVendor() : t.getMerchant();
                if (name != null && !name.isEmpty()) {
                    merchantNames.add(name);
                }
            }
        }

        List<View> rows = new ArrayList<>();

        for (String merchant : merchantNames) {
            String alias = aliases.get(merchant);
            if (alias != null) {
                rows.add(createNicknameRow(merchant, alias));
            }
        }

        for (String merchant : merchantNames) {
            String alias = aliases.get(merchant);
            if (alias == null) {
                rows.add(createMerchantNicknamePromptRow(merchant));
            }
        }

        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            if (!merchantNames.contains(entry.getKey())) {
                rows.add(createNicknameRow(entry.getKey(), entry.getValue()));
            }
        }

        if (rows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No merchants found.\nAdd transactions first, or tap + to add manually.");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            list.addView(empty);
        } else {
            CardView container = createCardContainer();
            LinearLayout inner = createCardInner();
            for (int i = 0; i < rows.size(); i++) {
                inner.addView(rows.get(i));
                if (i < rows.size() - 1) {
                    addDivider(inner);
                }
            }
            container.addView(inner);
            list.addView(container);
        }
        findViewById(R.id.btn_add_nickname).setOnClickListener(v -> showAddNicknameDialog());

        // ── Subject Rules Section ──
        loadSubjectRulesSection();
    }

    private void loadSubjectRulesSection() {
        LinearLayout root = findViewById(R.id.container_nicknames_list);
        SubjectRuleStore srs = new SubjectRuleStore(this);
        Map<String, Map<String, SubjectRuleStore.SubjectRule>> allRules = srs.getAll();

        boolean hasRules = false;
        for (Map.Entry<String, Map<String, SubjectRuleStore.SubjectRule>> senderEntry : allRules.entrySet()) {
            for (SubjectRuleStore.SubjectRule rule : senderEntry.getValue().values()) {
                if (rule.alias != null || rule.category != null) {
                    hasRules = true;
                    break;
                }
            }
            if (hasRules) break;
        }

        if (!hasRules) return;

        // Section header
        TextView header = new TextView(this);
        header.setText("Subject-Based Rules");
        header.setTextSize(13);
        header.setTextColor(0xFF888888);
        header.setPadding(dp(16), dp(20), dp(16), dp(8));
        root.addView(header);

        CardView container = createCardContainer();
        LinearLayout inner = createCardInner();
        boolean first = true;

        for (Map.Entry<String, Map<String, SubjectRuleStore.SubjectRule>> senderEntry : allRules.entrySet()) {
            String sender = senderEntry.getKey();
            for (SubjectRuleStore.SubjectRule rule : senderEntry.getValue().values()) {
                if (rule.alias == null && rule.category == null) continue;

                if (!first) addDivider(inner);
                first = false;

                LinearLayout row = createCardRow();
                LinearLayout textCol = new LinearLayout(this);
                textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                textCol.setOrientation(LinearLayout.VERTICAL);

                TextView tvSender = new TextView(this);
                tvSender.setText(sender);
                tvSender.setTextSize(10);
                tvSender.setTextColor(0xFFAAAAAA);
                textCol.addView(tvSender);

                TextView tvSubject = new TextView(this);
                tvSubject.setText("Subject: " + rule.keywordsKey.replace(",", ", "));
                tvSubject.setTextSize(11);
                tvSubject.setTextColor(0xFF888888);
                textCol.addView(tvSubject);

                if (rule.alias != null) {
                    TextView tvAlias = new TextView(this);
                    tvAlias.setText("→ " + rule.alias);
                    tvAlias.setTextSize(14);
                    tvAlias.setTextColor(0xFF000000);
                    tvAlias.setTypeface(null, android.graphics.Typeface.BOLD);
                    textCol.addView(tvAlias);
                }

                if (rule.category != null) {
                    TextView tvCat = new TextView(this);
                    tvCat.setText("  [" + rule.category + "]");
                    tvCat.setTextSize(12);
                    tvCat.setTextColor(0xFFF9A84D);
                    textCol.addView(tvCat);
                }

                row.addView(textCol);

                TextView btnDelete = new TextView(this);
                btnDelete.setText("Delete");
                btnDelete.setTextSize(13);
                btnDelete.setTextColor(0xFFE53935);
                btnDelete.setTypeface(null, android.graphics.Typeface.BOLD);
                btnDelete.setPadding(dp(12), dp(8), dp(12), dp(8));
                btnDelete.setClickable(true);
                btnDelete.setFocusable(true);
                String finalSender = sender;
                String finalKey = rule.keywordsKey;
                btnDelete.setOnClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Delete Subject Rule")
                            .setMessage("Remove rule for \"" + finalSender + "\" with subject keywords \"" + finalKey.replace(",", ", ") + "\"?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                srs.removeRule(finalSender, finalKey);
                                loadSettingsNicknames();
                                Toast.makeText(this, "Rule removed", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
                row.addView(btnDelete);
                inner.addView(row);
            }
        }

        container.addView(inner);
        root.addView(container);
    }

    private LinearLayout createNicknameRow(String original, String alias) {
        LinearLayout row = createCardRow();
        LinearLayout textCol = new LinearLayout(this);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView tvOriginal = new TextView(this);
        tvOriginal.setText(original);
        tvOriginal.setTextSize(12);
        tvOriginal.setTextColor(0xFF888888);
        textCol.addView(tvOriginal);
        TextView tvAlias = new TextView(this);
        tvAlias.setText(alias);
        tvAlias.setTextSize(15);
        tvAlias.setTextColor(0xFF000000);
        tvAlias.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(tvAlias);
        row.addView(textCol);
        TextView btnDelete = new TextView(this);
        btnDelete.setText("Delete");
        btnDelete.setTextSize(13);
        btnDelete.setTextColor(0xFFE53935);
        btnDelete.setTypeface(null, android.graphics.Typeface.BOLD);
        btnDelete.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnDelete.setClickable(true);
        btnDelete.setFocusable(true);
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Nickname")
                    .setMessage("Remove nickname for \"" + original + "\"?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        aliasStore.removeAlias(original);
                        loadSettingsNicknames();
                        Toast.makeText(this, "Nickname removed", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        row.addView(btnDelete);
        return row;
    }

    private LinearLayout createMerchantNicknamePromptRow(String merchant) {
        LinearLayout row = createCardRow();
        LinearLayout textCol = new LinearLayout(this);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView tvMerchant = new TextView(this);
        tvMerchant.setText(merchant);
        tvMerchant.setTextSize(15);
        tvMerchant.setTextColor(0xFF000000);
        tvMerchant.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(tvMerchant);
        TextView tvHint = new TextView(this);
        tvHint.setText("No nickname set");
        tvHint.setTextSize(12);
        tvHint.setTextColor(0xFF888888);
        textCol.addView(tvHint);
        row.addView(textCol);
        TextView btnSet = new TextView(this);
        btnSet.setText("Set Nickname");
        btnSet.setTextSize(13);
        btnSet.setTextColor(0xFFF9A84D);
        btnSet.setTypeface(null, android.graphics.Typeface.BOLD);
        btnSet.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnSet.setClickable(true);
        btnSet.setFocusable(true);
        btnSet.setOnClickListener(v -> showSetNicknameDialog(merchant));
        row.addView(btnSet);
        return row;
    }

    private void showSetNicknameDialog(String merchant) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(16));
        TextView tvMerchant = new TextView(this);
        tvMerchant.setText("Merchant: " + merchant);
        tvMerchant.setTextSize(14);
        tvMerchant.setTextColor(0xFF888888);
        tvMerchant.setPadding(0, 0, 0, dp(12));
        layout.addView(tvMerchant);
        final EditText etAlias = new EditText(this);
        etAlias.setHint("Nickname to display");
        etAlias.setTextSize(14);
        etAlias.setBackgroundResource(R.drawable.input_outline);
        etAlias.setPadding(dp(16), dp(12), dp(16), dp(12));
        layout.addView(etAlias);
        new AlertDialog.Builder(this)
                .setTitle("Set Nickname")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String alias = etAlias.getText().toString().trim();
                    if (alias.isEmpty()) {
                        Toast.makeText(this, "Please enter a nickname", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    aliasStore.setAlias(merchant, alias);
                    loadSettingsNicknames();
                    Toast.makeText(this, "Nickname saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddNicknameDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(16));
        final EditText etOriginal = new EditText(this);
        etOriginal.setHint("Original merchant name");
        etOriginal.setTextSize(14);
        etOriginal.setBackgroundResource(R.drawable.input_outline);
        etOriginal.setPadding(dp(16), dp(12), dp(16), dp(12));
        layout.addView(etOriginal);
        android.view.ViewGroup.MarginLayoutParams params = (android.view.ViewGroup.MarginLayoutParams) etOriginal.getLayoutParams();
        if (params != null) params.bottomMargin = dp(12);
        else {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(12);
            etOriginal.setLayoutParams(lp);
        }
        final EditText etAlias = new EditText(this);
        etAlias.setHint("Nickname to display");
        etAlias.setTextSize(14);
        etAlias.setBackgroundResource(R.drawable.input_outline);
        etAlias.setPadding(dp(16), dp(12), dp(16), dp(12));
        layout.addView(etAlias);
        new AlertDialog.Builder(this)
                .setTitle("Add Nickname")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String orig = etOriginal.getText().toString().trim();
                    String alias = etAlias.getText().toString().trim();
                    if (orig.isEmpty() || alias.isEmpty()) {
                        Toast.makeText(this, "Both fields required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    aliasStore.setAlias(orig, alias);
                    loadSettingsNicknames();
                    Toast.makeText(this, "Nickname saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private List<Map<String, Object>> loadCategoryDefs() {
        String json = settingsPrefs.getString("category_defs", null);
        if (json == null) {
            String[] defaultNames = {
                    "Food & Dining", "Shopping", "Subscriptions", "Transportation",
                    "Bills & Utilities", "Entertainment", "Health", "Interac Sent",
                    "Interac Received", "Transfers", "Travel", "Other"
            };
        int[] defaultColors = {
                0xFF2F4B4F, 0xFF365C4A, 0xFF3F6A42, 0xFF57713A,
                0xFF6C7335, 0xFF7A6A32, 0xFF8A6030, 0xFF97542F,
                0xFFA04A36, 0xFFA03F4C, 0xFF9B3F63, 0xFF8E4D79
        };
            List<Map<String, Object>> defaults = new ArrayList<>();
            for (int i = 0; i < defaultNames.length; i++) {
                Map<String, Object> m = new HashMap<>();
                m.put("name", defaultNames[i]);
                m.put("color", defaultColors[i]);
                defaults.add(m);
            }
            saveCategoryDefs(defaults);
            return defaults;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Map<String, Object> m = new HashMap<>();
                m.put("name", o.getString("name"));
                m.put("color", o.getInt("color"));
                result.add(m);
            }
        } catch (Exception ignored) {}
        return result;
    }

    private void saveCategoryDefs(List<Map<String, Object>> defs) {
        try {
            JSONArray arr = new JSONArray();
            for (Map<String, Object> m : defs) {
                JSONObject o = new JSONObject();
                o.put("name", m.get("name"));
                o.put("color", m.get("color"));
                arr.put(o);
            }
            settingsPrefs.edit().putString("category_defs", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void loadSettingsCategories() {
        LinearLayout list = findViewById(R.id.container_categories_list);
        list.removeAllViews();
        List<Map<String, Object>> defs = loadCategoryDefs();
        if (defs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No categories defined.");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            list.addView(empty);
        } else {
            // Build reverse map: category -> list of merchants
            Map<String, List<String>> catMerchants = new HashMap<>();
            Map<String, String> allVendorCats = vendorStore.getAll();
            for (Map.Entry<String, String> e : allVendorCats.entrySet()) {
                String cat = e.getValue();
                if (!catMerchants.containsKey(cat)) {
                    catMerchants.put(cat, new ArrayList<>());
                }
                catMerchants.get(cat).add(e.getKey());
            }

            CardView container = createCardContainer();
            LinearLayout inner = createCardInner();
            for (int i = 0; i < defs.size(); i++) {
                final int idx = i;
                final String catName = (String) defs.get(i).get("name");
                final int catColor = (int) defs.get(i).get("color");

                // Category header row
                LinearLayout row = createCardRow();
                row.setClickable(true);
                row.setFocusable(true);
                android.util.TypedValue tv = new android.util.TypedValue();
                getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
                row.setForeground(ContextCompat.getDrawable(this, tv.resourceId));
                row.setOnClickListener(v -> openSettingsEditCategory(catName, catColor, idx));
                ImageView dot = new ImageView(this);
                int dotSize = dp(28);
                dot.setLayoutParams(new LinearLayout.LayoutParams(dotSize, dotSize));
                Bitmap bmp = Bitmap.createBitmap(dotSize, dotSize, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bmp);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(catColor);
                c.drawCircle(dotSize / 2f, dotSize / 2f, dotSize / 2f, p);
                dot.setImageBitmap(bmp);
                row.addView(dot);
                TextView tvName = new TextView(this);
                tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                tvName.setText(catName);
                tvName.setTextSize(15);
                tvName.setTextColor(0xFF000000);
                tvName.setPadding(dp(12), 0, 0, 0);
                row.addView(tvName);
                TextView tvArrow = new TextView(this);
                tvArrow.setText("›");
                tvArrow.setTextColor(0xFFD4A373);
                tvArrow.setTextSize(18);
                row.addView(tvArrow);
                inner.addView(row);

                // Merchant sub-rows for this category
                List<String> merchants = catMerchants.get(catName);
                if (merchants != null && !merchants.isEmpty()) {
                    for (final String merchant : merchants) {
                        LinearLayout merchantRow = new LinearLayout(this);
                        merchantRow.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
                        merchantRow.setOrientation(LinearLayout.HORIZONTAL);
                        merchantRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                        merchantRow.setPadding(dp(48), 0, dp(16), 0);
                        merchantRow.setClickable(true);
                        merchantRow.setFocusable(true);
                        android.util.TypedValue tvMer = new android.util.TypedValue();
                        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tvMer, true);
                        merchantRow.setForeground(ContextCompat.getDrawable(this, tvMer.resourceId));
                        merchantRow.setOnClickListener(v -> showChangeMerchantCategoryDialog(merchant));

                        TextView tvMerchant = new TextView(this);
                        tvMerchant.setLayoutParams(new LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                        tvMerchant.setText(merchant);
                        tvMerchant.setTextSize(14);
                        tvMerchant.setTextColor(0xFF666666);
                        tvMerchant.setSingleLine(true);
                        tvMerchant.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        merchantRow.addView(tvMerchant);

                        TextView tvChange = new TextView(this);
                        tvChange.setText("change");
                        tvChange.setTextSize(12);
                        tvChange.setTextColor(0xFFD4A373);
                        tvChange.setPadding(dp(8), dp(4), dp(8), dp(4));
                        merchantRow.addView(tvChange);

                        inner.addView(merchantRow);
                    }
                } else {
                    // Show "No merchants" placeholder
                    TextView tvEmpty = new TextView(this);
                    tvEmpty.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));
                    tvEmpty.setText("No merchants assigned yet");
                    tvEmpty.setTextSize(12);
                    tvEmpty.setTextColor(0xFFBBBBBB);
                    tvEmpty.setPadding(dp(48), 0, dp(16), 0);
                    tvEmpty.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    inner.addView(tvEmpty);
                }

                if (i < defs.size() - 1) {
                    addDivider(inner);
                }
            }
            container.addView(inner);
            list.addView(container);
        }
        findViewById(R.id.btn_add_category).setOnClickListener(v -> {
            openSettingsEditCategory("", 0xFF29B6F6, -1);
        });
    }

    private void showChangeMerchantCategoryDialog(final String merchant) {
        List<Map<String, Object>> defs = loadCategoryDefs();
        String[] catNames = new String[defs.size()];
        for (int i = 0; i < defs.size(); i++) {
            catNames[i] = (String) defs.get(i).get("name");
        }
        new AlertDialog.Builder(this)
                .setTitle("Change category for " + merchant)
                .setItems(catNames, (dialog, which) -> {
                    String newCat = catNames[which];
                    vendorStore.setCategory(merchant, newCat);
                    loadSettingsCategories();
                    Toast.makeText(this, merchant + " moved to " + newCat, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int editingCategoryIndex = -1;

    private void openSettingsEditCategory(String name, int color, int index) {
        editingCategoryName = name;
        editingCategoryColor = color;
        editingCategoryIndex = index;
        ((EditText) findViewById(R.id.et_edit_category_name)).setText(name);
        setupSettingsColorPicker();
        highlightSettingsColorInPicker(color);
        findViewById(R.id.btn_edit_category_delete).setVisibility(index == -1 ? View.GONE : View.VISIBLE);
        refreshEditCategoryMerchants();
        showSettingsScreen(containerSettingsEditCategory);
    }

    private void refreshEditCategoryMerchants() {
        LinearLayout container = findViewById(R.id.container_edit_category_merchants);
        container.removeAllViews();
        Map<String, String> all = vendorStore.getAll();
        boolean hasAny = false;
        for (Map.Entry<String, String> e : all.entrySet()) {
            if (e.getValue().equals(editingCategoryName)) {
                hasAny = true;
                TextView tv = new TextView(this);
                tv.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));
                tv.setText(e.getKey());
                tv.setTextSize(14);
                tv.setTextColor(0xFF666666);
                tv.setPadding(dp(48), 0, dp(16), 0);
                tv.setGravity(android.view.Gravity.CENTER_VERTICAL);
                container.addView(tv);
            }
        }
        if (!hasAny) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));
            tv.setText("No merchants assigned yet");
            tv.setTextSize(12);
            tv.setTextColor(0xFFBBBBBB);
            tv.setPadding(dp(48), 0, dp(16), 0);
            tv.setGravity(android.view.Gravity.CENTER_VERTICAL);
            container.addView(tv);
        }
    }

    private void showAddMerchantDropdown() {
        List<Map<String, Object>> defs = loadCategoryDefs();
        Map<String, String> all = vendorStore.getAll();
        Set<String> used = new HashSet<>();
        for (Map.Entry<String, String> e : all.entrySet()) {
            if (e.getValue().equals(editingCategoryName)) {
                used.add(e.getKey());
            }
        }
        List<String> available = new ArrayList<>();
        for (String merchant : all.keySet()) {
            if (!used.contains(merchant)) {
                available.add(merchant);
            }
        }
        if (available.isEmpty()) {
            Toast.makeText(this, "No other merchants available", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] arr = available.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Add Merchant to " + editingCategoryName)
                .setItems(arr, (dialog, which) -> {
                    vendorStore.setCategory(arr[which], editingCategoryName);
                    refreshEditCategoryMerchants();
                    Toast.makeText(this, arr[which] + " added", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupSettingsEditCategory() {
        findViewById(R.id.btn_edit_category_add_merchant).setOnClickListener(v -> showAddMerchantDropdown());
        findViewById(R.id.btn_edit_category_save).setOnClickListener(v -> {
            String newName = ((EditText) findViewById(R.id.et_edit_category_name)).getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "Category name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            List<Map<String, Object>> defs = loadCategoryDefs();
            if (editingCategoryIndex >= 0 && editingCategoryIndex < defs.size()) {
                defs.get(editingCategoryIndex).put("name", newName);
                defs.get(editingCategoryIndex).put("color", editingCategoryColor);
            } else {
                Map<String, Object> m = new HashMap<>();
                m.put("name", newName);
                m.put("color", editingCategoryColor);
                defs.add(m);
            }
            saveCategoryDefs(defs);
            Toast.makeText(this, "Category saved", Toast.LENGTH_SHORT).show();
            loadSettingsCategories();
            showSettingsScreen(containerSettingsCategories);
        });
        findViewById(R.id.btn_edit_category_delete).setOnClickListener(v -> {
            String catName = ((EditText) findViewById(R.id.et_edit_category_name)).getText().toString().trim();
            new AlertDialog.Builder(this)
                    .setTitle("Delete Category")
                    .setMessage("Delete \"" + catName + "\"? Transactions in this category will be moved to \"Other\".")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        List<Map<String, Object>> defs = loadCategoryDefs();
                        if (editingCategoryIndex >= 0 && editingCategoryIndex < defs.size()) {
                            defs.remove(editingCategoryIndex);
                            saveCategoryDefs(defs);
                            Toast.makeText(this, "Category deleted", Toast.LENGTH_SHORT).show();
                            editingCategoryIndex = -1;
                            loadSettingsCategories();
                            showSettingsScreen(containerSettingsCategories);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void setupSettingsColorPicker() {
        LinearLayout container = findViewById(R.id.container_color_picker);
        int[] colors = {
                0xFF2F4B4F, 0xFF365C4A, 0xFF3F6A42, 0xFF57713A,
                0xFF6C7335, 0xFF7A6A32, 0xFF8A6030, 0xFF97542F,
                0xFFA04A36, 0xFFA03F4C, 0xFF9B3F63, 0xFF8E4D79,
                0xFF7B5F8B, 0xFF6B7296, 0xFF5F839B, 0xFF55939A,
                0xFF4A9E8D, 0xFF5FA47A, 0xFF83A26D, 0xFFA3A06A
        };
        container.removeAllViews();
        int cols = 5;
        int size = dp(36);
        int margin = dp(6);
        LinearLayout row = null;
        for (int i = 0; i < colors.length; i++) {
            final int c = colors[i];
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                row.setLayoutParams(rlp);
                container.addView(row);
            }
            ImageView dot = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, margin / 2, margin, margin / 2);
            dot.setLayoutParams(lp);
            dot.setTag(c);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(c);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, p);
            dot.setImageBitmap(bmp);
            dot.setClickable(true);
            dot.setFocusable(true);
            dot.setOnClickListener(v -> {
                editingCategoryColor = c;
                highlightSettingsColorInPicker(c);
            });
            row.addView(dot);
        }
    }

    private void highlightSettingsColorInPicker(int selectedColor) {
        LinearLayout container = findViewById(R.id.container_color_picker);
        int size = dp(36);
        for (int i = 0; i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            if (row instanceof LinearLayout) {
                for (int j = 0; j < ((LinearLayout) row).getChildCount(); j++) {
                    View child = ((LinearLayout) row).getChildAt(j);
                    if (child instanceof ImageView) {
                        int color = (int) child.getTag();
                        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bmp);
                        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                        p.setColor(color);
                        if (color == selectedColor) {
                            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
                            ring.setStyle(Paint.Style.STROKE);
                            ring.setStrokeWidth(dp(3));
                            ring.setColor(0xFF000000);
                            canvas.drawCircle(size / 2f, size / 2f, size / 2f - dp(2), ring);
                        }
                        canvas.drawCircle(size / 2f, size / 2f, size / 2f - dp(2), p);
                        ((ImageView) child).setImageBitmap(bmp);
                    }
                }
            }
        }
    }

    private void loadSettingsAutoTracking() {
        SwitchCompat switchClassify = findViewById(R.id.switch_auto_classify);
        SwitchCompat switchSync = findViewById(R.id.switch_auto_sync);
        SwitchCompat switchScan = findViewById(R.id.switch_email_scanning);
        TextView tvFrequency = findViewById(R.id.tv_scan_frequency);
        boolean classify = settingsPrefs.getBoolean("auto_classify", true);
        boolean sync = settingsPrefs.getBoolean("auto_sync", true);
        boolean scan = settingsPrefs.getBoolean("email_scanning", true);
        String freq = settingsPrefs.getString("scan_frequency", "Every 1 hour");
        switchClassify.setChecked(classify);
        switchSync.setChecked(sync);
        switchScan.setChecked(scan);
        tvFrequency.setText(freq);
        switchClassify.setOnCheckedChangeListener((buttonView, isChecked) ->
                settingsPrefs.edit().putBoolean("auto_classify", isChecked).apply());
        switchSync.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsPrefs.edit().putBoolean("auto_sync", isChecked).apply();
            if (isChecked) {
                scheduleNextScan();
            } else {
                stopPeriodicScan();
            }
        });
        switchScan.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor ed = settingsPrefs.edit().putBoolean("email_scanning", isChecked);
            if (isChecked) {
                String pausedAt = settingsPrefs.getString("paused_at", null);
                if (pausedAt != null) {
                    savePauseRange(pausedAt, String.valueOf(System.currentTimeMillis()));
                    ed.remove("paused_at");
                }
            } else {
                ed.putString("paused_at", String.valueOf(System.currentTimeMillis()));
            }
            ed.apply();
            if (isChecked) {
                fetchAndShowTransactions();
            }
        });
        tvFrequency.setOnClickListener(v -> {
            String[] options = {"Every 30 min", "Every 1 hour", "Every 3 hours", "Every 6 hours", "Daily"};
            int checked = 0;
            String current = tvFrequency.getText().toString();
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(current)) { checked = i; break; }
            }
            new AlertDialog.Builder(this)
                    .setTitle("Scan Frequency")
                    .setSingleChoiceItems(options, checked, (dialog, which) -> {
                        tvFrequency.setText(options[which]);
                        settingsPrefs.edit().putString("scan_frequency", options[which]).apply();
                        dialog.dismiss();
                    })
                    .show();
        });
    }

    private void loadSettingsMailScanning() {
        SwitchCompat switchEnable = findViewById(R.id.switch_mail_scanning_enable);
        SwitchCompat switchManual = findViewById(R.id.switch_manual_scan_only);
        LinearLayout manualScanBtnContainer = findViewById(R.id.container_manual_scan_button);

        boolean scanning = settingsPrefs.getBoolean("email_scanning", true);
        boolean manualOnly = settingsPrefs.getBoolean("manual_scan_only", false);
        String lastScanTime = settingsPrefs.getString("last_scan_time", null);

        switchEnable.setChecked(scanning);
        switchManual.setChecked(manualOnly);

        manualScanBtnContainer.setVisibility(manualOnly ? View.VISIBLE : View.GONE);
        TextView btnManualScan = findViewById(R.id.btn_run_manual_scan);
        android.graphics.drawable.GradientDrawable msBg = new android.graphics.drawable.GradientDrawable();
        msBg.setColor(0xFFF9A84D);
        msBg.setCornerRadius(dp(24));
        btnManualScan.setBackground(msBg);
        btnManualScan.setOnClickListener(v -> runManualScan());

        setMailScanningStatus(scanning);

        switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsPrefs.edit().putBoolean("email_scanning", isChecked).apply();
            if (isChecked) {
                String pausedAt = settingsPrefs.getString("paused_at", null);
                if (pausedAt != null) {
                    savePauseRange(pausedAt, String.valueOf(System.currentTimeMillis()));
                    settingsPrefs.edit().remove("paused_at").apply();
                }
                fetchAndShowTransactions();
            } else {
                settingsPrefs.edit().putString("paused_at", String.valueOf(System.currentTimeMillis())).apply();
            }
            setMailScanningStatus(isChecked);
            loadSettingsMailScanning();
        });

        switchManual.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsPrefs.edit().putBoolean("manual_scan_only", isChecked).apply();
            manualScanBtnContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        updateLastScanInfo(lastScanTime, !scanning);
        updateTotalEmailsScanned();
        loadKeywordTags();
        findViewById(R.id.btn_add_keyword).setOnClickListener(v -> showAddKeywordDialog());

        TextView subtitle = findViewById(R.id.tv_mail_scanning_subtitle);
        SpannableStringBuilder ss = new SpannableStringBuilder("Control when? and how? your Gmail is read");
        ss.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 8, 13, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 18, 22, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new android.text.style.ForegroundColorSpan(0xFFF9575C), 28, 33, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        subtitle.setText(ss);
    }

    private void setMailScanningStatus(boolean enabled) {
        TextView tvStatus = findViewById(R.id.tv_mail_scanning_status);
        LinearLayout alert = findViewById(R.id.container_mail_scanning_alert);
        TextView tvAlertBody = findViewById(R.id.tv_alert_body);
        if (enabled) {
            tvStatus.setText("Active \u2014 scanning new emails");
            tvStatus.setTextColor(0xFF2B9348);
            alert.setVisibility(View.GONE);
        } else {
            String pausedAt = settingsPrefs.getString("paused_at", null);
            String pausedDisplay;
            if (pausedAt != null) {
                try {
                    long pts = Long.parseLong(pausedAt);
                    pausedDisplay = new SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.US).format(new Date(pts));
                } catch (Exception e) {
                    pausedDisplay = "recently";
                }
            } else {
                pausedDisplay = "recently";
            }
            tvStatus.setText("Paused since " + pausedDisplay);
            tvStatus.setTextColor(0xFF888888);
            tvAlertBody.setText("Transactions from " + pausedDisplay + " \u2013 today will not be imported when you re-enable. This gap is intentional and permanent.");
            alert.setVisibility(View.VISIBLE);
        }
    }

    private void updateLastScanInfo(String lastScanTime, boolean isPaused) {
        TextView tvValue = findViewById(R.id.tv_last_scan_value);
        TextView badge = findViewById(R.id.badge_scan_status);
        android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
        badgeBg.setCornerRadius(dp(20));
        if (isPaused) {
            badge.setText("paused now");
            badge.setTextColor(0xFFBF8C00);
            badgeBg.setColor(0xFFFFF3E0);
        } else {
            badge.setText("active");
            badge.setTextColor(0xFF2B9348);
            badgeBg.setColor(0xFFE8F5E9);
        }
        badge.setBackground(badgeBg);
        if (lastScanTime != null) {
            tvValue.setText(lastScanTime);
        } else {
            tvValue.setText("No scans yet");
        }
    }

    private void updateTotalEmailsScanned() {
        TextView tvCount = findViewById(R.id.tv_total_emails_scanned);
        int count = 0;
        if (allTransactions != null) {
            for (Transaction t : allTransactions) {
                if (t.getMessageId() != null && !t.getMessageId().startsWith("manual_")) {
                    count++;
                }
            }
        }
        tvCount.setText(String.valueOf(count));
    }

    private void savePauseRange(String fromStr, String untilStr) {
        long from = Long.parseLong(fromStr);
        long until = Long.parseLong(untilStr);
        String json = settingsPrefs.getString("paused_ranges", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            JSONObject range = new JSONObject();
            range.put("from", from);
            range.put("until", until);
            arr.put(range);
            settingsPrefs.edit().putString("paused_ranges", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private boolean isInPausedRange(long dateMillis) {
        String json = settingsPrefs.getString("paused_ranges", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject range = arr.getJSONObject(i);
                long from = range.getLong("from");
                long until = range.getLong("until");
                if (dateMillis >= from && dateMillis <= until) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        String pausedAt = settingsPrefs.getString("paused_at", null);
        if (pausedAt != null) {
            long paused = Long.parseLong(pausedAt);
            if (dateMillis >= paused) {
                return true;
            }
        }
        return false;
    }

    private void runManualScan() {
        settingsPrefs.edit()
                .remove("paused_at")
                .putString("paused_ranges", "[]")
                .putString("last_scan_time", new SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.US).format(new Date()))
                .apply();
        Toast.makeText(this, "Manual scan started \u2014 fetching all emails...", Toast.LENGTH_SHORT).show();
        loadSettingsMailScanning();
        fetchAndShowTransactions();
    }

    private void loadKeywordTags() {
        LinearLayout container = findViewById(R.id.container_keywords_tags);
        container.removeAllViews();

        Set<String> keywords = new LinkedHashSet<>();
        keywords.add("receipt");
        keywords.add("order confirmed");
        keywords.add("e-transfer");
        keywords.add("payment");
        keywords.add("charged");
        keywords.add("your order");
        keywords.add("purchase");
        keywords.add("invoice");

        String customJson = settingsPrefs.getString("scan_keywords_extra", "[]");
        try {
            JSONArray arr = new JSONArray(customJson);
            for (int i = 0; i < arr.length(); i++) {
                keywords.add(arr.getString(i).toLowerCase());
            }
        } catch (Exception ignored) {}

        LinearLayout currentRow = null;
        int rowWidth = dp(320);
        int used = 0;

        for (String kw : keywords) {
            TextView tag = new TextView(this);
            tag.setText(kw);
            tag.setTextSize(14);
            tag.setTextColor(0xFF8D6E00);
            tag.setPadding(dp(14), dp(8), dp(14), dp(8));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFFFFF3E0);
            bg.setCornerRadius(dp(24));
            tag.setBackground(bg);
            tag.setClickable(true);
            tag.setFocusable(true);
            tag.setOnClickListener(v -> showDeleteKeywordDialog(kw));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(8), dp(8));

            int tw = (int) (kw.length() * dp(10)) + dp(28);
            if (currentRow == null || used + tw > rowWidth) {
                currentRow = new LinearLayout(this);
                currentRow.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                container.addView(currentRow);
                used = 0;
            }
            currentRow.addView(tag, lp);
            used += tw + dp(8);
        }
    }

    private void showDeleteKeywordDialog(String keyword) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Keyword")
                .setMessage("Remove \"" + keyword + "\" from the keyword list?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    String json = settingsPrefs.getString("scan_keywords_extra", "[]");
                    try {
                        JSONArray arr = new JSONArray(json);
                        JSONArray newArr = new JSONArray();
                        for (int i = 0; i < arr.length(); i++) {
                            if (!arr.getString(i).equalsIgnoreCase(keyword)) {
                                newArr.put(arr.getString(i));
                            }
                        }
                        settingsPrefs.edit().putString("scan_keywords_extra", newArr.toString()).apply();
                    } catch (Exception ignored) {}
                    loadKeywordTags();
                    Toast.makeText(this, "Keyword removed", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddKeywordDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(16));
        final EditText etKeyword = new EditText(this);
        etKeyword.setHint("e.g. subscription");
        etKeyword.setTextSize(14);
        etKeyword.setBackgroundResource(R.drawable.input_outline);
        etKeyword.setPadding(dp(16), dp(12), dp(16), dp(12));
        layout.addView(etKeyword);
        new AlertDialog.Builder(this)
                .setTitle("Add Keyword")
                .setView(layout)
                .setPositiveButton("Add", (dialog, which) -> {
                    String kw = etKeyword.getText().toString().trim().toLowerCase();
                    if (kw.isEmpty()) {
                        Toast.makeText(this, "Please enter a keyword", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String json = settingsPrefs.getString("scan_keywords_extra", "[]");
                    try {
                        JSONArray arr = new JSONArray(json);
                        for (int i = 0; i < arr.length(); i++) {
                            if (arr.getString(i).equalsIgnoreCase(kw)) {
                                Toast.makeText(this, "Keyword already added", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                        arr.put(kw);
                        settingsPrefs.edit().putString("scan_keywords_extra", arr.toString()).apply();
                    } catch (Exception e) {
                        try {
                            JSONArray arr = new JSONArray();
                            arr.put(kw);
                            settingsPrefs.edit().putString("scan_keywords_extra", arr.toString()).apply();
                        } catch (Exception ignored) {}
                    }
                    loadKeywordTags();
                    Toast.makeText(this, "Keyword added", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ════════════════════════════════════════════════════════════════
    // PAYCHECK REMINDERS
    // ════════════════════════════════════════════════════════════════

    private void loadPaycheckReminders() {
        LinearLayout list = findViewById(R.id.container_paycheck_reminders_list);
        list.removeAllViews();

        List<PaycheckReminder> reminders = paycheckReminderStore.getAll();

        if (reminders.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No paycheck reminders set.\nAdd an income transaction\nto create one.");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            list.addView(empty);
            return;
        }

        CardView container = createCardContainer();
        LinearLayout inner = createCardInner();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

        for (int i = 0; i < reminders.size(); i++) {
            final PaycheckReminder r = reminders.get(i);

            LinearLayout row = createCardRow();

            LinearLayout textCol = new LinearLayout(this);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            textCol.setOrientation(LinearLayout.VERTICAL);

            TextView tvMerchant = new TextView(this);
            tvMerchant.setText(r.merchant);
            tvMerchant.setTextSize(15);
            tvMerchant.setTextColor(0xFF000000);
            tvMerchant.setTypeface(null, android.graphics.Typeface.BOLD);
            textCol.addView(tvMerchant);

            String nextDateStr = sdf.format(new Date(r.nextReminderDateMillis));
            String intervalLabel = r.intervalDays == 15 ? "15 days" : "1 month";
            String amtStr = r.amount > 0 ? "$" + String.format("%.2f", r.amount) : "Amount not set";
            TextView tvDetail = new TextView(this);
            tvDetail.setText(amtStr + " · Every " + intervalLabel + " · Next: " + nextDateStr);
            tvDetail.setTextSize(12);
            tvDetail.setTextColor(0xFF888888);
            textCol.addView(tvDetail);

            row.addView(textCol);

            TextView btnDelete = new TextView(this);
            btnDelete.setText("Delete");
            btnDelete.setTextSize(13);
            btnDelete.setTextColor(0xFFE53935);
            btnDelete.setTypeface(null, android.graphics.Typeface.BOLD);
            btnDelete.setPadding(dp(12), dp(8), dp(12), dp(8));
            btnDelete.setClickable(true);
            btnDelete.setFocusable(true);
            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Delete Reminder")
                        .setMessage("Remove paycheck reminder for \"" + r.merchant + "\"?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            paycheckReminderStore.delete(r.id);
                            loadPaycheckReminders();
                            Toast.makeText(this, "Reminder removed", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
            row.addView(btnDelete);

            inner.addView(row);
            if (i < reminders.size() - 1) {
                addDivider(inner);
            }
        }

        container.addView(inner);
        list.addView(container);
    }

    private void checkPaycheckReminders() {
        List<PaycheckReminder> due = paycheckReminderStore.getDueReminders();
        for (final PaycheckReminder r : due) {
            showPaycheckDueDialog(r);
        }
    }

    private void showPaycheckDueDialog(final PaycheckReminder r) {
        String intervalLabel = r.intervalDays == 15 ? "15 days" : "1 month";
        String expectedAmt = r.amount > 0 ? "$" + String.format("%.2f", r.amount) : "your paycheck amount";

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(16));

        TextView tvInfo = new TextView(this);
        tvInfo.setText("Your " + r.merchant + " (" + expectedAmt + ") is expected today!\n\n"
                + "Enter the amount received to sync it, or mark as received to schedule the next reminder.");
        tvInfo.setTextSize(14);
        tvInfo.setTextColor(0xFF333333);
        tvInfo.setLineSpacing(dp(4), 1);
        layout.addView(tvInfo);

        final EditText etAmt = new EditText(this);
        etAmt.setHint("Amount received ($)");
        if (r.amount > 0) etAmt.setText(String.valueOf(r.amount));
        etAmt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etAmt.setBackgroundResource(R.drawable.input_outline);
        etAmt.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        etAmt.setLayoutParams(lp);
        layout.addView(etAmt);

        new AlertDialog.Builder(this)
                .setTitle("Paycheck Due Reminder")
                .setView(layout)
                .setPositiveButton("Save & Next", (dialog, which) -> {
                    String amtStr = etAmt.getText().toString().trim();
                    double amt;
                    if (amtStr.isEmpty()) {
                        Toast.makeText(this, "Please enter an amount or skip", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        amt = Double.parseDouble(amtStr);
                    } catch (Exception e) {
                        Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Save as manual transaction
                    String dateDisplay = dateFormat.format(new Date());
                    Transaction t = ManualTransactionStore.createTransaction(
                            r.merchant, amt, System.currentTimeMillis(), dateDisplay,
                            r.category, Transaction.Type.INCOMING, r.notes
                    );
                    manualStore.save(t);

                    // Update reminder next date
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTimeInMillis(System.currentTimeMillis());
                    cal.add(java.util.Calendar.DAY_OF_YEAR, r.intervalDays);
                    r.nextReminderDateMillis = cal.getTimeInMillis();
                    r.amount = amt;
                    paycheckReminderStore.update(r);

                    Toast.makeText(this, "Paycheck saved! Next reminder in " + intervalLabel, Toast.LENGTH_SHORT).show();
                    fetchAndShowTransactions();
                })
                .setNeutralButton("Skip / Later", (dialog, which) -> {
                    // Reschedule to tomorrow
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTimeInMillis(System.currentTimeMillis());
                    cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
                    r.nextReminderDateMillis = cal.getTimeInMillis();
                    paycheckReminderStore.update(r);
                    Toast.makeText(this, "Reminder snoozed to tomorrow", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Remove Reminder", (dialog, which) -> {
                    paycheckReminderStore.delete(r.id);
                    Toast.makeText(this, "Reminder removed", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // ════════════════════════════════════════════════════════════════
    // TRANSACTION CACHE
    // ════════════════════════════════════════════════════════════════

    private void saveTransactionsToCache(List<Transaction> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Transaction t : list) {
                JSONObject o = new JSONObject();
                o.put("merchant", t.getMerchant());
                o.put("amount", t.getAmount());
                o.put("dateMillis", t.getDateMillis());
                o.put("dateDisplay", t.getDateDisplay());
                o.put("category", t.getCategory());
                o.put("avatarLetter", String.valueOf(t.getAvatarLetter()));
                o.put("type", t.getType() == Transaction.Type.INCOMING ? "incoming" : "outgoing");
                o.put("senderEmail", t.getSenderEmail() != null ? t.getSenderEmail() : "");
                o.put("subject", t.getSubject() != null ? t.getSubject() : "");
                o.put("messageId", t.getMessageId() != null ? t.getMessageId() : "");
                o.put("rawVendor", t.getRawVendor() != null ? t.getRawVendor() : "");
                arr.put(o);
            }
            settingsPrefs.edit().putString("cached_transactions", arr.toString()).apply();
            settingsPrefs.edit().putLong("last_fetch_time", System.currentTimeMillis()).apply();
        } catch (Exception ignored) {}
    }

    private List<Transaction> loadTransactionsFromCache() {
        String json = settingsPrefs.getString("cached_transactions", null);
        if (json == null) return null;
        try {
            JSONArray arr = new JSONArray(json);
            List<Transaction> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String type = o.optString("type", "outgoing");
                String avatarStr = o.optString("avatarLetter", "?");
                Transaction t = new Transaction(
                        o.optString("merchant", ""),
                        o.optDouble("amount", 0),
                        o.optLong("dateMillis", System.currentTimeMillis()),
                        o.optString("dateDisplay", ""),
                        o.optString("category", "Other"),
                        avatarStr.isEmpty() ? '?' : avatarStr.charAt(0),
                        "incoming".equals(type) ? Transaction.Type.INCOMING : Transaction.Type.OUTGOING,
                        o.optString("senderEmail", null),
                        o.optString("subject", null),
                        o.optString("messageId", null),
                        o.optString("rawVendor", null)
                );
                list.add(t);
            }
            return list;
        } catch (Exception e) {
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════════
    // BUDGET GOALS persistence
    // ════════════════════════════════════════════════════════════════

    private Map<String, Double> loadBudgetDefs() {
        String json = settingsPrefs.getString("budget_defs", null);
        if (json == null) {
            Map<String, Double> defaults = new HashMap<>();
            defaults.put("Food & Dining", 600.0);
            defaults.put("Shopping", 300.0);
            defaults.put("Transportation", 150.0);
            defaults.put("Entertainment", 100.0);
            saveBudgetDefs(defaults);
            return defaults;
        }
        Map<String, Double> result = new HashMap<>();
        try {
            JSONObject obj = new JSONObject(json);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                result.put(k, obj.getDouble(k));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private void saveBudgetDefs(Map<String, Double> defs) {
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, Double> e : defs.entrySet()) {
                obj.put(e.getKey(), e.getValue());
            }
            settingsPrefs.edit().putString("budget_defs", obj.toString()).apply();
        } catch (Exception ignored) {}
    }

    // ════════════════════════════════════════════════════════════════
    // EXCLUDED SUBSCRIPTIONS persistence
    // ════════════════════════════════════════════════════════════════

    private Set<String> loadExcludedSubscriptions() {
        String json = settingsPrefs.getString("excluded_subscriptions", "[]");
        Set<String> set = new HashSet<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                set.add(arr.getString(i).toLowerCase().trim());
            }
        } catch (Exception ignored) {}
        return set;
    }

    private void saveExcludedSubscription(String merchant) {
        Set<String> excluded = loadExcludedSubscriptions();
        excluded.add(merchant.toLowerCase().trim());
        try {
            JSONArray arr = new JSONArray();
            for (String s : excluded) arr.put(s);
            settingsPrefs.edit().putString("excluded_subscriptions", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

}

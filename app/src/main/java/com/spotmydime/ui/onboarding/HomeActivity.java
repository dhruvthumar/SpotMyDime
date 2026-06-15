package com.spotmydime.ui.onboarding;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
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

    private int selectedTab = 0;
    private final int[] navIds = {
            R.id.nav_home, R.id.nav_document, R.id.nav_add,
            R.id.nav_gallery, R.id.nav_settings
    };
    private final int[] iconIds = {
            R.id.ic_nav_home, R.id.ic_nav_document, R.id.ic_nav_add,
            R.id.ic_nav_gallery, R.id.ic_nav_settings
    };
    private final int navOrange = 0xFFF9AC54;
    private final int navWhite  = 0xFFFFFFFF;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Initialize the Gemini AI API key from resources
        GeminiClassifier.apiKey = getString(R.string.generative_api_key);

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

        if (index > 2) {
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

                    // Deduplicate: same sender/merchant + same date + same amount = duplicate
                    Set<String> seen = new HashSet<>();
                    List<Transaction> deduped = new ArrayList<>();
                    for (Transaction tx : merged) {
                        String dedupKey = (tx.getSenderEmail() != null ? tx.getSenderEmail() : tx.getMerchant())
                                + "|" + tx.getDateMillis() + "|" + tx.getAmount();
                        if (!seen.contains(dedupKey)) {
                            seen.add(dedupKey);
                            deduped.add(tx);
                        }
                    }
                    allTransactions = deduped;
                    if (merged.isEmpty()) {
                        addEmptyState();
                    } else {
                        populateDashboard(merged);
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

package com.spotmydime.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.spotmydime.R;
import com.spotmydime.data.VendorAliasStore;
import com.spotmydime.data.VendorStore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private LinearLayout containerMain;
    private LinearLayout containerSubscriptions;
    private LinearLayout containerBudgetGoals;
    private LinearLayout containerNicknames;
    private LinearLayout containerCategories;
    private LinearLayout containerEditCategory;
    private LinearLayout containerAutoTracking;
    private LinearLayout containerMailScanning;

    private VendorStore vendorStore;
    private VendorAliasStore aliasStore;

    // In-memory data for stores that don't exist yet
    private final Map<String, Double> budgets = new HashMap<>();
    private final List<Map<String, String>> subscriptions = new ArrayList<>();
    private final Set<String> trackedSenders = new HashSet<>();
    private SharedPreferences settingsPrefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settingsPrefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE);
        vendorStore = new VendorStore(this);
        aliasStore = new VendorAliasStore(this);

        containerMain = findViewById(R.id.container_settings_main);
        containerSubscriptions = findViewById(R.id.container_settings_subscriptions);
        containerBudgetGoals = findViewById(R.id.container_settings_budget_goals);
        containerNicknames = findViewById(R.id.container_settings_merchant_nicknames);
        containerCategories = findViewById(R.id.container_settings_categories);
        containerEditCategory = findViewById(R.id.container_settings_edit_category);
        containerAutoTracking = findViewById(R.id.container_settings_auto_tracking);
        containerMailScanning = findViewById(R.id.container_settings_mail_scanning);

        setupProfile();
        setupMainMenu();
        setupBackButtons();
        setupEditCategory();
        setupColorPicker();

        showScreen(containerMain);
    }

    private void setupProfile() {
        String userName = getIntent().getStringExtra("user_name");
        String userEmail = getIntent().getStringExtra("user_email");

        if (userName == null || userName.isEmpty()) userName = "User Name";
        if (userEmail == null || userEmail.isEmpty()) userEmail = "user@email.com";

        ((TextView) findViewById(R.id.tv_settings_name)).setText(userName);
        ((TextView) findViewById(R.id.tv_settings_email)).setText(userEmail);

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

    private void setupMainMenu() {
        findViewById(R.id.btn_settings_subscriptions).setOnClickListener(v -> {
            loadSubscriptions();
            showScreen(containerSubscriptions);
        });
        findViewById(R.id.btn_settings_budget_goals).setOnClickListener(v -> {
            loadBudgetGoals();
            showScreen(containerBudgetGoals);
        });
        findViewById(R.id.btn_settings_merchant_nicknames).setOnClickListener(v -> {
            loadNicknames();
            showScreen(containerNicknames);
        });
        findViewById(R.id.btn_settings_categories).setOnClickListener(v -> {
            loadCategories();
            showScreen(containerCategories);
        });
        findViewById(R.id.btn_settings_auto_tracking).setOnClickListener(v -> {
            loadAutoTracking();
            showScreen(containerAutoTracking);
        });
        findViewById(R.id.btn_settings_mail_scanning).setOnClickListener(v -> {
            loadMailScanning();
            showScreen(containerMailScanning);
        });
        findViewById(R.id.btn_settings_clear_data).setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Clear All Data?")
                    .setMessage("This will delete all transactions, categories, and preferences. This cannot be undone.")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        getSharedPreferences("vendor_categories", Context.MODE_PRIVATE).edit().clear().apply();
                        getSharedPreferences("vendor_aliases", Context.MODE_PRIVATE).edit().clear().apply();
                        getSharedPreferences("excluded_messages", Context.MODE_PRIVATE).edit().clear().apply();
                        getSharedPreferences("manual_transactions", Context.MODE_PRIVATE).edit().clear().apply();
                        getSharedPreferences("settings_prefs", Context.MODE_PRIVATE).edit().clear().apply();
                        budgets.clear();
                        subscriptions.clear();
                        trackedSenders.clear();
                        Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void setupBackButtons() {
        findViewById(R.id.btn_settings_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_subscriptions_back).setOnClickListener(v -> showScreen(containerMain));
        findViewById(R.id.btn_budget_back).setOnClickListener(v -> showScreen(containerMain));
        findViewById(R.id.btn_nicknames_back).setOnClickListener(v -> showScreen(containerMain));
        findViewById(R.id.btn_categories_back).setOnClickListener(v -> showScreen(containerMain));
        findViewById(R.id.btn_edit_category_back).setOnClickListener(v -> showScreen(containerCategories));
        findViewById(R.id.btn_auto_tracking_back).setOnClickListener(v -> showScreen(containerMain));
        findViewById(R.id.btn_mail_scanning_back).setOnClickListener(v -> showScreen(containerMain));
    }

    private void showScreen(LinearLayout target) {
        containerMain.setVisibility(target == containerMain ? View.VISIBLE : View.GONE);
        containerSubscriptions.setVisibility(target == containerSubscriptions ? View.VISIBLE : View.GONE);
        containerBudgetGoals.setVisibility(target == containerBudgetGoals ? View.VISIBLE : View.GONE);
        containerNicknames.setVisibility(target == containerNicknames ? View.VISIBLE : View.GONE);
        containerCategories.setVisibility(target == containerCategories ? View.VISIBLE : View.GONE);
        containerEditCategory.setVisibility(target == containerEditCategory ? View.VISIBLE : View.GONE);
        containerAutoTracking.setVisibility(target == containerAutoTracking ? View.VISIBLE : View.GONE);
        containerMailScanning.setVisibility(target == containerMailScanning ? View.VISIBLE : View.GONE);
    }

    // ── SUBSCRIPTIONS ──

    private void loadSubscriptions() {
        LinearLayout list = findViewById(R.id.container_subscriptions_list);
        list.removeAllViews();

        if (subscriptions.isEmpty()) {
            // Populate with sample data from transactions
            subscriptions.add(createSub("Netflix", 15.99, "Monthly", "June 15, 2026"));
            subscriptions.add(createSub("Spotify", 9.99, "Monthly", "June 21, 2026"));
            subscriptions.add(createSub("iCloud+", 2.99, "Monthly", "June 8, 2026"));
            subscriptions.add(createSub("Amazon Prime", 139.00, "Yearly", "March 3, 2027"));
        }

        for (Map<String, String> sub : subscriptions) {
            list.addView(createSubscriptionCard(sub));
        }

        findViewById(R.id.btn_add_subscription).setOnClickListener(v -> {
            Toast.makeText(this, "Add subscription - coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    private Map<String, String> createSub(String name, double amount, String freq, String next) {
        Map<String, String> m = new HashMap<>();
        m.put("name", name);
        m.put("amount", String.format("%.2f", amount));
        m.put("frequency", freq);
        m.put("nextDate", next);
        return m;
    }

    private View createSubscriptionCard(Map<String, String> sub) {
        CardView card = new CardView(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72)));
        card.setCardBackgroundColor(0xFFFFFFFF);
        card.setRadius(dp(16));
        card.setCardElevation(1);
        ((LinearLayout.LayoutParams) card.getLayoutParams()).bottomMargin = dp(10);

        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));

        ImageView icon = new ImageView(this);
        int iconSize = dp(40);
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
        c.drawText(sub.get("name").substring(0, 1).toUpperCase(), iconSize / 2f, iconSize / 2f + y, tp);
        icon.setImageBitmap(bmp);
        row.addView(icon);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(12), 0, 0, 0);

        TextView tvName = new TextView(this);
        tvName.setText(sub.get("name"));
        tvName.setTextSize(15);
        tvName.setTextColor(0xFF000000);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(tvName);

        TextView tvDetail = new TextView(this);
        tvDetail.setText("$" + sub.get("amount") + "/" + sub.get("frequency").toLowerCase()
                + " · Next: " + sub.get("nextDate"));
        tvDetail.setTextSize(12);
        tvDetail.setTextColor(0xFF888888);
        textCol.addView(tvDetail);

        row.addView(textCol);

        card.addView(row);
        return card;
    }

    // ── BUDGET GOALS ──

    private void loadBudgetGoals() {
        LinearLayout list = findViewById(R.id.container_budget_list);
        list.removeAllViews();

        if (budgets.isEmpty()) {
            budgets.put("Food & Dining", 600.0);
            budgets.put("Shopping", 300.0);
            budgets.put("Transportation", 150.0);
            budgets.put("Entertainment", 100.0);
        }

        int[] catColors = {0xFF29B6F6, 0xFFFFA726, 0xFFE53935, 0xFF26A69A};
        int ci = 0;
        for (Map.Entry<String, Double> entry : budgets.entrySet()) {
            list.addView(createBudgetCard(entry.getKey(), entry.getValue(), catColors[ci % catColors.length]));
            ci++;
        }

        findViewById(R.id.btn_add_budget).setOnClickListener(v -> {
            Toast.makeText(this, "Add budget goal - coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    private View createBudgetCard(String category, double budget, int color) {
        CardView card = new CardView(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(80)));
        card.setCardBackgroundColor(0xFFFFFFFF);
        card.setRadius(dp(16));
        card.setCardElevation(1);
        ((LinearLayout.LayoutParams) card.getLayoutParams()).bottomMargin = dp(10);

        LinearLayout inner = new LinearLayout(this);
        inner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        topRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvCat = new TextView(this);
        tvCat.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
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

        inner.addView(topRow);

        // Progress bar
        LinearLayout barOuter = new LinearLayout(this);
        int barHeight = dp(10);
        barOuter.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, barHeight));
        barOuter.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, barHeight));
        android.graphics.drawable.GradientDrawable bgShape = new android.graphics.drawable.GradientDrawable();
        bgShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bgShape.setCornerRadius(barHeight / 2f);
        bgShape.setColor(0xFFF0E8D5);
        barOuter.setBackground(bgShape);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            barOuter.setClipToOutline(true);
        }

        double spent = 0;
        // Simulate some spend
        if (category.equals("Food & Dining")) spent = budget * 0.72;
        else if (category.equals("Shopping")) spent = budget * 0.45;
        else if (category.equals("Transportation")) spent = budget * 0.3;
        else if (category.equals("Entertainment")) spent = budget * 0.9;

        float fillPct = (float) Math.min(spent / budget, 1.0);
        View fill = new View(this);
        fill.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, fillPct));
        fill.setBackgroundColor(color);
        barOuter.addView(fill);

        inner.addView(barOuter);

        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setPadding(0, dp(4), 0, 0);

        TextView tvSpent = new TextView(this);
        tvSpent.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
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

        inner.addView(bottomRow);

        card.addView(inner);
        return card;
    }

    // ── MERCHANT NICKNAMES ──

    private void loadNicknames() {
        LinearLayout list = findViewById(R.id.container_nicknames_list);
        list.removeAllViews();

        Map<String, String> aliases = aliasStore.getAll();
        if (aliases.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No merchant nicknames set yet.\nTap + to add one.");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            list.addView(empty);
        } else {
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                list.addView(createNicknameCard(entry.getKey(), entry.getValue()));
            }
        }

        findViewById(R.id.btn_add_nickname).setOnClickListener(v -> showAddNicknameDialog());
    }

    private View createNicknameCard(String original, String alias) {
        CardView card = new CardView(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));
        card.setCardBackgroundColor(0xFFFFFFFF);
        card.setRadius(dp(16));
        card.setCardElevation(1);
        ((LinearLayout.LayoutParams) card.getLayoutParams()).bottomMargin = dp(8);

        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(8), dp(16), dp(8));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
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
        btnDelete.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnDelete.setClickable(true);
        btnDelete.setFocusable(true);
        btnDelete.setOnClickListener(v -> {
            aliasStore.setAlias(original, null);
            loadNicknames();
            Toast.makeText(this, "Nickname removed", Toast.LENGTH_SHORT).show();
        });
        row.addView(btnDelete);

        card.addView(row);
        return card;
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

        android.view.ViewGroup.MarginLayoutParams params =
                (android.view.ViewGroup.MarginLayoutParams) etOriginal.getLayoutParams();
        if (params != null) params.bottomMargin = dp(12);
        else {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(12);
            etOriginal.setLayoutParams(lp);
        }

        final EditText etAlias = new EditText(this);
        etAlias.setHint("Nickname to display");
        etAlias.setTextSize(14);
        etAlias.setBackgroundResource(R.drawable.input_outline);
        etAlias.setPadding(dp(16), dp(12), dp(16), dp(12));
        layout.addView(etAlias);

        new android.app.AlertDialog.Builder(this)
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
                    loadNicknames();
                    Toast.makeText(this, "Nickname saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── CATEGORIES ──

    private final String[] allCategories = {
            "Food & Dining", "Shopping", "Subscriptions", "Transportation",
            "Bills & Utilities", "Entertainment", "Health", "Interac Sent",
            "Interac Received", "Transfers", "Travel", "Other"
    };

    private final int[] categoryColors = {
            0xFF29B6F6, 0xFFFFA726, 0xFF8E24AA, 0xFFE53935,
            0xFF5C6BC0, 0xFF26A69A, 0xFF4CAF50, 0xFFEF5350,
            0xFF66BB6A, 0xFF42A5F5, 0xFFFF7043, 0xFF757575
    };

    private void loadCategories() {
        LinearLayout list = findViewById(R.id.container_categories_list);
        list.removeAllViews();

        for (int i = 0; i < allCategories.length; i++) {
            final int idx = i;
            CardView card = new CardView(this);
            card.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));
            card.setCardBackgroundColor(0xFFFFFFFF);
            card.setRadius(dp(16));
            card.setCardElevation(1);
            ((LinearLayout.LayoutParams) card.getLayoutParams()).bottomMargin = dp(8);
            card.setClickable(true);
            card.setFocusable(true);
            card.setForeground(ContextCompat.getDrawable(this, R.drawable.input_outline));
            card.setOnClickListener(v -> openEditCategory(allCategories[idx], categoryColors[idx]));

            LinearLayout row = new LinearLayout(this);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(8), dp(16), dp(8));

            // Color dot
            ImageView dot = new ImageView(this);
            int dotSize = dp(28);
            dot.setLayoutParams(new LinearLayout.LayoutParams(dotSize, dotSize));
            Bitmap bmp = Bitmap.createBitmap(dotSize, dotSize, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(categoryColors[idx]);
            c.drawCircle(dotSize / 2f, dotSize / 2f, dotSize / 2f, p);
            dot.setImageBitmap(bmp);
            row.addView(dot);

            TextView tvName = new TextView(this);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvName.setText(allCategories[idx]);
            tvName.setTextSize(15);
            tvName.setTextColor(0xFF000000);
            tvName.setPadding(dp(12), 0, 0, 0);
            row.addView(tvName);

            TextView tvArrow = new TextView(this);
            tvArrow.setText("›");
            tvArrow.setTextColor(0xFFD4A373);
            tvArrow.setTextSize(18);
            row.addView(tvArrow);

            card.addView(row);
            list.addView(card);
        }
    }

    // ── EDIT CATEGORY ──

    private String editingCategoryName;
    private int editingCategoryColor;

    private void openEditCategory(String name, int color) {
        editingCategoryName = name;
        editingCategoryColor = color;

        ((EditText) findViewById(R.id.et_edit_category_name)).setText(name);
        highlightColorInPicker(color);
        showScreen(containerEditCategory);
    }

    private void setupEditCategory() {
        findViewById(R.id.btn_edit_category_save).setOnClickListener(v -> {
            String newName = ((EditText) findViewById(R.id.et_edit_category_name))
                    .getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "Category name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            // In a real app, persist this. For now just toast.
            Toast.makeText(this, "Category updated to: " + newName, Toast.LENGTH_SHORT).show();
            loadCategories();
            showScreen(containerCategories);
        });
    }

    private void setupColorPicker() {
        LinearLayout container = findViewById(R.id.container_color_picker);
        int[] colors = {
                0xFF29B6F6, 0xFFFFA726, 0xFF8E24AA, 0xFFE53935,
                0xFF5C6BC0, 0xFF26A69A, 0xFF4CAF50, 0xFFEF5350,
                0xFF66BB6A, 0xFF42A5F5, 0xFFFF7043, 0xFF757575
        };
        container.removeAllViews();
        for (int color : colors) {
            final int c = color;
            ImageView dot = new ImageView(this);
            int size = dp(36);
            int margin = dp(6);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);
            dot.setTag(color);

            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(color);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, p);
            dot.setImageBitmap(bmp);

            dot.setClickable(true);
            dot.setFocusable(true);
            dot.setOnClickListener(v -> {
                editingCategoryColor = c;
                highlightColorInPicker(c);
            });

            container.addView(dot);
        }
    }

    private void highlightColorInPicker(int selectedColor) {
        LinearLayout container = findViewById(R.id.container_color_picker);
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof ImageView) {
                int color = (int) child.getTag();
                int size = dp(36);
                Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bmp);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(color);
                if (color == selectedColor) {
                    // Draw a slightly larger ring first
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

    // ── AUTO-TRACKING ──

    private void loadAutoTracking() {
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
        switchSync.setOnCheckedChangeListener((buttonView, isChecked) ->
                settingsPrefs.edit().putBoolean("auto_sync", isChecked).apply());
        switchScan.setOnCheckedChangeListener((buttonView, isChecked) ->
                settingsPrefs.edit().putBoolean("email_scanning", isChecked).apply());

        tvFrequency.setOnClickListener(v -> {
            String[] options = {"Every 30 min", "Every 1 hour", "Every 3 hours", "Every 6 hours", "Daily"};
            int checked = 0;
            String current = tvFrequency.getText().toString();
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(current)) { checked = i; break; }
            }
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Scan Frequency")
                    .setSingleChoiceItems(options, checked, (dialog, which) -> {
                        tvFrequency.setText(options[which]);
                        settingsPrefs.edit().putString("scan_frequency", options[which]).apply();
                        dialog.dismiss();
                    })
                    .show();
        });
    }

    // ── MAIL SCANNING ──

    private void loadMailScanning() {
        LinearLayout list = findViewById(R.id.container_senders_list);
        list.removeAllViews();

        if (trackedSenders.isEmpty()) {
            trackedSenders.add("noreply@amazon.com");
            trackedSenders.add("payment@netflix.com");
            trackedSenders.add("no-reply@spotify.com");
            trackedSenders.add("billing@apple.com");
        }

        for (String sender : trackedSenders) {
            list.addView(createSenderCard(sender));
        }

        findViewById(R.id.btn_add_sender).setOnClickListener(v -> showAddSenderDialog());
    }

    private View createSenderCard(String sender) {
        CardView card = new CardView(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));
        card.setCardBackgroundColor(0xFFFFFFFF);
        card.setRadius(dp(16));
        card.setCardElevation(1);
        ((LinearLayout.LayoutParams) card.getLayoutParams()).bottomMargin = dp(8);

        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(8), dp(16), dp(8));

        ImageView icon = new ImageView(this);
        int size = dp(32);
        icon.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFF9A84D);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(0xFFFFFFFF);
        tp.setTextSize(size * 0.35f);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setFakeBoldText(true);
        float y = -(tp.descent() + tp.ascent()) / 2f;
        c.drawText("@", size / 2f, size / 2f + y, tp);
        icon.setImageBitmap(bmp);
        row.addView(icon);

        TextView tvSender = new TextView(this);
        tvSender.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvSender.setText(sender);
        tvSender.setTextSize(14);
        tvSender.setTextColor(0xFF000000);
        tvSender.setPadding(dp(12), 0, 0, 0);
        row.addView(tvSender);

        TextView btnRemove = new TextView(this);
        btnRemove.setText("Remove");
        btnRemove.setTextSize(12);
        btnRemove.setTextColor(0xFFE53935);
        btnRemove.setPadding(dp(8), dp(4), dp(8), dp(4));
        btnRemove.setClickable(true);
        btnRemove.setFocusable(true);
        btnRemove.setOnClickListener(v -> {
            trackedSenders.remove(sender);
            loadMailScanning();
            Toast.makeText(this, "Sender removed", Toast.LENGTH_SHORT).show();
        });
        row.addView(btnRemove);

        card.addView(row);
        return card;
    }

    private void showAddSenderDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(16));

        final EditText etSender = new EditText(this);
        etSender.setHint("sender@email.com");
        etSender.setTextSize(14);
        etSender.setBackgroundResource(R.drawable.input_outline);
        etSender.setPadding(dp(16), dp(12), dp(16), dp(12));
        layout.addView(etSender);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Add Sender")
                .setView(layout)
                .setPositiveButton("Add", (dialog, which) -> {
                    String sender = etSender.getText().toString().trim();
                    if (sender.isEmpty()) {
                        Toast.makeText(this, "Please enter an email", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    trackedSenders.add(sender);
                    loadMailScanning();
                    Toast.makeText(this, "Sender added", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}

package com.spotmydime.data;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransactionParser {

    // Matches amounts WITH cents: $12.99, $1,200.50
    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("\\$\\s*([0-9]+(?:,[0-9]{3})*\\.[0-9]{2})");

    // Matches whole-dollar amounts with NO cents: $150, $1,200
    // (kept separate from AMOUNT_PATTERN so the decimal version is always
    // tried first — it's more specific and less likely to misfire on things
    // like phone numbers or order IDs prefixed with $, which whole-number
    // matching is more prone to)
    private static final Pattern WHOLE_DOLLAR_PATTERN =
            Pattern.compile("\\$\\s*([0-9]+(?:,[0-9]{3})*)(?!\\.[0-9])\\b");

    private static final Pattern CURRENCY_AMOUNT =
            Pattern.compile("(?:total|amount|charged|paid|sum|subtotal|grand total|due|cost|price|spent|payment|balance|fee|sale)\\s*[:\\s]*\\$?\\s*([0-9]+(?:,[0-9]{3})*\\.[0-9]{2})",
                    Pattern.CASE_INSENSITIVE);

    // Same keyword set but for whole-dollar amounts (no cents)
    private static final Pattern CURRENCY_AMOUNT_WHOLE =
            Pattern.compile("(?:total|amount|charged|paid|sum|subtotal|grand total|due|cost|price|spent|payment|balance|fee|sale)\\s*[:\\s]*\\$?\\s*([0-9]+(?:,[0-9]{3})*)(?!\\.[0-9])\\b",
                    Pattern.CASE_INSENSITIVE);

    // Date fallback patterns, used only when the Gemini date is unavailable.
    // Covers the formats seen in real transaction emails:
    //   "on Jun 19, 2026"          "dated: 2026-06-19"
    //   "Next billing date: July 21, 2026"   "Jun 1 - Jun 15, 2026" (takes the later date)
    private static final Pattern DATE_ISO =
            Pattern.compile("\\b(20[0-9]{2}-[01][0-9]-[0-3][0-9])\\b");

    private static final Pattern DATE_MONTH_NAME =
            Pattern.compile(
                    "\\b(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|" +
                    "Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)" +
                    "\\.?\\s+([0-3]?[0-9]),?\\s+(20[0-9]{2})\\b",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern TX_KEYWORDS = Pattern.compile(
            "(?:" +
            "(?:receipt|purchase|invoice|transaction)" +
            "|order\\s*(?:confirmation|summary|received|placed|#|shipped|details|from)" +
            "|your\\s*(?:order|receipt|invoice|payment|bill|transaction|purchase|subscription)" +
            "|you\\s*(?:spent|paid|purchased|bought|sent)" +
            "|paid\\s*(?:you|from|to|via)" +
            "|charged|billed|refund|credit" +
            "|bill(?:ing)?\\s*(?:statement|notification)" +
            "|statement|subscription|renewal|recurring" +
            "|booking|reservation|dispatch|shipping\\s*(?:confirmation|update)" +
            "|delivery\\s*(?:update|confirmation|notification)" +
            "|e[- ]?ticket|ticket\\s*(?:purchase|receipt|confirmation)" +
            "|checkout\\s*(?:receipt|confirmation|summary)" +
            "|monthly\\s*(?:statement|charge|fee|payment)" +
            "|annual\\s*(?:fee|charge|payment|renewal)" +
            "|top.up|reload" +
            "|withdrawal|deposit|auto.?pay|direct.?debit" +
            "|apple\\s*pay|google\\s*pay" +
            ")",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NEGATIVE_KEYWORDS = Pattern.compile(
            "(?:" +
            "(?:verification|verify|security\\s*(?:alert|code|update|notice))" +
            "|sign\\s*(?:in|up|on)" +
            "|login|log\\s*in|password|reset|2[- ]?fa|two[- ]?factor" +
            "|unusual\\s*(?:sign|activity|login)" +
            "|suspicious|unauthori[sz]ed" +
            "|code\\s*(?:is|for|from|to)" +
            "|welcome\\s*(?:to|aboard|from)" +
            "|newsletter|unsubscribe|weekly\\s*digest" +
            "|recommend|suggestion|trending" +
            "|invitation|invite\\s*(?:you|to)" +
            "|promo|promotion|discount|coupon|offer" +
            "|get\\s*(?:\\$|free|your|started)" +
            "|save\\s*(?:up|\\$|on|big)" +
            "|don't\\s*miss|limited\\s*time|exclusive" +
            "|gift\\s*(?:card|idea|guide|for)" +
            "|referral|refer\\s*(?:a|your|and)" +
            ")",
            Pattern.CASE_INSENSITIVE);

    private static final String[][] MERCHANT_RULES = {
        {"amazon",        "Amazon"},
        {"paypal",        "PayPal"},
        {"uber eats",     "Uber Eats"},
        {"ubereats",      "Uber Eats"},
        {"uber",          "Uber"},
        {"netflix",       "Netflix"},
        {"spotify",       "Spotify"},
        {"starbucks",     "Starbucks"},
        {"mcdonald",      "McDonald's"},
        {"doordash",      "DoorDash"},
        {"walmart",       "Walmart"},
        {"costco",        "Costco"},
        {"target",        "Target"},
        {"best buy",      "Best Buy"},
        {"bestbuy",       "Best Buy"},
        {"airbnb",        "Airbnb"},
        {"lyft",          "Lyft"},
        {"google",        "Google"},
        {"apple",         "Apple"},
        {"tim horton",    "Tim Hortons"},
        {"esso",          "Esso"},
        {"shell",         "Shell"},
        {"petro",         "Petro-Canada"},
        {"steam",         "Steam"},
        {"hbo",           "HBO"},
        {"disney",        "Disney+"},
        {"southwest",     "Southwest"},
        {"delta",         "Delta"},
        {"canada post",   "Canada Post"},
        {"nike",          "Nike"},
        {"wish",          "Wish"},
        {"ebay",          "eBay"},
        {"etsy",          "Etsy"},
        {"hulu",          "Hulu"},
        {"homedepot",     "Home Depot"},
        {"home depot",    "Home Depot"},
        {"lowes",         "Lowe's"},
        {"ikea",          "IKEA"},
        {"chipotle",      "Chipotle"},
        {"subway",        "Subway"},
        {"pizza hut",     "Pizza Hut"},
        {"dominos",       "Domino's"},
        {"kfc",           "KFC"},
        {"taco bell",     "Taco Bell"},
        {"burger king",   "Burger King"},
        {"wendys",        "Wendy's"},
        {"dunkin",        "Dunkin'"},
        {"popeyes",       "Popeyes"},
        {"adidas",        "Adidas"},
        {"shopify",       "Shopify"},
        {"stripe",        "Stripe"},
        {"square",        "Square"},
        {"venmo",         "Venmo"},
        {"cashapp",       "Cash App"},
        {"expedia",       "Expedia"},
        {"booking.com",   "Booking.com"},
        {"hotels.com",    "Hotels.com"},
        {"kayak",         "Kayak"},
        {"united",        "United Airlines"},
        {"american airlines","American Airlines"},
        {"via rail",      "Via Rail"},
        {"amtrak",        "Amtrak"},
        {"bp",            "BP"},
        {"chevron",       "Chevron"},
        {"exxon",         "Exxon"},
        {"mobil",         "Mobil"},
        {"7-eleven",      "7-Eleven"},
        {"7eleven",       "7-Eleven"},
        {"safeway",       "Safeway"},
        {"loblaws",       "Loblaws"},
        {"metro",         "Metro"},
        {"shoppers",      "Shoppers Drug Mart"},
        {"winners",       "Winners"},
        {"h&m",           "H&M"},
        {"zara",          "Zara"},
        {"gap",           "Gap"},
        {"old navy",      "Old Navy"},
        {"cvs",           "CVS"},
        {"walgreens",     "Walgreens"},
        {"microsoft",     "Microsoft"},
        {"adobe",         "Adobe"},
        {"dropbox",       "Dropbox"},
        {"discord",       "Discord"},
        {"twitch",        "Twitch"},
        {"patreon",       "Patreon"},
    };

    private TransactionParser() {}

    public static String extractMerchantName(String subject, String snippet, String body) {
        String lcSubject = subject != null ? subject.toLowerCase(Locale.US) : "";
        String lcSnippet = snippet != null ? snippet.toLowerCase(Locale.US) : "";
        String lcBody = body != null ? body.toLowerCase(Locale.US) : "";

        String merchant = extractMerchant(lcSubject, lcSnippet, lcBody);
        if (merchant != null) return merchant;

        // Try just subject-based patterns without snippet/body
        Pattern fromPattern = Pattern.compile(
                "(?:from|at|via)\\s+([a-z0-9'\\s.-]{2,30}?)(?:\\s+for|\\s+on|\\s+using|\\s+with|,|$|\\.)");
        Matcher m = fromPattern.matcher(lcSubject);
        if (m.find()) {
            String name = m.group(1).trim();
            if (name.length() >= 2
                    && !name.matches(".*(?:your|the|an?|order|receipt|payment|invoice|confirmation).*")) {
                return capitalizeWords(name);
            }
        }
        return null;
    }

    public static Transaction tryParse(String subject, String snippet,
                                        String fullBody, long internalDate) {
        String lcSubject = subject != null ? subject.toLowerCase(Locale.US) : "";
        String lcSnippet = snippet != null ? snippet.toLowerCase(Locale.US) : "";
        String lcBody = fullBody != null ? fullBody.toLowerCase(Locale.US) : "";

        if (!isLikelyTransaction(lcSubject, lcSnippet, lcBody)) return null;

        String merchant = extractMerchant(lcSubject, lcSnippet, lcBody);
        if (merchant == null) return null;

        String searchText = subject + "\n" + (fullBody != null ? fullBody : snippet);
        double amount = extractAmount(searchText);
        if (amount <= 0) return null;

        String category = guessCategory(merchant);
        char avatar = merchant.charAt(0);
        String dateDisplay = formatDate(internalDate);

        return new Transaction(merchant, amount, internalDate, dateDisplay, category, avatar, Transaction.Type.OUTGOING);
    }

    private static boolean isLikelyTransaction(String lcSubject, String lcSnippet, String lcBody) {
        if (NEGATIVE_KEYWORDS.matcher(lcSubject).find()) return false;

        if (TX_KEYWORDS.matcher(lcSubject).find()) return true;

        return false;
    }

    // Generic/noise words to strip from the edges of a subject-prefix candidate
    // before treating it as a merchant name — e.g. "Spotify Premium" -> "Spotify",
    // "DoorDash Delivery" -> "DoorDash". Deliberately conservative: only strips
    // from the START or END of the candidate, never removes words from the middle,
    // so multi-word merchant names like "Tim Hortons" or "Best Buy" stay intact.
    private static final java.util.Set<String> SUBJECT_NOISE_WORDS = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "payment", "order", "receipt", "confirmation", "invoice", "statement",
                    "billing", "subscription", "notification", "update", "purchase",
                    "premium", "delivery", "monthly", "your", "annual", "weekly", "mobile"));

    // Words/fragments that should never be treated as (part of) a merchant name —
    // catches false positives like "your one-time password" or "re: fw:" chains.
    private static final java.util.Set<String> SUBJECT_REJECT_WORDS = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "one", "the", "a", "an", "is", "code", "password", "verify",
                    "verification", "re", "re:", "fw", "fw:", "fwd", "fwd:"));

    private static String stripNoiseWords(String s) {
        String[] words = s.trim().split("\\s+");
        int start = 0, end = words.length;
        while (start < end && SUBJECT_NOISE_WORDS.contains(words[start].toLowerCase(Locale.US))) start++;
        while (end > start && SUBJECT_NOISE_WORDS.contains(words[end - 1].toLowerCase(Locale.US))) end--;
        if (start >= end) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(words[i]);
        }
        return sb.toString();
    }

    /**
     * A candidate is only treated as a possible merchant name if it contains
     * at least one alphabetic word of length >= 3 that isn't a reject word.
     * Filters out things like "482193", "is", "re: fw:" that would otherwise
     * slip through as nonsense "merchant names".
     */
    private static boolean isValidMerchantCandidate(String s) {
        if (s == null || s.isEmpty()) return false;
        String[] words = s.toLowerCase(Locale.US).split("\\s+");
        for (String w : words) {
            String wClean = w.replaceAll("[^a-z0-9']", "");
            if (wClean.isEmpty() || SUBJECT_REJECT_WORDS.contains(wClean)) continue;
            if (wClean.matches("[0-9]+")) continue;
            if (wClean.length() >= 3 && wClean.matches(".*[a-z].*")) return true;
        }
        return false;
    }

    // Dash must be surrounded by whitespace to count as a subject separator —
    // otherwise "one-time" or "e-transfer" would be misread as a dash-split point.
    private static final Pattern SUBJECT_DASH_SPLIT =
            Pattern.compile("^(.+?)\\s+[-\u2013\u2014]\\s+(.+)$");

    private static final Pattern SUBJECT_SUFFIX_WORD = Pattern.compile(
            "\\b(?:receipt|order|payment|confirmation|invoice|statement|billing|" +
            "subscription|notification|update|purchase)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Extracts a merchant candidate from common subject-line shapes:
     *   "X - Y"                 -> whichever side of the dash is a valid candidate
     *                              (e.g. "Spotify Premium - Receipt" -> "Spotify",
     *                               "Payment Confirmation - Tim Hortons" -> "Tim Hortons")
     *   "Your X <suffix word>"  -> X (e.g. "Your Uber Receipt" -> "Uber")
     * Returns null if nothing usable is found — caller falls through to the
     * existing looser fromPattern/receiptPattern regexes.
     */
    private static String extractMerchantFromSubjectShape(String lcSubject) {
        if (lcSubject == null || lcSubject.isEmpty()) return null;

        Matcher dashMatcher = SUBJECT_DASH_SPLIT.matcher(lcSubject);
        if (dashMatcher.matches()) {
            String left  = stripNoiseWords(dashMatcher.group(1));
            String right = stripNoiseWords(dashMatcher.group(2));
            boolean leftValid  = isValidMerchantCandidate(left);
            boolean rightValid = isValidMerchantCandidate(right);
            String candidate = null;
            if (leftValid && rightValid) {
                // Both sides plausible — prefer the shorter one (closer to a
                // single brand name), e.g. "Spotify" over "Premium Receipt"
                candidate = (left.split("\\s+").length <= right.split("\\s+").length) ? left : right;
            } else if (leftValid) {
                candidate = left;
            } else if (rightValid) {
                candidate = right;
            }
            if (candidate != null && candidate.length() >= 2 && candidate.length() <= 30) {
                return capitalizeWords(candidate);
            }
        }

        // No dash — try "(Your )Merchant <suffix word>" shape
        Matcher suffixMatcher = SUBJECT_SUFFIX_WORD.matcher(lcSubject);
        if (suffixMatcher.find()) {
            String before = lcSubject.substring(0, suffixMatcher.start()).trim();
            String candidate = stripNoiseWords(before);
            if (isValidMerchantCandidate(candidate) && candidate.length() >= 2 && candidate.length() <= 30) {
                return capitalizeWords(candidate);
            }
        }

        return null;
    }

    private static String extractMerchant(String lcSubject, String lcSnippet, String lcBody) {
        for (String[] rule : MERCHANT_RULES) {
            if (lcSubject.contains(rule[0])) {
                return rule[1];
            }
        }

        for (String[] rule : MERCHANT_RULES) {
            if (lcSnippet.contains(rule[0])) {
                return rule[1];
            }
        }

        // Subject-prefix extraction: catches merchants NOT in the hardcoded
        // MERCHANT_RULES table by reading common subject-line shapes, e.g.
        //   "Spotify Premium - Receipt"          -> "Spotify"
        //   "Tim Hortons Order Confirmation"      -> "Tim Hortons"
        //   "Your Uber Receipt"                   -> "Uber"
        //   "Payment Confirmation - Tim Hortons"   -> "Tim Hortons"
        // Tried before the looser fromPattern/receiptPattern regexes below,
        // since it targets the most common real-world subject shapes more precisely.
        String prefixMerchant = extractMerchantFromSubjectShape(lcSubject);
        if (prefixMerchant != null) return prefixMerchant;

        Pattern fromPattern = Pattern.compile(
                "(?:from|at|via)\\s+([a-z0-9'\\s.-]{2,30}?)(?:\\s+for|\\s+on|\\s+using|\\s+with|,|$|\\.)");
        Matcher m = fromPattern.matcher(lcSubject);
        if (m.find()) {
            String name = m.group(1).trim();
            if (name.length() >= 2
                    && !name.matches(".*(?:your|the|an?|order|receipt|payment|invoice|confirmation).*")) {
                return capitalizeWords(name);
            }
        }

        Pattern receiptPattern = Pattern.compile(
                "(?:receipt|invoice|order|purchase|payment|booking|subscription)\\s+(?:from|for|#\\s*)?\\s*([a-z0-9'\\s.-]{2,30}?)(?:\\.|,|$|\\s+-)",
                Pattern.CASE_INSENSITIVE);
        Matcher rm = receiptPattern.matcher(lcSubject);
        if (rm.find()) {
            String name = rm.group(1).trim();
            if (name.length() >= 2) return capitalizeWords(name);
        }

        return null;
    }

    public static double extractAmount(String text) {
        Matcher currencyMatcher = CURRENCY_AMOUNT.matcher(text);
        if (currencyMatcher.find()) {
            try {
                String raw = currencyMatcher.group(1).replace(",", "");
                double val = Double.parseDouble(raw);
                if (val > 0.5 && val < 100000) return val;
            } catch (NumberFormatException ignored) {}
        }

        Matcher m = AMOUNT_PATTERN.matcher(text);
        while (m.find()) {
            try {
                String raw = m.group(1).replace(",", "");
                double val = Double.parseDouble(raw);
                String before = text.substring(Math.max(0, m.start() - 20), m.start()).toLowerCase(Locale.US);
                if (before.matches(".*\\b(?:total|amount|paid|charged|due|cost|price|subtotal|grand total|sum|sale|spent|payment|balance|fee|\\$)\\b.*")) {
                    if (val > 0.5 && val < 100000) return val;
                } else {
                    if (val > 0.5 && val < 10000) return val;
                }
            } catch (NumberFormatException ignored) {}
        }

        // Fallback: whole-dollar amounts with no cents, e.g. "Total: $150",
        // "Charged $99 to your card". Tried only after decimal-amount patterns
        // find nothing, since decimal amounts are the more specific/reliable signal.
        Matcher currencyWholeMatcher = CURRENCY_AMOUNT_WHOLE.matcher(text);
        if (currencyWholeMatcher.find()) {
            try {
                String raw = currencyWholeMatcher.group(1).replace(",", "");
                double val = Double.parseDouble(raw);
                if (val > 0.5 && val < 100000) return val;
            } catch (NumberFormatException ignored) {}
        }

        Matcher wm = WHOLE_DOLLAR_PATTERN.matcher(text);
        while (wm.find()) {
            try {
                String raw = wm.group(1).replace(",", "");
                double val = Double.parseDouble(raw);
                String before = text.substring(Math.max(0, wm.start() - 20), wm.start()).toLowerCase(Locale.US);
                if (before.matches(".*\\b(?:total|amount|paid|charged|due|cost|price|subtotal|grand total|sum|sale|spent|payment|balance|fee|\\$)\\b.*")) {
                    if (val > 0.5 && val < 100000) return val;
                } else {
                    if (val > 0.5 && val < 10000) return val;
                }
            } catch (NumberFormatException ignored) {}
        }

        return 0.0;
    }

    /**
     * Date fallback for when the AI didn't return a usable date (rate-limited,
     * skipped, or failed). Looks for explicit dates in the email text and
     * returns millis, or null if nothing found (caller should fall back to
     * the Gmail internalDate in that case).
     *
     * For date ranges like "Jun 1 - Jun 15, 2026" or "Next billing date: July 21, 2026",
     * this returns the LAST date found in the text — for a range that's the more
     * relevant boundary (e.g. billing period end, statement date), and for a
     * single explicit date it's simply that date.
     */
    public static Long extractDateMillis(String text) {
        if (text == null || text.isEmpty()) return null;

        Long lastFound = null;

        Matcher isoMatcher = DATE_ISO.matcher(text);
        while (isoMatcher.find()) {
            try {
                Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoMatcher.group(1));
                if (d != null) lastFound = d.getTime();
            } catch (Exception ignored) {}
        }

        Matcher nameMatcher = DATE_MONTH_NAME.matcher(text);
        while (nameMatcher.find()) {
            try {
                String monthStr = nameMatcher.group(1);
                String dayStr   = nameMatcher.group(2);
                String yearStr  = nameMatcher.group(3);
                String normalized = monthStr + " " + dayStr + ", " + yearStr;
                Date d = null;
                // Try long-form then short-form month parsing
                for (String pattern : new String[]{"MMMM d, yyyy", "MMM d, yyyy"}) {
                    try {
                        d = new SimpleDateFormat(pattern, Locale.US).parse(normalized);
                        if (d != null) break;
                    } catch (Exception ignored) {}
                }
                if (d != null) lastFound = d.getTime();
            } catch (Exception ignored) {}
        }

        return lastFound;
    }

    private static String guessCategory(String merchant) {
        String m = merchant.toLowerCase(Locale.US);

        if (m.contains("uber") || m.contains("lyft") || m.contains("shell")
                || m.contains("esso") || m.contains("petro") || m.contains("southwest")
                || m.contains("delta") || m.contains("united") || m.contains("airlines")
                || m.contains("via rail") || m.contains("amtrak") || m.contains("bp")
                || m.contains("chevron") || m.contains("exxon") || m.contains("mobil"))
            return "Transport";

        if (m.contains("netflix") || m.contains("spotify") || m.contains("hbo")
                || m.contains("disney") || m.contains("steam") || m.contains("google")
                || m.contains("hulu") || m.contains("discord") || m.contains("twitch")
                || m.contains("patreon") || m.contains("adobe") || m.contains("dropbox")
                || m.contains("microsoft"))
            return "Subscriptions";

        if (m.contains("mcdonald") || m.contains("starbucks") || m.contains("tim horton")
                || m.contains("doordash") || m.contains("uber eats") || m.contains("chipotle")
                || m.contains("subway") || m.contains("pizza hut") || m.contains("domino")
                || m.contains("kfc") || m.contains("taco bell") || m.contains("burger king")
                || m.contains("wendy") || m.contains("dunkin") || m.contains("popeyes"))
            return "Food & Drink";

        if (m.contains("amazon") || m.contains("walmart") || m.contains("target")
                || m.contains("costco") || m.contains("best buy") || m.contains("nike")
                || m.contains("ebay") || m.contains("wish") || m.contains("etsy")
                || m.contains("home depot") || m.contains("lowes") || m.contains("ikea")
                || m.contains("adidas") || m.contains("7-eleven") || m.contains("safeway")
                || m.contains("loblaws") || m.contains("metro") || m.contains("shoppers")
                || m.contains("winners") || m.contains("h&m") || m.contains("zara")
                || m.contains("gap") || m.contains("old navy") || m.contains("cvs")
                || m.contains("walgreens"))
            return "Shopping";

        if (m.contains("airbnb") || m.contains("expedia") || m.contains("booking")
                || m.contains("hotels") || m.contains("kayak"))
            return "Travel";

        if (m.contains("apple"))
            return "Entertainment";

        if (m.contains("paypal") || m.contains("venmo") || m.contains("cash")
                || m.contains("square") || m.contains("stripe"))
            return "Transfers";

        if (m.contains("canada post") || m.contains("ups") || m.contains("fedex")
                || m.contains("dhl"))
            return "Shipping";

        if (m.contains("shopify"))
            return "Shopping";

        return "Shopping";
    }

    private static String formatDate(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd · h:mm a", Locale.US);
        return sdf.format(new Date(millis));
    }

    public static String formatAmount(double amount) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return "$" + df.format(amount);
    }

    private static String capitalizeWords(String input) {
        String[] words = input.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.length() > 0) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }
}

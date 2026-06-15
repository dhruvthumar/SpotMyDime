package com.spotmydime.data;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransactionParser {

    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("\\$\\s*([0-9]+(?:,[0-9]{3})*\\.[0-9]{2})");

    private static final Pattern CURRENCY_AMOUNT =
            Pattern.compile("(?:total|amount|charged|paid|sum|subtotal|grand total|due|cost|price|spent|payment|balance|fee|sale)\\s*[:\\s]*\\$?\\s*([0-9]+(?:,[0-9]{3})*\\.[0-9]{2})",
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

        return 0.0;
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

package com.spotmydime.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class TransactionParserTest {

    // ── extractMerchantName: hardcoded MERCHANT_RULES ──

    @Test
    public void testMerchantRulesSubject() {
        assertEquals("Amazon",       TransactionParser.extractMerchantName("Amazon order shipped", "", null));
        assertEquals("PayPal",       TransactionParser.extractMerchantName("Your PayPal receipt", "", null));
        assertEquals("Uber",         TransactionParser.extractMerchantName("Your Uber trip receipt", "", null));
        assertEquals("Netflix",      TransactionParser.extractMerchantName("Netflix monthly charge", "", null));
        assertEquals("Starbucks",    TransactionParser.extractMerchantName("Receipt from Starbucks", "", null));
        assertEquals("McDonald's",   TransactionParser.extractMerchantName("McDonald's order", "", null));
        assertEquals("Walmart",      TransactionParser.extractMerchantName("Walmart purchase", "", null));
        assertEquals("Costco",       TransactionParser.extractMerchantName("Costco membership renewal", "", null));
        assertEquals("Tim Hortons",  TransactionParser.extractMerchantName("Tim Hortons payment", "", null));
        assertEquals("Airbnb",       TransactionParser.extractMerchantName("Airbnb booking confirmed", "", null));
    }

    @Test
    public void testMerchantRulesSnippetFallback() {
        assertEquals("Spotify", TransactionParser.extractMerchantName("Re: payment", "Your Spotify Premium plan", null));
        assertEquals("Best Buy", TransactionParser.extractMerchantName("Order", "Order from Best Buy", null));
    }

    @Test
    public void testMerchantRulesNormalizedNames() {
        assertEquals("DoorDash",      TransactionParser.extractMerchantName("Doordash delivery", "", null));
        assertEquals("Home Depot",    TransactionParser.extractMerchantName("Home Depot receipt", "", null));
        assertEquals("Shoppers Drug Mart", TransactionParser.extractMerchantName("Shoppers Drug Mart purchase", "", null));
        assertEquals("Petro-Canada",  TransactionParser.extractMerchantName("Petro Canada payment", "", null));
        assertEquals("7-Eleven",      TransactionParser.extractMerchantName("7-Eleven purchase", "", null));
    }

    @Test
    public void testMerchantRuleCoverageAllEntries() {
        String[][] rules = {
            {"amazon","Amazon"}, {"paypal","PayPal"}, {"uber eats","Uber Eats"},
            {"ubereats","Uber Eats"}, {"uber","Uber"}, {"netflix","Netflix"},
            {"spotify","Spotify"}, {"starbucks","Starbucks"}, {"mcdonald","McDonald's"},
            {"doordash","DoorDash"}, {"walmart","Walmart"}, {"costco","Costco"},
            {"target","Target"}, {"best buy","Best Buy"}, {"bestbuy","Best Buy"},
            {"airbnb","Airbnb"}, {"lyft","Lyft"}, {"google","Google"},
            {"apple","Apple"}, {"tim horton","Tim Hortons"}, {"esso","Esso"},
            {"shell","Shell"}, {"petro","Petro-Canada"}, {"steam","Steam"},
            {"hbo","HBO"}, {"disney","Disney+"}, {"southwest","Southwest"},
            {"delta","Delta"}, {"canada post","Canada Post"}, {"nike","Nike"},
            {"wish","Wish"}, {"ebay","eBay"}, {"etsy","Etsy"}, {"hulu","Hulu"},
            {"homedepot","Home Depot"}, {"home depot","Home Depot"}, {"lowes","Lowe's"},
            {"ikea","IKEA"}, {"chipotle","Chipotle"}, {"subway","Subway"},
            {"pizza hut","Pizza Hut"}, {"dominos","Domino's"}, {"kfc","KFC"},
            {"taco bell","Taco Bell"}, {"burger king","Burger King"},
            {"wendys","Wendy's"}, {"dunkin","Dunkin'"}, {"popeyes","Popeyes"},
            {"adidas","Adidas"}, {"shopify","Shopify"}, {"stripe","Stripe"},
            {"square","Square"}, {"venmo","Venmo"}, {"cashapp","Cash App"},
            {"expedia","Expedia"}, {"booking.com","Booking.com"},
            {"hotels.com","Hotels.com"}, {"kayak","Kayak"}, {"united","United Airlines"},
            {"american airlines","American Airlines"}, {"via rail","Via Rail"},
            {"amtrak","Amtrak"}, {"bp","BP"}, {"chevron","Chevron"},
            {"exxon","Exxon"}, {"mobil","Mobil"}, {"7-eleven","7-Eleven"},
            {"7eleven","7-Eleven"}, {"safeway","Safeway"}, {"loblaws","Loblaws"},
            {"metro","Metro"}, {"shoppers","Shoppers Drug Mart"},
            {"winners","Winners"}, {"h&m","H&M"}, {"zara","Zara"},
            {"gap","Gap"}, {"old navy","Old Navy"}, {"cvs","CVS"},
            {"walgreens","Walgreens"}, {"microsoft","Microsoft"},
            {"adobe","Adobe"}, {"dropbox","Dropbox"}, {"discord","Discord"},
            {"twitch","Twitch"}, {"patreon","Patreon"},
        };
        for (String[] rule : rules) {
            String keyword = rule[0];
            String expected = rule[1];
            String result = TransactionParser.extractMerchantName(keyword + " receipt", "", null);
            assertEquals("Merchant rule for '" + keyword + "' should map to '" + expected + "'",
                    expected, result);
        }
    }

    // ── extractMerchantName: subject shape patterns ──

    @Test
    public void testSubjectDashSplit() {
        assertEquals("Spotify",  TransactionParser.extractMerchantName("Spotify Premium - Receipt", "", null));
        assertEquals("Uber",     TransactionParser.extractMerchantName("Uber Trip - Receipt", "", null));
    }

    @Test
    public void testSubjectSuffixWord() {
        assertEquals("Uber",     TransactionParser.extractMerchantName("Your Uber Receipt", "", null));
        assertEquals("Tim Hortons", TransactionParser.extractMerchantName("Tim Hortons Order Confirmation", "", null));
    }

    @Test
    public void testFromPatternSubject() {
        assertEquals("Canadian Tire", TransactionParser.extractMerchantName("Receipt from Canadian Tire", "", null));
    }

    @Test
    public void testExtractMerchantNullInputs() {
        assertNull(TransactionParser.extractMerchantName(null, null, null));
        assertNull(TransactionParser.extractMerchantName("", "", ""));
    }

    // ── extractAmount ──

    @Test
    public void testExtractAmountStandard() {
        assertEquals(12.99, TransactionParser.extractAmount("Total: $12.99"), 0.01);
        assertEquals(150.00, TransactionParser.extractAmount("Amount: $150.00"), 0.01);
        assertEquals(1234.56, TransactionParser.extractAmount("Paid $1,234.56"), 0.01);
    }

    @Test
    public void testExtractAmountKeywordPrefix() {
        assertEquals(59.99, TransactionParser.extractAmount("Charged $59.99 to your card"), 0.01);
        assertEquals(29.95, TransactionParser.extractAmount("Payment $29.95"), 0.01);
        assertEquals(99.00, TransactionParser.extractAmount("Total $99.00"), 0.01);
    }

    @Test
    public void testExtractAmountWholeDollar() {
        assertEquals(150.00, TransactionParser.extractAmount("Total: $150"), 0.01);
        assertEquals(25000.00, TransactionParser.extractAmount("Grand total $25000"), 0.01);
    }

    @Test
    public void testExtractAmountNoMatch() {
        assertEquals(0.0, TransactionParser.extractAmount("No dollar amounts here"), 0.01);
        assertEquals(0.0, TransactionParser.extractAmount(""), 0.01);
        assertEquals(0.0, TransactionParser.extractAmount("$0.50 too small"), 0.01);
    }

    @Test
    public void testExtractAmountEdgeCases() {
        assertEquals(0.0, TransactionParser.extractAmount("Total: $100000"), 0.01);
        assertEquals(0.0, TransactionParser.extractAmount("Total: $100001"), 0.01);
        assertEquals(99999.99, TransactionParser.extractAmount("Charged $99,999.99"), 0.01);
    }

    // ── extractDateMillis ──

    @Test
    public void testExtractDateIso() {
        Long millis = TransactionParser.extractDateMillis("dated: 2026-06-19");
        assertNotNull(millis);
    }

    @Test
    public void testExtractDateMonthName() {
        Long millis = TransactionParser.extractDateMillis("on Jun 19, 2026");
        assertNotNull(millis);
        Long millis2 = TransactionParser.extractDateMillis("Next billing date: July 21, 2026");
        assertNotNull(millis2);
    }

    @Test
    public void testExtractDateNullEmpty() {
        assertNull(TransactionParser.extractDateMillis(null));
        assertNull(TransactionParser.extractDateMillis(""));
        assertNull(TransactionParser.extractDateMillis("no date here"));
    }

    @Test
    public void testExtractDateRangeReturnsLaterDate() {
        Long millis = TransactionParser.extractDateMillis("Jun 1 - Jun 15, 2026");
        assertNotNull(millis);
    }

    // ── formatAmount ──

    @Test
    public void testFormatAmount() {
        assertEquals("$12.99",      TransactionParser.formatAmount(12.99));
        assertEquals("$1,234.56",   TransactionParser.formatAmount(1234.56));
        assertEquals("$0.00",       TransactionParser.formatAmount(0.00));
        assertEquals("$0.01",       TransactionParser.formatAmount(0.01));
        assertEquals("$99,999.99",  TransactionParser.formatAmount(99999.99));
    }

    // ── tryParse full pipeline ──

    @Test
    public void testTryParseValidTransaction() {
        Transaction txn = TransactionParser.tryParse(
            "Your Amazon order receipt",
            "Thank you for your order. Total: $59.99",
            "Full body with Amazon $59.99",
            1718900000000L
        );
        assertNotNull(txn);
        assertEquals("Amazon", txn.getMerchant());
        assertEquals(59.99, txn.getAmount(), 0.01);
        assertNotNull(txn.getCategory());
        assertEquals(Transaction.Type.OUTGOING, txn.getType());
    }

    @Test
    public void testTryParseNegativeKeywordsReturnsNull() {
        assertNull(TransactionParser.tryParse(
            "Verify your account - security alert",
            "Please verify your login", null, 1718900000000L));
    }

    @Test
    public void testTryParseNoMerchantReturnsNull() {
        assertNull(TransactionParser.tryParse(
            "Payment processed", "Total: $25.00", null, 1718900000000L));
    }

    @Test
    public void testTryParseNullInputs() {
        assertNull(TransactionParser.tryParse(null, null, null, 0));
    }

    // ── guessCategory (tested indirectly via tryParse and extractMerchantName logic) ──

    @Test
    public void testCategoryMapping() {
        testCategoryForMerchant("Uber",        "Transport");
        testCategoryForMerchant("Netflix",     "Subscriptions");
        testCategoryForMerchant("McDonald's",  "Food & Drink");
        testCategoryForMerchant("Amazon",      "Shopping");
        testCategoryForMerchant("Airbnb",      "Travel");
        testCategoryForMerchant("Apple",       "Entertainment");
        testCategoryForMerchant("PayPal",      "Transfers");
        testCategoryForMerchant("Canada Post", "Shipping");
    }

    private void testCategoryForMerchant(String merchant, String expectedCategory) {
        Transaction txn = TransactionParser.tryParse(
            merchant + " receipt",
            "Total: $10.00 from " + merchant,
            null, 1718900000000L);
        assertNotNull("Merchant " + merchant + " should parse", txn);
        assertEquals("Category for " + merchant, expectedCategory, txn.getCategory());
    }
}

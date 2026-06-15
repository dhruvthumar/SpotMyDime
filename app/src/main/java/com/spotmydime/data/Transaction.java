package com.spotmydime.data;

public class Transaction {
    private final String merchant;
    private final double amount;
    private final long dateMillis;
    private final String dateDisplay;
    private final String category;
    private final char avatarLetter;

    public Transaction(String merchant, double amount, long dateMillis,
                       String dateDisplay, String category, char avatarLetter) {
        this.merchant = merchant;
        this.amount = amount;
        this.dateMillis = dateMillis;
        this.dateDisplay = dateDisplay;
        this.category = category;
        this.avatarLetter = avatarLetter;
    }

    public String getMerchant() { return merchant; }
    public double getAmount() { return amount; }
    public long getDateMillis() { return dateMillis; }
    public String getDateDisplay() { return dateDisplay; }
    public String getCategory() { return category; }
    public char getAvatarLetter() { return avatarLetter; }
}

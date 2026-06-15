package com.spotmydime.data;

public class Transaction {

    public enum Type { INCOMING, OUTGOING }

    private final String merchant;
    private final double amount;
    private final long dateMillis;
    private final String dateDisplay;
    private final String category;
    private final char avatarLetter;
    private final Type type;
    private final String senderEmail;
    private final String subject;
    private final String messageId;
    private final String rawVendor;

    public Transaction(String merchant, double amount, long dateMillis,
                       String dateDisplay, String category, char avatarLetter, Type type) {
        this(merchant, amount, dateMillis, dateDisplay, category, avatarLetter, type, null, null, null, null);
    }

    public Transaction(String merchant, double amount, long dateMillis,
                       String dateDisplay, String category, char avatarLetter, Type type,
                       String senderEmail, String subject) {
        this(merchant, amount, dateMillis, dateDisplay, category, avatarLetter, type, senderEmail, subject, null, null);
    }

    public Transaction(String merchant, double amount, long dateMillis,
                       String dateDisplay, String category, char avatarLetter, Type type,
                       String senderEmail, String subject, String messageId) {
        this(merchant, amount, dateMillis, dateDisplay, category, avatarLetter, type, senderEmail, subject, messageId, null);
    }

    public Transaction(String merchant, double amount, long dateMillis,
                       String dateDisplay, String category, char avatarLetter, Type type,
                       String senderEmail, String subject, String messageId, String rawVendor) {
        this.merchant = merchant;
        this.amount = amount;
        this.dateMillis = dateMillis;
        this.dateDisplay = dateDisplay;
        this.category = category;
        this.avatarLetter = avatarLetter;
        this.type = type;
        this.senderEmail = senderEmail;
        this.subject = subject;
        this.messageId = messageId;
        this.rawVendor = rawVendor;
    }

    public String getMerchant() { return merchant; }
    public double getAmount() { return amount; }
    public long getDateMillis() { return dateMillis; }
    public String getDateDisplay() { return dateDisplay; }
    public String getCategory() { return category; }
    public char getAvatarLetter() { return avatarLetter; }
    public Type getType() { return type; }
    public String getSenderEmail() { return senderEmail; }
    public String getSubject() { return subject; }
    public String getMessageId() { return messageId; }
    public String getRawVendor() { return rawVendor; }
}

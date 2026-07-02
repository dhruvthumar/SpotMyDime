package com.spotmydime.data;

public class AiDebugEntry {
    public String messageId;
    public long timestamp;
    public String from;
    public String subject;
    public String snippet;
    public String body;

    public boolean snippetAmountPassed;
    public Boolean isTransactionalResult;

    public String geminiInput;
    public String geminiOutput;
    public int geminiHttpCode;
    public String parsedCategory;
    public String parsedMerchant;
    public Double parsedAmount;
    public String parsedDate;
    public String parsedType;
    public boolean parsedIsTransaction;
    public boolean parsedIsSuspicious;

    public String finalCategory;
    public String finalMerchant;
    public double finalAmount;
    public String finalDate;
    public String finalType;
    public boolean wasDiscarded;
    public String discardReason;

    public boolean cachedHit;
    public String cacheSource;
}

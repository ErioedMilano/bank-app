package com.erioed.bank.models;

import java.math.BigDecimal;

public class Transaction {
    public int id;
    public Integer fromAccount;
    public Integer toAccount;
    public String fromAccountNumber;
    public String toAccountNumber;
    public BigDecimal amount;
    public String description;
    public String createdAt;
}
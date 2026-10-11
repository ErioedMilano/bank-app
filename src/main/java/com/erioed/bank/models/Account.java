package com.erioed.bank.models;

import java.math.BigDecimal;

public class Account {
    public int id;
    public int userId;
    public String accountNumber;
    public BigDecimal balance;
    public String type; // CHECKING or SAVINGS
    public String createdAt;
}
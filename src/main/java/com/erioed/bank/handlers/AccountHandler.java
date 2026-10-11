package com.erioed.bank.handlers;

import com.erioed.bank.db.Database;
import com.erioed.bank.models.Account;
import com.erioed.bank.util.JsonUtil;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AccountHandler extends BaseHandler {

    @Override
    protected void handleRequest(HttpExchange ex) throws Exception {
        int uid = requireAuth(ex);
        if (uid < 0) return;

        String[] s = segments(ex, "/api/accounts");
        String method = ex.getRequestMethod();

        if (s.length == 0 && method.equals("GET")) {
            list(ex, uid);
        } else if (s.length == 0 && method.equals("POST")) {
            create(ex, uid);
        } else if (s.length == 1 && method.equals("GET")) {
            getOne(ex, uid, Integer.parseInt(s[0]));
        } else if (s.length == 2 && s[1].equals("deposit") && method.equals("POST")) {
            deposit(ex, uid, Integer.parseInt(s[0]));
        } else {
            JsonUtil.error(ex, 404, "Onbekende route");
        }
    }

    private void list(HttpExchange ex, int uid) throws SQLException, IOException {
        List<Account> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, user_id, account_number, balance, type, created_at " +
                             "FROM accounts WHERE user_id = ? ORDER BY id")) {
            ps.setInt(1, uid);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        JsonUtil.send(ex, 200, list);
    }

    private void getOne(HttpExchange ex, int uid, int accountId) throws SQLException, IOException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, user_id, account_number, balance, type, created_at " +
                             "FROM accounts WHERE id = ? AND user_id = ?")) {
            ps.setInt(1, accountId);
            ps.setInt(2, uid);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { JsonUtil.error(ex, 404, "Rekening niet gevonden"); return; }
            JsonUtil.send(ex, 200, map(rs));
        }
    }

    private void create(HttpExchange ex, int uid) throws SQLException, IOException {
        JsonObject body = JsonUtil.parse(JsonUtil.readBody(ex));
        String type = body.has("type") ? body.get("type").getAsString().toUpperCase() : "CHECKING";
        if (!type.equals("CHECKING") && !type.equals("SAVINGS")) {
            JsonUtil.error(ex, 400, "Type moet CHECKING of SAVINGS zijn");
            return;
        }
        String accNum = generateAccountNumber();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO accounts(user_id, account_number, balance, type) VALUES(?, ?, 0.00, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, uid);
            ps.setString(2, accNum);
            ps.setString(3, type);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            JsonObject resp = new JsonObject();
            resp.addProperty("id", rs.getInt(1));
            resp.addProperty("accountNumber", accNum);
            resp.addProperty("type", type);
            JsonUtil.send(ex, 201, resp);
        }
    }

    private void deposit(HttpExchange ex, int uid, int accountId) throws SQLException, IOException {
        JsonObject body = JsonUtil.parse(JsonUtil.readBody(ex));
        if (!body.has("amount")) { JsonUtil.error(ex, 400, "Amount verplicht"); return; }
        BigDecimal amount = body.get("amount").getAsBigDecimal();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            JsonUtil.error(ex, 400, "Bedrag moet positief zijn");
            return;
        }

        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Check ownership
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT balance FROM accounts WHERE id = ? AND user_id = ? FOR UPDATE")) {
                    ps.setInt(1, accountId);
                    ps.setInt(2, uid);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) {
                        c.rollback();
                        JsonUtil.error(ex, 404, "Rekening niet gevonden");
                        return;
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE accounts SET balance = balance + ? WHERE id = ?")) {
                    ps.setBigDecimal(1, amount);
                    ps.setInt(2, accountId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO transactions(to_account, amount, description) VALUES(?, ?, 'Storting')")) {
                    ps.setInt(1, accountId);
                    ps.setBigDecimal(2, amount);
                    ps.executeUpdate();
                }
                c.commit();
                JsonUtil.send(ex, 200, "{\"message\":\"Storting gelukt\"}");
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private Account map(ResultSet rs) throws SQLException {
        Account a = new Account();
        a.id = rs.getInt("id");
        a.userId = rs.getInt("user_id");
        a.accountNumber = rs.getString("account_number");
        a.balance = rs.getBigDecimal("balance");
        a.type = rs.getString("type");
        a.createdAt = String.valueOf(rs.getTimestamp("created_at"));
        return a;
    }

    private String generateAccountNumber() {
        long n = (long) (Math.random() * 9_000_000_000L) + 1_000_000_000L;
        return "TB" + n;
    }
}
package com.erioed.bank.handlers;

import com.erioed.bank.db.Database;
import com.erioed.bank.models.Transaction;
import com.erioed.bank.util.JsonUtil;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TransactionHandler extends BaseHandler {

    @Override
    protected void handleRequest(HttpExchange ex) throws Exception {
        int uid = requireAuth(ex);
        if (uid < 0) return;

        String[] s = segments(ex, "/api/transactions");
        String method = ex.getRequestMethod();

        if (s.length == 0 && method.equals("GET")) {
            history(ex, uid);
        } else if (s.length == 1 && s[0].equals("transfer") && method.equals("POST")) {
            transfer(ex, uid);
        } else {
            JsonUtil.error(ex, 404, "Onbekende route");
        }
    }

    private void history(HttpExchange ex, int uid) throws SQLException, IOException {
        List<Transaction> list = new ArrayList<>();
        String sql = """
                SELECT t.id, t.from_account, t.to_account, t.amount, t.description, t.created_at,
                       fa.account_number AS from_num,
                       ta.account_number AS to_num
                FROM transactions t
                LEFT JOIN accounts fa ON t.from_account = fa.id
                LEFT JOIN accounts ta ON t.to_account = ta.id
                WHERE fa.user_id = ? OR ta.user_id = ?
                ORDER BY t.created_at DESC
                LIMIT 100
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, uid);
            ps.setInt(2, uid);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Transaction t = new Transaction();
                t.id = rs.getInt("id");
                int fa = rs.getInt("from_account");
                t.fromAccount = rs.wasNull() ? null : fa;
                int ta = rs.getInt("to_account");
                t.toAccount = rs.wasNull() ? null : ta;
                t.fromAccountNumber = rs.getString("from_num");
                t.toAccountNumber = rs.getString("to_num");
                t.amount = rs.getBigDecimal("amount");
                t.description = rs.getString("description");
                t.createdAt = String.valueOf(rs.getTimestamp("created_at"));
                list.add(t);
            }
        }
        JsonUtil.send(ex, 200, list);
    }

    private void transfer(HttpExchange ex, int uid) throws SQLException, IOException {
        JsonObject body = JsonUtil.parse(JsonUtil.readBody(ex));
        if (!body.has("fromAccountId") || !body.has("toAccountNumber") || !body.has("amount")) {
            JsonUtil.error(ex, 400, "fromAccountId, toAccountNumber en amount verplicht");
            return;
        }
        int fromId = body.get("fromAccountId").getAsInt();
        String toAccNum = body.get("toAccountNumber").getAsString().trim();
        BigDecimal amount = body.get("amount").getAsBigDecimal();
        String description = body.has("description") ? body.get("description").getAsString() : "Overboeking";

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            JsonUtil.error(ex, 400, "Bedrag moet positief zijn");
            return;
        }
        if (amount.compareTo(new BigDecimal("10000")) > 0) {
            JsonUtil.error(ex, 400, "Max €10.000 per transactie");
            return;
        }

        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Lock + check source
                BigDecimal fromBalance;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT balance FROM accounts WHERE id = ? AND user_id = ? FOR UPDATE")) {
                    ps.setInt(1, fromId);
                    ps.setInt(2, uid);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) {
                        c.rollback();
                        JsonUtil.error(ex, 404, "Bronrekening niet gevonden");
                        return;
                    }
                    fromBalance = rs.getBigDecimal("balance");
                }

                // 2. Find destination
                int toId;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id FROM accounts WHERE account_number = ?")) {
                    ps.setString(1, toAccNum);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) {
                        c.rollback();
                        JsonUtil.error(ex, 404, "Doelrekening niet gevonden");
                        return;
                    }
                    toId = rs.getInt("id");
                }

                if (toId == fromId) {
                    c.rollback();
                    JsonUtil.error(ex, 400, "Kan niet naar dezelfde rekening overmaken");
                    return;
                }

                if (fromBalance.compareTo(amount) < 0) {
                    c.rollback();
                    JsonUtil.error(ex, 400, "Onvoldoende saldo");
                    return;
                }

                // 3. Update balances
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE accounts SET balance = balance - ? WHERE id = ?")) {
                    ps.setBigDecimal(1, amount);
                    ps.setInt(2, fromId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE accounts SET balance = balance + ? WHERE id = ?")) {
                    ps.setBigDecimal(1, amount);
                    ps.setInt(2, toId);
                    ps.executeUpdate();
                }

                // 4. Log
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO transactions(from_account, to_account, amount, description) VALUES(?, ?, ?, ?)")) {
                    ps.setInt(1, fromId);
                    ps.setInt(2, toId);
                    ps.setBigDecimal(3, amount);
                    ps.setString(4, description);
                    ps.executeUpdate();
                }

                c.commit();
                JsonUtil.send(ex, 200, "{\"message\":\"Overboeking gelukt\"}");
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }
}
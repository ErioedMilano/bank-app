package com.erioed.bank.handlers;

import com.erioed.bank.db.Database;
import com.erioed.bank.util.JsonUtil;
import com.erioed.bank.util.JwtUtil;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.sql.*;

public class AuthHandler extends BaseHandler {

    @Override
    protected void handleRequest(HttpExchange ex) throws Exception {
        String[] s = segments(ex, "/api/auth");
        String method = ex.getRequestMethod();

        if (s.length == 1 && s[0].equals("register") && method.equals("POST")) {
            register(ex);
        } else if (s.length == 1 && s[0].equals("login") && method.equals("POST")) {
            login(ex);
        } else if (s.length == 1 && s[0].equals("me") && method.equals("GET")) {
            me(ex);
        } else {
            JsonUtil.error(ex, 404, "Onbekende route");
        }
    }

    private void register(HttpExchange ex) throws IOException, SQLException {
        JsonObject body = JsonUtil.parse(JsonUtil.readBody(ex));
        if (!body.has("email") || !body.has("password")) {
            JsonUtil.error(ex, 400, "Email en password verplicht");
            return;
        }
        String email = body.get("email").getAsString().trim().toLowerCase();
        String password = body.get("password").getAsString();
        if (password.length() < 6) {
            JsonUtil.error(ex, 400, "Wachtwoord min 6 karakters");
            return;
        }
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO users(email, password_hash, role) VALUES(?, ?, 'USER')",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, hash);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            int userId = rs.getInt(1);

            // Automatisch een betaalrekening aanmaken met welkomstsaldo
            try (PreparedStatement ps2 = c.prepareStatement(
                    "INSERT INTO accounts(user_id, account_number, balance, type) VALUES(?, ?, 250.00, 'CHECKING')")) {
                ps2.setInt(1, userId);
                ps2.setString(2, generateAccountNumber());
                ps2.executeUpdate();
            }

            String token = JwtUtil.generate(userId, email, "USER");
            JsonObject resp = new JsonObject();
            resp.addProperty("token", token);
            resp.addProperty("email", email);
            JsonUtil.send(ex, 201, resp);
        } catch (SQLIntegrityConstraintViolationException dup) {
            JsonUtil.error(ex, 409, "Email al in gebruik");
        }
    }

    private void login(HttpExchange ex) throws IOException, SQLException {
        JsonObject body = JsonUtil.parse(JsonUtil.readBody(ex));
        String email = body.get("email").getAsString().trim().toLowerCase();
        String password = body.get("password").getAsString();

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, password_hash, role FROM users WHERE email = ?")) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (!rs.next() || !BCrypt.checkpw(password, rs.getString("password_hash"))) {
                JsonUtil.error(ex, 401, "Verkeerde email of wachtwoord");
                return;
            }
            int id = rs.getInt("id");
            String role = rs.getString("role");
            String token = JwtUtil.generate(id, email, role);

            JsonObject resp = new JsonObject();
            resp.addProperty("token", token);
            resp.addProperty("email", email);
            resp.addProperty("role", role);
            JsonUtil.send(ex, 200, resp);
        }
    }

    private void me(HttpExchange ex) throws Exception {
        int uid = requireAuth(ex);
        if (uid < 0) return;

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, email, role, created_at FROM users WHERE id = ?")) {
            ps.setInt(1, uid);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { JsonUtil.error(ex, 404, "Gebruiker niet gevonden"); return; }

            // --- HIER GEBRUIKEN WE HET USER MODEL ---
            com.erioed.bank.models.User user = new com.erioed.bank.models.User();
            user.id = rs.getInt("id");
            user.email = rs.getString("email");
            user.role = rs.getString("role");
            user.createdAt = String.valueOf(rs.getTimestamp("created_at"));

            JsonUtil.send(ex, 200, user);
            // ----------------------------------------
        }
    }
    private String generateAccountNumber() {
        long n = (long) (Math.random() * 9_000_000_000L) + 1_000_000_000L;
        return "TB" + n;
    }
}
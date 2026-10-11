package com.erioed.bank.handlers;

import com.erioed.bank.util.JsonUtil;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

public abstract class BaseHandler implements HttpHandler {

    @Override
    public final void handle(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        try {
            handleRequest(ex);
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.error(ex, 500, "Server fout: " + e.getMessage());
        }
    }

    protected abstract void handleRequest(HttpExchange ex) throws Exception;

    private void addCors(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.add("Access-Control-Allow-Origin", "*");
        h.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        h.add("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    protected String[] segments(HttpExchange ex, String base) {
        String path = ex.getRequestURI().getPath();
        String rest = path.substring(base.length());
        if (rest.isEmpty() || rest.equals("/")) return new String[0];
        if (rest.startsWith("/")) rest = rest.substring(1);
        return rest.split("/");
    }

    protected int requireAuth(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            JsonUtil.error(ex, 401, "Geen of ongeldig token");
            return -1;
        }
        try {
            var claims = com.erioed.bank.util.JwtUtil.verify(auth.substring(7));
            return com.erioed.bank.util.JwtUtil.getUserId(claims);
        } catch (Exception e) {
            JsonUtil.error(ex, 401, "Token verlopen of ongeldig");
            return -1;
        }
    }
}
package com.erioed.bank;

import com.erioed.bank.handlers.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/health", ex -> {
            byte[] b = "{\"status\":\"ok\"}".getBytes();
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });

        server.createContext("/api/auth", new AuthHandler());
        server.createContext("/api/accounts", new AccountHandler());
        server.createContext("/api/transactions", new TransactionHandler());

        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        System.out.println("🚀 Server draait op poort " + port);
    }
}
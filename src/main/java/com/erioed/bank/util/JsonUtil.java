package com.erioed.bank.util;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class JsonUtil {
    public static final Gson GSON = new GsonBuilder().serializeNulls().create();

    public static JsonObject parse(String s) throws JsonSyntaxException {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    public static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    public static void send(HttpExchange ex, int status, Object body) throws IOException {
        String json = (body instanceof String) ? (String) body : GSON.toJson(body);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (var os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void error(HttpExchange ex, int status, String message) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        send(ex, status, o);
    }
}

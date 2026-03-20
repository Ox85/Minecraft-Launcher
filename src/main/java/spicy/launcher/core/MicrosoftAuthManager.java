package spicy.launcher.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MicrosoftAuthManager {
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf";
    private static final String MS_AUTH_URL = "https://login.live.com/oauth20_authorize.srf";
    private static final String MS_TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    private static final String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Path cacheFile;

    public MicrosoftAuthManager(Path gameDir) {
        this.cacheFile = gameDir.resolve("ms_auth_cache.json");
    }


    public CompletableFuture<MinecraftAccount> authenticate() {
        CompletableFuture<MinecraftAccount> future = new CompletableFuture<>();
        MinecraftAccount cached = loadFromCache();
        if (cached != null && !cached.isExpired()) {
            future.complete(cached);
            return future;
        }

        new Thread(() -> {
            try {
                String authCode = showWebViewAndGetCode();

                String msAccessToken = getMicrosoftToken(authCode);

                String xblToken = authenticateXBL(msAccessToken);

                Map<String, String> xstsData = authenticateXSTS(xblToken);

                String mcAccessToken = loginMinecraft(xstsData.get("token"), xstsData.get("uhs"));

                MinecraftProfile profile = getMinecraftProfile(mcAccessToken);

                MinecraftAccount account = new MinecraftAccount(
                        profile.name,
                        profile.id,
                        mcAccessToken,
                        Instant.now().plusSeconds(86400).toEpochMilli()
                );

                saveToCache(account);
                future.complete(account);

            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }).start();

        return future;
    }

    private String showWebViewAndGetCode() throws Exception {
        CompletableFuture<String> codeFuture = new CompletableFuture<>();

        javafx.application.Platform.runLater(() -> {
            try {
                javafx.stage.Stage stage = new javafx.stage.Stage();
                stage.setTitle("Sign in with Microsoft");
                stage.setWidth(600);
                stage.setHeight(700);
                WebView webView = new WebView();
                javafx.scene.web.WebEngine engine = webView.getEngine();

                engine.locationProperty().addListener((obs, oldUrl, newUrl) -> {
                    if (newUrl != null && newUrl.startsWith(REDIRECT_URI)) {
                        try {
                            String query = newUrl.contains("?") ? newUrl.substring(newUrl.indexOf("?") + 1) : "";
                            Map<String, String> params = parseQueryString(query);

                            if (params.containsKey("code")) {
                                String code = params.get("code");
                                codeFuture.complete(code);
                                javafx.application.Platform.runLater(stage::close);
                            } else if (params.containsKey("error")) {
                                codeFuture.completeExceptionally(
                                        new Exception("Auth error: " + params.getOrDefault("error_description", params.get("error")))
                                );
                                javafx.application.Platform.runLater(stage::close);
                            }
                        } catch (Exception e) {
                            codeFuture.completeExceptionally(e);
                            javafx.application.Platform.runLater(stage::close);
                        }
                    }
                });

                String authUrl = buildAuthUrl();
                engine.load(authUrl);

                javafx.scene.Scene scene = new javafx.scene.Scene(webView);
                stage.setScene(scene);
                stage.show();

                stage.setOnCloseRequest(e -> {
                    if (!codeFuture.isDone()) {
                        codeFuture.completeExceptionally(new Exception("The user canceled the login"));
                    }
                });

            } catch (Exception e) {
                codeFuture.completeExceptionally(e);
            }
        });

        return codeFuture.get(180, TimeUnit.SECONDS);
    }

    private String buildAuthUrl() {
        return MS_AUTH_URL + "?" +
                "client_id=" + CLIENT_ID +
                "&response_type=code" +
                "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8) +
                "&scope=" + URLEncoder.encode("XboxLive.signin offline_access", StandardCharsets.UTF_8) +
                "&prompt=select_account";
    }


    private String getMicrosoftToken(String code) throws Exception {
        String body = "client_id=" + CLIENT_ID +
                "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                "&grant_type=authorization_code" +
                "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MS_TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json = gson.fromJson(response.body(), JsonObject.class);

        if (!json.has("access_token")) {
            String error = json.has("error") ? json.get("error").getAsString() : "unknown";
            String errorDesc = json.has("error_description") ? json.get("error_description").getAsString() : response.body();
            throw new Exception("Failed to get MS token: " + error + " - " + errorDesc);
        }
        return json.get("access_token").getAsString();
    }


    private String authenticateXBL(String msToken) throws Exception {
        JsonObject requestBody = new JsonObject();
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", "d=" + msToken);

        requestBody.add("Properties", properties);
        requestBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        requestBody.addProperty("TokenType", "JWT");
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(XBL_AUTH_URL))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
        if (!json.has("Token")) {
            String error = json.has("XErr") ? "XErr: " + json.get("XErr").getAsString() : "Unknown error";
            throw new Exception("Failed to authenticate with XBL: " + error + " | Body: " + response.body());
        }

        return json.get("Token").getAsString();
    }


    private Map<String, String> authenticateXSTS(String xblToken) throws Exception {
        JsonObject requestBody = new JsonObject();
        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");

        com.google.gson.JsonArray tokens = new com.google.gson.JsonArray();
        tokens.add(xblToken);
        properties.add("UserTokens", tokens);

        requestBody.add("Properties", properties);
        requestBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        requestBody.addProperty("TokenType", "JWT");

        System.out.println("XSTS Request: " + gson.toJson(requestBody));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(XSTS_AUTH_URL))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json = gson.fromJson(response.body(), JsonObject.class);

        if (!json.has("Token")) {
            if (json.has("XErr")) {
                long xErr = json.get("XErr").getAsLong();
                throw new Exception(String.valueOf(xErr));
            }
            throw new Exception("Failed to authenticate with XSTS: " + response.body());
        }

        String token = json.get("Token").getAsString();
        String uhs = json.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject()
                .get("uhs").getAsString();

        Map<String, String> result = new HashMap<>();
        result.put("token", token);
        result.put("uhs", uhs);
        return result;
    }


    private String loginMinecraft(String xstsToken, String uhs) throws Exception {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MC_LOGIN_URL))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
        if (!json.has("access_token")) {
            throw new Exception("Failed to login to Minecraft: " + response.body());
        }
        return json.get("access_token").getAsString();
    }

    private MinecraftProfile getMinecraftProfile(String mcToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MC_PROFILE_URL))
                .header("Authorization", "Bearer " + mcToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            throw new Exception("This account does not have Minecraft: Java Edition!");
        }

        if (response.statusCode() != 200) {
            throw new Exception("Profile could not be retrieved: HTTP " + response.statusCode());
        }

        JsonObject json = gson.fromJson(response.body(), JsonObject.class);

        return new MinecraftProfile(
                json.get("name").getAsString(),
                json.get("id").getAsString()
        );
    }

    private void saveToCache(MinecraftAccount account) {
        try {
            Files.writeString(cacheFile, gson.toJson(account), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Cache failed to save: " + e.getMessage());
        }
    }

    private MinecraftAccount loadFromCache() {
        try {
            if (Files.exists(cacheFile)) {
                String json = Files.readString(cacheFile, StandardCharsets.UTF_8);
                return gson.fromJson(json, MinecraftAccount.class);
            }
        } catch (Exception e) {
            System.err.println("Cache failed to read: " + e.getMessage());
        }
        return null;
    }

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;

        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                params.put(pair[0], pair[1]);
            }
        }
        return params;
    }


    public record MinecraftAccount(String username, String uuid, String accessToken, long expiresAt) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private record MinecraftProfile(String name, String id) {
    }
}
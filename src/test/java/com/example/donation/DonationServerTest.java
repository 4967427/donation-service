package com.example.donation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class DonationServerTest {
    private static DonationServer server;
    private static HttpClient client;
    private static String baseUrl;

    public static void main(String[] args) throws Exception {
        server = new DonationServer(0);
        server.start();
        client = HttpClient.newHttpClient();
        baseUrl = "http://localhost:" + server.port();
        try {
            sessionIsStableAndWellFormed();
            invalidSessionIsRejected();
            highestDonationWinsAndRankingIsSorted();
            rankingIsLimitedToTwenty();
            concurrentDonationsAreSafe();
            malformedRequestsAreRejected();
            System.out.println("All tests passed");
        } finally {
            server.close();
        }
    }

    private static void sessionIsStableAndWellFormed() throws Exception {
        HttpResponse<String> first = get("/1234/session");
        HttpResponse<String> second = get("/1234/session");
        check(first.statusCode() == 200, "session status");
        check(first.body().matches("[a-z0-9]+"), "session format");
        check(first.body().equals(second.body()), "session must remain stable");
    }

    private static void invalidSessionIsRejected() throws Exception {
        HttpResponse<String> response = post("/888/donate?sessionkey=notvalid", "50");
        check(response.statusCode() == 403, "invalid session status");
        check(response.body().isEmpty(), "invalid session body");
    }

    private static void highestDonationWinsAndRankingIsSorted() throws Exception {
        donate(2001, 888, 10);
        donate(2001, 888, 50);
        donate(2001, 888, 30);
        donate(2002, 888, 40);
        HttpResponse<String> response = get("/888/topdonors");
        check(response.statusCode() == 200, "ranking status");
        check("2001=50,2002=40".equals(response.body()), "ranking contents: " + response.body());
        check(get("/999/topdonors").body().isEmpty(), "empty department");
    }

    private static void rankingIsLimitedToTwenty() throws Exception {
        for (int i = 1; i <= 25; i++) {
            donate(3000 + i, 777, i);
        }
        String[] rows = get("/777/topdonors").body().split(",");
        check(rows.length == 20, "top 20 limit");
        check("3025=25".equals(rows[0]), "descending order");
        check("3006=6".equals(rows[19]), "twentieth entry");
    }

    private static void concurrentDonationsAreSafe() throws Exception {
        int patient = 5000;
        String key = get("/" + patient + "/session").body();
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (int points = 1; points <= 100; points++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/555/donate?sessionkey=" + key))
                    .POST(HttpRequest.BodyPublishers.ofString(Integer.toString(points)))
                    .build();
            futures.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        for (CompletableFuture<HttpResponse<String>> future : futures) {
            check(future.join().statusCode() == 204, "concurrent donation status");
        }
        check("5000=100".equals(get("/555/topdonors").body()), "concurrent max");
    }

    private static void malformedRequestsAreRejected() throws Exception {
        check(get("/abc/session").statusCode() == 400, "non-numeric id");
        String key = get("/6000/session").body();
        check(post("/1/donate?sessionkey=" + key, "oops").statusCode() == 400, "invalid points");
        check(get("/6000/unknown").statusCode() == 404, "unknown route");
    }

    private static void donate(int patientId, int departmentId, int points) throws Exception {
        String key = get("/" + patientId + "/session").body();
        HttpResponse<String> response = post(
                "/" + departmentId + "/donate?sessionkey=" + key,
                Integer.toString(points));
        check(response.statusCode() == 204, "donation status");
    }

    private static HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder().uri(URI.create(baseUrl + path))
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

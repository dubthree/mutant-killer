package io.github.dubthree.mutantkiller.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * GitHub implementation of GitProvider.
 */
public class GitHubProvider implements GitProvider {

    private static final String API_BASE = "https://api.github.com";
    
    private final String token;
    private final String owner;
    private final String repo;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GitHubProvider(String token, String owner, String repo) {
        this.token = token;
        this.owner = owner;
        this.repo = repo;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String name() {
        return "GitHub";
    }

    @Override
    public String createPullRequest(String headBranch, String baseBranch, String title, String body) 
            throws Exception {
        
        String url = String.format("%s/repos/%s/%s/pulls", API_BASE, owner, repo);
        
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", title);
        payload.put("body", body);
        payload.put("head", headBranch);
        payload.put("base", baseBranch);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github+json")
            .header("Content-Type", "application/json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 201) {
            JsonNode responseJson = objectMapper.readTree(response.body());
            return responseJson.get("html_url").asText();
        } else if (response.statusCode() == 422) {
            // PR might already exist
            JsonNode responseJson = objectMapper.readTree(response.body());
            String message = responseJson.has("message") ? responseJson.get("message").asText() : "";
            if (message.contains("A pull request already exists")) {
                return findExistingPr(headBranch, baseBranch);
            }
            throw new IOException("Failed to create PR: " + response.body());
        } else {
            throw new IOException("Failed to create PR (HTTP " + response.statusCode() + "): " + response.body());
        }
    }

    @Override
    public void addComment(String prId, String comment) throws Exception {
        String url = String.format("%s/repos/%s/%s/issues/%s/comments", API_BASE, owner, repo, prId);
        
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("body", comment);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github+json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String findExistingPr(String headBranch, String baseBranch) throws Exception {
        String url = String.format("%s/repos/%s/%s/pulls?head=%s:%s&base=%s", 
            API_BASE, owner, repo, owner, headBranch, baseBranch);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github+json")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode prs = objectMapper.readTree(response.body());
            if (prs.isArray() && prs.size() > 0) {
                return prs.get(0).get("html_url").asText();
            }
        }
        
        return "PR exists but could not find URL";
    }
}

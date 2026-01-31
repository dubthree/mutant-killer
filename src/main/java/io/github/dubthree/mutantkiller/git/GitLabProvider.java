package io.github.dubthree.mutantkiller.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * GitLab implementation of GitProvider.
 * Supports both gitlab.com and self-hosted GitLab instances.
 */
public class GitLabProvider implements GitProvider {

    private final String token;
    private final String baseUrl;
    private final String projectPath; // owner/repo or group/subgroup/repo
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GitLabProvider(String token, String baseUrl, String owner, String repo) {
        this.token = token;
        this.baseUrl = baseUrl.replaceAll("/$", ""); // Remove trailing slash
        this.projectPath = owner + "/" + repo;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String name() {
        return "GitLab";
    }

    @Override
    public String createPullRequest(String headBranch, String baseBranch, String title, String body) 
            throws Exception {
        
        String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
        String url = String.format("%s/api/v4/projects/%s/merge_requests", baseUrl, encodedPath);
        
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("source_branch", headBranch);
        payload.put("target_branch", baseBranch);
        payload.put("title", title);
        payload.put("description", body);
        payload.put("remove_source_branch", true);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("PRIVATE-TOKEN", token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 201) {
            JsonNode responseJson = objectMapper.readTree(response.body());
            return responseJson.get("web_url").asText();
        } else if (response.statusCode() == 409) {
            // MR might already exist
            return findExistingMr(headBranch, baseBranch);
        } else {
            throw new IOException("Failed to create MR (HTTP " + response.statusCode() + "): " + response.body());
        }
    }

    @Override
    public void addComment(String mrId, String comment) throws Exception {
        String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
        String url = String.format("%s/api/v4/projects/%s/merge_requests/%s/notes", 
            baseUrl, encodedPath, mrId);
        
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("body", comment);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("PRIVATE-TOKEN", token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String findExistingMr(String sourceBranch, String targetBranch) throws Exception {
        String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
        String url = String.format("%s/api/v4/projects/%s/merge_requests?source_branch=%s&target_branch=%s&state=opened", 
            baseUrl, encodedPath, 
            URLEncoder.encode(sourceBranch, StandardCharsets.UTF_8),
            URLEncoder.encode(targetBranch, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("PRIVATE-TOKEN", token)
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode mrs = objectMapper.readTree(response.body());
            if (mrs.isArray() && mrs.size() > 0) {
                return mrs.get(0).get("web_url").asText();
            }
        }
        
        return "MR exists but could not find URL";
    }
}

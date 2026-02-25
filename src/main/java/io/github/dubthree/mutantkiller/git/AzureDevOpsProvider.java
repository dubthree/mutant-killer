package io.github.dubthree.mutantkiller.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Azure DevOps implementation of GitProvider.
 */
public class AzureDevOpsProvider implements GitProvider {

    private final String token;
    private final String organization;
    private final String project;
    private final String repository;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AzureDevOpsProvider(String token, String organization, String project, String repository) {
        this.token = token;
        this.organization = organization;
        this.project = project;
        this.repository = repository;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String name() {
        return "Azure DevOps";
    }

    @Override
    public String createPullRequest(String headBranch, String baseBranch, String title, String body) 
            throws Exception {
        
        String url = String.format(
            "https://dev.azure.com/%s/%s/_apis/git/repositories/%s/pullrequests?api-version=7.0",
            organization, project, repository);
        
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("sourceRefName", "refs/heads/" + headBranch);
        payload.put("targetRefName", "refs/heads/" + baseBranch);
        payload.put("title", title);
        payload.put("description", body);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Basic " + encodeToken())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 201) {
            JsonNode responseJson = objectMapper.readTree(response.body());
            int prId = responseJson.get("pullRequestId").asInt();
            return String.format("https://dev.azure.com/%s/%s/_git/%s/pullrequest/%d",
                organization, project, repository, prId);
        } else if (response.statusCode() == 409) {
            // PR might already exist
            return findExistingPr(headBranch, baseBranch);
        } else {
            throw new IOException("Failed to create PR (HTTP " + response.statusCode() + "): " + response.body());
        }
    }

    @Override
    public void addComment(String prId, String comment) throws Exception {
        String url = String.format(
            "https://dev.azure.com/%s/%s/_apis/git/repositories/%s/pullrequests/%s/threads?api-version=7.0",
            organization, project, repository, prId);
        
        ObjectNode commentNode = objectMapper.createObjectNode();
        commentNode.put("content", comment);
        commentNode.put("commentType", 1); // Text comment
        
        ArrayNode comments = objectMapper.createArrayNode();
        comments.add(commentNode);
        
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("comments", comments);
        payload.put("status", 1); // Active

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Basic " + encodeToken())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();

        HttpResponse<String> commentResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (commentResponse.statusCode() < 200 || commentResponse.statusCode() >= 300) {
            throw new IOException("Failed to add comment (HTTP " + commentResponse.statusCode() + "): "
                + commentResponse.body());
        }
    }

    private String findExistingPr(String sourceBranch, String targetBranch) throws Exception {
        String url = String.format(
            "https://dev.azure.com/%s/%s/_apis/git/repositories/%s/pullrequests?searchCriteria.sourceRefName=refs/heads/%s&searchCriteria.targetRefName=refs/heads/%s&searchCriteria.status=active&api-version=7.0",
            organization, project, repository, sourceBranch, targetBranch);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Basic " + encodeToken())
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode responseJson = objectMapper.readTree(response.body());
            JsonNode prs = responseJson.get("value");
            if (prs != null && prs.isArray() && prs.size() > 0) {
                int prId = prs.get(0).get("pullRequestId").asInt();
                return String.format("https://dev.azure.com/%s/%s/_git/%s/pullrequest/%d",
                    organization, project, repository, prId);
            }
        }
        
        return "PR exists but could not find URL";
    }

    /**
     * Azure DevOps uses Basic auth with empty username and PAT as password.
     */
    private String encodeToken() {
        String auth = ":" + token;
        return Base64.getEncoder().encodeToString(auth.getBytes());
    }
}

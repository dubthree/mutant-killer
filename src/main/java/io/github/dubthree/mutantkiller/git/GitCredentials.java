package io.github.dubthree.mutantkiller.git;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HTTP credentials for git operations. The token is sent as an {@code Authorization} header
 * per command and never written into the clone's remote URL or config.
 *
 * @param username provider specific user name ({@code x-access-token} for GitHub, {@code oauth2}
 *                 for GitLab, empty for Azure DevOps)
 * @param token    personal access token
 */
public record GitCredentials(String username, String token) {

    public String basicAuthHeader() {
        String raw = (username == null ? "" : username) + ":" + token;
        return "AUTHORIZATION: basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}

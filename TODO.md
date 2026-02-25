# Mutant Killer: Remaining Issues

## Security

- **Token visible in process listings**: `--token` CLI flag and `GitProvider.injectAuth()` embed tokens in URLs passed to `ProcessBuilder`, making them visible via `ps`. Consider using `git credential-store` or a credential helper instead.
- **`git push --force` in RepositoryManager**: A bug in branch naming could force-push to protected branches. Add a safeguard to refuse force-push to `main`/`master`.
- **MutantKillerConfig record**: The custom `toString()` now excludes the API key, but the record's auto-generated `hashCode`/`equals` still include it. Not a security risk, but worth noting.

## Missing Verification

- **Generated code is never compiled before committing**: Both `RunCommand` and `KillCommand` apply LLM-generated test code without running `mvn test` or `gradle test` first. This means PRs may contain code that does not compile. Add a verification step between `improvement.apply()` and `commitAndPush()`.
- **No validation that applied tests actually kill the mutant**: After generating and applying test improvements, the tool should re-run PIT (or at least the new test) to confirm the mutant is actually killed.

## Error Handling

- **HTTP response null safety in git providers**: `responseJson.get("html_url")`, `responseJson.get("web_url")`, and `responseJson.get("pullRequestId")` could return null if the API response shape changes. Add null checks with descriptive error messages.
- **MutantAnalyzer.analyze() line number validation**: If `mutation.lineNumber()` is 0, negative, or exceeds the file length, the context extraction produces incorrect results silently.

## Incomplete Features

- **`analyze.md` prompt template unused**: The Handlebars-style template at `src/main/resources/prompts/analyze.md` is never referenced in code. `MutantAnalysis.buildAnalysisPrompt()` constructs the prompt inline. Either wire up a template engine or remove the file.
- **BuildExecutor verbose flag ignored**: `MavenExecutor.runMutationTesting()` always passes `false` to `execute(command, false)`. The verbose flag from CLI options should be threaded through so users can see build output.
- **Source file search is limited**: `MutantAnalyzer.findSourceFile()` only checks the exact package path. No recursive search or multi-module project support.
- **Test file discovery is limited**: Only looks for `ClassNameTest.java`. Does not check `TestClassName.java`, `ClassNameTests.java`, or tests in different packages.

## Configuration

- **No project-level config file**: The tool only accepts configuration via CLI flags and environment variables. A `.mutant-killer.yml` or similar would let teams persist settings in their repository.
- **maxTokens hardcoded to 2048**: In `TestImprover.java`, the Claude API max tokens is fixed. For complex test improvements this may be insufficient. Should be configurable.
- **Context window hardcoded to 5 lines**: `MutantAnalyzer` uses `mutationLine +/- 5` as magic numbers. Should be configurable.
- **Build timeout hardcoded to 30 minutes**: In `BuildExecutor.execute()`. Should be configurable via CLI or config.
- **Git timeout hardcoded to 5 minutes**: In `RepositoryManager`. Should be configurable.

## Resource Management

- **HttpClient instances never closed**: All three git providers (`GitHubProvider`, `GitLabProvider`, `AzureDevOpsProvider`) create `HttpClient` instances that are never closed. Since Java 21, `HttpClient` implements `AutoCloseable`.
- **AnthropicClient never closed**: `TestImprover` creates an `AnthropicOkHttpClient` that wraps OkHttp connection/thread pools but is never closed.
- **Temporary directories never cleaned up**: Cloned repositories in the work directory are never deleted, even on successful completion.

## CLI Usability

- **No progress reporting for long operations**: Mutation testing can run for 30+ minutes with zero output when verbose is off. Add a spinner or periodic status message.
- **No `--format` option on `analyze` command**: Output is only human-readable text. JSON or CSV output would help CI/CD integration.
- **Subcommands lack `mixinStandardHelpOptions`**: Only the top-level command has `--help`/`--version` via picocli mixin.

## Architecture

- **No retry logic for API calls**: Claude API and git provider API calls have no retry on transient failures (429, 500, 503).
- **No rate limiting for Claude API calls**: Processing multiple mutants fires API calls in a tight loop. Should add a delay or respect rate limit headers.
- **Each mutant gets its own branch and PR**: Related mutants in the same class/method should be grouped into a single PR to avoid PR spam.
- **No idempotency checking**: Repeated runs re-analyze and re-call the Claude API for mutants that already have open PRs.
- **No Bitbucket support**: Only GitHub, GitLab, and Azure DevOps are supported.
- **No multi-module Maven/Gradle project support**: Source and test directories are hardcoded to `src/main/java` and `src/test/java`.

## Code Quality

- **TestImproverTest tests a reimplemented copy of extractCodeBlock()**: The test class reimplements the private method rather than using reflection to call the actual one. If the real method changes, tests still pass.
- **RunCommandTest uses heavy reflection**: Private utility methods (`extractRepoName`, `simpleClassName`, `generateMutantId`, `buildPrBody`) are tested via `setAccessible(true)`. These should be extracted to a package-private utility class.
- **Logging framework configured but unused**: SLF4J/Logback are dependencies with `logback.xml` configured, but no source file uses `Logger`. All output goes through `System.out`/`System.err`.

## Build/Packaging

- **picocli-codegen generates native-image config but no GraalVM build**: The annotation processor generates `reflect-config.json` for native images, but there is no `native-maven-plugin` or GraalVM build profile. Either add native-image support or remove the annotation processor.
- **No mocking library for tests**: Without Mockito or similar, HTTP-based classes and the Claude API client cannot be properly unit tested without hitting real APIs.

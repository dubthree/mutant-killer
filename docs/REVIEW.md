# Code review: mutant-killer (as of commit 9f50fc6)

This is the review that preceded the 0.2.0 changes. Each finding says whether it was fixed
in this branch or left as a follow-up.

## Blocking defects (fixed)

| # | Finding | Impact | Status |
|---|---------|--------|--------|
| 1 | `TestImprover` cast `ContentBlock` to `TextBlock`; the Anthropic SDK returns a union type, so **the project did not compile** (`mvn package` failed). | Nothing could run. | Fixed: `block.text()` optional access, SDK bumped 1.0.0 to 2.69.0. |
| 2 | `PitReportParser` mapped `mutations.xml` onto package-private fields without `@JsonAutoDetect`, so Jackson never populated them, and `FAIL_ON_UNKNOWN_PROPERTIES` would reject PIT's `<indexes>`/`<blocks>`/`<killingTests>` elements. | Every mutant parsed as an empty record, or parsing threw. | Fixed; covered by `PitReportParserTest` and validated against a real PIT 1.30 report. |
| 3 | Generated tests were **never compiled or run** before a PR was opened. There was also no check that the mutant actually died. | PRs with broken or useless tests. | Fixed: `KillLoop` applies the test, runs it with the build tool, re-runs PIT scoped to the class, and retries with the failure fed back to the model (`--max-attempts`). |
| 4 | `createNewTestFile()` derived the directory from PIT's `sourceFile`, which is a bare file name (`Foo.java`), so `getParent()` returned null and the fallback wrote relative to the CWD. | NPE or test written outside the clone. | Fixed: new tests go under the module's `src/test/java` with the right package. |
| 5 | Inner classes (`Foo$Bar`) and default-package classes crashed `findSourceFile`/`findTestFile` (`lastIndexOf('.')` returning -1, `$` in paths). | Analysis failed for any mutant in an inner class. | Fixed in `MutantAnalyzer`; tests cover both. |
| 6 | Merging used `cu.toString()`, which re-prints the whole file with JavaParser's formatter. | Every PR rewrote the entire test file, hiding the actual change. | Fixed: `TestFileMerger` does line-based inserts/replacements and leaves the rest byte-identical (CRLF preserved). |
| 7 | The system prompt asked for imports "at the top of your response", but the merge wrapped the reply in `class Temp { ... }`, so any reply with imports failed to parse and was appended verbatim inside the class body. | Compile errors on exactly the replies the prompt asked for. | Fixed: imports are split out and inserted after the existing imports (wildcards respected). |
| 8 | PIT was invoked assuming the project already configures `pitest-maven` with the JUnit 5 plugin. Most repositories do not; JUnit 5 projects then report every mutant as `NO_COVERAGE`. | Tool unusable on typical repos. | Fixed: `MavenPitInjector` adds the plugin (and the JUnit 5 / TestNG plugin when needed) to a temporary copy of `pom.xml` and restores it; Gradle gets the plugin via an init script. |
| 9 | `--base-branch` defaulted to `main`; projects on `master` failed at clone. | Clone failure. | Fixed: default branch is detected from `origin/HEAD`. |
| 10 | The token was embedded in the clone URL, so it was stored in `.git/config` and printed in git error output. | Credential leak on disk and in logs. | Fixed: per-command `http.<host>.extraheader`, and all git output is masked. |
| 11 | Build output was discarded unless `--verbose`, and the 30-minute PIT timeout was hard-coded. | Failures were opaque; large projects timed out. | Fixed: every build writes a log file; failures include the tail; `--build-timeout`. |
| 12 | `git add -A` committed everything in the working tree, and the PR body claimed a verification that never happened. | Noise in PRs; misleading description. | Fixed: only the test file is committed; the PR body states what was verified. |

## Design gaps (fixed)

- **No way to use a Claude subscription.** Only `ANTHROPIC_API_KEY` worked. Added a pluggable
  `LlmClient` with an `AnthropicApiClient` and a `ClaudeCliClient` that drives `claude -p`
  (tools disabled, JSON output, prompt on stdin, run from an empty directory so the project's
  `CLAUDE.md` is never loaded). `--backend auto` prefers the API key and falls back to the CLI.
- **Template support was fictional.** `analyze.md` with `{{placeholders}}` shipped but was
  never loaded; the prompt was hard-coded in `MutantAnalysis.buildAnalysisPrompt()`. Added
  `PromptTemplate` (variables + `{{#if}}` blocks); both templates are now used and overridable
  with `--prompt-dir`.
- **Framework blindness.** Prompts always demanded JUnit 5, so JUnit 4 / TestNG projects got
  uncompilable tests. The framework is now detected from the existing test file first, then the
  build files, and passed into the prompt and into new test files.
- **Single source root.** Multi-module projects and tests living in a different package (very
  common, e.g. `org.json.junit.JSONObjectTest`) were not found. Source and test roots are now
  discovered across modules and test files are searched by name.
- **Mutant selection** took the first N survivors, which are usually all in the first class.
  Selection now round-robins across classes; `--include-no-coverage` opts into NO_COVERAGE
  mutants; `--target-classes` scopes PIT.
- **No tests, no report.** Added 68 unit tests and a `--report file.json` option with per-mutant
  outcomes, attempts, token usage and cost.
- `--dry-run` no longer needs a token for public repositories, and `kill` works on any local
  checkout (it runs PIT itself when no report is given).

## Follow-ups (not done here)

1. **Scoped verification when the project already configures PIT.** Maven lets an explicit
   `<configuration><targetClasses>` win over `-DtargetClasses`, so for such projects the
   verification run is a full PIT run. Writing a temporary profile would fix this.
2. **Gradle path is tested only with unit tests.** The init-script approach was not exercised
   against a real Gradle project in this session.
3. **Parallelism.** Mutants are processed sequentially. Each one needs its own clone/worktree to
   parallelize safely; `git worktree` would make that cheap.
4. **Cost caps.** There is no `--max-cost` guard; the report shows spend after the fact.
5. **PR hygiene.** One PR per mutant is noisy on real projects. Grouping by test class
   (`--group-by-class`) would be a natural next option.
6. **Prompt quality.** The retry feedback is the build log tail; summarising the first compile
   error and the relevant assertion failure would shorten prompts and likely raise the hit rate.
7. **`addComment` on providers is unused.** Either wire it up (e.g. to post the verification
   log) or remove it.
8. **Prompt caching on the API backend.** Prompts are 15-30k tokens and the system prompt plus
   source file are identical across attempts; a `cache_control` breakpoint after the source
   section would cut input cost substantially for retries. (The CLI backend already reports
   cache reads.)
9. **Equivalent-mutant heuristics.** Boundary mutants of the form `if (x > n) x = n;` are
   equivalent by construction; detecting the pattern statically would skip the model call
   entirely.
10. **Crash safety of the pom injection.** If the JVM is killed mid-run the injected
    `pitest-maven` plugin stays in the clone's `pom.xml`. Writing the backup to disk and
    restoring it at the next start would close that gap.

See `LIVE_RUNS.md` for the three public-repository runs that exercised the new pipeline.

# Mutant Killer 🔪

An autonomous agent that runs [PIT](https://pitest.org/) mutation testing on a Java project, asks
Claude for a test that kills each surviving mutant, **proves the test works** (it must compile,
pass, and make PIT report the mutant as killed), and opens one pull request per fix.

## How it works

```
clone repo ─► run PIT ─► pick surviving mutants ─► for each mutant:
                                                      ├─ locate source + test class
                                                      ├─ ask Claude for a test
                                                      ├─ apply, compile, run the test class
                                                      ├─ re-run PIT scoped to the class
                                                      ├─ failed? feed the error back, retry
                                                      └─ verified? commit, push, open PR
```

PIT does not need to be configured in the target project: the Maven plugin (or the Gradle
plugin, through an init script) is added temporarily for the run and removed afterwards.

## Quick start

```bash
mvn -q package      # builds target/mutant-killer-0.2.0-SNAPSHOT.jar
alias mutant-killer='java -jar target/mutant-killer-0.2.0-SNAPSHOT.jar'

# Try it on a public repo without touching GitHub: generates and verifies tests in a local clone
mutant-killer run https://github.com/stleary/JSON-java --dry-run --max-mutants 5 \
    --target-classes 'org.json.CDL*' --target-tests 'org.json.junit.*' --report cdl.json

# The real thing: one PR per killed mutant
export GITHUB_TOKEN=...
mutant-killer run https://github.com/you/your-repo --max-mutants 10
```

### Choosing how Claude is called

| `--backend` | Uses | Needs |
|-------------|------|-------|
| `api`  | Anthropic Messages API (official Java SDK) | `ANTHROPIC_API_KEY` |
| `cli`  | Claude Code CLI (`claude -p`), i.e. your Claude subscription or an already logged-in session | `claude` on the PATH, logged in |
| `auto` (default) | `api` when the key is set, otherwise `cli` | |

The CLI backend runs with tools disabled and no session persistence, so each call is a plain
completion billed to your Claude plan rather than to API credits. `--model` accepts a full id
(`claude-opus-5-5`, the default) or, with the CLI backend, an alias such as `sonnet`.

## Commands

### `run <repo-url>` — clone, test, open PRs

| Option | Default | Meaning |
|--------|---------|---------|
| `-b, --base-branch` | remote default | Branch to start from |
| `--dry-run` | off | Generate and verify in the clone only; no commit/push/PR, no token needed |
| `--max-mutants` | 10 | Mutants to process (spread round-robin across classes) |
| `--max-attempts` | 3 | Model retries per mutant, each with the previous failure as feedback |
| `--no-verify` | off | Skip the scoped PIT re-run (faster, but only "tests pass" is checked) |
| `--include-no-coverage` | off | Also target `NO_COVERAGE` mutants (needs brand-new tests) |
| `--target-classes` | project default | PIT `targetClasses` glob(s), e.g. `com.acme.util.*` |
| `--target-tests` | same as target classes | PIT `targetTests` glob(s); set it when tests live in another package, e.g. `org.json.junit.*` |
| `--build-system` | auto | `maven` or `gradle` when both files exist |
| `--build-timeout` | 60 | Minutes per build/PIT invocation |
| `--work-dir` | temp dir | Where to clone; logs land in `<work-dir>/logs` |
| `--report` | none | Write a JSON report (outcomes, attempts, tokens, cost) |
| `--token` | `GITHUB_TOKEN`/`GITLAB_TOKEN`/`AZURE_DEVOPS_TOKEN` | Hosting token for push + PR |
| `--prompt-dir` | bundled | Custom `system.md` / `analyze.md` |
| `-v` | off | Stream build output |

### `kill [mutations.xml] --project <dir>` — local, no git

Same loop on a checkout you already have. If no report is given PIT is run first. Changes stay in
the working tree for you to review with `git diff`.

### `analyze <mutations.xml>...` — just read a report

Prints counts per status, the mutation score, and the surviving mutants.

## What "verified" means

A mutant counts as killed only when, after adding the generated test:

1. the build tool compiles and runs the test class successfully, **and**
2. PIT, re-run with `targetClasses=<mutated class>*` and `targetTests=<test class>`, reports that
   exact mutant as `KILLED` (or `TIMED_OUT`).

If either step fails the change is reverted, the failure (compiler output, failing assertion, or
"mutant still survived") is appended to the next prompt, and the model gets another attempt.
Every outcome is recorded in the `--report` JSON.

## Custom prompts

Copy `src/main/resources/prompts/` somewhere, edit, and pass `--prompt-dir`. `analyze.md`
supports `{{variable}}` and `{{#if variable}}...{{/if}}`. Variables: `mutatedClass`,
`mutatedMethod`, `lineNumber`, `mutator`, `mutatorDescription`, `contextAroundMutation`,
`methodSource`, `sourceFile`, `sourceCode`, `testClassName`, `testFramework`,
`existingTestCode`, `feedback` (previous failed attempts).

## Supported git providers

| Provider | URL pattern | Token |
|----------|-------------|-------|
| GitHub | `github.com/owner/repo` | PAT with `repo` scope |
| GitLab (incl. self-hosted) | `gitlab.example.com/group/sub/repo` | PAT with `api` scope |
| Azure DevOps | `dev.azure.com/org/project/_git/repo` | PAT with Code read/write |

Tokens are sent as a per-command HTTP header and never written into the clone.

## Requirements

- Java 21+ to run mutant-killer; the target project builds with its own wrapper (`mvnw`/`gradlew`) or `mvn`/`gradle` on the PATH
- Target project: Maven or Gradle, JUnit 4, JUnit 5 or TestNG, compiling with passing tests
- An Anthropic API key **or** a logged-in Claude Code CLI

## Limitations

- Java only.
- If the project already configures PIT with explicit `targetClasses`, Maven ignores the
  command-line override, so the verification run is a full PIT run (slower, still correct).
- One PR per mutant; large projects will want `--max-mutants` kept small or `--target-classes`.
- Generated tests should still be reviewed before merging.

## Development

```bash
mvn test                                    # unit tests
mvn test-compile org.pitest:pitest-maven:mutationCoverage   # PIT on mutant-killer itself
```

See `docs/REVIEW.md` for the review that drove the 0.2.0 changes and the open follow-ups, and
`docs/LIVE_RUNS.md` for results against JSON-java and Apache commons-text (13 of 15 mutants
killed and verified; the other 2 were equivalent mutants and correctly left alone).

## License

MIT

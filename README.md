# Mutant Killer 🔪

An autonomous agent that clones a repository, runs [PIT](https://pitest.org/) mutation testing, and creates pull requests to kill surviving mutants.

## What It Does

1. **Clones** a GitHub repository
2. **Runs** PIT mutation testing
3. **Analyzes** each surviving mutant with Claude
4. **Creates PRs** — one branch and PR per mutant fix

## Quick Start

```bash
# Set your tokens
export ANTHROPIC_API_KEY=your_anthropic_key
export GITHUB_TOKEN=your_github_token

# Run against a repository
java -jar mutant-killer.jar run https://github.com/user/repo
```

That's it. The agent will:
1. Clone the repo
2. Run mutation tests
3. Create a PR for each surviving mutant it can fix

## Installation

```bash
git clone https://github.com/dubthree/mutant-killer.git
cd mutant-killer
mvn clean package
```

## Usage

### Full Autonomous Mode

```bash
java -jar target/mutant-killer-0.1.0-SNAPSHOT.jar run https://github.com/user/repo \
  --max-mutants 10 \
  --model claude-opus-4-20250514
```

Options:
- `--base-branch`: Branch to work from (default: `main`)
- `--model`: Claude model (default: `claude-sonnet-4-20250514`)
- `--max-mutants`: Max mutants to process (default: 10)
- `--dry-run`: Analyze without creating PRs
- `--work-dir`: Where to clone repos
- `--prompt-dir`: Custom prompt templates
- `--verbose`: Show detailed output

### Dry Run (Preview)

```bash
java -jar target/mutant-killer-0.1.0-SNAPSHOT.jar run https://github.com/user/repo --dry-run
```

Shows what fixes would be generated without creating any PRs.

### Analyze Existing Report

If you already have a PIT report:

```bash
java -jar target/mutant-killer-0.1.0-SNAPSHOT.jar analyze path/to/mutations.xml
```

### Local Kill Mode

Generate fixes for a local project without PRs:

```bash
java -jar target/mutant-killer-0.1.0-SNAPSHOT.jar kill path/to/mutations.xml \
  --source src/main/java \
  --test src/test/java
```

## Custom Prompts

You can customize how mutants are analyzed by providing your own prompt templates.

Create a directory with your prompts:

```
my-prompts/
├── system.md    # System prompt for Claude
└── analyze.md   # Per-mutant analysis prompt
```

Then run with:

```bash
java -jar mutant-killer.jar run https://github.com/user/repo --prompt-dir ./my-prompts
```

### Default Prompts

See `src/main/resources/prompts/` for the default templates you can customize.

**system.md** — Defines Claude's role and guidelines for generating tests.

**analyze.md** — Template for each mutation, with placeholders:
- `{{mutatedClass}}`, `{{mutatedMethod}}`, `{{lineNumber}}`
- `{{mutatorDescription}}`, `{{contextAroundMutation}}`
- `{{existingTestCode}}`

## How It Works

```
┌──────────────────┐
│  Clone Repo      │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  Run PIT         │──► Detect Maven/Gradle
└────────┬─────────┘    Run mutation tests
         │
         ▼
┌──────────────────┐
│  Parse Report    │──► Find surviving mutants
└────────┬─────────┘
         │
         ▼
┌──────────────────────────────────────────┐
│  For each mutant:                        │
│  1. Create branch: mutant-killer/fix-xxx │
│  2. Analyze with Claude                  │
│  3. Generate test improvement            │
│  4. Commit and push                      │
│  5. Create PR                            │
└──────────────────────────────────────────┘
```

## Requirements

- Java 21+
- Maven or Gradle (for target projects)
- PIT plugin configured in target project (or uses default config)
- Git hosting token (GitHub, GitLab, or Azure DevOps)
- Anthropic API key

## Supported Git Providers

| Provider | URL Pattern | Token Type |
|----------|-------------|------------|
| **GitHub** | `github.com/owner/repo` | Personal Access Token |
| **GitLab** | `gitlab.com/group/repo` | Personal Access Token |
| **Azure DevOps** | `dev.azure.com/org/project/_git/repo` | Personal Access Token |

Self-hosted GitLab instances are also supported. The provider is auto-detected from the URL.

## PR Format

Each generated PR includes:
- **Title**: `Kill mutant in ClassName.methodName`
- **Body**: Mutation details, explanation, and the generated test code
- **Branch**: `mutant-killer/fix-<class>-<method>-<line>-<index>`

## Supported Models

- `claude-opus-4-20250514` — Most capable, best for complex mutations
- `claude-sonnet-4-20250514` — Default, good balance of speed and quality

## Limitations

- Java projects only (Maven or Gradle)
- Requires PIT for mutation testing
- Target project must compile and have tests
- Generated tests should be reviewed before merging

## Contributing

Areas of interest:
- Support for Gradle Kotlin DSL
- Support for other languages (Stryker for JS/TS, mutmut for Python)
- Improved prompt engineering
- Batch PR creation
- Integration with CI/CD

## License

MIT

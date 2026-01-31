# Mutant Killer 🔪

An autonomous agent that analyzes [PIT](https://pitest.org/) mutation test results and improves your tests to kill surviving mutants.

## What It Does

1. **Analyzes** PIT mutation reports to find surviving mutants
2. **Understands** what code behavior the mutation changes
3. **Generates** new test methods or improves existing ones using Claude
4. **Applies** the changes to your test files (or shows a dry-run preview)

## Why?

Mutation testing tells you *which* mutations survive, but not *how* to fix your tests. Mutant Killer bridges that gap by using AI to understand what's missing and generate targeted test improvements.

## Installation

```bash
# Clone the repo
git clone https://github.com/dubthree/mutant-killer.git
cd mutant-killer

# Build
mvn clean package

# Or install locally
mvn install
```

## Usage

### Prerequisites

1. Set your Anthropic API key:
   ```bash
   export ANTHROPIC_API_KEY=your_key_here
   ```

2. Run PIT on your project to generate a mutations report:
   ```bash
   mvn pitest:mutationCoverage
   ```

### Analyze Surviving Mutants

```bash
java -jar target/mutant-killer-0.1.0-SNAPSHOT.jar analyze path/to/pit-reports/mutations.xml
```

Or with Maven:
```bash
mvn exec:java -Dexec.args="analyze path/to/pit-reports/mutations.xml"
```

### Kill Surviving Mutants

```bash
java -jar target/mutant-killer-0.1.0-SNAPSHOT.jar kill path/to/pit-reports/mutations.xml \
  --source src/main/java \
  --test src/test/java \
  --dry-run
```

Options:
- `--source`, `-s`: Path to main source directory
- `--test`, `-t`: Path to test source directory  
- `--model`: Claude model to use (default: `claude-sonnet-4-20250514`)
- `--dry-run`: Show proposed changes without applying
- `--max-mutants`: Maximum mutants to process (default: 10)
- `--verbose`, `-v`: Verbose output

### Example

```bash
# Run PIT first
mvn pitest:mutationCoverage

# Analyze what survived
java -jar target/mutant-killer-0.1.0-SNAPSHOT.jar analyze target/pit-reports/mutations.xml

# Generate improvements (dry run)
java -jar target/mutant-killer-0.1.0-SNAPSHOT.jar kill target/pit-reports/mutations.xml \
  -s src/main/java \
  -t src/test/java \
  --dry-run

# Apply improvements
java -jar target/mutant-killer-0.1.0-SNAPSHOT.jar kill target/pit-reports/mutations.xml \
  -s src/main/java \
  -t src/test/java

# Verify mutations are now killed
mvn pitest:mutationCoverage
```

## Configuration

### Model Selection

The default model is `claude-sonnet-4-20250514`. For more complex mutations, you might want to use Claude Opus:

```bash
java -jar target/mutant-killer-0.1.0-SNAPSHOT.jar kill mutations.xml \
  -s src/main/java -t src/test/java \
  --model claude-opus-4-20250514
```

### Supported Models

- `claude-opus-4-20250514` (most capable)
- `claude-sonnet-4-20250514` (default, good balance)
- Any other Anthropic model identifier

## How It Works

1. **Parse PIT Report**: Reads the XML mutations report to identify surviving mutants
2. **Gather Context**: Finds the source file, extracts the mutated method, locates existing tests
3. **Analyze with Claude**: Sends mutation details and code context to Claude
4. **Generate Tests**: Claude generates test methods specifically designed to catch the mutation
5. **Apply Changes**: Adds new methods to existing test classes or creates new test files

## Limitations

- Currently supports Java projects only
- Requires PIT for mutation testing
- Generated tests should be reviewed before committing
- Complex mutations may require manual refinement

## Contributing

Contributions welcome! Areas of interest:
- Support for other mutation testing tools (Stryker, mutmut)
- Support for other languages
- Improved test generation strategies
- Better code modification handling

## License

MIT

## Credits

Built with:
- [PIT](https://pitest.org/) - Mutation testing for Java
- [Claude](https://anthropic.com) - AI for test generation
- [JavaParser](https://javaparser.org/) - Java code analysis
- [picocli](https://picocli.info/) - CLI framework

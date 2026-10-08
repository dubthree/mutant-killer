# Live runs against public repositories

All runs used `--dry-run` (local clone only, nothing pushed), the Claude Code CLI backend
(`--backend cli`, billed to the Claude plan rather than API credits) and the default model
`claude-opus-5-5`. "Verified" means the generated test compiled, passed, and a scoped PIT re-run
reported the mutant as killed. Generated diffs and full JSON reports are in `live-runs/`.

| Run | Repository | Build | Framework | Target class | PIT baseline | Processed | Verified kills | Attempts | Cost | Wall time / mutant |
|-----|------------|-------|-----------|--------------|--------------|-----------|----------------|----------|------|--------------------|
| 1 | [stleary/JSON-java](https://github.com/stleary/JSON-java) | Maven (PIT injected) | JUnit 4, tests in `org.json.junit` | `CDL` | 92 mutants, 87% score | 5 | **5** | 1,1,1,1,1 | $0.57 | ~41 s |
| 2 | [apache/commons-text](https://github.com/apache/commons-text) | Maven (PIT + JUnit 5 plugin injected) | JUnit 5 | `WordUtils` | 145 mutants, 93% score | 6 | **4** | 3,3,1,2,1,1 | $2.87 | ~110 s |
| 3 | stleary/JSON-java | Gradle 8.14 via init script (wrapper 6.3 too old for JDK 21) | JUnit 4 | `Cookie` | 54 mutants, 91% score | 4 | **4** | 2,1,1,1 | $0.43 | ~60 s |

Totals: 15 mutants attempted, 13 killed and verified, 2 correctly rejected, 0 errors, about
$3.90 of model usage.

## What the runs showed

**Run 1 (JSON-java / CDL).** The default branch (`master`) was detected, PIT was injected into a
pom that has no PIT configuration, the test class was found in a different package than the
class under test, and the JUnit 4 style was picked up from that file. Each of the five fixes is
a single focused test method (see `live-runs/json-java-CDL.diff`); the rest of the file is
byte-identical, including its missing trailing newline.

**Run 2 (commons-text / WordUtils).** The JUnit 5 plugin was injected alongside PIT and the
parent pom's quality gates (checkstyle, spotbugs, japicmp, ...) were skipped for the scratch
build. Two of the six survivors are *equivalent mutants* (`lower > len` to `lower >= len`
guarding `lower = len`): the model's tests compiled and passed every time, PIT kept reporting
the mutant alive, and the loop gave up after three attempts without producing a fix. That is
the behaviour we want, but it cost about $0.78 per equivalent mutant; the `EQUIVALENT` verdict
added afterwards lets the model stop on the first attempt. One kill (`initials`, line 370) was
achieved by allocating a 1 GB string so the mutated buffer size overflowed: valid for PIT,
unacceptable for a reviewer. That led to the test-quality rules and the `UNTESTABLE` verdict in
`prompts/system.md`. Prompts here were ~30k tokens because the whole 850-line source and
600-line test class are included; cost scales with class size.

**Run 3 (JSON-java / Cookie, Gradle).** JSON-java pins Gradle 6.3, which cannot start on Java 21.
The executor noticed and used the Gradle on the PATH; the init script applied gradle-pitest-plugin
without touching the project's build files and honoured `-PmkTargetClasses`. The first attempt on
mutant 1 was lost to JSON-java's `jacocoTestReport` finalizer failing on a Maven Central 429
after the test had already passed; the loop now recognises download/network failures and retries
the build instead of charging the model, and the init script disables such report tasks.

## Reproducing

```bash
mvn -q package
J=target/mutant-killer-0.2.0-SNAPSHOT.jar
java -jar $J run https://github.com/stleary/JSON-java --dry-run --backend cli \
  --max-mutants 5 --target-classes 'org.json.CDL*' --target-tests 'org.json.junit.*' --report cdl.json
java -jar $J run https://github.com/apache/commons-text --dry-run --backend cli \
  --max-mutants 6 --target-classes 'org.apache.commons.text.WordUtils*' \
  --target-tests 'org.apache.commons.text.WordUtilsTest' --report wordutils.json
java -jar $J run https://github.com/stleary/JSON-java --dry-run --backend cli --build-system gradle \
  --max-mutants 4 --target-classes 'org.json.Cookie*' --target-tests 'org.json.junit.Cookie*' --report cookie.json
```

Drop `--dry-run` and set `GITHUB_TOKEN` to have the same runs open one PR per verified kill on a
repository you control.

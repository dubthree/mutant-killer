# Mutation analysis

A mutation testing tool (PIT) found a surviving mutation in this Java code.

## Mutation details

- **Class:** `{{mutatedClass}}`
- **Method:** `{{mutatedMethod}}`
- **Line:** {{lineNumber}}
- **Mutation:** {{mutatorDescription}}
- **Mutator:** `{{mutator}}`

## Code context

The `>>>` marker shows the mutated line:

```java
{{contextAroundMutation}}
```

{{#if methodSource}}
## Mutated method

```java
{{methodSource}}
```
{{/if}}

{{#if sourceCode}}
## Full source of `{{sourceFile}}`

```java
{{sourceCode}}
```
{{/if}}

## Test class

Add the new test to `{{testClassName}}`. Use {{testFramework}}.

{{#if existingTestCode}}
### Current content of `{{testClassName}}`

```java
{{existingTestCode}}
```
{{/if}}

{{#if feedback}}
## Previous attempts that did NOT work

Each attempt below was applied, compiled and run. Read the result carefully and do something
different this time: fix the compile error, fix the wrong expectation, or (if the mutant
survived) make the assertion depend on the mutated behaviour.

{{feedback}}
{{/if}}

## Task

Write a test method that will:
1. **FAIL** when this mutation is applied, and
2. **PASS** on the original code.

The test must exercise line {{lineNumber}} and assert on the behaviour the mutation changes.
Reply with a single Java code block containing any imports followed by the test method(s).

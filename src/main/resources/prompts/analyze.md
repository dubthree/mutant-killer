# Mutation Analysis

A mutation testing tool (PIT) found a surviving mutation in this Java code.

## Mutation Details
- **Class:** {{mutatedClass}}
- **Method:** {{mutatedMethod}}
- **Line:** {{lineNumber}}
- **Mutation type:** {{mutatorDescription}}

## Code Context

The `>>>` marker shows the mutated line:

```java
{{contextAroundMutation}}
```

## Full Method

```java
{{mutatedMethod}}
```

{{#if existingTestCode}}
## Existing Test Class

```java
{{existingTestCode}}
```
{{/if}}

## Task

Write a test method that will:
1. **FAIL** when this mutation is applied
2. **PASS** on the original code

This ensures the mutation gets killed.

The test should specifically verify the behavior at line {{lineNumber}} that the mutation changes.

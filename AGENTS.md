# AGENTS.md

## Project Objective
This fork exists to extend the IntelliJ BBCode plugin for Steam BBCode use cases, while keeping existing behavior stable unless intentionally changed.

Current high-priority feature targets:
- Support `[olist]...[/olist]`.
- Support nested lists such as `[list][list]...[/list][/list]`.

## Language Policy
Rules:
- Preserve the upstream section in `CHANGELOG.md` (`## Upstream History (before fork)`) as historical source text, including any Chinese entries.
- Outside that upstream history section, do not introduce Chinese text.

## Architecture Map
Primary edit points for BBCode behavior:
- Schema definition: `src/main/resources/schemas/standard.xml`
- Schema DTD: `src/main/resources/schemas/schema.dtd`
- Schema loading/resolution:
  - `src/main/kotlin/icu/windea/bbcode/lang/schema/BBCodeSchemaProvider.kt`
  - `src/main/kotlin/icu/windea/bbcode/lang/schema/BBCodeSchemaResolver.kt`
  - `src/main/kotlin/icu/windea/bbcode/lang/schema/BBCodeSchemaManager.kt`
- Validation:
  - `src/main/kotlin/icu/windea/bbcode/inspections/BBCodeSchemaValidationInspection.kt`
- Completion:
  - `src/main/kotlin/icu/windea/bbcode/codeInsight/completion/BBCodeTagNameCompletionProvider.kt`

Syntax source-of-truth:
- Parser grammar: `src/main/kotlin/icu/windea/bbcode/psi/BBCodeParser.bnf`
- Lexer grammar: `src/main/kotlin/icu/windea/bbcode/psi/BBCodeLexer.flex`

Generated sources (do not edit directly):
- `src/main/gen/**`

## Implementation Guidance
For Steam list support, prefer schema-first changes before parser changes.

Expected list-related schema work:
- Add tag `olist`.
- Update list container child rules to allow nested list containers where needed.
- Ensure `parentNames`/`childNames` are consistent for `list`, `ul`, `ol`, `olist`, `li`, and `*`.

After schema changes, verify:
- Inspections do not flag valid Steam list nesting as unexpected.
- Completion suggests list tags in valid nested contexts.

## Testing Workflow
Tests currently exist in:
- `src/test/kotlin/icu/windea/bbcode/test/BBCodeParsingTest.kt`
- `src/test/kotlin/icu/windea/bbcode/test/BBCodeSchemaResolvingTest.kt`
- `src/test/testData/Sample.bbcode`

When behavior changes:
- Add or update parsing/schema tests for Steam list cases.
- Keep sample data readable and focused on real BBCode snippets.
- Run `gradlew.bat test` on Windows.
- Clear IDE warnings in touched files before handoff (for example: unused imports, DevKit extension warnings, regex lint warnings, and test `tearDown`/`finally` warnings).

## Maintenance Notes
- Canonical README path is `README.md`; do not maintain a separate Chinese README.
- In `CHANGELOG.md`, keep upstream history intact (Chinese allowed there); keep fork release notes in English.
- Avoid manual edits under `src/main/gen`; regenerate from grammar files when needed.
- Preserve plugin compatibility settings unless feature work requires explicit version changes.

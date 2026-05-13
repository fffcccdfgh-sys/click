# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- `CONTEXT.md` at the repo root.
- `docs/adr/` for architectural decisions that touch the area being changed.

If any of these files do not exist, proceed silently. Do not flag their absence or suggest creating them upfront. Producer skills can create them lazily when terms or decisions are resolved.

## File structure

This is a single-context Android app repository:

```text
/
|-- CONTEXT.md
|-- docs/
|   `-- adr/
`-- app/
```

## Use the glossary's vocabulary

When output names a domain concept in an issue title, refactor proposal, hypothesis, or test name, use the term as defined in `CONTEXT.md`.

If the concept is not in the glossary yet, either reconsider whether the language belongs in this project or note the gap for a later documentation pass.

## Flag ADR conflicts

If output contradicts an existing ADR, surface it explicitly rather than silently overriding it.

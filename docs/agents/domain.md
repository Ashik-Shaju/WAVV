# Domain docs

This repository uses a single-context domain-doc layout.

## Before exploring

- Read root `CONTEXT.md` when it exists.
- Read relevant decisions in `docs/adr/` when that directory exists.
- If neither exists, proceed without creating placeholder files. Add a glossary or ADR only when a domain term or durable decision is actually resolved.

## Use the glossary

Use the terms defined in `CONTEXT.md` when it exists. If a needed concept is missing, resolve whether it is a genuine domain concept before adding it.

## Flag conflicts

If a proposed change contradicts an ADR, identify the ADR and explain why it may need to be reopened instead of silently overriding it.

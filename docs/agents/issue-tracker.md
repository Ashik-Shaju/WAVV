# Issue tracker: Local Markdown

Issues and specs for this repository live as Markdown files under `.scratch/`.

## Conventions

- One feature per directory: `.scratch/<feature-slug>/`.
- The feature spec is `.scratch/<feature-slug>/spec.md`.
- Implementation issues are one file per ticket under `.scratch/<feature-slug>/issues/`, numbered from `01`.
- An issue may use a `Status:` line near the top; use plain statuses such as `open`, `in progress`, or `resolved`.
- Append discussion under a `## Comments` heading.

## When a skill says to publish

Create or update the relevant file under `.scratch/<feature-slug>/`.

## When a skill says to fetch a ticket

Read the referenced file. The user will normally provide its path or issue number.

## Wayfinding files

- Map: `.scratch/<effort>/map.md`.
- Child tickets: `.scratch/<effort>/issues/NN-<slug>.md`.
- A child ticket may use `Type:` (`research`, `prototype`, `grilling`, or `task`), `Status:` (`claimed` or `resolved`), and `Blocked by:` lines.
- The frontier is the lowest-numbered open, unblocked, unclaimed child ticket.

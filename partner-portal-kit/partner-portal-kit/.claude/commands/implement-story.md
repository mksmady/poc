---
description: Implement one user story (US-001 to US-009) end to end
argument-hint: US-00x
---

Implement story $ARGUMENTS.

1. Read `CLAUDE.md`, `docs/stories/$ARGUMENTS.md`, `docs/build-plan.md` and every doc the story names.
2. Check the build-plan dependencies for this story are marked done. If not, stop and say which step is missing.
3. List the acceptance criteria you will implement and the files you expect to touch. If an open question in `docs/open-questions.md` blocks a criterion, use its default and say so.
4. Implement in this order: batch JSON changes (objects, templates, actions) → `partner-service` code with unit tests → custom element UI with component tests.
5. Run `/verify`.
6. Tick each finished criterion in the story file. Leave unfinished ones unticked with a one-line note.
7. Summarise what changed, what was tested and anything left open.

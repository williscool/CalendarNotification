---
name: plan-making
description: Creates development plan documents in docs/dev_todo/ using size-appropriate templates. Use when the user asks to plan a feature, write an RFC, create a dev plan, or when a task is complex enough to warrant a written plan before implementation.
---

# Plan Making

## CRITICAL: Never Use Cursor Plan Mode

**Do NOT use Cursor's built-in Plan Mode or create `.cursor/plans/*.plan.md` files.** That format is proprietary, throwaway-oriented, and not suitable for long-lived documentation. Plans in this project are learning artifacts kept in `docs/dev_todo/` (active) and `docs/dev_completed/` (done).

## Workflow

### Step 1: Gather Context

Before writing a plan:
- Read the GitHub issue (if linked)
- Check `docs/README.md` for related architecture docs
- Search `docs/dev_completed/` for prior art on similar work
- Search `docs/dev_todo/` for related active plans

### Step 2: Determine T-Shirt Size

Size based on **conceptual complexity**, not file count (a variable rename across 20 files is still small). Use these signals:

| Signal | S | M | L | XL |
|--------|---|---|---|-----|
| Scope boundary | One bug, one pattern, one screen | Single feature, single concern | End-to-end feature slice across layers | Multi-milestone program with child plans |
| Subsystem fan-out | Single subsystem | 1-2 subsystems | Multiple (UI + storage + background + settings) | Broad (nav + tabs + data + calendar + sync + tests) |
| Design decisions | None or obvious | 1-2 minor choices | Multiple options with tradeoffs | Decision tables, library evaluations |
| New architectural components | None | Maybe a helper | Named new class/pattern (e.g. `NotificationContext`, `FilterState`) | New navigation patterns, new packages |
| Independent deliverable phases | 1 | 2-3 | 4-6 across layers | 7+ grouped into milestones |
| Risk / rollback story | Trivial | Low | Feature flags, migration concerns | Legacy fallback, data loss prevention, phased rollout |

Pick the size that matches the majority of signals. When borderline, size down — you can always expand later.

### Step 3: Select Template

- **S or M** → Read [template-small.md](template-small.md) and follow it
- **L or XL** → Read [template-large.md](template-large.md) and follow it

### Step 4: Write the Plan

Output location: `docs/dev_todo/<snake_case_name>.md`

Filename should be descriptive and concise (e.g., `data_sync_improvements.md`, `events_view_lookahead.md`).

### Step 5: README Linking

**Do NOT add the plan to `docs/README.md` at creation time.** The README index is updated only when a feature is complete and the plan moves to `docs/dev_completed/`. During active development the plan lives in `docs/dev_todo/` without a README entry.

## Repo Conventions to Embed

Every plan should respect these project rules:

- **Tests first** — New features require tests before or during implementation
- **`CNPlusClockInterface`** — Never use `System.currentTimeMillis()` directly; use the clock interface for testable time-dependent code
- **No broad `Exception` catching** — Always catch specific exception types
- **Robolectric preferred** — Use Robolectric for logic tests; instrumentation only for real Android APIs (Calendar Provider, notifications, etc.)
- **Check existing docs** — Reference `docs/architecture/` and `docs/dev_completed/` for patterns and prior decisions
- **MockK limitations** — `mockkStatic`, `mockkConstructor`, `anyConstructed` fail in instrumentation tests; use dependency injection (see `docs/dev_completed/constructor-mocking-android.md`)
- **Concise code** — Everything it needs, nothing it doesn't

## Anti-Patterns

- **Over-specifying small plans**: Full code listings and test stubs in an S/M plan waste tokens and will change during implementation anyway
- **Implementation weeds in S/M plans**: Focus on *what* and *why*, not *how* — the how is discovered during implementation
- **Missing non-goals in L/XL plans**: Every large plan must explicitly state what is out of scope to prevent scope creep

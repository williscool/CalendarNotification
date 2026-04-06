# Large/XL Plan Template (L-XL)

Use for work touching 8+ files, requiring architecture decisions, new components, or phased rollout.

## Key Principle

Go deep. Large plans are long-lived reference documents. Future you (and the AI) will re-read them during implementation. Invest in thorough design decisions, non-goals, edge cases, and test stubs now to avoid rework and scope creep later.

## Template

```markdown
# Feature: [Title]

**GitHub Issue:** [#NNN](link)

## Background

[Problem statement, how it works today, history of related issues. Include tables of related issues if there's a trail.]

## Goal

[What we're building and what it enables for the user. 2-5 sentences.]

## Non-Goals

[Explicitly state what this plan does NOT cover. This is critical for preventing scope creep. Each item should be a concrete thing someone might reasonably expect to be included but isn't.]

- **[Thing A]** — [why it's out of scope or deferred]
- **[Thing B]** — [why]

## Key Decisions Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|

## Current Architecture (if modifying existing systems)

[Tables of existing components, data flow, key insight that unlocks the approach.]

## Design Decisions

[Detailed sections for non-obvious choices. Each with options considered and rationale.]

## Implementation Plan

### Phase 0: [Infrastructure / Setup]

[Substeps (0a, 0b, 0c if needed), each independently verifiable. Include code snippets, XML layouts, etc.]

### Phase N: [Name]

[Same pattern. Large plans typically have 4-10 phases grouped into milestones.]

### Milestone Checkpoint

[What milestone N delivers. Validate before proceeding.]

## Edge Cases & Error Handling

[Dedicated section for: empty states, null returns, permission denials, migration paths, graceful degradation.]

## Files to Modify/Create

### New Files

| File | Purpose |
|------|---------|

### Modified Files

| File | Changes |
|------|---------|

## Testing Plan

[Organized by Robolectric vs instrumentation. Full test stubs with @Test methods and Given/When/Then comments. Group by test class.]

### Unit Tests (Robolectric)

[Test class per component. Full method signatures with scenario comments.]

### Instrumentation Tests

[Only for things requiring real Android APIs.]

## Future Enhancements (if applicable)

[Numbered phases beyond current scope. Brief descriptions only.]

## Notes

[Design principles, behavior clarifications, technical notes that don't fit elsewhere.]

## Related Work

[Links to related docs in docs/architecture/, docs/dev_completed/, docs/dev_todo/.]
```

## Reference Examples

### events_view_lookahead.md (Size XL)

`docs/dev_todo/events_view_lookahead.md` — the gold standard for a large plan:

- **Key Decisions Summary** table up front for quick reference
- **UI Vision** with ASCII mockups
- **Current Architecture** tables showing existing components
- **Implementation Plan** with 7 phases grouped into 3 milestones
- **Phase substeps** (0a, 0b, 0c) each independently verifiable
- **Full code snippets** for Kotlin and XML
- **Edge Cases** section with empty states, error handling, refresh triggers
- **Testing Plan** with full `@Test` stubs organized by Robolectric vs instrumentation
- **Files to Modify/Create** split into New and Modified tables
- **Future Enhancements** as numbered phases beyond current scope
- **Notes** with design principles and technical clarifications
- Total: ~2000 lines

### settings_backup.md (Size L)

`docs/dev_completed/settings_backup.md` — good example of a large (not XL) plan:

- **Research Notes** documenting investigation findings
- **Current Architecture** of settings storage
- **4 phases** with clear boundaries
- **Permission Strategy** as a dedicated section (domain-specific concern)
- **Open Questions** with recommendations
- **Appendix** for implementation notes discovered during the build
- Total: ~360 lines

## Non-Goals Section Guidance

The Non-Goals section is **mandatory** for L/XL plans. Good non-goals are:

- Things adjacent to the feature that someone might assume are included
- Explicitly deferred work (with reasoning)
- Scope boundaries that prevent creep

**Example** (from a hypothetical navigation refactor):

```markdown
## Non-Goals

- **Bidirectional sync** — stays as-is; the delete+reupload approach sidesteps sync issues
- **Animated tab transitions** — visual polish deferred to a future phase
- **Deep link support** — existing deep links continue to work; new deep link routes are future work
- **Tablet layout** — single-column layout only; responsive layouts are a separate effort
```

## What L/XL Plans MUST Include (that S-M plans skip)

- Non-Goals section
- Key Decisions Summary table
- Full code snippets in implementation phases
- Edge Cases & Error Handling section
- Full `@Test` method stubs in Testing Plan
- Files to Modify/Create split into New and Modified
- Milestone checkpoints between major phases

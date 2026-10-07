# Small/Medium Plan Template (S-M)

Use for single-concern work with few or no design decisions.

## Key Principle

Focus on **what** and **why**, not implementation details. Code will change during the build — don't waste tokens specifying it upfront. Brief illustrative snippets are fine when they clarify an approach; full listings are not.

## Template

```markdown
# [Feature/Fix/Refactor]: [Title]

**GitHub Issue:** [#NNN](link)

## Overview

[1-3 sentences: what we're doing, why, and the key insight or approach]

## Background (if needed)

[Brief context: how it works today, related issues, why the current state is a problem. Skip if the overview is sufficient.]

## Plan

### Phase 1: [Name]

[What changes and why. List files involved. If a design choice exists, state the choice and rationale in 1-2 sentences. No full code listings.]

### Phase 2: [Name]

[Same pattern. Most S/M plans have 1-3 phases.]

## Files Changed Summary

| File | Change |
|------|--------|

## Testing

[What to test and how (Robolectric vs instrumentation). Describe test scenarios, don't write full test stubs.]

## Open Questions (if any)

[Unresolved decisions. Remove this section if there are none.]
```

## Example: Data Sync Improvements (Size M)

This is the plan from [#260](https://github.com/williscool/CalendarNotification/issues/260) — an excellent example of the right detail level for a medium plan:

- **Overview** frames the problem and goal in 2 sentences
- **Background** explains current workflow and why it's broken
- **Phases** name files and describe changes without full code
- **Phase 3 (future)** explicitly defers investigation — keeps scope tight
- Total: ~90 lines

## Example: Search Bar X of Y Count (Size S-M)

`docs/dev_todo/search_bar_x_of_y_count.md` — good example of a small-medium plan:

- **Design Decisions** table for the few choices that exist
- **Phases** include brief code fragments (5-10 lines) only where they clarify the approach
- **Testing** names specific test cases without writing full stubs
- Total: ~160 lines

## What NOT to Include in S-M Plans

- Full Kotlin/XML code listings (save for implementation)
- Full `@Test` method stubs with Given/When/Then
- ASCII UI mockups (if UI is simple enough to describe in words)
- Edge cases section (handle during implementation)
- Architecture diagrams
- Future enhancements beyond the immediate scope

# Refactor: Cross-Harness Agent Configuration

## Overview

Move the agent configuration out of `.cursor/` into open, cross-harness formats: rules become a root `AGENTS.md` ([agents.md](https://agents.md/)), and skills move to a harness-neutral `.skills/` directory following the [Agent Skills specification](https://agentskills.io/specification). Nothing about this config is Cursor-specific — it's project knowledge that any coding agent should be able to read.

## Background

All agent config currently lives under `.cursor/`, which ties it to one editor:

| Path | Contents | Problem |
|------|----------|---------|
| `.cursor/rules/main-rules.mdc` | Project coding rules (`alwaysApply: true`) | `.mdc` + frontmatter is a Cursor format; other agents ignore it |
| `.cursor/rules/wsl-unison-setup.mdc` | WSL/Unison/Windows build environment (`alwaysApply: false`) | Same, plus it's reference material rather than an always-on rule |
| `.cursor/skills/plan-making/` | Plan-making skill + 2 templates | Already spec-shaped; just in a vendor folder |
| `.cursor/skills/github-pr-comments/` | PR review comment fetching | Same |
| `.cursor/plans/*.plan.md` (6 files) | Cursor Plan Mode output | Proprietary throwaway format |

The irony worth noting: `plan-making/SKILL.md` itself says *"Do NOT use Cursor's built-in Plan Mode or create `.cursor/plans/*.plan.md` files"* — yet six of them are committed. They stay put (see Phase 4), but get labeled so no one mistakes them for current practice.

Skill discovery is the one genuinely unstandardized piece. The Agent Skills spec defines the *format* of a skill, not the *directory* agents scan. Claude Code reads `.claude/skills/`; Cursor reads `.cursor/skills/`. A bare `.skills/` is neutral but auto-discovered by nothing. Hence: canonical content in `.skills/`, with committed symlinks so both harnesses find it.

## Plan

### Phase 1: `AGENTS.md` at repo root

Convert `.cursor/rules/main-rules.mdc` → `AGENTS.md`, dropping the Cursor frontmatter (`description`/`globs`/`alwaysApply`). The spec requires no fields and prescribes no headings, so the body carries over nearly verbatim.

Content is preserved as-is — these are hard-won rules, not up for rewriting in a move:
- Code-change permission tiers (legacy 2016 codebase, tests-first)
- Test faithfulness / no-cheating
- Minimum viable solution, concise implementations
- Copyright header (William, inherited from Sergey Parshin 2020)
- Never catch broad `Exception`
- Never use `System.currentTimeMillis()` — use `CNPlusClockInterface`
- Key doc references

Two additions on top of the straight port:
- A short **Agent Configuration** section pointing at `.skills/` and the environment doc, so an agent landing in the repo can find everything from the root file.
- A brief project/build orientation line, since `AGENTS.md` is the first thing an unfamiliar agent reads.

`.mdc` frontmatter is NOT carried into `AGENTS.md`. `alwaysApply: true` is implicit — the root file always applies.

### Phase 2: WSL/Unison rule → `docs/build/`

`wsl-unison-setup.mdc` is `alwaysApply: false` — reference material, not a standing rule. It belongs in the docs tree, not `AGENTS.md`.

Move to `docs/build/wsl_unison_environment.md`, drop frontmatter, keep content intact (short path `C:\dev\CN`, the never-sync-through-the-junction warning, NativeWind pre-bundle workaround, instrumentation-tests-from-Windows instructions). Link it from `AGENTS.md` and add it to the `docs/README.md` **Build & Development** section.

This one is genuinely machine-specific (it hardcodes `/home/william/...`), but it's already committed and other agents benefit from it, so it moves rather than being dropped.

### Phase 3: Skills → `.skills/` + harness symlinks

Move both skills to `.skills/<name>/SKILL.md`. Frontmatter (`name`, `description`) already satisfies the spec — `name` matches the parent directory, lowercase-hyphenated, and descriptions state both what and when.

Two spec-conformance fixes while moving:
- **`plan-making`**: relocate `template-small.md` / `template-large.md` into `assets/`, per the spec's convention for templates. Update the two links in `SKILL.md` to `assets/…` (still one level deep, as recommended).
- **`github-pr-comments`**: the GraphQL block is an unterminated code fence — the file ends mid-block with no closing ```. Close it.

Also update the self-referential line in `plan-making/SKILL.md` that points at `.cursor/plans/` so it reflects the new deprecation notice.

Then make both harnesses discover them:

```
.skills/                      # canonical, vendor-neutral
├── plan-making/
│   ├── SKILL.md
│   └── assets/{template-small,template-large}.md
└── github-pr-comments/SKILL.md

.claude/skills  -> ../.skills  # symlink (committed, mode 120000)
.cursor/skills  -> ../.skills  # symlink (committed) — replaces the real dir
```

Cursor keeps working unchanged; Claude Code gains discovery it didn't have (there is no `.claude/` directory in the repo today).

**Verified before committing to this approach:** git stores symlinks as mode `120000` and they round-trip; symlinks create and resolve correctly on the `/mnt/c/dev/CN` DrvFs mount.

**Still to verify during implementation** — the Unison profile (`~/.unison/non_windows_cnplus.prf`) sets neither `links` nor `follow`, and the repo has zero tracked symlinks today, so this is new ground for the sync setup. After the first sync, confirm the symlinks arrive as symlinks and that Unison did not replace a directory with a link. If Unison mishandles them, the fallback is `.skills/` as the sole location with `AGENTS.md` pointing agents at it explicitly (no symlinks) — a real tradeoff, since it costs auto-discovery. Do not let a broken sync silently degrade the checkout; the `wsl-unison-setup` doc already records how destructive that failure mode is.

### Phase 4: Deprecate `.cursor/plans/`

Leave all six `.plan.md` files in place — git history and their content stay intact. Add `.cursor/plans/README.md` marking the directory deprecated: explain the files are Cursor Plan Mode output kept only for historical reference, that the format is not used going forward, and point to `docs/dev_todo/` + the `plan-making` skill as current practice.

### Phase 5: Open the PR

Branch, commit, push, and open with `gh pr create` against `master` (`williscool/CalendarNotification`).

## Files Changed Summary

| File | Change |
|------|--------|
| `AGENTS.md` | **New** — from `main-rules.mdc`, frontmatter dropped, agent-config + orientation sections added |
| `.cursor/rules/main-rules.mdc` | Deleted (content → `AGENTS.md`) |
| `docs/build/wsl_unison_environment.md` | **New** — from `wsl-unison-setup.mdc`, frontmatter dropped |
| `.cursor/rules/wsl-unison-setup.mdc` | Deleted (content → `docs/build/`) |
| `.cursor/rules/` | Removed (now empty) |
| `.skills/plan-making/SKILL.md` | Moved; template links → `assets/`, `.cursor/plans` reference updated |
| `.skills/plan-making/assets/template-{small,large}.md` | Moved into `assets/` per spec |
| `.skills/github-pr-comments/SKILL.md` | Moved; unterminated code fence closed |
| `.cursor/skills/` | Directory replaced by symlink → `../.skills` |
| `.claude/skills` | **New** symlink → `../.skills` |
| `.cursor/plans/README.md` | **New** — deprecation notice; the 6 `.plan.md` files untouched |
| `docs/README.md` | Add WSL environment doc to Build & Development |

## Testing

No application code changes — nothing to run against the Android/Jest suites, and no CI impact (no workflow references `.cursor/`; the only in-repo mention is the one line inside `plan-making/SKILL.md`).

Verification is structural:

1. `git ls-files -s .claude .cursor` shows both symlinks as mode `120000`.
2. Fresh-clone check: symlinks resolve and `SKILL.md` is readable through both `.claude/skills/` and `.cursor/skills/`.
3. Frontmatter validates against the spec — `name` matches its directory, lowercase/hyphen-only, `description` non-empty and under 1024 chars. Optionally `skills-ref validate ./.skills/<name>`.
4. Every relative link resolves from its new location: `assets/` template links, `docs/` references in `AGENTS.md`, the new `docs/README.md` entry.
5. Grep for stale `.cursor/rules` or `.cursor/skills` paths.
6. Unison round-trip per Phase 3 — the one step with real downside if it goes wrong.

## Open Questions

None blocking. The Unison symlink behavior in Phase 3 is the only unknown, with a stated fallback.

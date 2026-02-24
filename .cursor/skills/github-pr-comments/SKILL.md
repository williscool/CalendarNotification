---
name: github-pr-comments
description: Helps the agent fetch and interpret GitHub CLI / GraphQL queries for pull request review comments with line numbers and resolution status.
---

# GitHub PR review comments skill

This skill guides the agent to use GitHub CLI and GraphQL to fetch pull request review comments with detailed context including line numbers and resolution status.

## When to use

- When the goal is to examine PR review threads, comments, and their status (resolved/outdated).
- When you need to analyze or summarize PR feedback tied to specific files/lines.
- When building tools or scripts that process GitHub PR comments programmatically.

## Instructions

1. Prefer the GitHub CLI GraphQL query shown below to get full review‑thread context, including resolution status and line numbers.  
2. Substitute `OWNER`, `REPO`, and `PR_NUMBER` with the correct values before running.
3. If the response is paginated, use `endCursor` from `pageInfo` to iterate the next page until `hasNextPage` is `false`.

### Primary command (GraphQL – full‑featured)

Run this command in the terminal to fetch review threads with comments, paths, line numbers, and resolution status:

```bash
gh api graphql -f query='
  query($owner: String!, $repo: String!, $prNumber: Int!, $cursor: String) {
    repository(owner: $owner, name: $repo) {
      pullRequest(number: $prNumber) {
        reviewThreads(first: 100, after: $cursor) {
          pageInfo {
            hasNextPage
            endCursor
          }
          nodes {
            isResolved
            isOutdated
            comments(first: 20) {
              nodes {
                author {
                  login
                }
                body
                path
                line
                startLine
                originalLine
                originalStartLine
                diffHunk
                createdAt
                updatedAt
              }
            }
          }
        }
      }
    }
  }
' -f owner=OWNER -f repo=REPO -F prNumber=PR_NUMBER
---
name: github-pr-comments
description: Helps the agent fetch and interpret GitHub CLI / GraphQL queries for pull request review comments with line numbers and resolution status.
---

# GitHub PR review comments skill

This skill guides the agent to use GitHub CLI and GraphQL to fetch pull request review comments with detailed context including line numbers and resolution status.

## When to use

- When the goal is to examine PR review threads, comments, and their status (resolved/outdated).
- When you need to analyze or summarize PR feedback tied to specific files/lines.
- When building tools or scripts that process GitHub PR comments programmatically.

## Instructions

1. Prefer the GitHub CLI GraphQL query shown below to get full review‑thread context, including resolution status and line numbers.  
2. Substitute `OWNER`, `REPO`, and `PR_NUMBER` with the correct values before running.
3. If the response is paginated, use `endCursor` from `pageInfo` to iterate the next page until `hasNextPage` is `false`.

### Primary command (GraphQL – full‑featured)

Run this command in the terminal to fetch review threads with comments, paths, line numbers, and resolution status:

```bash
gh api graphql -f query='
  query($owner: String!, $repo: String!, $prNumber: Int!, $cursor: String) {
    repository(owner: $owner, name: $repo) {
      pullRequest(number: $prNumber) {
        reviewThreads(first: 100, after: $cursor) {
          pageInfo {
            hasNextPage
            endCursor
          }
          nodes {
            isResolved
            isOutdated
            comments(first: 20) {
              nodes {
                author {
                  login
                }
                body
                path
                line
                startLine
                originalLine
                originalStartLine
                diffHunk
                createdAt
                updatedAt
              }
            }
          }
        }
      }
    }
  }
' -f owner=OWNER -f repo=REPO -F prNumber=PR_NUMBER

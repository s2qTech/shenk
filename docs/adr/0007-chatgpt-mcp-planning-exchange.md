# ADR 0007: ChatGPT MCP planning exchange

Status: Accepted
Date: 2026-07-22

## Context

Shenk currently exchanges weekly review material with ChatGPT by copying text. This is dependable but cannot support a scheduled planning task that reads the latest cloud state and returns a draft while the user's computer is off.

The existing Worker bearer tokens are application credentials. They must not be exposed to ChatGPT, placed in an MCP URL, or reused as end-user OAuth credentials. Advanced AI also must not receive authority to mutate formal plans directly.

## Decision

- The Worker exposes a remote MCP endpoint at `/mcp`.
- ChatGPT authenticates with OAuth 2.1 authorization code flow and PKCE S256.
- A short-lived, one-time pairing code authorizes the single user. Only a hash of the pairing code is stored.
- MCP access and refresh tokens are random opaque values. Only hashes are stored.
- The initial MCP surface contains exactly two tools: `get_planning_snapshot` and `submit_coach_plan_patch`.
- `get_planning_snapshot` returns a bounded, sanitized planning snapshot and a digest.
- `submit_coach_plan_patch` accepts a Contract v2 patch and stores it as `pending`.
- MCP may write only `planning_runs` and `coach_plan_patches`.
- Shenk remains responsible for validation, preview, user confirmation, and formal entity writes.
- Missing patch fields and empty arrays are no-op. Replace mode and delete operations are rejected by the MCP draft endpoint.

## Consequences

- Scheduled ChatGPT tasks can operate without a continuously running desktop Codex task.
- A compromised MCP token cannot write health facts, training facts, routines, or formal plans.
- Android and Web can consume pending drafts later without changing existing formal-plan semantics.
- Removing the MCP routes and revoking OAuth tokens disables the integration without rewriting formal data.

## Non-goals

- No autonomous application of a plan patch.
- No account or multi-user system.
- No provider API key storage.
- No direct mutation of formal plan entities by ChatGPT.

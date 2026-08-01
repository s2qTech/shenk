# ADR 0008: Provider-neutral clipboard AI exchange for phase 1

Status: Accepted
Date: 2026-08-02

## Context

Android sharing can place review text in an external app's input box, but it cannot reliably select the established coaching conversation. A generic ChatGPT conversation also does not automatically carry the repository's planning history, constraints, schemas, and accepted decisions. Binding phase 1 to one provider therefore adds interaction cost without preserving planning quality.

The current Codex fitness-planning task can read the canonical repository documents directly. The dependable phase-1 workflow is consequently a small, explicit transport boundary: Shenk prepares normalized material for copying, and accepts a strict plan patch pasted back by the user.

## Decision

- Phase 1 advanced-AI collaboration is provider-neutral copy and paste.
- Shenk copies the normalized weekly review package to the system clipboard. It does not invoke Android sharing or target a provider application.
- A plan returns only as pasted `coach_plan_patch` content. Shenk validates the whole patch, previews it, and requires explicit confirmation before formal writes.
- Shenk is not an Android `text/plain` share target in phase 1.
- User-facing phase-1 UI must not expose ChatGPT-specific MCP setup, pairing, provider names, or promises of direct conversation routing.
- Existing MCP/OAuth implementation may remain dormant as future infrastructure, but it is not a phase-1 product path or acceptance dependency.
- A later API, MCP, or Skill integration must reuse the same normalized planning snapshot, `coach_plan_patch` schema, authority boundaries, validation, confirmation, and audit semantics. Provider-specific payloads must remain adapters outside the domain model.

## Consequences

- The user deliberately chooses the coaching task and pastes the review material there.
- The coaching task can continue reading repository constraints and history without duplicating them into an unbounded chat prompt.
- Phase 1 has no provider account, conversation selection, OAuth, or external-app routing dependency.
- Future automation can replace the transport without changing plan authority or data contracts.

## Non-goals

- No automatic call to an advanced AI in phase 1.
- No automatic application of returned plans.
- No provider-specific business entities or plan semantics.
- No removal of dormant MCP backend code solely because it is deferred.

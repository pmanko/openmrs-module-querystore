# resolve-ticket · openmrs-module-chartsearchai · #402 · 2026-09-26
outcome: aborted (ticket direction terminus: live gate lead criterion fails at head; draft PR #544, pr-harden not run, override recorded)
rounds: 0   cycles: 2 (harden, override on post-escalation Phase 2)   verifier: ran (live A/B, 6 arms A/B/C/D/E/F on standalone-8082)
context: no compaction · peak not surfaced (one operator pause/resume mid Step 6)
transcript: /Users/danielkayiwa/.claude/projects/-Users-danielkayiwa--claude-pipeline-worktrees-openmrs-openmrs-module-chartsearchai-402/abb941f5-fc98-4a9b-919f-ec16f51ccdb1.jsonl

## Refuted by measurement
- "the owner's A/B arm moves the prednisone lead" (issue comment, :8081) -> on :8082 the exact owner diff (arm C) still refuses · cost: 1 arm
- "the three extra referent sites keep the refusal" (my hypothesis) -> arm C refuses too · cost: 1 arm
- "a no-severity sentence fixes residue (a) and moves the lead" -> first wording moved the lead but produced ReferenceProseFidelityCheck false reports + dropped Susan's ratings; the reword fixed both and the lead refused again · cost: 2 arms + 1 harden escalation

## Raised by a fresh agent, missed by the author
- [harden P2 integration] no-severity sentence opening "This finding" made a faithful answer reported as unfaithful · blocking-equivalent · cost: 1 escalation + 1 live arm
- [harden P2 quality] README true/false list omitted sibling-row and question-pair exceptions; four stale javadocs in unchanged neighbours · non-blocking
- [refuter] #477 constituent case needed a data precondition (order resolves to rifampicin) · non-blocking, adopted

## Where a skill blocked or contradicted this run
- resolve-ticket Step 9 vs ticket direction: pr-harden FINISH marks ready, direction says end as draft when a gate criterion cannot be fixed by rendering — took labelled override
- harden: required second Phase 2 after escalation; overridden for the same terminus

## Declined
- (none declined; deferrals: CurrentMedicationFindingStrengthTest stale javadoc — direction keeps it unedited; five homes of the one-referent argument; substance set built 3x per pass; per-file onlyFinding helpers)

## Assumptions review overturned
- "extend the referent to class-only/several-orders/condition-mediated sites" -> kept; measured not to be the cause of the refusal (arm C)

## Driver capture (pool-run)
outcome as the driver measured it: draft
session: abb941f5-fc98-4a9b-919f-ec16f51ccdb1 · 1h30m · 386 assistant turns · stream: /Users/danielkayiwa/.claude/pipeline/logs/20260926T181748Z-openmrs_openmrs-module-chartsearchai-402.jsonl
- the run left its gate entry unfinished: phase=building blocking=0 round=1 override=True

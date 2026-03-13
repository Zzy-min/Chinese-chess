# Spec Reviewer Subagent Tool

## Command

```powershell
.\tools\spec-document-review-subagent.ps1 -SpecPath .\docs\superpowers\specs\2026-03-13-xiangqi-web-engine-strength-design.md
```

Optional parameters:

- `-OutputPath <path>`: custom output file path.
- `-Model <model>`: pass through to `codex exec --model`.

## Output

Review report is written to:

- default: `docs/superpowers/reviews/<timestamp>-<spec-name>-review.md`

## Notes

- Uses an isolated `codex exec` run as subagent-equivalent reviewer.
- Runs in `read-only` sandbox mode.
- Prompt template source: `docs/superpowers/reviewers/spec-document-reviewer-prompt.md`.

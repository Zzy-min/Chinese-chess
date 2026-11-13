Original prompt: Implement the approved local-only plan for multi-round rooms, simplified mobile navigation, five-level Gomoku AI difficulty, and a restrained light UI refresh. Do not commit, push, deploy, or delete production/user data; preserve unrelated workspace changes.

## Progress

- 2026-08-11: Verified `main` matches `origin/main` at `462d3c2`; pre-existing untracked `docs/gatewayhacks-2026/` is unrelated and must remain untouched.
- 2026-08-11: Confirmed current gaps: room lifecycle ends at `FINISHED`, no rematch route, mobile bottom nav still has five items, Gomoku practice still uses the shared three-level Xiangqi difficulty enum.
- 2026-08-11: Implemented session/episode room lifecycle with `BETWEEN_GAMES`, stable room codes, round score, alternating seats, rematch offer/accept/decline/cancel, dual server routes, and three-round regression coverage.
- 2026-08-11: Added independent five-level Gomoku profiles; NOVICE/EASY/MEDIUM force the built-in engine, HARD/MASTER prefer Rapfi with explicit fallback state. Targeted room and Gomoku tests pass.
- 2026-08-11: Reworked mobile play entry to four bottom tasks and three primary actions; added a restrained visual refinement and readable two-column personal-center grid.
- 2026-08-11: Full Maven suite passes (90 tests), package succeeds, JavaScript syntax, resource contracts, and `git diff --check` pass.
- 2026-08-11: Completed local browser acceptance at 1440x900 and 390x844: no document/body horizontal overflow, no competing play-page scroll containers, four mobile tabs, three play CTAs, five Gomoku levels, and all seven personal-center entries visible. No browser console or page errors were observed.
- 2026-08-11: Final diff/security review found no embedded credentials; pre-existing `docs/gatewayhacks-2026/` remains untouched. No commit, push, deployment, or production-data operation was performed.
- 2026-08-11: Fixed authentication failure UX: localized server errors, removed the duplicate error toast, restored the submit button after failure, cleared the password, and returned focus to the password field. Browser reproduction passed and the full Maven suite now passes 91 tests.

## TODO

- Human-tune the subjective Gomoku difficulty feel over a longer multi-game sample if desired; the automated profile monotonicity, engine routing, and fallback behavior are covered.

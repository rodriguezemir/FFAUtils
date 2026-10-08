# Personal kit editor
## Objective
Store each player's kit layout in FFAPlayer Map<String, ItemStack[]>; load/save it on join/quit without changing shared kit definitions.
## Agreed behavior
- Rearrangement only: preserve item identity, metadata and aggregate quantities; stack splitting/merging is allowed.
- Restore default removes only that player's override.
- Global definition changes invalidate personal layouts; deleted global kits cannot be applied through an old Kit object.
- Preserve all 41 positions, including null/AIR, armor and offhand; no personal-layout armor auto-equip.
- Clone arrays/stacks on storage and retrieval.
## Scope and constraints
Profile ownership, SQLite persistence, personal GUI, central application and tests.
User additionally authorized only configuration-fixture repair in CombatDamageIntegrationTest.java; no combat production behavior changes.
Single writer; preserve unrelated config/messages/combat/version changes.
Branch: feat/personal-kit-editor. Parent/workers did not stage, commit, push or open PRs.
User confirmed external commits were intentional. Do not rewrite them.
## Tasks
- [x] T1 (independently verified; user commits recorded): Isolated personal storage, exact-slot persistence, shared loaded profile ownership and quit/disable saves.
- [x] T2 (independently verified; user commits recorded): Personal-only GUI/restore-default, conservation validation, stale override invalidation and exact central application.
- [ ] T3 (automated checks complete; remaining checks blocked/pending): Final tests/build/native review and live Paper smoke checks.
## Acceptance / implementation
T1: two-player isolation, defensive copies, metadata and full-array codec round trips, real SQLite save/unload/reload/removal, legacy stats compatibility, failed-load write guards, shared join instance and combat-death-before-save ordering.
T2: no GUI global writes/deletion; effective personal reopen/reset/list; personal restore-default; reject missing/extra/metadata-changing items; fingerprint plus conservation against current global definition; exact saved slots with default legacy auto-equip unchanged.
Session tests cover snapshot/cursor restoration, item ingress/egress/use guards, equipment editing, save/cancel/close/quit and session-identity-safe deferred cleanup.
## Verification evidence
- T1 writer: initial preimplementation compiler RED had suppressed diagnostics, so behavioral assertion RED is not established for initial storage work. Final focused suite initially passed 21 tests.
- Independent T1 found retained dirty profiles reconnecting with stale runtime state. Real SQLite failed-save -> quit -> reconnect -> retry regression observed RED (9 tests/1 failure), then GREEN/refactor 9/9. Join resets only LOBBY/killstreak/lastKit/lastSpawn before lobby equipment. Independent corrected suite passed 22 fresh tests.
- T2 assertion REDs observed for unregistered old Kit application, deferred cleanup identity and AIR retention. Final fresh focused suite passed 24/24.
- First full integrated run failed: 280 tests/1 failure, all 46 personal-kit tests passing. CombatDamageIntegrationTest failed before assertions because excluded concurrent CombatLogManager config access met a null mock.
- User-authorized fixture-only repair: six additions, real YamlConfiguration, unrelated notification disabled; original participant assertions unchanged. Focused RED 1/1 failure, GREEN and fresh repeat 1/1.
- Subsequent independent integrated run passed 280/280, but concurrent user commits required a new stable-HEAD verification.
- FINAL command: ./gradlew test build --no-configuration-cache --rerun-tasks --console=plain > build/verification-final-head.log 2>&1
- FINAL result: exit 0; BUILD SUCCESSFUL in 1m 32s; six executed tasks: compileJava, processResources, jar, shadowJar, compileTestJava, test. Raw :shadowJar evidence: build/verification-final-head.log:9. Compilation emitted deprecation/unchecked warnings.
- FINAL fresh XML: 25 suites, 280 passed, zero failures/errors/skips; all 46 personal-kit tests and combat integration 1/1 passed.
- Before 2026-10-08T00:06:03Z / after 00:08:11Z: identical HEAD 4682efe30e0738e90174105fadbc9204428b2d36, same branch, clean worktree/index; diff quiet/cached quiet/check returned 0.
- Parent spot check: same HEAD, clean tree, git diff --check passed and artifact SHA matched. This final record update subsequently changes only the task document, not tested source.
## Current artifact
build/libs/FFAUtils-1.3.0-SNAPSHOT.jar
Descriptor version 1.3.0-SNAPSHOT; 825291 bytes; regenerated 2026-10-08T00:07:01.114664101Z.
SHA-256: 1f34cb9a2c0c764e44a429bbc343de30a90326a7781c765357ef98be4565dd2b
471 classes, including personal editor/layout/codec; relocated Gson 221, Hikari 80, FastInv 6.
## Remaining checks / limitations
- Native review unavailable: INSPECT blocked native-status-package-binary-missing; lineage_created=false, mutation_performed=false. ASSESS unassessable, explicit unavailable; independent verification completed instead. No native approval claimed.
- Native offered continuation (unchanged): If GENTLE_PI_SKIP_GENTLE_AI_INSTALL is set, remove or unset it before changing to the installed gentle-pi package directory and running `node scripts/install-gentle-ai.mjs`.
- Installation was not attempted: outside authorized repository scope.
- No live Paper/client smoke test. Verify cursor transport, disconnect/world-event rollback, exact layout after reconnect and real server persistence.
- Global metadata Gson reload not successfully exercised in MockBukkit. Fingerprint stability checked with metadata codec round trip and trailing-empty normalization. MockBukkit exposes 43 slots and can normalize AIR; exact 41-slot API arguments were separately captured.
- Production SQLite driver availability remains a preexisting external dependency; new testRuntimeOnly driver does not provision production.
- Preexisting utility relocation mismatch: configuration targets com.github.putindeer, archive contains five me/putindeer classes and zero relocated libs/utils classes. No packaging fix attempted.
No proven remaining candidate-caused defect; automated verification is not production approval.
## Recovery / incident history
Early worker stopped because task document disappeared after successful write/readback; cause unproven. Parent restored from Engram and confirmed same root/branch. No source writes occurred at that stop.
External staging/commits during first successful final run were confirmed intentional by user; no resets/reverts performed. Final rerun tied evidence to stable committed HEAD.
## User commit evidence / rollback
T1 source/lifecycle/codec: 54a0daf, d6c0a2f, 342ff43; tests: 56b3e10, dbc486d, b0a1cfa; JDBC test runtime: 4682efe.
T2 GUI/application/layout: 5f3bb5f, d6c0a2f, 342ff43; tests: 4604214, 5f22016, dbc486d, b0a1cfa.
Authorized combat fixture: 5f22016. These user-created commits also contain some unrelated changes; they are evidence, not automatic rollback units.
Rollback boundary: personal-kit source/tests and additive persistence behavior only; preserve unrelated source/resources, existing stats/global kit files and user commits. No destructive rollback authorized.
## Next step
Run live Paper smoke checks and, if authorized, restore native review availability for a separately scoped review candidate. T3 remains open for those checks; automated tests/build/artifact verification complete.

# Personal kit editor
## Objective
Save each player's kit layout in FFAPlayer Map<String, ItemStack[]> and load/save it on join/quit, without modifying shared kit definitions.
## Agreed behavior
- Rearrangement only: item identity, metadata and quantities must match the current global kit.
- Restore default removes only the personal override.
- Global kit changes invalidate saved layouts; deleted global kits remain unavailable.
- Preserve 41 slots, including interior/trailing null/AIR, armor and offhand. No auto-equip for personal layouts.
- Defensive copies prevent shared mutable inventory data.
## Scope and constraints
Profile ownership, SQLite persistence, personal editor/list/detail, central kit application, focused tests. Authorized verification repair: only configuration fixture in CombatDamageIntegrationTest.java, no combat production edits.
One writer at a time. Existing English repository artifact conventions apply.
Branch: feat/personal-kit-editor. No commits/push/PR without explicit user request; commit evidence pending authorization.
## Tasks
- [x] T1 (verified; commit pending authorization): Add isolated personal-kit storage and slot-preserving persistence; share the loaded FFAPlayer on join and save it on quit/disable.
- [x] T2 (writer verified; independent closure pending; commit pending authorization): Make GUI edits/restoration personal, enforce rearrangement-only validation, invalidate stale overrides and centrally apply exact personal layouts.
- [ ] T3 (in_progress): Verify focused/full tests and build, review candidate if enabled, and document evidence/limitations.
## Acceptance and checks
T1: two-player isolation, defensive copies, exact 41-slot codec round trips (empty, AIR, armor/offhand, metadata), save/unload/reload and override deletion, same join instance, legacy stats compatibility.
T2: global data unchanged by GUI; reopen/reset uses effective personal kit; safe restore-default; reject missing/extra/changed items; unchanged item quantities after cursor/drop handling; exact saved-slot application; changed globals invalidate override.
T3: ./gradlew test and ./gradlew build; focused tests first, native review is not functional verification. In-server GUI smoke checks must be reported pending if no server/client is exercised.
## Verification policy
Deterministic behavior tests are applicable: observe RED before implementation, GREEN afterward, then refactor with tests. Record actual commands/results; never infer lifecycle evidence.
## Progress / evidence
Read-only exploration completed. Current gameplay and StatsManager profiles are disconnected; global serializer loses trailing empty slots; default application auto-equips armor. User resolved three product questions.
T1 implemented; writer reports 21 focused tests passing and git diff --check passing. Initial RED was preimplementation compilation failure with diagnostics suppressed, not confirmed behavioral assertion RED. An intermediate SQLite setup failure was fixed by test-runtime JDBC; invalid CAVE_AIR fixture corrected. Live server smoke checks pending. RDD switch previously read on; native ASSESS unavailable (package-local-binary-missing), treated as high risk and independent verification required.
T1 worker stopped before edits because the task document disappeared. Parent confirmed same repository/branch, restored this document from Engram; cause unproven. Concurrent changes to gradle.properties, CombatLogManager.java and config.yml must be preserved. No tests had run at that early stop.
Independent T1 verification: fresh focused --no-configuration-cache --rerun-tasks passed 21 tests, zero skips/failures/errors (19s, 4 executed tasks); git diff --check passed. Found candidate-caused retained-profile reconnect runtime-state gap: failed unload save retains IN_FFA/killstreak/lastKit/lastSpawn and join does not reset them. T1 remains open until correction verification.
T1 correction implemented: reset LOBBY/killstreak/lastKit/lastSpawn before lobby equipment. Real SQLite failed-save -> quit -> reconnect -> successful retry regression observed RED 9 tests/1 failure; GREEN and refactor rerun 9/9. Stats/layout/fingerprint and shared identity preserved. Parent git diff --check passed. Independent corrected suite passed fresh: 22 tests, zero failures/errors/skips; 17s, 4 Gradle tasks executed. Reset order and real SQLite reconnect/retry verified. T1 functional work accepted, commit remains pending explicit authorization.
T2 implemented in 10 files (981 insertions/71 deletions including new files). Writer fresh 24-test suite passed (12s, 4 executed tasks), no skips/failures/errors, git diff --check passed. Behavioral REDs observed for stale unregistered Kit application, identity-safe deferred cleanup, and AIR-marker retention. Conservation checks aggregate metadata identity and quantity; GUI saves/removal personal only, exact personal application, global fingerprint plus content validation. Full suite/build and independent T2 verification pending. MockBukkit limitations: 43 slots/AIR normalization; captured 41-slot API arguments and codec metadata round-trip used, global Gson reload not exercised successfully.
Native review preflight attempted once after T2: INSPECT blocked native-status-package-binary-missing, lineage_created=false, mutation_performed=false. ASSESS unavailable; independent verifier required. Provider continuation: If GENTLE_PI_SKIP_GENTLE_AI_INSTALL is set, remove or unset it before changing to the installed gentle-pi package directory and running node scripts/install-gentle-ai.mjs. Installation not attempted (outside authorized repository work). No native review approval claimed.
Integrated independent ./gradlew test build --no-configuration-cache --rerun-tasks failed at :test (exit 1, 25s, 6 tasks executed). Fresh XML: 25 suites /280 tests /1 failure /0 errors/skips. All 10 candidate suites passed 46/46 (T1 22/T2 24). Sole failing CombatDamageIntegrationTest.onEntityDamageByEntity_shouldMarkBothPlayersInCombat: config-null NPE at CombatLogManager.java:34 /test:101. Read-only HEAD/diff proves excluded concurrent combat change introduced config access; candidate damage handler unchanged. Build still unverified. User explicitly authorized adjusting only configuration mock fixture src/test/java/site/zvolcan/fFAUtils/listeners/CombatDamageIntegrationTest.java and rerunning full tests/build; combat production behavior/source unchanged. Build tasks downstream remain pending, no successful build claimed.
Authorized fixture repair completed: CombatDamageIntegrationTest.java only, six additions (real YamlConfiguration; unrelated entry notification disabled explicitly). Original both-player assertions unchanged. Focused RED 1 test/1 config-null NPE; GREEN and fresh repeat 1/1, no skips/errors. Parent exact diff readback and git diff --check passed. Fresh full integrated test/build rerun now pending. Native ASSESS still unavailable, explicit outcome unavailable, independent verification required.
## Work-unit boundaries
T1: personal storage/persistence/lifecycle with tests. T2: editor/application/invalidation with tests.
Rollback: remove the relevant unit's changes without touching global kit files or existing stats rows.
Commit identities: pending explicit authorization.
## Next step
Run fresh independent ./gradlew test build --no-configuration-cache --rerun-tasks; full test/build closure pending.
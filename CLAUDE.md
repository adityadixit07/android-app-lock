# Contributor notes (humans and AI agents)

- Read `ARCHITECTURE.md` before changing anything. **Update it in the same commit** whenever a
  component, permission, data store, Android-version workaround or security decision changes.
- `domain/` and `core/` must stay free of `android.*` imports so the locking and auth rules
  remain JVM-unit-testable. Add or extend tests in `app/src/test` for any behaviour change there.
- Security invariants (don't break without updating ARCHITECTURE.md §6):
  - Never store or log a PIN/pattern; keep secrets in `CharArray` and wipe them after use.
  - Every unlock path goes through `Authenticator` (lockout) and reports to `LockEngine`.
  - Fail closed on detection/overlay errors; never lock when no credential exists.
  - No `INTERNET` permission, no analytics or ads SDKs.
- Commands: `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, `./gradlew lintDebug`.

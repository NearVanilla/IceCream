# Contributing

## Prerequisites

- Java 21
- A Paper 1.21 server for manual testing (optional but recommended — use `./gradlew runServer`)

## Workflow

1. Fork the repository and create a branch from `main`.
2. Make your changes.
3. Run `./gradlew spotlessApply` before pushing. The CI code-quality check will fail the PR if formatting is wrong.
4. Run `./gradlew build` to confirm the plugin compiles cleanly.
5. Open a pull request against `main`. Fill out the PR template — only include sections that apply to your change, remove the rest.

## Adding a Module

See `docs/creating_a_module.md` for the step-by-step guide and `DESIGN.md` for the conventions to follow. The `example/` module is the canonical reference implementation.

The short version:

1. Create `modules/<name>/` with a `<Name>Module.java` implementing `Module`, plus `commands/` and/or `events/` subdirectories as needed.
2. Add a config entry under `modules.<name>.enabled` in `config.yml` (default `true` or `false` as appropriate).
3. Instantiate the module in `IceCream.java` and call `.register()` from `onEnable()`.
4. If the module holds persistent state, implement `unregister()` and call it from `onDisable()`.

## Code Style

Formatting is enforced by Spotless with Google Java Format. Always run `./gradlew spotlessApply` before committing — the CI will reject PRs that fail `spotlessCheck`.

Specific conventions:
- User-facing strings must use MiniMessage components, never raw string concatenation.
- Permission nodes follow the pattern `icecream.modules.<name>.<verb>`.
- Per-player persistent state goes in the Paper Persistent Data Container (PDC), not instance fields.
- Optional integrations (Dynmap, DiscordSRV, CarbonChat) must be guarded with `instanceof` checks and must not prevent the parent module from enabling if the integration is absent.

## CI

Two checks run on every PR that touches `.java` or Gradle files:

- **Build Check** — runs `./gradlew build`. A failure posts a comment on the PR with a link to the logs.
- **Code Quality Check** — runs `./gradlew spotlessCheck`. A failure posts a comment pointing to the Spotless report artifact.

Both must pass before a PR can be merged.

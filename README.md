# Near Vanilla Ice Cream

The best treat for players and moderators!

## Overview

Near Vanilla Ice Cream is a modular Paper Minecraft plugin (API 1.21, Java 21) that adds quality-of-life features and administrative tools while keeping gameplay feeling vanilla. Each feature is a self-contained module that can be toggled on or off via `config.yml`.

## Building

Requires Java 21 and Gradle (wrapper included).

```bash
./gradlew build         # Compile and produce the plugin JAR
./gradlew shadowJar     # Build fat JAR with bundled dependencies
./gradlew runServer     # Start a local Paper test server with the plugin loaded
```

The built JAR is output to `build/libs/`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full guide. In short: run `./gradlew spotlessApply` before pushing — the CI will reject PRs that fail the Google Java Format check.

## AI Development

This repository is set up for AI-assisted development. Context files are provided for all major AI coding tools:

| File | Tool |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | Claude Code |
| [`AGENTS.md`](AGENTS.md) | OpenAI Codex / other agents |
| [`.github/copilot-instructions.md`](.github/copilot-instructions.md) | GitHub Copilot |

MCP servers are pre-configured to give AI assistants access to Gradle, Maven, library documentation, GitHub, the filesystem, and git:

| Server | Purpose |
|---|---|
| `gradle` | Interact with the Gradle build via [gradle-mcp](https://github.com/rnett/gradle-mcp) — requires [JBang](https://www.jbang.dev/) |
| `maven-tools` | Look up JVM dependencies on Maven Central via [maven-tools-mcp](https://github.com/arvindand/maven-tools-mcp) — requires Docker |
| `context7` | Fetch up-to-date library documentation via [Context7](https://context7.com/) — a `CONTEXT7_API_KEY` is optional but required beyond the free tier |
| `github` | Read and manage GitHub issues and PRs via the [GitHub MCP Server](https://github.com/github/github-mcp-server) |
| `filesystem` | Scoped file access via the [MCP filesystem server](https://github.com/modelcontextprotocol/servers/tree/main/src/filesystem) |
| `git` | Repository history and operations via [mcp-server-git](https://github.com/modelcontextprotocol/servers/tree/main/src/git) — requires [uv](https://github.com/astral-sh/uv) |

Configuration lives in `.mcp.json` (Claude Code / agents) and `.vscode/mcp.json` (VS Code / Copilot). You will need to set a `GITHUB_PERSONAL_ACCESS_TOKEN` environment variable for the GitHub MCP server.

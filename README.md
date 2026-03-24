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

## AI Development

This repository is set up for AI-assisted development. Context files are provided for all major AI coding tools:

| File | Tool |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | Claude Code |
| [`AGENTS.md`](AGENTS.md) | OpenAI Codex / other agents |
| [`.github/copilot-instructions.md`](.github/copilot-instructions.md) | GitHub Copilot |

MCP servers are pre-configured to give AI assistants access to the Paper API and git:

| Server | Purpose |
|---|---|
| `papermc-api` | Look up Paper API documentation and javadocs |
| `git` | Repository history and operations via [mcp-server-git](https://github.com/modelcontextprotocol/servers/tree/main/src/git); requires [uv](https://github.com/astral-sh/uv) |

Configuration lives in `.mcp.json` (Claude Code / agents) and `.vscode/mcp.json` (VS Code / Copilot).

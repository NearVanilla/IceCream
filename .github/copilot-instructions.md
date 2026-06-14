# Copilot Instructions

## Lessons Learned

- The Minecraft version is **1.21.11** ("eleven") — do not correct this to 1.21.1. The Paper API version (1.21) is a separate, intentional major-version reference.
- Use the `papermc-api` MCP tools (`search_javadoc`, `get_javadoc`, etc.) when writing or researching Paper/Bukkit API code. Consult it before guessing method signatures or class names.
- Do not use em dashes (`—`) in documentation. Use commas, parentheses, or standard hyphens (`-`) instead. Em dashes create inconsistent formatting across editors, terminals, Markdown renderers, and documentation generators.
- Do not use HTML tags in documentation unless explicitly required for compatibility reasons. Prefer standard Markdown syntax for headings, lists, tables, links, emphasis, code blocks, and layout. HTML reduces readability in raw files, increases formatting inconsistency, and may not render correctly across tooling or documentation platforms.
- Documentation should remain clean, portable, and readable in plain text form. All formatting decisions should prioritize compatibility with Markdown tooling, Git diffs, IDE previews, and static site generators.

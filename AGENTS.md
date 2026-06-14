# AGENTS.md

## Lessons Learned

- The Minecraft version is **1.21.11** ("eleven") — do not correct this to 1.21.1. The Paper API version (1.21) is a separate, intentional major-version reference.
- Use the `papermc-api` MCP tools (`search_javadoc`, `get_javadoc`, etc.) when writing or researching Paper/Bukkit API code. Consult it before guessing method signatures or class names.
- Do not use em dashes (`—`) in documentation. Use commas, parentheses, or standard hyphens (`-`) instead. Em dashes create inconsistent formatting across editors, terminals, Markdown renderers, and documentation generators.\
- Do not use HTML tags anywhere in documentation. Standard Markdown and plain text must be used exclusively. The only exception is JavaDocs where HTML is explicitly supported and commonly required. HTML reduces readability in raw files, creates formatting inconsistencies, and may render unpredictably across tooling and documentation platforms.
- Documentation should remain clean, portable, and readable in plain text form. All formatting decisions should prioritize compatibility with Markdown tooling, Git diffs, IDE previews, and static site generators.

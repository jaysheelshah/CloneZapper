# CloneZapper

Java/JVM file deduplication engine. Detects exact and near-duplicate files across local storage and cloud services using content-aware matching (Apache Tika, JImageHash, MinHash LSH).

## Project goals

- Content-aware dedup across local + cloud storage boundaries
- Support for documents, images, audio — not just hash-based exact matches
- Accessible to non-technical users (poor filing habits, low memory/mental clarity)

## Planned features

- `docs/ideas/fuzzy_file_finder.md` — natural-language file search for users who can't remember where files are

## Notes

- Windows development environment
- IntelliJ IDEA (JetBrains plugin for Claude Code installed)

# Fuzzy File Finder — "What was that file?"

For users who have poor filing habits, store files anywhere, and rely on mental metadata that fades.

## The Problem

User knows *about* a file but not *where* it is:
- "My tax file details for 2024"
- "Customer X's file from Y month regarding the Z case"
- "That photo from the beach trip a few years back"

No folder structure, no naming convention. Memory is the only index — and it's unreliable.

## Core Idea

After deduplication, let the user describe a file in plain language. The tool searches across:
- Filename (fuzzy match)
- File content (text extraction where possible)
- EXIF / metadata (dates, device, location)
- Previously attached user notes

Return ranked candidates. User confirms or refines.

## Possible Inputs

- Natural language query typed by user
- Date range hint ("sometime in 2023")
- File type hint ("a PDF", "a photo")
- Person/keyword hint ("invoice", "Carlos", "NDA")

## Output

- Short list of likely matches with a one-line reason why each was surfaced
- Option to attach a persistent note to the file for next time
# Manga Reader

A desktop manga reader application built with JavaFX that lets you browse, read, and manage manga from MangaDex.

## Features

- Browse and search manga from MangaDex
- Add manga to your personal library
- Read manga with traditional page-by-page or continuous webtoon mode
- Track reading progress
- Advanced search with genre and status filters
- Dark/light theme support
- Keyboard navigation (arrow keys, A/D for pages, +/- for zoom)

## Requirements

- Java 17 or higher
- Maven 3.6+

## Installation

Clone the repository:
```bash
git clone https://github.com/uno-jerome/manga-reader-DSA-2nd.git
cd manga-reader-DSA-2nd
```

Build and run:
```bash
mvn clean compile
mvn javafx:run
```

## Usage

1. Open the application
2. Click "Add Series" to search for manga
3. Click on any manga to view details and chapters
4. Click "Start Reading" to begin reading
5. Use arrow keys or A/D to navigate pages
6. Press Escape to go back

## Project Structure

```
src/main/java/com/mangareader/prototype/
├── MangaReaderApplication.java
├── model/              # Data models (Manga, Chapter, etc.)
├── service/            # Business logic
├── source/             # MangaDex API integration
└── ui/                 # JavaFX views
```

## Technologies

- JavaFX 21.0.2
- Jackson (JSON processing)
- Apache HttpClient
- Maven

## License

MIT License


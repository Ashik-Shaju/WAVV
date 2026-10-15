# Search and personalization

Search and recommendations use the current on-device library. Wavv does not send the library or listening history to a ranking service.

## Search

Wavv can combine matching titles and file metadata with local audio analysis for descriptive queries. It supports metadata constraints for recognized languages, known genres and years, and catalog fields such as artist, album, or composer.

Language filters use recognized language metadata explicitly present in the file or media track. A missing or unrecognized value remains unknown and does not match a language-specific query. Lyrics are not indexed by search.

If local analysis has not completed, ordinary text and metadata matching can still return results. A query cannot reliably match a field that is missing from the local catalog.

## Categories

Search categories use genre information from file metadata or completed local analysis. A category is shown only when at least one matching track has readable artwork. Selecting a category filters the library by available genre information. Wavv does not assign genres from cover art or use a fixed demo catalog.

## Local recommendations

Favorites and listening activity can influence Home shelves, mixes, search ordering, and AutoPlay suggestions. The app distinguishes direct user actions from automatically advanced playback, and a skip is not treated as a definitive dislike. Raw search text is not stored as listening history.

Recommendations are limited to the current library and can be absent when the listening history is new or sparse. Home mixes are temporary playback queues; they do not change a playlist. The Library's **For You** sort appears when recommendations are available.

Listening events are kept for up to one year. Clearing listening history removes the events and recently played markers while preserving favorites and playlists. See [Privacy](privacy.md).

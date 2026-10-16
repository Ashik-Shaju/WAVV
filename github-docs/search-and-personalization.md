# Search and personalization

Search and recommendations use the current on-device library. Wavv does not send the library or listening history to a ranking service.

## Search

Search combines local audio similarity, lexical matches, and genre evidence. A plain genre word contributes to ranking; an explicit `in <genre>` phrase filters to that known genre. Recognized languages, years, and marked catalog fields such as `by <artist>`, `from <album>`, or `composed by <composer>` constrain results.

The fixed blend is 0.60 semantic and 0.40 lexical when the query has no recognized genre. When genre terms are present, the weights are 0.45 semantic, 0.30 lexical, and 0.25 genre. Scores are normalized over the components available for each candidate. A bounded preference boost can move close results toward the user's existing recommendations.

Lexical weights are assigned separately to title (1.0), artist (0.85), album artist (0.8), album (0.75), composer (0.72), year (0.7), author and writer (0.68), language (0.6), track number (0.5), disc number (0.4), recording date (0.45), codec (0.35), container (0.3), bitrate/sample rate/channels (0.2), duration (0.15), file size (0.08), track count (0.1), and compilation (0.2). Confident non-genre Music Understanding tags use their model probability. Genre scoring is separate: file genres have confidence 1.0, while inferred genres use their model probability. These are hand-set heuristics, not learned or calibrated relevance probabilities.

Language filters use recognized language metadata explicitly present in the file or media track. A missing or unrecognized value remains unknown and does not match a language-specific query. Lyrics are not indexed by search.

If local analysis has not completed, ordinary text and metadata matching can still return results. A query cannot reliably match a field that is missing from the local catalog.

## Categories

Search categories use genre information from file metadata or completed local analysis. A category is shown only when at least one matching track has readable artwork. Selecting a category filters the library by available genre information. Wavv does not assign genres from cover art or use a fixed demo catalog.

## Local recommendations

Personalized discovery combines long-term audio similarity (0.34), genre (0.17), creator metadata (artist, album artist, composer, author, and writer; 0.13), current-session audio/genre/creator signals (0.18/0.08/0.04), model tags (0.04), current-session tags (0.03), time-of-day audio/genre/creator/tag signals (0.08/0.03/0.01/0.01), and a field-weighted catalog-metadata component (0.08). Favorites add 0.12, while recent repeat penalties can subtract up to 0.26. Within that metadata component, the field weights are album (0.20), file size (0.01), year (0.10), track number (0.02), disc number (0.01), language (0.15), recording date (0.08), compilation (0.03), duration (0.08), codec (0.04), container (0.04), bitrate (0.08), sample rate (0.07), channels (0.04), and track count (0.02). Available fields are normalized when combined. Metadata adjusts candidates with preference evidence; it cannot recommend a track by a file property alone. IDs, file locations, artwork references, scan state, and metadata provenance do not affect relevance. The overall score is a deterministic ranking heuristic, not a probability.

When a recent user-selected track seeds recommendations, the blend is 0.50 audio similarity, 0.22 genre, 0.15 model tags, 0.08 creator metadata, and 0.05 other catalog metadata. Audio-only candidates must pass a 0.75 cosine-similarity gate. A candidate needs at least one audio, genre, tag, or creator match; additional catalog metadata can refine the order but cannot qualify a candidate by itself.

Favorites and listening activity can influence Home shelves, mixes, search ordering, and AutoPlay suggestions. The app distinguishes direct user actions from automatically advanced playback, and a skip is not treated as a definitive dislike. Raw search text is not stored as listening history.

Recommendations are limited to the current library and can be absent when the listening history is new or sparse. Home mixes are temporary playback queues; they do not change a playlist. The Library's **For You** sort appears when recommendations are available.

Listening events are kept for up to one year. Clearing listening history removes the events and recently played markers while preserving favorites and playlists. See [Privacy](privacy.md).

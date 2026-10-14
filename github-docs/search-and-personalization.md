# Search and personalization

Search and recommendations operate over the current local catalog. They use different kinds of evidence, so a text-to-audio similarity should not be presented as a verified fact about a track.

## Search flow

1. `parseMusicQuery` extracts supported constraints when it can match them to the catalog: explicit language, a known genre or year, and known artist, album, or composer fields.
2. For a language constraint, Wavv reads supported language fields from tracks that have not been checked yet. Only recognized explicit metadata qualifies; missing or unrecognized language remains unknown.
3. Remaining descriptive words are encoded by the bundled text model and compared with cached audio embeddings. File metadata and above-threshold music tags also contribute lexical matches.
4. `rankLocalSearch` filters metadata constraints first, combines available semantic and text scores, then applies a small bounded personal tie-breaker before returning the best local matches. Personalization cannot add tracks outside the query's filtered candidate set and cannot outweigh a clear semantic/text relevance gap.

If semantic inference is unavailable or a track has no current embedding, text and metadata matching remain available. Queries over fields not present in the library cannot produce reliable matches. Lyrics are not indexed by search.

## Categories

Search categories come from genre values actually found in the catalog. A category card is shown only when a track for that genre has readable artwork. When preference evidence exists, cards and tracks within a selected genre are taste-ordered; all available categories and matching tracks remain available. Tapping a category filters the catalog by its genre tag. Wavv does not use a fixed demo catalog or infer missing categories from a cover image.

## Personalization signals

After playback state confirms a track, the playback service records local play, pause/resume, completion, replay, or skip activity as applicable. The selected surface is stored as a small source label (such as search, collection, or autoplay); raw search text and temporary mood requests are not stored. Successful user-requested queue additions are also recorded, but automatic queue population is not. Explicit favorites and playlist actions are stored as well. Recent session signals can influence short-term suggestions; older behavior and favorite tracks can influence longer-term suggestions. A direct user selection has more weight than a track advanced to automatically. A start alone is weak evidence: an early skip is a weak negative, while a long listen followed by a skip still contributes engagement. Completion and favorites are stronger signals.

When a suggestion is scored, Wavv can use matching artists and genres, above-threshold music tags, and semantic similarity between available audio embeddings. Repeated exposure receives a recency penalty, and the result order is diversified so one artist or album does not dominate. Time of day is a weak local context only after qualifying listening has occurred across at least three distinct days and two tracks in the current daypart; otherwise it has no effect. Mood words in search remain an explicit, temporary semantic request. Wavv does not infer a user's mood from the clock or turn DCLAP similarity into a verified tag.

Home's personalized shelves and evidence-backed mixes refresh as local listening signals change. Search lightly personalizes ranked results and taste-orders category cards and genre-filtered tracks. Autoplay receives updated rankings during playback, but removes tracks already in the playback queue and tracks dismissed from the current session. If the listener manually reorders Autoplay suggestions, later refreshes preserve that relative order. When recommendations are available, the Library offers an explicit “For You” sort for Songs, Liked Songs, Artists, and Albums; other sorts and user-created playlist contents remain separate.

## Mixes and cold start

Home mixes are generated from the current catalog and available local signals, for example recommendations, tracks to rediscover, repeated plays, and a sufficiently represented preferred genre. They are temporary queues, not playlists. Wavv does not modify a user-created playlist without an explicit action.

There is no cross-user profile or server ranking. With a new or sparse listening history, Wavv may show no personalized mix until enough evidence exists. This is intentional; it avoids presenting a fabricated preference as personalization.

Listening events are retained for up to one year. The user can clear history without removing favorites or playlists; see [Privacy](privacy.md).

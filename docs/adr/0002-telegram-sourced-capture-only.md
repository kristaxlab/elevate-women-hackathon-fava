# Capture text only from Telegram (no platform scrapers in v1)

Saved Items store the URL (when present) and whatever caption, link-preview, or forwarded-post text Telegram already delivers. We do not call Instagram/Meta oEmbed or scrape HTML for captions in v1: those paths are fragile, ToS-hostile for building a searchable archive, and easy to burn demo time on. Enrichment beyond Telegram remains a later, explicit decision.

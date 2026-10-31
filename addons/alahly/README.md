# Al Ahly matches

A TV-first first-team fixtures and recent-results screen for the personal Clean Your TV setup. The next match appears first, followed by upcoming fixtures and recent scorelines. Times are shown in Cairo time. D-pad focus moves through the cards, and the source and last update are visible near the header.

The checked-in screenshot shows the updated two-column layout on the TV.

## Match data

The primary source is [API-Sports Football v3](https://www.api-football.com/documentation_v3). Put `API_KEY=<your key>` in the workspace's `.env.local` (one level above `cleanyourtv/`) before building. Only the addon reads it at build time. The key is embedded in the locally sideloaded addon APK, so do not distribute that APK or commit the key. It cannot be fully hidden in an offline client.

The addon resolves Al Ahly's Egyptian team ID once and requests current-season fixtures across competitions. The launcher and addon read the same persisted feed through a signature-protected, read-only Android ContentProvider. The launcher contains no API key or football network client. Reads do not imply an API request: the addon waits 72 hours when the next fixture is distant, 24 hours when it is within a week, until 90 minutes before kickoff on match day, and 20 minutes during the match window while either UI is visible. Failed requests back off. It caps all API-Sports calls at 25 per UTC day and pauses until UTC midnight when the provider reports five or fewer calls remaining or returns HTTP 429.

When API-Sports is unavailable or incomplete, the addon checks the public [Al Ahly homepage](https://www.alahlyegypt.com/en) and [first-team page](https://www.alahlyegypt.com/en/football/first-team) at most daily as a fallback. Stale data carries its last update time, and only a recent API status can be labeled LIVE.

The club-site fallback exposes only its next two matches and three latest results. API-Sports coverage can vary by competition and season.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Fetch first-team fixtures and results. |
| `READ_MATCHES` (signature) | Let the launcher read match summaries from the addon. |

No account, location, background service, ads, or telemetry are used by the addon. Backups are disabled.

## Build and install

Build with JDK 17 and Android SDK platform 34. Put `API_KEY=<your key>` in the workspace's `.env.local` (one level above `cleanyourtv/`) first: `install-tv.sh` reads that file before building this addon and, when the line is missing, prints how to add it and skips the addon instead of failing silently. From `cleanyourtv/`, run:

```bash
./install-tv.sh --addons alahly --wallpaper themes/scenes/wallpapers/15-cairo-nile.jpg
```

The script takes the TV from `--device`, `ADB_SERIAL`, `TV_HOST` (plus `TV_PORT`), or the single device already connected to ADB. It tests and builds both APKs, installs the addon first so its signature permission exists when the launcher is installed, pushes the Cairo Nile wallpaper and a config that switches it on through the launcher's theme provider over the same ADB connection, opens both screens, and saves screenshots and focused logs to `shots/` (`--captures <dir>` moves them). Leave out `--addons` to install only the launcher, add `--launcher home` to hand Home over as well (that path needs `TV_HOST` and `tools/venv`), or `--dry-run` to print every command first. The Cairo image is also in the Scenes rotation if you apply that theme later. Both APKs use the same local debug signing key. The addon's module card opens it from the Clean Your TV launcher.

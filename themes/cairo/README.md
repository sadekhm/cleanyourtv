# Cairo on the Nile

A quiet dusk skyline over the Nile: the Cairo Tower, a bridge and a minaret under an open violet
sky, with the water carrying the light back. One picture, no rotation.

![Cairo theme](screenshot.jpg)

## What it changes

| Key | Value | Effect |
|---|---|---|
| `background_color` | `#16122B` | The sky's violet behind everything |
| `wallpaper` | `true` | Show `wallpaper.jpg` |
| `wifi_status_color` | `true` | The Wi-Fi icon green while online, white while it is not |
| `wifi_status_warnings` | `true` | The same icon orange when connected but the internet is not there |
| `vpn_pill` | `true` | The VPN as a pill of its own, green with a lock while the tunnel is up |
| `vpn_app` | `com.tailscale.ipn` | The app a long press on the Wi-Fi pill opens |
| `always_show_names` | `true` | A name under every tile, not only the focused one |
| `app_library_selection` | `true` | In the app library, OK ticks an app for Home instead of doing nothing |
| `header_white` | `true` | The date and its divider in full white over the bright sky |
| `back_opens_clock` | `true` | Back on Home opens the clock over the wallpaper |
| `app_glow` | `true` | The focus highlight glows in the app's colour on every tile |
| `fade_rows` | `true` | The rows fade as they scroll up under the clock |

## Install

```bash
TV_HOST=<tv ip> tools/theme.sh themes/cairo
# Windows
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost <tv ip> themes\cairo
```

Undo with `tools/theme.sh --reset`.

## Tested on

Chromecast 4K, launcher 3.2.

## Credit and forks

The picture is the author's own work; see [`CREDITS.md`](CREDITS.md). You may copy, change and
reuse this theme. If you publish a modified version, use a different folder name so people can
tell them apart.

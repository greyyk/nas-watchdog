# Ready-to-upload Play artwork

| File | Use | Verified dimensions |
|---|---|---|
| `icon.png` | High-res app icon | 512×512 PNG, full opaque background |
| `feature-graphic.png` | Main store listing feature graphic | 1024×500 PNG |
| `icon-source.svg` / `feature-source.svg` | Editable original project artwork | Vector source |

These are new original graphics with the own NAS Watchdog palette and storage/heartbeat motif. No manufacturer's wordmark, logo, product photograph, website capture, UI, trade dress, or promotional artwork is used. Text reflects actual independent monitoring and does not imply manufacturer affiliation.

## Screenshots

`play/screenshots/` contains captures from the Android 16 / API 36 emulator using the release source's locally generated **synthetic demo**, not a real host. Each subject shows `DEMO` in the application header or otherwise makes generated data explicit in the UI. When the app changes after screenshot capture, regenerate these instead of presenting stale visuals.

Upload at least two phone screenshots as currently requested by Console. Useful coverage:

1. Overview (`01-overview.png`)
2. Thermal history (`02-thermal.png`)
3. Storage/SMART (`03-storage.png`)
4. User-controlled background monitoring (`04-monitoring.png`)

Captured screenshots use the app's native 1080×2400 display, contain no production token, personal hostname, private address, real user logs, or third-party promotional content. Do not add auto-generated marketing device frames with unsupported processor/storage claims.

## Regenerating the graphics

The ready PNGs are committed; normal app/container builds do not need Python or a graphics renderer. To edit SVGs, use a project-local isolated Python environment:

```sh
python3 -m venv .test-runtime/assets-venv
.test-runtime/assets-venv/bin/pip install --disable-pip-version-check pillow cairosvg
.test-runtime/assets-venv/bin/python - <<'PY'
import cairosvg
for source, target in (
    ('play/assets/icon-source.svg', 'play/assets/icon.png'),
    ('play/assets/feature-source.svg', 'play/assets/feature-graphic.png'),
):
    cairosvg.svg2png(url=source, write_to=target)
PY
```

A virtual environment is deliberately used so nothing is installed globally. Read the resulting PNGs and verify their dimensions again before committing. New project artwork source, screenshots, and generated originals are covered by the same project terms where you own/contribute the rights; all third-party asset notices remain in [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md).

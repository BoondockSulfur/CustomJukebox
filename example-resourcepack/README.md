# CustomJukebox — Example Resource Pack

This folder is a **complete, working resource pack**. Add your `.ogg` files, zip it,
host it — done. Everything else is already in place and matches the discs
CustomJukebox writes into `disc.json` on first start.

No datapack is involved. Custom music needs a resource pack and nothing else.

---

## What is already here

```
example-resourcepack/
├── pack.mcmeta                                  pack metadata
├── README.md                                    this file
└── assets/minecraft/
    ├── sounds.json                              the three sound events
    ├── sounds/records/                          ← YOUR .ogg FILES GO HERE
    ├── items/                                   which model a disc item uses
    │   ├── music_disc_13.json
    │   ├── music_disc_cat.json
    │   └── music_disc_blocks.json
    ├── models/item/                             the models those point at
    │   ├── epic_journey_disc.json
    │   ├── ocean_dreams_disc.json
    │   └── forest_walk_disc.json
    └── textures/item/                           placeholder disc textures
        ├── epic_journey_disc.png                (replace with your own art)
        ├── ocean_dreams_disc.png
        └── forest_walk_disc.png
```

The three discs it is built around are the ones the plugin ships with:

| Disc id | Base item | `customModelData` |
|---|---|---|
| `epic_journey` | `MUSIC_DISC_13` | 1001 |
| `ocean_dreams` | `MUSIC_DISC_CAT` | 1002 |
| `forest_walk` | `MUSIC_DISC_BLOCKS` | 1003 |

---

## The one thing that trips everyone up

Four names have to line up. Get one wrong and the disc plays in silence, with no
error anywhere:

| Where | Value for `epic_journey` |
|---|---|
| The file | `assets/minecraft/sounds/records/epic_journey.ogg` |
| `sounds.json` — event key | `"music_disc.epic_journey"` |
| `sounds.json` — `name` | `"records/epic_journey"` (path under `sounds/`, no `.ogg`) |
| `disc.json` — `sound` | `"music_disc.epic_journey"` |

`sounds.json` maps an **event name** to a **file path**. `disc.json` refers to the
event name, never to the file. The `records/` folder is a plain subfolder — call
it whatever you like, as long as `name` matches.

---

## Step 1 — Convert your music to OGG

Minecraft plays **OGG Vorbis** only. MP3, WAV and FLAC do not work.

```bash
ffmpeg -i input.mp3 -c:a libvorbis -q:a 5 epic_journey.ogg
```

Or in Audacity: *File → Export → Export as OGG*.

Recommended: 44100 Hz, quality 5–7 (≈128–192 kbps). Mono halves the file size and
is fine for background music. Keep individual files well under 10 MB — every
player downloads the whole pack on join.

Put the files into `assets/minecraft/sounds/records/` and delete the
`PUT_YOUR_OGG_FILES_HERE.txt` placeholder.

## Step 2 — Register them in `sounds.json`

One block per song:

```json
{
  "music_disc.my_song": {
    "sounds": [
      {
        "name": "records/my_song",
        "stream": true
      }
    ]
  }
}
```

`"stream": true` is **required**. Without it Minecraft loads the entire track into
memory before playing, which stutters and wastes RAM on longer songs.

## Step 3 — Point a disc at it in `disc.json`

`plugins/CustomJukebox/disc.json` on your server:

```json
{
  "discs": {
    "my_song": {
      "displayName": "&6My Song",
      "author": "Artist Name",
      "sound": "music_disc.my_song",
      "type": "MUSIC_DISC_13",
      "customModelData": 1004,
      "durationTicks": 4280,
      "fragmentCount": 9,
      "lore": ["&7A description", "&7Duration: 3:34"],
      "description": "My Song"
    }
  }
}
```

- `sound` — the event key from `sounds.json`, exactly as written there
- `durationTicks` — song length in ticks, **20 ticks = 1 second** (3:34 → 214 s → 4280).
  Needed for playlists, ambient zones and the progress bar; a disc without it is
  skipped in zones
- `customModelData` — only needed if you want a custom texture (Step 6). Give
  every disc its own number
- `type` — any vanilla music disc material

You can do all of this in-game instead: `/cjb gui` → Admin Panel → Disc Management.

## Step 4 — Zip the pack

Zip the **contents** of this folder, not the folder itself. `pack.mcmeta` must sit
at the top level of the ZIP, otherwise Minecraft rejects the pack.

```bash
# Linux / macOS
cd example-resourcepack
zip -r ../customjukebox-pack.zip . -x '.*'

# Windows PowerShell
cd example-resourcepack
Compress-Archive -Path * -DestinationPath ..\customjukebox-pack.zip
```

Test it locally first: drop the ZIP into `.minecraft/resourcepacks/` and enable it
in the game. If the discs look right there, the pack is fine and anything that
still fails is server-side.

## Step 5 — Host it and tell the server

The URL must **download the ZIP directly**. Minecraft cannot read a webpage.

- **GitHub Releases** — upload the ZIP as a release asset, then copy the link from
  the asset itself. It looks like
  `https://github.com/user/repo/releases/download/v1/customjukebox-pack.zip`.
  A `/blob/` URL is a webpage and will not work.
- **Dropbox** — share link, change the trailing `?dl=0` to `?dl=1`.
- **Google Drive** — share to "anyone with the link", then use
  `https://drive.google.com/uc?export=download&id=FILE_ID`.
- **Your own webserver** — any HTTPS URL that serves the file.

Generate the hash:

```bash
sha1sum customjukebox-pack.zip          # Linux / macOS
certutil -hashfile customjukebox-pack.zip SHA1   # Windows
```

And put both into `server.properties`:

```properties
resource-pack=https://example.com/customjukebox-pack.zip
resource-pack-sha1=d53231bc253b4118a402116e9e2c2deb88433abd
require-resource-pack=false
```

**Regenerate the hash after every change to the pack.** A stale hash makes clients
reject the download, and the error they see does not say why.

Restart the server, then `/cjb reload` after any later `disc.json` edit.

## Step 6 — Custom disc textures (optional)

Already wired up in this pack; this section is only needed for your own discs.

Since Minecraft 1.21.4 a resource pack no longer overrides item models with
`overrides` / `predicate` blocks. Item appearance is decided in
`assets/minecraft/items/<base_item>.json`, which dispatches on the
`custom_model_data` value the plugin puts on the item:

```json
{
  "model": {
    "type": "minecraft:range_dispatch",
    "property": "minecraft:custom_model_data",
    "entries": [
      { "threshold": 1001,
        "model": { "type": "minecraft:model", "model": "minecraft:item/epic_journey_disc" } }
    ],
    "fallback": { "type": "minecraft:model", "model": "minecraft:item/music_disc_13" }
  }
}
```

Add one entry per disc that shares the same base item, with `threshold` set to that
disc's `customModelData`. The `fallback` keeps ordinary vanilla discs looking
normal. Each entry needs a model file
(`models/item/<name>_disc.json`, `parent: minecraft:item/generated`) and a 16×16
PNG under `textures/item/`.

The placeholder textures in this pack are deliberately plain — replace them.

## Step 7 — Test

```
/cjb give <player> epic_journey
```

Place the disc in a jukebox, or skip the jukebox with `/cjb play epic_journey`.

---

## `pack.mcmeta` and your Minecraft version

This pack ships with:

```json
{ "pack": { "min_format": [69, 0], "max_format": [88, 0] } }
```

which covers **Minecraft 1.21.9 through 26.2**. `min_format` / `max_format` exist
since 1.21.9; on **1.21.4 – 1.21.8** replace them with a single `pack_format`:

| Minecraft | Format |
|---|---|
| 1.21.4 | 46 |
| 1.21.5 | 55 |
| 1.21.7 – 1.21.8 | 64 |
| 1.21.9 – 1.21.10 | 69 |
| 1.21.11 | 75 |
| 26.1 – 26.1.2 | 84 |
| 26.2 | 88 |

```json
{ "pack": { "description": "...", "pack_format": 46 } }
```

A mismatched number usually still loads, with an "incompatible pack" warning in the
client's pack list. Wrong assets do not.

---

## Troubleshooting

**The disc plays but there is no sound.**
The usual cause, in this order:

1. A name does not line up — re-read the table at the top of this file. Set
   `"debug": true` in `config.json`; the console then logs the exact sound key it
   sends, which is the fastest way to spot the mismatch.
2. The file is not really OGG Vorbis. Some converters write `.ogg` containing Opus,
   which Minecraft ignores. Check with `ffprobe my_song.ogg` — it must say
   `Audio: vorbis`.
3. The player never accepted the pack. They hear everything else, just not this.

**Nothing happens when players join / no pack prompt.**
Open the `resource-pack` URL in a browser. If it shows a page instead of starting a
download, the URL is wrong. If it downloads, check the SHA-1.

**"Failed to apply resource pack" / players are kicked.**
Stale `resource-pack-sha1`, or `require-resource-pack=true` with a broken URL. Set
it to `false` while you are still testing.

**The disc texture is missing (black/purple checkerboard).**
The model or texture path is wrong. Paths are case-sensitive, and
`minecraft:item/foo` means `assets/minecraft/textures/item/foo.png`.

**The disc looks like a normal vanilla disc.**
`customModelData` in `disc.json` and `threshold` in `items/*.json` disagree, or the
disc item was handed out before you set `customModelData`. Re-issue it with
`/cjb give`.

**Bedrock players hear nothing.**
Bedrock cannot read Java resource packs at all. Install the
[Bedrock Extension](https://modrinth.com/plugin/bs-customjukebox-bedrock-extension),
which generates a Bedrock pack from these same files and hands it to Geyser.

---

## Reference

- Resource packs: <https://minecraft.wiki/w/Resource_pack>
- `sounds.json`: <https://minecraft.wiki/w/Sounds.json>
- Item model definitions: <https://minecraft.wiki/w/Items_model_definition>
- Pack formats: <https://minecraft.wiki/w/Pack_format>

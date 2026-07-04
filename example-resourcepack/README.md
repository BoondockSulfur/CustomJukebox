# CustomJukebox Example Resource Pack

This is a template resource pack for CustomJukebox. Follow these steps to create your own pack with custom music.

## 📁 Structure

```
example-resourcepack/
├── pack.mcmeta                 # Pack metadata (required)
├── pack.png                    # Pack icon (optional, 128x128px recommended)
├── README.md                   # This file
└── assets/
    └── customjukebox/
        ├── sounds.json         # Sound definitions (required)
        └── sounds/
            ├── epic_journey.ogg    # Your music files (.ogg format)
            ├── ocean_dreams.ogg
            └── forest_walk.ogg
```

## 🎵 Adding Your Own Music

### Step 1: Convert music to .ogg format

Music files **must** be in `.ogg` format (Ogg Vorbis).

**Conversion tools:**
- **Audacity** (Free): File → Export → Export as OGG
- **ffmpeg** (Command line):
  ```bash
  ffmpeg -i input.mp3 -c:a libvorbis -q:a 5 output.ogg
  ```
- **Online converter**: cloudconvert.com

**Recommendations:**
- Sample rate: 44100 Hz
- Bitrate: 128-192 kbps (quality 5-7 in Audacity)
- Mono or Stereo both work

### Step 2: Add music files

> **Note:** This template does not include real `.ogg` music files. You must add your own before the pack will play any sounds.

1. Place your `.ogg` files in `assets/customjukebox/sounds/`
2. Name them exactly as defined in your `disc.json` (e.g. `epic_journey.ogg`)

### Step 3: Update sounds.json

Add an entry for each sound:

```json
{
  "music_disc.your_song_name": {
    "sounds": [
      {
        "name": "customjukebox:your_song_name",
        "stream": true
      }
    ]
  }
}
```

**Important:**
- `"stream": true` is required for music to reduce memory usage
- The `name` field must match the filename (without `.ogg`) — e.g. `customjukebox:your_song_name` → `assets/customjukebox/sounds/your_song_name.ogg`
- The event key uses the `music_disc.` prefix (e.g. `music_disc.your_song_name`)
- Sound key in disc.json must be `"customjukebox:music_disc.your_song_name"`

### Step 4: Update disc.json

In your server's `plugins/CustomJukebox/disc.json`, configure the disc:

```json
{
  "discs": {
    "your_disc_id": {
      "displayName": "&6Your Disc Name",
      "author": "Artist Name",
      "sound": "customjukebox:music_disc.your_song_name",
      "type": "MUSIC_DISC_13",
      "customModelData": 1001,
      "durationTicks": 6000,
      "fragmentCount": 9,
      "lore": [
        "&7Your description",
        "&7Duration: 5:00"
      ],
      "description": "Your Disc Name"
    }
  }
}
```

**Important:**
- `sound` must match the full namespaced event key (e.g. `customjukebox:music_disc.your_song_name`)
- `durationTicks` is the song length in ticks (20 ticks = 1 second)
- All string values must be quoted in JSON

## 📦 Creating the Pack

### Option A: Manual ZIP

1. Select **all files inside** `example-resourcepack/` (pack.mcmeta, assets/, etc.)
2. Right-click → "Send to" → "Compressed (zipped) folder"
3. Name it `customjukebox-resourcepack.zip`

**Important:** Do NOT zip the folder itself! Zip the contents.

### Option B: Command Line

**Windows:**
```bash
cd example-resourcepack
powershell Compress-Archive -Path * -DestinationPath ../customjukebox-resourcepack.zip
```

**Linux/Mac:**
```bash
cd example-resourcepack
zip -r ../customjukebox-resourcepack.zip *
```

## 🌐 Hosting the Pack

### Option 1: GitHub Releases (Recommended)

1. Create a GitHub repository
2. Go to "Releases" → "Create a new release"
3. Upload your `customjukebox-resourcepack.zip`
4. Publish release
5. Copy the direct download URL (right-click the .zip → Copy link address)

> **Warning:** Do not use GitHub `/blob/` URLs. They point to a GitHub webpage, not directly to the ZIP file.
>
> **Bad:** `https://github.com/user/repo/blob/main/customjukebox-resourcepack.zip`
>
> **Good:** `https://github.com/user/repo/releases/download/v1/customjukebox-resourcepack.zip`

### Option 2: DropBox

1. Upload pack to DropBox
2. Share → Copy link
3. Change `?dl=0` to `?dl=1` at the end of the URL
4. Use this modified URL as the `resource-pack` value in `server.properties`.

### Option 3: Google Drive

1. Upload pack to Google Drive
2. Right-click → "Get link" → "Anyone with the link"
3. Copy the file ID from the URL
4. Use: `https://drive.google.com/uc?export=download&id=FILE_ID`

### Option 4: Self-hosting

Upload to your own web server and use the direct URL.

## 🔒 Generate SHA-1 Hash

**Windows:**
```bash
certutil -hashfile customjukebox-resourcepack.zip SHA1
```

**Linux/Mac:**
```bash
sha1sum customjukebox-resourcepack.zip
```

Copy the hash and paste it into `server.properties` as `resource-pack-sha1`.

## ⚙️ Configure Server

Edit `plugins/CustomJukebox/config.json`:

```json
{
  "settings": {
    "enabled": true,
    "language": "en",
    "enable-gui": true,
    "debug": false
  },
  "playback": {
    "volume": 4.0,
    "default-loop": false
  }
}
```

**Note:** CustomJukebox uses the server's built-in resource pack system.
Configure resource pack distribution in your `server.properties`:

```properties
resource-pack=https://your-download-url/customjukebox-resourcepack.zip
resource-pack-sha1=your-sha1-hash-here
require-resource-pack=false
```

Restart server or use `/cjb reload`.

## 🧪 Testing

1. Join server
2. Accept the resource pack prompt
3. Use `/cjb give <player> epic_journey`
4. Place disc in jukebox
5. You should hear your custom music!

**Alternative test methods:**
- Use `/cjb play epic_journey` to play directly without a jukebox
- Use `/cjb gui` to open the disc selection menu
- Enable debug mode in config.json to see detailed logs

## 🎨 Optional: Custom Textures

To add custom disc textures, create:
```
assets/minecraft/models/item/music_disc_13.json
```

Example:
```json
{
  "parent": "item/generated",
  "textures": {
    "layer0": "item/music_disc_13"
  },
  "overrides": [
    {
      "predicate": {
        "custom_model_data": 1001
      },
      "model": "customjukebox:item/epic_journey_disc"
    }
  ]
}
```

Then add your texture at:
```
assets/customjukebox/textures/item/epic_journey_disc.png
```

## 📝 Notes

- For Minecraft 1.21.9 and newer, use `min_format` and `max_format` for compatibility ranges. This example uses `[69, 0]` to `[75, 0]`, covering Minecraft 1.21.9 through 1.21.11.
- For older versions, check: https://minecraft.wiki/w/Pack_format
- Music files should not exceed 10MB each for best performance
- Test locally first: Place pack in `.minecraft/resourcepacks/`

## 🔗 Resources

- Minecraft Wiki - Resource Packs: https://minecraft.wiki/w/Resource_Pack
- Sound Events: https://minecraft.wiki/w/Sounds.json
- Pack Format History: https://minecraft.wiki/w/Pack_format

## 💡 Troubleshooting

**"Invalid resource pack URL"**
- Make sure URL is a direct download link
- Test URL in browser - should download immediately

**"Sound not playing"**
- Check sound name matches in disc.json, sounds.json, and filename
- Verify .ogg file is valid (test with VLC player)
- Enable debug mode in config.json and check console
- Look for validation errors when the plugin starts
- Players may need to `/reload` or rejoin to load the resource pack

**"Players kicked for declining pack"**
- Set `require-resource-pack=false` in server.properties
- Or educate players to accept the pack
- Note: Players who decline won't hear custom sounds

**"Hash mismatch"**
- Regenerate SHA-1 hash after ANY changes to the pack
- Make sure you're hashing the correct .zip file

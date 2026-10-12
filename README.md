# Filmup (stage 1)
Native Android video editor (Kotlin, Compose, Media3).

## APK build (GitHub)
1. GitHub-এ নতুন repo বানান, এই folder-এর সব ফাইল (`.github` সহ) upload/push করুন (branch: main)।
2. Repo → Actions → "Build Filmup APK" → Run workflow (push করলে নিজেই চলে)।
3. Run শেষ হলে নিচে Artifacts থেকে `Filmup-debug-apk` download করে zip খুলে APK install করুন।

## Local build
`gradle :app:assembleDebug` (Gradle 8.9, JDK 17, Android SDK 34)

## Implemented
Import videos · multi-clip timeline with thumbnails & fixed playhead · select · trim handles · split · delete · duplicate · reorder · undo/redo · per-clip caption (Bangla/English) overlay · music track · filters (B&W, Sepia, Contrast, Bright, Vivid) in preview+export · mute original audio · autosave/recovery · MP4 export (480/720/1080, H.264/AAC) saved to Movies/Filmup · unit tests.

## Not yet implemented
Speed, volume automation, transitions, keyframes, chroma key, masks, PiP, multiple video tracks, drag-and-drop move, voiceover, SRT, AI features, effect browser, waveform. See chat for the roadmap.

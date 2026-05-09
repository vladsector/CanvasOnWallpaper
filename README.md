# Spotify Canvas on Wallpaper

> **Note**: When you first launch the app, you will see a black screen on background. This is normal. 

---

## Getting Started

Once you start playing music in Spotify, the app will automatically detect the track and instantly pick up the **Canvas**. If the song doesn't have a Canvas, the app will display the **album art** instead.

## Acknowledgments

A huge thank you to [Paxsenix0/Spotify-Canvas-API](https://github.com/Paxsenix0/Spotify-Canvas-API) — this project wouldn't have been possible without it.

---


> *If you’d like to contribute to the project and help me keep the lights on, donations are much appreciated.*

> *USDT: TF6ZXJEU25V2Xu7uZGFHKQjDWoStkaMvyy*

> *BTC: bc1q6da5claezrt2kw7wj645nz23d007ml2fdagu98*

> *ETH: 0x05b2b76f82a8f6776b57b8933272b4c4be271903*

## 🚀 Key Features

*   **Canvas on Home Screen**: Automatically sets the current Spotify track's video loop as your live wallpaper.
*   **Smart Fallback**: If a track doesn't have a Canvas video, the app automatically fetches the album art directly from the Spotify player.
*   **High-Quality Effects**: 
    *   **Gaussian Blur**: Realistic Gaussian blur (7x7 grid) for creating a deep, atmospheric background.
    *   **Smooth Transitions**: Seamless transitions between tracks using Fade and Blur effects.
*   **Full Customization**: Independent brightness and blur intensity settings for both video and static covers.
*   **Dual Authentication Methods**:
    *   Secure login via the official Spotify account page.
    *   Manual `sp_dc` token entry for maximum privacy.
*   **Energy Efficient**: All rendering is handled by the GPU (OpenGL ES 2.0), minimizing CPU and battery load.

## 🛠 How It Works

### 1. Tracking (Background Service)
The app uses `SpotifyNotificationService` (based on `NotificationListenerService`), which:
*   Connects to Spotify's active media session.
*   Reacts instantly to track changes via system callbacks.
*   Extracts the unique `Track ID` and metadata (title, artist, cover).

### 2. Content Retrieval (API)
As soon as a track changes, the service sends an asynchronous request to the server:
`http://95.85.245.174:3000/api/canvas?trackId=[ID]&sp_dc=[TOKEN]`
The server returns a JSON with a direct link to the `.mp4` loop file.

### 3. Rendering (Live Wallpaper Engine)
`CanvasWallpaperService` manages the display:
*   **Video**: Uses `MediaPlayer` integrated with an OpenGL texture (`SurfaceTexture`).
*   **Covers**: If no video is available, it loads the album art from the Spotify cache and renders it using Hardware Canvas.
*   **Shaders**: A custom fragment shader processes each frame on-the-fly, applying brightness and Gaussian blur settings.

## 📱 Interface
*   **Onboarding**: Smooth first-run process with welcome screens and step-by-step permission handling.
*   **Main Screen**: Displays the current track, artist, and quick access to settings.
*   **Settings**: Full control over the visual style of the wallpaper.
*   **Debug Tool**: Transparent information about current tokens, links, and raw server responses for complete system confidence.

## 🔧 Technical Stack
*   **Language**: Kotlin
*   **Graphics**: OpenGL ES 2.0 + GLSL (Shaders)
*   **Animation**: ValueAnimator API
*   **Networking**: HttpURLConnection + Coroutines (IO Dispatcher)
*   **Design**: Material Design 3 (M3)

---
*Designed for music lovers who value aesthetics and technology.*


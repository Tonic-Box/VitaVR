# VitaVR
VitaVR is a VitaLite plugin that lets you play in a VR headset, and switch back to the desktop at any time without logging out.
- Play in first person at life size, or as a tabletop view in front of you
- Laser pointers on both controllers for clicking the world and the interfaces
- Your interfaces float around you, with health bars, hitsplats and overheads shown in 3D
- A wrist display with your orbs, compass and the time
- Comfort options, haptics, and session recording

## Requirements
- Windows
- [VitaLite](https://github.com/Tonic-Box/VitaLauncher/releases)
- A VR headset with an OpenXR runtime: SteamVR, Meta Quest Link or Virtual Desktop
- The GPU plugin (VitaVR turns it on for you while in VR); 117 HD support is in progress

## Installing
Put `VitaVR-1.0-SNAPSHOT.jar` in your `~\.runelite\sideloaded-plugins` folder and start VitaLite.

## Using it
1. Start your headset's VR app (SteamVR, Link or Virtual Desktop).
2. Open the **VitaVR** side panel and press **Launch in VR**.
3. Press **Back to desktop** when you're done, or just close the VR app. The game goes back to how it was.

Settings are under **VitaVR** in the config panel, and in the headset's own settings panel.

## Controls
| Button | What it does |
|--------|--------------|
| Trigger | Click what the pointer is on |
| Right grip | Right-click (opens the menu) |
| Left grip (hold) | Ctrl |
| Left grip + trigger on the chatbox, minimap or inventory | Move it; while held, right stick left/right moves it further or nearer, up/down resizes it, left stick turns and tilts it |
| Right stick | Turn; scroll when pointing at the interfaces |
| A (right) | Switch between first person and tabletop |
| B (right) | Show or hide the interfaces |
| X (left) | VR settings |
| Y (left) | Keyboard |
| Headset recentre button | Put the view back at your player's head |

Turn your left wrist toward you to see the wrist display.

Moved interfaces stay where you put them. **Moved interfaces** in the headset's settings (Interfaces tab) puts them back.

## Recording
Tick **Record the session** on the side panel before launching, and **Hide chat in the recording** to leave the chatbox out of the video. You'll be asked where to save the video when you return to the desktop.
Recording needs [ffmpeg](https://ffmpeg.org/download.html) installed and on your PATH.

## Streaming
Tick **Stream window** on the side panel to open a **VitaVR stream** window showing what you see in VR, interfaces included, at 30 fps. Capture it with your streaming software's window capture. It uses the recording view settings, can be opened and closed at any time, and closes when you leave VR.

## Building from source
**Requirements:** JDK 11
```
./gradlew buildPlugin
```
This builds the jar straight into `~/.runelite/sideloaded-plugins`.

## Disclaimer
VitaVR is a third-party plugin. Use at your own risk. The developers are not responsible for any consequences resulting from the use of this software.

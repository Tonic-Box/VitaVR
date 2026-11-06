# VitaVR
VitaVR is a VitaLite plugin that lets you play in a VR headset, and switch back to the desktop at any time without logging out.
- Play in first person, or as a tabletop view
- Your interfaces float around you, with health bars, hitsplats and overheads shown in 3D
- A wrist display with your orbs, compass and the time
- Comfort options, haptics, and session recording

## Requirements
- Windows
- [VitaLite](https://github.com/Tonic-Box/VitaLauncher/releases)
- A VR headset with an OpenXR runtime: SteamVR, Meta Quest Link or Virtual Desktop
- GPU plugin or 117HD must be on (supports both)

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
| Right stick left/right | Turn |
| Right stick up/down | Scroll when pointing at the interfaces; tilt the table in tabletop |
| A (right) | Switch between first person and tabletop |
| B (right) | Show or hide the interfaces |
| X (left) | VR settings |
| Y (left) | Keyboard |
| Headset recentre button | Put the view back at your player's head |

Turn your left wrist toward you to see the wrist display.

### Moving the interfaces
Hold the left grip and point at the chatbox, minimap or inventory; it is outlined. Press the trigger to grab it and release to let go.

| While holding one | What it does |
|-------------------|--------------|
| Pointer | Moves it around you |
| Right stick left/right | Pushes it further away or pulls it nearer |
| Right stick up/down | Makes it bigger or smaller |
| Left stick left/right | Angles it left or right |
| Left stick up/down | Tilts it up or down |

Moved interfaces stay where you put them, between sessions too, and fold back into place while a large interface like the bank is open. **Moved interfaces** in the headset's settings (Interfaces tab) puts them all back.

## Recording
Tick **Record the session** on the side panel before launching, and **Hide chat in recordings and the stream** to leave the chatbox out of the video. You'll be asked where to save the video when you return to the desktop.
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

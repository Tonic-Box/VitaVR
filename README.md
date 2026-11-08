# VitaVR
VitaVR is a VitaLite plugin that lets you play in a VR headset, and switch back to the desktop at any time without logging out.
- Play in first person, or as a tabletop view
- Your interfaces float around you, with health bars, hitsplats and overheads shown in 3D
- A wrist display with your orbs, compass and the time
- Comfort options, haptics, and session recording
- Your own gestures: record them in VR, then have each one eat, switch gear, pray or cast

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
| Thumbstick click (hold) | Arm a gesture (can be changed, see Gestures) |
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

## Gestures
Make your own gestures in VR and have each one eat, drink, switch gear, pray, cast and more. You record and name them in the headset, then choose what they do on the desktop. Item steps find items by name or by id, never by slot, so they work wherever the item is.

### Kinds of gesture
| Kind | How you make it |
|------|-----------------|
| Spot | Put your hand on a place on your body (mouth, hip, shoulder…) and squeeze grip. That grip never right-clicks |
| Shape | Hold the arm input and draw a shape in the air |
| Flick | Snap your wrist; each flick can need the arm input held, or not |
| Two-hand | Hold both arm inputs and move both hands together |

The **arm input** is the thumbstick click by default. Set it for each hand in the Gestures tab or the config panel: thumbstick click, thumb rest touch (Touch controllers only), or grip and trigger. Gestures are measured on your body and turn with you, so they work whichever way you face. A shape or flick recorded with one hand can be set to **Either** hand, which mirrors it for the other.

### Recording them (in the headset)
1. Press **X** for the VR settings and open the **Gestures** tab.
2. Press **New spot**, **New shape**, **New flick** or **New two-hand**, and choose the hand.
3. Make it as the panel says: a spot is one squeeze; the others are made three times. A take that is too small, too quick or unlike the others is asked for again.
4. Press **Save**.

The tab also switches profile, turns gestures on and off, renames them with the keyboard (enter saves), records new takes (**Redo**) and deletes them (press **Delete** twice). **Testing** shows what each gesture matches without running anything. Gestures don't run while a VitaVR panel or the keyboard is open, or while the game wants typing.

### Choosing what they do (on the desktop)
Press **Gesture editor…** on the side panel. Pick a gesture to see it replay on a figure; drag to turn the view, and double click to put it back. Its steps run in order, all in the same game tick unless a **Wait** step comes between them.

| Step | Does |
|------|------|
| Use an item's op | Eat, drink, wield, rub… or **Eat or drink**, whichever the item has |
| Use an item's sub-option | Such as Rub then Edgeville; worn jewellery uses the option directly |
| Wear a set | Wears every piece you carry and aren't wearing yet; **Use what I'm wearing now** fills it in |
| Take off a slot | Removes what is worn there |
| Prayers | On, off, toggle, or **only these** (turns the rest off) |
| Quick prayers, special attack, run | On, off or toggle |
| Cast a spell | On nothing, on what you point at when you make the gesture, or on an item |
| Use an item on something | On what you point at, or on another item |
| Open a tab, switch profile, wait ticks | |

An item can be that exact item, **any dose or charge** of it (lowest dose first by default), a name pattern like `*shark*`, any item with an op (such as any food you can Eat), or a list where each is used **or else** the next. Search covers every item in the game, untradeables too, or take items from your inventory or what you're wearing.

Each step can **skip** or **stop the rest** when it can't run, such as when there is no food left. Steps and whole gestures can have conditions (hitpoints, prayer, special or run energy, wearing or carrying an item), and each gesture has a cooldown. **Run now** tries a gesture's steps without the headset.

Profiles are named sets of what the gestures do: the same gestures can do different things in your PvP and your bossing profiles. Switch profile on the side panel, in the Gestures tab, or with a **Switch profile** step. Gestures and profiles are saved in `~/.runelite/vitavr/gestures`.

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

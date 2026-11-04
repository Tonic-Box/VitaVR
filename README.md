# VitaVR
A VitaLite plugin that moves your logged in session into a VR headset, and back, without logging out.

## Info
Open the VitaVR side panel and press **Launch in VR**. The headset takes over the game: first person or a tabletop view,
your interfaces around you, laser pointers on both controllers. **Back to desktop** (or closing the headset app) gives the
game back to the desktop client exactly as it was, GPU or 117 HD included.

Settings are under VitaVR in the RuneLite config panel, and in the headset's own settings panel.

## Building
```
./gradlew buildPlugin
```
builds the jar, with OpenXR bundled, straight into `~/.runelite/sideloaded-plugins`.

Windows only, with an OpenXR runtime (SteamVR, Oculus/Meta Link or Virtual Desktop).

## Live checklist
What to check on a headset, by checkpoint.

**CP1 session handling**
- [ ] Launch with 117 HD on: HD pauses, GPU draws while in VR, HD comes back after.
- [ ] Launch with neither on: GPU starts for VR and stops after.
- [ ] Back to desktop: window size, frame rate cap and vsync are as before.
- [ ] Closing the headset app (SteamVR/Link) returns to desktop and the side panel says why.
- [ ] No runtime or headset: the side panel shows the reason and the desktop is untouched.

**CP2 stereo world, first person**
- [ ] The world is all around you in stereo at life size, seeing from your player's head with the head itself hidden.
- [ ] Looking all the way round: no missing zones behind you, scenery out to the draw distance.
- [ ] Turning the head turns the client camera (minimap and compass follow).
- [ ] Right stick turns you: 30 degree snaps by default, smooth when Turn is set to smooth.
- [ ] Walking and running carry you smoothly; a teleport jumps straight there.
- [ ] Transparent things (water, windows, smoke) are drawn once, not doubled, in both eyes.
- [ ] Seated or standing, you see from the player's head on launch; holding the headset's recentre button (Meta button) puts you back there and facing ahead.
- [ ] Standing idle, the body turns to face where you look; walking and acting it faces as the game has it, eased between.
- [ ] The view follows the head as animations move it (Head follow: Always), eased; Actions follows only while acting in place.
- [ ] Your body stays under you while walking, no lag between it and the view.
- [ ] Indoors with Keep roofs on: roofs stay over you; off: they hide as on desktop.
- [ ] Mirror on: the desktop window shows the left eye; off: the desktop shows only the interfaces.
- [ ] Changing a GPU setting while in VR (e.g. vertex snapping) keeps the world drawn all round.
- [ ] On a boat: you stand on the boat as it sails.

**CP2b body labels** (needs VitaLite with TModelMixin)
- [ ] Eyes sit in the middle of your player's head, whatever is worn on it (try a tall hat or full helm).
- [ ] The head and anything worn on it are hidden; shoulders, arms and cape stay.
- [ ] Fade near eyes: parts of the body that come close to your eyes (raised arms in an emote) fade out.
- [ ] Transparent things near you (water, smoke, ghostly NPCs) show no holes when you look away from where the client camera faces.

**CP3 tabletop**
- [ ] The view button (A on the right hand) switches to the tabletop and back; the setting keeps it.
- [ ] The table stands 0.55 m in front of where you face, at Table height above your real floor.
- [ ] The world shows out to about 0.6 m round the player and fades into the sky at the edge.
- [ ] Right stick left/right turns the table; up/down tilts it (0 to 60 degrees), kept after you let go.
- [ ] Your player walks about on the table with roofs hidden as on desktop; switching back puts you in first person at your head.
- [ ] Table scale and Table height changes from the config panel apply while in VR.

**CP4a pointer** (needs VitaLite with TPickRayMixin and TSceneMixin)
- [ ] Both hands show a beam; the active one is brighter and ends on what it points at, with a dot there.
- [ ] Hovering an NPC, object, item or player with the beam shows the game's top option as the desktop does (top-left text).
- [ ] Trigger with either hand left-clicks along its beam and makes it the active hand; a short tick is felt.
- [ ] Trigger on the ground walks to the tile the beam meets; a yellow cross shows there. On an NPC/object, a red cross.
- [ ] Right grip right-clicks (the menu itself shows in VR from the next checkpoint); left grip held is ctrl.
- [ ] The same works on the tabletop.

**CP4b world menu**
- [ ] Right grip on the world opens the game's menu on a floating panel on the beam, short of what you pointed at.
- [ ] Pointing at its entries highlights them; trigger picks one.
- [ ] Moving the beam off the menu with the grip let go closes it; holding the grip keeps it open while off it.
- [ ] Trigger off the menu closes it.

**CP5a interfaces** (needs VitaLite with OverlayRendererMixin, WorldOverlays and clearMouseCross)
- [ ] B on the right hand shows the interfaces round you, facing where you look; B again hides them.
- [ ] They are cut in three: chat side, middle, inventory side, curved round you (flat panels where the runtime has no curved layers).
- [ ] Pointing at an interface moves the mouse there: hover highlights, trigger clicks, right grip right-clicks (menu shows on the panel), trigger held drags (items, the minimap).
- [ ] Right stick up/down over the interfaces scrolls (chat, bank, lists); on the tabletop it then does not tilt the table.
- [ ] Pointing off the interfaces, through their clear parts, goes back to the world.
- [ ] Names, hitsplats and health bars are not drawn flat on the interfaces (3D ones come next); neither are RuneLite's tile markers and other scene overlays, nor the game's yellow/red cross.
- [ ] Interfaces size, distance, side sizes and sides apart from the config panel apply while in VR.

**CP6 actor overlays**
- [ ] Health bars, hitsplats, overhead chat, prayer/skull icons and NPC head icons hang over actors in 3D, the game's own pictures, facing you, the same size near or far.
- [ ] Up to the 8 nearest actors show them; the hint arrow's target always does.
- [ ] Your own bar, chat, icons and hitsplats hang ahead of and below your eyes in first person.
- [ ] A hint arrow over an NPC or player, and over a tile, shows and blinks as on desktop.

**CP4c pointer label + tile outlines**
- [ ] Pointing at the world shows a label beside the beam's end: the top option as the game colours it ("Attack Goblin (level-2)") and "N more options".
- [ ] It hides while a menu is open, over the interfaces, with nothing under the beam, or with Pointer label off.
- [ ] The tile under the beam is outlined white, your own tile cyan, your walk destination green, each following the ground and hidden behind scenery; each can be turned off in the config.

**CP7 settings + keyboard**
- [ ] X on the left hand (or the menu button) opens VR settings in front of you: View, Body, Interfaces and Comfort tabs; arrows step each value; Done or the button again closes it.
- [ ] A change there shows in the RuneLite config panel at once, and a change in the config panel shows on it.
- [ ] Y on the left hand opens a keyboard; its keys type into the game (chat, bank search, quantities), Shift, Back, Enter, Esc work; Hide or Y closes it.
- [ ] A prompt that wants typing (bank search, enter amount) opens the keyboard by itself, and it goes away again when the prompt does.

**CP8 comfort, wrist, haptics, frame times**
- [ ] Turning with the stick or being carried along closes the view's edges in (Vignette setting); a teleport fades to black and back.
- [ ] Leaning into a wall darkens the view, and inside it is wholly dark (Wall fade setting).
- [ ] Hits taken buzz both hands, hits you deal buzz the right hand, a level up gives two strong pulses, low HP a heartbeat (each in the settings, scaled by Vibration).
- [ ] Turning the left wrist to look at it brings up a plate with HP/prayer/run/spec orbs, the compass and the time; pointing the right hand at an orb and pulling the trigger presses it.
- [ ] The VitaLite console logs fps, frame and slow times and per-part times every 10 s, with the headset's Hz.


**CP9 body**
- [ ] Standing still in first person, look down: your arms reach for the controllers, elbows bending out and down, and the hands turn as you hold them; a weapon or shield turns with its hand.
- [ ] Walking, running or playing an animation hands the arms back to the game; with Hands when animating on, white balls show at the controllers until you stand still again.
- [ ] Held items When acting: the weapon and shield are hidden while idle and show for an attack or skilling animation; Always shows them, Never hides them; Held item size shrinks them about the hand.
- [ ] A helmet and the head stay hidden with Hide own head, and the held weapon is not taken for part of the head when raised above the shoulders.

**CP10 recording**
- [ ] With "Record the session" ticked on the side panel (or Record session in the config), Launch in VR logs "Recording the session to ..." under `.runelite/vitavr/recordings`; without ffmpeg on the PATH (or `-Dvitavr.ffmpeg=<path>`) the side panel says why after Back to desktop, and VR runs unrecorded.
- [ ] Back to desktop offers a save dialog; saving moves the MP4 there, cancelling deletes it.
- [ ] Record One eye + Exact: the left eye cropped to 16:9; One eye + Steadied: a smooth head camera with no roll; Both eyes: side by side.
- [ ] The video shows the pointers, dot and cross, tile outlines, overheads, the wrist plate, the interfaces where they stand, the world menu, the pointer label and the settings/keyboard panels when open.
- [ ] Hide chat leaves the chat column out of the video while the headset still shows it; the console's frame times include "recording".

**Roofs indoors + ceilings**
- [ ] First person, Roofs indoors Keep, game Hide roofs off: walking into a house keeps its upper floor and roof; inside, the ceiling is solid and dimmed (Ceiling light setting), not sky.
- [ ] Pointing up inside ends the beam on the ceiling with no menu; stairs and ladders still work; going upstairs keeps the floors above that one.
- [ ] Game Hide roofs on, Roofs indoors Hide, or the tabletop: roofs go as they do on the desktop.

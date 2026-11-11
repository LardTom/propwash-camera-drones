# Changelog

What changed in **Propwash: Camera Drones**, for players and server owners. The player guide is in
[`docs/guide.md`](docs/guide.md), developer notes per day in [`changelogs/`](changelogs/) (German).

## [1.0.0] - First release: camera drones with GPS, gimbal and flight planner

### Highlights
- **Camera drones that fly themselves:** let go of the sticks and the drone brakes and holds its position and height by GPS, even in wind. A stabilized 3-axis gimbal keeps the horizon level.
- **Flight Planner:** a tablet to click waypoints into the world, plan missions on a map and start them. The drone flies on when you take off the goggles, and you can run the gimbal yourself while it flies.
- **Smart functions and two pilots:** hold as an air tripod, follow me, circle a point of interest, course lock, and a second player who steers the camera with their own radio.

### New
- Three camera drones as ready-to-fly presets in their own creative tab: **Compact** (244 g, about 30 min hover), **Tour** (720 g, about 41 min) and **Cine** (3.8 kg, about 26 min, 360° gimbal with interchangeable lenses).
- 19 parts with real-world values and their own models in Propwash's style: folding frames with a closed shell, nav flight controllers with GPS, compass and barometer, low-KV motors, folding props, smart batteries with charge LEDs, digital camera links and 3-axis gimbals with action-cam data, plus three lenses (24, 50 and 85 mm) for the cine gimbal. Every part and lens has a recipe, and a compact drone sometimes turns up in village chests.
- Flight modes **Normal**, **Cine** (smoothed sticks, slow yaw) and **Sport** (manual, 35° angle limit) on a three-position switch or a key; automatic **ATTI** without GPS fix and with a disturbed compass.
- Obstacle sensors on the Nav Flight Controller Pro: the drone slows down and stops about 1.5 m in front of walls, or climbs over them in missions (Tour and Cine), with a beep that gets faster the closer it gets.
- Flight Planner: pair it with your parked drone, set waypoints by right-clicking blocks, in flight or by recording a flight, and edit up to 8 missions with 50 waypoints and 4 points of interest each: height, speed, wait time, gimbal tilt, look direction, photo and recording actions, smooth or sharp curves, and at the end hover, return, land or loop. Select several waypoints and change them together, double-click a value to type it. Templates for a circle, a reveal and a line, and a preview of length, time and battery use.
- Waypoint actions: a waypoint can press an accessory action like the keys in flight (once, from that point on or timed). With Propwash's gripper under a drone, the mission picks up an item when it flies low over it, and *Release gripper* at a later waypoint drops it.
- Missions live on the drone: they survive picking it up, placing it again and server restarts. A drone on the ground takes off by itself (also the Cine drone standing on its gimbal); leave the FPV view and the server flies on, put the goggles back on and you take over. The planner also flies FPV drones on the GPS of their flight controller while nobody wears the goggles.
- Landing pad: a camera drone flying without a pilot centres itself above the pad and touches down in its middle.
- Smart functions: hold, follow me (behind, parallel or watching), point of interest from the middle of the picture with stick control of radius and speed, course lock.
- Gimbal: tilt with a dial or Page Up / Page Down, pan on the cine gimbal, follow or lock, recenter (twice: straight down), zoom. Recording and photos are stabilized, and the Flashback camera path follows the gimbal. Moving the gimbal is silent; only recentering and an operator taking over play a short servo sound.
- Two pilots: invite a player within 16 blocks with the **Crew** button in the service menu or `/camdrones crew invite`. The operator pans, tilts and zooms with their own radio, records, takes photos and hands the camera back by holding action 4. The gimbal keeps its direction in the world when the pilot yaws (Compact and Tour up to their ±25° stop with GIMBAL LIMIT on the operator's OSD, the Cine gimbal all the way round), and the crew waits two minutes for an operator who lost the connection.
- Camera on your own: when you start a mission alone, the drone flies itself and the *Live view* in the Flight Planner lets you run the gimbal like a camera operator (action 2 recenters, action 1 pauses or resumes, hold it to abort, Esc or hold action 4 to go back). A second player as camera operator still has priority.
- OSD elements in the editor group *Add-ons* (flight mode, gimbal tilt, mission progress, nearest obstacle, flight time left) and warnings: OBSTACLE, ALT LIMIT, DIST LIMIT, NO GPS ATTI, COMPASS ERROR, PAUSED, HIGH WIND, OPERATOR, GIMBAL LIMIT.
- Command `/camdrones`: missions (list, start, pause, resume, stop), hold, follow me and the crew.
- 12 switches and 3 axes on the *Add-ons* page of Propwash's *Modes* tab, assigned like built-in functions; default keys only on keys Propwash and Minecraft leave free (keypad / * − and the decimal key, Insert, Delete, Page Up/Down).
- Four Flight School chapters, an own advancements tab (*Two-Person Crew*, *Camera Rolling*, *Flight Plan Complete*) and eight new sounds for the planner, missions, the gimbal and warnings.

### For server owners
- World rules in their own section *Camera Drones*: allow autonomous flights (on), follow other players (off), second pilot for the gimbal (on), altitude limit (120 m), distance limit (off) and an autonomous flight time limit (60 min). They don't affect FPV races.
- The server decides missions, starts, follow targets and the crew. One flight without pilot per player at a time; it ends when the owner goes offline, the drone leaves radio range, the battery runs low or the time limit is reached.
- Following another player needs the world rule and their consent; they see who follows them and can stop it any time with `/camdrones follow stop`.
- A player without the add-on gets Propwash's join message with the add-on's name and version instead of an error, on Fabric and NeoForge.

### Compatibility
- Minecraft 26.3 · Fabric (Loader 0.19.5+, Fabric API) or NeoForge 26.3 · Java 25 · install it on the server and on every client that plays there.
- Needs Propwash FPV 0.6.0 or newer (add-on API 1.8). With an older Propwash the game stops at start with a clear message.
- Works with Just More Parts and Mob Defense. Camera drone parts have their own short numbers in Propwash's share codes; missions are not part of share codes.

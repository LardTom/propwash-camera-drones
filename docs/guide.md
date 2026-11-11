# Camera Drones — player guide

**Propwash: Camera Drones** is an add-on for [Propwash FPV](https://propwashfpv.com). It adds a second
kind of drone: camera drones that fly themselves. They hold their position by GPS, brake when you let go of the sticks,
carry a stabilized 3-axis gimbal camera and can be programmed with a tablet, the **Flight Planner**: waypoints, position
hold, follow me, point of interest and course lock. A second player can steer the camera with their own radio.

German version: [`guide.de.md`](guide.de.md).

- [What is different from FPV](#what-is-different-from-fpv)
- [Getting started](#getting-started)
- [Flight modes](#flight-modes)
- [Flight Planner](#flight-planner)
- [Smart functions](#smart-functions)
- [Gimbal and two pilots](#gimbal-and-two-pilots)
- [Controls](#controls)
- [OSD and warnings](#osd-and-warnings)
- [Commands](#commands)
- [World rules](#world-rules)
- [On servers](#on-servers)
- [FAQ](#faq)
- [Compatibility](#compatibility)

---

## What is different from FPV

| | FPV quad (Propwash) | Camera drone (this add-on) |
|---|---|---|
| Sticks | rotation rates or angles (Acro, Angle, Horizon) | speed: let go and the drone brakes and holds its position |
| Position | GPS only for rescue and the OSD | GPS, barometer and compass on the nav flight controller; without GPS fix it flies ATTI and drifts with the wind |
| Camera | fixed with uptilt, shakes with the frame | two cameras: you fly with a fixed FPV camera on the frame; the 3-axis gimbal camera (level horizon, tilt with a dial or keys) films |
| Video | 20–35 ms latency | 100–120 ms latency, clean HD picture, long range |
| Flight time | 3–6 min | about 26–41 min |
| Speed | 30–45 m/s, very agile | 3 m/s (Cine) to 26 m/s (Sport), smooth |
| Safety | none | obstacle sensors, altitude limit, return home when the battery runs low |
| Programmable | no | waypoints, hold, follow me, point of interest, course lock |

The physics is Propwash's flight model (wind, gusts, prop wash, battery curve). The add-on brings parts with real-world
values and the flight assist that steers Propwash's altitude and position hold.

---

## Getting started

### Install

Put the add-on next to Propwash FPV 0.6.0 or newer into the `mods` folder, on Fabric or NeoForge for Minecraft 26.3. A
server needs it too, and so does every player who joins that server.

### Get a camera drone

- **Creative:** the creative tab *Propwash: Camera Drones* holds three ready-to-fly drones (*Compact · 2S*,
  *Tour · 4S*, *Cine · 6S*), all parts, the three lenses, the Flight Planner and the landing pad.
- **Survival:** every part and lens has a recipe made from Propwash's intermediate parts (circuit board, HD chip, carbon
  plate, copper coil, LiPo cell) and vanilla items; the recipe book shows them. Build the drone at Propwash's
  workbench like any other drone. A compact camera drone sometimes turns up in village chests.
- **Flight Planner and landing pad** are crafted at a crafting table: the planner from six iron ingots, a glass pane,
  a map and a circuit board (its recipe unlocks with your first Propwash part), the landing pad from two gray carpets,
  white dye and three smooth stone slabs (unlocks once you hold a Flight Planner).

| Class | Take-off weight | Battery | Hover time | Cine / Normal / Sport | Top speed | Obstacle sensors |
|---|---|---|---|---|---|---|
| **Compact** | 244 g | 2S Li-Ion 3850 mAh | about 30 min | 3 / 10 / 16 m/s | about 66 km/h | downwards only |
| **Tour** | 720 g | 4S LiHV 4240 mAh | about 41 min | 3 / 12 / 20 m/s | about 81 km/h | all around |
| **Cine** | 3.8 kg | 6S LiHV 8560 mAh | about 26 min | 4 / 15 / 26 m/s | about 105 km/h | all around, 360° gimbal with lenses |

Figures from Propwash's workbench analysis of the presets; the top speed is what the airframe could do, in Sport the
flight assist stops at the Sport speed of the class. What makes a drone a camera drone is the nav flight
controller (*Nav Flight Controller Compact* or *Pro*): it switches the flight assist on. Camera frames have two mounts:
the gimbal goes on the front mount, the top mount takes an action cam. Each of the three gimbals fits all three camera
frames (Compact, Tour and Cine), the cine gimbal too: the Compact can't lift its 420 g, though, and the Tour hovers
about a third shorter with it. On other frames the workbench takes the gimbals only while Propwash's world rule
*Parts must fit* is off. Batteries, motors and props of Propwash and other add-ons fit by Propwash's usual rules.

The workbench button *Camera* (*Check camera drone*; in some languages under *⋯* when the header is full) shows
whether the build is a complete camera drone: Nav-FC, gimbal, battery and camera link. Missing parts are named to
match the camera frame; another battery or video link is fine, the check only recommends the camera part.

### First flight

1. Place the drone and fly it like any Propwash drone (goggles on, arm).
2. Give throttle to take off. Push the sticks: the drone flies at the speed the stick asks for.
3. **Let go of the sticks:** the drone brakes, stays where it stops without pulling back, and holds its position and
   height, even in wind. At the start and on every mode change the OSD shows for 5 s what the mode means.
4. Tilt the camera with the gimbal dial on your radio or with Page Up / Page Down. You fly with the fixed FPV camera;
   **Delete** shows the gimbal camera and back. Recordings and photos always come from the gimbal camera.

### Pair the Flight Planner

Hold the Flight Planner, **sneak and right-click your own parked drone**. The planner remembers the drone (its serial
number stays with the drone when you pick it up). You can only pair your own drone, and only while nobody flies it.

### Your first mission

1. With the paired planner in your hand, **right-click blocks** around a building: each click sets a waypoint above the
   block (10 m by default, *Click height* in the editor). Numbered markers and the flight line appear in the world while
   you hold the planner.
2. **Right-click into the air** to open the editor. Check the waypoints, set *End* to *land* or *to start*.
3. Press **Send**: the mission now lives on the drone.
4. Press **▶ Start**. A drone on the ground takes off, climbs to the height of the first waypoint and flies the
   mission without you wearing the goggles.

### Landing pad

The landing pad is a flat block with an *H*. Put it where the drone should land, for example under the start point.
When a camera drone flying without a pilot lands (mission end *to start* or *land*, or *■ Stop*) and a
landing pad lies below it, it centres itself above the pad and touches down in its middle. Break it with any tool; it
drops itself.

### Flight school

Four chapters of Propwash's flight school show the add-on in short scenes: *Flying a camera drone* (position hold,
Cine/Normal/Sport, gimbal, ATTI, obstacle stop), *Your first mission* (pair, waypoints, send and start, landing pad),
*Follow me and point of interest* and *Recording: Flashback or direct* (recording mode, recording in the background,
live view). Hold the flight school key (M by default) over a camera part, the Flight Planner
or the landing pad in your inventory, or open them from the flight school's list.

### Advancements

The add-on has its own advancements tab, *Camera Drones*. It opens as soon as you carry a gimbal or the Flight Planner
and holds three advancements:

| Advancement | How to get it |
|---|---|
| *Two-Person Crew* | Fly a camera drone as a pair: one flies, the other runs the camera. Both get it once the armed drone is at least a block above the ground. |
| *Camera Rolling* | Record a video with a camera drone: start and stop a recording as pilot, as camera operator or in the live view from the Flight Planner. Direct recording and Flashback recording both count. |
| *Flight Plan Complete* | Finish a mission from the Flight Planner (not a loop). The owner of the drone gets it, and the pilot if someone flew it. |

---

## Flight modes

| Mode | How | What it does |
|---|---|---|
| **N – Normal** | default; Propwash's flight mode *Angle* | sticks = speed up to the normal limit, throttle = climb rate, yaw 90°/s; let go = brake and hold |
| **C – Cine** | Propwash's flight mode *Horizon* | like N with smoothed sticks, cine speed and slow yaw (30°/s) for calm shots |
| **S – Sport** | Propwash's flight mode *Acro* | manual flight with a 35° angle limit up to the Sport speed of the class (16, 20 or 26 m/s), no obstacle braking; the fastest mode |
| **ATTI** | automatic without GPS fix (caves, under blocks) or with a compass error | holds only the height and drifts with the wind, no obstacle braking; *NO GPS ATTI* or *COMPASS ERROR* in the OSD, with obstacle sensors also *ATTI - OBSTACLE SENSING OFF* and a tone |

Switch modes like Propwash's flight modes: the flight mode key (default R) goes N → C → S, and the flight mode switch
on your radio selects them on the same channel as before: Angle = N, Horizon = C, Acro = S (with Propwash's standard
radio setup channel 6 high = N, middle = C, low = S). Propwash's popup when switching names the same modes as the OSD:
N, C or S (ATTI instead of N without GPS fix). At the start the drone flies the mode that Propwash's flight mode stands
for (with the keyboard Angle, so N). In addition you can assign *Flight mode N*, *C* and *S* to the positions of any
three-position switch, and *Next flight mode* (keypad /) also goes N → C → S. The last change counts, whether from the
radio or a key. In Propwash's configurator (K), tab *Modes*, page *Add-ons*, the description of these functions names
the flight mode key and the switch positions.

- **Coming from FPV:** the sticks set the speed, not the tilt or the rotation rate. N: GPS holds position and horizon;
  full deflection reaches the top speed of the mode in about 2 s, behind a 5 % deadband without expo. C: slow and
  smooth for footage. S: fast, without obstacle braking; full deflection tilts the drone up to 35° and reaches 90 % of
  the Sport speed in about 3.5 s (Compact), 4.5 s (Tour) or 6 s (Cine). ATTI: no GPS, the drone drifts with the wind.
  At the start and
  on every change the OSD shows the mode for 5 s with a short explanation (element *Mode hint*, e.g.
  *N: GPS HOLDS POS + HORIZON*). On the keyboard Propwash's *Keyboard deflection* (tab *Receiver*, default 60 %) sets
  the speed: set it to 100 % for full speed.
- **Hold and release:** the drone only holds its position once the sticks are centred. Released, it brakes and stays
  where it stops without pulling back, also in strong wind and after S or ATTI: the flight assist keeps the wind it has
  learned and learns it anew as soon as the drone no longer brakes. Out of a hold (*Pause and hover*) a small
  deflection already flies on like in N.

- **Obstacle sensors** (Nav Flight Controller Pro, six directions, 15 m): in N, C, missions and follow me the drone slows
  down towards an obstacle and stops about 1.5 m in front of it (*OBSTACLE*, the beep gets faster the closer you get).
  Missions can climb over an obstacle instead (Tour and Cine). The compact drone only has a downward sensor.
  With sensors it only flies as fast as it can stop within their range (the Cine about 13 m/s in N). Close to the ground
  it tilts only so far that the sensors still see a wall before they see the ground, so it is slower there (about
  10 to 12 m/s at 3 to 4 m). While braking it holds its height, and the sensors also look a little above and below the
  flight height, so it also stops in front of a wall whose top is just above it. A wall it has seen stays known until
  you steer aside, turn away, fly back or climb with the throttle, as long as GPS is there; without GPS (ATTI) obstacle
  detection is off. The OSD then shows *ATTI - OBSTACLE SENSING OFF* and a tone sounds once. If the GPS drops while
  the drone has a wall in view and you still push towards it, the drone stays where it is until the stick has been back
  in the centre once; after that it flies ATTI without obstacle braking.
- **Angle limit:** the camera drones tilt at most 40°. If you set a smaller *Angle limit* in Propwash's PID tuning, the
  drone tilts and brakes only as far as that limit allows: it slows down earlier towards an obstacle and is slower with
  sensors (the Cine at 30° about 11 m/s in N), but still stops about 1.5 m in front of it. Sport tilts at most 35° or the
  smaller limit, ATTI at most 25°. In Sport the flight assist sets the throttle itself and holds the height up to full
  throttle; only if even full throttle no longer suffices at high speed does it lean less until it holds its height
  again.
- **Altitude limit:** by default 120 m above the take-off point (world rule). Above it the drone does not climb,
  *ALT LIMIT* in the OSD. A distance limit can be set as well (*DIST LIMIT*).
- **Compass error:** now and then Propwash rolls a disturbed compass for a flight. The drone then flies ATTI and shows
  *COMPASS ERROR* until the compass reads correctly again. Land and start a new flight if it stays.
- **Return home:** Propwash's GPS rescue switch brings the drone back, as with every Propwash drone. In a piloted flight
  the flight assist starts the return home once by itself at 20 % battery; if you cancel it, it only starts it again
  with a fresh battery. Below 10 % the drone lands where it is (mode *LAND* in the OSD), throttle up no longer helps,
  you can still steer it sideways. When the battery runs low during a flight without pilot, the drone returns
  home or lands by itself.
- **Crash:** an impact that disarms the drone ends course lock, follow me, point of interest, hold and a running mission;
  after arming again you fly in the selected mode.

---

## Flight Planner

The Flight Planner is a tablet you hold in your hand. Your radio stays Propwash's radio.

| In the world | Action |
|---|---|
| Sneak + right-click your parked drone | pair |
| Right-click a block | waypoint above the block |
| Sneak + right-click a marker | delete that waypoint |
| Right-click into the air | open the editor |

**Waypoints in flight:** *Add waypoint here* (Insert) stores position, height, heading and the gimbal tilt while you
fly; the paired planner must be in your inventory. **Record:** switch *Record* on in the editor and fly; a waypoint is
added every 10 m and at every change of direction.

### Editor

- **Map** (left): top-down map of the loaded area with waypoints, points of interest, start point and the live position
  of the drone. Click: add a waypoint. Right-click: point of interest. Drag a point to move it, drag empty space to pan,
  mouse wheel to zoom, *Fit mission* to see everything. *Hide map* gives the list more room in small windows.
- **List and details** (right): per waypoint *Height* (above the start point), *Speed*, *Wait*, *Tilt* of the gimbal,
  *Look* (along the course, fixed direction, at a point of interest, or free for the camera operator), *Direction* for
  a fixed look, *Action* (none, photo, start or stop recording) and the waypoint actions *Light*, *Accessory* and
  *Repeat* (see [Waypoint actions](#waypoint-actions)). Points of interest have a height and a radius.
- **Changing values:** click the left half of a value to lower it, the right half to raise it, or use the mouse wheel;
  Shift takes the big step. Double-click the value itself (not the arrows) to type a number: Enter applies, Esc cancels,
  values outside the limits are set to the nearest limit.
- **Several waypoints at once:** Ctrl-click (Cmd on a Mac) adds or removes a waypoint in the list or on the map,
  Shift-click selects the range from the last click, Ctrl+A (Cmd+A) selects all. A value then applies to all selected
  waypoints and sets them all to the same value: typing sets the number you type (for example all to 12 m), a click
  sets the value of the waypoint clicked last plus the step. With Alt (Option on a Mac) held, each waypoint changes by
  the step instead, so their differences stay. Values that differ show “–”. Delete removes all selected waypoints; the
  arrows for moving up and down need a single waypoint.
- **Mission** (top): name, *Base speed*, *Curves* (sharp: stop at every waypoint; smooth: one continuous path with wide
  arcs that start and end at 45 % of the shorter leg; the drone keeps its cruise speed and only slows down as far as its
  class needs for the turn, climbs and descents blend smoothly, actions trigger at the point of the arc closest to the
  waypoint; it still stops at waypoints with a wait time, at the last one and before hairpin turns over 150°), *End*
  (hover, fly back to the start, land, start over), *Obstacle* (stop or climb over), *Click height*, *Record*. In smooth
  mode the map and the markers in the world show the arcs.
- **On exit** (with the mission settings): what the paired drone does when you leave the FPV view – *hover* (default),
  *home* or *land*. The setting is stored on the drone and applies right away.
- **View** (next to *On exit*): what you see in FPV with the paired drone – *OSD* (Propwash's OSD, default) or
  *Camera* (the camera view, see [OSD and warnings](#osd-and-warnings)). Stored on the drone, applies right away.
- **Templates:** *Circle point* (12 waypoints in a 20 m circle looking at the centre), *Reveal* (backwards and rising
  away from the subject, camera on the subject), *Line* (a point every 10 m between the last two waypoints).
- **Preview:** length, time and battery use of the mission; on the right the number of waypoint actions and how many
  would be skipped, or a note when the paired drone has no GPS right now.
- **Buttons:** *Send* (mission to the drone), *▶ Start*, *⏸ Pause*, *■ Stop*, *Auto flight* and *Follow me* (see
  [Smart functions](#smart-functions)), *Save*. Up to 8 missions per planner,
  50 waypoints and 4 points of interest per mission.
- **Live view** (in the header): shows the paired drone's camera picture like when spectating. The drone has to be
  flying (with or without a pilot) and within video range, and you need to wear goggles; otherwise you get a message.
  If the drone flies without a pilot, you steer the gimbal yourself in the live view (see [Camera on your own](#camera-on-your-own)).

The editor works in small windows and at large GUI scales; at 320×240 the map folds away and the details of a waypoint
split into the pages *Flight* and *Actions* (switch *Actions ›* or *‹ Flight* below the fields).

**Turning the preview markers off:** in the settings (K), tab *Camera Drones*, *Preview markers* switches the numbered
markers and the flight line in the world off; the editor still shows the points. The setting is saved in
`config/camdrones-client.json`.

**Service menu:** the service menu of a camera drone (sneak + right-click with an empty hand) has the buttons *Mission*
and *Crew* in its header. *Mission* shows the mission stored on the drone and its state, sets *On exit* and *View* and has
*▶ Start*, *⏸ Pause* and *■ Stop*.

### What happens during a mission

- **Drone on the ground:** *Start* takes off, climbs to the height of the first waypoint and flies the mission.
- **You are flying the drone:** the mission runs in your flight assist. Moving the sticks pauses it; *Start or resume
  mission* (keypad *) continues.
- **You take off the goggles:** the server flies on. The drone keeps its mission even when you walk away, as long as
  you stay online and within radio range.
- **You put the goggles back on:** you take over and the mission pauses.
- The active mission is stored on the drone: it survives picking the drone up, placing it again and a server restart.
- The owner gets a chat message when a mission starts, ends or cannot start.

### Waypoint actions

Besides photo and recording, every waypoint can trigger two actions. They run when the drone reaches the point (within
1.5 m, or when it moves on in a smooth curve), once per pass, and again in every lap of a loop. The accessory action can
also run timed, every X ticks.

| Field | Values | Effect |
|---|---|---|
| *Light* | unchanged, on, off, Static, Blink, Arm status, Low battery, Rainbow | switches the drone's LEDs or picks the mode like the “LED strip” tab; needs an LED strip |
| *Accessory* | none, Action 1 to 4, Stop repeat, Release gripper | presses the accessory action like the *Accessory action 1–4* keys in flight, for example an add-on's dispenser; *Release gripper* drops the cargo of Propwash's gripper |
| *Repeat* | only here, from here, every … s | *only here*: once at this waypoint; *from here*: at this and at every following waypoint; *every … s* (timed): from this waypoint on every X ticks, shown here in seconds |
| *Every* | 1 to 1200 ticks, for example “20 ticks” | only when timed: the time between two triggers in ticks (20 ticks = 1 s, the seconds show at *Repeat*); click ±1 tick, with Shift ±20 ticks |

- **Repeat from waypoint X:** a repeat lasts until a later point has another accessory action or *Stop repeat*.
  Repeated points show “Action 1 from 3” in the editor and “Accessory 1 again” on the markers in the world.
- **Timed (every X ticks):** the action triggers when the drone reaches the waypoint and then every X ticks, also in
  flight and while it waits at a point – until a waypoint with another accessory setting, *Stop repeat* or the end of the
  mission. Set on the first waypoint, the timer covers the whole mission. A later point with exactly the same setting
  keeps the timer running without an extra trigger. Nothing triggers while the mission is paused. Timed points show
  “Action 1 timed” in the editor, the marker at the starting point “Accessory 1 every 20 ticks (1 s)”. Preview and battery time
  do not change.
- **The timer ends at the last waypoint:** with every *End* except *loop* the mission ends there. The timer keeps
  running while the drone waits at that point and stops when the wait is over – counted from the moment the drone has
  arrived at the point, just like the drone itself counts it. After a pause there the drone waits the full time again,
  and the timer runs along. It stops at the latest when the drone is more than 3 m away from the point. Nothing triggers
  on the flight back to the start, while hovering or while landing afterwards. In a loop the timer runs on from lap to
  lap.
- **At most as often as the accessory allows:** the accessory itself sets the minimum time between two triggers, for
  example the cooldown of the Mob Defense dispenser (a world rule, 4 ticks by default). If the timer is shorter, the
  accessory still never fires faster: at 2 ticks the Mob Defense dispenser fires every 4 ticks. The editor says so in
  the tooltip of *Repeat* and *Every*.
- **Without the accessory** the action is skipped: the editor marks the field and the waypoint with “!” and names the
  reason in the tooltip; in flight you get one chat message per point. If the accessory names the action (for example
  “Fire”), the name shows in the tooltip and in the action bar.
- **Permissions and rules:** the action is triggered on behalf of the drone's owner. Propwash's permissions apply (for
  example spawn protection and adventure mode when dispensing) as well as the rules of the add-on the accessory belongs
  to (for example cooldown and ammunition of the Mob Defense dispenser). If the owner is offline, nothing is triggered.
- **Gripper:** with Propwash's gripper under the drone, the gripper is always ready during a mission as long as it is
  empty – as simple as flying with the goggles: when the drone flies low enough over an item (its feet at most about
  0.9 blocks above it), it picks the item up, and the weight flies along. Plan a waypoint just above the item for this,
  about 0.5 m high with a short wait. *Release gripper* at a later waypoint drops the cargo there; the drone does not
  pick up again right away what it has just dropped. Like any other accessory setting, *Release gripper* replaces a
  repeat from an earlier point. After the mission has ended, on the way home and in flights without a mission (hold,
  follow me, automatic flights) the gripper does not grab by itself. Without a gripper on the drone the editor marks the
  waypoint with “!” and says “no gripper on the drone” in the tooltip.
- **Not possible yet:** *Light* is skipped until Propwash lets add-ons switch the LEDs. The editor says so at the
  waypoint.
- Camera drones also run the actions in piloted flight as soon as the pilot reaches the waypoint.

### Normal FPV drones

The Flight Planner also flies freestyle, race, long-range and other FPV drones, including frames from Just More Parts.
Pair them like a camera drone.

- **Equipment:** every Propwash drone has the GPS of its flight controller, the same one Propwash's GPS rescue uses. No
  extra part is needed. The condition is a GPS fix: at least 5 satellites, so a clear view of the sky, not under blocks,
  in caves or in the Nether. Without a fix the editor header shows “no GPS” in orange, the status line and the preview
  name the satellites, and *Start* reports “no GPS fix”.
- **Only without a pilot:** an FPV drone flies missions as a flight without pilot through Propwash's autonomy, exactly
  like a camera drone without pilot. Take off the goggles, then *Start*. While you fly it yourself it stays a normal FPV
  drone and *Start* reports “only without a pilot”. Putting the goggles on during a mission takes over and pauses it.
- **No obstacle sensing:** without a nav FC the mission flies the planned line; *Obstacle* has no effect.
- **On exit:** without a running mission, taking off the goggles keeps Propwash's behaviour (landing or GPS rescue);
  the *On exit* setting only applies to camera drones.
- The editor header shows “FPV”; its tooltip names the frame role, the GPS state and these rules.

---

## Smart functions

All smart functions also run without a pilot once you take off the goggles (world rule *Allow autonomous flights*).

- **Hold:** *Pause and hover* (keypad −) or simply leaving the FPV view while the drone is in the air: the drone hovers
  on the spot as an air tripod and the camera stays free. With *On exit* set to *home* or *land* in the planner it flies
  home or lands instead; for *home* it first climbs 1 m and then takes Propwash's return flight to the home point.
  `/camdrones hold` does the same without goggles.
- **Follow me:** *Follow me* in the flight planner offers three styles: *Behind* (about 8 m behind you and 5 m up),
  *Parallel* (8 m beside you) and *Watch* (stays where it is and only turns). If nobody flies the paired drone, it takes
  off and follows you without a pilot. While you fly it yourself, the style applies to the *Follow me* switch: switch it
  on and take off the goggles. The planner remembers the style for each drone until you quit the game; without a choice
  the switch follows behind you. With the command: `/camdrones follow me [drone] [behind|parallel|watch]`. The camera
  always aims at you. Following your own pets and mounts is always allowed; other players only with the world rule and
  their consent (see [On servers](#on-servers)).
- **Point of interest:** *Point of interest in view* sets the point in the middle of the picture (up to 64 m away) and
  circles it at the current distance. Roll stick = speed and direction, pitch stick = radius (2–200 m), throttle =
  height. If the image center hits nothing within 64 m (sky, target too far), the drone does not circle: the action bar
  says "no target at the image center within 64 m" and the OSD shows *NO POI* for two seconds. In missions, points of
  interest come from the map; waypoints with *Look: at P1* turn nose and camera to the point.
- **Course lock:** *Course lock* stores the current heading. Forward always flies that direction; yaw only turns the
  nose and the camera. Ideal for side-on tracking shots.
- **Auto flights:** *Auto flight* in the flight planner (paired drone, nobody flying it) offers four camera moves
  around the subject 10 m in front of the drone's nose: *Pull back* (15 s backwards and climbing, the camera stays on
  the subject), *Rise* (12 s straight up while the camera tilts down), *Circle* (one circle in 20 s, nose towards the
  centre) and *Spiral* (20 s circling, 10 m wider and climbing). They start from the ground or from a hover and run
  without a pilot; afterwards the drone holds its position. *■ Stop* lands it. Started during a mission, an auto flight
  or *Follow me* pauses the mission: the OSD shows *AUTO* or *FOLLOW* and *PAUSED*, and *▶ Start* continues the mission
  at the waypoint where it stopped.

---

## Gimbal and two pilots

### Gimbal

- **Two cameras:** as pilot you fly with a fixed FPV camera on the frame, like on an FPV drone: it points along the frame
  at the *Camera angle* from the drone's service menu, with Propwash's own field of view, and shows the props where they
  sit on the frame. The gimbal camera films and shows no props: the operator, *Live view*, recordings, photos and the
  Flashback camera path see the gimbal. Without an operator, *Pilot view FPV/gimbal* (Delete) switches your picture
  between the FPV camera and the gimbal camera; every flight starts with the FPV camera. While an operator is bound you
  always see the FPV camera, and the operator's camera moves never move your picture.
- **Tilt** −90° (straight down) to +30°, cine gimbal +60°: gimbal dial on the radio or Page Up / Page Down.
- **Smooth in every frame:** tilt and pan follow missions, points of interest and Follow me smoothly in every frame, in
  *Live view*, recordings and photos alike. The tilt an operator (or the pilot) sets reaches spectators and *Live view*
  about 0.15 s later and glides there instead of jumping.
- **Pan** (cine gimbal only, 360°): its own axis on the radio.
- **Follow / lock** (flying alone): in *follow* the camera turns when the drone yaws, in *lock* it keeps its direction.
  With an operator the camera always keeps its direction (see *Two pilots*). The gimbal
  compensates pitch and roll in flight in every frame, even when the drone leans forward and sideways at once: the
  picture doesn't pan with the lean and doesn't drift back after braking.
- **FPV:** with the *Gimbal FPV* switch on, the picture rolls with the frame like an FPV camera and turns with the
  drone; only the tilt stays stabilized. The switch wins over follow / lock.
- **Recenter:** keypad decimal key; pressed twice the camera points straight down.
- **Silent:** moving the gimbal makes no sound, like a real gimbal. Only recentering and an operator taking over the
  camera play a short servo sound at the drone (*Gimbal servo* in the settings, see *OSD and warnings*).
- **Zoom:** compact gimbal 24–48 mm, tour gimbal 24–70 mm (wide and tele camera), cine gimbal with the inserted lens up
  to twice its focal length. Put a lens (24, 50 or 85 mm) into the cine gimbal through the accessory inventory in the
  drone's service menu.
- **Recording without an operator:** as soon as a recording starts – with Propwash's *Recording* key or switch or at a
  waypoint – your picture switches to the gimbal camera until you stop, then back to the camera you chose. For a photo
  at a waypoint it switches for a moment. So recordings and photos always come from the gimbal: Propwash records the
  picture on your screen and does not draw the world twice. During a recording, *Pilot view FPV/gimbal* does not
  switch away. With *Record when armed* on in the *Action cam* tab you therefore fly every armed flight with the gimbal
  camera.
- **Recording with an operator:** the operator records (actions 3 and 4) and the waypoint actions record on the
  operator's side, both with the gimbal picture. Your own picture stays the FPV camera; what you record yourself is your
  FPV picture.
- **Stabilized recording:** recordings and photos show the gimbal's view with a level horizon, not the shaking frame.
  The Flashback camera path follows the gimbal's orientation and zoom, even while you see the FPV camera.
- **Where recordings go:** with *Direct recording* (see *Recording mode* below), videos and photos – by hand, from the
  operator or from waypoint actions – are saved by Propwash's action cam in the `propwash/recordings` folder of your game
  directory, just like Propwash's own action-cam recordings; the chat reports every file with a link to open it. Direct
  waypoint actions (photo, start recording, stop recording) need a camera picture: they record while you fly the drone,
  operate its camera or follow a mission without pilot in *Live view*. With *Flashback recording* they go into Flashback
  instead, also in the background.
- In missions the tilt comes from the waypoints; with point of interest and follow me the gimbal aims at the target.
  Everyone watching sees the same camera angle.

### Recording mode

K menu, tab *Camera drones*, section *Recording*: **Recording mode** decides how the gimbal camera records. It applies
to videos, photos and waypoint recordings of camera drones.

| Mode | What it records | When |
|---|---|---|
| **Flashback recording** | the gimbal's camera path (position, orientation, zoom) as a scene in the running Flashback recording; a photo becomes a marker and a scene with one camera frame | always, also in the background without *Live view*; no FPS cost, the picture is not drawn twice |
| **Direct recording** | the gimbal picture as a video or photo into `propwash/recordings` | only in *Live view*: while you fly the drone, operate its camera or watch it from the flight planner |

- **Default:** *Flashback recording* when the Flashback mod is installed (Fabric), otherwise *Direct recording*. Without
  Flashback the Flashback value is greyed out with the hint *Flashback recording needs the Flashback mod (Fabric)*.
  *Reset* follows Flashback again; the choice is stored as `recording_mode` in `config/camdrones-client.json`.
- **Flashback recording:** if Flashback is not recording yet, the add-on starts it and ends it again afterwards (save
  dialog of Flashback). In Flashback every recording is a scene *Camera drone · Recording N* (with the drone's name, if
  it has one) with the tracks *Gimbal camera* and *Gimbal field of view* and a marker at the start; render it with
  Flashback's export. Missions of your own drones – a flight planner paired with the drone is in your inventory – keep
  recording while you do something else. When the drone flies out of your view distance, the add-on asks the server to
  send the chunks and entities around it (world rule *Chunks around camera drones*, default 4), so the replay shows its
  surroundings; at the end of the mission it releases them.
- **Direct recording without Live view:** a mission with camera actions shows *Direct recording needs the live view –
  Flashback mode records in the background* and records nothing.
- Propwash's own *Recording* key and switch always record directly what you see as pilot.

### Two pilots

1. The pilot invites a player within 16 blocks: **Crew** button in the drone's service menu, or
   `/camdrones crew invite <player>`.
2. The invited player gets a request in chat and clicks *[Accept]* (or `/camdrones crew accept`).
3. The **operator** now sees the camera picture and steers the gimbal with their own radio: right stick pans and tilts,
   left stick up and down zooms. Actions: 2 recenter, 3 recording, 4 photo; action 1 has no function for the operator.
   **Hold action 4** to hand the camera back (or `/camdrones crew leave`).
   - **The camera keeps its direction in the world** when the pilot turns the drone: if it looks north, it stays north
     however the drone yaws. Only the operator decides where it looks: the pan stick turns that direction, action 2
     points it to the drone's front again. There is no *follow* for the operator; the gimbal starts with the view it
     showed when the operator took over.
   - **Compact and tour gimbal** pan only ±25° against the frame. They hold the direction up to that stop; if the
     pilot turns further, the camera stays at the stop and turns with the drone, and the operator's OSD shows
     *GIMBAL LIMIT*. Within the ±25° the operator can pan freely, from the stop back at once. The **cine gimbal** pans
     all around (360°) and keeps any direction.
4. While an operator is bound, the pilot has no gimbal control and flies with the fixed FPV camera; the operator's
   camera moves never move the pilot's picture. The pilot can take the camera back with *Take back* in the crew window
   (or `/camdrones crew release`).
5. If the operator disconnects (connection lost, left the game), the crew stays for **2 minutes**: the pilot reads
   *Operator … disconnected – the crew waits 2:00 for them to return* in the chat and the action bar counts down. If the
   operator returns in time, they control the camera again automatically. If they come back out of video range, in
   another dimension or flying another drone, both read a note in the chat and the action bar keeps counting down; as
   soon as they can take over, the crew is restored, otherwise it ends after the 2 minutes. A server restart ends every
   crew.

It also works during a mission without pilot: the owner invites with `/camdrones crew invite <player>` while holding
the paired Flight Planner or standing next to the drone, and the operator films while the drone flies the mission. The
crew window also has *Live view*, which shows the drone's camera picture while you are within video range.

### Camera on your own

If you start a mission on your own, the drone flies itself – it takes over the pilot's role, and you can run the camera
like a camera operator:

1. Open the **Live view** in the Flight Planner (or in the crew window of the service menu) while the drone flies
   without a pilot (mission, hold, automatic flight, follow me). You see the gimbal camera's picture, the OSD shows
   *OPERATOR*.
2. As long as you do not move the sticks, the gimbal keeps following the flight plan (tilt and look direction of the
   waypoints, point of interest). From your first move you are in control, starting from the picture you see: right
   stick pans and tilts, left stick up and down zooms, action 2 points the camera to the drone's front. As with a camera
   operator, the camera keeps its direction in the world; Compact and Tour pan up to ±25° and show *GIMBAL LIMIT* at
   the stop. Action 3 records, action 4 takes a photo.
3. **Action 1** pauses the mission and resumes it with the next press; **holding action 1** aborts it like *■ Stop*, and
   the drone lands.
4. **Esc** or **holding action 4** takes you back to the normal view; the mission keeps flying and the gimbal follows the
   flight plan again. When the flight without a pilot ends, for example with the landing, you are back in the normal view
   as well.

Only during a flight without a pilot: when you fly the drone yourself, everything stays as before (gimbal via dial and
keys, picture switched by key). A second player as camera operator always has priority: while one is bound, the live
view only shows the picture; if they accept an invitation while you steer on your own, they take over the camera and
you are back in the normal view.

---

## Controls

Every function can be assigned in Propwash's configurator, tab *Modes*, page *Add-ons*, like the built-in switches:
switches to a channel range, axes to an analog channel (dial, slider or stick). The keyboard keys are in the Minecraft
controls under *Propwash: Camera Drones*. Default keys only use keys that neither Propwash nor Minecraft use; rare
functions have none.

| Function | Kind | Default key |
|---|---|---|
| Propwash's *Flight mode* (key and radio switch): N → C → S | button, three-position switch | R |
| Flight mode N, C, S | switch (three-position channel) | – |
| Next flight mode | button | keypad / |
| Pause and hover | button | keypad − |
| Start or resume mission | button | keypad * |
| Add waypoint here | button | Insert |
| Follow me | switch | – |
| Point of interest in view | button | – |
| Course lock | switch | – |
| Gimbal follow/lock | switch | – |
| Recenter gimbal | button | keypad decimal key |
| Gimbal FPV | switch | – |
| Pilot view FPV/gimbal | button | Delete |
| Gimbal tilt | axis | Page Up / Page Down |
| Gimbal pan (Cine) | axis | – |
| Zoom | axis | – |

Recording and return home use Propwash's own *Recording* and *GPS Rescue* functions.

---

## OSD and warnings

The OSD editor has six elements in the group *Add-ons*: *Nav-FC mode* (the flight mode: *N*, *C*, *S*, *ATTI*, *POI*,
*NO POI*, *FOLLOW*, *WP 3/12*), *Gimbal tilt* (*G -45°*), *Mission* (progress with time left), *Obstacle* (the nearest,
only closer than 8 m), *Flight time left* and *Mode hint* (for 5 s at the start and on every change between N, C, S
and ATTI, what the mode means). Warnings: *OBSTACLE*, *ALT LIMIT*, *DIST LIMIT*, *NO GPS ATTI*,
*ATTI - OBSTACLE SENSING OFF*, *COMPASS ERROR*, *PAUSED*, *HIGH WIND*, *OPERATOR* (an operator has the camera),
*GIMBAL LIMIT* (only for the operator: the compact or tour gimbal is at its ±25° pan stop). OSD
texts stay in English capitals like Propwash's OSD; only the compass error, the switched-off obstacle sensing and the
mode hint are translated. Near obstacles a warning tone beeps faster the closer the drone gets, like a parking sensor.
When the drone reaches the altitude or distance limit (*ALT LIMIT*, *DIST LIMIT*), a tone sounds once; it sounds again
only after the drone has been away from the limit for a second. When a drone with obstacle sensors switches to ATTI, a
lower tone sounds once (subtitle *ATTI – obstacle sensing off*).

**Mission timer:** *WP 3/12 0:42* shows the waypoint the drone is flying to and the time left until it reaches the
*last* waypoint, including the waits on the way. The flight back to the start and the landing are not counted. The
timer counts down second by second; if the drone is faster or slower than expected (wind, sharp corners), it catches up
gently, at most two seconds per second and never upwards, so it does not jump. It shows *0:00* from the moment the drone
reaches the last waypoint, also while it waits there, flies back to the start or lands. During a pause it stands still.
Loops have no timer.

**Camera view:** instead of the OSD a camera drone can show its own camera view: mode, satellites, battery and time
left, signal at the top; height, distance and speeds bottom left; a mini map with home arrow bottom right; a tilt scale
on the right edge; red edges near obstacles and a dot with the time while recording. Switch it per drone with *View*
(*OSD* or *Camera*) in the Flight Planner's editor or in the *Mission* window of the service menu.

**Speed unit, warning tone volume and gimbal servo:** in the settings (K), tab *Camera Drones*, section *Camera view and
warning tones*: *Speed unit* switches H.S. and V.S. in the camera view between *m/s* (default) and *km/h*; height and
distance stay in metres. *Warning tone volume* (default 60%) sets how loud the obstacle beeps, the limit tone and the
ATTI tone are, 0% mutes all of them. *Gimbal servo* (default 15%) is the short servo sound at the drone when the gimbal
recenters or an operator takes over the camera; moving the gimbal stays silent, 0% turns the sound off. All are saved
with *Save* in `config/camdrones-client.json` (`speed_unit`, `warning_volume`, `gimbal_servo_volume`).

---

## Commands

All commands are under `/camdrones`. `<drone>` is the drone's name or serial number; without it the command uses your
paired drone.

| Command | What it does |
|---|---|
| `/camdrones mission list` | your camera drones with mission and state |
| `/camdrones mission start [drone] [waypoint]` | start the mission, optionally at a waypoint |
| `/camdrones mission pause\|resume\|stop [drone]` | pause, resume or end the mission |
| `/camdrones hold [drone]` | hover on the spot without pilot |
| `/camdrones follow me [drone] [behind\|parallel\|watch]` | follow you |
| `/camdrones follow <target> [drone]` | follow your pet, your mount or a player who agrees |
| `/camdrones follow accept\|deny` | answer a request to follow you |
| `/camdrones follow stop` | stop every drone that follows you |
| `/camdrones crew invite <player>` | invite a camera operator |
| `/camdrones crew accept\|decline` | answer an invitation |
| `/camdrones crew leave` | hand the camera back (operator) |
| `/camdrones crew release` | take the camera back (pilot or owner) |

Operators with permission level 2 may pause and stop other players' missions.

---

## World rules

Section *Camera Drones* in Propwash's *World rules* tab. None of them affects FPV races: race times stay valid.

| Rule | Default | Range | Effect |
|---|---|---|---|
| Allow autonomous flights | on | | flights without pilot: hold, missions, point of interest and follow me, also after leaving the FPV view; off: the drone lands or returns home as without the add-on |
| Follow other players | off | | follow me on other players, always with their consent; off: only the owner, own pets and mounts |
| Second pilot for the gimbal | on | | two-pilot camera |
| Altitude limit (m) | 120 | 0–512 | highest altitude above the take-off point, 0 = off |
| Distance limit (blocks) | 0 | 0–4096 | largest distance from the take-off point, 0 = only Propwash's radio range |
| Autonomous flight time limit (min) | 60 | 1–60 | longest flight without pilot; then the drone returns home or lands, earlier when the battery runs low |

---

## On servers

- The server decides everything: missions, starts, follow targets and the crew. Every request is checked for owner,
  distance, world rules and rate.
- **How many flights without pilot run at once** is set by Propwash's world rules
  *Autonomous flights per player at once* (default 1, 0 = none) and *Autonomous flights in total* (default 6); besides
  them Propwash always keeps room for returns home. A start beyond that reports *no budget*: Propwash names the limit
  and its value in the action bar, the chat names both world rules.
- A flight without pilot ends when the owner goes offline (the drone lands or returns home), when the drone leaves radio
  range (it returns home, without GPS reception it lands on the spot), when the battery runs low or when the time limit
  is reached. The owner gets a message. If Propwash's world rule *Drones generate new terrain* is off, the flight also
  ends at the edge of the world generated so far: the drone returns home, without GPS reception it lands on the spot.
- **Privacy:** following another player needs the world rule *Follow other players* and that player's consent. They get
  a request with *[Allow]* and *[Decline]*, see in the action bar who follows them and can end it any time with
  `/camdrones follow stop`. The consent ends when they log out.
- Camera drones carry no weapons: only the front mount (gimbal) and the top mount.

---

## FAQ

**What is different from FPV?**
A camera drone flies by speed, not by rotation rates: let go of the sticks and it stops in the air. It flies slower,
much longer and films with a stabilized camera. For acrobatics and racing, take an FPV quad.

**Why does my drone say ATTI?**
It has no usable position: no GPS fix under blocks or in caves, or a compass error. In ATTI it holds only its height and
drifts with the wind, and the obstacle sensors do not brake (*ATTI - OBSTACLE SENSING OFF*). Fly back into the open;
with a compass error land and start again.

**Why did my mission end?**
The chat message tells you why: you put the goggles on (you took over), the drone landed, the battery ran low, you went
offline, the drone left radio range, the time limit of the world rule was reached, the drone reached the world edge
while drones may not generate new terrain, someone stopped it, or another flight without pilot replaced it.

**The mission does not start.**
Pair the planner and *Send* the mission first. The drone needs a battery with at least 15 % charge and a GPS fix (a
clear view of the sky, not under blocks), must not be flown by someone else, and the world rule *Allow autonomous
flights* must be on. How many flights without pilot run at once is set by Propwash's world rules
*Autonomous flights per player at once* and *Autonomous flights in total*. The message names the reason.

**Can I use the Flight Planner with my freestyle drone?**
Yes. Every Propwash drone has GPS in its flight controller; it needs a clear view of the sky and flies missions only
without a pilot. See [Normal FPV drones](#normal-fpv-drones).

**Why was a waypoint action skipped?**
The chat message and the tooltip in the editor name the reason: no accessory with actions on the drone, no gripper on
the drone (for *Release gripper*) or a light action while Propwash does not let add-ons switch the LEDs. If a
dispenser does not fire although the action was triggered, its own rules apply: empty slot, cooldown, spawn protection
or the owner's adventure mode.

**My drone does not stop in front of a wall.**
Only the *Nav Flight Controller Pro* (Tour, Cine) has obstacle sensors at the front and sides, and Sport mode does not
brake for obstacles.

**Can I share a camera drone as a share code?**
Yes, like every Propwash drone (`/propwash code`, the configurator and the website). Missions are not part of share
codes. Someone without the add-on gets the add-on's name and link, and a replacement built from Propwash parts.

**Does it need to be installed on the server?**
Yes, on the server and on every client. A player without the add-on gets Propwash's join message with name and version
of the missing add-on instead of an error.

---

## Compatibility

- Minecraft 26.3, Fabric (Loader 0.19.5+, Fabric API) or NeoForge 26.3, Java 25.
- Propwash FPV **0.6.0 or newer** (add-on API 1.8); with an older Propwash the loader names the missing version at
  start.
- **Just More Parts:** works together; its batteries, motors and props fit camera frames by Propwash's rules.
- **Mob Defense:** works together; both use the front mount, so one drone carries either a gimbal or a Mob Defense
  accessory. On an FPV drone, waypoint actions trigger the Mob Defense dispenser, with its rules, also
  timed – at most as often as its cooldown allows.
- **Flashback:** optional; the camera path follows the gimbal's orientation and zoom, even in the FPV picture. With the
  recording mode *Flashback recording* the gimbal records into Flashback also in the background (see *Recording mode*).
- **Share codes (PW3):** camera drone parts have their own short numbers in Propwash's share codes.
- Removing the add-on later: Propwash keeps the drone data unchanged, the parts become Propwash's placeholders.

Made by lardtom. Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.

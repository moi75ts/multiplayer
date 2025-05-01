# Starsector Multiplayer Mod

## Description

This is an experimental multiplayer mod for *Starsector*, built using a client-server architecture. The server maintains the definitive game world state, and clients can update the server through their actions, synchronizing changes across all connected clients.

This is my first attempt at Java programming and game modding, so expect bugs and rough edges. I created this mod primarily as a proof of concept. Updates may be infrequent, but you are welcome to fork or contribute to the project.

## AI Disclaimer

Portions of this codebase were written with assistance from large language models (LLMs).

## License

This software is free and unencumbered, released with the following conditions:

Anyone may copy, modify, publish, use, or distribute this software, in source code or compiled binary form, for any purpose, including commercial use, subject to these restrictions:

- You may not sell this software as-is.
- You may not misrepresent the origin of this software or claim authorship or exclusive rights to the original work.
- Attribution is appreciated but not required.

To the extent permitted by law, the author(s) dedicate all copyright interest in the software to the public domain, subject to the above restrictions.

**Disclaimer**: The software is provided "as is," without any warranty, express or implied, including but not limited to merchantability, fitness for a particular purpose, or non-infringement. The authors are not liable for any claims, damages, or liabilities arising from the use of or dealings with the software.

## Prerequisites

This is a standalone mod requiring no additional mods or libraries.

**Important**: When connecting to a server, the game seed must match. If you start a new game with `devmode` enabled, map generation ignores the seed. **Do not start a new game with devmode enabled.**

## How to Create or Join a Game

Upon launching the game, a separate window will appear:

![Connection Window](https://i.imgur.com/FxQQhUh.png)

In this window, you can:
- Set the IP address to connect to.
- Choose a username, which will be displayed above your fleet.
- Start a server by clicking "Connect" in server mode.

To join a game as a client, the following must match:
- Game version
- Mod list
- Game seed

If you don't know the seed, attempting to connect will display the correct seed. You can copy it using the "Copy" button in the UI:

![Client and Server Connection Windows](https://i.imgur.com/ZJ5M9Nk.png)

## Features

### What Works
- View other players' fleets, including composition, weapons, and officers:
  <img src="Assets/movingaround.gif" alt="Two fleet moving around">

- Affect markets through trading, military actions, founding colonies, or destroying colonies:
  <img src="Assets/marketinteraction.gif" alt="Market Interaction">

- Drop cargo pods for other players to pick up:
  <img src="Assets/droppods.gif" alt="Cargo Pickup">

### What Doesn't Work (Yet)
- No combat.
- No AI fleets.
- No missions.
- Games must start with the same seed (future updates may remove this requirement).
- Fleet position rubberbanding due to acceleration issues.

## Troubleshooting

- **Planets are misaligned**: Try pausing and unpausing the game.
- **Disconnected from server**: Check the UI to confirm your connection status.
- **Other player's fleet is invisible or disappears**: Modify your fleet composition or order.

## Credits

**Author**: MatlabMaster

This mod was made possible thanks to community resources, including the *Starsector* wiki, open-source mods, and the official Starfarer API.
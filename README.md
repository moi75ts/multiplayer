# Starsector Multiplayer Mod


## Description

This is a simple, experimental multiplayer for the game Starsector

It uses a client / server architecture, where the server has the definitive state of the game world, the client through their actions can update the server, so that their changes are reflected to all other clients

This is my first JAVA / modding a game, so there is / will be a lot of bugs, I mainly made this mod to prove that it could be done.

As such do not expect regular updates, however feel free to fork / contribute to this project.

## AI disclamer

Some parts / portions of this codebase was written by / with the help of LLMs

## LICENSE

This is free and unencumbered software released with the following conditions:

Anyone is free to copy, modify, publish, use, or distribute this software, either in source code form or as a compiled binary, for any purpose, including commercial use, with the following restrictions:

    You may not sell this software as-is.

    You may not misrepresent the origin of this software. You must not claim authorship or exclusive rights to the original software.

    Attribution is not required, but is appreciated.

In jurisdictions that recognize copyright laws, the author(s) of this software dedicate any and all copyright interest in the software to the public domain, to the extent allowed by law, and subject to the above restrictions.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
## Prerequisites

this is a standalone mod, you do not need any other mods / libraries to make it work

When you connect to a server both seeds must match.
If you create a new game with `devmode` on, the map generation will not take the seed into account. DO NOT START A NEW GAME WITH DEVMODE ON.

## how to create / join a game
When the game loads a new separate window will appear
![Alt text](https://i.imgur.com/FxQQhUh.png)
from this window you can set the ip address to connect to
you can also set your username that will be visible over your fleet
when you are in server mode, connect will start the server

for a client to join the game all the following must match
- game version
- mod list
- game seed

if you do not know the seed trying to connect will give you the right seed, you can copy it using the `copy` button in the ui
![both client and server connexions windows](https://i.imgur.com/ZJ5M9Nk.png)

## FAQ

### What works
- You can see other players fleet along with their fleet composition / weapons / officers
![Moving around](https://i.imgur.com/9CBtYOZ.gif)
- you can affect markets via trades / military actions / creating a colony / destroying a colony
![sold 5 things to market, he then bought them](https://i.imgur.com/kJUCHrH.gif)
- you can drop cargo pods and have another player pick them back up
![picking up Cargo](https://i.imgur.com/TrFQ7k3.gif)
### What doesn't work / exist yet

- no combat
- no ai fleet
- no missions
- the games must be started with the same seed, in the future  you ideally would not need to do that
- fleet position rubber banding (acceleration issue)

## Troubleshooting

- The planets are not in the same positions -> try to pause / un-pause the game
- check the UI if you are still connected to the server
- I do not see the other person fleet, or it keeps disappearing -> change fleet composition / order

## Other

Author: MatlabMaster

This was made possible thanks to the many community provided resources like wiki, other open sourced mods, and the offical starfarer API 

# Chunkblaze
Chunkblaze: back up and keep your multiplayer builds forever.

## Usage
With this mod enabled, as you travel through worlds (both singleplayer and multiplayer), a fully game-compatible world will be mirrored into the `remote-saves` directory in your minecraft folder. The coordinates are consistent, so a coordnate in one world is the same coordinate in the mirror.

### Drawbacks
Due to the nature of the client/server relationship, the following things are **impossible**:

* Mapping areas that the player cannot physically travel to or near
* Mapping the contents of tile entities (chests, furnaces, etc) without the data being sent to the client (usually by interacting with th tile, like opening a chest or editing a command block). Tiles with a client component, like signs and flower pots, will maintain their data.

## Note to server owners

This mod has no effect on server performance. It does not request any data, or interact with the server in any way. When chunk data packets are pushed from the server to a client with this mod installed, the mod will wait for the data to be unpacked by the client and then saves it as region files to the users' disk. Feel free to ban the usage of this mod on your server if you feel there is a possibility that it will be used outside of the realm of players downloading copies of their own creations and intellectual property.

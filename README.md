# Twinstones

A small NeoForge mod for Minecraft 1.21.1 that adds paired teleport stones, in the spirit of Valheim's portals.

Name two stones the same thing and they become a pair. Right-click one and you arrive at the other.

## How it works

- **Craft:** 7 cobblestone (or any stone crafting material), 1 flint, 1 torch in a crafting table. One craft gives 2 stones.

  ```
  C F C
  C T C
  C C C
  ```

- **Name:** sneak + right-click a stone to name or rename it. Names match ignoring case.
- **Travel:** right-click a named stone to teleport to its twin. Pairs work across every dimension, including ones added by other mods.
- **Pairs only:** a name can belong to two stones. Naming a third stone the same thing is refused with an "already paired" message.
- **Linked look:** a paired stone's rune lights up, the stone gives off light, and it sheds particles. An unpaired stone is dull.
- **Freeing a name:** breaking or renaming a stone frees its name, so the surviving stone can be paired again.
- **Name label:** look at a named stone to see its name floating above it.

There is no ownership, cost, cooldown, or item restriction. Anyone can use or rename any stone. This is version 1, built for family play.

## Installing

Both the server and every player need the mod. Put the jar in the `mods` folder of a NeoForge 1.21.1 instance.

## Building

Requires JDK 21.

```
./gradlew build
```

The jar lands in `build/libs/`.

## Status

Version 0.1.0 compiles and builds. It has not yet been played in game.

## Layout

- `Twinstones.java`: registration and the sneak-click rule
- `TwinstoneBlock.java`, `TwinstoneBlockEntity.java`: the stone and its stored name
- `TwinstoneLinks.java`: the server-wide list of names and locations, saved with the overworld
- `TwinstoneLogic.java`: naming, pairing, and teleport rules
- `SetTagPayload.java`: the client-to-server naming packet
- `client/`: naming screen and name label

## License

MIT. See `LICENSE`.

## Credits

Built from the [NeoForge MDK](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle) template; its license is in `TEMPLATE_LICENSE.txt`.

---

*AI authorship: this mod's code, textures, and README were written by Claude (Claude Code, model claude-opus-5-5) in a working session with Emmy Ringuette, who specified the design, and are published with her approval. Method: scaffolded from the official NeoForge 1.21.1 MDK, written against NeoForge 21.1.256, and verified only by a successful `./gradlew build`. No in-game testing had been done at the time of writing.*

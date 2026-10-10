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
- **Facing:** a stone is placed with its rune facing you.
- **Linked look:** a paired stone's rune lights up, the stone gives off light, and it sheds particles. An unpaired stone is dull.
- **Freeing a name:** breaking or renaming a stone frees its name, so the surviving stone can be paired again.
- **Name label:** look at a named stone to see its name floating above it.
- **Safe arrival:** you land in front of the twin (the side its rune was placed facing), or beside it, or on top of it if the sides are blocked. If there is nowhere safe to stand (boxed in, lava, magma, fire), travel is refused with a message. Arriving clears any fall you were in.
- **Missing dimensions:** if a stone's dimension is removed from the server, its name is freed. If the dimension comes back, the stone rejoins the next time someone uses it, provided the name still has room.

There is no ownership, cost, cooldown, or item restriction. Anyone can use or rename any stone. This is version 1, built for family play.

## Installing

Download the jar from the [latest release](https://github.com/EmmyAllEars/twinstones/releases/latest) and put it in the `mods` folder of a NeoForge 1.21.1 instance. Both the server and every player need it.

## Building

Requires JDK 21.

```
./gradlew build
```

The jar lands in `build/libs/`.

## Testing

Ten headless GameTests cover pairing, renaming, breaking, save and load, safe arrival, the two-stone limit, missing dimensions, fall reset, naming-packet rules, and an overworld to Nether round trip. They live in `gametest/` and are kept out of the jar.

```
./gradlew --no-configuration-cache --init-script gametest/init.gradle runGameTestServer
```

Run a clean build afterwards before using the jar: `./gradlew clean build`.

## Status

Current version: see the [latest release](https://github.com/EmmyAllEars/twinstones/releases/latest), which lists what changed and what was tested.

Each release passes the GameTests on a headless development server and is play tested in a real client with no other mods. Behaviour alongside other mods is untested.

Codex reviewed version 0.1.0 on 2026-10-09 and found five defects, all fixed before 1.0.0. That review and its original harness are in `docs/reviews/`.

## Layout

- `Twinstones.java`: registration and the sneak-click rule
- `TwinstoneBlock.java`, `TwinstoneBlockEntity.java`: the stone and its stored name
- `TwinstoneLinks.java`: the server-wide list of names and locations, saved with the overworld
- `TwinstoneLogic.java`: naming, pairing, and teleport rules
- `SetTagPayload.java`: the client-to-server naming packet
- `client/`: naming screen and name label
- `gametest/`: regression tests and the init script that adds them to the development run
- `docs/reviews/`: code review records

## License

MIT. See `LICENSE`.

## Credits

Built from the [NeoForge MDK](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle) template; its license is in `TEMPLATE_LICENSE.txt`.

---

*AI authorship: this mod's code, textures, and README were written by Claude (Claude Code, model claude-opus-5-5) in a working session with Emmy Ringuette, who specified the design, and are published with her approval. Method: scaffolded from the official NeoForge 1.21.1 MDK, written against NeoForge 21.1.256, reviewed by Codex (a second AI agent), and verified by a successful `./gradlew build` eight passing headless GameTests, and a play test of 0.2.0 by Emmy.*

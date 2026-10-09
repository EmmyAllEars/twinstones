# Twinstones code review — 2026-10-09

Reviewer: Codex. Reviewed commit: `28d1258` (all ten Java files, resources, metadata, and build configuration). Handoff: `2026-10-09 — Twinstones mod — code review requested.md` in Obsidian/Cross-Agent Chat.

**Recommendation: fix the unsafe landing before family play.** Five actionable findings follow. The remaining findings concern fall damage, pairing consistency, stale dimensions, and packet validation. This review does not implement the fixes.

## Findings

### 1. [P1] Refuse travel when no safe landing exists

Location: `src/main/java/net/punhalla/twinstones/TwinstoneLogic.java:133–146`, especially line 141.

When the four neighbors fail, `findArrival` unconditionally returns `stone.above()`. Surround a destination stone with solid blocks and put a solid block directly above it: travel places the player's feet inside that block. This can suffocate a survival player. A low ceiling can intersect the player's head even when the feet block is clear. The server teleport API does not perform a safe-position search for this call.

The neighboring-position checks also accept harmful support blocks: four clear neighboring spaces over magma pass the `isFaceSturdy` test and select magma as the landing floor. Fire and some other contact hazards also have empty collision shapes, so empty collision and empty fluid are insufficient safety checks.

**Evidence:** `reproduces_obstructed_fallback` and `reproduces_magma_arrival` both reproduced these selections in the actual GameTest world.

**Fix:** validate every candidate, including the fallback, against the player's bounding box, suitable support, hazards, world border, and build height. Return no destination and show a blocked-arrival message if none is safe. Add regression checks that expect refusal for a boxed-in stone and an unsafe floor.

### 2. [P2] Reset fall state when teleporting

Location: `src/main/java/net/punhalla/twinstones/TwinstoneLogic.java:80–84`.

The selected `ServerPlayer.teleportTo(ServerLevel, double, double, double, float, float)` overload moves the player and dismounts them, but does not reset `fallDistance`. The underlying connection teleport calls `absMoveTo`, which also leaves the accumulated fall distance intact. A player clicking a stone during a fall brings that accumulator to the destination; the next landing can apply damage from the previous location. Teleport acknowledgement also does not clear it.

**Evidence:** `reproduces_fall_distance_retained` used a real `ServerPlayer` with an embedded connection, set `fallDistance` to 30, and observed 30 after `TwinstoneLogic.teleport`. This was a state reproduction, not an end-to-end survival damage assertion.

**Fix:** reset fall distance for successful travel and deliberately handle motion at arrival. Test same-dimension and cross-dimension travel from a falling player. If retaining momentum is desired, document it and still prevent inherited fall damage.

### 3. [P2] Enforce the two-stone limit when registering during travel

Location: `src/main/java/net/punhalla/twinstones/TwinstoneLogic.java:70–74`; related: `TwinstoneLinks.java:45–49`.

The rename path enforces the cap, but teleport unconditionally calls `links.add(key, self)`. A third named block entity created by `/clone`, a saved structure, or another mod's copying mechanism bypasses the naming UI and joins an already full pair on its first use. All three valid entries survive pruning. `refreshLinked` then sets every stone to unlinked because the count is three, while travel still arbitrarily chooses the first other entry. The original pair loses its linked appearance and destinations become asymmetric.

**Evidence:** `reproduces_third_imported_stone` created two normally named stones, assigned the same persisted-name state to a third, then used it. The registry contained three entries and the original stones became unlinked. The test reproduced the resulting block-entity state; it did not invoke the `/clone` command itself.

**Fix:** use one validated, capacity-enforcing registration operation for every insertion path. Check membership and cap after pruning before adding an unregistered source. Reject excess imported stones without disturbing an existing pair, and provide recovery for an already oversized saved list.

### 4. [P2] Recover names reserved by a removed dimension

Location: `src/main/java/net/punhalla/twinstones/TwinstoneLogic.java:101–104`.

When `server.getLevel(entry.dimension())` returns null, pruning preserves the entry forever. Unloaded chunks are different: their dimension still has a `ServerLevel`. In the normal server lifecycle, a removed dimension mod or removed dimension datapack leaves this null-level case with no reachable stone to validate or break.

An overworld stone paired with that missing dimension cannot travel, and naming a replacement is refused as already paired. Renaming the survivor does not clear the missing partner's registry entry. Subsequent reuse of the name can still pair with the ghost and prevent a real pair.

**Evidence:** `reproduces_absent_dimension_reservation` inserted one location in an absent dimension and confirmed that naming a replacement was refused.

**Fix:** reconcile saved entries against the active dimension registry after levels are initialized. Remove permanently absent dimensions or provide an explicit stale-link reset if retaining entries for reinstated dimensions is intentional. Distinguish that policy from ordinary chunk unloading.

### 5. [P2] Apply normal interaction restrictions to naming packets

Location: `src/main/java/net/punhalla/twinstones/SetTagPayload.java:33–39`.

Any connected player can submit a rename for a loaded stone within the fixed eight-block radius, including a spectator. The custom packet bypasses vanilla's spectator behavior, server spawn protection, world-border interaction check, and attribute-based block reach. A crafted packet can therefore rename or unlink stones that the same player could not interact with through a normal right-click.

This finding does not require adding ownership: anyone allowed to interact can remain allowed to rename. It concerns existing Minecraft restrictions.

**Evidence:** `reproduces_spectator_packet_rename` supplied a spectator server player through `IPayloadContext` and successfully changed the name. Spawn-protection and reach bypasses were confirmed by comparing the handler with `ServerGamePacketListenerImpl.handleUseItemOn` and `ServerLevel.mayInteract`; they were not separately exercised in the harness.

**Fix:** reject spectators and players who cannot normally interact with the target; use the appropriate reach check and `level.mayInteract`. For a naming screen that stays open while the player moves, apply an explicit, bounded interaction policy at submission. Do not rely on the client opening the screen as authorization.

## Handoff questions checked

| Concern | Result |
| --- | --- |
| Dedicated-server client references | Headless server bootstrap and common block use passed. The guarded `ClientHooks` call was not executed on the server; no side-loading failure occurred. A proxy is not required merely because this method contains that guarded call. |
| Sneak-click with held items / off hand | Exact client and server sources fire `RightClickBlock` and honor the forced block-use/denied item-use flags. The default `useItemOn` delegates to `useWithoutItem` only for the main hand; that method consumes the action. No double naming-screen invocation follows from this code path. Client UI behavior still needs manual testing. |
| `LINKED` flips / removal re-entrancy | Same-block transitions preserve the block entity and name. Breaking removes the registry location and updates the survivor. These paths passed runtime checks. |
| Chunk loads / stale locations | `getBlockEntity` synchronously obtains the full chunk; `refreshLinked` also loads chunks through `getBlockState`. This can stall interactions on cold or expensive chunks. Ordinary absent or renamed blocks in an available dimension are pruned. Removed dimensions are finding 4. No timing benchmark was run. |
| Cross-dimension travel / mounts | A real server player completed an overworld→Nether→overworld round trip. The selected API explicitly calls `stopRiding`; mounts are left behind. Destination inspection loads chunks before travel. Tensura dimensions and mod vetoes were not exercised. |
| Arrival | Findings 1 and 2. |
| Packet handling | Finding 5. Codec bounds and server-side trimming/capping exist. Handlers use the main thread by default, so an extra `enqueueWork` is not required here. See [NeoForge 1.21.1 payload documentation](https://docs.neoforged.net/docs/1.21.1/networking/payload/). |
| SavedData | The two-argument factory is valid in these patched APIs. Overworld storage spans dimensions. Save/load round trip retained both positions; dirty tracking exists for additions/removals. No actual shutdown/restart reload test was performed. |
| Name renderer | The scale signs match this version's `EntityRenderer.renderNameTag`: `(0.025F, -0.025F, 0.025F)`. No source-level transform defect found; appearance was not visually checked. |
| Recipe / loot / assets | Singular `recipe`, `loot_table`, and `tags/block` folders are correct. Recipe yields two stones from seven stone crafting materials, flint, and torch. Resource JSON and model/texture references are consistent; server recipe loading reported no Twinstones error. Texture appearance and client model loading remain untested. |

## Verification and limits

- Original checkout was clean. `./gradlew --offline build` succeeded; Gradle reported `test NO-SOURCE`.
- Exact source used: the generated NeoForge **21.1.256 / Minecraft 1.21.1** source jar, plus official networking documentation.
- The final isolated GameTest run completed **8/8 checks** in a fresh disposable world: two normal-behavior checks and six checks reproducing the five finding groups. Reproduction checks pass when the current bug occurs; they are **not** acceptance tests asserting the mod is correct.
- The tests used a headless development server, fake players where only rename/registry behavior mattered, and real server players with embedded connections for fall state and dimension travel.
- An intermediate rerun reused a disposable world and had two failures from previous named-stone state. The harness now creates a fresh world for each invocation; the final run above passed.
- No manual client play, production-server launch, Minecraft client rendering, survival-damage end-to-end assertion, actual modpack run, or NeoForge 21.1.257 validation was performed.
- No runtime source fixes, commit, or push were made. Review artifacts and the reproducible harness are saved alongside this report.
- After the harness run, `./gradlew --offline clean build` succeeded. The distributable jar was inspected: it contains neither `ReviewGameTests` nor the review structure resource.

## Reproduce the review checks

From the repository root, with JDK 21:

```sh
./gradlew --no-configuration-cache \
  -Dtwinstones.reviewHarness="$PWD/docs/reviews/2026-10-09-harness" \
  --init-script docs/reviews/2026-10-09-harness/init.gradle \
  runGameTestServer
```

The init script temporarily adds the review Java/resources to the development run and creates a new test world in the system temporary directory. Runtime dependencies/assets may need downloading on the first invocation. The recorded successful run is in `2026-10-09-harness/run.log`.

After running the harness, rebuild without it before using or distributing the jar:

```sh
./gradlew --offline clean build
```

The reproduction checks should be converted into assertions of corrected behavior when fixes are implemented.

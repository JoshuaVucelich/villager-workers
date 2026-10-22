# Villager Workers design

This document is the approved plan for mod id `villager_workers`. Milestones 1, 2, 3, and 4 are implemented. Milestone issues are in [MILESTONES.md](MILESTONES.md).

The target game is Minecraft 1.21.1 on NeoForge 21.1.x, in a pack with Create 6.0.x and Create Aeronautics (`aeronautics`). The mod must still load if Create is absent. There is no leveling system.

## Loader split

| Package | May import | Holds |
| --- | --- | --- |
| `com.vucelich.villagerworkers.common` | JDK, `net.minecraft` | Saved data, codecs, chat parsing, the permission policy, work-volume math, the break-safety predicate |
| `com.vucelich.villagerworkers.neoforge` | NeoForge plus `common` | `@Mod` entry, attachment registration, menus, screens, payload registration, event hooks |

`common` must not import `net.neoforged`, Forge, or Fabric. NeoForge 1.21 stores extra entity data as a data attachment. That registration stays in the NeoForge package. The data class and its Mojang `Codec` stay in `common`, so a later loader can persist the same NBT with its own attachment API.

Permission checks are a pure function of `(worker data, actor UUID, action)`. They do not read the client, and they do not special-case singleplayer. An integrated server is still multiplayer as far as these checks go.

## What a worker is

A worker is still a `Villager`.

- The villager can die. Death is not cancelled, and worker data is not copied onto a respawned entity. Do not set `copyOnDeath` on the attachment.
- The villager heals by eating food with the vanilla villager inventory. This mod does not add a hunger bar.
- The villager can breed under vanilla rules, including with another worker or a normal villager. The baby is a new entity and does not receive worker data, a custom job, or an owner.
- Hiring is adults only. A name tag on a baby renames the baby and does not open the hire screen.
- Applying a name tag to someone who is already a worker renames them and does not change the owner.

Unhired villagers are unchanged.

## Rank and gear

A hired villager has rank `worker` or `foreman`. Hiring sets `worker`. The owner, or a trusted player who may edit setup, can promote and demote from the worker screen. Rank is saved inside the worker attachment.

Other players need to see the hat, so the rank string is copied to a second attachment, `villager_workers:look`, and that attachment is synced. The trusted list stays on the worker attachment and is not synced.

Gear is drawn on the client. It is not armor and it is not an item in the villager's hand slot.

| Rank | Hat | Hands |
| --- | --- | --- |
| Worker | Yellow hard hat | none |
| Foreman | Orange hard hat | Clipboard in one hand, rolled blueprint under the other arm |

A villager who was never hired has no look attachment and no hat. Death does not copy either attachment. A baby has neither.

## Crews

A worker may store the UUID of one foreman. A foreman is not a member of a crew. The worker screen lists foremen the player may edit, by name, next to the existing people controls. The foreman screen lists unassigned workers the player may edit, and the current crew.

For each crew member the foreman screen can unassign them, move them to another foreman, call them, or hand sole ownership to an online player in the same dimension. Come, stay, and work each have three targets: the foreman only, the foreman plus the whole crew, or the ticked members. A ticked id that is not on that crew is ignored.

Scaffold preference is a saved order (`andesite`, `vanilla`, `spare`) plus a plain-blocks flag. The default order is andesite scaffolding, then vanilla scaffolding, then spare blocks. Changing it on the foreman copies it onto every loaded crew member whose own-rules flag is off. A member with own rules on keeps their order. While a member is working, the foreman's current order is what they use, unless that member has own rules on.

## Hiring

1. A player uses a name tag on an adult villager. Vanilla consumes the tag and sets the custom name.
2. If that villager is not already a worker, the server opens the hire screen for the player who applied the tag.
3. The screen offers two choices:
   - **Named.** Close the screen. No attachment is stored. The villager is an ordinary named villager. A later name tag opens this screen again.
   - **Worker.** Create the attachment. The actor becomes the owner. Job is `NONE`, activity is `IDLE`, the trusted set is empty, `rested` is false, and the home bed is empty.

The server remembers a pending hire as `(player UUID, villager UUID)` for 60 seconds. The hire packet is accepted only when that pending entry matches, the villager is still loaded in the player's level, and the player is within 6 blocks. Choosing Named deletes the pending entry and writes nothing.

## Data model

One optional attachment on the villager, id `villager_workers:worker`. Absence means "not a worker". Default-constructing the attachment must not turn a villager into a worker; only the hire action calls `setData`.

`WorkerData` fields:

| Field | Type | Meaning |
| --- | --- | --- |
| `owner` | UUID | Player who hired the villager, or the player who last received a transfer |
| `trusted` | set of UUID | Players who may operate this worker. Names are resolved while online and are not the source of truth |
| `job` | `NONE`, `LUMBERJACK`, `MINER`, `BUILDER` | Selected job. `NONE` does no work |
| `activity` | `IDLE`, `WORKING`, `COMING`, `STOPPED` | Whether job logic may run |
| `rested` | boolean | Buff for the current day |
| `sleptLastNight` | boolean | Set when they finish sleeping; copied into `rested` at dawn, then cleared |
| `homeBed` | dimension id + block pos, or empty | Last bed they finished sleeping in |
| `lumberjack` | `LumberjackConfig` | Kept when the selected job changes |
| `miner` | `MinerConfig` | Kept when the selected job changes |
| `builder` | `BuilderConfig` | Kept when the selected job changes |
| `breakWhitelist` | set of block ids | The only Create, Aeronautics, or other machine blocks this worker may break |
| `problem` | problem code + optional item or block id, or empty | Latest problem the owner was told about |
| `carry` | 27 item stacks plus one tool stack | What the worker is holding. Omitted from the save when every slot is empty, so a 0.4.6 save still loads |

Configs stay stored across job switches so setup is not wiped by selecting another job.

### LumberjackConfig

- `area`: inclusive box (min pos, max pos) in one dimension, or empty
- `clearLeaves`: break `#minecraft:leaves` inside the box
- `replantSaplings`: after a trunk is cut, place the matching sapling
- `logChest`, `saplingChest`, `toolChest`: block positions in that same dimension, or empty

### MinerConfig

- `mode`: `CLEAR_BOX`, `TUNNEL`, `BRANCH`, `SHAFT`
- `area`: used by `CLEAR_BOX` only
- `origin`: one block position plus a horizontal facing, used by tunnel, branch, and shaft
- `width`, `height`, `length`: tunnel and branch corridor size
- `wallBlock`: block id to place on the sides and ceiling, or empty for dig-only
- `branchSpacing`, `branchLength`: branch mode only
- `shaftWidth`, `shaftDepth`, `shaftUp`: shaft mode only
- `valuables`: item ids whose drops go to the valuables chest
- `useValuables`: when true (the default, including old saves), matching drops go to the valuables chest. When false, every drop goes to the regular chest and that chest is the only one required
- `valuablesChest`, `regularChest`, `toolChest`: positions or empty

### BuilderConfig

- `schematicAnchor`: position of a placed Create schematicannon that already has a schematic, or empty
- `materialChests`: list of chest or barrel positions

### Default valuables list

A new miner config starts with these item ids, and the owner can edit the list:

`minecraft:diamond`, `minecraft:emerald`, `minecraft:raw_gold`, `minecraft:raw_iron`, `minecraft:raw_copper`, `minecraft:lapis_lazuli`, `minecraft:redstone`, `minecraft:coal`, `minecraft:ancient_debris`, `minecraft:quartz`, `minecraft:amethyst_shard`

The filter matches dropped items, not the ore block. Ancient debris drops itself, so it is listed as the item. The worker screen offers checkboxes for those drops, the vanilla ores, and any block in the `c:ores` tag. Nothing on that list is typed.

Mined and chopped drops go into the 27 carry slots first. What does not fit is inserted into the linked chest for that item. Only a leftover that fits in neither place is dropped, and a full chest pauses the worker. A stack that did fit in the inventory is still hauled to that chest when the chest has room. The tool slot is an axe or a pickaxe and is used before the tool chest. A worn tool is put back in the tool chest, and the tool slot stays free for a usable one. On death, on a lightning or zombie conversion, and when the villager is discarded from a loaded chunk, the carry slots, the tool slot, and the held item are dropped once. The player can put items in and take them out from the menu.

### Work volumes

Breaking is allowed only inside the volume for the selected job and mode. The volume is always in the worker's current dimension.

- **Clear box.** The inclusive box from the area wand's two corners. Mine from the highest `y` downward.
- **Tunnel.** One wand click sets the start block. Facing is the horizontal direction the player was looking. `width` is an odd integer from 1 to 7 (default 3). `height` is an integer from 2 to 5 (default 3). `length` is an integer from 1 to 128 (default 16). The dug volume is that corridor. When `wallBlock` is set, the worker may also place that block on the left, right, and ceiling, taking the item from the regular chest. The floor is not filled. Placement never replaces a block the break rules would refuse to remove. It also refuses a position on a ship plot or a Create contraption, and it refuses a position the worker cannot reach.
- **Branch.** The tunnel corridor, plus left and right branches of `branchLength` (1 to 64, default 8) every `branchSpacing` blocks (2 to 16, default 4). Branches use the same width and height. No branch is cut in the first spacing interval, so the entrance stays a single corridor. The volume is the union of those corridors.
- **Shaft.** One start block, square `shaftWidth` from 1 to 5 (default 2), `shaftDepth` from 1 to 64 (default 16), downward unless `shaftUp` is set. No wall fill.
- **Lumberjack.** The clear-box volume only.
- **Builder.** The schematic's own bounds. The builder does not break blocks (see Builder).

### Rested and the home bed

Workers are not forced to sleep. While `activity` is `WORKING` or `COMING`, vanilla bed-seeking is suppressed, so a working night is a missed night. While `IDLE` or `STOPPED`, vanilla villager sleep still runs.

When the villager finishes sleeping in a bed:

- `sleptLastNight = true`
- `homeBed` becomes that bed's dimension and position

At dawn (the level's day time crosses into the morning), `rested = sleptLastNight`, then `sleptLastNight = false`. A villager who slept is faster the next day. A villager who did not sleep still works.

The speed change is applied only by job code (M2), not by M1:

| | Break-speed multiplier | Ticks between actions |
| --- | --- | --- |
| Rested | 1.25 | 10 |
| Not rested | 0.75 | 20 |

`homeBed` is cleared when that block is no longer a bed. It is not cleared when `rested` flips.

## Ownership and permissions

The owner is the hiring player. Trusted players are UUIDs the owner adds. A trusted player must be online at the moment they are added; the server resolves the typed name to a UUID and stores the UUID. Removing a trusted player does not require them to be online.

A worker has one primary owner and up to 7 more owners, 8 in total. Every owner can do what the primary owner can do, including the trusted list and sole transfer. Hiring starts with one owner. Adding an owner who was trusted removes them from the trusted set. Trusted players still cannot change owners.

Sole transfer leaves exactly one owner. The previous owners are not added to the trusted set. Transfer requires the new owner to be online in the same dimension. A save that only has the legacy `owner` field reloads as that one owner. A save with an `owners` list reloads those UUIDs, primary first.

| Action | Owner | Trusted | Anyone else |
| --- | --- | --- | --- |
| Open the worker screen | yes | yes | no |
| See status, job, setup | yes | yes | no |
| Start, stop, come, resume | yes | yes | no |
| Change job or job setup, wand, chests, whitelist, valuables | yes | yes | no |
| Edit the trusted list | yes | no | no |
| Transfer ownership | yes | no | no |
| Chat `@Name` commands | yes | yes | no |

The client hides the permissions tab from trusted players. That is not the security check. Every packet and menu button checks the role again on the server. A failed check is ignored except for a one-line denial sent to that player. Checks use the server player UUID, never a UUID supplied by the client as "I am the owner".

A trusted player acts with the owner's claim rights. Starting a job, marking an area, or telling the worker to come does not lend the trusted player's claims to that worker. Breaking a block, placing a block, and opening a linked container are checked as the owner: spawn protection, the world border, claim mods, and container locks all see the owner. If the owner is offline, the worker waits instead of skipping those checks. The click that links a chest or schematicannon is the clicking player's own interaction. That click is refused when another mod already cancelled it, when that player may not interact with the position, or when a lockable container does not open for them. Hauling later checks the owner again.

Other players keep the vanilla villager interaction, including trading. Owner and trusted players open the worker screen on right-click. Sneak-right-click keeps the vanilla interaction so trading still works. GUI and mutation packets are rejected if the player is more than 6 blocks from the villager or the worker menu is not open. Chat commands run only when the line starts with `@` and the worker is loaded in the sender's dimension. Distance does not matter.

## Networking

M1 does not broadcast the attachment to tracking clients. The trusted list must not be entity-synced. The open menu is the client view, filled by the server.

Payloads are records in `common`. The NeoForge package registers them. The server drops a payload when the villager id is not a worker in the sender's level, the sender is out of range, or the role does not allow the action.

Client to server:

| Payload | Body | Who |
| --- | --- | --- |
| `hire_choice` | villager UUID, hire or not | player with a matching pending hire |
| `set_job` | villager UUID, job | owner, trusted |
| `set_activity` | villager UUID, `START`, `STOP`, or `COME` | owner, trusted |
| `update_lumberjack` | villager UUID, lumberjack config | owner, trusted |
| `update_miner` | villager UUID, miner config | owner, trusted |
| `update_builder` | villager UUID, builder config | owner, trusted |
| `update_whitelist` | villager UUID, block ids | owner, trusted |
| `add_trusted` | villager UUID, player name | owner; server resolves the name |
| `remove_trusted` | villager UUID, trusted UUID | owner |
| `transfer_owner` | villager UUID, new owner UUID | owner; new owner must be online in that dimension |

`START` sets `WORKING` when a job is selected, otherwise `IDLE`. `STOP` sets `STOPPED`. `COME` sets `COMING`. Job code (M2 and M3) runs only while `WORKING`. `COMING` pathing is M2. M1 stores the activity and shows it.

Server to client:

| Payload | Body | Who |
| --- | --- | --- |
| `open_hire_screen` | villager UUID, current name | the player who just applied the name tag |
| `permissions_snapshot` | owner UUID, trusted UUID and last known name | the owner, when the permissions tab is opened |

Job setup, the valuables checkboxes, and the size buttons use the menu's `clickMenuButton`. Trust and transfer still use the payloads above. Both paths call the same server-side permission function. Chat commands stay optional.

## GUI screens

### Hire screen

Shown after a successful name tag on an adult who is not a worker. It shows the villager's name and two buttons:

- **Named villager.** They keep the name and are not hired.
- **Hire as worker.** The player who named them becomes the owner.

### Worker screen

Right-click, for the owner and trusted players. The top of the screen always shows the name, health (`current/max`), activity (working in green, stopped in red), job, rested or not, and the current problem. Five tabs sit under that. Buttons have tooltips. Nothing in the menu asks for typed text. The panel is 448 by 232, so it fits a 1920x1080 window at GUI scale 4. The 27 carry slots, the gold tool slot, and the player's inventory sit on the right. Size buttons use the same `Name- value` and `Name+` labels.

**Job**

- None, Lumberjack, Miner, Builder
- Start, Stop, Come

**Area**

- Lumberjack: mark area, clear-leaves toggle, replant toggle
- Miner: mode cycle, mark, width, height, and length buttons, wall cycle, branch or shaft buttons, valuables on or off, and a paged checkbox list of ores and valuables
- Builder: a note that Create is required when it is not installed

**Storage**

- Lumberjack: log chest, sapling chest, tool chest
- Miner: valuables chest, regular chest, tool chest
- Builder: schematicannon, material chest

**Inventory**

- A short note that shift-click moves items and that the gold-edged slot is the tool

**Settings**

- Allow the block in the player's hand, or clear those extra blocks
- Owner only: trust an online player, remove a trusted player, or give that player the worker

### Area wand

Mod item `villager_workers:area_wand`. It is not consumed. The player selects "Mark area" on a worker they may edit, then uses the wand.

- Clear box and lumberjack: the next two block clicks are the corners.
- Tunnel, branch, and shaft: the next block click is the origin. Facing is the player's horizontal facing.

The server stores the result on that worker only. Clicks by a player who may not edit that worker do nothing. The wand never breaks or places blocks.

### Menu hold

`holdWhileMenuOpen` in the common config is on by default. While it is on and at least one player has this worker's menu open, the worker stands still. The server remembers those viewers only in memory. It drops a viewer when the menu closes, and it checks again every tick for a logout, death, a dimension change, or a walk out of the 6 block menu range. Chunk unload and server stop drop the record too. The last viewer to leave is the one that lets the worker move.

While a viewer remains, pathing is cleared and horizontal movement is set to zero. The worker is not moved back to a fixed world X or Z, so a moving ship can carry them. Gravity and water still apply. The worker looks at the nearest viewer. Lumberjack, miner, and builder actions do not run, so a hold never breaks or places a block. Cooldowns, the rescan wait, the stuck timer, and the hungry and hurt notice timers do not advance. The job, the current target, and a stopped or paused activity stay as they were. `@Name stop` during a hold stays stopped after the menu closes.

### Linking chests

"Link chest" on the setup panel, then right-click a chest, trapped chest, or barrel within 8 blocks of the worker. The server checks the block is one of those and is in the worker's dimension. Create machines, funnels, and vaults cannot be linked. The click is ignored when it was already cancelled. The player must be allowed to interact with that position, and a lockable container must open for them. A linked chest is remembered by position. If the block there is no longer a chest or barrel, the link is empty and the worker reports a missing chest. Each time the worker opens a linked container it checks the owner again: spawn protection, the world border, and the container lock. A trusted player who linked the chest does not keep it open if the owner cannot.

## Chat commands

Listen on the server chat event. Do not cancel the player's message.

A command is a chat line whose first character is `@`. The rest is `Name`, then whitespace, then one verb. The name is the villager's custom name, compared exactly, including spaces. The verb is case-insensitive: `come`, `stop`, `resume`, `status`. The server does not search for workers unless the raw message starts with `@`.

Parsing:

1. If the line does not start with `@`, ignore it.
2. Among loaded workers in the sender's dimension, keep those whose custom name is a prefix of the text after `@`, with a name boundary (end of line or whitespace) after the name. Distance does not matter.
3. If none match, ignore the line. It is ordinary chat.
4. Keep the longest matching name. If two workers tie, reply `More than one worker is named <name>.` and run nothing.
5. The trailing verb must be one of the four. Anything else gets `Unknown command. Use come, stop, resume, or status.` when the sender may command that worker.
6. If the sender is not the owner and not trusted, reply `You cannot command <name>.` and run nothing.

Verbs:

| Verb | Effect |
| --- | --- |
| `come` | Set activity to `COMING`. From M2, path to within 2 blocks of the sender, then wait |
| `stop` | Set activity to `STOPPED` and clear navigation |
| `resume` | Set activity to `WORKING` if a job is selected, otherwise `IDLE` |
| `status` | Reply with name, job, activity, rested or not, health, and the current problem |

Example status: `[Birch] lumberjack, working, rested, 20/20 health. No problems.`

## Messages to the owner

Problems are sent as a system chat line to the owner only, and only if the owner is online. The same problem code for the same worker is sent at most once per minute. If the owner is offline, keep the latest problem on the worker and deliver that one line when the owner next logs in. Do not queue a history.

| Code | When |
| --- | --- |
| `HUNGRY` | Food in the villager inventory is 0 and health is below max. M1 |
| `HURT` | Health is at or below half. M1 |
| `BROKEN_TOOL` | The equipped tool has 1 durability left, or there is no usable tool in the tool chest. M2 |
| `OUT_OF_MATERIALS` | A place action needs an item that is not in the linked chests. Includes the item id. M2 and M3 |
| `MISSING_CHEST` | A linked chest position is empty or not a container. M2 |
| `NO_ACCESS` | The linked container is locked, or the owner may not interact with it. Work pauses once. A double chest checks both halves. |
| `CHEST_FULL` | The destination chest cannot take the drops. Drops stay on the ground at the worker, and work pauses. M2 |
| `STUCK` | The worker has a target and has not moved for 10 seconds. Retry the path once. If still stuck, pause. M2 |
| `BLOCKED` | Builder found a solid block that does not match the schematic. Skip that position. M3 |
| `SHIP_GUARD` | Sable is loaded, but the ship lookup failed. Every block is treated as occupied, and the owner is told once. The lookup is tried again after 600 ticks (30 seconds). A lookup that returns without throwing clears that occupied block, so digging can resume. If the plot handle is what threw, the notice stays until a later lookup gets that handle to return. A retry that never reaches the plot does not clear the notice and raise it again. The warning stays quiet until a plot and its sublevel both return, or the server stops. |

`HUNGRY` and `HURT` can both be true. Send each under its own rate limit. Work does not stop for hungry or hurt. Work does pause for a missing tool, a missing or full chest, no materials, or stuck.

## Block breaking safety

Every break attempt goes through one predicate in `common`. Job code cannot call `destroyBlock` any other way. The builder does not break blocks.

The predicate allows a break only when all of the following are true:

1. Activity is `WORKING`, the job is lumberjack or miner, and the position is inside that job's computed volume and dimension.
2. The worker is within 4.5 blocks of the position.
3. The block's destroy speed is zero or greater (not bedrock-class unbreakable).
4. The block is not on the global denylist: `bedrock`, `barrier`, `command_block`, `chain_command_block`, `repeating_command_block`, `structure_block`, `structure_void`, `jigsaw`, `end_portal`, `end_portal_frame`, `nether_portal`, `reinforced_deepslate`. The whitelist cannot override this list.
5. The position is not the home bed and not a linked chest, schematic anchor, or wand origin marker. It is also not a block that currently sits on a loaded Create contraption, and not a block whose chunk belongs to a Sable ship plot. The whitelist cannot override this.
6. The block has no block entity, unless its id is on this worker's `breakWhitelist`.
7. The block's namespace is not `create`, `aeronautics`, `simulated`, `sable`, `offroad`, `createbigcannons`, `railways`, or `numismatics`, and its path is not `rail`, `powered_rail`, `detector_rail`, or `activator_rail`, unless its id is on this worker's `breakWhitelist`. The extra Create namespaces are the Aeronautics pack, Big Cannons, Steam 'n' Rails, and Numismatics. Vanilla rails live in `minecraft`, so they are matched by path. The default whitelist is empty.
8. Lumberjack additionally requires `#minecraft:logs`, or `#minecraft:leaves` when `clearLeaves` is set. Logs outside the box are not cut, including a tree that grows out of the box. Leaves outside the box are not cleared.
9. Miner may break any other block that passed the checks above. It still cannot break denylisted blocks, unwhitelisted machines, or unwhitelisted Create and Aeronautics blocks.
10. The owner would be allowed to break that block. Before the break, post NeoForge's block-break event as a survival-mode break by the owner (spawn protection, world border, and claim mods that listen to that event). If the event is cancelled, do not break the block. Do not use a creative fake player.

Whitelist entries exempt only rules 6 and 7. They do not exempt the volume, the reach check, the global denylist, linked chests, ship plots, contraptions, or the owner permission check.

Drops are inserted into the worker inventory first. Nothing is spawned as an item for a stack that fits. What does not fit goes to the linked chest for that item (logs, saplings, valuables, or the regular chest). What still does not fit is dropped at the worker, and a full chest pauses the job. A stack that fit in the inventory is still hauled to that same chest when the chest has room. Nothing is voided.

The worker honors the `doTileDrops` game rule for the block item. When it is off, the block is removed and the block item is not kept, but items that were inside a container still spill on the ground the way vanilla does. When it is on, those contents are hauled with the block drop instead of spilled. A break counts as finished when `destroyBlock` succeeds or the original block is gone, including when a waterlogged block leaves water behind, so that chest's contents are still hauled. A creative-mode owner does not run the creative destroy hook for a shulker box or beehive, so those blocks are not dropped a second time.

Tools come from the tool chest: an axe for the lumberjack, a pickaxe for the miner. The worker equips one, and vanilla durability applies. At 1 remaining durability the worker stops, returns the tool if it can, and messages the owner. On death the equipped tool drops with the villager. On stop or idle the tool is put back in the tool chest when that chest has room.

Replanting places one matching sapling on a dirt-like block inside the lumberjack box after that trunk's logs are gone. It does not place outside the box, and it does not replace a non-dirt block. The sapling is taken from the sapling chest. A pass that starts over keeps a stump that is still waiting for that sapling. While the worker is still working, a finished lumberjack or miner pass starts again after a wait of at least 800 ticks (40 seconds). The wait is also at least ten times the ticks an empty scan of that volume takes, including a leaves walk and any wall cells, so the scan stays under a tenth of the cycle. Each worker adds a stable offset of 0 to 200 ticks from its id. That later pass uses the same per-tick scan budget and the ship cache. Stop then resume starts a pass on the next tick.

## Builder

Create is an optional runtime dependency, declared as `create` `[6.0,)`. The mod loads without it. Starting the builder job without Create is rejected unless a structure file is linked. That rejection is a problem line the owner can see.

The player places a Create schematicannon and loads a schematic into it, then links that block from the builder setup. The worker reads the schematic. It does not turn the cannon on and does not spend the cannon's fuel.

A builder can also place gzipped vanilla structure files, the same NBT `StructureTemplate.load` reads. Put the files in the world folder `villager_workers/schematics`. A written book, or a book and quill, lists one piece per line as `file.nbt dx dy dz`. Mark origin with the area wand. Pieces are numbered parts of one build, each with its own offset, so a huge build can be split the way a 3D printer splits a model. The worker places the lowest layer first and keeps block entity data when that data has no items, book, record, or loot table. Materials come from the linked chests, then the worker inventory. Missing materials are told to every online owner. When Create is installed, item counts use Create's `ItemRequirement`, and only consumed items are taken. Aeronautics ships saved unassembled are ordinary blocks in that file, so they build the same way. The cannon path is unchanged and is used only when no structure file is linked.

`buildPlacementsPerTick` (default 1), `buildScanBudget` (default 64), and `buildIntervalTicks` (default -1, which uses the normal work pace) cap the cost. `scaffoldSearchBudget` (default 8) caps how many non-empty slots a builder inspects when picking the next temporary block. `createSchematicUploadKb` defaults to 256 and does not change Create. A higher value raises Create's `maxTotalSchematicSize`, and `maxSchematicPacketSize` up to its 32767 byte cap, through the public config object. No mixin. A structure file is rejected when its compressed size is over that cap. Tag accounting may use up to eight times the cap, and never more than 32 MB.

Build now places the whole assigned schematic in one action. The server allows it only when the player is in creative mode and has instabuild. Survival, adventure, and spectator place nothing.

A foreman with a structure file can split layers across the crew. Each member becomes a builder with the same pieces, origin, and chests, and a y band of their own.

When the next cell is out of reach, the builder places one temporary block and steps onto it. The block comes from the crew rule: Create andesite scaffolding when Create is installed and the item is in a material chest, then vanilla scaffolding, then a spare block that is not valuable and does not fall. Plain blocks skips both scaffolding items and uses spare blocks only. A scaffolding piece that would be seven steps from support is not placed, so it cannot fall into the build. The spare block is a pillar or a single step, not a stair item. Each temporary block remembers the state it replaced. The builder puts that state back when the job finishes or stops, and the item goes to a material chest, then the inventory, then the ground. A temporary block sitting where the schematic wants a real block is lifted first and the item is returned. Blocks the builder did not place are left alone. The ship check runs only for the one block about to be placed.

Confirm Create 6.0's schematic holder API at the start of M3 (`SchematicannonBlockEntity` and the schematic type in `com.simibubi.create.content.schematics`) before writing the adapter. The adapter lives in the NeoForge package because it has to call Create. The placement rules stay in `common`.

Placement, bottom to top:

- If the world block already matches the schematic entry, skip it.
- If the position is replaceable (air, water, or another replaceable material), take one matching item from the material chests and place it. Create 6.0.10 schematic block-entity NBT can carry arbitrary item components, so the builder places the block state only and does not copy that NBT.
- If the position is a solid block that does not match, skip it, record `BLOCKED`, and continue. The builder never breaks blocks, so a Create machine already in the footprint stays in place.
- If an item is missing, pause with `OUT_OF_MATERIALS` and check the chests again every 5 seconds. Resume also clears the pause when the item is present.

The finished build only changes blocks inside the schematic bounds, in the anchor's dimension. Temporary blocks outside that footprint are put back before the job stops.

## Villager behavior that must keep working

- Death, damage, and vanilla drops still happen.
- Food pickup and eating still happen.
- Breeding still happens, and the baby has no worker attachment.
- Idle and stopped workers still wander, trade, and sleep.
- Working and coming workers do not path to a bed.
- No experience levels, no job skill, no better tools from practice.

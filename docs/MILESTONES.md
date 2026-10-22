# Milestones

Issues to file for Villager Workers. Each one lists the title, scope, and acceptance criteria. The behavior they refer to is specified in [DESIGN.md](DESIGN.md).

M1 through M5 are implemented. M5 is crews. Trusted players can operate a worker, but breaking, placing, and opening a linked container use the owner's claim and lock rights. See Ownership in [DESIGN.md](DESIGN.md).

Order is M1, then M2, then M3. Later issues may persist fields that an earlier issue defined, but they must not start a later milestone's job AI early.

## M1 - Hiring, ownership, permissions, worker GUI, and chat

A named adult villager can be hired, shared, and commanded. The screen can save a job and its setup. No block is broken and nothing is built. Start, stop, and come only change the saved activity.

### Persist worker data on villagers

**Title:** Persist worker data on villagers

**Scope:** Add `WorkerData` and its codec in `common`, and register a NeoForge data attachment from the NeoForge package. Cover every field in the design: owner, trusted players, job, activity, rested, slept-last-night, home bed, the three job configs, the break whitelist, and the current problem. Do not default-construct the attachment onto villagers that were never hired.

**Acceptance criteria:**

- A hired villager reloads from a saved world with the same owner, trusted set, job, configs, activity, and bed.
- A villager who was not hired has no worker attachment after a reload.
- Dying does not copy worker data onto another entity.
- A baby bred from one or two workers has no worker attachment and no worker screen.
- `common` sources do not import NeoForge.

### Hire a villager with a name tag

**Title:** Hire a villager with a name tag

**Scope:** After a player name-tags an adult villager who is not a worker, open the hire screen for that player. Named leaves the villager as a normal named villager. Worker creates the attachment and sets that player as owner. Remember the pending hire on the server for 60 seconds and reject a stale or forged choice.

**Acceptance criteria:**

- Name-tagging an adult who is not a worker opens a screen with Named and Hire as worker.
- Named writes no attachment. Another name tag opens the screen again.
- Hire as worker sets the naming player as owner, job `NONE`, activity `IDLE`, and an empty trusted set.
- A name tag on a baby renames the baby and does not open the screen.
- A name tag on an existing worker renames them and does not change the owner.
- A hire packet is rejected when the sender has no pending hire, the villager UUID does not match, the sender is more than 6 blocks away, or the pending hire is older than 60 seconds.

### Share control and transfer ownership

**Title:** Share control and transfer ownership

**Scope:** Let the owner add an online player to the trusted set, remove a trusted player, and transfer ownership. Trusted players can operate the worker and cannot change who is trusted or who owns it. Store UUIDs, resolved by the server from the online name.

**Acceptance criteria:**

- The owner can add a player who is online, and that player can open the worker screen.
- Adding an offline or unknown name is rejected and the trusted set does not change.
- A trusted player cannot add or remove trusted players and cannot transfer ownership, including by sending the payload directly.
- Transfer requires the new owner to be online in the same dimension. After it succeeds, only the new owner can change ownership, and the previous owner is not trusted unless the new owner adds them.
- Another player cannot transfer ownership or edit the trusted set.

### Worker screen, job choice, and saved setup

**Title:** Worker screen, job choice, and saved setup

**Scope:** Add the worker screen for the owner and trusted players. Show name, health, activity, job, rested, and the problem line. Let them pick a job and press Start, Stop, and Come. Save lumberjack, miner, and builder setup, including the area wand and chest links. Do not run job AI. Builder setup can be saved when Create is missing; starting that job is rejected until M3.

**Acceptance criteria:**

- Owner and trusted players open the screen with right-click. Sneak-right-click still opens the vanilla villager interaction.
- Any other player gets the vanilla interaction and cannot open the screen by packet.
- Start, Stop, and Come persist `WORKING` (or `IDLE` when no job is selected), `STOPPED`, and `COMING`. Reloading the world keeps that activity. No blocks change.
- The area wand's two clicks save a lumberjack or clear-box area. One click saves a tunnel, branch, or shaft origin. The wand does not break or place blocks.
- Linking accepts only a chest, trapped chest, or barrel in the worker's dimension, within 8 blocks. A Create block cannot be linked.
- Setup survives a job switch and a world reload.
- The permissions tab is visible to the owner only. It can add, remove, and transfer as specified in the ownership issue.

### Reject unauthorized worker actions on the server

**Title:** Reject unauthorized worker actions on the server

**Scope:** Put the role check in `common` as a pure function of worker data, actor UUID, and action. Call it from every menu button and every mutating payload. Do not trust the client to hide buttons. Do not skip the check in singleplayer.

**Acceptance criteria:**

- Owner-only actions (trusted list, transfer, permissions snapshot) fail for a trusted player and for any other player.
- Operate actions (job, setup, start, stop, come, wand, chests) fail for a player who is neither owner nor trusted.
- A payload fails when the sender is more than 6 blocks away or does not have the worker menu open, except the hire choice, which uses the pending-hire rules.
- A denied action does not change saved data. The sender gets a one-line denial.
- A plain JUnit test covers the role matrix without launching Minecraft.

### Chat commands

**Title:** Chat commands for come, stop, resume, and status

**Scope:** Parse `@Name come`, `@Name stop`, `@Name resume`, and `@Name status` on the server. Match the villager's custom name exactly, including spaces, and take the longest name when one name is a prefix of another. Do not cancel the original chat line.

**Acceptance criteria:**

- Owner and trusted players can run all four verbs on a loaded worker in their dimension. `come`, `stop`, and `resume` change activity the same way as the screen buttons. `status` replies with job, activity, rested, health, and the current problem.
- A name with spaces matches only when the verb comes after the full name. The longest matching name wins.
- Two loaded workers with the same name produce an ambiguity reply and no activity change.
- A player who is not owner or trusted gets `You cannot command <name>.` and the activity does not change.
- A chat line that does not start with `@`, or that does not match a worker name, is left alone.
- An unknown verb from someone who may command that worker gets the usage reply.

### Track rest, the home bed, and hunger or injury

**Title:** Track rest, the home bed, and hunger or injury

**Scope:** Record sleep and the home bed. Notify the owner when the worker is hungry or hurt, using the rate limit and offline delivery in the design. Do not change move or break speed in this milestone. While the worker is `WORKING` or `COMING`, suppress vanilla bed-seeking. While `IDLE` or `STOPPED`, leave vanilla sleep, wandering, and trading in place.

**Acceptance criteria:**

- Finishing a sleep sets `sleptLastNight` and stores that bed as `homeBed`. At the next dawn, `rested` matches whether they slept, and `sleptLastNight` is cleared.
- A worker who never sleeps stays able to be set to `WORKING`. `rested` is false after a dawn they did not earn.
- `homeBed` clears when that block is no longer a bed.
- The owner receives at most one `HUNGRY` line and one `HURT` line per worker per minute. An offline owner receives the latest line on login, not a backlog.
- Hungry and hurt do not stop the saved activity.
- An idle worker can still sleep, trade, pick up food, and breed. A baby from that breeding is not a worker.

## M2 - Lumberjack and miner

Workers with a saved area break only the blocks the safety rules allow, haul drops to linked chests, and work slower when they did not sleep. The builder still does not run.

### Break only safe blocks inside the work volume

**Title:** Break only safe blocks inside the work volume

**Scope:** Implement the break predicate from the design and make it the only way job code destroys a block. Compute the clear-box, tunnel, branch, and shaft volumes from saved setup. Honor the global denylist, block entities, the `create` and `aeronautics` namespaces, the per-worker whitelist, reach, the home bed, and linked chests. `simulated`, `sable`, and `offroad` register world blocks in the Create Aeronautics pack, so they use the same protection as `create` and `aeronautics`. Gate the break on the owner's survival-mode break permission so spawn protection and claim mods can cancel it.

**Acceptance criteria:**

- A worker never breaks a block outside its computed volume, outside 4.5 blocks of itself, or in another dimension.
- Bedrock, barrier, command blocks, structure blocks, jigsaw blocks, portals, and reinforced deepslate are never broken, even if whitelisted.
- A Create or Aeronautics block, and any block entity, is not broken unless that block id is on the worker whitelist. The default whitelist is empty.
- A linked chest and the home bed are not broken.
- If a protection check cancels the break for the owner, the worker does not break it.
- Lumberjack breaks only logs in the box, plus leaves in the box when clear-leaves is on. A log or leaf outside the box stays.

### Lumberjack

**Title:** Cut and replant trees inside the marked box

**Scope:** When a lumberjack is `WORKING`, cut allowed logs in the box, optionally clear leaves, and optionally replant one matching sapling on dirt inside the box. Use an axe from the tool chest. Put logs in the log chest and spare saplings in the sapling chest. Pause on a missing tool, a missing chest, or a full chest.

**Acceptance criteria:**

- With replant off and clear-leaves off, only logs inside the box are removed, and no sapling is placed.
- With both options on, leaves inside the box are cleared and one matching sapling is planted on dirt inside the box, taken from the sapling chest.
- Nothing is planted or broken outside the box.
- Logs land in the log chest. A full chest pauses the job, messages the owner, and does not delete the drops.
- A missing or nearly broken axe pauses the job and messages the owner. Stopping the worker returns the axe to the tool chest when there is room.
- `STOPPED` and `COMING` do not break blocks. `resume` or Start continues the same box.

### Miner modes and the valuables filter

**Title:** Mine a box, tunnel, branch mine, or shaft

**Scope:** When a miner is `WORKING`, break allowed blocks in the volume for the selected mode, from the top down for a clear box. Tunnel and branch may place the optional wall block from the regular chest on the sides and ceiling. Route drops by the valuables list. Use a pickaxe from the tool chest.

**Acceptance criteria:**

- Clear box removes allowed blocks inside the box and leaves protected blocks in place.
- Tunnel digs a corridor of the saved width, height, and length. With a wall block set, sides and ceiling are filled from the chest and the floor is not. With no wall block, it only digs.
- Branch adds left and right branches at the saved spacing and length, and does not branch in the first spacing interval.
- Shaft digs the saved square cross-section for the saved depth, down unless shaft-up is set.
- Items on the valuables list go to the valuables chest. Other drops go to the regular chest. The default list matches the design, and an edited list is what the worker uses after a reload.
- A Create machine inside the volume survives unless its block id is whitelisted.
- The job pauses and messages the owner when the pickaxe is missing or about to break, or when a destination chest is missing or full.

### Rested speed, hauling, and work problems

**Title:** Apply the rested buff and report work problems

**Scope:** Use the rested multipliers from the design while a lumberjack or miner is working. Path `COMING` to within 2 blocks of the commanding player. Detect stuck workers. Send `BROKEN_TOOL`, `OUT_OF_MATERIALS`, `MISSING_CHEST`, `CHEST_FULL`, and `STUCK` to the owner with the same rate limit as M1.

**Acceptance criteria:**

- A rested worker waits 10 ticks between actions and uses the 1.25 break multiplier. An unrested worker waits 20 ticks and uses 0.75. Both still complete the job.
- `come` from chat or the screen brings the worker to within 2 blocks of that player and does not keep breaking blocks on the way.
- A worker that cannot move toward its target for 10 seconds retries once, then pauses and tells the owner.
- Wall blocks and saplings that are not in the linked chests pause the job with `OUT_OF_MATERIALS` and name the item.
- Problem lines follow the once-per-minute limit and do not go to trusted players or to the public chat as a broadcast.

## M3 - Builder

The worker places a Create schematic. It still does not break blocks to do that.

### Link a placed Create schematic

**Title:** Link a placed Create schematic

**Scope:** Add an optional dependency on Create `[6.0,)`. The mod still loads when Create is absent. From builder setup, link a placed schematicannon that already has a schematic. Spike the Create 6.0 schematic API before writing the adapter, and keep Create calls out of `common`.

**Acceptance criteria:**

- `./gradlew build` succeeds with and without Create on the runtime classpath. The published dependency is optional.
- Without Create, the builder tab explains that Create is required, and Start does not set the worker to building.
- With Create, the owner or a trusted player can link a schematicannon in range that contains a schematic. A cannon with no schematic is rejected.
- Linking does not start the cannon and does not spend its fuel.
- The link reloads with the world.

### Build the schematic from linked chests

**Title:** Build the schematic from linked chests

**Scope:** When a builder is `WORKING` and a schematic is linked, place missing blocks from the bottom up, inside the schematic bounds, using items from the linked material chests. Skip positions that already match. Skip solid blocks that do not match, and tell the owner. Never break a block. Apply schematic block-entity data only if the API spike showed that is safe.

**Acceptance criteria:**

- Air and other replaceable positions inside the bounds become the schematic block when a matching item is in a material chest.
- A position that already matches is not replaced and costs no item.
- A solid wrong block is left in place, including a Create or Aeronautics machine, and the owner gets a `BLOCKED` notice for that position.
- No block outside the schematic bounds is placed or broken.
- A missing item pauses the job with `OUT_OF_MATERIALS`. The worker continues on its own when the item is in a material chest, and Resume does the same.
- Stopping the worker stops placement. Resume continues the remaining positions.
- A baby, death, food, and breeding still behave as in M1 while a builder is working.

## M4 - Hard hats and foremen

Hiring a villager shows a yellow hard hat. The owner can promote that worker to a foreman or demote a foreman. A foreman's hat is orange. A foreman also holds a clipboard and carries a rolled blueprint. The rank is saved. The trusted list is still not synced to other players.

### Show a hard hat and a foreman

**Title:** Show a hard hat and let the owner promote a foreman

**Scope:** When a villager is hired, store rank `worker` and sync only that rank so clients can draw gear. Add a yellow hard hat model. Add Promote to Foreman and Demote on the worker screen. A foreman uses an orange hat, a clipboard in the hand, and a rolled blueprint under one arm. Demote returns the yellow hat and removes the clipboard and blueprint. Do not change job AI.

**Acceptance criteria:**

- A new worker's saved rank is `worker`. A save from 0.4.9 with no rank loads as `worker`.
- Promote sets rank `foreman`. Demote sets it back to `worker`. The change reloads with the world.
- The screen shows Promote to Foreman on a worker and Demote on a foreman.
- A player who cannot edit the worker cannot promote or demote, including by menu button.
- Clients draw the yellow hat for a worker and the orange hat, clipboard, and blueprint for a foreman. Unhired villagers have no hat.

## M5 - Crews

A worker can be assigned to one of your foremen. The foreman's screen lists unassigned workers and the current crew. Commands can target the foreman, the whole crew, or ticked members. A scaffold rule changed on the foreman is copied onto every member who has not turned on own rules.

### Assign a crew and command it

**Title:** Assign workers to a foreman and command the crew

**Scope:** Store one foreman UUID on the worker. From the worker screen, pick a foreman by name, or leave the crew. From the foreman screen, add an unassigned worker, unassign, move to another foreman, call one member, give sole ownership of a ticked member, and run come, stay, and work for the foreman only, the whole crew, or the ticked members. Changing the scaffold order or the plain-blocks flag on the foreman updates every crew member in that same action, except members with own rules on. Do not run scaffold placement yet.

**Acceptance criteria:**

- A worker stores one foreman or none. A foreman cannot be assigned as a member. An older save with no foreman field stays unassigned.
- Assign, unassign, and move change only that link, and only for a player who may edit both villagers.
- Come, stay, and work accept foreman only, whole crew, and ticked members. A ticked id that is not on the crew is ignored.
- One scaffold change on the foreman updates every member who has not opted out, and leaves an opted-out member alone.
- The link and the rule reload with the world.

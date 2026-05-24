# MineMind Stage 0 Baseline Analysis

## Scope

This document records the current Steve codebase before MineMind feature work.
Stage 0 does not change runtime behavior. It identifies the existing execution
chain, reusable modules, risk areas, and the safest modification entry points
for the later phased MineMind migration.

Success criteria for this stage:

1. Maven remains the primary build workflow.
2. Current Steve command and GUI behavior is left unchanged.
3. The MineMind implementation plan is grounded in the real source layout.
4. `mvn clean package` passes after the document is added.

## Build Baseline

Steve has been migrated to Maven for this branch. `pom.xml` is the main build
entry point, with the legacy Gradle files retained only as migration reference.

Current Maven flow:

1. `maven-dependency-plugin` copies Forge helper tools and writes the dependency
   classpath.
2. `scripts/prepare-forge-maven.ps1` prepares a Minecraft 1.20.1 official-name
   compile jar from Mojang client/server jars and mappings.
3. `maven-compiler-plugin` compiles the mod against Java 17, Forge, Minecraft,
   and third-party libraries.
4. `maven-surefire-plugin` runs the current tests.
5. `maven-shade-plugin` packages runtime libraries into
   `target/steve-ai-mod-1.0.0.jar`.

The required validation command for all later stages is:

```bash
mvn clean package
```

## Current Runtime Chain

### Mod entry and entity registration

`com.steve.ai.SteveMod` is the Forge mod entry point. It registers:

- mod id `steve`
- `SteveEntity` as entity type `steve`
- common config through `SteveConfig`
- `/steve` commands through `RegisterCommandsEvent`
- client GUI event handling on client dist
- a static `SteveManager`

`SteveEntity` extends `PathfinderMob`. Each entity owns a `SteveMemory` and an
`ActionExecutor`. Server ticks call `actionExecutor.tick()`, so behavior is
driven through the normal Minecraft tick loop.

### Spawn and lifecycle

Manual spawn goes through `/steve spawn <name>`, implemented in
`SteveCommands`, then `SteveManager.spawnSteve`. `SteveManager` tracks Steves by
name and UUID and enforces `SteveConfig.MAX_ACTIVE_STEVES`.

`ServerEventHandler` currently auto-spawns four named Steves on first player
login and clears existing Steve entities and `StructureRegistry` state. That is
useful for demos but is a high-impact behavior for MineMind because it mutates
world state automatically.

### Command input

`SteveCommands` exposes the current command surface:

- `/steve spawn <name>`
- `/steve remove <name>`
- `/steve list`
- `/steve stop <name>`
- `/steve tell <name> <command>`

`tell` resolves the named Steve and forwards natural-language text to
`ActionExecutor.processNaturalLanguageCommand`. The command entry currently
starts a new Java thread, while the executor itself also uses the async planner.
The later MineMind command additions should keep the existing `/steve` behavior
stable and add only narrow subcommands for autonomous mode/status.

### GUI input

`SteveGUI` is a client-side panel opened with the K key. It lets the user select
one Steve or all Steves, then sends vanilla commands such as:

- `steve spawn <name>`
- `steve tell <name> <command>`

This means GUI task input reuses the same command and planning chain as manual
commands. MineMind should preserve this and avoid a second hidden task path.

## LLM System

### Current providers

`TaskPlanner` creates synchronous clients for OpenAI, Groq, and Gemini, plus
async resilient clients for the same providers. Provider selection is driven by
`SteveConfig.AI_PROVIDER`.

Existing limitations:

- all providers currently share `SteveConfig.OPENAI_API_KEY`
- Groq and Gemini async models are hardcoded in `TaskPlanner`
- DeepSeek is not present yet
- constructing `TaskPlanner` requires a non-empty API key because async clients
  validate keys in their constructors

DeepSeek should be added as another provider in the existing LLM package rather
than creating a separate planning subsystem.

### Prompt building

`PromptBuilder` creates:

- a strict JSON system prompt
- action schemas for `attack`, `build`, `mine`, `follow`, and `pathfind`
- examples for common natural-language commands
- a user prompt with current position, nearby players, nearby entities, nearby
  blocks, biome, and the player command

`WorldKnowledge` provides the environmental summary used in this prompt.

### Response parsing and validation

`ResponseParser` extracts JSON, parses `tasks`, and creates `Task` objects from
`action` plus nested `parameters`.

`TaskPlanner` already has `validateTask` and `validateAndFilterTasks`, but the
main async path currently returns parsed tasks without applying that validation
before `ActionExecutor` queues them. This is an important MineMind safety gap:
later phases must validate LLM output before execution and fall back to a rule
planner when parsing or validation fails.

### Async and fallback behavior

`ActionExecutor.processNaturalLanguageCommand` starts
`TaskPlanner.planTasksAsync`, stores the resulting `CompletableFuture`, and
returns immediately. `tick()` polls completion and queues tasks only after the
future is done. This is the main reusable foundation for the requirement that
LLM calls must not block the Minecraft main thread.

The async LLM layer includes:

- `AsyncLLMClient`
- provider clients for OpenAI, Groq, and Gemini
- `ResilientLLMClient` with circuit breaker, retry, rate limiter, bulkhead, and
  cache
- `LLMFallbackHandler` with pattern-based JSON responses
- `LLMCache` using Caffeine

Current fallback responses need attention before relying on them broadly:
several fallback examples use top-level task fields or action names such as
`wait` and `place_block`, while the parser and action executor expect nested
`parameters` and registered action names such as `place`. This should be fixed
before MineMind depends on fallback planning.

## Action System

### Execution model

`ActionExecutor` owns:

- the current action
- a task queue
- idle-follow behavior
- async planning state
- a per-Steve event bus
- an `AgentStateMachine`
- an `InterceptorChain`

Actions extend `BaseAction` and are tick-based. Each action implements
`onStart`, `onTick`, `onCancel`, and `getDescription`. This is the right
execution substrate for MineMind autonomous behavior because new goals can be
converted into existing `Task` objects and submitted to the same executor.

### Registered and legacy actions

The built-in action set is:

- `pathfind`
- `mine`
- `place`
- `craft`
- `attack`
- `follow`
- `gather`
- `build`

`CoreActionsPlugin` can register these in `ActionRegistry`, and
`PluginManager` can discover plugins through Java `ServiceLoader`. However,
there is no startup call currently loading `PluginManager`, so in practice the
legacy switch in `ActionExecutor.createActionLegacy` remains the reliable path.
Later phases can either explicitly initialize plugins or continue using the
legacy fallback until the registry is wired safely.

### Action readiness

Strong reusable actions:

- `PathfindAction` for coordinate movement
- `MineBlockAction` for ore/resource mining behavior
- `PlaceBlockAction` for single block placement
- `CombatAction` for hostile target handling
- `FollowPlayerAction` and `IdleFollowAction`
- `BuildStructureAction` for template/procedural structures

Partial or placeholder actions:

- `CraftItemAction` returns "Crafting not yet implemented"
- `GatherResourceAction` returns "Resource gathering not yet fully implemented"

MineMind autonomous survival should not depend on crafting or generic gathering
until those actions are implemented or mapped to safer existing actions.

## Memory and World Knowledge

`SteveMemory` currently stores:

- current goal
- recent action descriptions, capped at 20
- NBT save/load for current goal and recent actions

It does not yet store long-term observations, player guidance, discovered
locations, roles, preferences, or shared community state.

`WorldKnowledge` scans a fixed radius around a Steve and summarizes:

- biome
- nearby non-air blocks
- nearby entities
- nearby player names

The scan is bounded and samples blocks every two blocks, which is a usable base
for Stage 2 world observation. It needs structured fields for health, hunger,
dimension, time of day, danger level, inventory, current action, current goal,
and recent failures.

`StructureRegistry` stores built structure bounds in memory to avoid overlap.
It is not persisted and is currently cleared by `ServerEventHandler` on first
login.

## Multi-Agent and Collaboration

Existing multi-agent support is centered on:

- `SteveManager` tracking multiple entities
- GUI support for targeting one Steve or all Steves
- `CollaborativeBuildManager`
- `BuildStructureAction`

`CollaborativeBuildManager` divides a structure plan into quadrants, assigns
Steves to sections, tracks progress with atomic counters, and keeps active
builds in a concurrent map. This is the strongest reusable foundation for
MineMind community task assignment because it already prevents duplicate work
inside one collaborative build.

It does not yet provide general roles, a task board, shared memory, or agent
communication. Those should be layered on top rather than replacing the current
build collaboration.

## Code Execution System

`CodeExecutionEngine` and `SteveAPI` provide a GraalVM JavaScript sandbox and a
limited API bridge. Current source search found no active runtime path from the
LLM planner to this engine.

For MineMind, this should remain disabled unless a later phase explicitly
requires it. The stated requirement is that LLM output must become validated
structured plans, not arbitrary code execution.

## Reusable Modules

The main modules to reuse are:

- `SteveEntity` for per-agent lifecycle and tick integration
- `SteveManager` for spawn/list/remove and multi-agent lookup
- `SteveCommands` for command entry points
- `SteveGUI` for preserving GUI-driven task mode
- `ActionExecutor` and `BaseAction` for tick-based execution
- existing action classes where behavior is already implemented
- `Task`, `ActionResult`, and `TaskPlanner.validateTask` as the action contract
- `WorldKnowledge` as the first observation layer
- `SteveMemory` as the per-agent memory anchor
- `CollaborativeBuildManager` for non-conflicting multi-agent build work
- async LLM clients and `ResilientLLMClient` for non-blocking provider calls

## High-Risk Areas

1. LLM output is parsed but not consistently validated before task execution.
2. Fallback JSON does not fully match the parser/action schema.
3. Provider config is shared and not yet suitable for DeepSeek or per-provider
   API keys/models.
4. `ServerEventHandler` auto-spawns and clears world Steve state on login.
5. `PluginManager` exists but does not appear to be initialized.
6. `AgentStateMachine` and `InterceptorChain` are constructed but not deeply
   integrated into action start/complete transitions.
7. Long-term memory is limited to entity NBT fields and recent action strings.
8. Crafting and generic gathering are placeholders.
9. README still describes some intended capabilities that are ahead of the
   implementation, such as persistent memory and several action names.
10. Existing tests are mostly placeholder tests, so build success does not yet
    prove behavior correctness.

## Recommended MineMind Entry Points

Stage 1 should add only configuration and command control:

- extend `SteveConfig` with MineMind feature flags, defaulting off
- add minimal `/steve` subcommands for autonomous mode on/off/status
- store per-Steve autonomous state without changing normal command/GUI flows
- update README with the new config/command surface

Stage 2 should extend observation without changing action execution:

- build a structured MineMind observation object using `WorldKnowledge` plus
  safe, bounded extra fields
- expose a debug command to inspect the observation
- keep scans low-frequency and radius-bounded

Stage 3 and Stage 4 should introduce rule goals and a rule planner first:

- generate candidate goals from observation
- select one goal with survival priority rules
- convert selected goals into existing `Task` objects
- submit tasks only when Steve is idle and autonomous mode is enabled

LLM-based autonomous planning, DeepSeek, persistent memory, and community
coordination should come after the rule planner is stable, because each depends
on validated task schemas and fallback behavior.

## Technical Risks for Later Phases

- Threading: all LLM calls must stay off-thread, but task queue mutation and
  Minecraft world mutation must remain tick-side.
- Chat volume: chat guidance and agent communication need cooldowns and
  importance filters.
- Persistence: file writes for long-term memory need throttling and lifecycle
  hooks, not per-tick writes.
- Safety: DeepSeek and other providers must return structured plans only; no
  direct Minecraft API calls and no arbitrary code execution.
- Coordination: community task assignment needs explicit ownership, leases, and
  failure states to avoid duplicated work.
- Compatibility: original `/steve tell`, GUI commands, idle-follow, and
  collaborative building must keep working when MineMind flags are off.

## Stage 0 Conclusion

Steve already has the essential pieces for a gradual MineMind migration:
entities, commands, GUI input, async LLM planning, tick-based actions, bounded
world summaries, short-term memory, and collaborative building. The first
feature stages should be conservative: add disabled-by-default MineMind state
and command controls, then structured observation, then rule-based autonomous
goals. LLM planner expansion and DeepSeek should wait until validation and
fallback schemas are made reliable.

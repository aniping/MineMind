# MineMind

MineMind is a phased Maven migration and extension of the original Steve
Minecraft Forge mod. The original Steve command mode, GUI task input, natural
language planning, action system, memory, and multi-Steve collaboration remain
in place while MineMind adds opt-in autonomous agent behavior.

Current MineMind status:

- Maven is the only active build entry point.
- Legacy Gradle wrapper and Gradle build files have been removed.
- Original `/steve` commands still work.
- The K-key GUI still sends natural language tasks through the existing LLM
  task planner.
- MineMind autonomous features are disabled by default.
- A single Steve can be enabled for low-frequency rule-based autonomy.
- The current autonomous loop observes the world, records a bounded memory
  summary, selects a rule goal, converts it to existing Steve tasks, and falls
  back to a bounded wait task when no safe task is available.
- DeepSeek and LLM-based MineMind planning are planned later phases and are not
  required for the current rule-based autonomous loop.

## Requirements

- Minecraft 1.20.1 with Forge
- Java 17
- Maven
- An LLM API key only if you use the original natural language command or GUI
  task mode

Do not commit API keys. Keep keys in local config or pass them at runtime.

## Build

From the project root:

```bash
mvn clean package
```

The packaged mod JAR is written to:

```bash
target/steve-ai-mod-1.0.0.jar
```

The Maven build compiles Java sources, prepares the Forge/Minecraft compile
classpath, processes resources, runs tests, and packages the mod JAR. On a
fresh machine it may download Minecraft 1.20.1 client/server artifacts, Mojang
mappings, Forge helper tools, and Maven dependencies into the local Maven
repository.

Maven is the sole build path in this branch.

## Run In Minecraft

### Client Smoke Test

1. Build with `mvn clean package`.
2. Copy `target/steve-ai-mod-1.0.0.jar` into a Minecraft 1.20.1 Forge `mods`
   directory.
3. Launch Minecraft with the Forge profile.
4. Configure `config/steve-common.toml` after Forge generates it, or copy and
   adapt `config/steve-common.toml.example`.
5. Create or open a world, then run the basic commands below.

### Dedicated Server Smoke Test

Use a Forge server that matches the mod target: Minecraft `1.20.1`, Forge
`47.2.0`.

1. Build with `mvn clean package`.
2. Install a Forge `1.20.1-47.2.0` dedicated server in a separate run
   directory.
3. Accept the Minecraft EULA for that server directory.
4. Copy `target/steve-ai-mod-1.0.0.jar` into the server `mods` directory.
5. Copy or adapt `config/steve-common.toml.example` into the server `config`
   directory.
6. Start the server from the Forge server directory:

```powershell
.\run.bat --nogui
```

If the Forge installer did not create `run.bat`, use the generated Forge args
file instead:

```powershell
java @user_jvm_args.txt @libraries/net/minecraftforge/forge/1.20.1-47.2.0/win_args.txt nogui
```

Then join the server from a Minecraft 1.20.1 Forge client with the same mod JAR
installed.

## Basic Commands

```bash
/steve spawn Bob
/steve list
/steve tell Bob mine 20 iron ore
/steve stop Bob
/steve remove Bob
```

Press `K` in-game to open the existing Steve GUI and submit natural language
tasks.

## MineMind Commands

MineMind autonomous behavior is opt-in per Steve:

```bash
/steve minemind enable Bob
/steve minemind disable Bob
/steve minemind toggle Bob
/steve minemind status Bob
/steve minemind observe Bob
/steve minemind goals Bob
```

`observe` prints a bounded world snapshot with position, health, dimension,
time, biome, nearby entities/resources, current goal, current action, recent
failures, and a rule-based danger level.

`goals` generates MineMind candidate goals from the current observation and
shows the selected rule-based goal. The command itself is a debug view and does
not enqueue actions.

When autonomous mode is enabled, the tick loop only runs while the existing
`ActionExecutor` is idle. It does not call an LLM, does not block the Minecraft
main thread with network requests, and does not spam chat.

If `enableChatGuidance` is true, players can address a Steve by name in normal
Minecraft chat:

```text
Bob, what are you doing?
Bob, why are you doing that?
Bob, next plan?
Bob, remember this is the base
Bob, don't go east
Bob, stop
Bob, continue autonomous exploration
Bob, gather wood
```

Common questions are answered with rule-based replies. Guidance such as marked
locations, preferences, danger warnings, and avoid-direction notes is written to
Steve memory and can influence later MineMind goal selection. Clear task-like
messages reuse the existing non-blocking natural language task planner.

## Configuration

Example:

```toml
[ai]
provider = "groq"

[openai]
apiKey = ""
model = "gpt-4-turbo-preview"
maxTokens = 8000
temperature = 0.7

[behavior]
actionTickDelay = 20
enableChatResponses = true
maxActiveSteves = 10

[minemind]
autonomousModeDefault = false
enableChatGuidance = false
enableCommunityMode = false
enableLongTermMemory = false
thinkIntervalTicks = 200
maxPlanningSteps = 3
useLlmPlanner = false
chatResponseCooldownTicks = 100
```

All MineMind options default to disabled or rule-based behavior so existing
Steve command and GUI workflows keep their current behavior unless explicitly
enabled.

## Current Architecture

Main packages:

```text
src/main/java/com/steve/ai/
  action/       Tick-based action execution and action classes
  client/       K-key GUI overlay
  command/      Minecraft commands
  config/       Forge config definitions
  entity/       Steve entity, spawning, lifecycle, MineMind tick hook
  llm/          Existing LLM clients, prompt building, response parsing
  memory/       Steve memory and world knowledge
  minemind/     MineMind state, observation, goal selection, autonomous loop
  plugin/       Action plugin registry
  structure/    Structure templates and procedural generation
```

MineMind currently reuses:

- `SteveEntity` for entity lifecycle
- `ActionExecutor` and existing `Task` objects for execution
- `WorldKnowledge` for bounded observation
- `SteveMemory` for recent actions, failures, and observation summaries
- Existing command registration under `/steve`

## Validation

Every MineMind stage must pass:

```bash
mvn clean package
```

The build must produce `target/steve-ai-mod-1.0.0.jar`.

The Maven gate verifies compilation, resources, tests, Forge/Minecraft
classpath preparation, and JAR packaging. A live in-game smoke test is separate:
start a Forge client or dedicated server, join the world, spawn a Steve, and run
the commands in the next checklist.

Runtime smoke checklist:

```bash
/steve spawn Bob
/steve list
/steve minemind status Bob
/steve minemind observe Bob
/steve minemind goals Bob
/steve minemind enable Bob
/steve minemind disable Bob
/steve stop Bob
```

Expected result: the commands return without server errors, `observe` and
`goals` print summaries, and enabling MineMind only starts autonomous behavior
when Bob is idle.

## Roadmap

Planned phases continue from the staged MineMind goal document:

- Chat guidance and player instruction memory
- Longer-term persisted memory
- DeepSeek provider support
- Safe LLM planner with validation and rule fallback
- Multi-agent roles, task board, shared memory, and communication
- Community-level autonomous strategy

## Credits

MineMind is based on the Steve Minecraft AI mod and preserves its original
command, GUI, LLM, action, memory, and multi-agent foundations while adding the
new autonomous community simulation features in phases.

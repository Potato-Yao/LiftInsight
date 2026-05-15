# Potato Hand-Coded Style Skill

This document captures the recurring coding style and architecture patterns found in `multimeter-engine` and `ClinicAssistant`.

Use it as a prompt or style guide for another LLM when you want code that feels hand-written, pragmatic, and easy to extend.

## Intent

Write code like one careful engineer built it over time:

- feature-first, not framework-first
- direct and practical, not over-abstracted
- organized by responsibility
- readable from top to bottom
- tolerant of partial failure when the app can keep running
- explicit about platform boundaries, external tools, and runtime assumptions

## High-Level Style

Prefer small, concrete modules with obvious jobs.

Avoid architecture that is more general than the current product needs.

Keep important flows visible in one place. Do not hide core behavior behind deep indirection unless reuse is clearly needed.

Favor code that reads like this:

1. validate or initialize
2. do the real work
3. map the result into app-facing data
4. return early on failure

The overall feel should be closer to a well-organized personal engineering project than a corporate framework template.

## Module Organization

Organize modules by domain and runtime role, not by pattern names alone.

Good examples from the source projects:

- `multimeter-engine/src/monitor/`: hardware monitoring domain
- `multimeter-engine/src/web/`: request protocol and request execution
- `multimeter-engine/src/util/`: small shared data helpers
- `multimeter-engine/src/external_program/`: process and tool wrappers
- `ClinicAssistant/Kernel/.../Hardware/`: hardware domain model and manager
- `ClinicAssistant/Kernel/.../Software/`: OS and software utilities
- `ClinicAssistant/Kernel/.../External/`: wrappers around external programs
- `ClinicAssistant/Desktop/.../Controller/`: UI behavior per window/frame
- `ClinicAssistant/Desktop/.../Utils/`: small UI helpers

Rules:

- Put related files together by feature area.
- Keep platform-specific code isolated in platform-specific modules.
- Keep protocol/request parsing separate from business logic.
- Keep UI code separate from system/tool access code.
- Keep data model classes/structs simple and near the domain they describe.
- Use a small central config/constants module instead of scattering magic values.

## Architecture Pattern

Use a thin layered architecture:

1. Entry point layer
2. Feature/domain layer
3. Integration/tool layer
4. Data model layer

### Entry Point Layer

Entry points should stay small.

They should mainly:

- configure logging
- parse startup arguments
- initialize core services
- start the server, loop, or UI

Examples:

- `multimeter-engine/src/main.rs`
- `ClinicAssistant/Desktop/src/main/java/com/potato/desktop/MainApp.java`

### Feature/Domain Layer

This layer owns the real behavior.

Examples:

- `monitor/mod.rs` coordinates hardware updating
- `HardwareInfoManager.java` coordinates sensor mapping and refresh
- `MainFrameController.java` coordinates UI state refresh and feature actions

This layer may hold state, threads, schedulers, or singleton-style managers when appropriate.

### Integration/Tool Layer

Wrap external processes, sockets, OS commands, or third-party tools in their own modules.

Examples:

- `LHMHelper.java`
- `ExternalTools.java`
- `program.rs`

Rules:

- keep ugly protocol/process details here
- expose a narrower app-facing API upward
- make filesystem and runtime assumptions explicit
- produce good failure messages when the tool is missing or exits early

### Data Model Layer

Use plain classes/structs for domain data.

Examples:

- `hardware_model.rs`
- `CPU`, `GPU`, `Battery`, `RAM` classes in `ClinicAssistant`

Rules:

- keep fields direct and obvious
- add tiny derived helpers near the data if they improve usage
- avoid moving business orchestration logic into value objects

## Naming Style

Names should be literal and domain-driven.

Prefer:

- `HardwareInfoManager`
- `CrossPlatform`
- `RequestKind`
- `ExternalTools`
- `updateMonitorData`
- `parseSystemInfo`

Avoid names that are too generic or fashionable, such as:

- `BaseServiceFactory`
- `AbstractProviderManager`
- `ProcessorPipelineAdapter`

Rules:

- managers manage
- helpers wrap messy integrations
- controllers drive UI actions
- config stores constants
- model/data/container names describe payload shape clearly

## Readability Through Control Flow

This author strongly favors explicit `if` blocks and staged logic over compressed expressions.

Write conditionals so the reader can scan them quickly.

### Prefer Early Checks

Check obvious invalid states first.

Examples of the style:

- if not initialized, initialize
- if dependency is unavailable, degrade gracefully
- if a tool/path/socket is missing, fail with a clear message
- if a value is absent, skip that update

### Keep Branches Flat When Possible

Prefer:

```text
if bad state {
    return error
}

if optional thing exists {
    use it
}

continue main flow
```

Over deeply nested branching.

### Use Simple Repeated `if` Blocks for Independent Updates

When setting many fields from optional sensor indexes or optional values, it is acceptable to use many separate `if` statements instead of clever loops or maps.

This style is visible in `HardwareInfoManager.update()` and keeps each assignment easy to inspect.

### Match or Switch Only When It Clarifies a Closed Set

Use `match`/`switch` when handling a known enum-like set such as request kinds, versions, or pane choices.

Do not force everything into pattern matching if plain `if` reads better.

## Readability Through Blank Lines

Whitespace is used as structure, not decoration.

Apply these rules:

- separate imports/package/module declarations from code
- separate field declarations from constructors and methods
- put a blank line between validation, processing, and return phases
- put a blank line before a new conceptual step inside a method
- avoid giant uninterrupted walls of statements

Within a method, group statements like this:

1. acquire inputs/dependencies
2. validate prerequisites
3. perform the core operation
4. map/update fields
5. finalize/return

If a method is long, blank lines should reveal those phases without needing extra comments everywhere.

## Comment Style

Comments are used sparingly, but when present they are practical.

Common comment types in these projects:

- short intent comments before a tricky block
- runtime/environment notes
- warnings around generated code
- sample output documentation for parsing code
- packaging/distribution notes in build scripts

Rules:

- comment the reason, assumption, or protocol
- do not comment obvious syntax
- if code is generated, say so loudly and near the generated block
- if behavior depends on platform/runtime packaging, explain that directly

## Error Handling Style

Use straightforward error handling.

Rules:

- fail fast during required initialization
- degrade gracefully in optional UI features
- include concrete context in error messages
- do not swallow integration failures silently unless the app can truly continue
- if the app can continue, disable only that feature and keep the rest alive

The `ClinicAssistant` UI code is a strong example here: windows, disk, external tools, and hardware monitor are initialized independently so one failure does not kill the whole app.

## State and Lifecycle Style

This coding style accepts simple global/singleton-style state when it matches the app shape.

Examples:

- `OnceLock`, `LazyLock`, `Mutex` in Rust
- singleton-like `getX()` accessors in Java

Use this style when:

- there is one real process-wide manager
- initialization is expensive or stateful
- the app is desktop/server-like rather than library-pure

But keep the boundary clear:

- one obvious owner per shared subsystem
- one obvious init path
- one obvious shutdown/close path if needed

## Data Design Style

Prefer simple domain objects with mostly public/direct fields or plain getters/setters.

Derived calculations can live next to the data when small and obvious, for example:

- battery health percentage
- remaining capacity percentage
- RAM used percentage

Do not introduce builders, visitors, mappers, DTO layers, or heavy generic abstractions unless the codebase genuinely needs them.

## Integration Style

External tools are treated as real dependencies, not hidden implementation details.

Rules:

- centralize tool path resolution
- make working directory assumptions explicit
- handle spaces in file paths correctly
- log or surface wrapper/tool startup failures clearly
- keep low-level packet/socket/process logic in a dedicated wrapper

When wrapping a protocol:

- keep command encoding/decoding local to the wrapper
- expose small methods like `connect`, `update`, `getValue`, `disconnect`
- make protocol constants visible and named

## Build and Packaging Style

The author cares about code running outside the IDE.

Reflect that by:

- encoding runtime paths deterministically
- documenting packaging assumptions in build files
- adding build-time tasks for dependent external tools when needed
- preferring distribution layouts that are easy to reason about

This is especially visible in `ClinicAssistant/Desktop/build.gradle.kts`.

## What To Avoid

Do not generate code in these styles:

- architecture astronaut code
- excessive interface layering without multiple implementations
- dependency injection frameworks for small desktop/server apps
- tiny one-line helper methods that fragment the main flow
- dense functional chains when plain statements are clearer
- generic abstractions before there is proven duplication
- hiding platform-specific behavior behind vague names

Also avoid making the code feel AI-generated by over-normalizing everything into uniform patterns.

## Common LLM Mistakes To Avoid

Another LLM should be explicitly told not to make these mistakes:

- Do not repeat the same logic in multiple places when one clear implementation is enough.
- Do not create multiple helpers that each wrap only one call and make the main flow harder to follow.
- Do not over-abstract early with factories, adapters, service layers, repositories, or strategy objects unless the code already has real variation that needs them.
- Do not split one coherent feature across too many tiny files.
- Do not introduce generic utility functions for behavior that is only used once.
- Do not convert simple sequential logic into clever pipelines just to look concise.
- Do not add configuration objects, builder patterns, or dependency injection unless the existing codebase already relies on them.
- Do not write comments that restate the code.
- Do not duplicate validation, fallback handling, or formatting logic across branches if they can be shared without hiding the main behavior.
- Do not turn straightforward `if` blocks into nested ternaries, dense chaining, or callback-heavy control flow.
- Do not produce boilerplate "enterprise" naming that hides the real domain.
- Do not make all modules symmetrical if the real responsibilities are not symmetrical.
- Do not invent extension points for future use without evidence that they are needed now.
- Do not pad the code with extra wrappers, aliases, or pass-through methods that add no meaning.
- Do not try to make every part of the code equally reusable; optimize first for clarity in the current product.

When choosing between duplication and abstraction, prefer:

- one obvious shared implementation when the duplication is truly the same
- otherwise, a little repetition over a bad abstraction

When choosing between compactness and readability, prefer readability.

## Preferred Output Shape For Another LLM

When asked to write code in this style, follow these instructions:

1. Organize files by feature/domain first.
2. Keep entrypoints thin and move real work into focused modules.
3. Use direct names that reflect the problem domain.
4. Prefer explicit `if` blocks, early checks, and flat control flow.
5. Use blank lines to separate phases of thought inside a method.
6. Keep data models simple and close to their usage.
7. Wrap external tools, sockets, commands, and OS quirks in dedicated helper modules.
8. Allow partial failure in optional subsystems instead of crashing the whole app.
9. Add comments only for protocol notes, runtime assumptions, generated code, or non-obvious reasoning.
10. Choose the smallest architecture that cleanly supports the feature.

## Compact Prompt Version

Use this prompt directly with another LLM:

```text
Write code in the style of a careful solo engineer.

Organize modules by feature and responsibility, not by abstract patterns. Keep entrypoints thin. Put real behavior in focused managers/controllers/modules. Keep data models simple and direct. Wrap external tools, OS commands, sockets, and packaging/runtime quirks in dedicated helper modules.

Prefer explicit control flow: early checks, clear if-blocks, flat branches, and visible phases separated by blank lines. Use match/switch only for true closed sets like enums or request types. Do not over-abstract. Do not introduce framework-heavy architecture, unnecessary interfaces, or generic helpers unless duplication clearly demands it.

Do not repeat the same logic in multiple places. Share code only when the shared behavior is genuinely the same. Otherwise keep a little local repetition instead of introducing bad abstractions. Do not split the code into too many tiny helpers or files. Do not add builders, factories, dependency injection, or extension points unless the existing codebase clearly needs them.

Allow optional subsystems to fail independently when the application can continue. Emit concrete error messages. Comment only when explaining a protocol, generated code, runtime assumption, or non-obvious reasoning.

Aim for code that feels hand-written, pragmatic, readable, and maintainable.
```

## Source Basis

This skill was derived primarily from patterns repeatedly visible in:

- `multimeter-engine/src/main.rs`
- `multimeter-engine/src/lib.rs`
- `multimeter-engine/src/monitor/*`
- `multimeter-engine/src/web/*`
- `multimeter-engine/src/external_program/*`
- `ClinicAssistant/Kernel/src/main/java/com/potato/kernel/**/*`
- `ClinicAssistant/Desktop/src/main/java/com/potato/desktop/**/*`
- `ClinicAssistant/Desktop/build.gradle.kts`

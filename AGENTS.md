# LiftInsight Project Guide

Read this file before making product, UI, or architecture changes.

## Project Idea

`LiftInsight` is an Android app for weightlifters.

The product should help lifters do two main things:

1. record and manage their training plan
2. analyze lifting motion to improve form and technique

The app is not just a workout log. It should connect planning and motion analysis so users can understand both **what they intended to train** and **how they actually moved**.

## Target Users

The primary users are weightlifters and strength-training athletes who want to:

- plan workouts and lifting cycles
- record sets, reps, weights, and exercise details
- review lifting sessions over time
- analyze motion quality for technique feedback
- spot patterns, weaknesses, and progress

## Core Product Direction

When adding features, prefer work that supports these core capabilities:

### 1. Training Plan Recording

Examples:

- workout plans
- exercise lists
- sets, reps, and weight targets
- training history
- notes for sessions or exercises

### 2. Motion Analysis

Examples:

- capture or import lift videos or motion data
- inspect movement phases
- analyze bar path, joint movement, or timing when supported
- surface simple, actionable technique feedback
- compare current lifts with past attempts

### 3. Insight Over Raw Data

The app should help users understand their lifting, not just store information.

Prefer features that produce:

- clear summaries
- trends over time
- useful analysis
- practical recommendations

## UI and Design Direction

The app should use:

- **Jetpack Compose** for UI
- **Material 3** theme/components
- **Material icons** where appropriate
- a clean visual style similar to **Google apps**

### Expected UI Feel

Aim for a UI that feels:

- clean
- modern
- spacious
- readable
- calm and practical
- mobile-first
- polished in a way that is comparable to Google&apos;s Android Clock app, especially in spacing, clarity, and restrained motion

### Clock-Inspired Interaction Notes

When refining existing screens, it is acceptable to take inspiration from the feel of Google&apos;s Android Clock app, especially its:

- strong headline hierarchy
- spacious card layout
- obvious selected states and touch targets
- subtle, smooth animations
- clean Material icon usage

Do not copy proprietary assets or clone screens literally. Match the calm design language and interaction quality while keeping the product focused on lifting plans, records, and motion analysis.

### Google-Inspired Design Principles

When designing screens, prefer:

- simple top app bars
- clear section hierarchy
- cards, lists, and sheets that feel native to Android
- strong spacing and alignment
- limited visual clutter
- obvious primary actions
- consistent Material behavior

Avoid:

- overly flashy sports-app styling
- crowded dashboards
- unnecessary custom widgets when Material components already fit
- mixing multiple visual styles in the same screen

## Technical Expectations

The current app already uses Compose and a Material theme. Keep moving in that direction.

### Use These Defaults

- Kotlin-first Android development
- Jetpack Compose screens and state-driven UI
- Material 3 theming
- Material icons for common actions and navigation
- Android-native patterns before introducing custom frameworks
- Keep good MVC model

### Architecture Guidance

Prefer simple, feature-focused structure.

Good examples of feature areas for this app:

- `plan` or `training`
- `session`
- `analysis`
- `video` or `capture`
- `history`
- `profile` or `settings`

Keep code easy for another engineer or LLM to follow.

## Assistant Notes

If you are an LLM or coding assistant working on this repository:

1. preserve the product focus on weightlifting plans plus motion analysis
2. keep UI work in Jetpack Compose
3. stay aligned with Material 3 styling and Material icons
4. make screens feel like a polished Google-style Android app
5. avoid inventing unrelated product directions without evidence in the repo
6. prefer straightforward, maintainable code over heavy abstraction
7. for non-ui code, write test with as much as possible coverage.
8. add files you changes to git, but do not commit or push. Let a human review before committing.

## Scope and Non-Goals

Do not drift into a generic fitness app unless the repository later shows that direction.

For now, the project should stay centered on:

- weightlifting
- training plan tracking
- lift session records
- motion/form analysis
- useful feedback and insights

Non-goals unless explicitly requested later:

- broad social-network features
- gamified lifestyle tracking
- unrelated wellness content
- bodybuilding-only aesthetics that conflict with the Google-like UI direction

## i18n

The app should be designed with internationalization in mind, even if it starts with English-only content. Use string resources and avoid hardcoding text in the UI.

But if not explicitly requested, you can focus on English for now while keeping the code ready for future localization.

## Related Context

Also review `skill.md` for coding style and implementation preferences before making major changes.


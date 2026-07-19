# Crumb Project Rules

## Project Context

Crumb is a native Android university prototype for recipe management and food-community communication.

- Main project directory: `D:\Projects\Crumb`
- Android frontend: `D:\Projects\Crumb`
- FastAPI backend: `D:\Projects\Crumb\backend`
- Android package / namespace: `com.example.crumb`
- Emulator backend base URL: `http://10.0.2.2:8000/`
- Database: MySQL, accessed only through FastAPI
- Authentication: intentionally not implemented yet
- User identity: use the existing temporary local user where required

## Current Android Architecture

The active Android application uses:

- Kotlin
- XML layouts only
- Fragments
- ViewBinding
- Navigation Component
- BottomNavigationView
- Material Components / Material 3 styling
- RecyclerView and adapters
- Retrofit
- Moshi
- OkHttp
- Repository classes
- Gradle Wrapper

Do not introduce Jetpack Compose.

Do not create:

- Compose screens
- composables
- Navigation Compose
- Compose themes
- a second Android application

## Main Navigation

The application currently uses five main destinations:

1. Home
2. Search
3. Add
4. Saved
5. Profile

Preserve the existing Navigation Component and bottom-navigation structure unless a task explicitly requires a navigation change.

## Core Implemented Features

The project already contains:

- Recipe create, read, update, and delete
- Recipe ingredients with quantity and unit
- Ingredient catalogue
- Ingredient categories
- Ingredient search
- Community Post create, read, update, and delete
- Comment create, read, update, and delete
- Real recipe data on Home
- Real community data on Home
- Profile dietary preferences
- Profile allergy and avoided-ingredient selections
- Automatic local preference saving with SharedPreferences
- Backend health checking
- Loading states
- Empty states
- Error states
- Retry behaviour
- Friendly API error handling
- Request timeout handling
- Duplicate request protection

Do not replace working implementations with placeholders or fake prototype data.

## Current Development Priority

Finish, repair, and verify basic features before adding advanced features.

Current high-priority work includes:

- Fix the Create Recipe selected-ingredient display and layout issue
- Complete basic recipe Search behaviour
- Make Add a dedicated Create Recipe workflow
- Add local Create Recipe draft saving and restoration
- Add dietary and allergy warnings
- Complete Saved Recipes in the Android frontend
- Show the current user's recipes and posts in Profile where requested
- Support recipe visibility and community sharing without incorrectly merging Recipe and Post entities

Do not begin advanced AI or social features unless explicitly requested.

## Architecture Rules

- Keep changes small and focused.
- Keep the system appropriate for a university prototype.
- Preserve XML, Fragments, ViewBinding, Navigation Component, Retrofit, Moshi, repositories, and existing backend contracts.
- The Android application must never connect directly to MySQL.
- All remote data access must go through FastAPI.
- Do not add authentication until explicitly requested.
- Do not perform broad refactors during a focused bug-fix task.
- Do not rename existing view IDs unless necessary.
- Reuse existing DTOs, repositories, adapters, dialogs, and resources where practical.
- Avoid unrelated formatting changes.
- Avoid generated churn.
- Avoid IDE-only edits.
- Do not edit `local.properties` except for a genuine machine-local requirement.
- Never store secrets in source files.

## Android UI Rules

- Use XML Views only.
- Preserve ViewBinding.
- Use Material components compatible with the existing theme.
- Keep screen states functional before visual polish.
- Preserve the current Crumb prototype style where possible.
- Allow Antigravity or Gemini to perform final visual polish after functionality is stable.
- Design-only work must not alter API contracts, repositories, DTOs, database models, or backend behaviour.
- Avoid fixed heights for dynamic lists unless deliberately required.
- Inspect nested scrolling carefully when RecyclerView is placed inside a ScrollView or NestedScrollView.
- Keep Save, Cancel, Retry, and destructive actions reachable on supported emulator sizes.
- Preserve existing IDs and bindings during design work.

## RecyclerView and Form Rules

- Use one source of truth for mutable form state.
- Do not use adapter positions as permanent item identity.
- Prefer backend IDs or canonical names as stable identity.
- When using ListAdapter, submit a new copied list after changes.
- Do not mutate and resubmit the same list instance.
- Remove old TextWatcher instances before rebinding editable rows.
- Preserve quantity and unit values when rows are recycled.
- Prevent duplicate selected ingredients.
- Prevent invisible selected ingredients.
- Prevent duplicate Save, Delete, and Retry requests.
- Validate form input before sending a network request.
- Removing one selected ingredient must not remove or reset unrelated ingredients.

## Network and Coroutine Rules

- Use the existing Retrofit, Moshi, OkHttp, and repository structure.
- Use shared API error handling.
- Do not expose raw exception text or stack traces to users.
- Preserve current timeout behaviour unless explicitly changed.
- Rethrow CancellationException from generic coroutine error wrappers.
- Do not update Fragment views after the binding has been destroyed.
- Guard asynchronous callbacks.
- Clear listeners where needed.
- Show loading, empty, success, error, and retry states where relevant.

## Backend Rules

The backend is located at:

```text
D:\Projects\Crumb\backend
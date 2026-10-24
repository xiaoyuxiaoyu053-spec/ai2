# DeepLine — Local Personal Android App

DeepLine is a local-first LINE-inspired AI companion app. It has no account system, no invite codes, no administrator panel, no Render backend, and no cloud database.

## Included in this source package

- LINE-inspired chat list and rounded bottom navigation (Chat / Feed / Settings)
- Personal display name, bio, and photo-library avatar selection
- Up to 10 AI personas, each with its own profile, personality, gender, relationship, likes, dislikes, hobbies, API key, and separate conversation history
- API key required to create an AI persona
- DeepSeek Chat Completions API integration
- Local chat history and local feed posts
- Feed posts with text and selected image, likes, comments, and replies
- Theme accent presets and photo-library background selection for chat/feed surfaces
- Optional AI auto-post / proactive chat-note loop (disabled by default; can consume API quota while the app is running)
- GitHub Actions workflow to build a debug APK

## Build APK with GitHub Actions

1. Upload the contents of this ZIP to a GitHub repository.
2. Open **Actions** and run **Build DeepLine APK** (or push a commit to trigger it).
3. Download the `DeepLine-debug-apk` artifact from the completed workflow.

## API key and privacy

API keys are entered inside the app and stored locally in the app's private preferences. Do not commit API keys to GitHub or share screenshots/backups that expose them. API requests go directly from the device to the configured DeepSeek endpoint. Provider availability, quota, and fees depend on the provider/account.

Local data can be lost if the app is uninstalled or its data is cleared. Photo access uses Android's system photo picker/content URI.

## Technical notes

- Kotlin + Jetpack Compose
- Minimum Android API: 26
- App version: 1.0.0

This package is source code, not a prebuilt APK. It has not been compiled or device-tested in this environment, so run the GitHub Actions build and test the resulting APK on your phone before relying on it. The auto-post feature runs only while the app process is active; Android may suspend background work.

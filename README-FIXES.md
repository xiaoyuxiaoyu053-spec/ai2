# DeepLine local fixes (1.0.2)

This source revision addresses the reported issues:

- Uses a real animated three-dot typing indicator for internal `---` placeholders.
- Displays the AI avatar beside AI messages and the local user's avatar beside user messages.
- Copies picked images into app-private storage instead of retaining temporary gallery URIs, so avatars/backgrounds/posts can load after reopening the app.
- Fixes persistence/recomposition: saves no longer replace the in-memory JSON tree while a chat request still holds references to its message list.
- Applies the saved theme color to Material components and starts with the saved theme.
- Pins the GitHub Actions Gradle distribution to 8.9 for Android Gradle Plugin 8.7.3 and sets Java/Kotlin targets to 17.

## Build

The repository's GitHub Actions workflow builds `android/app/build/outputs/apk/debug/app-debug.apk` and uploads it as the `DeepLine-debug-apk` artifact.

This source patch has not been built on a local Android/Gradle toolchain in this environment. Run Actions after uploading and share any remaining `compileDebugKotlin` or build error log for further correction.

- Updates the DeepSeek request model from the legacy `deepseek-chat` alias to `deepseek-flash`, as listed in the current official API documentation. Disables thinking for faster ordinary chat responses and sets a bounded output length.
- Makes API HTTP errors include the status code and provider error text, and makes empty-response errors easier to diagnose instead of leaving an unexplained typing indicator.

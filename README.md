# Little Steps

Therapy game for autistic children aged 4-10: 365 daily missions, 3 sessions a day, adaptive levels, break time, parent-recorded voice.

- `web/index.html` is the game (single offline file, the source of truth).
- `android/` is the WebView wrapper. Copy `web/index.html` (and `web/voice/` if generated) into `android/app/src/main/assets/` before building.
- `voice-tools/make_voices.py` generates natural voice clips (see the script header).

Never commit signing keys (*.jks, *.keystore) or passwords.

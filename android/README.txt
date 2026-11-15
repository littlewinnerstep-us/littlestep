LITTLE STEPS - Android (WebView) project
========================================

BUILD THE APK (needs Android Studio, free: https://developer.android.com/studio)
1. Open Android Studio > File > Open > choose this folder (LittleSteps-Android).
2. Wait for "Gradle sync" to finish (first time it downloads tools; needs internet).
3. Menu: Build > Build Bundle(s) / APK(s) > Build APK(s).
4. When it says "APK(s) generated", click "locate".
   File: app/build/outputs/apk/debug/app-debug.apk
5. Copy the APK to the phone and open it (allow "install unknown apps" if asked).
   You can also connect the phone by USB and press the green Run button.

NO ANDROID STUDIO? BUILD ON GITHUB (free, only a web browser)
1. Create a free account at github.com and make a new repository (private is fine).
2. Upload all files of this folder into it (drag the folder contents into
   "Add file > Upload files"; keep the .github folder).
3. Open the repository's "Actions" tab. "Build APK" starts by itself (about 3-5 minutes).
   If it does not, choose "Build APK" > "Run workflow".
4. Open the finished run and download "LittleSteps-APK" under Artifacts. Unzip it:
   app-debug.apk is the file to install on the phone.

CHANGE THE APP NAME:   app/src/main/res/values/strings.xml  ->  app_name
CHANGE THE GAME:       replace app/src/main/assets/index.html with a newer file
CHANGE THE ICON:       app/src/main/res/drawable/ic_launcher.xml
CHANGE THE PACKAGE ID: applicationId in app/build.gradle (before publishing)

FOR GOOGLE PLAY: Build > Generate Signed Bundle / APK, keep your key file safe.

The game talks to Android text-to-speech through a bridge named "AndroidTTS"
(speak, stop, setLang). The phone needs the English and Indonesian voices
installed (Settings > System > Languages > Text-to-speech) for spoken
instructions. Without them the game still works, silently.

# Getting the app onto your phone (no programming needed)

I can't compile the actual installable app file (the `.apk`) myself here —
that needs Google's Android build tools, which aren't available in this
environment. But this project now includes a file
(`.github/workflows/build-apk.yml`) that tells **GitHub's own free
computers** to do that compiling for you automatically. You just need to
upload the project once and click a couple of buttons.

Total time: about 15–20 minutes, most of it waiting.

## Step 1 — Create a free GitHub account (skip if you have one)

Go to https://github.com and sign up. It's free.

## Step 2 — Create a new repository

1. Click the **+** icon (top right) → **New repository**.
2. Name it anything, e.g. `klartext-app`.
3. Leave everything else as default, click **Create repository**.

## Step 3 — Upload the project

1. Unzip the file I gave you (`Klartext_Android.zip`) somewhere
   on your computer, so you have a normal folder called `Klartext_Android`
   (or whatever it unzipped to) with folders like `app`, `gradle`, `.github` inside it.
2. On your new repository's GitHub page, click **uploading an existing file**
   (or **Add file → Upload files**).
3. Open that unzipped folder on your computer, select **everything inside
   it** (all files and folders — `app`, `gradle`, `.github`, `build.gradle.kts`,
   etc.), and drag the whole selection onto the GitHub upload page.
   Modern browsers (Chrome, Edge) keep the folder structure when you drag a
   selection like this — you don't need to zip it again.
4. Scroll down, click **Commit changes**.

## Step 4 — Let it build

1. Click the **Actions** tab near the top of your repository page.
2. You should see a workflow run already starting (triggered by your upload),
   called "Build APK". If you don't see one, click **Run workflow**.
3. Wait for it to finish — usually 3–5 minutes. A green checkmark means it worked.

## Step 5 — Download the APK

1. Click on the finished run (the one with the green checkmark).
2. Scroll down to **Artifacts** and click **Klartext-debug-apk** to download it.
   It downloads as a `.zip` file.
3. Unzip it — inside you'll find **app-debug.apk**. That's the actual app.

## Step 6 — Get it onto your phone

Any of these works:
- Email `app-debug.apk` to yourself and open the email on your phone.
- Upload it to Google Drive / Dropbox and open it on your phone.
- Plug your phone into your computer with a USB cable and copy it over.

## Step 7 — Install it

1. On your phone, tap the `app-debug.apk` file (in your Downloads, or
   wherever you saved it).
2. Android will likely ask for permission to "install unknown apps" — this
   is normal for any app installed outside the Play Store. Allow it for
   whichever app you're using to open the file (Files, Gmail, Chrome, etc.).
3. Tap **Install**, then **Open**.

You now have the app on your phone, fully working (Klartext v1.17.0: Learn,
Practice, Quick Quiz, Search, Word lists and Profile).

## Updating it later

Whenever I hand you an updated project (or you want to re-upload changed
files), just repeat Steps 3–7: upload the new files to the same GitHub
repository (overwriting the old ones), wait for the build, download the new
APK, and install it over the old one on your phone (same steps — Android
will just update the existing app rather than making a duplicate).

## Notes

- This produces a **debug build** — completely fine to install and use
  normally, just not signed for the Google Play Store. That's not needed
  for installing directly on your own phone.
- The build uses Gradle 8.7 with Android Gradle Plugin 8.5.2 and Java 17 (the
  workflow sets Java up for you). If you open the project in Android Studio
  and it offers to upgrade Gradle, Java or the plugin, decline unless you
  upgrade all three together.
- If a step doesn't look like what's described here, GitHub's interface
  changes design occasionally — the button names might be slightly
  different, but the flow (upload → Actions tab → download artifact) stays
  the same.

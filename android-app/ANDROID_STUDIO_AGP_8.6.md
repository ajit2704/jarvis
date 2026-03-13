# Using AGP 8.6 with compileSdk 35

This project uses **Android Gradle Plugin (AGP) 8.6.0** and **compileSdk 35** by design.

## If you see: "Latest supported version is AGP 8.1.3"

Your current **Android Studio is too old** to support AGP 8.6. You have two options.

---

## Option 1: Upgrade Android Studio (recommended)

Upgrade to an Android Studio version that supports AGP 8.6:

| Android Studio | Version      | AGP support |
|----------------|-------------|-------------|
| **Koala**      | 2024.1.x    | AGP 8.6     |
| **Ladybug**    | 2024.2.x    | AGP 8.6     |
| **Newer**      | 2025.x etc. | AGP 8.6+    |

**Steps:**

1. Download the latest (or at least **Koala 2024.1.1** or **Ladybug 2024.2.1**) from:  
   https://developer.android.com/studio
2. Install it (you can keep the old version installed if you want).
3. Open this project (`android-app`) with the **new** Android Studio.
4. Let Gradle sync; it will use AGP 8.6 and Gradle 8.7.

No project changes needed; keep AGP 8.6 and compileSdk 35.

---

## Option 2: Build from command line (no Android Studio upgrade)

You can build with AGP 8.6 without upgrading Android Studio by using the Gradle wrapper:

```bash
cd /path/to/jarvis/android-app
./gradlew assembleDebug
```

Requirements:

- **JDK 17** (AGP 8.6 requirement).
- **Gradle wrapper** present (e.g. `gradlew` and `gradle/wrapper/`). If missing, generate with:
  ```bash
  gradle wrapper --gradle-version 8.7
  ```

Android Studio will still show the "incompatible AGP" warning when you open the project, but the command-line build will use AGP 8.6 and compileSdk 35 as required.

---

## Summary

- **Requirement:** AGP 8.6 for compileSdk 35 — no reverting.
- **Solution:** Use Android Studio **Koala 2024.1+** or **Ladybug 2024.2+** (or newer), or build with `./gradlew` and JDK 17.

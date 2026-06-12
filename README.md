# BitBloom User

Android client for BitBloom account, wallet, investment-plan, referral-team, reward, and support workflows backed primarily by Firebase.

## Overview

BitBloom User is a Kotlin Android application built with XML layouts, fragments, Jetpack Navigation, ViewModels, repositories, LiveData, and coroutines. Users can create and verify accounts, view balances and transaction activity, submit financial actions, purchase configured plans, inspect referral levels, collect in-app rewards, manage a profile, and contact support.

Most application data is read from and written directly to Firebase services. The app also calls a Firebase callable function for referral-team data, reads cryptocurrency prices from CoinGecko, and uses Firebase Remote Config for application updates.

## Features

- Firebase email/password registration, email verification, login, password reset, and session persistence
- User profile viewing, editing, and profile-image storage
- Wallet balances, token valuation, recent activity, and cryptocurrency price retrieval
- Deposit recording and withdrawal-request workflows with history views
- Configurable investment-plan catalog, plan purchase, auto-invest selection, and purchased-plan history
- Direct and indirect referral metrics, six-level team views, and leaderboard data
- ROI, plan, referral, team, salary, achievement, lucky-spin, deposit, and withdrawal transaction views
- Daily rewards, starter rewards, lucky-spin rewards, achievement rewards, and salary-level collection
- Announcements, image notices, FAQs, privacy content, and support-ticket submission/tracking
- Firebase Cloud Messaging notifications
- Firebase App Check with Play Integrity
- Remote Config-driven APK update flow with package-name and SHA-256 validation

## Tech Stack

- Kotlin and Android SDK
- XML layouts and View Binding
- Jetpack Navigation with Safe Args
- ViewModel, LiveData, and Kotlin coroutines
- Material Components
- Firebase Authentication
- Cloud Firestore
- Firebase Storage
- Firebase Cloud Messaging
- Firebase Cloud Functions
- Firebase Remote Config
- Firebase App Check / Play Integrity
- OkHttp, Volley, Gson, and Moshi
- Glide, Picasso, Lottie, PhotoView, uCrop, and ZXing Core

## Architecture

The app follows a lightweight MVVM-style structure:

1. Fragments render each workflow and observe ViewModel state.
2. ViewModels coordinate coroutine work and expose LiveData to the UI.
3. Repository classes query Firebase, invoke callable functions, fetch external data, and perform account or transaction updates.
4. Models map Firestore documents and UI data.
5. Utility classes handle preferences, sounds, transaction dialogs, onboarding tours, and APK updates.

Navigation starts at the login screen and routes authenticated users through home, wallet, plans, team, rewards, profile, announcements, transaction, and support screens.

## Project Structure

```text
app/src/main/
|-- java/com/codingEmpire/bitbloom/
|   |-- adapters/       # RecyclerView and pager adapters
|   |-- fcm/            # Notification receiving and FCM helper code
|   |-- models/         # Firestore and UI models
|   |-- repos/          # Firebase and network data operations
|   |-- ui/             # Main activity and fragments
|   |-- utils/          # Preferences, updates, dialogs, and shared helpers
|   `-- viewModels/     # Screen state and repository coordination
|-- res/                # Layouts, navigation graph, drawables, audio, and animations
`-- AndroidManifest.xml
```

## Getting Started

### Prerequisites

- Android Studio with a JDK compatible with Android Gradle Plugin 8.10.1
- Android SDK 35
- An emulator or device running Android 7.0 (API 24) or newer
- Access to a Firebase project configured for the services used by the app

### Build

```bash
git clone https://github.com/shayann07/bitBloom-user.git
cd bitBloom-user
./gradlew assembleDebug
```

On Windows PowerShell, use `./gradlew.bat assembleDebug`.

Running the complete application also requires the expected Firestore collections and indexes, enabled Firebase Authentication and Storage, the `getTeamLevels` callable Cloud Function, Remote Config values used by the updater, App Check configuration, and access to the external services referenced by the source.

## Current Status and Limitations

- The repository contains an implemented Android client, but backend provisioning and deployment instructions are not included.
- Financial balances, plan purchases, deposits, withdrawals, and several reward claims are performed directly from the client and depend on strict server-side Firebase rules.
- A Firebase service-account credential is embedded in the Android source for sending FCM messages. This credential must not be shipped in a client application.
- Passwords are duplicated in Firestore and local preferences, and login code logs password data. Firebase Authentication should be the only password authority.
- The app downloads APK updates from a Remote Config URL and requests permission to install packages outside an app store.
- The external payment backend, Firestore schema, composite indexes, and operational ownership are not documented.
- Only generated example unit and instrumentation tests are present.
- No license file is included.

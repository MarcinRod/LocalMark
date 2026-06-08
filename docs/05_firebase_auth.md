# Firebase Auth (with OAuth)

## What is it?

**Firebase Authentication** provides ready-made backend infrastructure for user identity. LocalMark supports four sign-in methods:

| Method | Mechanism |
|---|---|
| Email + password | `FirebaseAuth.signInWithEmailAndPassword` / `createUserWithEmailAndPassword` |
| Google | Credential Manager API → `GoogleAuthProvider.getCredential` |
| GitHub | `OAuthProvider` → Chrome Custom Tab → `startActivityForSignInWithProvider` |
| Password reset | `FirebaseAuth.sendPasswordResetEmail` |

All auth operations are wrapped in a coroutine-friendly `FirebaseAuthentication` class and consumed through `AuthRepository`.

---

## Step-by-Step Setup Guide

### Step 1 — Create a Firebase Project

1. Open the [Firebase Console](https://console.firebase.google.com/) and click **Add project**.
2. Enter a project name (e.g. `LocalMark`) and click **Continue**.
3. Choose whether to enable Google Analytics (optional for auth) and click **Create project**.
4. Once the project is ready, click **Continue** to open the project dashboard.

---

### Step 2 — Register Your Android App

1. On the project dashboard click the **Android** icon (`</>`).
2. Fill in the **Android package name** — this must exactly match the `applicationId` in `app/build.gradle.kts` (e.g. `com.example.localmark`).
3. *(Optional)* Enter a nickname and your **Debug SHA-1** fingerprint.
   - Run the following in your project root to get the SHA-1:
     ```powershell
     .\gradlew signingReport
     ```
   - Copy the `SHA1` value shown under the `debug` variant.
   > **Note:** The SHA-1 is **required** for Google Sign-In to work. Add both your debug and release SHA-1 fingerprints.
4. Click **Register app**.
5. Download the generated **`google-services.json`** file and place it in the `app/` directory of your project. This file already contains your OAuth client information.
6. Follow the on-screen instructions to apply the Google Services Gradle plugin (see [Dependencies](#dependencies) below).
7. Click **Continue to Console**.

---

### Step 3 — Apply the Gradle Plugin and Dependencies

Add the plugin to the **project-level** `build.gradle.kts`:

```kotlin
// build.gradle.kts (root)
plugins {
    alias(libs.plugins.google.gms.google.services) apply false
}
```

Apply it in the **app-level** `build.gradle.kts`:

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.google.gms.google.services)
}
```

Then declare the required dependencies (see [Dependencies](#dependencies) for the full list).

---

### Step 4 — Enable Email / Password Authentication

1. In the Firebase Console go to **Authentication** → **Sign-in method** tab.
2. Click **Email/Password** in the provider list.
3. Toggle **Enable** to on.
4. *(Optional)* You can also toggle **Email link (passwordless sign-in)** here — this is not used by LocalMark.
5. Click **Save**.

That is everything required on the Firebase side. No additional credentials or secrets need to be added to the project. The SDK uses the `google-services.json` for routing.

**Corresponding code:**

```kotlin
// Sign-up (registration)
val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
val user: FirebaseUser? = result?.user

// Sign-in
val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
val user: FirebaseUser? = result?.user
```

---

### Step 5 — Enable Google Sign-In

#### 5a. Enable the provider in Firebase Console

1. Go to **Authentication** → **Sign-in method** → click **Google**.
2. Toggle **Enable** to on.
3. Choose a **Project support email** from the dropdown (your Google account).
4. Click **Save**.

Firebase will automatically create an **OAuth 2.0 Web Client ID** in Google Cloud Console and embed it in your `google-services.json`.

#### 5b. Add the SHA-1 fingerprint (if not done in Step 2)

1. Go to **Project settings** (the gear icon) → **Your apps** → select your Android app.
2. Under **SHA certificate fingerprints** click **Add fingerprint**.
3. Paste the SHA-1 from `.\gradlew signingReport` and click **Save**.

> Without a registered SHA-1 the Credential Manager bottom sheet will fail to appear at runtime.

#### 5c. Obtain the Web Client ID

1. In the Firebase Console go to **Project settings** → **Your apps** → select your Android app → **google-services.json**.
   Alternatively, open `google-services.json` and find the entry with `"client_type": 3` — the value of `"client_id"` is your **Web Client ID**.
2. Copy the Web Client ID (it ends in `.apps.googleusercontent.com`).
3. Add it to `local.properties`:
   ```properties
   # local.properties
   GOOGLE_WEB_CLIENT_ID=123456789-xxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com
   ```
4. Expose it through `BuildConfig` in `app/build.gradle.kts`:
   ```kotlin
   val localProperties = java.util.Properties().apply {
       load(rootProject.file("local.properties").inputStream())
   }

   android {
       defaultConfig {
           buildConfigField(
               "String",
               "GOOGLE_WEB_CLIENT_ID",
               "\"${localProperties["GOOGLE_WEB_CLIENT_ID"]}\""
           )
       }
   }
   ```
5. Read it at runtime:
   ```kotlin
   val googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
   ```

> **Important:** Never commit `local.properties` to version control. Add it to `.gitignore`.

#### 5d. Add required dependencies

```kotlin
// app/build.gradle.kts
implementation(libs.androidx.credentials)
implementation(libs.androidx.credentials.play.services.auth)
implementation(libs.googleid)
```

---

### Step 6 — Enable GitHub Sign-In

GitHub sign-in uses Firebase as an OAuth proxy — the app never directly calls the GitHub API. You need to register a GitHub OAuth App so that Firebase can perform the OAuth exchange on your behalf.

#### 6a. Register a GitHub OAuth App

1. Log in to [GitHub](https://github.com) and go to **Settings** → **Developer settings** → **OAuth Apps** → **New OAuth App**.
2. Fill in the form:
   | Field | Value |
   |---|---|
   | **Application name** | `LocalMark` (or any name) |
   | **Homepage URL** | Your app's URL or `https://localmark.example.com` |
   | **Authorization callback URL** | *Copy this from the Firebase Console — see next step* |
3. Before saving, get the callback URL from Firebase:
   - Go to Firebase Console → **Authentication** → **Sign-in method** → click **GitHub**.
   - Toggle **Enable** to on.
   - Copy the **Authorization callback URL** shown on the page. It looks like:
     `https://<your-project-id>.firebaseapp.com/__/auth/handler`
4. Paste that URL into the **Authorization callback URL** field in GitHub.
5. Click **Register application**.
6. On the next page you will see the **Client ID**. Click **Generate a new client secret** to create the **Client Secret**. Copy both values immediately — the secret is only shown once.

#### 6b. Configure GitHub in Firebase Console

1. Return to Firebase Console → **Authentication** → **Sign-in method** → **GitHub**.
2. Paste the **Client ID** and **Client Secret** from GitHub into the respective fields.
3. Click **Save**.

#### 6c. No extra dependencies needed

GitHub sign-in reuses Firebase Auth's built-in `OAuthProvider`. No additional Gradle dependencies are required beyond `firebase.auth`.

**Corresponding code:**

```kotlin
val provider = OAuthProvider.newBuilder("github.com").build()
val authResult = firebaseAuth
    .startActivityForSignInWithProvider(activity, provider)
    .await()
```

Firebase opens a **Chrome Custom Tab** to github.com, which redirects back via the callback URL after the user authorises the app. The SDK handles all token exchange steps automatically.

---


## Dependencies

```kotlin
// app/build.gradle.kts
implementation(libs.firebase.auth)
implementation(libs.androidx.credentials)               // Credential Manager API
implementation(libs.androidx.credentials.play.services.auth)
implementation(libs.googleid)                           // GoogleIdTokenCredential
```

The `google-services.json` file (from Firebase Console) must be placed in `app/` and the plugin applied:

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.google.gms.google.services)
}
```

---

## Core Concepts

### `FirebaseAuth` singleton

All Firebase Auth operations go through one `FirebaseAuth` instance:

```kotlin
// FirebaseProvider.kt
val auth by lazy { FirebaseAuth.getInstance() }
```

Shared through `FirebaseAuthentication`:

```kotlin
class FirebaseAuthentication(
    private val firebaseAuth: FirebaseAuth,
    private val googleWebClientId: String,  // OAuth 2.0 Web Client ID from google-services.json
)
```

### Email / password sign-in and registration

```kotlin
// Sign in
val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
val user: FirebaseUser? = result?.user

// Register
val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
val user: FirebaseUser? = result?.user

// Password reset
firebaseAuth.sendPasswordResetEmail(email).await()
```

`.await()` (from `kotlinx-coroutines-play-services`) converts the `Task<AuthResult>` into a suspending call.

### Google sign-in — Credential Manager API

The modern approach replaces the deprecated Google Sign-In SDK and the older One Tap API:

```kotlin
suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUser> {
    val credentialManager = CredentialManager.create(activityContext)

    // 1. Build the "Sign in with Google" request
    val googleIdOption = GetSignInWithGoogleOption.Builder(googleWebClientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    // 2. Show the account picker bottom sheet
    val credentialResponse = credentialManager.getCredential(activityContext, request)

    // 3. Extract the ID token from the returned credential
    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credentialResponse.credential.data)
    val idToken = googleIdTokenCredential.idToken

    // 4. Exchange the ID token for a Firebase credential and sign in
    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
    val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()

    return authResult?.user?.let { Result.success(it) }
        ?: Result.failure(Exception("Google sign-in failed: null user"))
}
```

> **`googleWebClientId`** is the **Web** client ID from the Firebase Console (not the Android client ID). It is stored in `local.properties` as `GOOGLE_WEB_CLIENT_ID` and exposed through `BuildConfig`.

### GitHub sign-in — OAuthProvider

Firebase handles the GitHub OAuth exchange server-side via a Chrome Custom Tab. No GitHub Client ID is needed in the app:

```kotlin
suspend fun signInWithGitHub(activity: Activity): Result<FirebaseUser> {
    val provider = OAuthProvider.newBuilder("github.com").build()
    val authResult = firebaseAuth
        .startActivityForSignInWithProvider(activity, provider)
        .await()
    return authResult?.user?.let { Result.success(it) }
        ?: Result.failure(Exception("GitHub sign-in failed: null user"))
}
```

### Pending OAuth result recovery

When the Custom Tab opens, Android may kill the host Activity to free memory. On return, the Firebase SDK retains the in-progress sign-in as a "pending auth result":

```kotlin
suspend fun checkPendingSignIn(): Result<FirebaseUser>? {
    val pending = firebaseAuth.pendingAuthResult ?: return null  // no pending result
    return try {
        val authResult = pending.await()
        authResult?.user?.let { Result.success(it) }
            ?: Result.failure(Exception("Pending sign-in completed but user is null"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

This is called once in `SignInViewModel`'s `init` block so returning users are signed in automatically without re-tapping the button.

### Auth-state `Flow`

`callbackFlow` wraps the `AuthStateListener` callback into a cold Kotlin `Flow` that emits the current user's UID on every auth-state change:

```kotlin
fun userIdFlow(): Flow<String?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser?.uid)  // null when signed out
    }
    firebaseAuth.addAuthStateListener(listener)
    awaitClose { firebaseAuth.removeAuthStateListener(listener) }
}
```

The listener fires **immediately** with the current state when registered, then again on every sign-in / sign-out / token refresh. `awaitClose` unregisters it when the flow is cancelled, preventing memory leaks.

---

## How the app uses it

### `AuthRepository` — coordination layer

`AuthRepository` sits between `FirebaseAuthentication` (auth operations) and `UsersDataSource` (Firestore profile documents) and enforces a two-step invariant:

> **Every Firebase Auth account must have a matching Firestore user profile document.**

#### Registration with rollback

```kotlin
suspend fun register(email: String, password: String, username: String): Result<String> {
    // Step 1: create the Auth account
    val registerResult = authService.signUp(email, password)
    if (registerResult.isFailure) return Result.failure(…)

    val uid = registerResult.getOrNull()!!.uid

    // Step 2: create the Firestore profile
    val createProfileResult = userDataStore.createUserProfile(uid, username, email)
    if (createProfileResult.isFailure) {
        authService.deleteCurrentUser()  // rollback Step 1 on Step 2 failure
        return Result.failure(…)
    }
    return Result.success(username)
}
```

#### OAuth sign-in — new vs. returning user

```kotlin
private suspend fun handleOAuthUser(
    signInResult: Result<FirebaseUser>
): Result<String> {
    val user = signInResult.getOrNull() ?: return Result.failure(…)

    // Check if a Firestore profile already exists (returning user)
    val existingProfile = userDataStore.getUserProfile(user.uid).getOrNull()
    if (existingProfile != null) return Result.success(existingProfile.username)

    // First-time OAuth user — create a profile from Firebase display name / email
    val username = user.displayName ?: user.email?.substringBefore('@') ?: "User"
    val createResult = userDataStore.createUserProfile(user.uid, username, user.email ?: "")
    if (createResult.isFailure) {
        authService.deleteCurrentUser()  // rollback
        return Result.failure(…)
    }
    return Result.success(username)
}
```

Both `signInWithGoogle` and `signInWithGitHub` delegate to this shared helper:

```kotlin
suspend fun signInWithGoogle(activityContext: Context): Result<String> =
    handleOAuthSignIn { authService.signInWithGoogle(activityContext) }

suspend fun signInWithGitHub(activity: Activity): Result<String> =
    handleOAuthSignIn { authService.signInWithGitHub(activity) }

private suspend fun handleOAuthSignIn(
    signIn: suspend () -> Result<FirebaseUser>
): Result<String> = handleOAuthUser(signIn())
```

---

## Minimal Setup Checklist

| # | Task | Auth method |
|---|---|---|
| 1 | Create a Firebase project | All |
| 2 | Register the Android app and download `google-services.json` → `app/` | All |
| 3 | Apply the `google-services` Gradle plugin | All |
| 4 | Enable **Email/Password** in Firebase Console → Authentication → Sign-in method | Email / Password reset |
| 5 | Enable **Google** in Firebase Console → Authentication → Sign-in method | Google |
| 6 | Add SHA-1 fingerprint(s) in Firebase Console → Project settings | Google |
| 7 | Copy the **Web Client ID** from `google-services.json` into `local.properties` as `GOOGLE_WEB_CLIENT_ID` | Google |
| 8 | Add `credentials`, `credentials-play-services-auth`, and `googleid` dependencies | Google |
| 9 | Register a GitHub OAuth App at github.com/settings/developers | GitHub |
| 10 | Paste Firebase's callback URL (`https://<project>.firebaseapp.com/__/auth/handler`) as the GitHub OAuth callback | GitHub |
| 11 | Enable **GitHub** in Firebase Console and paste the GitHub Client ID + Secret | GitHub |
| 12 | Wrap all `FirebaseAuth` `Task` calls with `.await()` (from `kotlinx-coroutines-play-services`) | All |
| 13 | Wrap `AuthStateListener` in `callbackFlow { … awaitClose { … } }` for a reactive `Flow` | All |


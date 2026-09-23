# Wavv — Cloud Computing Architecture Specification

**Status:** Active V1 specification
**Project:** Wavv — Offline-first Android AI music player
**Exhibition:** 16 October 2026
**Platform:** Android only
**Application stack:** Kotlin + Jetpack Compose
**AWS integration:** AWS Amplify Android
**Primary AWS services:** Amazon Cognito + Amazon S3
**Cloud principle:** optional, minimal, useful

---

## 1. Purpose

Wavv uses AWS for a small set of user-facing cloud capabilities that are genuinely useful without making the music player cloud-dependent:

1. optional Wavv account;
2. backup and restore of selected Wavv state;
3. user-selected cloud music storage;
4. controlled sharing of cloud-stored music files.

The core Wavv application remains fully functional without an account, AWS, or internet access.

---

## 2. Architecture decision

V1 intentionally uses the smallest AWS service set that covers the requirements:

```text
                    Wavv Android
              Kotlin + Jetpack Compose
                         │
                         ▼
                  AWS Amplify Android
                    /          \
                   /            \
             Cognito             S3
             Account        Cloud Storage
                   \            /
                    \          /
                    Wavv Cloud Features
```

### Required V1 AWS services

**Amazon Cognito**
- optional user registration/sign-in;
- account identity and authentication;
- recovery/verification as configured.

**Amazon S3**
- Wavv backup objects;
- user-selected cloud music files;
- explicitly shared cloud file objects.

**AWS Amplify Android**
- application integration layer that simplifies authentication and storage configuration.

Current AWS documentation states that Amplify Auth is powered by Cognito and that Amplify Storage is built on S3.

---

## 3. Explicit non-goals

Do **not** build these in the exhibition V1:

- WebRTC/P2P music transfer;
- custom signaling;
- STUN/TURN servers;
- BitTorrent/DHT/swarm protocols;
- multi-peer chunk scheduling;
- custom microservice backend;
- cloud-only recommendation;
- cloud-only ML inference;
- automatic full-library upload.

This decision is primarily about reducing configuration, debugging and operational risk before the exhibition.

---

## 4. Account model

### 4.1 Anonymous mode

The user can open Wavv and use:
- local file discovery;
- playback;
- local playlists/favorites;
- local AI analysis;
- semantic retrieval;
- recommendation;
- cached UHQ.

No account is required.

### 4.2 Authenticated mode

The user signs in using Cognito-backed authentication. Email/password is the recommended V1 login because it is sufficient for the project and keeps the account UI simple.

```text
Wavv
  ↓
Sign in / Create account
  ↓
Cognito
  ↓
Authenticated Wavv account
```

---

## 5. Backup and restore

### 5.1 Purpose

The main account value is protection of the user's Wavv state if they delete the app, reinstall it, replace the device, or need to recover their setup.

### 5.2 Backed-up data

V1 backup should include only durable, useful state:

- playlists;
- favorites;
- listening history/recently played;
- settings/preferences;
- recommendation-related user state;
- selected persistent song-analysis metadata where stable song identity permits restoration.

### 5.3 Excluded data

Do not upload:

- temporary UHQ intermediates;
- temporary ML tensors;
- logs;
- transient caches;
- temporary audio buffers;
- every local music file automatically.

### 5.4 Backup object

Use a versioned compact JSON payload, for example:

```json
{
  "schema_version": 1,
  "account_id": "...",
  "updated_at": 0,
  "playlists": [],
  "favorites": [],
  "history": [],
  "settings": {},
  "recommendation_state": {},
  "analysis_metadata": []
}
```

The exact schema is an implementation contract and must be versioned.

### 5.5 Restore

```text
Install Wavv
   ↓
Sign in
   ↓
Find cloud backup
   ↓
Validate schema/version
   ↓
Restore local DB
   ↓
Continue offline
```

If the backup cannot be downloaded, the app remains usable with an empty/local database rather than blocking startup.

---

## 6. Wavv Cloud Music Library

### 6.1 Explicit upload

A user selects a local song and chooses **Upload to Wavv Cloud**. Wavv does not silently upload the whole library.

```text
Local song
   ↓
User selects Upload
   ↓
AWS Amplify Storage
   ↓
Amazon S3
```

Amplify Storage provides mobile file storage APIs over S3 and supports explicit access rules.

### 6.2 Download/restore

A cloud song can be downloaded to the local Wavv library after sign-in. Local playback remains independent of cloud availability.

### 6.3 Streaming

Streaming can be considered as a later optimization. V1 should prioritize reliable upload/download and local playback after download rather than adding a custom streaming pipeline.

---

## 7. Sharing with other users

### 7.1 V1 model

Sharing is **cloud-mediated whole-file sharing**. The file is stored in S3; another user receives controlled access to download it.

This is deliberately simpler than P2P.

### 7.2 Sharing levels

Recommended V1 levels:

**Private**
- owner only.

**Shared**
- owner explicitly shares a file with another intended recipient or through a controlled share mechanism.

**Public/discoverable**
- stretch only; implement only if a simple, secure discovery catalog can be provided without adding unnecessary backend complexity.

### 7.3 Access mechanism

S3 objects are private by default. Time-limited S3 presigned URLs can grant temporary download access without exposing long-lived AWS credentials. AWS documents presigned URLs as a mechanism for time-limited object access and uploads/downloads.

For recipient-specific sharing, the implementation may add **one small server-side function** if required to authorize the share and issue a short-lived access URL. This should be a single-purpose backend function, not a general backend platform.

### 7.4 What is deliberately absent

There is no peer discovery, IP exchange, WebRTC session, TURN relay, torrent tracker, DHT, or chunk swarm in V1.

---

## 8. Security rules

1. Never embed permanent AWS access keys in the Android app.
2. Use Cognito-backed identity and scoped AWS access.
3. Keep S3 objects private by default.
4. Use the narrowest access rule that meets the feature.
5. Use time-limited access for temporary sharing.
6. User-selected files only; no silent bulk upload.
7. Do not allow cloud failures to disable local playback.

AWS documentation describes Cognito-backed identity/authorization and Amplify Storage access controls for S3 objects.

---

## 9. Local/cloud data model

Each local song keeps its own stable local identity. A cloud-uploaded song additionally stores cloud metadata such as:

```text
cloud_object_key
cloud_uploaded
cloud_size
cloud_checksum
cloud_updated_at
sharing_state
```

The local file and the cloud object are not the same physical storage location.

A playlist can therefore contain a cloud song that is currently not downloaded:

```text
Playlist
  ├── local song      → available
  ├── cloud song      → download available
  └── missing song    → unavailable
```

---

## 10. Failure and offline behaviour

### No internet

Continue to support:
- local playback;
- local AI;
- local recommendation;
- local playlists/favorites;
- previously downloaded cloud songs.

Cloud operations show a recoverable offline error and can be retried later.

### AWS unavailable

Do not block application startup.

### Upload interrupted

Keep local file intact and mark cloud upload as incomplete/retryable.

### Restore interrupted

Keep current local data intact. Retry restore explicitly.

---

## 11. Android module boundary

Recommended logical modules:

```text
cloud/
  auth/
  backup/
  music/
  sharing/

app/
  ui/              ← Jetpack Compose
  playback/
  library/
  indexing/
  recommendation/
```

Cloud modules must expose small interfaces to the rest of the app. The player and AI pipelines must not directly depend on AWS SDK calls.

Example boundary:

```text
CloudAccountRepository
CloudBackupRepository
CloudMusicRepository
CloudShareRepository
```

---

## 12. Minimal implementation order

### Stage 1 — Account
- Amplify configuration;
- Cognito sign-up/sign-in;
- session restore.

### Stage 2 — Backup/restore
- versioned backup schema;
- S3 backup upload/download;
- restore after reinstall.

### Stage 3 — Cloud music
- explicit song upload;
- cloud library listing;
- download;
- local playback.

### Stage 4 — Sharing
- private/shared object state;
- controlled access;
- test sharing between two accounts/devices.

Stop at Stage 4. Do not expand into P2P unless the exhibition schedule has surplus time and the core project is already stable.

---

## 13. Testing checklist

### Account
- create account;
- sign in/out;
- reopen session;
- incorrect password;
- verification/recovery flow as configured.

### Backup
- backup;
- uninstall;
- reinstall;
- sign in;
- restore;
- verify playlists/favorites/history/settings.

### Cloud music
- upload MP3/AAC/FLAC demo file;
- interrupted upload;
- download;
- delete cloud copy;
- compare checksum.

### Sharing
- owner shares;
- recipient receives access;
- unauthorized account cannot access private file;
- access expires when the chosen temporary share expires.

### Offline
- airplane mode;
- playback continues;
- cloud screens fail gracefully;
- retry after reconnect.

---

## 14. Exhibition demonstration

Use two prepared accounts/devices only for the cloud segment.

```text
1. Use Wavv offline.
2. Sign in.
3. Upload a small permitted demo song to Wavv Cloud.
4. Show the cloud library entry.
5. Share the file with the second account/device.
6. Download it on the second device.
7. Delete/reinstall one Wavv instance.
8. Sign in again.
9. Restore Wavv backup and show the cloud song still associated with the account.
10. Turn internet off and demonstrate that local playback continues.
```

Keep demo files small and authorized to control AWS storage and data-transfer consumption.

---

## 15. Cost and quota discipline

The project has access to AWS student/free resources, but the implementation must not assume an unlimited allowance.

Use these controls:
- small demo files;
- explicit upload;
- no background cloud upload;
- no repeated backup writes for unchanged data;
- delete obsolete cloud objects during testing;
- avoid unnecessary bandwidth-heavy cloud inference.

Do not hard-code a presumed student-credit amount into the app or documentation.

---

## 16. Source references

The AWS-specific implementation guidance above is based on current official AWS documentation for:

- Amplify Android authentication/Cognito. citeturn808711search4turn808711search6
- Amplify Android Storage backed by Amazon S3.
- S3 presigned URLs for controlled temporary object access.

The project architecture and scope decisions remain Wavv-specific and intentionally minimize the number of AWS services.

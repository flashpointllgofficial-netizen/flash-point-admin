# FLASH POINT Admin (Part 1)

1. Put the real Admin `google-services.json` at `app/google-services.json` (package com.flashpoint.admin).
2. Put the supplied logo at `app/src/main/res/drawable/app_logo.png`.
3. GitHub: push this folder as the repo root. Add repo secret `GOOGLE_SERVICES_JSON` = full file content. Actions -> "Build Admin APK" -> Run. Download the APK artifact.
4. Admin users need the custom claim `admin: true` (set with Firebase Admin SDK: `setCustomUserClaims(uid, {admin:true})`), then sign in again.
5. Firebase Console -> Project settings -> Admin app -> add the SHA-1/SHA-256 below.

Debug keystore (committed, password `android`, alias `androiddebugkey`):
SHA-1:   92:D5:49:A7:5D:22:32:CD:AA:67:83:E3:47:7B:0D:8E:46:59:6C:31
SHA-256: 10:6E:75:23:A3:DC:C6:C5:F4:EA:F5:FD:EC:6E:F3:A0:CF:02:DF:A8:3D:F1:BC:62:32:6E:E4:A0:2B:5D:38:E7

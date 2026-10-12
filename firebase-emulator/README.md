# Local Firebase Emulator (Realtime Database)

Used to check Labs 3–4 without a cloud project.

```
cd firebase-emulator
npx firebase-tools emulators:start --only database --project demo-safebox
adb reverse tcp:9000 tcp:9000      # so the app in the Android emulator can reach localhost:9000
```

Database UI: http://127.0.0.1:4000/database

In `local.properties`:
```
FIREBASE_DB_URL=http://localhost:9000/
FIREBASE_DB_NS=demo-safebox
```
For a real database: `FIREBASE_DB_URL=https://<project>-default-rtdb.<region>.firebasedatabase.app/`, `FIREBASE_DB_NS=` (empty).

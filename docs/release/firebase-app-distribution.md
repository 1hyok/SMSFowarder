# Firebase App Distribution setup

`release-distribution.yml` follows the same boundary as Afternote: PR validation never
receives production signing material, while the release job runs only through the
`release-distribution` GitHub Environment.

## One-time GitHub configuration

1. Create a Firebase Android app for `com.example.smsforwarder` and a tester group.
2. Create the GitHub Environment `release-distribution`.
3. Add these Environment secrets:
   - `RELEASE_STORE_FILE_B64`
   - `RELEASE_STORE_PASSWORD`
   - `RELEASE_KEY_ALIAS`
   - `RELEASE_KEY_PASSWORD`
   - `FIREBASE_SERVICE_ACCOUNT_JSON`
4. Add `FIREBASE_APP_ID` as an Environment variable. Optionally set
   `FIREBASE_TESTER_GROUPS`; it defaults to `testers`.
5. Add the repository variable `FIREBASE_APP_DISTRIBUTION_ENABLED=true`.
6. Merge the next trusted change to `master` and confirm that the signed APK reaches
   only the intended Firebase app and tester group. Every later `master` push uses the
   same protected path.

The workflow intentionally has no manual dispatch path: release credentials are exposed
only to code already accepted on `master`.

Encode the keystore locally without printing it:

```bash
base64 < release.jks | tr -d '\n'
```

Never commit the keystore, service-account JSON, or decoded credentials. The workflow
materializes them under `RUNNER_TEMP`, verifies the signed APK, attests that exact file,
uploads it, and removes the private files even when the job fails.

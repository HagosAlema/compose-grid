# Releasing

Maintainer runbook for publishing to Maven Central. The build is fully
configured; what follows is the procedure and the one-time account setup.

## One-time setup

### Coordinates

`groupId` is `io.github.hagosalema`, declared once in the root
`build.gradle.kts`; every module inherits it. It must match a namespace verified
against the GitHub account of the same name — the Kotlin packages
(`io.github.composegrid`) are unrelated and don't need to agree.

### Sonatype Central Portal

1. Register at https://central.sonatype.com.
2. Add and verify the `io.github.hagosalema` namespace.
3. Generate a **user token** — a username/password pair, not your login.

### GPG signing key

Maven Central requires signed artifacts.

```bash
gpg --full-generate-key                        # RSA 4096
gpg --list-secret-keys --keyid-format=long
gpg --export-secret-keys --armor <LONG_KEY_ID> # value for CI
```

Use that output **verbatim**, including the
`-----BEGIN PGP PRIVATE KEY BLOCK-----` / `-----END …-----` lines and the line
breaks. Do not base64-wrap it.

**Publish the public key and verify it landed.** Central fetches your public key
to check signatures, so an unpublished key fails the release. `gpg --send-keys`
prints nothing on success *and* nothing on several failure modes, so always
verify rather than trusting silence:

```bash
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys <LONG_KEY_ID>
curl -s "https://keyserver.ubuntu.com/pks/lookup?op=index&search=0x<FULL_FINGERPRINT>"
```

If the lookup says `Not Found`, the upload didn't happen — usually `dirmngr`
failing to reach the keyserver, silently. Fall back to the web form at
https://keyserver.ubuntu.com, pasting `gpg --armor --export <LONG_KEY_ID>`.

Confirm a *clean* keyring receives a usable key, since your own keyring proves
nothing about what Central sees:

```bash
TMP=$(mktemp -d); gpg --homedir "$TMP" --recv-keys <LONG_KEY_ID>; gpg --homedir "$TMP" --list-keys; rm -rf "$TMP"
```

A key served **without a user ID** is unusable — `gpg` refuses to import one.
`keys.openpgp.org` strips UIDs until you complete its email verification, so
prefer `keyserver.ubuntu.com` or verify your address there.

### Credentials

**CI** — four repository secrets under
*Settings → Secrets and variables → Actions*:

| Secret | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Portal **token** username |
| `MAVEN_CENTRAL_PASSWORD` | Portal token password |
| `SIGNING_IN_MEMORY_KEY` | Full armored private key |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | Key passphrase |

Secret values may contain newlines, so the armored key pastes in directly.

**Local** — Sonatype values in `~/.gradle/gradle.properties`:

```properties
mavenCentralUsername=<portal token username>
mavenCentralPassword=<portal token password>
```

Pass the key by environment variable, since a properties value can't span lines
without escaping each one:

```bash
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --export-secret-keys --armor <LONG_KEY_ID>)"
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword='<key passphrase>'
```

Set `signingInMemoryKeyId` only if the keyring holds several secret keys, and
never wire it to a GitHub secret that may not exist — an absent secret expands to
an empty string, Gradle sees the property as present-but-blank, and signing fails
with *"The key ID must be in a valid form"*.

Signing is skipped when no key is configured, so `publishToMavenLocal` works
without any of this.

## Cutting a release

1. Confirm `master` is green: `./gradlew build test`, plus
   `./gradlew :grid-core:connectedDebugAndroidTest` on a device.
2. Run benchmarks on a **physical device** and check for regressions.
3. Set the release version in the root `build.gradle.kts`.
4. Move `[Unreleased]` items in `CHANGELOG.md` under the new version and date it.
5. Verify artifacts locally:
   ```bash
   ./gradlew publishToMavenLocal
   find ~/.m2/repository/io/github/hagosalema -name '*.asc' | wc -l    # expect 15
   ```
6. Commit and tag:
   ```bash
   git commit -am "Release <version>"
   git tag -a v<version> -m "ComposeGrid <version>"
   git push origin master --tags
   ```
7. The tag triggers `.github/workflows/release.yml`. It runs tests, proves
   signing works locally, then publishes. It can also be run manually via
   *Actions → Release → Run workflow*.
8. Confirm the artifacts resolve — allow a few minutes for the CDN:
   ```bash
   curl -s -o /dev/null -w "%{http_code}\n" \
     https://repo1.maven.org/maven2/io/github/hagosalema/grid-core/<version>/grid-core-<version>.pom
   ```
9. Create the GitHub release from the tag, pasting that version's changelog
   section.
10. Bump the version on `master` for the next line.

## Publishing is irreversible

Released coordinates can never be withdrawn or overwritten — a mistake can only
be superseded by a higher version, and the bad one stays visible forever. Before
tagging, check the groupId is one you own, the version is what you intend and
isn't already released, and that CI is green.

`release.yml` uses `publishAndReleaseToMavenCentral`, which auto-releases without
a staging pause. Switch to `publishToMavenCentral` if you'd rather inspect the
upload in the Portal and release it by hand.

## Troubleshooting

**`Cannot get stagingProfiles for account …: (402)` from
`createStagingRepository`.** The build is talking to legacy OSSRH/Nexus instead of
the Central Portal, and a Portal account has no OSSRH staging profiles. A bare
`publishToMavenCentral()` defaults to `SonatypeHost.DEFAULT` —
`https://oss.sonatype.org`. Every module passes `SonatypeHost.CENTRAL_PORTAL`
explicitly for this reason; don't drop it. Telltale sign: the Portal shows **no
deployment at all**, because nothing reached it.

**`The key ID must be in a valid form … given value:` (empty).** A
`signingInMemoryKeyId` property is present but blank — usually an env var wired
to a nonexistent secret. Remove the mapping.

**No `.asc` files produced.** The *Verify signing works* step in `release.yml`
catches this before anything uploads. Usually `SIGNING_IN_MEMORY_KEY` isn't the
raw armored block.

**`401`/`403` on upload.** Credentials must be the Portal *user token* pair, not
the account login.

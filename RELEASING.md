# Releasing

How to cut a ComposeGrid release to Maven Central.

The build is fully configured for this — POM metadata, signing hooks, and
coordinates are all in place. What remains is account setup and secrets, which
can only be done by the project owner. **Nothing here has been run yet: no
version of ComposeGrid has been published.**

## One-time setup

### 1. The `groupId` — settled

The build declares **`io.github.hagosalema`** in the root `build.gradle.kts`;
every module inherits it.

This is the reverse-DNS of `hagosalema.github.io` and maps to the GitHub account
the namespace is verified against. It does *not* conflict with the GitHub Pages
site — Maven coordinates are identifiers in Central's index, never resolved over
HTTP against the domain, and GitHub-based namespaces are verified by creating a
temporary public repo rather than by DNS records or files on the site.

The Kotlin packages remain `io.github.composegrid`. groupId and package name
don't have to agree, and renaming packages would break every consumer import for
no benefit.

> An earlier draft used `io.github.composegrid` as the groupId. That cannot be
> verified without a GitHub user or org literally named `composegrid`, which
> doesn't exist. If you ever create such an org, moving to it is a groupId
> change — and after a release that means new coordinates, since published ones
> can't be renamed.

### 2. Sonatype Central Portal account

1. Register at https://central.sonatype.com.
2. Add and verify the namespace chosen above (GitHub-based namespaces verify by
   proving control of the account/org).
3. Generate a user token — this yields a username/password pair, not your login
   credentials.

### 3. GPG signing key

Maven Central requires signed artifacts.

```bash
gpg --full-generate-key                 # RSA 4096, no expiry or a long one
gpg --list-secret-keys --keyid-format=long
gpg --keyserver keyserver.ubuntu.com --send-keys <LONG_KEY_ID>   # must be discoverable
gpg --export-secret-keys --armor <LONG_KEY_ID> | base64           # for CI
```

Keep the private key and passphrase out of the repo. Nothing in this project
reads them from a checked-in file.

### 4. Credentials

Local, in `~/.gradle/gradle.properties` (never the repo):

```properties
mavenCentralUsername=<portal token username>
mavenCentralPassword=<portal token password>

signingInMemoryKey=<base64 armored private key, single line>
signingInMemoryKeyPassword=<key passphrase>
```

For CI, add the same four as repository secrets named `MAVEN_CENTRAL_USERNAME`,
`MAVEN_CENTRAL_PASSWORD`, `SIGNING_IN_MEMORY_KEY`, and
`SIGNING_IN_MEMORY_KEY_PASSWORD`. `.github/workflows/release.yml` maps them to
the Gradle properties above.

## Cutting a release

1. Confirm `master` is green: `./gradlew build test` plus
   `./gradlew :grid-core:connectedDebugAndroidTest` on a device.
2. Run the benchmarks on a **physical device** and sanity-check for regressions:
   `./gradlew :benchmark:connectedBenchmarkAndroidTest`.
3. Set the release version in the root `build.gradle.kts` (drop any `-SNAPSHOT`).
4. Move the `[Unreleased]` items in `CHANGELOG.md` under the new version, and
   date it.
5. Verify the artifacts build and sign locally:
   ```bash
   ./gradlew publishToMavenLocal
   ls ~/.m2/repository/io/github/hagosalema/grid-core/<version>/
   ```
   Expect `.aar`, `-sources.jar`, `-javadoc.jar`, `.module`, `.pom`, and an
   `.asc` alongside each.
6. Commit, tag, push:
   ```bash
   git commit -am "Release <version>"
   git tag -a v<version> -m "ComposeGrid <version>"
   git push origin master --tags
   ```
7. Publish. Either push the tag and let `release.yml` do it, or locally:
   ```bash
   ./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
   ```
8. Create the GitHub release from the tag, pasting that version's changelog
   section.
9. Bump to the next `-SNAPSHOT` on `master`.

## Before you publish, read this

**Publishing to Maven Central is irreversible.** Released coordinates can never
be deleted or overwritten — a mistake can only be superseded by a higher
version, and the bad one stays visible forever. Worth double-checking:

- The `groupId` is the one you actually own (step 1). Wrong namespace is the
  most common irreversible mistake.
- The version is what you intend, and isn't already released.
- `0.1.0` signals an unstable API. Do not jump to `1.0.0` until you are willing
  to keep the current public surface — per the plan, `1.0.0` is the point at
  which breaking changes require a major bump.

## `1.0.0` checklist

Beyond the mechanics, the plan's remaining open decisions
([`DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md) §6) should be settled first:

- **Name** — confirm "ComposeGrid" doesn't collide with an existing library on
  Maven Central or a prominent GitHub project.
- **`groupId`** — as above.
- **Repo/org** — personal account versus a dedicated org.
- **API freeze** — `1.0.0` is a commitment. The API surface has moved
  substantially during M6/M7 (the `GridStyle` slots and the sorting helpers are
  both new), so it is worth living with `0.1.0` through some real usage before
  freezing.

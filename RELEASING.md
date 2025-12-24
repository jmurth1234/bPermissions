# Release Guide

This document explains how bPermissions packages are published to GitHub Packages.

## Two Publishing Workflows

### 1. Automatic Snapshot Builds (Development)

**Workflow:** `.github/workflows/build.yml`

**Trigger:** Every commit to `master` branch

**Version format:** `2.13.0-{BUILD_NUMBER}` (e.g., `2.13.0-123`)

**Published artifacts:**
- `de.bananaco:bPermissions-API:2.13.0-123`
- `de.bananaco:bPermissions-Bukkit:2.13.0-123` (+ `-all` shadow JAR)
- `de.banaco:bPermissions-Bukkit:2.13.0-123` (legacy group ID, deprecated)
- `de.bananaco:bPermissions-Sponge:2.13.0-123-alpha` (+ `-all` shadow JAR)

**Use case:** Development builds, testing, continuous integration

---

### 2. Official Releases

**Workflow:** `.github/workflows/release.yml`

**Trigger:**
- Pushing a git tag matching `v*.*.*` (e.g., `v2.13.0`)
- Manual workflow dispatch with version input

**Version format:** `2.13.0` (clean version, no build number)

**Published artifacts:**
- `de.bananaco:bPermissions-API:2.13.0`
- `de.bananaco:bPermissions-Bukkit:2.13.0` (+ `-all` shadow JAR)
- `de.banaco:bPermissions-Bukkit:2.13.0` (legacy group ID, deprecated)
- `de.bananaco:bPermissions-Sponge:2.13.0-alpha` (+ `-all` shadow JAR)

**Additional actions:**
- Creates a GitHub Release with release notes
- Attaches JAR files as release assets
- Marks alpha/beta/rc versions as pre-releases

**Use case:** Stable releases for end users

---

## How to Create a Release

### Method 1: Using Git Tags (Recommended)

1. **Update the version** in `build.gradle`:
   ```bash
   # Edit build.gradle and change:
   baseVersion = '2.14.0'  # Update to your new version
   ```

2. **Commit the version bump:**
   ```bash
   git add build.gradle
   git commit -m "chore: Bump version to 2.14.0"
   git push origin master
   ```

3. **Create and push a tag:**
   ```bash
   git tag v2.14.0
   git push origin v2.14.0
   ```

4. **Monitor the release:**
   - Go to Actions tab in GitHub
   - Watch the "Release" workflow run
   - Check the Releases page for the new release

### Method 2: Manual Workflow Dispatch

1. **Go to GitHub Actions:**
   - Navigate to your repository on GitHub
   - Click "Actions" tab
   - Select "Release" workflow from the left sidebar

2. **Run workflow:**
   - Click "Run workflow" button
   - Enter the version (e.g., `2.14.0`) - do NOT include the 'v' prefix
   - Click "Run workflow"

3. **Monitor the release:**
   - Watch the workflow run
   - Check the Releases page for the new release

---

## Version Numbering

### Semantic Versioning

bPermissions follows semantic versioning: `MAJOR.MINOR.PATCH`

- **MAJOR:** Breaking changes, incompatible API changes
- **MINOR:** New features, backward-compatible
- **PATCH:** Bug fixes, backward-compatible

### Pre-release Versions

For pre-releases, use suffixes:
- **Alpha:** `v2.14.0-alpha`, `v2.14.0-alpha.1`
- **Beta:** `v2.14.0-beta`, `v2.14.0-beta.1`
- **Release Candidate:** `v2.14.0-rc.1`

The release workflow automatically marks these as pre-releases on GitHub.

### Sponge Module

The Sponge module always has `-alpha` appended to its version since it's in early development:
- Development: `2.13.0-123-alpha`
- Release: `2.13.0-alpha`

---

## Published Packages Location

All packages are published to:
```
https://github.com/jmurth1234/bpermissions/packages
```

You can view them in your repository under the "Packages" section.

---

## Consuming Published Packages

### For Development Builds (Snapshots)

Use the latest build number from the Actions tab:

```groovy
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/jmurth1234/bpermissions")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") ?: System.getenv("TOKEN")
        }
    }
}

dependencies {
    // Find the latest build number from GitHub Actions
    implementation 'de.bananaco:bPermissions-API:2.13.0-456'
}
```

### For Stable Releases

Use the release version from the Releases page:

```groovy
dependencies {
    implementation 'de.bananaco:bPermissions-API:2.14.0'
}
```

### Shadow JARs (Fat JARs)

For Bukkit and Sponge modules with bundled dependencies:

```groovy
dependencies {
    // Shadow JAR with all dependencies included
    implementation 'de.bananaco:bPermissions-Bukkit:2.14.0:all'
    implementation 'de.bananaco:bPermissions-Sponge:2.14.0-alpha:all'
}
```

---

## Troubleshooting

### Release Workflow Fails

**Check the version format:**
- Tags must start with 'v': `v2.14.0` ✅ not `2.14.0` ❌
- Manual dispatch version should NOT have 'v': `2.14.0` ✅ not `v2.14.0` ❌

**Check permissions:**
- Ensure the workflow has `packages: write` permission
- Verify GITHUB_TOKEN is available (it's automatic)

### Packages Not Appearing

**Wait a moment:**
- It can take 1-2 minutes for packages to appear after publishing

**Check workflow logs:**
- Go to Actions tab
- Click on the failed/successful workflow run
- Check the "Publish to GitHub Packages" step

### Version Conflicts

**Clean local Maven cache:**
```bash
rm -rf ~/.m2/repository/de/bananaco/bPermissions-*
rm -rf ~/.m2/repository/de/banaco/bPermissions-*
```

**Use --refresh-dependencies in Gradle:**
```bash
./gradlew build --refresh-dependencies
```

---

## Deprecation Notice: de.banaco Group ID

The Bukkit module is currently published under two group IDs:
- ✅ `de.bananaco` - **Correct, use this**
- ⚠️ `de.banaco` - **Legacy typo, deprecated**

**Migration timeline:**
- The legacy group ID will be removed in version 3.0.0
- Update your dependencies to use `de.bananaco` before then

---

## Checklist for Releases

- [ ] All tests passing on master
- [ ] Version bumped in `build.gradle`
- [ ] CHANGELOG updated (if you maintain one)
- [ ] Tag created with correct format (`v*.*.*`)
- [ ] Release workflow completed successfully
- [ ] GitHub Release created with notes
- [ ] Packages visible in GitHub Packages
- [ ] Tested downloading and using the published artifacts

---

## Questions or Issues?

If you encounter problems with the release process:
1. Check the workflow logs in GitHub Actions
2. Review this guide for common issues
3. Open an issue on the repository

# KSerial Release Checklist

Use this checklist before publishing a stable version.

## Version and repository

- Set `appVersion=x.y.z` in `gradle.properties`.
- Confirm README files, configuration samples, `ROADMAP.md`, and release notes match the shipped behavior.
- Confirm the working tree contains only intentional release changes.

## Automated verification

```powershell
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat :composeApp:packageReleaseDistributionForCurrentOS :composeApp:createReleaseDistributable
```

- Confirm unit tests cover settings, HEX parsing, checksums, framing, simulator rules, routes, filters, and history.
- Launch the portable application once and confirm `conf/settings.conf` and all six samples are created without opening a serial port.
- Smoke-test a real or paired virtual serial port: open, send, receive, reconnect, and close.

## Platform packages

- Windows: verify `.msi`, `.exe`, and portable `.zip`.
- macOS: verify `.dmg`, `.pkg`, and portable `.zip`.
- Linux: verify `.deb`, `.rpm`, and portable `.tar.gz`.
- Verify `SHA256SUMS.txt` against every uploaded asset.
- Document that unsigned Windows and macOS builds may show an operating-system warning.

## Publish

- Push the matching `vX.Y.Z` tag to publish automatically, or run the workflow manually with `publish` enabled.
- Confirm all three platform jobs succeed before announcing the release.
- Download at least one release asset from GitHub and perform a clean-machine launch check.
- Check the generated release title, notes, version badge, download links, and checksums.

Code signing and macOS notarization require external certificates and credentials; enable them only after those secrets are configured in the repository.

## Summary

<!-- What changed, and which NAS hardware/layout or user problem does it address? -->

## Hardware / app context

<!-- Model, ZettOS/kernel/architecture, Android version/device where relevant. Remove identifiers. -->

## Verification

- [ ] `cd watchdog && go test -race ./... && go vet ./...`
- [ ] Docker AMD64 build and Compose config validated (for backend/config changes)
- [ ] `cd android && ./gradlew testDebugUnitTest lintDebug assembleDebug` (for app changes)
- [ ] Real hardware behavior checked, or explicitly listed as not tested below
- [ ] Documentation/support matrix updated for behavior or configuration changes

### Commands and results

```text

```

## Safety and compatibility

- [ ] No firmware or Car Thing changes are bundled into this NAS Watchdog PR
- [ ] Existing sensors/API fields remain compatible, or migration is explained
- [ ] Empty/missing/extra drives, sensors, fans, and containers are handled
- [ ] Actions/auto-reboot remain safe and disabled by default
- [ ] No secrets, `.env`, serials, tokens, notification URLs/topics, IPs, raw logs, or private fixtures are included

## Not tested / limitations

<!-- Be explicit. Compilation is not on-device UI or hardware validation. -->

## Screenshots

<!-- For web/Android visual changes, when available. Redact server and device details. -->

# Runtime Validation Report

**Generated**: 2026-10-27T21:23:00Z  
**Target**: Mytheria Origins, Paper 26.3

## Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Build | PASS | 0 | `mvn -q clean package`; compiled against `26.3.build.227-stable` |
| Startup | PASS | 0 | Paper `26.3-227`; Java 25; server reached `Done` |
| Plugin load | PASS | 0 | `MytheriaOrigins v1.0.0` loaded and enabled without errors |
| Player protocol smoke test | PARTIAL | 0 | Protocol 777 connection reached login success; no full client library was available for configuration/play packets |
| GUI and abilities | UNVERIFIED | n/a | Requires a real 26.3 client or Eaglercraft client session |

**Overall**: NEEDS_SIGNOFF

## Environment

- Paper: `26.3-227-main@7f9c797`
- Implementing API: `26.3.build.227-stable`
- Java: OpenJDK 25.0.4.1
- Server port: `25575` (isolated test server)
- Docker: available, not required for this plugin
- Node.js: available; a temporary protocol-client package installation required user approval and could not be used

## Evidence

The live server log contains:

```text
This server is running Paper version 26.3-227-main
Implementing API version 26.3.build.227-stable
[MytheriaOrigins] Enabling MytheriaOrigins v1.0.0
[MytheriaOrigins] Mytheria Origins is ready.
Done (17.039s)! For help, type "help"
```

An offline protocol 777 smoke client completed the handshake and received
login success. It did not complete the 26.3 configuration phase, so it was not
used to claim GUI or ability interaction coverage.

## Remaining live-client checks

Run the checklist in [README.md](./README.md) with two real players before
production release. In particular, verify all cooldowns, inventory relic
activation, the private vault, cross-player damage, water extinguishing, and
Eaglercraft menu rendering.

# RapidPro Surveyor

[![Build Status](https://github.com/rapidpro/surveyor/workflows/CI/badge.svg)](https://github.com/rapidpro/surveyor/actions?query=workflow%3ACI)
[![codecov](https://codecov.io/gh/rapidpro/surveyor/branch/main/graph/badge.svg)](https://codecov.io/gh/rapidpro/surveyor)

Surveyor is an offline client for [RapidPro](https://github.com/rapidpro/rapidpro). It allows flows
to be run offline, i.e. without internet access, and the results to be uploaded later when internet
is available.

RapidPro Surveyor is open source (BSD). Copyright 2019, UNICEF.

<p align="center">
  <img src="https://raw.githubusercontent.com/rapidpro/surveyor/master/screens/login.png" width="150">
  <img src="https://raw.githubusercontent.com/rapidpro/surveyor/master/screens/org.png" width="150">
  <img src="https://raw.githubusercontent.com/rapidpro/surveyor/master/screens/flow.png" width="150">
  <img src="https://raw.githubusercontent.com/rapidpro/surveyor/master/screens/run.png" width="150">
</p>

## Compatibility

### Server (RapidPro + Mailroom)

Surveyor is an offline client for a RapidPro instance and its **Mailroom** component. It depends on
three server capabilities:

1. RapidPro **API v2** with surveyor support: `/api/v2/authenticate` (with `role=S`), `org.json`,
   `fields.json`, `groups.json`, `flows.json`, `definitions.json`, `boundaries.json`, `media.json`.
2. A **Mailroom** that still serves the surveyor submission endpoint `POST /mr/surveyor/submit`.
3. Flows exported at **flow spec ≤ 13.x** that the embedded engine can run.

**Verified working target (pin the server to this):** RapidPro **7.4.x** (Feb-2024 era) with
**Mailroom v9.1.9** — the last Mailroom release containing surveyor code. Upstream removed surveyor
support in Mailroom **v9.1.10** (2024-02-23) and archived Surveyor; newer stacks (RapidPro v8.0+/v9+
with Mailroom ≥ v9.1.10) will **not** accept submissions. Pre-Mailroom-era RapidPro (v4/v5) is also
unsupported.

> Quick check on your live server: `curl -si -X POST https://<host>/mr/surveyor/submit`
> → any 400/401 = endpoint present; **404 = surveyor removed** (Surveyor won't work).

### Flow specification support (embedded engine)

The app runs flows through an embedded goflow engine binary.

| Spec version | Support |
|---|---|
| 10.x | ✘ Not parseable |
| 11.x | ✔ Via built-in migration |
| 12.x | ✘ Rejected (internal transitional version) |
| 13.0 – 13.5 | ✔ (engine reports `currentSpecVersion` 13.1.0; keep published flows ≤ 13.3 for safety) |
| 14.0+ | ✘ Unsupported → Surveyor prompts to update the app |

### Server configuration checklist

To make an org's flows available offline:

- [ ] Flows are of type **survey** (`/api/v2/flows.json?type=survey&archived=false` is what Surveyor fetches).
- [ ] The user's account has the **Surveyor** role (`role=S`), so `/api/v2/authenticate` returns org tokens.
- [ ] Mailroom is pinned to a surveyor-capable build (≤ v9.1.9).
- [ ] Published survey flows are at spec ≤ 13.x.
- [ ] HTTPS is reachable from the field devices (the app does not allow cleartext HTTP).



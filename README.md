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

**Verified working target:** RapidPro **v9.0.0** (`UbuhingaVizion/rapidpro` @ `modern`, Django 5.2)
with the **AGPL `rapidpro/mailroom`** (v9.0.0), which retains `POST /mr/surveyor/submit`. The BSL
`nyaruka/mailroom` dropped that endpoint in **v9.1.10** (2024-02-23), so use the AGPL fork for v9+.
The web app still exposes the full surveyor surface (`role=S`, `surveyor_password`, `org_surveyor*`,
`Flow.TYPE_SURVEY`). Pre-Mailroom-era RapidPro (v4/v5) is unsupported.

> Quick check on your live server: `curl -si -X POST https://<host>/mr/surveyor/submit`
> → any 400/401 = endpoint present; **404 = surveyor removed** (Surveyor won't work).

### Flow specification support (embedded engine)

The app runs flows through an embedded goflow engine binary.

| Spec version | Support |
|---|---|
| 10.x | ✘ Not parseable |
| 11.x | ✔ Via built-in migration |
| 12.x | ✘ Rejected (internal transitional version) |
| 13.x | ✔ The bundled engine accepts any spec whose **major** version is 13 (e.g. RapidPro's current `13.2.0`); it reports `currentSpecVersion` 13.1.0 for the flows it writes itself. |
| 14.0+ | ✘ Unsupported → Surveyor prompts to update the app |

### Server configuration checklist

To make an org's flows available offline:

- [ ] Flows are of type **survey** (`/api/v2/flows.json?type=survey&archived=false` is what Surveyor fetches).
- [ ] The user's account has the **Surveyor** role (`role=S`), so `/api/v2/authenticate` returns org tokens.
- [ ] Mailroom is a build that still serves `POST /mr/surveyor/submit` (the AGPL `rapidpro/mailroom`).
- [ ] Published survey flows have spec **major version 13** (e.g. RapidPro v9's `13.2.0`).
- [ ] HTTPS is reachable from the field devices (the app does not allow cleartext HTTP).



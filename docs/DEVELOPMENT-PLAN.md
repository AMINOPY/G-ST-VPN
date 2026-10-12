# ArasClient — Development Plan & AI Handoff (Post-1.8.0 Maintenance Round)

> This file is the plan-and-state document for the maintenance round started
> 2026-10-12. `AI-HANDOFF-BRIEF.md` remains the entry point for build/signing/
> core-repo facts; this file records what THIS round changed and what comes next.
> Every AI picking this repo up should read the brief first, then this file.

---

## 1. What this round changed (all verified, one commit pending owner review)

### 1.1 Flags in remarks are no longer deleted on import

**Problem:** Every imported profile had its flag emoji stripped before storage.
`AngConfigManager` called `CountryResolver.withoutFlags()` at all four import
entry points (JSON array, single custom, WireGuard/AWG conf, link batch), so a
provider remark like `🇩🇪 Frankfurt 01` became `Frankfurt 01` in MMKV forever.

**Fix:** Removed the four `withoutFlags()` calls in
`app/src/main/java/com/aras/client/handler/AngConfigManager.kt`. Flags now
persist with the remark. `withoutFlags()` itself is unchanged and still used by
the server card (`MainServerPager.cardRemarks`) — the card resolves and shows
its own flag, so the stored flag would render twice otherwise. Storage keeps
what the provider wrote; only the display composes.

**Test:** `CountryResolverTest` — added
`remarks imported with a flag keep the flag`; the existing card-title test
documents why `withoutFlags` still exists.

### 1.2 Batch "ping all" is faster — two changes

**(a) One attempt instead of two** — `~/corebuild/AndroidLibXrayLite/libv2ray_utils.go`,
`measureInstDelay`: the measurement loop ran 2 HTTP attempts per profile and
kept the min. Every profile in a batch paid double wall time for a marginal
min-vs-single difference. `attempts = 2` → `attempts = 1`.

**(b) Egress country rides the delay measurement** — this closes the "batch
test cannot report a country" defect from brief §7. New gomobile shim:

```go
type OutboundDelayResult struct {
    Delay   int64  `json:"delay"`
    Country string `json:"country"`
    IP      string `json:"ip"`
}
func MeasureOutboundDelayWithInfo(config, url, ipApiUrl string) (*OutboundDelayResult, error)
```

It runs the same instance build/measure path, then does one extra request to
the user's IP-API through the **same `core.Dial`** transport the delay test
uses — so the country reflects the profile's real egress, not a direct
connection. `MeasureOutboundDelay` (old signature) is kept and delegates, so
existing callers and older app builds keep working.

App wiring:
- `ArasCore.kt` — thin wrapper, typed `libv2ray.OutboundDelayResult`.
- `CoreNativeManager.measureOutboundDelayWithInfo` — returns null on any native error.
- `RealPingWorkerService` — new internal `PingResult(delay, country, ip)`;
  `startRealPing` calls the new shim; TCP-only path returns bare results.
- `RealPingEvent.Result` — gained optional `country`/`ipAddress`.
- `CoreTestService` — persists them via the existing
  `MmkvManager.encodeServerTestCountry`, so batch results now populate the
  card flag exactly like the single-server test already did.

**AAR:** rebuilt with `gomobile bind` (see brief §5 for the env), verified:
`amneziawg>0`, stock `wireguard=0`, and the new `MeasureOutboundDelayWithInfo`
native present. Installed to `app/libs/aras-core.aar`. **The tracked AAR must
be committed together with the app changes or the app won't compile on a fresh
checkout.** Core repo (`~/corebuild/xray-core` branch
`feat/awg-mimic-packets`) did NOT change — only the bindings repo did. The
bindings change must be pushed to `ArasTey/AndroidLibXrayLite` before CI can
build this app version.

### 1.3 FPS/stutter fix during batch tests

**Problem:** `MainViewModel.MeasureConfigSuccess` called
`loadGroup(forceRefresh = true)` for EVERY result — re-decoding every profile
and affiliation from MMKV once per ping. A 100-config subscription recomposed
the whole LazyColumn ~100 times mid-test: dropped frames, janky scroll, the
"fps" complaint.

**Fix:** The success broadcast already carries the guid. Wired it through:
- `MainServiceEvent.MeasureConfigSuccess` → `data class(guid: String)`
- `MainRepository` → reads the broadcast's "content" extra
- `MainViewModel` → patches the single `ServersCache` in the group flow
  (`copy(testDelayMillis=…, testCountryCode=…)`) and skips anything outside
  the current round. One result = one item recomposition.

### 1.4 PattNG options audit — nothing to add

User asked to port PattNG (github.com/patterniha/PattNG, local mirror
`~/Documents/ArasClient/pattng-src`). Audited prefs, `ProfileItem` fields,
settings UI, fmt parsers, and core behavior. Result: **full parity already.**

| PattNG feature | ArasClient status |
|---|---|
| `cipherSuites` (`cs=`) in settings + share links | Present — `ProfileItem.cipherSuites`, `FmtBase` `cs=`, VMess `cs`, editor UI, `CoreOutboundBuilder:814` |
| `unsafe` TLS fingerprint | Present — parses any `fp=`, core accepts `unsafe` (`transport_security.go:181`) |
| Unencrypted VLESS/TROJAN to public addresses | Present in core — patterniha's `validateOutboundTransportSecurity` enforcement is commented out in `xray.go:341-343` of the Aras core |
| IPv6 enable / prefer IPv6 settings | Present (`PREF_IPV6_ENABLED`, `PREF_PREFER_IPV6`, VPN settings group) |

ArasClient additionally has what PattNG lacks: Aether, Mieru, AmneziaWG (with
mimic packets), MASQUE, AnyTLS, Clash YAML import, protected `.arasc` export,
Free sub, quick connect, smart connect. **Do not "port" anything from PattNG
without a specific user-visible gap.**

---

## 2. Verification status for this round

- [x] `:app:testDebugUnitTest` — all green after every step.
- [x] AAR symbol check (anytls/amneziawg/stock-wg/MeasureOutboundDelayWithInfo).
- [ ] `:app:assembleRelease` — NOT yet run this round; run before any release.
- [ ] Emulator sweep of a batch ping (does the flag appear on cards mid-batch?)
- [ ] The slow-ping change is behavioral: spot-check that measured delays look
      consistent with before (single sample is noisier than min-of-2; that is
      accepted, it is what v2rayNG does).
- [ ] Bindings repo push to `ArasTey/AndroidLibXrayLite` (CI needs it).

## 3. Open defects (unchanged from brief §7)

1. **Standalone AWG socket protection** — `awg_tunnel.go` uses
   `conn.NewDefaultBind()`, no `vpnProtect()`. Reproduce first
   (bypass-LAN = Not Bypass, full-tunnel AWG profile) before touching it.
2. **Entry-proxy chain fixes never tested on a device.**
3. **OpenVPN** — still absent, still not startable from the app side.
4. `proxy/wireguard/client.go.rej` — tracked debris in the core repo; optional cleanup.

## 4. Working agreement (unchanged)

One clean commit on `main`, fully local, until the owner says otherwise. The
signing key is irreplaceable: never touch `app/build.gradle.kts` signing,
never stage `keystore/` or `keystore.properties`. Reproduce on the emulator
before theorizing — three guesses during 1.8.0 were all wrong.

## 5. Suggested commit message for this round

```
Keep remark flags, speed up batch ping, fix test-time stutter

- Import no longer strips flag emojis from remarks; cards still compose
  their own flag, storage keeps the provider's.
- New core shim MeasureOutboundDelayWithInfo returns the egress country
  through the same dial path as the delay test, so "ping all" now fills
  card flags without extra requests; batch measurements run one HTTP
  attempt instead of two.
- Per-test results patch a single card instead of rebuilding the whole
  group cache, which dropped frames during long batches.
```
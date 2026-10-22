
#   RADAR VPN

**A modern, fast and feature-rich VPN client for Android.**

[![Release](https://img.shields.io/github/v/release/AMINOPY/G-ST-VPN?style=for-the-badge&logo=github&color=0284c7&labelColor=101418)](https://github.com/AMINOPY/G-ST-VPN/releases)
[![License](https://img.shields.io/badge/GPL--3.0-licensed?style=for-the-badge&color=38bdf8&labelColor=101418)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-7.0%2B-3ddc84?style=for-the-badge&logo=android&labelColor=101418)]()

</div>

---

## 🧬 Based on PattNG

RADAR VPN is a **fork of [PattNG](https://github.com/patterniha/PattNG)**, which itself is a fork of [2dust/v2rayNG](https://github.com/2dust/v2rayNG).

> **RADAR VPN → PattNG → v2rayNG**

RADAR VPN keeps the foundation of PattNG while adding its own application features, configuration workflows, UI improvements, subscription tools and networking/core integrations.

### Upstream projects

- **PattNG:** https://github.com/patterniha/PattNG
- **Original v2rayNG:** https://github.com/2dust/v2rayNG

PattNG itself documents additions such as `cipherSuites`, unsafe fingerprint configuration/sharing, the **aether** core, and related Xray-core changes. RADAR VPN builds on that lineage rather than presenting itself as an independent implementation from scratch.

---

## ✨ Highlights

| | Feature |
|:---:|---|
| ⚡ | **Smart Connect** — test available servers and connect to a fast server automatically |
| 📶 | **Latency testing & global sorting** — test servers and reorder the list based on measured results |
| 🔄 | **Cross-subscription workflow** — manage and evaluate servers from multiple subscriptions |
| 💾 | **Persistent test information** — retain useful test/latency information with profiles |
| 🧹 | **Duplicate & invalid cleanup** — keep large server lists clean and manageable |
| 📥 | **Batch import** — import multiple configurations efficiently |
| 🔐 | **`.arasc` container** — RADAR VPN's own configuration/subscription export format |
| 🛡️ | **Protected configurations** — restrict access to sensitive configuration data while keeping profiles usable |
| 📊 | **Subscription information** — display traffic usage, total traffic and expiry information when available |
| 📢 | **Provider announcements** — show provider/creator messages inside the client |
| 📤 | **Flexible sharing** — share configurations through QR, TXT and `.arasc` workflows |
| 📱 | **Per-app proxy & routing** — Android application-level proxying and routing capabilities |
| 🌗 | **Light & dark themes** — a clean interface for both day and night |

---

## 🔌 Protocol Support

RADAR VPN supports the following configuration types exposed by the client:

`VLESS` · `VMess` · `Trojan` · `Shadowsocks` · `Hysteria2` · `Hysteria` · `WireGuard` · `SOCKS` · `HTTP` · `AnyTLS` · `AmneziaWG` · `MASQUE`

`MASQUE` is import-only: it arrives through a `masque://` link, a pasted config, a QR code, or a `.arasc` file, and deliberately has no "Add manually" entry — there is no editor screen for it, so the server menu hides Edit for it rather than opening an editor that would save the profile back as a different protocol. The `masque` and `xdrive` transports appear in the transport picker of any protocol that supports them.

It also supports proxy chains and policy groups where supported by the underlying configuration/core.

### Aether / WARP gateway profiles

Use **Add Aether config manually**, found in the import menu directly below *Import .arasc File*.

Aether's data path is not something RADAR VPN can dial with its own outbound: the tunnel needs a
scanned WARP endpoint and a WARP identity, and both live in the aether core. So the core runs as a
child process and the profile's outbound is a SOCKS hop to its loopback listener — which is what the
Aether app and PattNG both do.

The core ships in the APK as `libaether.so`, with Psiphon's client and Tor's pluggable transport
beside it, for `arm64-v8a`, `armeabi-v7a` and `x86_64`. It is packaged rather than downloaded at
runtime because a file in the app's data directory is not executable on Android 10 and later, and only
`nativeLibraryDir` is. This costs about 54 MB of native library per ABI.

**What the editor offers**, all of it carried in `aether://` links and `.arasc` files:

- **Protocol** — `wg`, `masque`, `gool` and `mim`, the last two being the two-hop tunnels
- **Scan mode**, **obfuscation**, **IP version** and the MASQUE **transport**
- ClientHello **fragmentation** and **ECH** on the h2 carrier
- **Scan for an endpoint** and **Get a new WARP key**, both running a short-lived core; a renewal that
  fails leaves the working identity in place
- **Psiphon** and **Tor**, each inside the tunnel, around it, or alone with no WARP

A note on Psiphon in this build: the server list is handed over through the core's own
psiphon config, and the profile's CDN edge-list field is kept for link round-trip but has
no effect — aether 2.1 decides the edge lists from the Psiphon mode and its built-in list.

Two pairings the core refuses are caught in the editor rather than at connect time, because each is a
configuration that builds and then does not work: either carrier in *reverse* needs `masque`, and the
two of them go together only nested, one inside the tunnel and the other around it.

Aether profiles are import-only in the sense that there is no fixed single endpoint to type in — leave
the address empty and the core sweeps for a reachable gateway and keeps the fastest it finds.

**A WARP gateway usually does not answer a plain ping, and its exit address reads as Iranian.** Both
are expected: the address Cloudflare hands out is not a host that answers ICMP, and the exit it routes
to is geolocated that way. Judge these profiles by whether they connect, not by the ping column.

If Cloudflare ever stops accepting a saved device, the core says so on startup and the tunnel
handshakes while carrying nothing. **Reset WARP key** forgets the identity and the remembered
endpoint so the next start registers a new one.

---

## ⚡ Smart Connect

RADAR VPN is built around a simple connection workflow:

```text
Test → Measure → Sort → Select → Connect
```

Features include:

- Test a single server
- Test all servers
- Run real connection tests
- Measure server latency
- Keep test results associated with profiles
- Sort servers using test results
- Keep untested servers after tested results
- Automatically connect after the test workflow
- Work across servers from multiple subscriptions

---

## 📡 Subscription Management

RADAR VPN provides an extended subscription workflow for managing large server collections:

- Update individual subscriptions
- Update all subscriptions
- Batch configuration import
- Subscription-aware profile management
- Traffic usage parsing
- Total traffic parsing
- Expiry-time parsing
- Subscription metadata display
- Cache-aware profile reconstruction
- GeoIP refresh workflow
- Provider announcements
- Free subscription synchronization and management

---

## 🧹 Configuration Management

Keep large configuration lists organized with:

- Multi-field search
- Search by remark
- Search by description
- Search by server
- Search by configuration type
- Duplicate identity detection
- Bulk duplicate cleanup
- Invalid-server cleanup
- Group-scoped cleanup
- Global cleanup
- Batch configuration import
- Latency information stored with profiles

---

## 🔐 Protected Configurations

Protected configurations are designed for controlled distribution of configuration profiles.

Depending on the protection mode, a protected profile can be used for connection/testing while sensitive configuration data is restricted from normal application workflows.

Protection workflows include:

- Connect to a protected profile
- Test/ping a protected profile
- Restrict raw URI extraction
- Restrict copying sensitive configuration data
- Restrict sharing
- Restrict exporting protected profiles
- Enforce protection at the application/data layer

---

## 📦 `.arasc` Configuration Container

RADAR VPN provides its own `.arasc` format for exporting and importing configurations and subscriptions.

### Normal export

- Package configurations inside an RADAR VPN container
- Keep exported configuration data out of plain-text config files
- Import the container back into RADAR VPN

### Protected export

- Optional password protection
- Intended for restricted configuration sharing
- Keeps protected profiles usable while restricting normal access to sensitive configuration data

### Subscription-aware export

The format can preserve information related to:

- Subscriptions
- Profiles
- Subscription/profile relationships
- Export metadata
- Export timestamp
- Optional notes

> The protected-container cryptographic implementation is intentionally not described here beyond its public behavior. Refer to the project implementation and distribution for the exact details.

---

## 🛠️ Advanced Networking & Core Integration

RADAR VPN also includes integrations beyond the Android UI layer, including:

- **AnyTLS** integration
- **AmneziaWG** support
- **AmneziaWG 3.1 parameters**
- **Jc / Jmin / Jmax** support for AWG
- **S1 / S2** support for AWG
- **H1 / H2 / H3 / H4** support for AWG
- **Standalone AmneziaWG tunnel** integration
- **AWG UAPI** integration
- **AWG device lifecycle** handling
- **Native TUN file-descriptor integration**
- **Process discovery / Process Finder** integration
- **Process-to-UID mapping**
- **Custom AndroidLibXrayLite integration**
- **Custom Xray-core integration**
- **TLS compatibility fixes**
- **Core/client integration for AWG and AnyTLS**

These components allow the Android client, native networking layer and proxy core to work together as one stack.

---

## 🧩 Architecture

```text
┌──────────────────────────────┐
│        RADAR VPN UI         │
├──────────────────────────────┤
│ Smart Connect                │
│ Subscription Management      │
│ Config Management            │
│ Protected Profiles           │
│ .arasc Import / Export       │
└──────────────┬───────────────┘
               │
┌──────────────▼───────────────┐
│     Android Networking       │
├──────────────────────────────┤
│ TUN Integration              │
│ Process / UID Mapping        │
│ Native Tunnel Support        │
└──────────────┬───────────────┘
               │
┌──────────────▼───────────────┐
│       Core Integration       │
├──────────────────────────────┤
│ Xray                         │
│ AnyTLS                       │
│ AmneziaWG                    │
└──────────────────────────────┘
```

---

## 🤖 AI / Developer Documentation

Documentation for working on the codebase itself:

- **`AI-HANDOFF-BRIEF.md`** — entry point: build commands, release signing, core
  repo layout, and the traps. Read this first.
- **`HANDBOOK.md`** — deep reference for maintenance and release workflows.
- **`docs/DEVELOPMENT-PLAN.md`** — rolling plan: what the current maintenance
  round changed, what is verified, and what is still open.

---

## 📥 Installation

<div align="center">

**→ [Download the latest release](https://github.com/AMINOPY/G-ST-VPN/releases) ←**

</div>

| Build | Target |
|---|---|
| `arm64-v8a.apk` | Most modern Android devices — recommended |
| `universal.apk` | Universal device build |

### Quick start

1. Install RADAR VPN.
2. Add or import your configurations/subscriptions.
3. Run a server test if needed.
4. Use Smart Connect or select a profile manually.
5. Connect.

---

## 🌱 Credits & Upstream

RADAR VPN is a **PattNG fork** and ultimately descends from the v2rayNG project.

- **RADAR VPN:** https://github.com/AMINOPY/G-ST-VPN
- **PattNG:** https://github.com/patterniha/PattNG
- **v2rayNG:** https://github.com/2dust/v2rayNG

Additional components and ideas may originate from their respective upstream open-source projects. Please review the individual licenses and upstream repositories for component-specific attribution.

---

## 📄 License

RADAR VPN is released under the **GNU General Public License v3.0**.

See [LICENSE](LICENSE) for the full license text.

---

## 📬 Contact

<div align="center">


</div>

---

<sub>RADAR VPN</sub>
</div>

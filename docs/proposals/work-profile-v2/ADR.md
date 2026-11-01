# Proposed ADR V2 — first-party Android work-profile controller

**PROPOSED / OWNER TRADEOFF AGREEMENT PENDING / NOT RUNTIME PROVEN**

The user authorized architecture replanning. The exact material tradeoffs were separately presented by the parent; no agreement is recorded in this draft. RM-08c prohibits silently replacing the original model with a limited profile. This ADR therefore does not activate V2 requirements, authorize provisioning or alter the immutable362 ledger. Original plan accepted5/32 tasks remains its own historical progress; V2 accepted0 tasks.

## Proposed decision and unavoidable tradeoffs

Replace embedded third-party virtualization with a minimal first-party DPC plus Android managed/work profile, using public platform APIs and unchanged signed apps. This eliminates dependence on an unqualified embedded engine, **not** all product feasibility risks.

1. **One additional work copy per package**, alongside personal installation; no arbitrary number of Fonesi instances of one package. Existing work profile/MDM blocks provisioning; no takeover/removal.
2. **Shared device-wide app code/version**, separate profile data. Personal or work updates may change both code installations. No independent version pinning; all affected scope must be disclosed and verified. Unknown inaccessible profiles mean update blocked, not incomplete consent.
3. **Suspend/resume and hide/unhide**, not a claimed universal force-stop. Whole-profile pause initially uses owner-operated OS controls; PO status alone does not authorize quiet-mode calls.
4. **OS profile/package principal proof** replaces SDK-private-storage readback. Exact package+signer+profile serial+generation remains mandatory; SDK-private tests are not marked passed by renaming evidence.
5. **Manager action lock only**, not universal app lock. Android launcher/settings may independently reach work apps. A separate work-profile challenge is a separate security decision; no guarantee of intercepted direct OS launches.
6. **Original-source import retained:** accessible, consistent installed-original signed base/split bytes; no Play/download replacement, session copying, signature/package edits or GMS patching as fallback.

Eight mandatory apps remain individually UNKNOWN, not dropped. No promise of100% compatibility or API26–37/OEM coverage. Green branding, six accents, Java/View+SQLite and existing min26/target36 build baseline remain unchanged unless a later explicit plan change is justified.

## Proposed components and trust boundaries

- Personal-side manager: visible launchable source inventory, source cluster snapshot, metadata, preview/consent and exact accessible-profile launch. No guest credentials/data.
- Work-side first-party DPC: verified profile-owner role, scoped installation/control/readback of that profile only. Native Android runs third-party apps under its normal sandbox, not inside Fonesi's UID.
- Bounded cross-profile transport: a design/proof prerequisite, not assumed Binder reachability. Use only supported system-routed cross-profile intents/URI grants if permitted. Authenticate exact own signature/caller/profile, allowlist commands, bind operation/source digest/profile serial/generation, cap sizes/lifetimes and revoke grants. No general file proxy, implicit untrusted intents, arbitrary endpoint or persistent unrestricted exported service. If secure original-source transport cannot be proven, installation is unsupported.
- SQLite metadata/journal records original/work role, package, signer lineage, stable profile serial, operation generation, source membership/digests and observed states. Raw UserHandle/userId is not durable identity. Profile recreation invalidates old handles/shortcuts/jobs rather than reconnecting by name.
- OS package installation is authoritative. Provider shim remains unavailable until measured fixture gates pass; metadata cannot create platform authority. Lost callbacks/timeouts lead to reconciliation/REVIEW, never blind replay or false success.

## Public API restrictions to prove, not bypass

- Check managed-user feature and `isProvisioningAllowed(ACTION_PROVISION_MANAGED_PROFILE)` immediately before visible system consent. Confirm actual provisioning completion, owner identity and enabled profile. Full-device provisioning is not substituted.
- Do **not** rely on `installExistingPackage`: API28+ affiliation restriction means ordinary BYOD PO is insufficient. Evaluate work-local PackageInstaller signed-cluster sessions, including user action/cancel/partial failure and independent readback.
- Parent-side LauncherApps may target accessible work profiles. Work-side cannot assume parent inventory access. Check direction, profile accessibility, package visibility and actual component immediately before launch; no personal-app/web fallback.
- Quiet-mode API is restricted to authorized callers/default launcher, not PO alone. No becoming default launcher or privileged app without a separate decision.
- Deletion is owner-confirmed profile-local app uninstall with readback; no blanket silent-uninstall assertion and never full-profile wipe for an app delete. Profile teardown needs separate explicit all-data-loss consent.

## Reuse and evidence

Google TestDPC is an Apache-2.0 **test sample**, not a production dependency recommendation. Before copying any selected example: freeze revision/files, verify licenses/notices and transitive/native inventory, review minimal security surface and test integration. No code has been selected/imported by this draft. Shelter GPL source is behavioral reference only absent compatible licensing decision; Island/DPC architecture is not evidence of this product's success. Popularity alone never meets the user's adoption/quality requirement.

Primary source assessment: `fonesi-apk-review/WORK_PROFILE_FEASIBILITY_1002.md` (outside repository; immutable hash in draft crosswalk metadata). Official links are carried in [proof gate](PROOF_GATE.md). This draft is not a platform guarantee or legal clearance.

## Additional activation blockers from independent draft review

- All111 generic OS-proof crosswalk rows need clause-specific replacement acceptance before V2 can activate. Verbatim originals remain authoritative meanwhile; a generic provider→profile vocabulary substitution is insufficient. In particular SEC-001/019/020 manager-originated restrictions cannot promise control over Android/other-profile actions, and EVD-008/013 manual-construction probes cannot replace real OS lifecycle proof.
- Manager-originated install/update/delete/launch requires current foreground/auth, but external Android Settings/launcher/store/other-profile changes may occur while Fonesi is locked or absent. Observe/reconcile only: invalidate source snapshots, consent previews, capability/profile generations and queued assumptions; ambiguous in-flight work stays REVIEW. Never claim those external actions were authorized or prevented by the manager lock; never replay them.
- `LauncherApps.getProfiles()` yields accessible profiles, not an exhaustive device-user inventory. Complete shared-code affected scope is a **measured capability prerequisite**, not assumed from that list. If the public API route cannot establish all affected installations on a supported device, Fonesi-controlled updates are BLOCKED. A user-visible prompt acknowledging unknown scope does not silently relax this original requirement; relaxing it would require a separate explicit amendment.

## Acceptance and stopping rule

Scope agreement precedes activation of amendments. Independent plan/security review precedes implementation of production profile flow. Actual provisioning/device/account/new-license actions require their own authorization. A failed capability remains explicit unsupported; never root/ADB/hidden API/MDM takeover/identity spoofing workaround. If the smallest owned-fixture proof cannot satisfy original-source installation, exact profile launch, separation, restart, shared update and profile-local uninstall, report the specific incompatibility before building more UX around it.

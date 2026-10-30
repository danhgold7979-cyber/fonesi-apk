#!/usr/bin/env python3
"""Static safety audit for the isolated E82 one-shot host-factory authorization gate."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


auth = read("native-runtime-probe/src/main/java/vn/fonesi/nativeprobe/E82FactoryAuthorization.java")
tests = read("native-runtime-probe/src/test/java/vn/fonesi/nativeprobe/E82FactoryAuthorizationTest.java")
gradle = read("native-runtime-probe/build.gradle.kts")

for marker in (
    "public static synchronized void arm(Ticket ticket)",
    "public static synchronized Ticket consume(Dispatch dispatch)",
    "pending = null;",
    "state = State.CONSUMED;",
    "ticket.matches(dispatch)",
    "ticket.privateActivityClass.getClassLoader() != ticket.privateClassLoader",
    "ticket.loadedGuestCode == null",
    "PRIVATE_ACTIVITY_CLASS = \"vn.fonesi.payload.PayloadActivity\"",
    "HOST_PROCESS = HOST_PACKAGE + \":fonesi_guest_ui\"",
):
    assert marker in auth, "E82 authorization invariant missing: " + marker

consume = auth.split("public static synchronized Ticket consume(Dispatch dispatch)", 1)[1]
assert consume.index("pending = null;") < consume.index("state = State.CONSUMED;") < consume.index("ticket.matches(dispatch)"), (
    "E82 must consume the in-memory ticket before evaluating untrusted dispatch identity"
)
assert "public static synchronized void resetForTest" not in auth, "Production code must not expose ticket reset"
assert "android.content.Intent" not in auth and "android.app.Instrumentation" not in auth, (
    "Authorization state must not be reconstructed from Intent or Instrumentation callbacks"
)
for marker in (
    "exactSignedPrivateIdentityConsumesOneShotTicket",
    "duplicateArmIsRejected",
    "wrongNonceConsumesTicketAndCannotBeRetried",
    "componentProcessUidAndApkIdentityAreAllBound",
    "invalidPrivateLoaderOrClusterCannotBeArmed",
):
    assert marker in tests, "E82 authorization regression missing: " + marker
assert 'testImplementation("junit:junit:4.13.2")' in gradle, "JUnit dependency missing"
print("PASS E82 subgate audit: one-shot process-local authorization is identity-bound and fail-closed.")
print("NOT VERIFIED: Android AppComponentFactory dispatch/Activity attachment; this audit is not E82 acceptance.")

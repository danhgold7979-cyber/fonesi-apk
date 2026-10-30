package vn.fonesi.nativeprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import vn.fonesi.payload.PayloadActivity;

public final class E82FactoryAuthorizationTest {
    private static final String RUN = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String CLUSTER = "11111111-2222-4333-8444-555555555555";
    private static final String NONCE = "0123456789abcdef0123456789abcdef";
    private static final String SOURCE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HOST_APK = "b".repeat(64);
    private static final String PAYLOAD_APK = "c".repeat(64);
    private final Object loadedImage = new Object();

    @Before public void resetBefore() { E82FactoryAuthorization.resetForTest(); }
    @After public void resetAfter() { E82FactoryAuthorization.resetForTest(); }

    private E82FactoryAuthorization.Ticket ticket() {
        return new E82FactoryAuthorization.Ticket(RUN, NONCE,
                E82FactoryAuthorization.HOST_PACKAGE,
                E82FactoryAuthorization.STUB_CLASS,
                E82FactoryAuthorization.HOST_PACKAGE + "/" + E82FactoryAuthorization.STUB_CLASS,
                E82FactoryAuthorization.HOST_PROCESS, 10142, SOURCE, HOST_APK, PAYLOAD_APK,
                CLUSTER, E82FactoryAuthorization.PRIVATE_ACTIVITY_CLASS,
                PayloadActivity.class.getClassLoader(), E82FactoryAuthorizationTest.class.getClassLoader(),
                PayloadActivity.class, loadedImage);
    }

    private E82FactoryAuthorization.Dispatch dispatch() {
        return new E82FactoryAuthorization.Dispatch(RUN, NONCE,
                E82FactoryAuthorization.HOST_PACKAGE,
                E82FactoryAuthorization.STUB_CLASS,
                E82FactoryAuthorization.HOST_PACKAGE + "/" + E82FactoryAuthorization.STUB_CLASS,
                E82FactoryAuthorization.HOST_PROCESS, 10142, E82FactoryAuthorizationTest.class.getClassLoader(),
                SOURCE, HOST_APK, PAYLOAD_APK);
    }

    @Test public void exactSignedPrivateIdentityConsumesOneShotTicket() {
        E82FactoryAuthorization.Ticket expected = ticket();
        E82FactoryAuthorization.arm(expected);
        E82FactoryAuthorization.Ticket actual = E82FactoryAuthorization.consume(dispatch());
        assertSame(expected, actual);
        assertSame(loadedImage, actual.getLoadedGuestCode());
        assertSame(PayloadActivity.class, actual.getPrivateActivityClass());
        assertEquals(NONCE, actual.getNonce());
        assertReplayDenied();
    }

    @Test public void duplicateArmIsRejected() {
        E82FactoryAuthorization.arm(ticket());
        try {
            E82FactoryAuthorization.arm(ticket());
            fail("duplicate E82 authorization must be rejected");
        } catch (SecurityException expected) {
            assertEquals("E82 factory authorization is already armed or consumed", expected.getMessage());
        }
    }

    @Test public void wrongNonceConsumesTicketAndCannotBeRetried() {
        E82FactoryAuthorization.arm(ticket());
        E82FactoryAuthorization.Dispatch wrong = new E82FactoryAuthorization.Dispatch(
                RUN, "ffffffffffffffffffffffffffffffff",
                E82FactoryAuthorization.HOST_PACKAGE, E82FactoryAuthorization.STUB_CLASS,
                E82FactoryAuthorization.HOST_PACKAGE + "/" + E82FactoryAuthorization.STUB_CLASS,
                E82FactoryAuthorization.HOST_PROCESS, 10142, E82FactoryAuthorizationTest.class.getClassLoader(),
                SOURCE, HOST_APK, PAYLOAD_APK);
        expectSecurityFailure(() -> E82FactoryAuthorization.consume(wrong));
        assertReplayDenied();
    }

    @Test public void componentProcessUidAndApkIdentityAreAllBound() {
        String[][] mismatches = {
            {"wrong-component", E82FactoryAuthorization.STUB_CLASS},
            {"wrong-process", E82FactoryAuthorization.HOST_PROCESS},
            {"wrong-package", E82FactoryAuthorization.HOST_PACKAGE},
            {"wrong-source", SOURCE},
            {"wrong-host-apk", HOST_APK},
            {"wrong-payload-apk", PAYLOAD_APK},
            {"wrong-uid", "10142"},
            {"wrong-run", RUN},
        };
        for (String[] mismatch : mismatches) {
            E82FactoryAuthorization.resetForTest();
            E82FactoryAuthorization.arm(ticket());
            E82FactoryAuthorization.Dispatch d = dispatch();
            if ("wrong-component".equals(mismatch[0])) {
                d = new E82FactoryAuthorization.Dispatch(RUN, NONCE,
                    E82FactoryAuthorization.HOST_PACKAGE, "vn.fonesi.nativeprobe.OtherActivity",
                    E82FactoryAuthorization.HOST_PACKAGE + "/vn.fonesi.nativeprobe.OtherActivity",
                    E82FactoryAuthorization.HOST_PROCESS, 10142, E82FactoryAuthorizationTest.class.getClassLoader(),
                    SOURCE, HOST_APK, PAYLOAD_APK);
            } else if ("wrong-process".equals(mismatch[0])) {
                d = new E82FactoryAuthorization.Dispatch(RUN, NONCE,
                    E82FactoryAuthorization.HOST_PACKAGE, E82FactoryAuthorization.STUB_CLASS,
                    E82FactoryAuthorization.HOST_PACKAGE + "/" + E82FactoryAuthorization.STUB_CLASS,
                    "vn.fonesi.nativeprobe:other", 10142, SOURCE, HOST_APK, PAYLOAD_APK);
            } else if ("wrong-package".equals(mismatch[0])) {
                d = new E82FactoryAuthorization.Dispatch(RUN, NONCE,
                    "vn.fonesi.attacker", E82FactoryAuthorization.STUB_CLASS,
                    E82FactoryAuthorization.HOST_PACKAGE + "/" + E82FactoryAuthorization.STUB_CLASS,
                    E82FactoryAuthorization.HOST_PROCESS, 10142, E82FactoryAuthorizationTest.class.getClassLoader(),
                SOURCE, HOST_APK, PAYLOAD_APK);
            } else if ("wrong-source".equals(mismatch[0])) {
                d = new E82FactoryAuthorization.Dispatch(RUN, NONCE,
                    E82FactoryAuthorization.HOST_PACKAGE, E82FactoryAuthorization.STUB_CLASS,
                    E82FactoryAuthorization.HOST_PACKAGE + "/" + E82FactoryAuthorization.STUB_CLASS,
                    E82FactoryAuthorization.HOST_PROCESS, 10142, E82FactoryAuthorizationTest.class.getClassLoader(),
                    "d".repeat(40), HOST_APK, PAYLOAD_APK);
            } else if ("wrong-host-apk".equals(mismatch[0])) {
                d = new E82FactoryAuthorization.Dispatch(RUN, NONCE,
                    E82FactoryAuthorization.HOST_PACKAGE, E82FactoryAuthorization.STUB_CLASS,
                    E82FactoryAuthorization.HOST_PACKAGE + "/" + E82FactoryAuthorization.STUB_CLASS,
                    E82FactoryAuthorization.HOST_PROCESS, 10142, E82FactoryAuthorizationTest.class.getClassLoader(),
                    SOURCE, "d".repeat(64), PAYLOAD_APK);
            } else if ("wrong-payload-apk".equals(mismatch[0])) {
                d = new E82FactoryAuthorization.Dispatch(RUN, NONCE,
                    E82FactoryAuthorization.HOST_PACKAGE, E82FactoryAuthorization.STUB_CLASS,
                    E82FactoryAuthorization.HOST_PACKAGE + "/" + E82FactoryAuthorization.STUB_CLASS,
                    E82FactoryAuthorization.HOST_PROCESS, 10142, E82FactoryAuthorizationTest.class.getClassLoader(),
                    SOURCE, HOST_APK, "d".repeat(64));
            } else if ("wrong-uid".equals(mismatch[0])) {
                d = new E82FactoryAuthorization.Dispatch(RUN, NONCE,
                    E82FactoryAuthorization.HOST_PACKAGE, E82FactoryAuthorization.STUB_CLASS,
                    E82FactoryAuthorization.HOST_PACKAGE + "/" + E82FactoryAuthorization.STUB_CLASS,
                    E82FactoryAuthorization.HOST_PROCESS, 10143, E82FactoryAuthorizationTest.class.getClassLoader(),
                    SOURCE, HOST_APK, PAYLOAD_APK);
            } else if ("wrong-run".equals(mismatch[0])) {
                d = new E82FactoryAuthorization.Dispatch("bbbbbbbb-cccc-4ddd-8eee-ffffffffffff", NONCE,
                    E82FactoryAuthorization.HOST_PACKAGE, E82FactoryAuthorization.STUB_CLASS,
                    E82FactoryAuthorization.HOST_PACKAGE + "/" + E82FactoryAuthorization.STUB_CLASS,
                    E82FactoryAuthorization.HOST_PROCESS, 10142, E82FactoryAuthorizationTest.class.getClassLoader(),
                SOURCE, HOST_APK, PAYLOAD_APK);
            }
            final E82FactoryAuthorization.Dispatch attempt = d;
            expectSecurityFailure(() -> E82FactoryAuthorization.consume(attempt));
            assertReplayDenied();
        }
    }

    @Test public void invalidPrivateLoaderOrClusterCannotBeArmed() {
        E82FactoryAuthorization.Ticket invalid = new E82FactoryAuthorization.Ticket(
                RUN, NONCE, E82FactoryAuthorization.HOST_PACKAGE,
                E82FactoryAuthorization.STUB_CLASS,
                E82FactoryAuthorization.HOST_PACKAGE + "/" + E82FactoryAuthorization.STUB_CLASS,
                E82FactoryAuthorization.HOST_PROCESS, 10142, SOURCE, HOST_APK, PAYLOAD_APK,
                "not-a-uuid", E82FactoryAuthorization.PRIVATE_ACTIVITY_CLASS,
                PayloadActivity.class.getClassLoader(), E82FactoryAuthorizationTest.class.getClassLoader(),
                PayloadActivity.class, loadedImage);
        expectSecurityFailure(() -> E82FactoryAuthorization.arm(invalid));
        E82FactoryAuthorization.Ticket wrongLoader = new E82FactoryAuthorization.Ticket(
                RUN, NONCE, E82FactoryAuthorization.HOST_PACKAGE,
                E82FactoryAuthorization.STUB_CLASS,
                E82FactoryAuthorization.HOST_PACKAGE + "/" + E82FactoryAuthorization.STUB_CLASS,
                E82FactoryAuthorization.HOST_PROCESS, 10142, SOURCE, HOST_APK, PAYLOAD_APK,
                CLUSTER, E82FactoryAuthorization.PRIVATE_ACTIVITY_CLASS,
                new ClassLoader() { }, E82FactoryAuthorizationTest.class.getClassLoader(),
                PayloadActivity.class, loadedImage);
        expectSecurityFailure(() -> E82FactoryAuthorization.arm(wrongLoader));
        E82FactoryAuthorization.arm(ticket());
        assertReplayDenied();
    }

    private static void assertReplayDenied() {
        expectSecurityFailure(() -> E82FactoryAuthorization.consume(null));
    }

    private static void expectSecurityFailure(Runnable action) {
        try {
            action.run();
            fail("invalid E82 authorization must fail closed");
        } catch (SecurityException expected) {
            // Expected.
        }
    }
}

package vn.fonesi.nativeprobe;

import java.util.UUID;
import java.util.regex.Pattern;

/** Process-local, one-shot authorization for the E82 host AppComponentFactory experiment. */
public final class E82FactoryAuthorization {
    public static final String HOST_PACKAGE = "vn.fonesi.nativeprobe";
    public static final String HOST_PROCESS = HOST_PACKAGE + ":fonesi_guest_ui";
    public static final String STUB_CLASS = HOST_PACKAGE + ".E82HostActivityStub";
    public static final String PRIVATE_ACTIVITY_CLASS = "vn.fonesi.payload.PayloadActivity";

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern NONCE = Pattern.compile("[0-9a-f]{32}");

    private enum State { EMPTY, ARMED, CONSUMED }
    private static State state = State.EMPTY;
    private static Ticket pending;

    private E82FactoryAuthorization() { }

    /** Arms once per process. A consumed process cannot be re-armed. */
    public static synchronized void arm(Ticket ticket) {
        if (state != State.EMPTY || pending != null) {
            throw new SecurityException("E82 factory authorization is already armed or consumed");
        }
        validate(ticket);
        pending = ticket;
        state = State.ARMED;
    }

    /**
     * Atomically consumes authorization before checking dispatch identity.
     * A mismatch is terminal, so callers cannot probe or replay a live ticket.
     */
    public static synchronized Ticket consume(Dispatch dispatch) {
        if (state != State.ARMED || pending == null) {
            throw new SecurityException("E82 factory authorization is absent, stale or already consumed");
        }
        Ticket ticket = pending;
        pending = null;
        state = State.CONSUMED;
        if (dispatch == null || !ticket.matches(dispatch)) {
            throw new SecurityException("E82 factory dispatch identity does not match its one-shot authorization");
        }
        return ticket;
    }

    private static void validate(Ticket ticket) {
        if (ticket == null ||
                !canonicalUuid(ticket.runId) ||
                !canonicalUuid(ticket.clusterId) ||
                ticket.nonce == null || !NONCE.matcher(ticket.nonce).matches() ||
                !HOST_PACKAGE.equals(ticket.hostPackage) ||
                !STUB_CLASS.equals(ticket.stubClassName) ||
                !(HOST_PACKAGE + "/" + STUB_CLASS).equals(ticket.stubComponent) ||
                !HOST_PROCESS.equals(ticket.processName) ||
                ticket.hostUid < 0 ||
                ticket.sourceCommit == null || !SHA1.matcher(ticket.sourceCommit).matches() ||
                ticket.hostApkSha256 == null || !SHA256.matcher(ticket.hostApkSha256).matches() ||
                ticket.payloadApkSha256 == null || !SHA256.matcher(ticket.payloadApkSha256).matches() ||
                !PRIVATE_ACTIVITY_CLASS.equals(ticket.activityClassName) ||
                ticket.privateClassLoader == null ||
                ticket.hostClassLoader == null ||
                ticket.privateActivityClass == null ||
                !PRIVATE_ACTIVITY_CLASS.equals(ticket.privateActivityClass.getName()) ||
                ticket.privateActivityClass.getClassLoader() != ticket.privateClassLoader ||
                ticket.loadedGuestCode == null) {
            throw new SecurityException("E82 factory authorization contains an invalid signed-image or host identity");
        }
    }

    private static boolean canonicalUuid(String value) {
        if (value == null) return false;
        try { return UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException ignored) { return false; }
    }

    /** Immutable authorization retained only in host memory; never serialized into an Intent. */
    public static final class Ticket {
        final String runId, nonce, hostPackage, stubClassName, stubComponent, processName;
        final int hostUid;
        final String sourceCommit, hostApkSha256, payloadApkSha256, clusterId, activityClassName;
        final ClassLoader privateClassLoader;
        final ClassLoader hostClassLoader;
        final Class<?> privateActivityClass;
        final Object loadedGuestCode;

        public Ticket(String runId, String nonce, String hostPackage, String stubClassName,
                String stubComponent, String processName, int hostUid, String sourceCommit,
                String hostApkSha256, String payloadApkSha256, String clusterId,
                String activityClassName, ClassLoader privateClassLoader,
                ClassLoader hostClassLoader, Class<?> privateActivityClass, Object loadedGuestCode) {
            this.runId = runId;
            this.nonce = nonce;
            this.hostPackage = hostPackage;
            this.stubClassName = stubClassName;
            this.stubComponent = stubComponent;
            this.processName = processName;
            this.hostUid = hostUid;
            this.hostClassLoader = hostClassLoader;
            this.sourceCommit = sourceCommit;
            this.hostApkSha256 = hostApkSha256;
            this.payloadApkSha256 = payloadApkSha256;
            this.clusterId = clusterId;
            this.activityClassName = activityClassName;
            this.privateClassLoader = privateClassLoader;
            this.hostClassLoader = hostClassLoader;
            this.privateActivityClass = privateActivityClass;
            this.loadedGuestCode = loadedGuestCode;
        }

        public String getRunId() { return runId; }
        public String getNonce() { return nonce; }
        public String getClusterId() { return clusterId; }
        public String getActivityClassName() { return activityClassName; }
        public ClassLoader getPrivateClassLoader() { return privateClassLoader; }
        public Class<?> getPrivateActivityClass() { return privateActivityClass; }
        public Object getLoadedGuestCode() { return loadedGuestCode; }

        private boolean matches(Dispatch d) {
            return runId.equals(d.runId) && nonce.equals(d.nonce) &&
                    hostPackage.equals(d.hostPackage) &&
                    stubClassName.equals(d.stubClassName) &&
                    stubComponent.equals(d.stubComponent) &&
                    processName.equals(d.processName) && hostUid == d.hostUid &&
                    hostClassLoader == d.hostClassLoader &&
                    sourceCommit.equals(d.sourceCommit) &&
                    hostApkSha256.equals(d.hostApkSha256) &&
                    payloadApkSha256.equals(d.payloadApkSha256);
        }
    }

    /** Identity observed at the real framework component-instantiation boundary. */
    public static final class Dispatch {
        final String runId, nonce, hostPackage, stubClassName, stubComponent, processName;
        final int hostUid;
        final ClassLoader hostClassLoader;
        final String sourceCommit, hostApkSha256, payloadApkSha256;

        public Dispatch(String runId, String nonce, String hostPackage, String stubClassName,
                String stubComponent, String processName, int hostUid, ClassLoader hostClassLoader,
                String sourceCommit, String hostApkSha256, String payloadApkSha256) {
            this.runId = runId;
            this.nonce = nonce;
            this.hostPackage = hostPackage;
            this.stubClassName = stubClassName;
            this.stubComponent = stubComponent;
            this.processName = processName;
            this.hostUid = hostUid;
            this.sourceCommit = sourceCommit;
            this.hostApkSha256 = hostApkSha256;
            this.payloadApkSha256 = payloadApkSha256;
        }
    }

    /* Package-private test isolation; production callers cannot reset a consumed ticket. */
    static synchronized void resetForTest() {
        pending = null;
        state = State.EMPTY;
    }
}

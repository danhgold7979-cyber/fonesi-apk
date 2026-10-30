package vn.fonesi.nativeprobe;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import vn.fonesi.apk.PrivateApkCluster;
import vn.fonesi.apk.PrivateApkStore;
import vn.fonesi.apk.ExplicitActivityCaller;
import vn.fonesi.engine.GuestCodeLoader;
import vn.fonesi.engine.LoadedGuestCode;
import vn.fonesi.engine.PrivateGuestActivityClassHandle;
import vn.fonesi.engine.SyntheticGuestScopedContext;
import vn.fonesi.engine.NativeRuntime;
import vn.fonesi.engine.PrivateSyntheticComponentCatalog;
import vn.fonesi.engine.SyntheticComponentDispatchTicket;
import vn.fonesi.engine.VerifiedPrivateSyntheticComponents;

/**
 * E24: real Android HOST Activity with an attached View created by independently signed,
 * private SYNTHETIC payload DEX. Lifecycle callbacks are relayed, not guest ActivityThread.
 * No arbitrary APK launch, UID sandbox, permissions, network, credentials or account data.
 */
public final class SyntheticGuestUiProbeActivity extends Activity {
    private static final String GUEST_PACKAGE = "vn.fonesi.payload";
    private static final String ENTRY = GUEST_PACKAGE + ".SyntheticGuestScreen";
    private static final String SIGNED_ACTIVITY = GUEST_PACKAGE + ".PayloadActivity";
    private static final String SIGNED_APPLICATION = GUEST_PACKAGE + ".SyntheticGuestApplication";
    private static final String SCOPED_ENTRY = GUEST_PACKAGE + ".ScopedProbeEntrypoint";
    private static final String PREFIX = "FONESI-E24-PRIVATE-SCREEN:";
    private static final List<String> COMPLETE = Arrays.asList(
        "CREATE", "START", "RESUME", "PAUSE", "STOP", "DESTROY");
    private final List<String> androidEvents = new ArrayList<>();
    private File reports;
    private String runId;
    private String challenge;
    private String proofLevel = "e24";
    private volatile boolean privateComponentCatalogVerified;
    /** E74: class/type only, never an attached Android guest Activity. */
    private volatile boolean privateActivityClassVerified;
    private volatile boolean privateActivityConstructorPreflightVerified;
    private volatile boolean privateApplicationClassPreflightVerified;
    private volatile boolean syntheticApplicationAttached;
    private volatile boolean syntheticApplicationOnCreateVerified;
    private volatile boolean syntheticApplicationMainThreadVerified;
    private volatile boolean syntheticApplicationInstancesDistinct;
    private volatile boolean syntheticScopeAStorageVerified;
    private volatile boolean syntheticScopeBStorageVerified;
    private volatile boolean syntheticScopePersistenceVerified;
    private volatile boolean syntheticScopeCrossReadbackVerified;
    private volatile String syntheticApplicationClassName;
    private volatile String syntheticScopeAId;
    private volatile String syntheticScopeBId;
    private volatile String sourceCommit;
    private volatile String probeApkSha256;
    private volatile String payloadApkSha256;
    private volatile String e79Stage;
    private volatile String e79PrivateClusterId;
    private volatile String e79ProcessToken;
    private volatile String e79MarkerA;
    private volatile String e79MarkerB;
    private volatile String e79PriorProcessToken;
    private volatile String e79PriorRunId;
    private volatile int e79PriorPid = -1;
    private volatile int e79StartCountA;
    private volatile int e79StartCountB;
    private volatile boolean e79ProcessRestartVerified;
    private volatile boolean e79ScopedDataAfterRestartVerified;
    private volatile boolean e80PrivateActivityConstructorVerified;
    private volatile boolean e80PrivateActivityMainThreadVerified;
    private volatile boolean e80PrivateActivityUnattachedVerified;
    private volatile boolean e80PrivateActivityLoaderVerified;
    private volatile String e80PrivateActivityClassName;
    private volatile int e80PrivateActivityConstructorCount;
    private volatile boolean e81ScopedMode;
    private volatile Activity e81PrivateActivity;
    private volatile boolean e81PrivateInstanceScopeBound;
    private volatile boolean e81PrivateRebindingRejected;
    private volatile boolean e81PrivateScopedDataReadbackVerified;
    private volatile boolean e81PrivateBaseContextStillNull;
    private volatile String e81BoundScopeId;
    private volatile boolean syntheticComponentDispatchVerified;
    private volatile boolean dispatchOnly;
    private boolean dispatchReceiver;
    private String dispatchTicketJson;
    private volatile String dispatchTicketPrivateApkSha256;
    private Class<?> privateEntrypoint;
    private PrivateApkStore store;
    private PrivateApkCluster cluster;
    private View signedView;
    private boolean verifiedPrivateClass;
    private boolean verifiedPrivateResource;
    private boolean viewAttached;
    private boolean destroyed;
    private boolean reported;
    private String failureStage;
    private volatile String preparationStage = "not_started";
    private volatile boolean preparationFinished;
    private volatile int configurationChangeCallbacks;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        androidEvents.add("CREATE");
        preparationStage = "activity_create";
        setContentView(new TextView(this) {{
            setText("Fonesi E24 signed PRIVATE synthetic View proof; no guest account.");
        }});
        try {
            // Fail closed on Activity recreation: never reuse a prior private classloader/process.
            if (saved != null) throw new IllegalStateException("Synthetic screen cannot hot-restart");
            String requestedProof = getIntent().getStringExtra("proof_level");
            if (requestedProof != null) proofLevel = requestedProof;
            if (!"e24".equals(proofLevel) && !"e25".equals(proofLevel) &&
                !"e29".equals(proofLevel) && !"e74".equals(proofLevel) &&
                !"e77".equals(proofLevel) && !"e78".equals(proofLevel) && !("e79".equals(proofLevel) || "e80".equals(proofLevel)))
                throw new IllegalArgumentException("Unsupported synthetic host screen proof");
            String e81Mode = getIntent().getStringExtra("e81_scoped_mode");
            if (e81Mode != null) {
                if (!"e80".equals(proofLevel) || !"enabled".equals(e81Mode))
                    throw new IllegalArgumentException("E81 requires explicit e80 synthetic scope opt-in");
                e81ScopedMode = true;
            }
            if ("e78".equals(proofLevel) || ("e79".equals(proofLevel) || "e80".equals(proofLevel))) {
                sourceCommit = getIntent().getStringExtra("source_commit");
                probeApkSha256 = getIntent().getStringExtra("probe_apk_sha256");
                payloadApkSha256 = getIntent().getStringExtra("payload_apk_sha256");
                if (sourceCommit == null || !sourceCommit.matches("[0-9a-f]{40}") ||
                    probeApkSha256 == null || !probeApkSha256.matches("[0-9a-f]{64}") ||
                    payloadApkSha256 == null || !payloadApkSha256.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("E78/E79 source/APK SHA-256 binding is missing or malformed");
            }
            if (("e79".equals(proofLevel) || "e80".equals(proofLevel))) {
                e79Stage = getIntent().getStringExtra("e79_stage");
                if (!"write".equals(e79Stage) && !"verify".equals(e79Stage))
                    throw new IllegalArgumentException("E79 requires an explicit write/verify stage");
                if ("verify".equals(e79Stage)) {
                    e79PrivateClusterId = getIntent().getStringExtra("e79_private_cluster_id");
                    syntheticScopeAId = getIntent().getStringExtra("e79_scope_a");
                    syntheticScopeBId = getIntent().getStringExtra("e79_scope_b");
                    e79MarkerA = getIntent().getStringExtra("e79_marker_a");
                    e79MarkerB = getIntent().getStringExtra("e79_marker_b");
                    e79PriorPid = getIntent().getIntExtra("e79_prior_pid", -1);
                    e79PriorProcessToken = getIntent().getStringExtra("e79_prior_process_token");
                    e79PriorRunId = getIntent().getStringExtra("e79_prior_run_id");
                    ProbeData.check(canonicalUuid(e79PrivateClusterId) &&
                        canonicalUuid(syntheticScopeAId) && canonicalUuid(syntheticScopeBId) &&
                        !syntheticScopeAId.equals(syntheticScopeBId) &&
                        e79MarkerA != null && e79MarkerA.matches("[0-9a-f]{32}") &&
                        e79MarkerB != null && e79MarkerB.matches("[0-9a-f]{32}") &&
                        !e79MarkerA.equals(e79MarkerB) && e79PriorPid > 0 &&
                        canonicalUuid(e79PriorProcessToken) && canonicalUuid(e79PriorRunId),
                        "E79 verify-stage handoff fields are invalid");
                }
            }
            dispatchTicketJson = getIntent().getStringExtra("dispatch_ticket");
            if (dispatchTicketJson != null) {
                if (!"e29".equals(proofLevel) || dispatchTicketJson.length() > 4096)
                    throw new IllegalArgumentException("Unexpected or oversized private dispatch ticket");
                dispatchReceiver = true;
            }
            String raw = getIntent().getStringExtra("run_id");
            runId = UUID.fromString(raw).toString();
            if (!runId.equals(raw)) throw new IllegalArgumentException("Noncanonical test run UUID");
            String process = Build.VERSION.SDK_INT >= 28 ? Application.getProcessName() : null;
            ProbeData.check((getPackageName() + ":fonesi_guest_ui").equals(process) &&
                Process.is64Bit() && Process.myUid() == getApplicationInfo().uid,
                "Host synthetic UI must run in its dedicated 64-bit process");
            File owned = getApplicationContext().getDataDir().getCanonicalFile();
            File files = getFilesDir().getCanonicalFile();
            ProbeData.check(files.getPath().startsWith(owned.getPath() + File.separator),
                "Receipt root must remain host private");
            reports = new File(files, "guest-ui-probe").getCanonicalFile();
            ProbeData.check((reports.isDirectory() || reports.mkdirs()) &&
                reports.getPath().startsWith(files.getPath() + File.separator),
                "Cannot create safe synthetic UI receipt directory");
            byte[] random = new byte[16];
            new SecureRandom().nextBytes(random);
            StringBuilder nonce = new StringBuilder(32);
            for (byte b : random) nonce.append(Character.forDigit((b >>> 4) & 15, 16))
                                       .append(Character.forDigit(b & 15, 16));
            challenge = nonce.toString();
            new Thread(this::prepare, "Fonesi-E24-signed-screen-loader").start();
        } catch (Throwable error) {
            failure("activity_create", error);
        }
    }

    private void prepare() {
        String stage = "signed_installed_synthetic_source";
        preparationStage = stage;
        try {
            File owned = getApplicationContext().getDataDir().getCanonicalFile();
            File files = getFilesDir().getCanonicalFile();
            File code = new File(files, "e24-private-payload").getCanonicalFile();
            ProbeData.check(code.getPath().startsWith(files.getPath() + File.separator),
                "Private screen code store escaped app");
            PrivateApkStore localStore = new PrivateApkStore(code.toPath());
            PrivateApkCluster localCluster;
            SyntheticComponentDispatchTicket dispatchTicket = null;

            if (dispatchReceiver) {
                stage = "e29_parse_ticket_and_open_private_cluster";
                preparationStage = stage;
                dispatchTicket = SyntheticComponentDispatchTicket.Companion.fromJson(dispatchTicketJson);
                localCluster = localStore.open(dispatchTicket.getClusterId(), Build.VERSION.SDK_INT);
            } else if (("e79".equals(proofLevel) || "e80".equals(proofLevel)) && "verify".equals(e79Stage)) {
                stage = "e79_reopen_same_signed_private_cluster_after_process_restart";
                preparationStage = stage;
                localCluster = localStore.open(e79PrivateClusterId, Build.VERSION.SDK_INT);
            } else {
                stage = "private_signed_apk_publish_and_readback";
                preparationStage = stage;
                localCluster = localStore.publish(
                    SyntheticSignedPayloadSource.fromInstalled(this), Build.VERSION.SDK_INT);
                if (("e79".equals(proofLevel) || "e80".equals(proofLevel))) e79PrivateClusterId = localCluster.getId();
            }
            ProbeData.check(localStore.open(localCluster.getId(), Build.VERSION.SDK_INT).equals(localCluster) &&
                localCluster.getApks().getBase().getPackageName().equals(GUEST_PACKAGE) &&
                localCluster.getApks().getSplits().isEmpty(),
                "Synthetic UI code must be a verified, single signed private payload APK");

            if (dispatchReceiver) {
                ProbeData.check(NativeRuntime.state() == NativeRuntime.ENABLED,
                    "E29 internal dispatch must remain in the already initialized private test process");
            } else {
                stage = "synthetic_dedicated_process_native_routing";
                preparationStage = stage;
                File src = new File(files, "e24-inert-source").getCanonicalFile();
                File dst = new File(files, "e24-inert-target").getCanonicalFile();
                ProbeData.check((src.isDirectory() || src.mkdirs()) && (dst.isDirectory() || dst.mkdirs()),
                    "Cannot create private inert native routes");
                Map<String, File> routes = new LinkedHashMap<>();
                routes.put(src.getPath(), dst);
                NativeRuntime.configure(getApplicationContext(), routes);
                ProbeData.check(NativeRuntime.state() == NativeRuntime.CONFIGURED,
                    "E24 native routing configuration was not accepted");
                NativeRuntime.enable(getApplicationContext());
                ProbeData.check(NativeRuntime.state() == NativeRuntime.ENABLED,
                    "E24 native routing could not be enabled");
            }

            VerifiedPrivateSyntheticComponents catalog = null;
            if ("e25".equals(proofLevel) || "e29".equals(proofLevel)) {
                stage = "private_signed_component_manifest_catalog";
                preparationStage = stage;
                catalog = new PrivateSyntheticComponentCatalog(getApplicationContext(), localStore)
                    .inspect(localCluster.getId());
                ProbeData.check(
                    catalog.getPackageName().equals(GUEST_PACKAGE) &&
                    catalog.getApplicationName().equals(GUEST_PACKAGE + ".SyntheticGuestApplication") &&
                    catalog.getActivityName().equals(GUEST_PACKAGE + ".PayloadActivity") &&
                    catalog.getActivityExported() &&
                    catalog.getPrivatePath().equals(localCluster.getApks().getBase().getFile().getPath()),
                    "Synthetic private component manifest catalog is incomplete or not private"
                );
                privateComponentCatalogVerified = true;
            }

            if ("e29".equals(proofLevel)) {
                if (catalog == null)
                    throw new IllegalStateException("E29 requires a fresh verified private component catalog");
                if (dispatchReceiver) {
                    stage = "e29_validate_ticket_immediately_before_private_code_load";
                    preparationStage = stage;
                    SyntheticComponentDispatchTicket.Companion.validate(
                        dispatchTicket, catalog, localCluster, UUID.fromString(runId), code);
                    syntheticComponentDispatchVerified = true;
                    dispatchTicketPrivateApkSha256 = dispatchTicket.getPrivateApkSha256();
                } else {
                    stage = "e29_issue_bounded_private_component_ticket";
                    preparationStage = stage;
                    dispatchTicket = SyntheticComponentDispatchTicket.Companion.issue(
                        catalog, localCluster, UUID.fromString(runId), code);
                    dispatchTicketPrivateApkSha256 = dispatchTicket.getPrivateApkSha256();
                    final String serializedTicket = dispatchTicket.toJson();
                    dispatchOnly = true;
                    preparationStage = "e29_explicit_internal_dispatch_queued";
                    runOnUiThread(() -> {
                        try {
                            Intent dispatch = new Intent(
                                SyntheticGuestUiProbeActivity.this, SyntheticGuestUiProbeActivity.class);
                            dispatch.setPackage(getPackageName());
                            dispatch.putExtra("proof_level", "e29");
                            dispatch.putExtra("run_id", runId);
                            dispatch.putExtra("dispatch_ticket", serializedTicket);
                            startActivity(dispatch);
                            finish();
                        } catch (Throwable error) {
                            failure("e29_internal_dispatch_intent", error);
                        }
                    });
                    preparationFinished = true;
                    return;
                }
            }

            stage = "signed_private_dex_and_resources";
            preparationStage = stage;
            LoadedGuestCode image = new GuestCodeLoader(getApplicationContext(), localStore)
                .load(localCluster.getId());
            ProbeData.check(GUEST_PACKAGE.equals(image.getPackageName()),
                "Signed private payload package mismatch");
            if ("e74".equals(proofLevel) || "e77".equals(proofLevel) || "e78".equals(proofLevel) || ("e79".equals(proofLevel) || "e80".equals(proofLevel))) {
                stage = ("e79".equals(proofLevel) || "e80".equals(proofLevel)) ? "e79_signed_private_activity_and_application_preflight" :
                    ("e78".equals(proofLevel) ? "e78_signed_private_activity_and_application_preflight" :
                        ("e77".equals(proofLevel) ? "e77_signed_private_activity_constructor_preflight" :
                            "e74_signed_private_activity_class_preflight"));
                preparationStage = stage;
                PrivateGuestActivityClassHandle activityProof = image.preflightExplicitActivityClass(
                    SIGNED_ACTIVITY, ExplicitActivityCaller.TRUSTED_INTERNAL);
                ProbeData.check(activityProof.getSelection().getClusterId().equals(localCluster.getId()) &&
                    activityProof.getSelection().getPackageName().equals(GUEST_PACKAGE) &&
                    activityProof.getSelection().getTargetActivityName().equals(SIGNED_ACTIVITY) &&
                    !activityProof.getSelection().getThroughAlias() &&
                    activityProof.getActivityClass().getName().equals(SIGNED_ACTIVITY) &&
                    activityProof.getActivityClass().getClassLoader() == image.getClassLoader() &&
                    Activity.class.isAssignableFrom(activityProof.getActivityClass()),
                    "Selected signed Activity bytecode was not defined by exact PRIVATE image");
                try {
                    getClassLoader().loadClass(SIGNED_ACTIVITY);
                    throw new SecurityException("Signed private Activity class leaked into host classloader");
                } catch (ClassNotFoundException expected) { /* No original/host package fallback. */ }
                ProbeData.check(localStore.open(localCluster.getId(), Build.VERSION.SDK_INT)
                    .equals(localCluster), "Private signed APK changed during Activity class preflight");
                privateActivityClassVerified = true;
                if ("e77".equals(proofLevel) || "e78".equals(proofLevel) || ("e79".equals(proofLevel) || "e80".equals(proofLevel)))
                    privateActivityConstructorPreflightVerified = true;
            }
            int marker = image.getResources().getIdentifier(
                "fonesi_guest_code_probe", "string", GUEST_PACKAGE);
            ProbeData.check(marker != 0 && "Fonesi signed private payload".equals(
                image.getResources().getString(marker)), "Private signed Resources marker not found");
            Class<?> screen = image.getClassLoader().loadClass(ENTRY);
            ProbeData.check(screen.getClassLoader() == image.getClassLoader(),
                "Signed private DEX, not host app classloader, must define the UI screen");
            try {
                getClassLoader().loadClass(ENTRY);
                throw new SecurityException("Synthetic screen was improperly packaged inside host APK");
            } catch (ClassNotFoundException required) { /* genuine private code */ }
            ProbeData.check(localStore.open(localCluster.getId(), Build.VERSION.SDK_INT)
                .equals(localCluster), "Private DEX/resources changed while preparing UI");
            if ("e78".equals(proofLevel)) {
                stage = "e78_signed_private_application_preflight_attach_and_scoped_data";
                preparationStage = stage;
                verifyE78SyntheticApplication(image, localStore, localCluster);
                ProbeData.check(localStore.open(localCluster.getId(), Build.VERSION.SDK_INT)
                    .equals(localCluster), "Signed private APK changed after E78 Application/scope proof");
            } else if (("e79".equals(proofLevel) || "e80".equals(proofLevel))) {
                stage = "e79_signed_private_application_process_restart_data_proof";
                preparationStage = stage;
                verifyE79SyntheticApplication(image, localStore, localCluster);
                ProbeData.check(localStore.open(localCluster.getId(), Build.VERSION.SDK_INT)
                    .equals(localCluster), "Signed private APK changed after E79 process restart proof");
            }
            preparationStage = "private_dex_ready_ui_callback_queued";
            PrivateApkCluster verifiedCluster = localCluster;
            PrivateApkStore verifiedStore = localStore;
            Class<?> verifiedScreen = screen;
            runOnUiThread(() -> {
                preparationStage = "private_dex_ready_ui_callback_running";
                display(verifiedScreen, verifiedStore, verifiedCluster);
            });
            preparationFinished = true;
        } catch (Throwable error) {
            final String failedAt = stage;
            preparationStage = "failed_" + failedAt;
            preparationFinished = true;
            runOnUiThread(() -> failure(failedAt, error));
        }
    }

    /**
     * E78 only: manually attach the project-owned signed synthetic Application with public
     * Instrumentation APIs. This does not register a package/Application with ActivityThread.
     * Storage work remains on this worker thread; attach/onCreate run on Android's main thread.
     */
    private void verifyE78SyntheticApplication(LoadedGuestCode image,
            PrivateApkStore verifiedStore, PrivateApkCluster verifiedCluster) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("E78 storage preparation must remain off the Android main Looper");
        final var applicationProof = image.preflightApplicationClass();
        ProbeData.check(SIGNED_APPLICATION.equals(applicationProof.getSignedName()) &&
            applicationProof.getApplicationClass().getName().equals(SIGNED_APPLICATION) &&
            applicationProof.getApplicationClass().getClassLoader() == image.getClassLoader() &&
            Application.class.isAssignableFrom(applicationProof.getApplicationClass()),
            "Signed PRIVATE Application class was not owned by the exact private loader");
        try {
            getClassLoader().loadClass(SIGNED_APPLICATION);
            throw new SecurityException("Signed PRIVATE Application class leaked into host classloader");
        } catch (ClassNotFoundException expected) { /* No host/original package fallback. */ }
        ProbeData.check(verifiedStore.open(verifiedCluster.getId(), Build.VERSION.SDK_INT).equals(verifiedCluster),
            "Private signed APK changed during Application class preflight");
        privateApplicationClassPreflightVerified = true;
        syntheticApplicationClassName = applicationProof.getSignedName();

        final String scopeA = UUID.randomUUID().toString();
        final String scopeB = UUID.randomUUID().toString();
        ProbeData.check(!scopeA.equals(scopeB) &&
            UUID.fromString(scopeA).toString().equals(scopeA) &&
            UUID.fromString(scopeB).toString().equals(scopeB),
            "E78 requires two distinct canonical UUID data scopes");
        syntheticScopeAId = scopeA;
        syntheticScopeBId = scopeB;
        final String markerA = randomHex();
        final String markerB = randomHex();
        ProbeData.check(!markerA.equals(markerB), "E78 synthetic markers must be distinct and non-secret");
        final SyntheticGuestScopedContext contextA =
            new SyntheticGuestScopedContext(getApplicationContext(), image, scopeA);
        final SyntheticGuestScopedContext contextB =
            new SyntheticGuestScopedContext(getApplicationContext(), image, scopeB);
        Class<?> scopedEntrypoint = image.getClassLoader().loadClass(SCOPED_ENTRY);
        ProbeData.check(scopedEntrypoint.getClassLoader() == image.getClassLoader(),
            "E78 scoped data helper must come from the signed PRIVATE DEX");
        try {
            getClassLoader().loadClass(SCOPED_ENTRY);
            throw new SecurityException("E78 scoped data helper leaked into host classloader");
        } catch (ClassNotFoundException expected) { /* exact private DEX only */ }
        java.lang.reflect.Method write = scopedEntrypoint.getMethod("write", Context.class, String.class);
        java.lang.reflect.Method read = scopedEntrypoint.getMethod("read", Context.class);
        ProbeData.check("EMPTY".equals(read.invoke(null, contextA)) &&
            "EMPTY".equals(read.invoke(null, contextB)),
            "E78 both fresh scoped stores must begin empty");

        final Application[] applications = new Application[2];
        final Throwable[] attachFailure = new Throwable[1];
        final CountDownLatch attached = new CountDownLatch(1);
        runOnUiThread(() -> {
            try {
                ProbeData.check(Looper.myLooper() == Looper.getMainLooper(),
                    "E78 Instrumentation attach/onCreate must execute on Android main thread");
                Instrumentation instrumentationA = new Instrumentation();
                applications[0] = instrumentationA.newApplication(
                    image.getClassLoader(), SIGNED_APPLICATION, contextA);
                ProbeData.check(applications[0].getClass() == applicationProof.getApplicationClass() &&
                    applications[0].getBaseContext() == contextA,
                    "Instrumentation did not attach Application A to exact scoped Context");
                instrumentationA.callApplicationOnCreate(applications[0]);

                Instrumentation instrumentationB = new Instrumentation();
                applications[1] = instrumentationB.newApplication(
                    image.getClassLoader(), SIGNED_APPLICATION, contextB);
                ProbeData.check(applications[1].getClass() == applicationProof.getApplicationClass() &&
                    applications[1].getBaseContext() == contextB,
                    "Instrumentation did not attach Application B to exact scoped Context");
                instrumentationB.callApplicationOnCreate(applications[1]);
            } catch (Throwable failure) {
                attachFailure[0] = failure;
            } finally {
                attached.countDown();
            }
        });
        if (!attached.await(45, TimeUnit.SECONDS))
            throw new IllegalStateException("E78 public Instrumentation attach/onCreate timed out");
        if (attachFailure[0] != null)
            throw new IllegalStateException("E78 public Instrumentation attach/onCreate failed", attachFailure[0]);
        ProbeData.check(applications[0] != null && applications[1] != null &&
            applications[0] != applications[1] &&
            applications[0].getClass().getClassLoader() == image.getClassLoader() &&
            applications[1].getClass().getClassLoader() == image.getClassLoader() &&
            applications[0].getBaseContext() == contextA &&
            applications[1].getBaseContext() == contextB &&
            applications[0].getPackageName().equals(GUEST_PACKAGE) &&
            applications[1].getPackageName().equals(GUEST_PACKAGE) &&
            applications[0].getResources() == image.getResources() &&
            applications[1].getResources() == image.getResources(),
            "E78 attached Application object/classloader/Context/resource identity mismatch");
        syntheticApplicationAttached = true;
        syntheticApplicationOnCreateVerified = true;
        syntheticApplicationMainThreadVerified = true;
        syntheticApplicationInstancesDistinct = true;

        SharedPreferences appPrefsA = contextA.getSharedPreferences("synthetic_application", Context.MODE_PRIVATE);
        SharedPreferences appPrefsB = contextB.getSharedPreferences("synthetic_application", Context.MODE_PRIVATE);
        ProbeData.check(appPrefsA.getInt("starts", 0) == 1 && appPrefsB.getInt("starts", 0) == 1,
            "E78 each synthetic Application must persist exactly one onCreate marker");

        write.invoke(null, contextA, markerA);
        write.invoke(null, contextB, markerB);
        String readA = (String) read.invoke(null, contextA);
        String readB = (String) read.invoke(null, contextB);
        ProbeData.check(markerA.equals(readA) && markerB.equals(readB),
            "E78 SharedPreferences/file/SQLite write/readback did not match both scopes");
        syntheticScopeAStorageVerified = true;
        syntheticScopeBStorageVerified = true;

        SyntheticGuestScopedContext reopenedA =
            new SyntheticGuestScopedContext(getApplicationContext(), image, scopeA);
        SyntheticGuestScopedContext reopenedB =
            new SyntheticGuestScopedContext(getApplicationContext(), image, scopeB);
        ProbeData.check(reopenedA != contextA && reopenedB != contextB &&
            markerA.equals(read.invoke(null, reopenedA)) &&
            markerB.equals(read.invoke(null, reopenedB)) &&
            reopenedA.getSharedPreferences("synthetic_application", Context.MODE_PRIVATE).getInt("starts", 0) == 1 &&
            reopenedB.getSharedPreferences("synthetic_application", Context.MODE_PRIVATE).getInt("starts", 0) == 1,
            "E78 scope state did not survive Context wrapper reconstruction");
        syntheticScopePersistenceVerified = true;
        ProbeData.check(markerA.equals((String) read.invoke(null, reopenedA)) &&
            markerB.equals((String) read.invoke(null, reopenedB)) &&
            !markerB.equals((String) read.invoke(null, reopenedA)) &&
            !markerA.equals((String) read.invoke(null, reopenedB)),
            "E78 cross-scope readback detected data leakage");
        syntheticScopeCrossReadbackVerified = true;
        ProbeData.check(verifiedStore.open(verifiedCluster.getId(), Build.VERSION.SDK_INT).equals(verifiedCluster),
            "Signed private APK changed during E78 scoped data verification");
    }

    /**
     * E79 only: write in one host process, force-stop it externally, then verify in a new process.
     * Both Application objects are still project-owned synthetic code in the host UID.
     */
    private void verifyE79SyntheticApplication(LoadedGuestCode image,
            PrivateApkStore verifiedStore, PrivateApkCluster verifiedCluster) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("E79 scoped storage work must remain off Android main Looper");
        final var applicationProof = image.preflightApplicationClass();
        ProbeData.check(SIGNED_APPLICATION.equals(applicationProof.getSignedName()) &&
            applicationProof.getApplicationClass().getName().equals(SIGNED_APPLICATION) &&
            applicationProof.getApplicationClass().getClassLoader() == image.getClassLoader() &&
            Application.class.isAssignableFrom(applicationProof.getApplicationClass()),
            "E79 signed PRIVATE Application class was not owned by exact private loader");
        try { getClassLoader().loadClass(SIGNED_APPLICATION);
            throw new SecurityException("E79 PRIVATE Application leaked into host classloader");
        } catch (ClassNotFoundException expected) { /* no host/original package fallback */ }
        ProbeData.check(verifiedStore.open(verifiedCluster.getId(), Build.VERSION.SDK_INT).equals(verifiedCluster),
            "E79 private APK changed before process lifecycle proof");
        privateApplicationClassPreflightVerified = true;
        syntheticApplicationClassName = applicationProof.getSignedName();
        if ("e80".equals(proofLevel))
            verifyE80UnattachedPrivateActivity(image, verifiedStore, verifiedCluster);

        final String scopeA;
        final String scopeB;
        final String markerA;
        final String markerB;
        if ("write".equals(e79Stage)) {
            scopeA = UUID.randomUUID().toString();
            scopeB = UUID.randomUUID().toString();
            markerA = randomHex();
            markerB = randomHex();
        } else if ("verify".equals(e79Stage)) {
            scopeA = syntheticScopeAId;
            scopeB = syntheticScopeBId;
            markerA = e79MarkerA;
            markerB = e79MarkerB;
            ProbeData.check(Process.myPid() != e79PriorPid,
                "E79 verify stage did not start in a different host process PID");
        } else {
            throw new IllegalArgumentException("E79 stage must be write or verify");
        }
        ProbeData.check(canonicalUuid(scopeA) && canonicalUuid(scopeB) && !scopeA.equals(scopeB) &&
            markerA != null && markerA.matches("[0-9a-f]{32}") &&
            markerB != null && markerB.matches("[0-9a-f]{32}") && !markerA.equals(markerB),
            "E79 requires two canonical scopes and distinct non-secret markers");
        syntheticScopeAId = scopeA;
        syntheticScopeBId = scopeB;
        final SyntheticGuestScopedContext contextA =
            new SyntheticGuestScopedContext(getApplicationContext(), image, scopeA);
        final SyntheticGuestScopedContext contextB =
            new SyntheticGuestScopedContext(getApplicationContext(), image, scopeB);
        if (e81ScopedMode)
            verifyE81PrivateScopedMethodBinding(image, verifiedStore, verifiedCluster,
                contextA, contextB, scopeA, scopeB);
        Class<?> scopedEntrypoint = image.getClassLoader().loadClass(SCOPED_ENTRY);
        ProbeData.check(scopedEntrypoint.getClassLoader() == image.getClassLoader(),
            "E79 scoped storage helper must be loaded from signed PRIVATE DEX");
        java.lang.reflect.Method write = scopedEntrypoint.getMethod("write", Context.class, String.class);
        java.lang.reflect.Method read = scopedEntrypoint.getMethod("read", Context.class);
        if ("write".equals(e79Stage)) {
            ProbeData.check("EMPTY".equals(read.invoke(null, contextA)) &&
                "EMPTY".equals(read.invoke(null, contextB)), "E79 write stage requires fresh empty scopes");
        }

        final Application[] applications = new Application[2];
        final Throwable[] attachFailure = new Throwable[1];
        final CountDownLatch attached = new CountDownLatch(1);
        runOnUiThread(() -> {
            try {
                ProbeData.check(Looper.myLooper() == Looper.getMainLooper(),
                    "E79 Instrumentation attach/onCreate must execute on Android main thread");
                Instrumentation instrumentationA = new Instrumentation();
                applications[0] = instrumentationA.newApplication(image.getClassLoader(), SIGNED_APPLICATION, contextA);
                ProbeData.check(applications[0].getClass() == applicationProof.getApplicationClass() &&
                    applications[0].getBaseContext() == contextA, "E79 Application A attach Context mismatch");
                instrumentationA.callApplicationOnCreate(applications[0]);
                Instrumentation instrumentationB = new Instrumentation();
                applications[1] = instrumentationB.newApplication(image.getClassLoader(), SIGNED_APPLICATION, contextB);
                ProbeData.check(applications[1].getClass() == applicationProof.getApplicationClass() &&
                    applications[1].getBaseContext() == contextB, "E79 Application B attach Context mismatch");
                instrumentationB.callApplicationOnCreate(applications[1]);
            } catch (Throwable failure) { attachFailure[0] = failure; }
            finally { attached.countDown(); }
        });
        if (!attached.await(45, TimeUnit.SECONDS))
            throw new IllegalStateException("E79 public Instrumentation attach/onCreate timed out");
        if (attachFailure[0] != null)
            throw new IllegalStateException("E79 public Instrumentation attach/onCreate failed", attachFailure[0]);
        ProbeData.check(applications[0] != null && applications[1] != null && applications[0] != applications[1] &&
            applications[0].getClass().getClassLoader() == image.getClassLoader() &&
            applications[1].getClass().getClassLoader() == image.getClassLoader() &&
            applications[0].getResources() == image.getResources() && applications[1].getResources() == image.getResources(),
            "E79 Application object/classloader/resource identity mismatch");
        java.lang.reflect.Method tokenMethod = applicationProof.getApplicationClass().getMethod("processToken");
        String tokenA = (String) tokenMethod.invoke(applications[0]);
        String tokenB = (String) tokenMethod.invoke(applications[1]);
        ProbeData.check(canonicalUuid(tokenA) && tokenA.equals(tokenB),
            "E79 Application instances must share one canonical per-process token");
        e79ProcessToken = tokenA;
        e79StartCountA = contextA.getSharedPreferences("synthetic_application", Context.MODE_PRIVATE).getInt("starts", 0);
        e79StartCountB = contextB.getSharedPreferences("synthetic_application", Context.MODE_PRIVATE).getInt("starts", 0);
        int lastPidA = contextA.getSharedPreferences("synthetic_application", Context.MODE_PRIVATE).getInt("last_pid", -1);
        int lastPidB = contextB.getSharedPreferences("synthetic_application", Context.MODE_PRIVATE).getInt("last_pid", -1);
        String lastTokenA = contextA.getSharedPreferences("synthetic_application", Context.MODE_PRIVATE).getString("last_process_token", null);
        String lastTokenB = contextB.getSharedPreferences("synthetic_application", Context.MODE_PRIVATE).getString("last_process_token", null);
        int expectedStarts = "write".equals(e79Stage) ? 1 : 2;
        ProbeData.check(e79StartCountA == expectedStarts && e79StartCountB == expectedStarts &&
            lastPidA == Process.myPid() && lastPidB == Process.myPid() &&
            tokenA.equals(lastTokenA) && tokenA.equals(lastTokenB),
            "E79 persisted Application onCreate count/process marker is not exact");
        if ("verify".equals(e79Stage)) {
            ProbeData.check(!tokenA.equals(e79PriorProcessToken) && Process.myPid() != e79PriorPid,
                "E79 process token/PID did not change after force-stop and relaunch");
            e79ProcessRestartVerified = true;
        }
        syntheticApplicationAttached = true;
        syntheticApplicationOnCreateVerified = true;
        syntheticApplicationMainThreadVerified = true;
        syntheticApplicationInstancesDistinct = true;
        if ("write".equals(e79Stage)) {
            write.invoke(null, contextA, markerA);
            write.invoke(null, contextB, markerB);
        }
        ProbeData.check(markerA.equals((String) read.invoke(null, contextA)) &&
            markerB.equals((String) read.invoke(null, contextB)),
            "E79 preferences/file/SQLite markers differ from expected process handoff values");
        syntheticScopeAStorageVerified = true;
        syntheticScopeBStorageVerified = true;
        SyntheticGuestScopedContext reopenedA = new SyntheticGuestScopedContext(getApplicationContext(), image, scopeA);
        SyntheticGuestScopedContext reopenedB = new SyntheticGuestScopedContext(getApplicationContext(), image, scopeB);
        ProbeData.check(markerA.equals((String) read.invoke(null, reopenedA)) &&
            markerB.equals((String) read.invoke(null, reopenedB)) &&
            reopenedA.getSharedPreferences("synthetic_application", Context.MODE_PRIVATE).getInt("starts", 0) == expectedStarts &&
            reopenedB.getSharedPreferences("synthetic_application", Context.MODE_PRIVATE).getInt("starts", 0) == expectedStarts,
            "E79 data did not survive Context reconstruction in current process");
        syntheticScopePersistenceVerified = true;
        ProbeData.check(markerA.equals((String) read.invoke(null, reopenedA)) &&
            markerB.equals((String) read.invoke(null, reopenedB)) &&
            !markerB.equals((String) read.invoke(null, reopenedA)) &&
            !markerA.equals((String) read.invoke(null, reopenedB)),
            "E79 cross-scope readback detected data leakage");
        syntheticScopeCrossReadbackVerified = true;
        if (e81ScopedMode) {
            ProbeData.check(e81PrivateActivity != null &&
                scopeA.equals(e81BoundScopeId) && e81PrivateBaseContextStillNull &&
                e81PrivateInstanceScopeBound && e81PrivateRebindingRejected,
                "E81 Activity scope preflight did not complete");
            java.lang.reflect.Method readViaPrivateActivity = e81PrivateActivity.getClass()
                .getMethod("readBoundSyntheticScopeForTestOnly");
            String viaActivity = (String) readViaPrivateActivity.invoke(e81PrivateActivity);
            ProbeData.check(markerA.equals(viaActivity) && !markerB.equals(viaActivity) &&
                e81PrivateActivity.getBaseContext() == null,
                "E81 PRIVATE Activity scoped method did not preserve A/B data boundary");
            e81PrivateScopedDataReadbackVerified = true;
        }
        if ("verify".equals(e79Stage)) e79ScopedDataAfterRestartVerified = true;
        e79MarkerA = markerA;
        e79MarkerB = markerB;
        ProbeData.check(verifiedStore.open(verifiedCluster.getId(), Build.VERSION.SDK_INT).equals(verifiedCluster),
            "E79 signed private APK changed during process restart data verification");
    }
    /**
     * E80 bounded proof: construct one project-owned signed PRIVATE Activity Java object on
     * the Android main thread via public Instrumentation. No framework attach or onCreate.
     */
    private void verifyE80UnattachedPrivateActivity(LoadedGuestCode image,
            PrivateApkStore verifiedStore, PrivateApkCluster verifiedCluster) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("E80 private Activity preparation must run off main Looper");
        PrivateGuestActivityClassHandle signed = image.preflightExplicitActivityClass(
            SIGNED_ACTIVITY, ExplicitActivityCaller.TRUSTED_INTERNAL);
        ProbeData.check(signed.getSelection().getClusterId().equals(verifiedCluster.getId()) &&
            signed.getSelection().getTargetActivityName().equals(SIGNED_ACTIVITY) &&
            signed.getActivityClass().getClassLoader() == image.getClassLoader(),
            "E80 signed Activity target was not owned by exact PRIVATE code image");
        ProbeData.check(verifiedStore.open(verifiedCluster.getId(), Build.VERSION.SDK_INT)
            .equals(verifiedCluster), "E80 signed PRIVATE image changed before construction");
        final Throwable[] failure = new Throwable[1];
        final CountDownLatch finished = new CountDownLatch(1);
        runOnUiThread(() -> {
            try {
                ProbeData.check(Looper.myLooper() == Looper.getMainLooper(),
                    "E80 Activity constructor must be called on Android main thread");
                Class<?> tracker = image.getClassLoader().loadClass(
                    "vn.fonesi.payload.SyntheticActivityConstructionProbe");
                ProbeData.check(tracker.getClassLoader() == image.getClassLoader(),
                    "E80 constructor tracker must be defined by signed PRIVATE image");
                java.lang.reflect.Method count = tracker.getMethod("constructedCount");
                ProbeData.check(((Number) count.invoke(null)).intValue() == 0,
                    "E80 private Activity was constructed before authorized preflight");
                Activity created = new Instrumentation().newActivity(
                    image.getClassLoader(), SIGNED_ACTIVITY, new Intent());
                int after = ((Number) count.invoke(null)).intValue();
                ProbeData.check(created != null && created != SyntheticGuestUiProbeActivity.this &&
                    created.getClass() == signed.getActivityClass() &&
                    created.getClass().getClassLoader() == image.getClassLoader() && after == 1,
                    "E80 exact signed PRIVATE Activity constructor did not run once");
                ProbeData.check(created.getBaseContext() == null,
                    "E80 synthetic private Activity unexpectedly received a framework Context");
                e80PrivateActivityConstructorCount = after;
                if (e81ScopedMode) e81PrivateActivity = created;
                e80PrivateActivityClassName = created.getClass().getName();
                e80PrivateActivityMainThreadVerified = true;
                e80PrivateActivityLoaderVerified = true;
                e80PrivateActivityUnattachedVerified = true;
                e80PrivateActivityConstructorVerified = true;
            } catch (Throwable error) {
                failure[0] = error;
            } finally {
                finished.countDown();
            }
        });
        if (!finished.await(45, TimeUnit.SECONDS))
            throw new IllegalStateException("E80 public Instrumentation.newActivity timed out");
        if (failure[0] != null)
            throw new IllegalStateException("E80 signed PRIVATE constructor failed", failure[0]);
        ProbeData.check(verifiedStore.open(verifiedCluster.getId(), Build.VERSION.SDK_INT)
            .equals(verifiedCluster), "E80 signed PRIVATE image changed after constructor");
    }

    /**
     * E81: execute ONLY project-owned signed PRIVATE Activity instance methods against
     * exact synthetic scope A; framework base Context stays null, no onCreate.
     */
    private void verifyE81PrivateScopedMethodBinding(LoadedGuestCode image,
            PrivateApkStore verifiedStore, PrivateApkCluster verifiedCluster,
            SyntheticGuestScopedContext contextA, SyntheticGuestScopedContext contextB,
            String scopeA, String scopeB) throws Exception {
        ProbeData.check(e81PrivateActivity != null && e80PrivateActivityConstructorVerified &&
            e81PrivateActivity.getClass().getClassLoader() == image.getClassLoader() &&
            e81PrivateActivity.getBaseContext() == null &&
            scopeA.equals(contextA.getScopeId()) && scopeB.equals(contextB.getScopeId()) &&
            !scopeA.equals(scopeB), "E81 Activity/scope identity was not signed or distinct");
        ProbeData.check(verifiedStore.open(verifiedCluster.getId(), Build.VERSION.SDK_INT)
            .equals(verifiedCluster), "E81 PRIVATE APK changed before scoped method binding");
        final Throwable[] failure = new Throwable[1];
        final CountDownLatch finished = new CountDownLatch(1);
        runOnUiThread(() -> {
            try {
                ProbeData.check(Looper.myLooper() == Looper.getMainLooper(),
                    "E81 scoped PRIVATE Activity method must bind on Android main Looper");
                Activity instance = e81PrivateActivity;
                java.lang.reflect.Method bind = instance.getClass().getMethod(
                    "bindSyntheticScopedContextForTestOnly", Context.class, String.class);
                java.lang.reflect.Method scopeId = instance.getClass().getMethod(
                    "boundSyntheticScopeIdForTestOnly");
                ProbeData.check(bind.getDeclaringClass() == instance.getClass() &&
                    scopeId.getDeclaringClass() == instance.getClass(),
                    "E81 methods must be defined by exact PRIVATE Activity");
                bind.invoke(instance, contextA, scopeA);
                ProbeData.check(scopeA.equals(scopeId.invoke(instance)) &&
                    instance.getBaseContext() == null, "E81 bind leaked framework Context");
                try {
                    bind.invoke(instance, contextB, scopeB);
                    throw new IllegalStateException("E81 PRIVATE Activity allowed cross-scope rebinding");
                } catch (java.lang.reflect.InvocationTargetException expected) {
                    ProbeData.check(expected.getCause() instanceof IllegalStateException,
                        "E81 binding did not reject B scope as immutable");
                }
                try {
                    bind.invoke(instance, contextA, scopeA);
                    throw new IllegalStateException("E81 PRIVATE Activity allowed duplicate binding");
                } catch (java.lang.reflect.InvocationTargetException expected) {
                    ProbeData.check(expected.getCause() instanceof IllegalStateException,
                        "E81 binding did not reject duplicate A scope");
                }
                ProbeData.check(scopeA.equals(scopeId.invoke(instance)) &&
                    instance.getBaseContext() == null, "E81 rejected rebinding changed scope identity");
                e81BoundScopeId = scopeA;
                e81PrivateBaseContextStillNull = true;
                e81PrivateRebindingRejected = true;
                e81PrivateInstanceScopeBound = true;
            } catch (Throwable error) { failure[0] = error; }
            finally { finished.countDown(); }
        });
        if (!finished.await(45, TimeUnit.SECONDS))
            throw new IllegalStateException("E81 synthetic scoped method binding timed out");
        if (failure[0] != null)
            throw new IllegalStateException("E81 signed PRIVATE Activity scoped binding failed", failure[0]);
        ProbeData.check(verifiedStore.open(verifiedCluster.getId(), Build.VERSION.SDK_INT)
            .equals(verifiedCluster), "E81 PRIVATE APK changed after scoped binding");
    }

    private static boolean canonicalUuid(String value) {
        if (value == null) return false;
        try { return UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException ignored) { return false; }
    }

    private static String randomHex() {
        byte[] value = new byte[16];
        new SecureRandom().nextBytes(value);
        return hex(value);
    }

    private void display(Class<?> screen, PrivateApkStore verifiedStore,
            PrivateApkCluster verifiedCluster) {
        if (destroyed || reported) return;
        try {
            if (!androidEvents.equals(Arrays.asList("CREATE", "START", "RESUME")))
                throw new IllegalStateException("Expected live/resumed Android host Activity");
            privateEntrypoint = screen;
            store = verifiedStore;
            cluster = verifiedCluster;
            signedView = (View) screen.getMethod("createView", Activity.class, String.class)
                .invoke(null, this, challenge);
            if (!(signedView instanceof TextView) ||
                !"FONESI-E24-SIGNED-PRIVATE-VIEW".equals(signedView.getTag()) ||
                !((TextView) signedView).getText().toString().equals(
                    PREFIX + hex(MessageDigest.getInstance("SHA-256").digest(
                        (PREFIX + challenge).getBytes(StandardCharsets.UTF_8)))))
                throw new SecurityException("Private signed View marker failed challenge verification");
            verifiedPrivateClass = true;
            verifiedPrivateResource = true;
            // The Android registered host Activity owns the Window. The private DEX created its View.
            setContentView(signedView);
            for (String event : androidEvents)
                screen.getMethod("lifecycle", String.class).invoke(null, event);
            signedView.post(() -> {
                if (destroyed || reported) return;
                try {
                    ProbeData.check(signedView.isAttachedToWindow() &&
                        signedView.getWindowToken() != null && signedView.getRootView() != null,
                        "Signed private View did not actually attach to the Android window");
                    ProbeData.check(store.open(cluster.getId(), Build.VERSION.SDK_INT).equals(cluster),
                        "Signed private UI APK changed after attachment");
                    viewAttached = true;
                    finish(); // Trigger real Android host PAUSE, STOP and DESTROY.
                } catch (Throwable error) {
                    failure("real_android_view_attachment", error);
                }
            });
        } catch (Throwable error) {
            failure("private_view_factory_or_lifecycle_replay", error);
        }
    }

    private void record(String event) {
        androidEvents.add(event);
        if (privateEntrypoint != null) {
            try {
                privateEntrypoint.getMethod("lifecycle", String.class).invoke(null, event);
            } catch (Throwable failure) {
                failure("host_lifecycle_relay_" + event, failure);
            }
        }
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        configurationChangeCallbacks++;
    }

    @Override protected void onStart() {
        super.onStart();
        record("START");
    }

    @Override protected void onResume() {
        super.onResume();
        record("RESUME");
    }

    @Override protected void onPause() {
        record("PAUSE");
        super.onPause();
    }

    @Override protected void onStop() {
        record("STOP");
        super.onStop();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        String verificationStage = "final_guest_event";
        try {
            record("DESTROY");
            if (!reported && !dispatchOnly) {
                verificationStage = "final_signed_view_attachment";
                ProbeData.check(viewAttached && verifiedPrivateClass && verifiedPrivateResource,
                    "Private View/code/resources were not all verified");
                verificationStage = "final_e25_private_catalog";
                if ("e25".equals(proofLevel) || "e29".equals(proofLevel))
                    ProbeData.check(privateComponentCatalogVerified,
                        "Signed private Android manifest component metadata was not verified");
                if ("e29".equals(proofLevel))
                    ProbeData.check(syntheticComponentDispatchVerified,
                        "E29 private component dispatch ticket was not validated before private code load");
                verificationStage = "final_e74_private_activity_class_preflight";
                if ("e74".equals(proofLevel) || "e77".equals(proofLevel) || "e78".equals(proofLevel) || ("e79".equals(proofLevel) || "e80".equals(proofLevel)))
                    ProbeData.check(privateActivityClassVerified,
                        "Signed PRIVATE Activity class was not preflighted before hosted View");
                if ("e77".equals(proofLevel) || "e78".equals(proofLevel) || ("e79".equals(proofLevel) || "e80".equals(proofLevel)))
                    ProbeData.check(privateActivityConstructorPreflightVerified,
                        "E77 public no-argument constructor preflight was not verified");
                if ("e78".equals(proofLevel)) {
                    verificationStage = "final_e78_synthetic_application_and_scoped_data";
                    ProbeData.check(privateApplicationClassPreflightVerified &&
                        syntheticApplicationAttached && syntheticApplicationOnCreateVerified &&
                        syntheticApplicationMainThreadVerified && syntheticApplicationInstancesDistinct &&
                        syntheticScopeAStorageVerified && syntheticScopeBStorageVerified &&
                        syntheticScopePersistenceVerified && syntheticScopeCrossReadbackVerified &&
                        SIGNED_APPLICATION.equals(syntheticApplicationClassName) &&
                        syntheticScopeAId != null && syntheticScopeBId != null &&
                        !syntheticScopeAId.equals(syntheticScopeBId),
                        "E78 signed Application attach/onCreate or two-scope data proof is incomplete");
                }
                if (("e79".equals(proofLevel) || "e80".equals(proofLevel))) {
                    verificationStage = "final_e79_process_restart_and_scoped_data";
                    int expectedStarts = "write".equals(e79Stage) ? 1 : 2;
                    ProbeData.check(privateApplicationClassPreflightVerified &&
                        syntheticApplicationAttached && syntheticApplicationOnCreateVerified &&
                        syntheticApplicationMainThreadVerified && syntheticApplicationInstancesDistinct &&
                        syntheticScopeAStorageVerified && syntheticScopeBStorageVerified &&
                        syntheticScopePersistenceVerified && syntheticScopeCrossReadbackVerified &&
                        SIGNED_APPLICATION.equals(syntheticApplicationClassName) &&
                        canonicalUuid(syntheticScopeAId) && canonicalUuid(syntheticScopeBId) &&
                        !syntheticScopeAId.equals(syntheticScopeBId) &&
                        canonicalUuid(e79ProcessToken) &&
                        e79StartCountA == expectedStarts && e79StartCountB == expectedStarts &&
                        ("write".equals(e79Stage) || ("verify".equals(e79Stage) &&
                            e79ProcessRestartVerified && e79ScopedDataAfterRestartVerified)),
                        "E79 process restart or two-scope persistence proof is incomplete");
                }
                if (e81ScopedMode) {
                    verificationStage = "final_e81_private_activity_scoped_method_only";
                    ProbeData.check(e81PrivateInstanceScopeBound && e81PrivateRebindingRejected &&
                        e81PrivateScopedDataReadbackVerified && e81PrivateBaseContextStillNull &&
                        syntheticScopeAId.equals(e81BoundScopeId),
                        "E81 scoped PRIVATE Activity instance-method proof is incomplete");
                }
                if ("e80".equals(proofLevel)) {
                    verificationStage = "final_e80_private_activity_java_constructor_only";
                    ProbeData.check(e80PrivateActivityConstructorVerified &&
                        e80PrivateActivityMainThreadVerified &&
                        e80PrivateActivityLoaderVerified &&
                        e80PrivateActivityUnattachedVerified &&
                        e80PrivateActivityConstructorCount == 1 &&
                        SIGNED_ACTIVITY.equals(e80PrivateActivityClassName),
                        "E80 signed PRIVATE synthetic Activity construction proof is incomplete");
                }
                verificationStage = "final_android_host_lifecycle";
                ProbeData.check(COMPLETE.equals(androidEvents),
                    "Actual Android Activity lifecycle was incomplete");
                verificationStage = "final_private_dex_transcript";
                String privateSequence = (String) privateEntrypoint
                    .getMethod("completedTranscript").invoke(null);
                ProbeData.check(String.join(",", COMPLETE).equals(privateSequence),
                    "Synthetic private bytecode did not receive every host lifecycle callback");
                verificationStage = "final_signed_apk_readback";
                ProbeData.check(store.open(cluster.getId(), Build.VERSION.SDK_INT).equals(cluster) &&
                    NativeRuntime.state() == NativeRuntime.ENABLED, "Signed private image changed");
                verificationStage = "final_persist_success_receipt";
                JSONObject receipt = new JSONObject()
                    .put("complete", true).put("passed", true).put("run_id", runId)
                    .put("proof_level", proofLevel)
                    .put("api", Build.VERSION.SDK_INT)
                    .put("pid", Process.myPid()).put("uid", Process.myUid())
                    .put("process_name", Build.VERSION.SDK_INT >= 28 ? Application.getProcessName() : "unsupported")
                    .put("real_host_activity_lifecycle_verified", true)
                    .put("signed_private_guest_view_rendered", true)
                    .put("private_guest_view_attached_to_window", true)
                    .put("private_guest_lifecycle_relay_verified", true)
                    .put("signed_private_resources_verified", true)
                    .put("signed_private_image_rechecked", true)
                    .put("private_component_manifest_verified", privateComponentCatalogVerified)
                    .put("private_activity_class_preflight_verified", privateActivityClassVerified)
                    .put("private_activity_constructor_preflight_verified", privateActivityConstructorPreflightVerified)
                    .put("private_application_class_preflight_verified", privateApplicationClassPreflightVerified)
                    .put("synthetic_guest_application_attached", syntheticApplicationAttached)
                    .put("synthetic_guest_application_on_create_verified", syntheticApplicationOnCreateVerified)
                    .put("synthetic_guest_application_on_main_thread", syntheticApplicationMainThreadVerified)
                    .put("synthetic_guest_application_instances_distinct", syntheticApplicationInstancesDistinct)
                    .put("synthetic_application_on_create_once_per_scope", syntheticApplicationOnCreateVerified)
                    .put("synthetic_application_class_name", syntheticApplicationClassName)
                    .put("synthetic_scope_a_id", syntheticScopeAId)
                    .put("synthetic_scope_b_id", syntheticScopeBId)
                    .put("synthetic_scope_a_preferences_file_sqlite_verified", syntheticScopeAStorageVerified)
                    .put("synthetic_scope_b_preferences_file_sqlite_verified", syntheticScopeBStorageVerified)
                    .put("synthetic_scope_persistence_after_context_reconstruction_verified", syntheticScopePersistenceVerified)
                    .put("synthetic_scope_cross_readback_isolated", syntheticScopeCrossReadbackVerified)
                    .put("e79_stage", e79Stage)
                    .put("e79_private_cluster_id", e79PrivateClusterId)
                    .put("e79_process_token", e79ProcessToken)
                    .put("e79_marker_a", e79MarkerA)
                    .put("e79_marker_b", e79MarkerB)
                    .put("e79_prior_pid", e79PriorPid)
                    .put("e79_prior_process_token", e79PriorProcessToken)
                    .put("e79_prior_run_id", e79PriorRunId)
                    .put("e79_start_count_a", e79StartCountA)
                    .put("e79_start_count_b", e79StartCountB)
                    .put("e79_process_restart_verified", e79ProcessRestartVerified)
                    .put("e79_scoped_data_after_process_restart_verified", e79ScopedDataAfterRestartVerified)
                    .put("e80_private_activity_constructor_verified", e80PrivateActivityConstructorVerified)
                    .put("e80_private_activity_main_thread_verified", e80PrivateActivityMainThreadVerified)
                    .put("e80_private_activity_private_loader_verified", e80PrivateActivityLoaderVerified)
                    .put("e80_private_activity_unattached_verified", e80PrivateActivityUnattachedVerified)
                    .put("e80_private_activity_constructor_count", e80PrivateActivityConstructorCount)
                    .put("e80_private_activity_class_name", e80PrivateActivityClassName)
                    .put("e80_guest_activity_oncreate_called", false)
                    .put("e81_scoped_mode", e81ScopedMode)
                    .put("e81_private_activity_scope_bound_verified", e81PrivateInstanceScopeBound)
                    .put("e81_private_activity_rebinding_rejected", e81PrivateRebindingRejected)
                    .put("e81_private_activity_scoped_readback_verified", e81PrivateScopedDataReadbackVerified)
                    .put("e81_private_activity_base_context_null", e81PrivateBaseContextStillNull)
                    .put("e81_bound_scope_id", e81BoundScopeId)
                    .put("e81_guest_activity_oncreate_called", false)
                    .put("e81_framework_activity_attached", false)
                                        .put("guest_application_registered_with_activity_thread", false)
                    .put("guest_application_registered_with_package_manager", false)
                    .put("full_guest_application_lifecycle_verified", false)
                    .put("source_commit", sourceCommit)
                    .put("probe_apk_sha256", probeApkSha256)
                    .put("payload_apk_sha256", payloadApkSha256)
                    .put("synthetic_component_dispatch_verified", syntheticComponentDispatchVerified)
                    .put("dispatch_ticket_private_apk_sha256", dispatchTicketPrivateApkSha256)
                    .put("production_clone_accepted", false)
                    .put("third_party_component_executed", false)
                    .put("real_guest_activity_registered", false)
                    .put("lifecycle_transcript", privateSequence)
                    .put("same_android_uid", true).put("kernel_uid_isolation", false)
                    .put("configuration_change_callbacks", configurationChangeCallbacks)
                    .put("actual_guest_activity_attached", false)
                    .put("third_party_guest_components_tested", false)
                    .put("social_accounts_tested", false);
                saveReceipt(receipt);
            }
        } catch (Throwable error) {
            failure(verificationStage, error);
        } finally {
            super.onDestroy();
        }
    }

    private void failure(String stage, Throwable error) {
        if (reported) return;
        failureStage = stage;
        try {
            JSONObject receipt = new JSONObject()
                .put("complete", true).put("passed", false).put("run_id", runId)
                .put("proof_level", proofLevel)
                .put("failure_stage", stage)
                .put("failure_type", error.getClass().getSimpleName())
                .put("android_lifecycle_transcript", String.join(",", androidEvents))
                .put("signed_view_exists", signedView != null)
                .put("signed_view_attached", signedView != null && signedView.isAttachedToWindow())
                .put("view_attached_verified", viewAttached)
                .put("private_class_verified", verifiedPrivateClass)
                .put("private_resource_verified", verifiedPrivateResource)
                .put("private_component_catalog_verified", privateComponentCatalogVerified)
                .put("private_activity_class_preflight_verified", privateActivityClassVerified)
                .put("private_activity_constructor_preflight_verified", privateActivityConstructorPreflightVerified)
                .put("private_application_class_preflight_verified", privateApplicationClassPreflightVerified)
                .put("synthetic_guest_application_attached", syntheticApplicationAttached)
                .put("synthetic_guest_application_on_create_verified", syntheticApplicationOnCreateVerified)
                .put("synthetic_guest_application_on_main_thread", syntheticApplicationMainThreadVerified)
                .put("synthetic_guest_application_instances_distinct", syntheticApplicationInstancesDistinct)
                .put("synthetic_application_on_create_once_per_scope", syntheticApplicationOnCreateVerified)
                .put("synthetic_application_class_name", syntheticApplicationClassName)
                .put("synthetic_scope_a_id", syntheticScopeAId)
                .put("synthetic_scope_b_id", syntheticScopeBId)
                .put("synthetic_scope_a_preferences_file_sqlite_verified", syntheticScopeAStorageVerified)
                .put("synthetic_scope_b_preferences_file_sqlite_verified", syntheticScopeBStorageVerified)
                .put("synthetic_scope_persistence_after_context_reconstruction_verified", syntheticScopePersistenceVerified)
                .put("synthetic_scope_cross_readback_isolated", syntheticScopeCrossReadbackVerified)
                .put("e79_stage", e79Stage)
                .put("e79_private_cluster_id", e79PrivateClusterId)
                .put("e79_process_token", e79ProcessToken)
                .put("e79_marker_a", e79MarkerA)
                .put("e79_marker_b", e79MarkerB)
                .put("e79_prior_pid", e79PriorPid)
                .put("e79_prior_process_token", e79PriorProcessToken)
                .put("e79_prior_run_id", e79PriorRunId)
                .put("e79_start_count_a", e79StartCountA)
                .put("e79_start_count_b", e79StartCountB)
                .put("e79_process_restart_verified", e79ProcessRestartVerified)
                .put("e79_scoped_data_after_process_restart_verified", e79ScopedDataAfterRestartVerified)
                    .put("e80_private_activity_constructor_verified", e80PrivateActivityConstructorVerified)
                    .put("e80_private_activity_main_thread_verified", e80PrivateActivityMainThreadVerified)
                    .put("e80_private_activity_private_loader_verified", e80PrivateActivityLoaderVerified)
                    .put("e80_private_activity_unattached_verified", e80PrivateActivityUnattachedVerified)
                    .put("e80_private_activity_constructor_count", e80PrivateActivityConstructorCount)
                    .put("e80_private_activity_class_name", e80PrivateActivityClassName)
                    .put("e80_guest_activity_oncreate_called", false)
                    .put("e81_scoped_mode", e81ScopedMode)
                    .put("e81_private_activity_scope_bound_verified", e81PrivateInstanceScopeBound)
                    .put("e81_private_activity_rebinding_rejected", e81PrivateRebindingRejected)
                    .put("e81_private_activity_scoped_readback_verified", e81PrivateScopedDataReadbackVerified)
                    .put("e81_private_activity_base_context_null", e81PrivateBaseContextStillNull)
                    .put("e81_bound_scope_id", e81BoundScopeId)
                    .put("e81_guest_activity_oncreate_called", false)
                    .put("e81_framework_activity_attached", false)
                                    .put("guest_application_registered_with_activity_thread", false)
                .put("guest_application_registered_with_package_manager", false)
                .put("full_guest_application_lifecycle_verified", false)
                .put("source_commit", sourceCommit)
                .put("probe_apk_sha256", probeApkSha256)
                .put("payload_apk_sha256", payloadApkSha256)
                .put("synthetic_component_dispatch_verified", syntheticComponentDispatchVerified)
                .put("dispatch_ticket_private_apk_sha256", dispatchTicketPrivateApkSha256)
                .put("production_clone_accepted", false)
                .put("third_party_component_executed", false)
                .put("real_guest_activity_registered", false)
                .put("activity_finishing", isFinishing())
                .put("activity_destroyed", destroyed)
                .put("preparation_stage", preparationStage)
                .put("preparation_finished", preparationFinished)
                .put("android_is_finishing", isFinishing())
                .put("android_is_changing_configurations", isChangingConfigurations())
                .put("android_task_id", getTaskId())
                .put("android_lifecycle_count", androidEvents.size())
                .put("configuration_change_callbacks", configurationChangeCallbacks)
                .put("actual_guest_activity_attached", false);
            saveReceipt(receipt);
        } catch (Throwable ignored) {
            // Parent runner treats a missing receipt as a hard failure, never PASS.
        }
        if (!isFinishing()) finish();
    }

    private void saveReceipt(JSONObject receipt) throws Exception {
        if (reported) return;
        if (reports == null || runId == null || !UUID.fromString(runId).toString().equals(runId))
            throw new IllegalStateException("No validated private E24 receipt location");
        ProbeData.report(new File(reports, runId + ".json"), receipt);
        reported = true;
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(Character.forDigit((b >>> 4) & 15, 16))
                                .append(Character.forDigit(b & 15, 16));
        return out.toString();
    }
}

package vn.fonesi.nativeprobe;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Process;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

/** Test-only framework factory for the non-exported E82 host Activity stub. */
public final class E82AppComponentFactory extends AppComponentFactory {
    public static final String EXTRA_RUN_ID = "e82_run_id";
    public static final String EXTRA_NONCE = "e82_nonce";
    public static final String EXTRA_SOURCE_COMMIT = "e82_source_commit";
    public static final String EXTRA_HOST_APK_SHA256 = "e82_host_apk_sha256";
    public static final String EXTRA_PAYLOAD_APK_SHA256 = "e82_payload_apk_sha256";
    public static final String EXTRA_CLUSTER_ID = "e82_private_cluster_id";
    public static final String EXTRA_ACTIVITY_CLASS = "e82_private_activity_class";
    public static final String EXTRA_AUTHORIZED = "e82_factory_authorized";
    public static final String EXTRA_AUTHORIZED_CLASS = "e82_factory_authorized_class";

    @Override
    public Activity instantiateActivity(ClassLoader classLoader, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        if (!E82FactoryAuthorization.STUB_CLASS.equals(className)) {
            return super.instantiateActivity(classLoader, className, intent);
        }
        if (Build.VERSION.SDK_INT < 28 || intent == null || intent.getComponent() == null) {
            throw denied("E82 requires API 28+ and an explicit host stub ComponentName");
        }

        ComponentName component = intent.getComponent();
        String processName = Application.getProcessName();
        E82FactoryAuthorization.Dispatch dispatch = new E82FactoryAuthorization.Dispatch(
                intent.getStringExtra(EXTRA_RUN_ID),
                intent.getStringExtra(EXTRA_NONCE),
                component.getPackageName(),
                className,
                component.flattenToString(),
                processName,
                Process.myUid(),
                classLoader,
                intent.getStringExtra(EXTRA_SOURCE_COMMIT),
                intent.getStringExtra(EXTRA_HOST_APK_SHA256),
                intent.getStringExtra(EXTRA_PAYLOAD_APK_SHA256));

        final E82FactoryAuthorization.Ticket ticket;
        try {
            ticket = E82FactoryAuthorization.consume(dispatch);
        } catch (SecurityException rejected) {
            throw denied("E82 host-stub dispatch was not authorized: " + rejected.getMessage());
        }

        Class<?> candidate = ticket.getPrivateActivityClass();
        if (candidate == null ||
                !E82FactoryAuthorization.PRIVATE_ACTIVITY_CLASS.equals(candidate.getName()) ||
                candidate.getClassLoader() != ticket.getPrivateClassLoader() ||
                !Activity.class.isAssignableFrom(candidate) ||
                !Modifier.isPublic(candidate.getModifiers()) ||
                Modifier.isAbstract(candidate.getModifiers())) {
            throw denied("E82 authorized class is not the exact public signed PRIVATE Activity");
        }

        try {
            Constructor<?> constructor = candidate.getConstructor();
            Activity activity = (Activity) constructor.newInstance();
            intent.putExtra(EXTRA_CLUSTER_ID, ticket.getClusterId());
            intent.putExtra(EXTRA_ACTIVITY_CLASS, ticket.getActivityClassName());
            intent.putExtra(EXTRA_AUTHORIZED, true);
            intent.putExtra(EXTRA_AUTHORIZED_CLASS, candidate.getName());
            return activity;
        } catch (NoSuchMethodException | InvocationTargetException error) {
            InstantiationException failure = denied("E82 signed PRIVATE Activity construction failed");
            failure.initCause(error);
            throw failure;
        }
    }

    private static InstantiationException denied(String message) {
        return new InstantiationException(message);
    }
}

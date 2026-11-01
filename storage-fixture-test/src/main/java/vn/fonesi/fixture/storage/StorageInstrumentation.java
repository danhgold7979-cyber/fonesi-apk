package vn.fonesi.fixture.storage;

import android.app.Instrumentation;
import android.os.Bundle;
import vn.fonesi.app.storage.StorageFixture;

/** Owned fixture only. No AndroidX, arbitrary arguments, activity or production target. */
public final class StorageInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result;
        try {
            if (!"vn.fonesi.fixture.storage.test".equals(getContext().getPackageName())
                    || !"vn.fonesi.fixture.storage".equals(getTargetContext().getPackageName())) {
                throw new IllegalStateException("Fixture identity mismatch");
            }
            result = StorageFixture.run(getTargetContext());
        } catch (Throwable failure) {
            result = new Bundle();
            result.putString("schema", "owned-storage-fixture-text-v2");
            result.putString("result", "FAIL");
            result.putString("failure", "RUNNER_FAILURE");
        }
        finish("PASS".equals(result.getString("result")) ? -1 : 0, result);
    }
}

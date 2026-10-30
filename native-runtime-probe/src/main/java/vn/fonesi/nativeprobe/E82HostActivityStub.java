package vn.fonesi.nativeprobe;

import android.app.Activity;
import android.os.Bundle;

/** Non-exported test-only component intercepted by E82AppComponentFactory. */
public final class E82HostActivityStub extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        throw new SecurityException("E82 host stub must be replaced by the authorized factory instance");
    }
}

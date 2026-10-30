package vn.fonesi.rebuild;

import android.app.Activity;
import android.os.Bundle;

/** Fresh presentation scaffold only. No provider, migration, or operation adapter. */
public final class ManagerActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.manager);
    }
}

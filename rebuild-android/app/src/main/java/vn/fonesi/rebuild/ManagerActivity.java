package vn.fonesi.rebuild;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import vn.fonesi.rebuild.model.ManagerModel;

/** Fresh presentation scaffold only. No provider, migration, or operation adapter. */
public final class ManagerActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.manager);
        // No storage/provider adapter is connected. Never seed synthetic clone rows.
        ManagerModel.Snapshot snapshot = new ManagerModel().snapshot();
        TextView summary = findViewById(R.id.metadata_summary);
        summary.setText(getString(R.string.metadata_unconnected, snapshot.totalCount));
    }
}

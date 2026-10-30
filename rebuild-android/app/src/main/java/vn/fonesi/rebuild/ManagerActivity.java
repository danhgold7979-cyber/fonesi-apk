package vn.fonesi.rebuild;

import android.app.Activity;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import vn.fonesi.rebuild.model.ManagerController;
import vn.fonesi.rebuild.model.ManagerModel;

/** Fresh metadata views only. No storage/provider adapter, operation dispatch or fake rows. */
public final class ManagerActivity extends Activity implements ManagerController.View {
    private final ManagerController controller = new ManagerController();
    private ManagerController.Render state;
    private boolean rendering;
    private EditText search;
    private EditText group;
    private CheckBox groupFilter;
    private LinearLayout rows;
    private TextView summary;
    private CheckBox selectVisible;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.manager);
        search = findViewById(R.id.metadata_search);
        group = findViewById(R.id.metadata_group);
        groupFilter = findViewById(R.id.metadata_group_filter);
        rows = findViewById(R.id.metadata_rows);
        summary = findViewById(R.id.metadata_summary);
        selectVisible = findViewById(R.id.metadata_select_visible);
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable text) { filterChanged(); }
        };
        search.addTextChangedListener(watcher);
        group.addTextChangedListener(watcher);
        groupFilter.setOnCheckedChangeListener((button, checked) -> filterChanged());
        selectVisible.setOnCheckedChangeListener((button, checked) -> {
            if (!rendering && state != null) controller.selectVisible(state.token, checked);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        controller.attach(this);
    }

    @Override
    protected void onPause() {
        controller.detach(); // hide rows and invalidate callbacks before leaving foreground
        super.onPause();
    }

    private void filterChanged() {
        if (rendering || state == null) return;
        controller.filter(state.token, search.getText().toString(),
                groupFilter.isChecked() ? group.getText().toString() : null);
    }

    @Override
    public void render(ManagerController.Render next) {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Render requires main thread");
        rendering = true;
        try {
            state = next;
            setTextIfDifferent(search, next.query);
            groupFilter.setChecked(next.group != null);
            setTextIfDifferent(group, next.group == null ? "" : next.group);
            search.setEnabled(next.active);
            groupFilter.setEnabled(next.active);
            group.setEnabled(next.active && next.group != null);
            selectVisible.setEnabled(next.active && next.phase == ManagerModel.Phase.LOADED && !next.visible.isEmpty());
            selectVisible.setChecked(!next.visible.isEmpty() && next.selected.size() == next.visible.size());
            summary.setText(summary(next));
            rows.removeAllViews();
            for (ManagerModel.Entry entry : next.visible) {
                CheckBox row = new CheckBox(this);
                row.setSaveEnabled(false);
                row.setText(getString(R.string.metadata_row, entry.name, entry.group, status(entry.status)));
                row.setChecked(next.selected.contains(entry.key));
                // Captured token binds a row event to the exact view revision and foreground epoch.
                row.setOnCheckedChangeListener((button, checked) -> controller.select(next.token, entry.key, checked));
                rows.addView(row);
            }
        } finally {
            rendering = false;
        }
    }

    private String summary(ManagerController.Render next) {
        if (!next.active) return getString(R.string.metadata_hidden);
        switch (next.phase) {
            case IDLE: return getResources().getQuantityString(
                    R.plurals.metadata_unconnected, next.totalCount, next.totalCount);
            case LOADING: return getString(R.string.metadata_loading);
            case FAILED: return getString(R.string.metadata_failed);
            case LOADED:
                if (next.totalCount == 0) return getString(R.string.metadata_empty);
                return getString(R.string.metadata_counts, next.visible.size(), next.totalCount, next.selected.size());
            default: throw new IllegalStateException("Unknown presentation phase");
        }
    }

    private String status(ManagerModel.Status value) {
        switch (value) {
            case QUARANTINED: return getString(R.string.metadata_quarantined);
            case UNVERIFIED: return getString(R.string.metadata_unverified);
            case UNAVAILABLE: return getString(R.string.metadata_unavailable);
            default: throw new IllegalStateException("Unknown metadata status");
        }
    }

    private static void setTextIfDifferent(EditText view, String text) {
        if (!view.getText().toString().equals(text)) view.setText(text);
    }
}

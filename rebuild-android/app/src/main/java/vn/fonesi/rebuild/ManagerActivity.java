package vn.fonesi.rebuild;

import android.app.Activity;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.widget.CheckBox;
import android.widget.Button;
import vn.fonesi.rebuild.catalog.CatalogLoader;
import vn.fonesi.rebuild.catalog.CatalogPage;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import vn.fonesi.rebuild.model.ManagerController;
import vn.fonesi.rebuild.model.ManagerModel;

/** Owned catalog metadata only; no legacy data, provider dispatch or seeded rows. */
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
    private Button firstPage;
    private Button nextPage;
    private CatalogLoader loader;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.manager);
        search = findViewById(R.id.metadata_search);
        group = findViewById(R.id.metadata_group);
        search.setFilters(new InputFilter[] {new InputFilter.LengthFilter(4096)});
        group.setFilters(new InputFilter[] {new InputFilter.LengthFilter(4096)});
        groupFilter = findViewById(R.id.metadata_group_filter);
        rows = findViewById(R.id.metadata_rows);
        summary = findViewById(R.id.metadata_summary);
        selectVisible = findViewById(R.id.metadata_select_visible);
        firstPage = findViewById(R.id.metadata_first);
        nextPage = findViewById(R.id.metadata_next);
        loader = CatalogLoader.get(getApplicationContext());
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
        loadPage(false, state.token);
    }

    @Override
    protected void onPause() {
        loader.cancel(controller);
        controller.detach(); // hide rows and invalidate callbacks before leaving foreground
        super.onPause();
    }

    private void loadPage(boolean advance, ManagerController.Token token) {
        if (rendering || state == null || !state.active || state.phase == ManagerModel.Phase.LOADING) return;
        CatalogPage.Position position = advance ? loader.nextPosition(controller) : null;
        if (advance && position == null) return;
        ManagerController.RefreshTicket ticket = controller.beginRefresh(token);
        if (ticket != null) loader.request(controller, ticket, position);
    }

    private void filterChanged() {
        if (rendering || state == null) return;
        try {
            controller.filter(state.token, search.getText().toString(),
                    groupFilter.isChecked() ? group.getText().toString() : null);
        } catch (IllegalArgumentException overBudget) {
            render(controller.current());
            summary.setText(R.string.metadata_filter_budget);
        }
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
            firstPage.setOnClickListener(button -> loadPage(false, next.token));
            nextPage.setOnClickListener(button -> loadPage(true, next.token));
            firstPage.setEnabled(next.active && next.phase != ManagerModel.Phase.LOADING);
            nextPage.setEnabled(next.active && next.phase == ManagerModel.Phase.LOADED
                    && loader.nextPosition(controller) != null);
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
                return getString(R.string.metadata_page_summary,
                        getString(R.string.metadata_counts, next.visible.size(), next.totalCount, next.selected.size()),
                        getString(loader.nextPosition(controller) == null ? R.string.metadata_page_end : R.string.metadata_page_more));
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

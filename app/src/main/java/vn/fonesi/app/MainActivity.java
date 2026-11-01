package vn.fonesi.app;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;

/** Fresh stateless shell. No provider is installed or invoked by this activity. */
public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        View content = findViewById(R.id.shell_content);
        int spacing = getResources().getDimensionPixelSize(R.dimen.screen_spacing);
        content.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets safe = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(spacing + safe.left, spacing + safe.top,
                        spacing + safe.right, spacing + safe.bottom);
            } else {
                applyLegacyInsets(view, windowInsets, spacing);
            }
            return windowInsets;
        });
        content.requestApplyInsets();
    }

    @SuppressWarnings("deprecation")
    private static void applyLegacyInsets(View view, WindowInsets insets, int spacing) {
        view.setPadding(spacing + insets.getSystemWindowInsetLeft(),
                spacing + insets.getSystemWindowInsetTop(),
                spacing + insets.getSystemWindowInsetRight(),
                spacing + insets.getSystemWindowInsetBottom());
    }
}

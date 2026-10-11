package org.inaturalist.android;

import android.app.Activity;
import android.app.Application;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * Starting with Android 16 (API 36), apps targeting SDK 36 can no longer opt out of edge-to-edge
 * (windowOptOutEdgeToEdgeEnforcement is ignored), so the status/navigation bars are drawn
 * on top of the app content. This restores the pre-edge-to-edge layout for all of our activities,
 * by padding the window's root view with the system bar insets (and the keyboard, for adjustResize),
 * and drawing a colored background behind the (now transparent) status bar.
 */
@RequiresApi(api = Build.VERSION_CODES.BAKLAVA)
public class EdgeToEdgeInsetsHandler implements Application.ActivityLifecycleCallbacks {

    public static void register(Application application) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return;
        application.registerActivityLifecycleCallbacks(new EdgeToEdgeInsetsHandler());
    }

    @Override
    public void onActivityPostCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        // Leave third-party activities (e.g. sign in screens) alone
        if (!activity.getClass().getName().startsWith("org.inaturalist.")) return;

        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        View root = decorView.getChildAt(0);
        View content = activity.findViewById(android.R.id.content);
        if ((root == null) || (content == null)) return;

        // The view to pad is the window's original root (that contains the action bar) - which might
        // have been moved by BaseFragmentActivity.moveDrawerToTop into a DrawerLayout (that ignores padding)
        View paddedView = content;
        while ((paddedView.getParent() instanceof View) && (paddedView.getParent() != decorView) &&
                (((View) paddedView.getParent()).getId() != R.id.drawer_content)) {
            paddedView = (View) paddedView.getParent();
        }
        final View target = paddedView;

        View statusBarBackground = new View(activity);
        statusBarBackground.setBackgroundColor(getStatusBarColor(activity));
        decorView.addView(statusBarBackground, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP));

        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());

            int bottom = bars.bottom;
            int adjust = activity.getWindow().getAttributes().softInputMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST;
            if ((adjust != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN) && (adjust != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)) {
                // Edge-to-edge windows aren't resized by the system when the keyboard is shown
                bottom = Math.max(bottom, windowInsets.getInsets(WindowInsets.Type.ime()).bottom);
            }

            // Activities that asked to be laid out underneath the status bar (e.g. onboarding)
            boolean drawsUnderStatusBar = (decorView.getSystemUiVisibility() & View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) != 0;
            int top = drawsUnderStatusBar ? 0 : bars.top;

            target.setPadding(bars.left, top, bars.right, bottom);

            ViewGroup.LayoutParams params = statusBarBackground.getLayoutParams();
            if (params.height != top) {
                params.height = top;
                statusBarBackground.setLayoutParams(params);
            }

            return WindowInsets.CONSUMED;
        });
        root.requestApplyInsets();
    }

    private static int getStatusBarColor(Activity activity) {
        TypedArray a = activity.obtainStyledAttributes(new int[] { android.R.attr.statusBarColor });
        int color = a.getColor(0, Color.BLACK);
        a.recycle();
        // Edge-to-edge forces the theme's status bar color to be transparent
        return Color.alpha(color) == 0 ? Color.BLACK : color;
    }

    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) { }
    @Override public void onActivityStarted(@NonNull Activity activity) { }
    @Override public void onActivityResumed(@NonNull Activity activity) { }
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }
    @Override public void onActivityDestroyed(@NonNull Activity activity) { }
}

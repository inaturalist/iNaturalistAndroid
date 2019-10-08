package org.inaturalist.android;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.StyleRes;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.BottomSheetDialog;
import android.view.View;

public class ExpandedBottomSheetDialog extends BottomSheetDialog {

    public ExpandedBottomSheetDialog(@NonNull Context context) {
        super(context);
    }

    protected ExpandedBottomSheetDialog(@NonNull Context context, boolean cancelable, OnCancelListener cancelListener) {
        super(context, cancelable, cancelListener);
    }

    public ExpandedBottomSheetDialog(@NonNull Context context, @StyleRes int theme) {
        super(context, theme);
    }

    @Override
    public void show() {
        super.show();
        final View view = findViewById(R.id.design_bottom_sheet);
        view.post(new Runnable() {
            @Override
            public void run() {
                BottomSheetBehavior behavior = BottomSheetBehavior.from(view);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

            }
        });
    }
}

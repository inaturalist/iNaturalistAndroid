package org.inaturalist.android;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
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

package org.inaturalist.android;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tables.TableTheme;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import me.saket.bettermovementmethod.BetterLinkMovementMethod;

public class HtmlUtils {
    private static final String TAG = "HtmlUtils";

    /** Formats HTML string onto the specified text view */
    public static void fromHtml(TextView textView, String html) {
        Context context = textView.getContext();
        ActivityHelper helper = new ActivityHelper(context);

        TableTheme tableTheme = new TableTheme.Builder()
                .tableBorderWidth(3)
                .tableCellPadding((int)helper.dpToPx(6))
                .tableOddRowBackgroundColor(Color.parseColor("#00ffffff"))
                .build();


        final Markwon markwon = Markwon.builder(context)
                .usePlugin(TablePlugin.create(tableTheme))
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(HtmlPlugin.create())
                .usePlugin(StrikethroughPlugin.create())
                .build();

        // Replace new lines (\n) with <br> tags
        html = html.replaceAll("\r", "");
        html = html.replaceAll("\n", "  \n");

        Spanned htmlText = markwon.toMarkdown(html);

        URLSpan[] currentSpans = htmlText.getSpans(0, htmlText.length(), URLSpan.class);

        SpannableString buffer = new SpannableString(htmlText);

        // Turn @username mentions into clickable links
        Linkify.TransformFilter filter = new Linkify.TransformFilter() {
            public final String transformUrl(final Matcher match, String url) {
                return match.group();
            }
        };

        Pattern mentionPattern = Pattern.compile("@([A-Za-z0-9_-]+)");
        Linkify.addLinks(buffer, mentionPattern, null, null, filter);

        for (URLSpan span : currentSpans) {
            int end = htmlText.getSpanEnd(span);
            int start = htmlText.getSpanStart(span);
            buffer.setSpan(span, start, end, 0);
        }

        Spanned finalHtmlText = buffer;

        BetterLinkMovementMethod linker = BetterLinkMovementMethod.newInstance();
        textView.setMovementMethod(linker);
        linker.setOnLinkClickListener((tv, url) -> {
            if (url.startsWith("@")) {
                // Username mention - show user profile screen
                try {
                    JSONObject item = new JSONObject();
                    item.put("login", url.substring(1));
                    Intent intent = new Intent(context, UserProfile.class);
                    intent.putExtra("user", new BetterJSONObject(item));
                    context.startActivity(intent);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                return true;

            } else {
                // Regular URL - use default action (e.g. open browser)
                return false;
            }
        });

        textView.setLinksClickable(true);
        // Leaving this commented out in case it was doing something useful, but
        // it was also making it impossible to select text
        // textView.setFocusable(false);
        markwon.setParsedMarkdown(textView, finalHtmlText);
    }

    /** Used for displaying <img> tags */
    private static class PicassoImageGetter implements Html.ImageGetter {
        private TextView mTextView;
        private Picasso mPicasso;

        public PicassoImageGetter(@NonNull Picasso picasso, @NonNull TextView textView) {
            mPicasso = picasso;
            mTextView = textView;
        }

        @Override
        public Drawable getDrawable(String source) {
            Logger.tag(TAG).debug("Start loading url " + source);

            BitmapDrawablePlaceHolder drawable = new BitmapDrawablePlaceHolder(mTextView.getContext());

            mPicasso
                    .load(source)
                    .error(R.drawable.ic_error_black_24dp)
                    .into(drawable);

            return drawable;
        }

        private class BitmapDrawablePlaceHolder extends BitmapDrawable implements Target {

            protected Drawable mDrawable;
            private Context mContext;

            public BitmapDrawablePlaceHolder(Context context) {
                mContext = context;
            }

            @Override
            public void draw(final Canvas canvas) {
                if (mDrawable != null) {
                    checkBounds();
                    mDrawable.draw(canvas);
                }
            }

            public void setDrawable(@Nullable Drawable drawable) {
                if (drawable != null) {
                    mDrawable = drawable;
                    checkBounds();
                }
            }

            private void checkBounds() {
                float defaultProportion = (float) mDrawable.getIntrinsicWidth() / (float) mDrawable.getIntrinsicHeight();
                int width = Math.min(mTextView.getWidth(), mDrawable.getIntrinsicWidth());
                int height = (int) ((float) width / defaultProportion);

                if (getBounds().right != mTextView.getWidth() || getBounds().bottom != height) {

                    setBounds(0, 0, mTextView.getWidth(), height); //set to full width

                    int halfOfPlaceHolderWidth = (int) ((float) getBounds().right / 2f);
                    int halfOfImageWidth = (int) ((float) width / 2f);

                    mDrawable.setBounds(
                            halfOfPlaceHolderWidth - halfOfImageWidth, //centering an image
                            0,
                            halfOfPlaceHolderWidth + halfOfImageWidth,
                            height);

                    mTextView.setText(mTextView.getText()); //refresh text
                }
            }

            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                setDrawable(new BitmapDrawable(mContext.getResources(), bitmap));
            }

            @Override
            public void onBitmapFailed(Drawable errorDrawable) {
                setDrawable(errorDrawable);
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
                setDrawable(placeHolderDrawable);
            }

        }
    }
}

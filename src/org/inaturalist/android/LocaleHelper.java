package org.inaturalist.android;

import java.util.Locale;

/**
 * Created by ulrikeaxen on 1/4/15.
 */
public class LocaleHelper {

    private static final String DefaultLocale = "en";

    // add locale codes here when they are complete in values
    public static final String[] SupportedLocales = new String[] {
            "", // Use device locale
            "eu",
            "gl"
    };


    public static String getDefaultLocale()
    {
        String languageCode = Locale.getDefault().getLanguage();
        for (int i = 0; i < SupportedLocales.length; i++)
            if (languageCode.equalsIgnoreCase(SupportedLocales[i]))
                return SupportedLocales[i];
        return DefaultLocale;
    }
}

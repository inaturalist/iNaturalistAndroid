package org.inaturalist.android;

import android.app.NotificationManager;
import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/** Various app-wide taxon-related utility functions */
public class TaxonUtils {
    public static String getTaxonName(Context context, JSONObject item) {
        JSONObject defaultName;
        String displayName = null;

        // Get the taxon display name according to device locale
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Locale deviceLocale = context.getResources().getConfiguration().locale;
        String deviceLexicon =   deviceLocale.getLanguage();

        try {
            JSONArray taxonNames = item.getJSONArray("taxon_names");
            for (int i = 0; i < taxonNames.length(); i++) {
                JSONObject taxonName = taxonNames.getJSONObject(i);
                String lexicon = taxonName.getString("lexicon");
                if (lexicon.equals(deviceLexicon)) {
                    // Found the appropriate lexicon for the taxon
                    displayName = taxonName.getString("name");
                    break;
                }
            }
        } catch (JSONException e) {
        }

        if (displayName == null) {
            // Couldn't extract the display name from the taxon names list - use the default one
            try {
                defaultName = item.getJSONObject("default_name");
                displayName = defaultName.getString("name");
            } catch (JSONException e1) {
                // alas
                JSONObject commonName = item.optJSONObject("common_name");
                if (commonName != null) {
                    displayName = commonName.optString("name");
                } else {
                    displayName = item.optString("preferred_common_name");
                    if ((displayName == null) || (displayName.length() == 0)) {
                        displayName = item.optString("english_common_name");
                        if ((displayName == null) || (displayName.length() == 0)) {
                            displayName = item.optString("name");
                        }
                    }
                }
            }
        }

        return displayName;

    }

    public static int observationIcon(JSONObject o) {
 		if (o == null) return R.drawable.ic_taxa_unknown;
 		String iconicTaxonName = null;

        if (o.has("iconic_taxon_name") && !o.isNull("iconic_taxon_name")) {
            try {
                iconicTaxonName = o.getString("iconic_taxon_name");
            } catch (JSONException e) {
                e.printStackTrace();
                return R.drawable.ic_taxa_unknown;
            }
        } else if (o.has("taxon")) {
            try {
                iconicTaxonName = o.getJSONObject("taxon").optString("iconic_taxon_name");
            } catch (JSONException e) {
                e.printStackTrace();
                return R.drawable.ic_taxa_unknown;
            }
        }

 		if (iconicTaxonName == null) {
 			return R.drawable.ic_taxa_unknown;
 		} else if (iconicTaxonName.equals("Animalia")) {
 			return R.drawable.animalia_large;
 		} else if (iconicTaxonName.equals("Plantae")) {
 			return R.drawable.plantae_large;
 		} else if (iconicTaxonName.equals("Chromista")) {
 			return R.drawable.chromista_large;
 		} else if (iconicTaxonName.equals("Fungi")) {
 			return R.drawable.fungi_large;
 		} else if (iconicTaxonName.equals("Protozoa")) {
 			return R.drawable.protozoa_large;
 		} else if (iconicTaxonName.equals("Actinopterygii")) {
 			return R.drawable.actinopterygii_large;
 		} else if (iconicTaxonName.equals("Amphibia")) {
 			return R.drawable.amphibia_large;
 		} else if (iconicTaxonName.equals("Reptilia")) {
 			return R.drawable.reptilia_large;
 		} else if (iconicTaxonName.equals("Aves")) {
 			return R.drawable.aves_large;
 		} else if (iconicTaxonName.equals("Mammalia")) {
 			return R.drawable.mammalia_large;
 		} else if (iconicTaxonName.equals("Mollusca")) {
 			return R.drawable.mollusca_large;
 		} else if (iconicTaxonName.equals("Insecta")) {
 			return R.drawable.insecta_large;
 		} else if (iconicTaxonName.equals("Arachnida")) {
 			return R.drawable.arachnida_large;
 		} else {
 			return R.drawable.ic_taxa_unknown;
 		}


 	}
}

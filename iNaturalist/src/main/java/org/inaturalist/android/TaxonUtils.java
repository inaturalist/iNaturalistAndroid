package org.inaturalist.android;

import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Html;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Various app-wide taxon-related utility functions */
public class TaxonUtils {
    private static final Map<String, Float> RANK_NAME_TO_LEVEL;

    static {
        Map<String, Float> rankNameToLevel = new HashMap<>();

        rankNameToLevel.put("root", 100f);
        rankNameToLevel.put("kingdom", 70f);
        rankNameToLevel.put("subkingdom", 67f);
        rankNameToLevel.put("phylum", 60f);
        rankNameToLevel.put("subphylum", 57f);
        rankNameToLevel.put("superclass", 53f);
        rankNameToLevel.put("class", 50f);
        rankNameToLevel.put("subclass", 47f);
        rankNameToLevel.put("infraclass", 45f);
        rankNameToLevel.put("superorder", 43f);
        rankNameToLevel.put("order", 40f);
        rankNameToLevel.put("suborder", 37f);
        rankNameToLevel.put("infraorder", 35f);
        rankNameToLevel.put("parvorder", 34.5f);
        rankNameToLevel.put("zoosection", 34f);
        rankNameToLevel.put("zoosubsection", 33.5f);
        rankNameToLevel.put("superfamily", 33f);
        rankNameToLevel.put("epifamily", 32f);
        rankNameToLevel.put("family", 30f);
        rankNameToLevel.put("subfamily", 27f);
        rankNameToLevel.put("supertribe", 26f);
        rankNameToLevel.put("tribe", 25f);
        rankNameToLevel.put("subtribe", 24f);
        rankNameToLevel.put("genus", 20f);
        rankNameToLevel.put("genushybrid", 20f);
        rankNameToLevel.put("subgenus", 15f);
        rankNameToLevel.put("section", 13f);
        rankNameToLevel.put("subsection", 12f);
        rankNameToLevel.put("species", 10f);
        rankNameToLevel.put("hybrid", 10f);
        rankNameToLevel.put("subspecies", 5f);
        rankNameToLevel.put("variety", 5f);
        rankNameToLevel.put("form", 5f);
        rankNameToLevel.put("infrahybrid", 5f);

        RANK_NAME_TO_LEVEL = Collections.unmodifiableMap(rankNameToLevel);
    }

    public static void setTaxonScientificName(TextView textView, String taxonName, int rankLevel, String rank) {
        JSONObject taxon = new JSONObject();
        try {
            taxon.put("rank", rank);
            taxon.put("rank_level", rankLevel);
            taxon.put("name", taxonName);

            setTaxonScientificName(textView, taxon);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void setTaxonScientificName(TextView textView, JSONObject item) {
        setTaxonScientificName(textView, item, false);
    }

    public static void setTaxonScientificName(TextView textView, JSONObject item, boolean bold) {
        textView.setText(Html.fromHtml(getTaxonScientificNameHtml(item, bold)));
    }

    public static String getTaxonScientificNameHtml(JSONObject item, boolean bold) {
        String name = item.optString("name", "");
        String rank = getTaxonRank(item);
        if (getTaxonRankLevel(item) <= 20) {
            name = (rank.equals("") ? "" : (rank + " ")) + "<i>" + name + "</i>";
        }

        return bold ? "<b>" + name + "</b>" : name;
    }

    public static double getTaxonRankLevel(JSONObject item) {
        if (item.has("rank_level")) return item.optDouble("rank_level");

        // Try to deduce rank level from rank
        if (item.has("rank")) {
            if (RANK_NAME_TO_LEVEL.containsKey(item.optString("rank"))) {
                return RANK_NAME_TO_LEVEL.get(item.optString("rank").toLowerCase());
            } else {
                return 0;
            }
        } else {
            return 0;
        }
    }


    public static String getTaxonRank(JSONObject item) {
        double rankLevel = getTaxonRankLevel(item);

        if (rankLevel < 15) {
            // Lower than subgenus - don't return anything
            return "";
        } else {
            return StringUtils.capitalize(item.optString("rank", ""));
        }
    }

    public static String getTaxonScientificName(JSONObject item) {
        String rank = getTaxonRank(item);
        String scientificName = item.optString("name", "");

        if (rank.equals("")) {
            return scientificName;
        } else {
            return String.format("%s %s", rank, scientificName);
        }
    }

    public static String getTaxonName(Context context, JSONObject item) {
        JSONObject defaultName;
        String displayName = null;

        // Get the taxon display name according to device locale
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

        if (displayName == null) {
            displayName = context.getResources().getString(R.string.unknown);
        }

        return displayName;

    }


    public static int taxonicIconNameToResource(String iconicTaxonName) {
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
        } else if (o.has("taxon") && !o.isNull("taxon")) {
            try {
                iconicTaxonName = o.getJSONObject("taxon").optString("iconic_taxon_name");
            } catch (JSONException e) {
                e.printStackTrace();
                return R.drawable.ic_taxa_unknown;
            }
        }

        return taxonicIconNameToResource(iconicTaxonName);
 	}

 	public static BitmapDescriptor observationMarkerIcon(String iconic_taxon_name) {
        if (iconic_taxon_name == null) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_unknown);
        }

        if (iconic_taxon_name.equals("Animalia") ||
                iconic_taxon_name.equals("Actinopterygii") ||
                iconic_taxon_name.equals("Amphibia") ||
                iconic_taxon_name.equals("Reptilia") ||
                iconic_taxon_name.equals("Aves") ||
                iconic_taxon_name.equals("Mammalia")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_dodger_blue);
        } else if (iconic_taxon_name.equals("Insecta") ||
                iconic_taxon_name.equals("Arachnida") ||
                iconic_taxon_name.equals("Mollusca")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_orange_red);
        } else if (iconic_taxon_name.equals("Protozoa")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_dark_magenta);
        } else if (iconic_taxon_name.equals("Plantae")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_inat_green);
        } else if (iconic_taxon_name.equals("Fungi")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_hot_pink);
        } else if (iconic_taxon_name.equals("Chromista")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_chromista_brown);
        } else {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_unknown);
        }

    }

}

package org.inaturalist.android;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

public class ObservationUtils {
    private static final String TAG = "ObservationUtils";

    public static BetterJSONObject getMinimalIdentificationResults(BetterJSONObject results) {
        if (results == null) return null;

        SerializableJSONArray innerResults = results.getJSONArray("results");
        if (innerResults == null) return null;

        JSONArray identificationResults = innerResults.getJSONArray();

        if (identificationResults != null) {
            JSONArray minimizedResults = new JSONArray();

            for (int i = 0; i < identificationResults.length(); i++) {
                JSONObject item = identificationResults.optJSONObject(i);
                minimizedResults.put(getMinimalIdentification(item));
            }

            results.put("results", minimizedResults);
        }

        return results;
    }

    public static BetterJSONObject getMinimalObserverResults(BetterJSONObject results) {
        if (results == null) return null;

        SerializableJSONArray innerResults = results.getJSONArray("results");
        if (innerResults == null) return null;

        JSONArray observerResults = innerResults.getJSONArray();

        if (observerResults != null) {
            JSONArray minimizedResults = new JSONArray();

            for (int i = 0; i < observerResults.length(); i++) {
                JSONObject item = observerResults.optJSONObject(i);
                minimizedResults.put(getMinimalObserver(item));
            }

            results.put("results", minimizedResults);
        }

        return results;
    }


    public static BetterJSONObject getMinimalSpeciesResults(BetterJSONObject results) {
        if (results == null) return null;

        SerializableJSONArray innerResults = results.getJSONArray("results");
        if (innerResults == null) return null;

        // Minimize results - save only basic info for each observation (better memory usage)
        JSONArray speciesResults = innerResults.getJSONArray();
        JSONArray minimizedResults = new JSONArray();

        if (speciesResults != null) {
            for (int i = 0; i < speciesResults.length(); i++) {
                JSONObject item = speciesResults.optJSONObject(i);
                minimizedResults.put(getMinimalSpecies(item));
            }

            results.put("results", minimizedResults);
        }

        return results;
    }

    public static BetterJSONObject getMinimalObservationResults(BetterJSONObject results) {
        if (results == null) return null;

        SerializableJSONArray innerResults = results.getJSONArray("results");
        if (innerResults == null) return null;

        // Minimize results - save only basic info for each observation (better memory usage)
        JSONArray observationResults = innerResults.getJSONArray();
        JSONArray minimizedObservations = new JSONArray();

        if (observationResults != null) {
            for (int i = 0; i < observationResults.length(); i++) {
                JSONObject item = observationResults.optJSONObject(i);
                minimizedObservations.put(getMinimalObservation(item));
            }

            results.put("results", minimizedObservations);
        }

        return results;
    }

    // Returns a minimal version of an observation JSON (used to lower memory usage)
    public static JSONObject getMinimalObservation(JSONObject observation) {
        JSONObject minimaldObs = new JSONObject();

        try {
            minimaldObs.put("id", observation.optInt("id"));
            minimaldObs.put("quality_grade", observation.optString("quality_grade"));
            if (observation.has("observed_on") && !observation.isNull("observed_on")) minimaldObs.put("observed_on", observation.optString("observed_on"));
            if (observation.has("time_observed_at") && !observation.isNull("time_observed_at")) minimaldObs.put("time_observed_at", observation.optString("time_observed_at"));
            if (observation.has("species_guess") && !observation.isNull("species_guess")) minimaldObs.put("species_guess", observation.optString("species_guess"));
            if (observation.has("place_guess") && !observation.isNull("place_guess")) minimaldObs.put("place_guess", observation.optString("place_guess"));
            if (observation.has("latitude") && !observation.isNull("latitude")) minimaldObs.put("latitude", observation.optString("latitude"));
            if (observation.has("longitude") && !observation.isNull("longitude")) minimaldObs.put("longitude", observation.optString("longitude"));
            if (observation.has("observed_on") && !observation.isNull("observed_on")) minimaldObs.put("observed_on", observation.optString("observed_on"));
            if (observation.has("comments_count") && !observation.isNull("comments_count")) minimaldObs.put("comments_count", observation.optInt("comments_count"));
            if (observation.has("identifications_count") && !observation.isNull("identifications_count")) minimaldObs.put("identifications_count", observation.optInt("identifications_count"));

            minimaldObs.put("taxon", getMinimalTaxon(observation.optJSONObject("taxon")));
            if (observation.has("iconic_taxon_name")) minimaldObs.put("iconic_taxon_name", observation.optString("iconic_taxon_name"));

            if (observation.has("observation_photos") && !observation.isNull("observation_photos")) {
                JSONArray minimalObsPhotos = new JSONArray();
                JSONArray obsPhotos = observation.optJSONArray("observation_photos");
                for (int i = 0; i < obsPhotos.length(); i++) {
                    minimalObsPhotos.put(getMinimalPhoto(obsPhotos.optJSONObject(i)));
                }

                minimaldObs.put("observation_photos", minimalObsPhotos);
            }


            if (observation.has("sounds") && !observation.isNull("sounds")) {
                JSONArray minimalObsSounds = new JSONArray();
                JSONArray obsSounds = observation.optJSONArray("sounds");
                for (int i = 0; i < obsSounds.length(); i++) {
                    minimalObsSounds.put(getMinimalSound(obsSounds.optJSONObject(i)));
                }

                minimaldObs.put("sounds", minimalObsSounds);
            }

            if (observation.has("user")) {
                JSONObject user = observation.optJSONObject("user");
                minimaldObs.put("user", getMinimalUser(user));
            }
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }

        return minimaldObs;
    }


    // Returns a minimal version of an identification JSON (used to lower memory usage)
    public static JSONObject getMinimalIdentification(JSONObject identification) {
        JSONObject minimalObserver = new JSONObject();

        if (identification == null) return null;

        try {
            if (identification.has("observation")) minimalObserver.put("observation", getMinimalObservation(identification.optJSONObject("observation")));
            if (identification.has("taxon")) minimalObserver.put("taxon", getMinimalTaxon(identification.optJSONObject("taxon")));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }

        return minimalObserver;
    }

    // Returns a minimal version of an observer JSON (used to lower memory usage)
    public static JSONObject getMinimalObserver(JSONObject observer) {
        JSONObject minimalObserver = new JSONObject();

        if (observer == null) return null;

        try {
            if (observer.has("observation_count")) minimalObserver.put("observation_count", observer.optInt("observation_count"));
            if (observer.has("count")) minimalObserver.put("count", observer.optInt("count"));

            if (observer.has("user")) {
                JSONObject user = observer.optJSONObject("user");
                minimalObserver.put("user", getMinimalUser(user));
            }
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }

        return minimalObserver;
    }

    // Returns a minimal version of a user JSON (used to lower memory usage)
    public static JSONObject getMinimalUser(JSONObject user) {
        JSONObject minimalUser = new JSONObject();

        if (user == null) return null;

        try {
            minimalUser.put("login", user.optString("login"));
            minimalUser.put("icon_url", user.optString("icon_url"));

            if (user.has("observations_count")) minimalUser.put("observations_count", user.optInt("observations_count"));
            if (user.has("identifications_count")) minimalUser.put("identifications_count", user.optInt("identifications_count"));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }

        return minimalUser;
    }


    // Returns a minimal version of a species JSON (used to lower memory usage)
    public static JSONObject getMinimalSpecies(JSONObject species) {
        JSONObject minimalSpecies = new JSONObject();

        if (species == null) return null;

        try {
            minimalSpecies.put("count", species.optInt("count"));
            minimalSpecies.put("taxon", getMinimalTaxon(species.optJSONObject("taxon")));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }

        return minimalSpecies;
    }

    // Returns a minimal version of a sound JSON (used to lower memory usage)
    public static JSONObject getMinimalSound(JSONObject sound) {
        JSONObject minimalSound = new JSONObject();

        if (sound == null) return null;

        try {
            minimalSound.put("id", sound.optInt("id"));
            minimalSound.put("file_url", sound.optString("file_url"));
            minimalSound.put("file_content_type", sound.optString("file_content_type"));
            minimalSound.put("attribution", sound.optString("attribution"));
            minimalSound.put("subtype", sound.optString("subtype"));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }

        return minimalSound;
    }

    // Returns a minimal version of a photo JSON (used to lower memory usage)
    public static JSONObject getMinimalPhoto(JSONObject photo) {
        JSONObject minimalPhoto = new JSONObject();

        if (photo == null) return null;

        try {
            minimalPhoto.put("id", photo.optInt("id"));
            minimalPhoto.put("position", photo.optInt("position"));

            if (photo.has("photo") && !photo.isNull("photo")) {
                JSONObject innerPhoto = new JSONObject();
                innerPhoto.put("id", photo.optJSONObject("photo").optInt("id"));
                innerPhoto.put("url", photo.optJSONObject("photo").optString("url"));

                minimalPhoto.put("photo", innerPhoto);
            }
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }

        return minimalPhoto;
    }


    // Returns a minimal version of a taxon JSON (used to lower memory usage)
    public static JSONObject getMinimalTaxon(JSONObject taxon) {
        JSONObject minimalTaxon = new JSONObject();

        if (taxon == null) return null;

        try {
            minimalTaxon.put("id", taxon.optInt("id"));
            minimalTaxon.put("name", taxon.optString("name"));
            minimalTaxon.put("rank", taxon.optString("rank"));
            minimalTaxon.put("rank_level", taxon.optInt("rank_level"));
            minimalTaxon.put("iconic_taxon_name", taxon.optString("iconic_taxon_name"));
            if (taxon.has("taxon_names")) minimalTaxon.put("taxon_names", taxon.optJSONArray("taxon_names"));
            if (taxon.has("default_name")) minimalTaxon.put("default_name", taxon.optJSONObject("default_name"));
            if (taxon.has("common_name")) minimalTaxon.put("common_name", taxon.optJSONObject("common_name"));
            if (taxon.has("preferred_common_name")) minimalTaxon.put("preferred_common_name", taxon.optString("preferred_common_name"));
            if (taxon.has("english_common_name")) minimalTaxon.put("english_common_name", taxon.optString("english_common_name"));
            if (taxon.has("observations_count")) minimalTaxon.put("observations_count", taxon.optInt("observations_count"));
            if (taxon.has("default_photo") && !taxon.isNull("default_photo")) {
                JSONObject minimalPhoto = new JSONObject();
                JSONObject defaultPhoto = taxon.optJSONObject("default_photo");

                if (defaultPhoto.has("medium_url") && !defaultPhoto.isNull("medium_url")) {
                    minimalPhoto.put("medium_url", defaultPhoto.optString("medium_url"));
                } else {
                    minimalPhoto.put("photo_url", defaultPhoto.optString("photo_url"));
                }

                minimalTaxon.put("default_photo", minimalPhoto);
            }
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }

        return minimalTaxon;
    }

}

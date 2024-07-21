package org.inaturalist.android;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

public class ObservationUtils {
    private static final String TAG = "ObservationUtils";

    public static BetterJSONObject getMinimalIdentificationResults(BetterJSONObject results, String username) {
        if (results == null) return null;

        SerializableJSONArray innerResults = results.getJSONArray("results");
        if (innerResults == null) return null;

        JSONArray identificationResults = innerResults.getJSONArray();

        if (identificationResults != null) {
            JSONArray minimizedResults = new JSONArray();

            for (int i = 0; i < identificationResults.length(); i++) {
                JSONObject item = identificationResults.optJSONObject(i);
                minimizedResults.put(getMinimalIdentification(item, username));
            }

            results.put("results", minimizedResults);
        }

        return results;
    }

    public static BetterJSONObject getMinimalMessagesResults(BetterJSONObject results) {
        if (results == null) return null;

        SerializableJSONArray innerResults = results.getJSONArray("results");
        if (innerResults == null) return null;

        JSONArray messages = innerResults.getJSONArray();

        if (messages != null) {
            JSONArray minimizedResults = new JSONArray();

            for (int i = 0; i < messages.length(); i++) {
                JSONObject item = messages.optJSONObject(i);
                minimizedResults.put(getMinimalMessage(item));
            }

            results.put("results", minimizedResults);
        }

        return results;
    }

    public static JSONObject getMinimalMessage(JSONObject message) {
        if (message == null) return null;

        try {
            if (message.has("from_user")) {
                JSONObject user = message.optJSONObject("from_user");
                message.put("from_user", getMinimalUser(user));
            }
            if (message.has("to_user")) {
                JSONObject user = message.optJSONObject("to_user");
                message.put("to_user", getMinimalUser(user));
            }
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }

        return message;
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
            if (observation.has("uuid") && !observation.isNull("uuid")) minimaldObs.put("uuid", observation.optString("uuid"));
            if (observation.has("observed_on") && !observation.isNull("observed_on")) minimaldObs.put("observed_on", observation.optString("observed_on"));
            if (observation.has("time_observed_at") && !observation.isNull("time_observed_at")) minimaldObs.put("time_observed_at", observation.optString("time_observed_at"));
            if (observation.has("species_guess") && !observation.isNull("species_guess")) minimaldObs.put("species_guess", observation.optString("species_guess"));
            if (observation.has("place_guess") && !observation.isNull("place_guess")) minimaldObs.put("place_guess", observation.optString("place_guess"));
            if (observation.has("latitude") && !observation.isNull("latitude")) minimaldObs.put("latitude", observation.optString("latitude"));
            if (observation.has("longitude") && !observation.isNull("longitude")) minimaldObs.put("longitude", observation.optString("longitude"));
            if (observation.has("observed_on") && !observation.isNull("observed_on")) minimaldObs.put("observed_on", observation.optString("observed_on"));
            if (observation.has("comments_count") && !observation.isNull("comments_count")) minimaldObs.put("comments_count", observation.optInt("comments_count"));
            if (observation.has("identifications_count") && !observation.isNull("identifications_count")) minimaldObs.put("identifications_count", observation.optInt("identifications_count"));
            if (observation.has("private_latitude") && !observation.isNull("private_latitude")) minimaldObs.put("private_latitude", observation.optString("private_latitude"));
            if (observation.has("private_longitude") && !observation.isNull("private_longitude")) minimaldObs.put("private_longitude", observation.optString("private_longitude"));
            if (observation.has("geoprivacy") && !observation.isNull("geoprivacy")) minimaldObs.put("geoprivacy", observation.optString("geoprivacy"));
            if (observation.has("taxon_geoprivacy") && !observation.isNull("taxon_geoprivacy")) minimaldObs.put("taxon_geoprivacy", observation.optString("taxon_geoprivacy"));

            if (observation.has("location") && !minimaldObs.has("latitude") && !minimaldObs.has("longitude")) {
                String location = observation.getString("location");
                if ((location != null) && (location.length() > 0) && (!location.equals("null"))) {
                    String[] locationSplit = location.split(",");
                    if ((locationSplit.length == 2) && (!locationSplit[0].equals("null")) && (!locationSplit[1].equals("null"))) {
                        minimaldObs.put("latitude", locationSplit[0]);
                        minimaldObs.put("longitude", locationSplit[1]);
                    }
                }
            }

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
    public static JSONObject getMinimalIdentification(JSONObject identification, String username) {
        JSONObject minimalObserver = new JSONObject();

        if (identification == null) return null;

        try {
            if (identification.has("observation")) minimalObserver.put("observation", getMinimalObservation(identification.optJSONObject("observation")));
            if (identification.has("taxon")) minimalObserver.put("taxon", getMinimalTaxon(identification.optJSONObject("taxon")));

            // See if we can retrieve a full identification details object (since the basic taxon object of the observation doesn't
            // contain things like taxon name, image URL etc.)
            if (identification.has("observation")) {
                JSONObject observation = identification.getJSONObject("observation");
                if (observation.has("identifications")) {
                    JSONArray obsIdentifications = observation.getJSONArray("identifications");
                    for (int i = 0; i < obsIdentifications.length(); i++) {
                        JSONObject id = obsIdentifications.optJSONObject(i);
                        if ((id.getJSONObject("user").getString("login").equalsIgnoreCase(username)) && (id.getBoolean("current"))) {
                            // Found current ID of the user, for this observation
                            minimalObserver.put("taxon", getMinimalTaxon(id.getJSONObject("taxon")));
                        }
                    }
                }
            }

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
            if (user.has("name")) minimalUser.put("name", user.optString("name"));
            if (user.has("id")) minimalUser.put("id", user.optInt("id"));

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
            if (sound.optBoolean("hidden", true)) {
                minimalSound.put("hidden", true);
            }

        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }

        return minimalSound;
    }

    public static JSONObject getMinimalPhoto(JSONObject photo) {
        return getMinimalPhoto(photo, false);
    }

    // Returns a minimal version of a photo JSON (used to lower memory usage)
    public static JSONObject getMinimalPhoto(JSONObject photo, boolean returnMultiplePhotoSizes) {
        JSONObject minimalPhoto = new JSONObject();

        if (photo == null) return null;

        try {
            minimalPhoto.put("id", photo.optInt("id"));
            minimalPhoto.put("position", photo.optInt("position"));

            if (photo.has("photo") && !photo.isNull("photo")) {
                JSONObject innerPhoto = new JSONObject();
                innerPhoto.put("id", photo.optJSONObject("photo").optInt("id"));

                if (photo.optJSONObject("photo").optBoolean("hidden", false)) {
                    innerPhoto.put("hidden", true);
                }

                if (!returnMultiplePhotoSizes) {
                    innerPhoto.put("url", photo.optJSONObject("photo").optString("url"));
                } else {
                    if (photo.optJSONObject("photo").has("large_url") && !photo.optJSONObject("photo").isNull("large_url")) innerPhoto.put("large_url", photo.optJSONObject("photo").optString("large_url"));
                    if (photo.optJSONObject("photo").has("original_url") && !photo.optJSONObject("photo").isNull("original_url")) innerPhoto.put("original_url", photo.optJSONObject("photo").optString("original_url"));
                    if (photo.optJSONObject("photo").has("small_url") && !photo.optJSONObject("photo").isNull("small_url")) innerPhoto.put("small_url", photo.optJSONObject("photo").optString("small_url"));
                }

                minimalPhoto.put("photo", innerPhoto);
            }
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }

        return minimalPhoto;
    }


    public static JSONObject getMinimalTaxon(JSONObject taxon) {
        return getMinimalTaxon(taxon, false);
    }

    // Returns a minimal version of a taxon JSON (used to lower memory usage)
    public static JSONObject getMinimalTaxon(JSONObject taxon, boolean includePhotos) {
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

            if (includePhotos && taxon.has("taxon_photos") && !taxon.isNull("taxon_photos")) {
                JSONArray minimalTaxonPhotos = new JSONArray();
                JSONArray taxonPhotos = taxon.optJSONArray("taxon_photos");
                for (int i = 0; i < taxonPhotos.length(); i++) {
                    minimalTaxonPhotos.put(getMinimalPhoto(taxonPhotos.optJSONObject(i), true));
                }

                minimalTaxon.put("taxon_photos", minimalTaxonPhotos);
            }

        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }

        return minimalTaxon;
    }

}

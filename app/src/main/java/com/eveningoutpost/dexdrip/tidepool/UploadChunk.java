package com.eveningoutpost.dexdrip.tidepool;


import com.eveningoutpost.dexdrip.Models.APStatus;
import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.BloodTest;
import com.eveningoutpost.dexdrip.Models.JoH;
import com.eveningoutpost.dexdrip.Models.Profile;
import com.eveningoutpost.dexdrip.Models.Treatments;
import com.eveningoutpost.dexdrip.Models.UserError;
import com.eveningoutpost.dexdrip.UtilityModels.Constants;
import com.eveningoutpost.dexdrip.UtilityModels.PersistentStore;

import java.util.LinkedList;
import java.util.List;

import static com.eveningoutpost.dexdrip.Models.JoH.dateTimeText;

/**
 * jamorham
 *
 * This class gets the next time slice of all data to upload
 */

public class UploadChunk {

    private static final String TAG = "TidePoolUploadChunk";
    private static final String LAST_UPLOAD_END_PREF = "tidepool-last-end";

    private static final long MAX_UPLOAD_SIZE = Constants.DAY_IN_MS * 3;


    public static String getNext(final Session session) {
        session.start = getLastEnd();
        session.end = maxWindow(session.start);
        return get(session.start, session.end);
    }

    public static String get(final long start, final long end) {

        UserError.Log.uel(TAG, "Syncing data between: " + dateTimeText(start) + " -> " + dateTimeText(end));
        if (end <= start) {
            UserError.Log.e(TAG, "End is <= start: " + start + " " + end);
            return null;
        }
        if (end - start > MAX_UPLOAD_SIZE) {
            UserError.Log.e(TAG, "More than 24 hours range - rejecting");
            return null;
        }

        final List<BaseElement> records = new LinkedList<>();

        records.addAll(getTreatments(start, end));
        records.addAll(getBloodTests(start, end));
        records.addAll(getBasals(start, end));
        records.addAll(getBgReadings(start, end));

        return JoH.defaultGsonInstance().toJson(records);
    }


    private static long maxWindow(final long last_end) {
        return Math.min(last_end + MAX_UPLOAD_SIZE, JoH.tsl() - Constants.MINUTE_IN_MS * 15);
    }

    private static long getLastEnd() {
        long result = PersistentStore.getLong(LAST_UPLOAD_END_PREF);
        return Math.max(result, JoH.tsl() - Constants.MONTH_IN_MS * 2);
    }

    public static void setLastEnd(final long when) {
        if (when > getLastEnd()) {
            PersistentStore.setLong(LAST_UPLOAD_END_PREF, when);
            UserError.Log.d(TAG, "Updating last end to: " + dateTimeText(when));
        } else {
            UserError.Log.e(TAG, "Cannot set last end to: " + dateTimeText(when) + " vs " + dateTimeText(getLastEnd()));
        }
    }

    static List<BaseElement> getTreatments(final long start, final long end) {
        List<BaseElement> result = new LinkedList<>();
        final List<Treatments> treatments = Treatments.latestForGraph(600, start, end);
        for (Treatments treatment : treatments) {
            if (treatment.carbs > 0) {
                result.add(EWizard.fromTreatment(treatment));
            } else if (treatment.insulin > 0) {
                result.add(EBolus.fromTreatment(treatment));
            } else {
                // note only TODO
            }
        }
        return result;
    }

    // numeric limits must match max time windows

    static List<EBloodGlucose> getBloodTests(final long start, final long end) {
        return EBloodGlucose.fromBloodTests(BloodTest.latestForGraph(600, start, end));
    }

    static List<ESensorGlucose> getBgReadings(final long start, final long end) {
        return ESensorGlucose.fromBgReadings(BgReading.latestForGraphAsc(4000, start, end));
    }

    static List<EBasal> getBasals(final long start, final long end) {
        final List<EBasal> basals = new LinkedList<>();
        final List<APStatus> aplist = APStatus.latestForGraph(4000, start, end);
        EBasal current = null;
        for (APStatus apStatus : aplist) {
            final double this_rate = Profile.getBasalRate(apStatus.timestamp) * apStatus.basal_percent / 100d;

            if (current != null) {
                if (this_rate != current.rate) {
                    current.duration = apStatus.timestamp - current.timestamp;
                    UserError.Log.d(TAG, "Adding current: " + current.toS());
                    if (current.isValid()) {
                        basals.add(current);
                    } else {
                        UserError.Log.e(TAG, "Current basal is invalid: " + current.toS());
                    }
                    current = null;
                } else {
                    UserError.Log.d(TAG, "Same rate as previous basal record: " + current.rate + " " + apStatus.toS());
                }
            }
            if (current == null) {
                current = new EBasal(this_rate, apStatus.timestamp, 0); // start duration is 0
            }
        }
        return basals;

    }

}

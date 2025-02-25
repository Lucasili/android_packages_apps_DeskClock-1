/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2015 The MoKee OpenSource Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock.provider;

import com.android.deskclock.alarms.AlarmStateManager;
import mokee.alarmclock.ClockContract;
import mokee.app.ProfileManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.util.Log;

import com.android.deskclock.R;
import com.mokee.cloud.calendar.ChineseCalendarUtils;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public final class Alarm implements Parcelable, ClockContract.AlarmsColumns {
    /**
     * Alarms start with an invalid id when it hasn't been saved to the database.
     */
    public static final long INVALID_ID = -1;

    /**
     * The default sort order for this table
     */
    private static final String DEFAULT_SORT_ORDER =
            HOUR + ", " +
            MINUTES + " ASC" + ", " +
            _ID + " DESC";

    private static final String[] QUERY_COLUMNS = {
            _ID,
            HOUR,
            MINUTES,
            DAYS_OF_WEEK,
            ENABLED,
            VIBRATE,
            LABEL,
            RINGTONE,
            DELETE_AFTER_USE,
            INCREASING_VOLUME,
            PROFILE,
            WORKDAY
    };

    /**
     * These save calls to cursor.getColumnIndexOrThrow()
     * THEY MUST BE KEPT IN SYNC WITH ABOVE QUERY COLUMNS
     */
    private static final int ID_INDEX = 0;
    private static final int HOUR_INDEX = 1;
    private static final int MINUTES_INDEX = 2;
    private static final int DAYS_OF_WEEK_INDEX = 3;
    private static final int ENABLED_INDEX = 4;
    private static final int VIBRATE_INDEX = 5;
    private static final int LABEL_INDEX = 6;
    private static final int RINGTONE_INDEX = 7;
    private static final int DELETE_AFTER_USE_INDEX = 8;
    private static final int INCREASING_VOLUME_INDEX = 9;
    private static final int PROFILE_INDEX = 10;
    private static final int WORKDAY_INDEX = 11;

    private static final int COLUMN_COUNT = PROFILE_INDEX + 1;

    public static ContentValues createContentValues(Alarm alarm) {
        ContentValues values = new ContentValues(COLUMN_COUNT);
        if (alarm.id != INVALID_ID) {
            values.put(ClockContract.AlarmsColumns._ID, alarm.id);
        }

        values.put(ENABLED, alarm.enabled ? 1 : 0);
        values.put(HOUR, alarm.hour);
        values.put(MINUTES, alarm.minutes);
        values.put(DAYS_OF_WEEK, alarm.daysOfWeek.getBitSet());
        values.put(VIBRATE, alarm.vibrate ? 1 : 0);
        values.put(LABEL, alarm.label);
        values.put(DELETE_AFTER_USE, alarm.deleteAfterUse);
        values.put(INCREASING_VOLUME, alarm.increasingVolume ? 1 : 0);
        if (alarm.alert == null) {
            // We want to put null, so default alarm changes
            values.putNull(RINGTONE);
        } else {
            values.put(RINGTONE, alarm.alert.toString());
        }
        values.put(PROFILE, alarm.profile.toString());
        values.put(WORKDAY, alarm.workday ? 1 : 0);

        return values;
    }

    public static Intent createIntent(String action, long alarmId) {
        return new Intent(action).setData(getUri(alarmId));
    }

    public static Intent createIntent(Context context, Class<?> cls, long alarmId) {
        return new Intent(context, cls).setData(getUri(alarmId));
    }

    public static Uri getUri(long alarmId) {
        return ContentUris.withAppendedId(CONTENT_URI, alarmId);
    }

    public static long getId(Uri contentUri) {
        return ContentUris.parseId(contentUri);
    }

    /**
     * Get alarm cursor loader for all alarms.
     *
     * @param context to query the database.
     * @return cursor loader with all the alarms.
     */
    public static CursorLoader getAlarmsCursorLoader(Context context) {
        return new CursorLoader(context, ClockContract.AlarmsColumns.CONTENT_URI,
                QUERY_COLUMNS, null, null, DEFAULT_SORT_ORDER);
    }

    /**
     * Get alarm by id.
     *
     * @param contentResolver to perform the query on.
     * @param alarmId for the desired alarm.
     * @return alarm if found, null otherwise
     */
    public static Alarm getAlarm(ContentResolver contentResolver, long alarmId) {
        Cursor cursor = contentResolver.query(getUri(alarmId), QUERY_COLUMNS, null, null, null);
        Alarm result = null;
        if (cursor == null) {
            return result;
        }

        try {
            if (cursor.moveToFirst()) {
                result = new Alarm(cursor);
            }
        } finally {
            cursor.close();
        }

        return result;
    }

    /**
     * Get all alarms given conditions.
     *
     * @param contentResolver to perform the query on.
     * @param selection A filter declaring which rows to return, formatted as an
     *         SQL WHERE clause (excluding the WHERE itself). Passing null will
     *         return all rows for the given URI.
     * @param selectionArgs You may include ?s in selection, which will be
     *         replaced by the values from selectionArgs, in the order that they
     *         appear in the selection. The values will be bound as Strings.
     * @return list of alarms matching where clause or empty list if none found.
     */
    public static List<Alarm> getAlarms(ContentResolver contentResolver,
            String selection, String ... selectionArgs) {
        Cursor cursor  = contentResolver.query(CONTENT_URI, QUERY_COLUMNS,
                selection, selectionArgs, null);
        List<Alarm> result = new LinkedList<Alarm>();
        if (cursor == null) {
            return result;
        }

        try {
            if (cursor.moveToFirst()) {
                do {
                    result.add(new Alarm(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }

        return result;
    }

    public static Alarm addAlarm(ContentResolver contentResolver, Alarm alarm) {
        ContentValues values = createContentValues(alarm);
        Uri uri = contentResolver.insert(CONTENT_URI, values);
        alarm.id = getId(uri);
        return alarm;
    }

    public static boolean updateAlarm(ContentResolver contentResolver, Alarm alarm) {
        if (alarm.id == Alarm.INVALID_ID) return false;
        ContentValues values = createContentValues(alarm);
        long rowsUpdated = contentResolver.update(getUri(alarm.id), values, null, null);
        return rowsUpdated == 1;
    }

    public static boolean deleteAlarm(ContentResolver contentResolver, long alarmId) {
        if (alarmId == INVALID_ID) return false;
        int deletedRows = contentResolver.delete(getUri(alarmId), "", null);
        return deletedRows == 1;
    }

    /**
     * Set an existing alarm's enabled status, accounting for all required
     * follow up actions after this occurs. These include:
     *  - Delete all existing instances of this alarm
     *  - Update the ringtone URI to be accessible if the alarm is enabled.
     *  - Update the Alarms table to set the enabled flag.
     *  - If enabling the alarm, schedule a new instance for it.
     * @param context A Context to retrieve a ContentResolver.
     * @param alarmId The ID of the alarm to change the enabled state of.
     * @param enabled if true, set the alarm to enabled. Otherwise, disabled the alarm.
     * @return true if the alarm enabled state change was successful.
     */
    public static boolean setAlarmEnabled(Context context, long alarmId, boolean enabled) {
        ContentResolver contentResolver = context.getContentResolver();
        Alarm alarm = getAlarm(contentResolver, alarmId);
        // If this alarm does not exist, we can't update it's enabled status.
        if (alarm == null || alarm.enabled == enabled) {
            return false;
        } else if (alarm.enabled == enabled) {
            // While we didn't "successfully" update anything, the current state
            // is what the caller requested, so return true.
            return true;
        }

        // Set the new enabled state.
        alarm.enabled = enabled;
        // Persist the change and schedule the next instance.
        AlarmInstance nextInstance = alarm.processUpdate(context);

        if (alarm.enabled) {
            // If the alarm's new state is enabled, processUpdate must
            // result in a new instance being created.
            return nextInstance != null;
        } else {
            // processUpdate handled disabling the alarm, return true.
            return true;
        }
    }

    /**
     * Schedule the next instance of this alarm.
     * @param context A Context to retrieve a ContentResolver.
     * @param alarm The alarm to set enabled/disabled.
     * @return The new AlarmInstance that was created.
     */
    public static AlarmInstance setupAlarmInstance(Context context, Alarm alarm) {
        ContentResolver cr = context.getContentResolver();
        AlarmInstance newInstance = alarm.createInstanceAfter(Calendar.getInstance(), context);
        newInstance = AlarmInstance.addInstance(cr, newInstance);
        // Register instance to state manager
        AlarmStateManager.registerInstance(context, newInstance, true);
        return newInstance;
    }

    public static final Parcelable.Creator<Alarm> CREATOR = new Parcelable.Creator<Alarm>() {
        public Alarm createFromParcel(Parcel p) {
            return new Alarm(p);
        }

        public Alarm[] newArray(int size) {
            return new Alarm[size];
        }
    };

    // Public fields
    // TODO: Refactor instance names
    public long id;
    public boolean enabled;
    public int hour;
    public int minutes;
    public DaysOfWeek daysOfWeek;
    public boolean vibrate;
    public String label;
    public Uri alert;
    public boolean deleteAfterUse;
    public boolean increasingVolume;
    public UUID profile;
    public boolean workday;

    // Creates a default alarm at the current time.
    public Alarm() {
        this(0, 0);
    }

    public Alarm(int hour, int minutes) {
        this.id = INVALID_ID;
        this.hour = hour;
        this.minutes = minutes;
        this.vibrate = true;
        this.daysOfWeek = new DaysOfWeek(0);
        this.label = "";
        this.alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        this.deleteAfterUse = false;
        this.increasingVolume = false;
        this.profile = ProfileManager.NO_PROFILE;
        this.workday = false;
    }

    public Alarm(Cursor c) {
        id = c.getLong(ID_INDEX);
        enabled = c.getInt(ENABLED_INDEX) == 1;
        hour = c.getInt(HOUR_INDEX);
        minutes = c.getInt(MINUTES_INDEX);
        daysOfWeek = new DaysOfWeek(c.getInt(DAYS_OF_WEEK_INDEX));
        vibrate = c.getInt(VIBRATE_INDEX) == 1;
        label = c.getString(LABEL_INDEX);
        deleteAfterUse = c.getInt(DELETE_AFTER_USE_INDEX) == 1;
        increasingVolume = c.getInt(INCREASING_VOLUME_INDEX) == 1;

        if (c.isNull(RINGTONE_INDEX)) {
            // Should we be saving this with the current ringtone or leave it null
            // so it changes when user changes default ringtone?
            alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        } else {
            alert = Uri.parse(c.getString(RINGTONE_INDEX));
        }

        if (c.isNull(PROFILE_INDEX)) {
            profile = ProfileManager.NO_PROFILE;
        } else {
            try {
                profile = UUID.fromString(c.getString(PROFILE_INDEX));
            } catch (IllegalArgumentException ex) {
                profile = ProfileManager.NO_PROFILE;
            }
        }

        if (c.isNull(WORKDAY_INDEX)) {
            workday = false;
        } else {
            workday = c.getInt(WORKDAY_INDEX) == 1;
        }
    }

    Alarm(Parcel p) {
        id = p.readLong();
        enabled = p.readInt() == 1;
        hour = p.readInt();
        minutes = p.readInt();
        daysOfWeek = new DaysOfWeek(p.readInt());
        vibrate = p.readInt() == 1;
        label = p.readString();
        alert = (Uri) p.readParcelable(null);
        deleteAfterUse = p.readInt() == 1;
        increasingVolume = p.readInt() == 1;
        profile = ParcelUuid.CREATOR.createFromParcel(p).getUuid();
        workday = p.readInt() == 1;
    }

    public String getLabelOrDefault(Context context) {
        if (label == null || label.length() == 0) {
            return context.getString(R.string.default_label);
        }
        return label;
    }

    public void writeToParcel(Parcel p, int flags) {
        p.writeLong(id);
        p.writeInt(enabled ? 1 : 0);
        p.writeInt(hour);
        p.writeInt(minutes);
        p.writeInt(daysOfWeek.getBitSet());
        p.writeInt(vibrate ? 1 : 0);
        p.writeString(label);
        p.writeParcelable(alert, flags);
        p.writeInt(deleteAfterUse ? 1 : 0);
        p.writeInt(increasingVolume ? 1 : 0);
        p.writeParcelable(new ParcelUuid(profile), 0);
        p.writeInt(workday ? 1 : 0);
    }

    public int describeContents() {
        return 0;
    }

    public AlarmInstance createInstanceAfter(Calendar time, Context context) {
        Calendar nextInstanceTime = getNextAlarmTime(time, context);
		AlarmInstance result = new AlarmInstance(nextInstanceTime, id);
        result.mVibrate = vibrate;
        result.mLabel = label;
        result.mRingtone = alert;
        result.mIncreasingVolume = increasingVolume;
        result.mProfile = profile;
        return result;
    }

    public Calendar getNextAlarmTime(Calendar currentTime, Context context) {
        return getNextAlarmTime(currentTime, context, hour, minutes);
    }

    public Calendar getNextAlarmTime(Calendar currentTime, Context context, int hour, int minutes) {
        Calendar nextInstanceTime = Calendar.getInstance();
        nextInstanceTime.set(Calendar.YEAR, currentTime.get(Calendar.YEAR));
        nextInstanceTime.set(Calendar.MONTH, currentTime.get(Calendar.MONTH));
        nextInstanceTime.set(Calendar.DAY_OF_MONTH, currentTime.get(Calendar.DAY_OF_MONTH));
        nextInstanceTime.set(Calendar.HOUR_OF_DAY, hour);
        nextInstanceTime.set(Calendar.MINUTE, minutes);
        nextInstanceTime.set(Calendar.SECOND, 0);
        nextInstanceTime.set(Calendar.MILLISECOND, 0);

        // If we are still behind the passed in currentTime, then add a day
        if (nextInstanceTime.getTimeInMillis() <= currentTime.getTimeInMillis()) {
            nextInstanceTime.add(Calendar.DAY_OF_YEAR, 1);
        }

        if (workday) {
            Context calendarContext = null;
            try {
                calendarContext = context.createPackageContext("com.android.calendar", Context.CONTEXT_IGNORE_SECURITY);
            } catch (Exception e) {
                Log.e(Alarm.class.getName(), "Create calendar context failed.");
            }
            SharedPreferences holidayPrefs = calendarContext.getSharedPreferences("chinese_holiday", Context.MODE_WORLD_READABLE);
            SharedPreferences workdayPrefs = calendarContext.getSharedPreferences("chinese_workday", Context.MODE_WORLD_READABLE);
            return ChineseCalendarUtils.calculateDaysToNextAlarmWithoutHoliday(nextInstanceTime, workdayPrefs, holidayPrefs);
        } else {
            // The day of the week might be invalid, so find next valid one
            int addDays = daysOfWeek.calculateDaysToNextAlarm(nextInstanceTime);
            if (addDays > 0) {
                nextInstanceTime.add(Calendar.DAY_OF_WEEK, addDays);
            }
        }
        return nextInstanceTime;
    }

    /**
     * Fully handle the logic to persist changes that have been made to this alarm.
     * Deletes all instances, re-grants any ringtone URI permissions, updates
     * the alarm in the DB, and recreates the next instance of this alarm.
     * @param context A Context to retrieve a ContentResolver.
     * @return The instance that was created, if the alarm is enabled and instance
     * creation was successful. Returns null otherwise.
     */
    public AlarmInstance processUpdate(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        // Register/Update the ringtone uri
        if (alert != null) {
            try {
                contentResolver.takePersistableUriPermission(
                        alert, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ex) {
                // Ignore
            }
        }

        // Dismiss all old instances
        AlarmStateManager.deleteAllInstances(context, id);

        // Update alarm
        Alarm.updateAlarm(contentResolver, this);
        if (enabled) {
            return setupAlarmInstance(context, this);
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Alarm)) return false;
        final Alarm other = (Alarm) o;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Long.valueOf(id).hashCode();
    }

    @Override
    public String toString() {
        return "Alarm{" +
                "alert=" + alert +
                ", id=" + id +
                ", enabled=" + enabled +
                ", hour=" + hour +
                ", minutes=" + minutes +
                ", daysOfWeek=" + daysOfWeek +
                ", vibrate=" + vibrate +
                ", label='" + label + '\'' +
                ", deleteAfterUse=" + deleteAfterUse +
                ", increasingVolume=" + increasingVolume +
                ", profile=" + profile +
                ", workday=" + workday +
                '}';
    }
}

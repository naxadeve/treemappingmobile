/*
 * Copyright 2018 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.light.collect.treemappingmobile.tasks;

import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;

import org.light.collect.treemappingmobile.R;
import org.light.collect.treemappingmobile.activities.FormDownloadList;
import org.light.collect.treemappingmobile.activities.NotificationActivity;
import org.light.collect.treemappingmobile.application.Collect;
import org.light.collect.treemappingmobile.dao.FormsDao;
import org.light.collect.treemappingmobile.logic.FormDetails;
import org.light.collect.treemappingmobile.preferences.GeneralSharedPreferences;
import org.light.collect.treemappingmobile.utilities.DownloadFormListUtils;
import org.light.collect.treemappingmobile.utilities.FormDownloader;
import org.light.collect.treemappingmobile.utilities.NotificationUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.light.collect.treemappingmobile.activities.FormDownloadList.DISPLAY_ONLY_UPDATED_FORMS;
import static org.light.collect.treemappingmobile.preferences.GeneralKeys.KEY_AUTOMATIC_UPDATE;
import static org.light.collect.treemappingmobile.provider.FormsProviderAPI.FormsColumns.JR_FORM_ID;
import static org.light.collect.treemappingmobile.provider.FormsProviderAPI.FormsColumns.LAST_DETECTED_FORM_VERSION_HASH;
import static org.light.collect.treemappingmobile.utilities.ApplicationConstants.RequestCodes.FORM_UPDATES_AVAILABLE_NOTIFICATION;
import static org.light.collect.treemappingmobile.utilities.ApplicationConstants.RequestCodes.FORMS_DOWNLOADED_NOTIFICATION;
import static org.light.collect.treemappingmobile.utilities.DownloadFormListUtils.DL_AUTH_REQUIRED;
import static org.light.collect.treemappingmobile.utilities.DownloadFormListUtils.DL_ERROR_MSG;
import static org.light.collect.treemappingmobile.utilities.NotificationUtils.FORM_UPDATE_NOTIFICATION_ID;

public class ServerPollingJob extends Job {

    private static final long FIFTEEN_MINUTES_PERIOD = 900000;
    private static final long ONE_HOUR_PERIOD = 3600000;
    private static final long SIX_HOURS_PERIOD = 21600000;
    private static final long ONE_DAY_PERIOD = 86400000;

    public static final String TAG = "serverPollingJob";

    @Override
    @NonNull
    protected Result onRunJob(@NonNull Params params) {
        if (!isDeviceOnline()) {
            return Result.FAILURE;
        }

        DownloadFormListUtils downloadFormListTask = new DownloadFormListUtils();
        HashMap<String, FormDetails> formList = downloadFormListTask.downloadFormList(true);

        if (formList != null && !formList.containsKey(DL_ERROR_MSG)) {
            if (formList.containsKey(DL_AUTH_REQUIRED)) {
                formList = downloadFormListTask.downloadFormList(true);

                if (formList == null || formList.containsKey(DL_AUTH_REQUIRED) || formList.containsKey(DL_ERROR_MSG)) {
                    return Result.FAILURE;
                }
            }

            List<FormDetails> newDetectedForms = new ArrayList<>();
            for (FormDetails formDetails : formList.values()) {
                if (formDetails.isNewerFormVersionAvailable() || formDetails.areNewerMediaFilesAvailable()) {
                    newDetectedForms.add(formDetails);
                }
            }

            if (!newDetectedForms.isEmpty()) {
                if (GeneralSharedPreferences.getInstance().getBoolean(KEY_AUTOMATIC_UPDATE, false)) {
                    final HashMap<FormDetails, String> result = new FormDownloader().downloadForms(newDetectedForms);
                    informAboutNewDownloadedForms(Collect.getInstance().getString(R.string.download_forms_result), result);
                } else {
                    for (FormDetails formDetails : newDetectedForms) {
                        String manifestFileHash = formDetails.getManifestFileHash() != null ? formDetails.getManifestFileHash() : "";
                        String formVersionHash = FormDownloader.getMd5Hash(formDetails.getHash()) + manifestFileHash;
                        if (!wasThisNewerFormVersionAlreadyDetected(formVersionHash)) {
                            updateLastDetectedFormVersionHash(formDetails.getFormID(), formVersionHash);
                        } else {
                            newDetectedForms.remove(formDetails);
                        }
                    }

                    if (!newDetectedForms.isEmpty()) {
                        informAboutNewAvailableForms();
                    }
                }
            }
            return Result.SUCCESS;
        } else {
            return Result.FAILURE;
        }
    }

    public static void schedulePeriodicJob(String selectedOption) {
        if (selectedOption.equals(Collect.getInstance().getString(R.string.never_value))) {
            JobManager.instance().cancelAllForTag(TAG);
        } else {
            long period = FIFTEEN_MINUTES_PERIOD;
            if (selectedOption.equals(Collect.getInstance().getString(R.string.every_one_hour_value))) {
                period = ONE_HOUR_PERIOD;
            } else if (selectedOption.equals(Collect.getInstance().getString(R.string.every_six_hours_value))) {
                period = SIX_HOURS_PERIOD;
            } else if (selectedOption.equals(Collect.getInstance().getString(R.string.every_24_hours_value))) {
                period = ONE_DAY_PERIOD;
            }

            new JobRequest.Builder(TAG)
                    .setPeriodic(period, 300000)
                    .setUpdateCurrent(true)
                    .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                    .build()
                    .schedule();
        }
    }

    private boolean wasThisNewerFormVersionAlreadyDetected(String formVersionHash) {
        Cursor cursor = new FormsDao().getFormsCursor(LAST_DETECTED_FORM_VERSION_HASH + "=?", new String[]{formVersionHash});
        return cursor == null || cursor.getCount() > 0;
    }

    private void informAboutNewAvailableForms() {
        Intent intent = new Intent(getContext(), FormDownloadList.class);
        intent.putExtra(DISPLAY_ONLY_UPDATED_FORMS, true);
        PendingIntent contentIntent = PendingIntent.getActivity(getContext(), FORM_UPDATES_AVAILABLE_NOTIFICATION, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationUtils.showNotification(
                contentIntent,
                FORM_UPDATE_NOTIFICATION_ID,
                R.string.form_updates_available,
                null);
    }

    private void informAboutNewDownloadedForms(String title, HashMap<FormDetails, String> result) {
        Intent intent = new Intent(Collect.getInstance(), NotificationActivity.class);
        intent.putExtra(NotificationActivity.NOTIFICATION_TITLE, title);
        intent.putExtra(NotificationActivity.NOTIFICATION_MESSAGE, FormDownloadList.getDownloadResultMessage(result));
        PendingIntent contentIntent = PendingIntent.getActivity(getContext(), FORMS_DOWNLOADED_NOTIFICATION, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationUtils.showNotification(contentIntent,
                FORM_UPDATE_NOTIFICATION_ID,
                R.string.odk_auto_download_notification_title,
                getContentText(result));
    }

    private void updateLastDetectedFormVersionHash(String formId, String formVersionHash) {
        ContentValues values = new ContentValues();
        values.put(LAST_DETECTED_FORM_VERSION_HASH, formVersionHash);
        new FormsDao().updateForm(values, JR_FORM_ID + "=?", new String[] {formId});
    }

    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) Collect.getInstance().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private String getContentText(HashMap<FormDetails, String> result) {
        return allFormsDownloadedSuccessfully(result)
                ? Collect.getInstance().getString(R.string.success)
                : Collect.getInstance().getString(R.string.failures);
    }

    private boolean allFormsDownloadedSuccessfully(HashMap<FormDetails, String> result) {
        for (Map.Entry<FormDetails, String> item : result.entrySet()) {
            if (!item.getValue().equals(Collect.getInstance().getString(R.string.success))) {
                return false;
            }
        }
        return true;
    }
}
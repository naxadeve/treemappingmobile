package org.light.collect.treemappingmobile.tasks;

import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;

import org.light.collect.treemappingmobile.R;
import org.light.collect.treemappingmobile.activities.FormEntryActivity;
import org.light.collect.treemappingmobile.application.Collect;
import org.light.collect.treemappingmobile.dao.helpers.ContentResolverHelper;
import org.light.collect.treemappingmobile.exception.GDriveConnectionException;
import org.light.collect.treemappingmobile.fragments.dialogs.ProgressDialogFragment;
import org.light.collect.treemappingmobile.logic.FormController;
import org.light.collect.treemappingmobile.utilities.FileUtils;
import org.light.collect.treemappingmobile.utilities.ImageConverter;
import org.light.collect.treemappingmobile.utilities.MediaUtils;
import org.light.collect.treemappingmobile.utilities.ToastUtils;
import org.light.collect.treemappingmobile.views.ODKView;
import org.light.collect.treemappingmobile.widgets.BaseImageWidget;
import org.light.collect.treemappingmobile.widgets.ImageWebViewWidget;
import org.light.collect.treemappingmobile.widgets.QuestionWidget;

import java.io.File;
import java.lang.ref.WeakReference;

import timber.log.Timber;

public class MediaLoadingTask extends AsyncTask<Uri, Void, File> {

    private WeakReference<FormEntryActivity> formEntryActivity;

    public MediaLoadingTask(FormEntryActivity formEntryActivity) {
        onAttach(formEntryActivity);
    }

    public void onAttach(FormEntryActivity formEntryActivity) {
        this.formEntryActivity = new WeakReference<>(formEntryActivity);
    }

    public void onDetach() {
        formEntryActivity = null;
    }

    @Override
    protected File doInBackground(Uri... uris) {

        File instanceFile;
        FormController formController = Collect.getInstance().getFormController();

        if (formController != null) {
            instanceFile = formController.getInstanceFile();
            if (instanceFile != null) {
                String instanceFolder = instanceFile.getParent();
                String extension = ContentResolverHelper.getFileExtensionFromUri(formEntryActivity.get(), uris[0]);
                String destMediaPath = instanceFolder + File.separator + System.currentTimeMillis() + extension;

                try {
                    File chosenFile = MediaUtils.getFileFromUri(formEntryActivity.get(), uris[0], MediaStore.Images.Media.DATA);
                    if (chosenFile != null) {
                        final File newFile = new File(destMediaPath);
                        FileUtils.copyFile(chosenFile, newFile);
                        QuestionWidget questionWidget = formEntryActivity.get().getWidgetWaitingForBinaryData();

                        // apply image conversion if the widget is an image widget
                        if (questionWidget instanceof BaseImageWidget ||
                                questionWidget instanceof ImageWebViewWidget) {
                            ImageConverter.execute(newFile.getPath(), questionWidget, formEntryActivity.get());
                        }

                        return newFile;
                    } else {
                        Timber.e("Could not receive chosen file");
                        formEntryActivity.get().runOnUiThread(() -> ToastUtils.showShortToastInMiddle(R.string.error_occured));
                        return null;
                    }
                } catch (GDriveConnectionException e) {
                    Timber.e("Could not receive chosen file due to connection problem");
                    formEntryActivity.get().runOnUiThread(() -> ToastUtils.showLongToastInMiddle(R.string.gdrive_connection_exception));
                    return null;
                }
            }
        }
        return null;

    }

    @Override
    protected void onPostExecute(File result) {
        Fragment prev = formEntryActivity.get().getSupportFragmentManager().findFragmentByTag(ProgressDialogFragment.COLLECT_PROGRESS_DIALOG_TAG);
        if (prev != null && !formEntryActivity.get().isInstanceStateSaved()) {
            ((DialogFragment) prev).dismiss();
        }

        ODKView odkView = formEntryActivity.get().getCurrentViewIfODKView();
        if (odkView != null) {
            odkView.setBinaryData(result);
        }

        formEntryActivity.get().saveAnswersForCurrentScreen(FormEntryActivity.DO_NOT_EVALUATE_CONSTRAINTS);
        formEntryActivity.get().refreshCurrentView();
    }
}

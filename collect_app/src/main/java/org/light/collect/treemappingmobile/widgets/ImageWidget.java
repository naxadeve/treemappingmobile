/*
 * Copyright (C) 2009 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.light.collect.treemappingmobile.widgets;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.v4.content.FileProvider;
import android.view.View;
import android.widget.Button;

import org.javarosa.form.api.FormEntryPrompt;
import org.light.collect.treemappingmobile.BuildConfig;
import org.light.collect.treemappingmobile.R;
import org.light.collect.treemappingmobile.activities.CaptureSelfieActivity;
import org.light.collect.treemappingmobile.activities.CaptureSelfieActivityNewApi;
import org.light.collect.treemappingmobile.application.Collect;
import org.light.collect.treemappingmobile.listeners.PermissionListener;
import org.light.collect.treemappingmobile.utilities.CameraUtils;
import org.light.collect.treemappingmobile.utilities.FileUtils;

import java.io.File;
import java.util.Locale;

import static org.light.collect.treemappingmobile.utilities.ApplicationConstants.RequestCodes;

/**
 * Widget that allows user to take pictures, sounds or video and add them to the form.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class ImageWidget extends BaseImageWidget {

    private Button captureButton;
    private Button chooseButton;

    private boolean selfie;

    public ImageWidget(Context context, final FormEntryPrompt prompt) {
        super(context, prompt);
        imageClickHandler = new ViewImageClickHandler();
        imageCaptureHandler = new ImageCaptureHandler();
        setUpLayout();
        setUpBinary();
        addAnswerView(answerLayout);
    }

    @Override
    protected void setUpLayout() {
        super.setUpLayout();

        String appearance = getFormEntryPrompt().getAppearanceHint();
        selfie = appearance != null && (appearance.equalsIgnoreCase("selfie")
                || appearance.equalsIgnoreCase("new-front"));

        captureButton = getSimpleButton(getContext().getString(R.string.capture_image), R.id.capture_image);

        chooseButton = getSimpleButton(getContext().getString(R.string.choose_image), R.id.choose_image);

        answerLayout.addView(captureButton);
        answerLayout.addView(chooseButton);
        answerLayout.addView(errorTextView);

        hideButtonsIfNeeded(appearance);
        errorTextView.setVisibility(View.GONE);

        if (selfie) {
            if (!CameraUtils.isFrontCameraAvailable()) {
                captureButton.setEnabled(false);
                errorTextView.setText(R.string.error_front_camera_unavailable);
                errorTextView.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public Intent addExtrasToIntent(Intent intent) {
        return intent;
    }

    @Override
    public void clearAnswer() {
        super.clearAnswer();
        // reset buttons
        captureButton.setText(getContext().getString(R.string.capture_image));
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        captureButton.setOnLongClickListener(l);
        chooseButton.setOnLongClickListener(l);
        super.setOnLongClickListener(l);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        captureButton.cancelLongPress();
        chooseButton.cancelLongPress();
    }

    @Override
    public void onButtonClick(int buttonId) {
        switch (buttonId) {
            case R.id.capture_image:
                getPermissionUtils().requestCameraPermission(new PermissionListener() {
                    @Override
                    public void granted() {
                        captureImage();
                    }

                    @Override
                    public void denied() {
                    }
                });
                break;
            case R.id.choose_image:
                imageCaptureHandler.chooseImage(R.string.choose_image);
                break;
        }
    }

    private void hideButtonsIfNeeded(String appearance) {
        if (selfie || ((appearance != null
                && appearance.toLowerCase(Locale.ENGLISH).contains("new")))) {
            chooseButton.setVisibility(View.GONE);
        }
    }

    private void captureImage() {
        errorTextView.setVisibility(View.GONE);
        Intent intent;
        if (selfie) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                intent = new Intent(getContext(), CaptureSelfieActivityNewApi.class);
            } else {
                intent = new Intent(getContext(), CaptureSelfieActivity.class);
            }
        } else {
            intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            // We give the camera an absolute filename/path where to put the
            // picture because of bug:
            // http://code.google.com/p/android/issues/detail?id=1480
            // The bug appears to be fixed in Android 2.0+, but as of feb 2,
            // 2010, G1 phones only run 1.6. Without specifying the path the
            // images returned by the camera in 1.6 (and earlier) are ~1/4
            // the size. boo.

            Uri uri = FileProvider.getUriForFile(getContext(),
                    BuildConfig.APPLICATION_ID + ".provider",
                    new File(Collect.TMPFILE_PATH));
            // if this gets modified, the onActivityResult in
            // FormEntyActivity will also need to be updated.
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri);
            FileUtils.grantFilePermissions(intent, uri, getContext());
        }

        imageCaptureHandler.captureImage(intent, RequestCodes.IMAGE_CAPTURE, R.string.capture_image);
    }

}

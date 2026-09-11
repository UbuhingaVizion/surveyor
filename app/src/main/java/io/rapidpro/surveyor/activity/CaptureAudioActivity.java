package io.rapidpro.surveyor.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import io.rapidpro.surveyor.Logger;
import io.rapidpro.surveyor.R;
import io.rapidpro.surveyor.SurveyorIntent;
import io.rapidpro.surveyor.ui.IconTextView;

/**
 * Activity for capturing an audio recording
 */
public class CaptureAudioActivity extends BaseActivity {

    private boolean isRecording = false;
    private MediaRecorder mediaRecorder;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_capture_audio);
    }

    @Override
    public boolean requireLogin() {
        return false;
    }

    /**
     * Starts recording audio to the file provided in the intent
     */
    public void recordAudio() {
        String output = getIntent().getStringExtra(SurveyorIntent.EXTRA_MEDIA_FILE);

        Logger.d("Recording audio to " + output + "...");

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mediaRecorder = new MediaRecorder(this);
            } else {
                mediaRecorder = new MediaRecorder();
            }
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(output);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;

        } catch (Exception e) {
            Logger.e("Unable to start recording", e);

            releaseMediaRecorder();
            isRecording = false;

            Toast.makeText(this, R.string.audio_error, Toast.LENGTH_SHORT).show();
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
            } catch (Exception ignored) {
            }
            try {
                mediaRecorder.release();
            } catch (Exception ignored) {
            }
            mediaRecorder = null;
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (RuntimeException e) {
                // stop() throws if no valid audio data was recorded (e.g. stopped too early)
                Logger.e("Recording was too short or invalid", e);

                releaseMediaRecorder();
                isRecording = false;
                deleteOutput();

                setResult(Activity.RESULT_CANCELED);
                finish();
                return;
            }
        }

        releaseMediaRecorder();
        isRecording = false;

        Intent returnIntent = new Intent();
        returnIntent.putExtra(SurveyorIntent.EXTRA_MEDIA_FILE, getIntent().getStringExtra(SurveyorIntent.EXTRA_MEDIA_FILE));
        setResult(Activity.RESULT_OK, returnIntent);
        finish();
    }

    private void deleteOutput() {
        String output = getIntent().getStringExtra(SurveyorIntent.EXTRA_MEDIA_FILE);
        if (output != null) {
            File file = new File(output);
            if (file.exists() && !file.delete()) {
                Logger.w("Unable to delete incomplete recording " + output);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // don't leave the microphone locked if we're backgrounded (e.g. incoming call)
        if (isRecording) {
            stopRecording();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        releaseMediaRecorder();
    }

    public void toggleRecording(View view) {
        if (!isRecording) {
            Resources res = getResources();

            IconTextView button = (IconTextView) getViewCache().getView(R.id.button_capture);
            button.setTextColor(res.getColor(R.color.recording));

            TextView instructions = (TextView) getViewCache().getView(R.id.text_instructions);
            instructions.setText(R.string.tap_to_stop);

            getViewCache().getView(R.id.content_view).setBackgroundColor(res.getColor(R.color.warning));
            recordAudio();
        } else {
            stopRecording();
        }
    }
}

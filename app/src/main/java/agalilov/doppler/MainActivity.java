package agalilov.doppler;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.apache.commons.math3.complex.Complex;

import agalilov.doppler.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, ProcessingThread.IOnSpectreReady {
    public static final String TAG = "Doppler";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private ActivityMainBinding _binding;

    private final String[] _permissions = new String[]{Manifest.permission.RECORD_AUDIO};
    private boolean _isMicInUse = false;
    private RecordAudioThread _recordAudioThread = null;
    private PlayAudioThread _playAudioThread = null;

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Requesting permission to RECORD_AUDIO
        boolean granted = grantResults.length > 0 &&
                requestCode == REQUEST_RECORD_AUDIO_PERMISSION &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            continueCreation();
        } else {
            Toast.makeText(this, "Sound recording is not available", Toast.LENGTH_LONG)
                    .show();
            finish();
        }
    }

    private void continueCreation() {
        setRequestedOrientation(SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        _binding = ActivityMainBinding.inflate(getLayoutInflater());
        _binding.btnStartStop.setOnClickListener(this);
        setContentView(_binding.getRoot());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        ActivityCompat.requestPermissions(
                this,
                _permissions,
                REQUEST_RECORD_AUDIO_PERMISSION);
    }

    @Override
    public void onClick(View v) {
        if (v == _binding.btnStartStop) {
            if (_isMicInUse) {
                _binding.btnStartStop.setText(R.string.start);
            } else {
                _binding.btnStartStop.setText(R.string.stop);
            }
            _isMicInUse = !_isMicInUse;
            if (_isMicInUse) {
                startAudioProcessing();
            } else {
                stopAudioProcessing();
            }
        }
    }

    private void stopAudioProcessing() {
        if (_recordAudioThread != null && _playAudioThread != null) {
            _recordAudioThread.interrupt();
            _playAudioThread.interrupt();
            try {
                _recordAudioThread.join();
                _playAudioThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted");
            } finally {
                _recordAudioThread = null;
                _playAudioThread = null;
            }
        }
    }

    private void startAudioProcessing() {
        if (_recordAudioThread != null || _playAudioThread != null) {
            throw new IllegalStateException("Thread is already running or was not properly cleared");
        }
        float soundSpeed = Float.parseFloat(_binding.editSoundSpeed.getText().toString());
        int centralFreq = Integer.parseInt(_binding.editCentralFreq.getText().toString());
        int sampleRate = Integer.parseInt(_binding.editSampleRate.getText().toString());
        int fftWindow = Integer.parseInt(_binding.editFFTWindow.getText().toString());
        AudioParameters params = new AudioParameters(sampleRate, fftWindow, AudioFormat.ENCODING_PCM_16BIT, centralFreq);
        _recordAudioThread = new RecordAudioThread(params, this);
        _playAudioThread = new PlayAudioThread(params);
        _binding.dopplerView.setFftChunkSize(params.getNFftChunkSamples());
        _binding.dopplerView.setSampleRate(params.getSampleRate());
        _binding.dopplerView.setSoundSpeed(soundSpeed, _binding.checkBoxPlayFreq.isChecked());
        _binding.dopplerView.setCentralHarmonic(params.getHarmonic());
        _recordAudioThread.start();
        if (_binding.checkBoxPlayFreq.isChecked())
            _playAudioThread.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAudioProcessing();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("centralFreq", _binding.editCentralFreq.getText().toString());
        outState.putString("soundSpeed", _binding.editSoundSpeed.getText().toString());
        outState.putString("sampleRate", _binding.editSampleRate.getText().toString());
        outState.putString("fftWindow", _binding.editFFTWindow.getText().toString());
        outState.putBoolean("playFreq", _binding.checkBoxPlayFreq.isChecked());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        String centralFreq = savedInstanceState.getString("centralFreq");
        if (centralFreq != null && !centralFreq.isEmpty())
            _binding.editCentralFreq.setText(centralFreq);

        String soundSpeed = savedInstanceState.getString("soundSpeed");
        if (soundSpeed != null && !soundSpeed.isEmpty())
            _binding.editSoundSpeed.setText(soundSpeed);

        String sampleRate = savedInstanceState.getString("sampleRate");
        if (sampleRate != null && !sampleRate.isEmpty())
            _binding.editSampleRate.setText(sampleRate);

        String fftWindow = savedInstanceState.getString("fftWindow");
        if (fftWindow != null && !fftWindow.isEmpty())
            _binding.editFFTWindow.setText(fftWindow);

        boolean isPleyFreq = savedInstanceState.getBoolean("playFreq");
        _binding.checkBoxPlayFreq.setChecked(isPleyFreq);
    }

    @Override
    public void onSpectreReady(Complex[] data) {
        _binding.dopplerView.enqueue(data);
    }
}
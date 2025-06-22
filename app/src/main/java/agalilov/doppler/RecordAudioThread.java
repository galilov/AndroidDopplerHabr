package agalilov.doppler;

import android.Manifest;
import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import org.apache.commons.math3.complex.Complex;

class RecordAudioThread extends Thread {
    private final AudioParameters _params;
    private final ProcessingThread _processingThread;

    public RecordAudioThread(AudioParameters params, ProcessingThread.IOnSpectreReady onSpectreReady) {
        _params = params;
        _processingThread = new ProcessingThread(onSpectreReady, params.getNFftChunkSamples());
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @Override
    public void run() {
        @SuppressLint("WrongConstant")
        AudioFormat af = new AudioFormat.Builder()
                .setEncoding(_params.getEncoding())
                .setSampleRate(_params.getSampleRate())
                .setChannelMask(_params.getChannelRecordConfig())
                .build();
        final AudioRecord audioRecord = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
                .setAudioFormat(af)
                .setBufferSizeInBytes(_params.getRecordBufferSize())
                .build();

        audioRecord.startRecording();

        _processingThread.start();

        while (!interrupted()) {
            final short[] rawData = new short[_params.getRecordBufferSize()];
            final int nRead = audioRecord.read(rawData, 0, rawData.length);
            //Log.d(MainActivity.TAG, nRead.toString())
            _processingThread.enqueue(rawData, nRead);
        }
        _processingThread.interrupt();
        try {
            _processingThread.join();
        } catch (InterruptedException e) {
            Log.e(MainActivity.TAG, "Unexpected exception");
        }
        audioRecord.stop();
        audioRecord.release();
    }
}

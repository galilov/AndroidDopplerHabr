package agalilov.doppler;

import android.annotation.SuppressLint;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

class PlayAudioThread extends Thread {
    private final AudioParameters _params;
    private final int _harmonic;

    public PlayAudioThread(AudioParameters params) {
        _params = params;
        _harmonic = (int) Math.round((double) (_params.getNFftChunkSamples() * _params.getPlayFreq()) / _params.getSampleRate());
    }

    @Override
    public void run() {
        @SuppressLint("WrongConstant") final AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(_params.getSampleRate())
                        .setChannelMask(_params.getChannelPlayConfig())
                        .setEncoding(_params.getEncoding())
                        .build()
                )
                .setBufferSizeInBytes(_params.getPlayBufferSize())
                .build();
        try {
            track.play();
            final int amplitude = 32767;
            final short[] samples = new short[_params.getPlayBufferSize()];
            final int fftChuckLength = _params.getNFftChunkSamples();
            final double delta = 2 * Math.PI * _harmonic / fftChuckLength;
            double phase = 0.0;
            while (!interrupted()) {
                for (int i = 0; i < samples.length; i++) {
                    samples[i] = (short) (amplitude * Math.sin(phase));
                    phase += delta;
                }
                track.write(samples, 0, samples.length);
            }
        } finally {
            track.stop();
            track.release();
        }
    }

    public int getHarmonic() {
        return _harmonic;
    }
}

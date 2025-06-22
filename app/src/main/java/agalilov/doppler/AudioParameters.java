package agalilov.doppler;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;

final class AudioParameters {
    private static final int _channelRecordConfig = AudioFormat.CHANNEL_IN_MONO;
    private static final int _channelPlayConfig = AudioFormat.CHANNEL_OUT_MONO;
    //private final AudioFormat _audioRecordFormat, _audioPlayFormat;
    private final int _recordBufferSize, _playBufferSize, _nFftChunkSamples, _playFreq, _encoding, _sampleRate;

    public AudioParameters(int sampleRate, int nFftChunkSamples, int encoding, int playFreq) {
        _sampleRate = sampleRate;
        _nFftChunkSamples = nFftChunkSamples;
        _encoding = encoding;
        _playFreq = playFreq;

        _recordBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                _channelRecordConfig,
                encoding);
        if (_recordBufferSize < 0) {
            throw new RuntimeException("Error in AudioRecord.getMinBufferSize");
        }

        _playBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                _channelPlayConfig,
                encoding);
        if (_playBufferSize < 0) {
            throw new RuntimeException("Error in AudioTrack.getMinBufferSize");
        }
    }

    public int getRecordBufferSize() {
        return _recordBufferSize;
    }

    public int getPlayBufferSize() {
        return _playBufferSize;
    }

    public int getNFftChunkSamples() {
        return _nFftChunkSamples;
    }

    public int getPlayFreq() {
        return _playFreq;
    }

    public int getEncoding() {
        return _encoding;
    }

    public int getSampleRate() {
        return _sampleRate;
    }

    public int getChannelRecordConfig() {
        return _channelRecordConfig;
    }

    public int getChannelPlayConfig() {
        return _channelPlayConfig;
    }

    public int getHarmonic() {
        return (int) Math.round((double) (_nFftChunkSamples * _playFreq) / _sampleRate);
    }
}

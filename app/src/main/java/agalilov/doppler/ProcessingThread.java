package agalilov.doppler;

import android.util.Log;

import org.apache.commons.math3.complex.Complex;

import java.util.LinkedList;

import uk.me.berndporr.kiss_fft.KISSFastFourierTransformer;

class ProcessingThread extends Thread {
    public interface IOnSpectreReady {
        void onSpectreReady(Complex[] data);
    }

    static final String TAG = "ProcessingThread";
    private int _nFftChunkSamples;
    //final int FFT_CHUNK_SIZE_POW2 = FFT_CHUNK_SIZE * FFT_CHUNK_SIZE;
    private short[] _fftChunk;
    private int _index = 0;
    private final LinkedList<short[]> _chunks = new LinkedList<>();
    private final Object _sync = new Object();
    private final KISSFastFourierTransformer _fft = new KISSFastFourierTransformer();
    //private FFT _fft = new FFT(FFT_CHUNK_SIZE);
    private final double[] _timeData;
    //private float[] _y = new float[FFT_CHUNK_SIZE];
    //private double[] _spectreData = new double[FFT_CHUNK_SIZE];
    private final IOnSpectreReady _onSpectreReady;

    public ProcessingThread(IOnSpectreReady onSpectreReady, int nFftChunkSample) {
        _onSpectreReady = onSpectreReady;
        _nFftChunkSamples = nFftChunkSample;
        _fftChunk = new short[_nFftChunkSamples];
        _timeData = new double[_nFftChunkSamples];
    }

    void enqueue(short[] rawData, int nRead) {
        int start = 0, end = 0;
        do {
            end = Math.min(_fftChunk.length - _index, nRead - end) + start;
            System.arraycopy(rawData, start, _fftChunk, _index, end - start);
            _index += (end - start);
            start = end;

            if (_index == _fftChunk.length) {
                synchronized (_sync) {
                    _chunks.add(_fftChunk);
                    _sync.notify();
                }
                _index = 0;
                _fftChunk = new short[_nFftChunkSamples];
            }
        } while (start < nRead);
    }

    @Override
    public void run() {
        try {
            while (!interrupted()) {
                short[] fftChunk;
                synchronized (_sync) {
                    do {
                        _sync.wait(); // wait for _condition.signal()
                    } while (_chunks.isEmpty());
                    fftChunk = _chunks.removeLast(); // get latest chunk
                    _chunks.clear(); // remove old chunks
                }

                for (int i = 0; i < _nFftChunkSamples; i++) {
                    _timeData[i] = fftChunk[i];
                }

                Complex[] complexSpectre = _fft.transformRealOptimisedForward(_timeData);
                _onSpectreReady.onSpectreReady(complexSpectre);
            }
        } catch (InterruptedException e) {
            Log.d(TAG, "Interrupted");
        } finally {
            try {
                _fft.close();
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
    }
}

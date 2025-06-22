package agalilov.doppler;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import org.apache.commons.math3.complex.Complex;

import java.util.LinkedList;
import java.util.Locale;
import java.util.Objects;

public class MySurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private class DrawThread extends Thread {
        static final String TAG = "MySurfaceView.DrawThread";
        private final LinkedList<Complex[]> _queue = new LinkedList<>();
        private final Object _sync = new Object();
        private final SurfaceHolder _holder;

        DrawThread(SurfaceHolder holder) {
            _holder = holder;
        }

        public void enqueue(Complex[] data) {
            synchronized (_sync) {
                _queue.addLast(data);
                _sync.notify();
            }
        }

        @Override
        public void run() {
            try {
                while (!interrupted()) {
                    synchronized (_sync) {
                        _sync.wait();
                        while (!_queue.isEmpty()) {
                            Complex[] data = _queue.removeFirst();
                            if (_holder.getSurface().isValid()) {
                                Canvas canvas = _holder.lockCanvas();
                                if (canvas != null) {
                                    try {
                                        draw(canvas, data);
                                    } finally {
                                        _holder.unlockCanvasAndPost(canvas);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "Interrupted");
            }
        }
    }

    static final String TAG = "MySurfaceView";

    private DrawThread _thread = null;
    private Typeface _tf = null;
    private int _centralHarmonic = 0;
    private int _fftChunkSize = 0;
    private int _sampleRate = 0;
    private float _soundSpeed = 0;
    private boolean _isReflectedSound = false;

    public void setCentralHarmonic(int harmonic) {
        _centralHarmonic = harmonic;
    }

    public void setFftChunkSize(int fftChunkSize) {
        _fftChunkSize = fftChunkSize;
    }

    public void setSampleRate(int sampleRate) {
        _sampleRate = sampleRate;
    }

    public void setSoundSpeed(float soundSpeed, boolean isReflectedSound) {
        _soundSpeed = soundSpeed;
        _isReflectedSound = isReflectedSound;
    }

    private void draw(Canvas canvas, Complex[] data) {
        final int w = canvas.getWidth();
        final int h = canvas.getHeight();
        final float strokeWidth = w / 200f;
        final float textSize = h / 20f;

        Paint paintHarmonics = new Paint();
        paintHarmonics.setColor(Color.YELLOW);
        paintHarmonics.setStrokeWidth(strokeWidth);
        paintHarmonics.setAntiAlias(true);

        Paint paintText = new Paint();
        paintText.setTypeface(_tf);
        paintText.setTextSize(textSize);
        paintText.setColor(Color.GREEN);
        paintText.setAntiAlias(true);

        Paint paintCentralHarmonic = new Paint();
        paintCentralHarmonic.setColor(Color.WHITE);
        paintCentralHarmonic.setStrokeWidth(2);
        paintCentralHarmonic.setAntiAlias(true);

        // Fill the canvas with specified ARGB color.
        canvas.drawARGB(255, 0x04, 0x09, 0x47);
        final float step = 1.2f * strokeWidth;
        final int nHarmonics = (int) ((w - strokeWidth * 2) / step);
        final int startHarmonic = _centralHarmonic - nHarmonics / 2;
        final int endHarmonic = _centralHarmonic + nHarmonics / 2;
        double centralHarmonicMagnitude = 0;
        double max = 0;
        int maxHarmonic = -1;
        for (int i = startHarmonic; i < endHarmonic; i++) {
            if (i >= data.length) break;
            double re = data[i].getReal();
            double im = data[i].getImaginary();
            double m = re * re + im * im;
            if (i == _centralHarmonic) {
                centralHarmonicMagnitude = m;
                continue;
            }
            if (m > max) {
                max = m;
                maxHarmonic = i;
            }
        }

        double scale = 0;
        if (max > 0) {
            scale = (h - textSize) / Math.max(max, centralHarmonicMagnitude / 100);
        }

        int i = startHarmonic;
        for (float x = step + strokeWidth; x < w - strokeWidth; x += step, i++) {
            if (i >= data.length) continue;
            double re = data[i].getReal();
            double im = data[i].getImaginary();
            double m = re * re + im * im;
            float ampl = (float) (scale * m);
            if (i == _centralHarmonic) {
                canvas.drawLine(x, h, x, 0, paintCentralHarmonic);
            } else {
                if (ampl > h * 0.05f) {
                    canvas.drawLine(x, h, x, h - ampl, paintHarmonics);
                }
                if (ampl > h * 0.1f) {
                    float freq = (float) (_sampleRate) * i / _fftChunkSize;
                    String sFreq = String.format(Locale.ROOT, "%.2fHz (%.1f cm/s)",
                            freq, 100 * speed(i));
                    canvas.drawText(sFreq, x, h - ampl, paintText);
                }
            }

        }
        //canvas.drawText("Hello", 550, 500, paintText);
    }

    private float speed(int harmonic) {
        float freq1 = (float) _sampleRate * harmonic / _fftChunkSize;
        float freq = (float) _sampleRate * _centralHarmonic / _fftChunkSize;
        if (_isReflectedSound) {
            // f' = f * (c + v_отражателя) / (c - v_отражателя)
            // f' * (c - v_отражателя) = f * (c + v_отражателя)
            // f' * c - f' * v_отражателя = f * c + f * v_отражателя
            // f' * c - f * c  = f * v_отражателя + f' * v_отражателя
            // f' * c - f * c  = v_отражателя * (f + f')
            // c * (f' - f) / (f + f') = v_отражателя
            return _soundSpeed * (freq1 - freq) / (freq1 + freq);
        } else {
            // f' = f * (c + v_наблюдателя) / c
            // c * f' = f * (c + v_наблюдателя)
            // c * f' / f = c + v_наблюдателя
            // c * f' / f - c = v_наблюдателя
            return _soundSpeed * freq1 / freq - _soundSpeed;
        }
    }

    public void enqueue(Complex[] data) {
        Objects.requireNonNull(_thread).enqueue(data);
    }

    public MySurfaceView(Context context) {
        super(context);
        init();
    }

    public MySurfaceView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        init();
    }

    public MySurfaceView(Context context, AttributeSet attributeSet, int defStyleAttr) {
        super(context, attributeSet, defStyleAttr);
        init();
    }

    private void init() {
        setZOrderOnTop(true);
        getHolder().addCallback(this);
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        _tf = Typeface.createFromAsset(getContext().getAssets(), "fonts/liberation_mono_regular.ttf");
        _thread = new DrawThread(holder);
        _thread.start();
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        _thread.interrupt();
        try {
            _thread.join();
        } catch (InterruptedException e) {
            Log.d(TAG, "Interrupted");
        } finally {
            _thread = null;
        }
    }
}

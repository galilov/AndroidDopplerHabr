package uk.me.berndporr.kiss_fft;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.TransformType;

/**
 * Created by Bernd Porr, mail@berndporr.me.uk on 31/12/17.
 * 22/06/2025 agalilov: added FFT_CFG and related code to avoid the recon of FFT coefficients.
 * <p>
 * Fast Fourier JAVA class which calls the C KISS FFT for
 * superfast native FFT.
 */

public class KISSFastFourierTransformer implements AutoCloseable {

    static {
        System.loadLibrary("kiss-fft-lib");
    }

    private long m_state = 0; // it is used in kiss-fft-lib.cpp

    public Complex[] transform(Complex[] input, TransformType transformType) {
        double[] ri = new double[input.length * 2];
        int idx = 0;
        for (Complex c : input) {
            ri[idx++] = c.getReal();
            ri[idx++] = c.getImaginary();
        }
        ri = dofft(ri, transformtype2Int(transformType));
        Complex[] result = new Complex[input.length];
        idx = 0;
        for (int i = 0; i < input.length; i++) {
            result[i] = new Complex(ri[idx++], ri[idx++]);
        }
        return result;
    }

    public Complex[] transform(double[] v) {
        return dofftdouble(v, 0);
    }

    public Complex[] transform(Double[] v) {
        int n = v.length;
        double[] cv = new double[n];
        for (int i = 0; i < n; i++) {
            cv[i] = v[i];
        }
        return dofftdouble(cv, 0);
    }

    public Complex[] transformRealOptimisedForward(double[] v) {
        return dofftr(v);
    }

    public double[] transformRealOptimisedInverse(Complex[] v) {
        return dofftri(v);
    }


    private native double[] dofft(double[] data, int is_inverse);

    private native Complex[] dofftdouble(double[] data, int is_inverse);

    private native Complex[] dofftr(double[] data);

    private native double[] dofftri(Complex[] data);

    public native void removeConfigs();

    private int transformtype2Int(TransformType transformType) {
        int i = 0;
        if (transformType == TransformType.INVERSE) i = 1;
        return i;
    }

    @Override
    public void close() {
        removeConfigs();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            removeConfigs();
        } finally {
            super.finalize();
        }
    }
}

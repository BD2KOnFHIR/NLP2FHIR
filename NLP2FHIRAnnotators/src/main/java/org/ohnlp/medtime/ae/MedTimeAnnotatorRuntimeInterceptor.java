package org.ohnlp.medtime.ae;

import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import org.apache.uima.jcas.JCas;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Provides runtime interception of {@link MedTimeAnnotator} processing calls
 */
public class MedTimeAnnotatorRuntimeInterceptor {
    public void intercept(@SuperCall Callable<Void> call, @Argument(0) JCas cas) {
        try {
            call.call();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

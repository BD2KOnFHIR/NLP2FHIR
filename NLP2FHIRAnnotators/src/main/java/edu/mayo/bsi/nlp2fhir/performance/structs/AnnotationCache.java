package edu.mayo.bsi.nlp2fhir.performance.structs;

import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serves as an annotation cache which keeps track of annotation begin/ends in a given document so as to achieve
 * O(klogn) instead of O(n^2) collision checking
 * <p>
 * Adapted from LayeredLanguageIR project
 *
 * @author Andrew Wen
 */
public class AnnotationCache {

    // Cache as a static variable across index generations where possible
    public static ConcurrentHashMap<String, AtomicBoolean> ANN_CACHE_LOCKS = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, AnnotationCache.AnnotationTree> ANN_CACHE = new ConcurrentHashMap<>();


    public static AnnotationTree getAnnotationCache(String meta, JCas cas) {
        FSIterator<TOP> it = cas.getJFSIndexRepository().getAllIndexedFS(Annotation.type);
        Collection<Annotation> anns = new LinkedList<>();
        while (it.hasNext()) {
            anns.add((Annotation) it.next());
        }
        return getAnnotationCache(meta, cas.getDocumentText().length(), anns);
    }

    public static AnnotationTree getAnnotationCache(String meta, int docLength, Collection<Annotation> items) {
        ANN_CACHE_LOCKS.putIfAbsent(meta, new AtomicBoolean(false));
        final AtomicBoolean lock = ANN_CACHE_LOCKS.get(meta);
        if (!ANN_CACHE.containsKey(meta)) {
            if (ANN_CACHE.putIfAbsent(meta, new AnnotationNode(0, docLength))
                    == null) { // Successful Lock Acquisition
                AnnotationTree currCache = ANN_CACHE.get(meta);
                for (Annotation ann : items) {
                    currCache.insert(ann);
                }
                synchronized (lock) {
                    lock.set(true);
                    lock.notifyAll();
                }
                return currCache;
            } else {
                synchronized (lock) {
                    while (!lock.get()) {
                        try {
                            lock.wait(100);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
                return ANN_CACHE.get(meta);
            }
        } else {
            synchronized (lock) {
                while (!lock.get()) {
                    try {
                        lock.wait(100);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            return ANN_CACHE.get(meta);
        }
    }

    public static void removeAnnotationCache(String meta) {
        final AtomicBoolean lock = ANN_CACHE_LOCKS.get(meta);
        synchronized (lock) {
            while (!lock.get()) {
                try {
                    lock.wait(100);
                } catch (InterruptedException ignored) {
                }
            }
            lock.set(false);
            lock.notifyAll();
        }
        ANN_CACHE.remove(meta);
        ANN_CACHE_LOCKS.remove(meta);
    }

    public static abstract class AnnotationTree {
        public abstract void insert(Annotation ann);

        public abstract void remove(Annotation ann); // Shouldn't be necessary but implement it just in case

        /**
         * @return a list of T constrained by the given bounds
         */
        public abstract <T extends Annotation> Collection<T> getCovering(int start, int end, Class<T> clazz);

        /**
         * @return a list of T constraining the given bounds
         */
        public abstract <T extends Annotation> Collection<T> getCovered(int start, int end, Class<T> clazz);

        public abstract <T extends Annotation> Collection<T> getCollisions(int start, int end, Class<T> clazz);
    }


    private static class AnnotationNode extends AnnotationTree {

        private static int MIN_LEAF_SIZE = 20;

        private AnnotationTree left;
        private AnnotationTree right;
        private int split;

        public AnnotationNode(int start, int end) {
            split = (start + end) / 2;
            if ((split - start) > MIN_LEAF_SIZE) {
                left = new AnnotationNode(start, split);
                right = new AnnotationNode(split + 1, end);
            } else {
                left = new AnnotationLeaf();
                right = new AnnotationLeaf();
            }
        }

        @Override
        public void insert(Annotation ann) {
            if (ann.getBegin() <= split) {
                left.insert(ann);
            }
            if (ann.getEnd() > split) {
                right.insert(ann);
            }
        }

        @Override
        public void remove(Annotation ann) {
            if (ann.getBegin() <= split) {
                left.remove(ann);
            }
            if (ann.getEnd() > split) {
                right.remove(ann);
            }
        }

        @Override
        public <T extends Annotation> Collection<T> getCovering(int start, int end, Class<T> clazz) {
            LinkedHashSet<T> build = new LinkedHashSet<>();
            if (start <= split) {
                build.addAll(left.getCovering(start, end, clazz));
            }
            if (end > split) {
                build.addAll(right.getCovering(start, end, clazz));
            }
            return build;
        }

        @Override
        public <T extends Annotation> Collection<T> getCovered(int start, int end, Class<T> clazz) {
            LinkedHashSet<T> build = new LinkedHashSet<>();
            if (start <= split) {
                build.addAll(left.getCovered(start, end, clazz));
            }
            if (end > split) {
                build.addAll(right.getCovered(start, end, clazz));
            }
            return build;
        }

        @Override
        public <T extends Annotation> Collection<T> getCollisions(int start, int end, Class<T> clazz) {
            LinkedHashSet<T> build = new LinkedHashSet<>();
            if (start <= split) {
                build.addAll(left.getCollisions(start, end, clazz));
            }
            if (end > split) {
                build.addAll(right.getCollisions(start, end, clazz));
            }
            return build;
        }
    }


    private static class AnnotationLeaf extends AnnotationTree {

        private LinkedList<Annotation> annColl;

        public AnnotationLeaf() {
            annColl = new LinkedList<>();
        }

        @Override
        public void insert(Annotation ann) {
            annColl.add(ann);
        }

        @Override
        public void remove(Annotation ann) {
            annColl.remove(ann);
        }

        @Override
        public <T extends Annotation> Collection<T> getCovering(int start, int end, Class<T> clazz) {
            return null;
        }

        @Override
        public <T extends Annotation> Collection<T> getCovered(int start, int end, Class<T> clazz) {
            return null;
        }

        @Override
        public <T extends Annotation> Collection<T> getCollisions(int start, int end, Class<T> clazz) {
            LinkedList<T> ret = new LinkedList<>();
            for (Annotation ann : annColl) {
                if ((ann.getBegin() <= start && ann.getEnd() > start) || (ann.getBegin() >= start
                        && ann.getBegin() <= end)) {
                    if (clazz.isInstance(ann)) {
                        ret.add((T) ann);
                    }
                }
            }
            return ret;
        }
    }

}
/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.jenkins.plugins.dockerslaves;

import hudson.console.ConsoleNote;
import hudson.model.TaskListener;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.io.output.DeferredFileOutputStream;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Delegating {@link hudson.model.TaskListener} that tee output stream
 *
 * Most of the code comes from hudson.maven.SplittableBuildListener}
 */
public class TeeTaskListener implements TaskListener {

    private final TaskListener delegate;

    /**
     * Used to accumulate data when no one is claiming the {@link #side},
     * so that the next one who set the {@link #side} can claim all the data.
     *
     * {@link DeferredFileOutputStream} is used so that even if we get out of sync with Maven
     * and end up accumulating a lot of data, we still won't kill the JVM.
     */
    private DeferredFileOutputStream unclaimed;

    private final File deferredFile;

    private volatile OutputStream side;

    /**
     * Constant {@link PrintStream} connected to both {@link #delegate} and {@link #side}.
     * This is so that we can change the side stream without the client noticing it.
     */
    private final PrintStream logger;

    public TeeTaskListener(TaskListener delegate, File deferredFile) {
        this.delegate = delegate;
        this.deferredFile = deferredFile;

        final OutputStream base = delegate.getLogger();
        unclaimed = newLog();
        side = unclaimed;

        final OutputStream tee = new OutputStream() {
            public void write(int b) throws IOException {
                base.write(b);
                synchronized (lock()) {
                    side.write(b);
                }
            }

            public void write(byte b[], int off, int len) throws IOException {
                base.write(b, off, len);
                synchronized (lock()) {
                    side.write(b, off, len);
                }
            }

            public void flush() throws IOException {
                base.flush();
                synchronized (lock()) {
                    side.flush();
                }
            }

            public void close() throws IOException {
                base.close();
                synchronized (lock()) {
                    side.close();
                }
            }
        };

        logger = new PrintStream(tee);
    }

    public void setSideOutputStream(OutputStream os) throws IOException {
        synchronized (lock()) {
            if(os==null) {
                os = unclaimed;
            } else {
                unclaimed.close();
                unclaimed.writeTo(os);
                File f = unclaimed.getFile();
                if (f!=null)    f.delete();

                unclaimed = newLog();
            }
            this.side = os;
        }
    }

    private DeferredFileOutputStream newLog() {
        return new DeferredFileOutputStream(10*1024, deferredFile);
    }

    /**
     * We need to be able to atomically write the buffered bits and then create a fresh {@link ByteArrayOutputStream},
     * when another thread (pipe I/O thread) is calling log.write().
     *
     * This locks controls the access and the write operation to {@link #side} (and since that can point to the same
     * object as {@link #unclaimed}, that access needs to be in the same lock, too.)
     */
    private Object lock() {
        return this;
    }

    public PrintStream getLogger() {
        return logger;
    }

    public PrintWriter error(String msg) {
        delegate.error(msg);
        return new PrintWriter(logger, true);
    }

    public PrintWriter error(String format, Object... args) {
        delegate.error(format,args);
        return new PrintWriter(logger, true);
    }

    public PrintWriter fatalError(String msg) {
        delegate.fatalError(msg);
        return new PrintWriter(logger, true);
    }

    public PrintWriter fatalError(String format, Object... args) {
        delegate.fatalError(format,args);
        return new PrintWriter(logger, true);
    }

    public void annotate(ConsoleNote ann) throws IOException {
        delegate.annotate(ann);
    }

    @Override
    public void hyperlink(String url, String text) throws IOException {
        delegate.hyperlink(url, text);
    }
}

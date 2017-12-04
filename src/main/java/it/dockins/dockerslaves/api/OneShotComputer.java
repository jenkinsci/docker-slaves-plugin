/*
 * The MIT License
 *
 *  Copyright (c) 2016, CloudBees, Inc.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 */
package it.dockins.dockerslaves.api;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerListener;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public abstract class OneShotComputer extends SlaveComputer {

    private final OneShotSlave slave;


    public OneShotComputer(OneShotSlave slave) {
        super(slave);
        this.slave = slave;
    }

    /**
     * Claim we are online so we get task assigned to the executor, so a ${@link Run} is created, then can actually
     * launch and report provisioning status in the build log.
     */
    @Override
    public boolean isOffline() {
        final OneShotSlave node = getNode();
        if (node != null) {
            if (node.hasProvisioningFailed()) return true;
            if (!node.hasExecutable()) return false;
        }

        return isActuallyOffline();
    }

    public boolean isActuallyOffline() {
        return super.isOffline();
    }

    @Override
    public @Nonnull OneShotSlave getNode() {
        return slave;
    }


    @Override
    protected void removeExecutor(Executor e) {
        terminate();
        super.removeExecutor(e);
    }

    protected void terminate() {
        try {
            Jenkins.getInstance().removeNode(slave);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * We only do support Linux docker images, so we assume UTF-8.
     * This let us wait for build log to be created and setup as a
     * ${@link hudson.model.BuildListener} before we actually launch
     */
    @Override
    public Charset getDefaultCharset() {
        return StandardCharsets.UTF_8;
    }

    // --- we need this to workaround hudson.slaves.SlaveComputer#taskListener being private
    private TaskListener listener;

    @Extension
    public final static ComputerListener COMPUTER_LISTENER = new ComputerListener() {
        @Override
        public void preLaunch(Computer c, TaskListener listener) throws IOException, InterruptedException {
            if (c instanceof OneShotComputer) {
                ((OneShotComputer) c).setListener(listener);
            }
        }
    };

    public void setListener(TaskListener listener) {
        this.listener = listener;
    }

    public TaskListener getListener() {
        return listener;
    }
}

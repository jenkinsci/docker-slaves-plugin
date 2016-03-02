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
package com.cloudbees.jenkins.plugins.dockerslaves.api;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerListener;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.nio.charset.Charset;

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
        if (getNode().hasProvisioningFailed()) return true;
        if (!getNode().hasExecutable()) return false;

        return isActuallyOffline();
    }

    public boolean isActuallyOffline() {
        return super.isOffline();
    }

    @Override
    public OneShotSlave getNode() {
        return slave;
    }

    @Extension
    public final static ComputerListener COMPUTER_LISTENER = new ComputerListener() {
        @Override
        public void preLaunch(Computer c, TaskListener listener) throws IOException, InterruptedException {
            if (c instanceof OneShotComputer) {
                ((OneShotComputer) c).getNode().setTeeSpongeTaskListener(listener);
            }
        }
    };

    @Override
    protected boolean isAlive() {
        if (getNode().hasExecutable()) {
            // #isAlive is used from removeExecutor to determine if executors should be created to replace a terminated one
            // We hook into this lifecycle implementation detail (sic) to get notified as the build completed
            terminate();
        }
        return super.isAlive();
    }

    protected void terminate() {
        try {
            Jenkins.getActiveInstance().removeNode(slave);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * ${@link Computer#getDefaultCharset()} is the first computer method used by
     * ${@link hudson.model.Run#execute(Run.RunExecution)} when a job is executed.
     * Relying on this implementation detail is fragile, but we don't really have a better
     * option yet.
     */
    @Override
    public Charset getDefaultCharset() {
        getNode().provision();
        return super.getDefaultCharset();
    }
}

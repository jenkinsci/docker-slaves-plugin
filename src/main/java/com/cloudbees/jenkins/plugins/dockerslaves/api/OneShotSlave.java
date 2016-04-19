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

import com.cloudbees.jenkins.plugins.dockerslaves.DockerComputer;
import com.cloudbees.jenkins.plugins.dockerslaves.TeeSpongeTaskListener;
import hudson.Extension;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.EphemeralNode;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A slave that is designed to be used only once, for a specific ${@link hudson.model.Run}, and as such has a life cycle
 * to fully match the Run's one.
 * <p>
 * Provisioning such a slave should be a lightweight process, so one can provision them at any time and concurrently
 * to match ${@link hudson.model.Queue} load. Typical usage is Docker container based Jenkins agents.
 * <p>
 * Actual launch of the Slave is postponed until a ${@link Run} is created, so we can have a 1:1 match between Run and
 * Executor lifecycle:
 * <ul>
 *     <li>dump the launch log in build log.</li>
 *     <li>mark the build as ${@link Result#NOT_BUILT} on launch failure.</li>
 *     <li>shut down and remove the Executor on build completion</li>
 * </ul>
 */
public abstract class OneShotSlave extends Slave implements EphemeralNode {
    /** Listener to log computer's launch and activity */
    protected transient TeeSpongeTaskListener teeSpongeTaskListener;

    /**
     * The ${@link Queue.Executable} associated to this OneShotSlave. By design, only one Run can be assigned, then slave is shut down.
     * This field is set as soon as the ${@link Queue.Executable} has been created.
     */
    private transient Queue.Executable executable;

    private boolean provisioningFailed = false;

    public OneShotSlave(String name, String nodeDescription, String remoteFS, String labelString) throws Descriptor.FormException, IOException {
        super(name, nodeDescription, remoteFS, 1, Mode.EXCLUSIVE, labelString, null, RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList());
        setLauncher(new DeferredComputerLauncher(new Callable<ComputerLauncher>() {
            @Override
            public ComputerLauncher call() throws Exception { return createRealComputerLauncher(); }
        }));
    }

    @Override
    public int getNumExecutors() {
        return 1;
    }

    public abstract ComputerLauncher createRealComputerLauncher();

    /*
     * Assign the ${@link ComputerLauncher} listener as the node is actually started, so we can pipe it to the
     * ${@link Run} log. We need this as we can't just use <code>getComputer().getListener()</code>
     *
     * @see DockerComputer#COMPUTER_LISTENER
     */
    public void setTeeSpongeTaskListener(TaskListener teeSpongeTaskListener) {
        try {
            final File log = File.createTempFile("one-shot", "log");

            // We use a "Tee+Sponge" TaskListener here as Run's log is created after computer has been first acceded
            // If this can be changed in core, we would just need a "Tee"
            this.teeSpongeTaskListener = new TeeSpongeTaskListener(teeSpongeTaskListener, log);
        } catch (IOException e) {
            e.printStackTrace(); // FIXME
        }
    }

    public TeeSpongeTaskListener getTeeSpongeTaskListener() {
        return teeSpongeTaskListener;
    }

    /*package*/  boolean hasExecutable() {
        return executable != null;
    }

    /*package*/  boolean hasProvisioningFailed() {
        return provisioningFailed;
    }

    @Override
    public OneShotComputer getComputer() {
        return (OneShotComputer) super.getComputer();
    }

    /**
     * Assign a ${@link Queue.Executable} to this OneShotSlave. By design, only one Queue.Executable can be assigned, then slave is shut down.
     * This method has to be called just as the ${@link Run} as been created. It run the actual launch of the executor
     * and collect it's log so we can pipe it to the Run's ${@link hudson.model.BuildListener} (which is created later).
     * <p>
     * Delaying launch of the executor until the Run is actually started allows to fail the build on launch failure,
     * so we have a strong 1:1 relation between a Run and it's Executor.
     */
    synchronized void provision() {
        if (executable != null) {
            // already provisioned
            return;
        }

        final Executor executor = Executor.currentExecutor();
        if (executor == null) {
            throw new IllegalStateException("running task without associated executor thread");
        }

        try {
            getLauncher().launch(this.getComputer(), teeSpongeTaskListener);

            if (getComputer().isActuallyOffline()) {
                provisionFailed(new IllegalStateException("Computer is offline after launch"));
            }
        } catch (Exception e) {
            provisionFailed(e);
        }
        executable = executor.getCurrentExecutable();
    }

    void provisionFailed(Exception cause) {
        if (executable instanceof Run) {
            ((Run) executable).setResult(Result.NOT_BUILT);
        }

        try {
            Jenkins.getInstance().removeNode(this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        throw new OneShotExecutorProvisioningException();
    }

    /**
     * Pipeline does not use the same mecanism to use nodes, so we also need to consider ${@link #createLauncher(TaskListener)}
     * as an event to determine first use of the slave.
     */
    @Override
    public Launcher createLauncher(TaskListener listener) {
        provision();
        return super.createLauncher(listener);
    }

    /**
     * We listen to loggers creation by ${@link Run}s so we can write the executor's launch log into build log.
     * Relying on this implementation detail is fragile, but we don't really have a better
     * option yet.
     */
    @Extension
    public static final ConsoleLogFilter LOG_FILTER = new ConsoleLogFilter() {

        @Override
        public OutputStream decorateLogger(Run run, OutputStream logger) throws IOException, InterruptedException {
            Computer computer = Executor.currentExecutor().getOwner();

            if (computer instanceof DockerComputer) {
                OneShotSlave slave = ((DockerComputer) computer).getNode();
                slave.teeSpongeTaskListener.setSideOutputStream(logger);
            }
            return logger;
        }
    };
}

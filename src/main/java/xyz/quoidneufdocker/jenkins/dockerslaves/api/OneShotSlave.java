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
package xyz.quoidneufdocker.jenkins.dockerslaves.api;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.EphemeralNode;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import java.io.IOException;
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

    /**
     * The ${@link Queue.Executable} associated to this OneShotSlave. By design, only one Run can be assigned, then slave is shut down.
     * This field is set as soon as the ${@link Queue.Executable} has been created.
     */
    private transient Queue.Executable executable;

    private final ComputerLauncher realLauncher;

    private boolean provisioningFailed = false;

    public OneShotSlave(String name, String nodeDescription, String remoteFS, String labelString, ComputerLauncher launcher) throws Descriptor.FormException, IOException {
        super(name, nodeDescription, remoteFS, 1, Mode.EXCLUSIVE, labelString, NOOP_LAUNCHER, RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList());
        this.realLauncher = launcher;
    }

    @Override
    public int getNumExecutors() {
        return 1;
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
     * and use Run's ${@link hudson.model.BuildListener} as computer launcher listener to collect the startup log as part of the build.
     * <p>
     * Delaying launch of the executor until the Run is actually started allows to fail the build on launch failure,
     * so we have a strong 1:1 relation between a Run and it's Executor.
     * @param listener
     */
    synchronized void provision(TaskListener listener) {
        if (executable != null) {
            // already provisioned
            return;
        }

        final Executor executor = Executor.currentExecutor();
        if (executor == null) {
            throw new IllegalStateException("running task without associated executor thread");
        }

        try {
            realLauncher.launch(this.getComputer(), listener);

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
        provision(listener);
        return super.createLauncher(listener);
    }

    /**
     * We listen to loggers creation by ${@link Run}s so we can write the executor's launch log into build log.
     * Relying on this implementation detail is fragile, but we don't really have a better
     * option yet.
     */
    @Extension
    public final static RunListener RUN_LISTENER = new RunListener<Run>() {
        @Override
        public void onStarted(Run run, TaskListener listener) {
            Computer c = Computer.currentComputer();
            if (c instanceof OneShotComputer) {
                final OneShotSlave node = ((OneShotComputer) c).getNode();
                node.provision(listener);
            }
        }
    };

    private static final ComputerLauncher NOOP_LAUNCHER = new ComputerLauncher() {
        @Override
        public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
            //noop;
        }
    };
}

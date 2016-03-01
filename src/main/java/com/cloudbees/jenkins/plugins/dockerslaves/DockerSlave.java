/*
 * The MIT License
 *
 *  Copyright (c) 2015, CloudBees, Inc.
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

package com.cloudbees.jenkins.plugins.dockerslaves;

import hudson.Extension;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Environment;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.EphemeralNode;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;

/**
 * An ${@link EphemeralNode} using docker containers to host the build processes.
 * Slave is dedicated to a specific ${@link Job}, and even better to a specific build, but when this class
 * is created the build does not yet exists due to Jenkins lifecycle.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerSlave extends AbstractCloudSlave implements EphemeralNode {

    private final DockerProvisionerFactory provisionerFactory;

    private final Queue.Item item;

    /** Listener to log computer's launch and activity */
    private transient TeeSpongeTaskListener computerListener;

    public DockerSlave(String name, String nodeDescription, String labelString, Queue.Item item, DockerProvisionerFactory provisionerFactory) throws Descriptor.FormException, IOException {
        // TODO would be better to get notified when the build start, and get the actual build ID. But can't find the API for that
        super(name, nodeDescription, "/home/jenkins", 1, Mode.EXCLUSIVE, labelString,
                new DockerComputerLauncher(),
                RetentionStrategy.NOOP, // Slave is stopped on completion see DockerComputer.taskCompleted
                Collections.<NodeProperty<?>>emptyList());
        this.provisionerFactory = provisionerFactory;
        this.item = item;
    }

    public DockerComputer createComputer() {
        return new DockerComputer(this, provisionerFactory, item);
    }

    @Override
    public Node asNode() {
        return this;
    }

    /*
     * Assign the ${@link ComputerLauncher} listener as the node is actually started, so we can pipe it to the
     * ${@link Run} log. We need this as we can't just use <code>getComputer().getListener()</code>
     *
     * @see DockerComputer#COMPUTER_LISTENER
     */
    public void setComputerListener(TaskListener computerListener) {
        try {
            final File log = File.createTempFile("one-shot", "log");

            // We use a "Tee+Sponge" TaskListener here as Run's log is created after computer has been first acceded
            // If this can be changed in core, we would just need a "Tee"
            this.computerListener = new TeeSpongeTaskListener(computerListener, log);
        } catch (IOException e) {
            e.printStackTrace(); // FIXME
        }
    }

    public TeeSpongeTaskListener getComputerListener() {
        return computerListener;
    }

    @Override
    public DockerComputer getComputer() {
        return (DockerComputer) super.getComputer();
    }

    /**
     * We listen to loggers creation by ${@link Run}s so we can write the executor's launch log into build log.
     */
    @Extension
    public static final ConsoleLogFilter LOG_FILTER = new ConsoleLogFilter() {

        @Override
        public OutputStream decorateLogger(AbstractBuild run, OutputStream logger) throws IOException, InterruptedException {
            Computer computer = Executor.currentExecutor().getOwner();

            if (computer instanceof DockerComputer) {
                ((DockerComputer) computer).getNode().computerListener.setSideOutputStream(logger);
            }

            return logger;
        }
    };

    /**
     * Create a custom ${@link Launcher} which relies on plil <code>docker run</code> to start a new process
     */
    @Override
    public Launcher createLauncher(TaskListener listener) {
        DockerComputer c = getComputer();
        if (c == null) {
            listener.error("Issue with creating launcher for slave " + name + ".");
            throw new IllegalStateException("Can't create a launcher if computer is gone.");
        }

        return new DockerLauncher(listener, c.getChannel(), c.isUnix(), c.getProvisioner()).decorateFor(this);
    }

    /**
     * This listener get notified as the build is going to start. We use it to remove the temporary unique Label we
     * created to ensure exclusive executor usage, but which would pollute Jenkins labels set.
     */
    @Extension
    public static class DockerSlaveRunListener extends RunListener<AbstractBuild> {
        @Override
        public Environment setUpEnvironment(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
            Computer c = Computer.currentComputer();
            if (c instanceof DockerComputer) {
                build.addAction(((DockerComputer) c).getProvisioner().getContext());
                Action temporaryLabel = build.getAction(DockerLabelAssignmentAction.class);
                build.getActions().remove(temporaryLabel);
            }
            return new Environment() {};
        }

    }

    /**
     * This listener get notified as the build completes the SCM checkout. We use this event to determine when the
     * build has to switch from SCM docker images to Build images to host build steps execution.
     */
    @Extension
    public static class DockerSlaveSCMListener extends SCMListener {
        @Override
        public void onChangeLogParsed(Run<?, ?> build, SCM scm, TaskListener listener, ChangeLogSet<?> changelog) throws Exception {
            final JobBuildsContainersContext action = build.getAction(JobBuildsContainersContext.class);
            if (action != null) {
                action.onScmChekoutCompleted(build, listener);
            }
        }

    }
    public void _terminate(TaskListener task){
        //Do nothing, although it would be nice to clean a bit docker images

    }
}

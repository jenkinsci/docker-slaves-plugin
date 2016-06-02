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

package it.dockins.dockerslaves;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.slaves.EphemeralNode;
import it.dockins.dockerslaves.api.OneShotSlave;

import java.io.IOException;

/**
 * An ${@link EphemeralNode} using docker containers to host the build processes.
 * Slave is dedicated to a specific ${@link Job}, and even better to a specific build, but when this class
 * is created the build does not yet exists due to Jenkins lifecycle.
 */
public class DockerSlave extends OneShotSlave {

    public static final String SLAVE_ROOT = "/home/jenkins/";

    private final DockerProvisionerFactory provisionerFactory;

    public DockerSlave(String name, String nodeDescription, String labelString, DockerProvisionerFactory provisionerFactory) throws Descriptor.FormException, IOException {
        // TODO would be better to get notified when the build start, and get the actual build ID. But can't find the API for that
        super(name.replaceAll("/", " » "), nodeDescription, SLAVE_ROOT, labelString, new DockerComputerLauncher());
        this.provisionerFactory = provisionerFactory;
    }

    public DockerComputer createComputer() {
        return new DockerComputer(this, provisionerFactory);
    }

    @Override
    public DockerSlave asNode() {
        return this;
    }

    @Override
    public DockerComputer getComputer() {
        return (DockerComputer) super.getComputer();
    }

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

        super.createLauncher(listener);
        return new DockerLauncher(listener, c.getChannel(), c.isUnix(), c.getProvisioner()).decorateFor(this);
    }

    /**
     * This listener get notified as the build is going to start.
     */
    @Extension
    public static class DockerSlaveRunListener extends RunListener<Run> {

        @Override
        public void onStarted(Run run, TaskListener listener) {
            Computer c = Computer.currentComputer();
            if (c instanceof DockerComputer) {
                run.addAction(((DockerComputer) c).getProvisioner().getContext());
                // TODO remove DockerSlaveAssignmentAction
            }
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
}

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
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Environment;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.EphemeralNode;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.util.Collections;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerSlave extends AbstractCloudSlave implements EphemeralNode {

    private final Job job;

    public DockerSlave(Job job, String labelString) throws Descriptor.FormException, IOException {
        // TODO would be better to get notified when the build start, and get the actual build ID. But can't find the API for that
        super("Container for " +job.getName() + "#" + job.getNextBuildNumber(), "Container slave for building " + job.getFullName(),
                "/home/jenkins", 1, Mode.EXCLUSIVE, labelString,
                new DockerComputerLauncher(),
                RetentionStrategy.NOOP, // Slave is stopped on completion see DockerComputer.taskCompleted
                Collections.<NodeProperty<?>>emptyList());
        this.job = job;
    }

    public DockerComputer createComputer() {
        return new DockerComputer(this, job);
    }

    public Job getJob() {
        return job;
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
    }

    @Override
    public Node asNode() {
        return this;
    }

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

    @Override
    public Launcher createLauncher(TaskListener listener) {
        SlaveComputer c = getComputer();
        if (c == null) {
            listener.error("Issue with creating launcher for slave " + name + ".");
            throw new IllegalStateException("Can't create a launcher if computer is gone.");
        } else {
            DockerComputer dc = (DockerComputer) c;
            try {
                dc.connectJobListener(listener);
            } catch (IOException e) {
                e.printStackTrace(listener.getLogger());
            }
            return new DockerLauncher(listener, c.getChannel(), c.isUnix(), dc.getProvisioner()).decorateFor(this);
        }
    }
}

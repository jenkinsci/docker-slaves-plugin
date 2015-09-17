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

package com.cloudbees.jenkins.plugins.containerslaves;

import hudson.model.Job;
import hudson.model.Label;
import hudson.model.Queue;
import hudson.model.TaskListener;

/**
 */
public class DockerEngine {

    protected final String host;

    /**
     * Base Container image name. Jenkins Remoting will be launched in it.
     */
    protected final String remotingContainerImageName;

    /**
     * Base Build image name. Build commands will run on it.
     */
    protected final String buildContainerImageName;

    public DockerEngine(String buildContainerImageName) {
        host = "TODO";
        remotingContainerImageName = "jenkinsci/slave";
        this.buildContainerImageName = buildContainerImageName;
    }

    public DockerEngine(String host, String remotingContainerImageName, String buildContainerImageName) {
        this.host = host;
        this.remotingContainerImageName = remotingContainerImageName;
        this.buildContainerImageName = buildContainerImageName;
    }

    public DockerLabelAssignmentAction createLabelAssignmentAction(final Queue.BuildableItem bi) {
        final String id = Long.toHexString(System.nanoTime());
        final Label label = Label.get("docker_" + id);
        return new DockerLabelAssignmentAction(label);
    }

    public DockerProvisioner buildProvisioner(Job job, TaskListener listener) {
        DockerBuildContext context = new DockerBuildContext(job, remotingContainerImageName, buildContainerImageName);

        return new DockerProvisioner(context, new DockerDriver(host), listener);
    }

}

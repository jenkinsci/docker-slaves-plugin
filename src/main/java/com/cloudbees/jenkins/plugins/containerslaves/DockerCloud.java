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

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * {@link Cloud} implementation designed to launch a set of containers (aka "pod") to establish a Jenkins executor.
 *
 */
public class DockerCloud extends Cloud {

    /**
     * Base Build image name. Build commands will run on it.
     */
    private final String defaultBuildContainerImageName;

    /**
     * Remoting Container image name. Jenkins Remoting will be launched in it.
     */
    private final String remotingContainerImageName;

    private final DockerServerEndpoint dockerHost;

    @DataBoundConstructor
    public DockerCloud(String name, String defaultBuildContainerImageName, String remotingContainerImageName, DockerServerEndpoint dockerHost) {
        super(name);
        this.defaultBuildContainerImageName = defaultBuildContainerImageName;
        this.dockerHost = dockerHost;
        this.remotingContainerImageName = StringUtils.isEmpty(remotingContainerImageName) ? "jenkinsci/slave" : remotingContainerImageName;
    }

    public String getDefaultBuildContainerImageName() {
        return defaultBuildContainerImageName;
    }

    public String getRemotingContainerImageName() {
        return remotingContainerImageName;
    }

    public DockerServerEndpoint getDockerHost() {
        return dockerHost;
    }

    public DockerLabelAssignmentAction createLabelAssignmentAction(final Queue.BuildableItem bi) {
        final String id = Long.toHexString(System.nanoTime());
        final Label label = Label.get("docker_" + id);
        return new DockerLabelAssignmentAction(label);
    }

    public DockerJobContainersProvisioner buildProvisioner(Job job, TaskListener slaveListener) {
        return new DockerJobContainersProvisioner(job, new DockerDriver(dockerHost), slaveListener, getDefaultBuildContainerImageName(job), remotingContainerImageName);
    }

    private String getDefaultBuildContainerImageName(Job job) {
        // TODO iterate over job.getParent() to find configuration for a default container image at folder level
        return defaultBuildContainerImageName;
    }

    @Override
    /**
     * Not just considering delay for NodeProvisioner to call this, we also can't create the Build Pod here as we
     * just don't know which {@link Job} it target, to retrieve the set of container images to launch.
     */
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        return Collections.EMPTY_LIST;
    }

    public static @Nullable DockerCloud getCloud() {
        for (Cloud cloud : Jenkins.getInstance().clouds) {
            if (cloud instanceof DockerCloud) {
                return (DockerCloud) cloud;
            }
        }

        return null;
    }

    @Override
    public boolean canProvision(Label label) {
        return false;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        @Override
        public String getDisplayName() {
            return "Containers Cloud";
        }

    }
}

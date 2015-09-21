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
import hudson.Plugin;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * {@link Cloud} implementation designed to launch a set of containers (aka "pod") to establish a Jenkins executor.
 *
 */
public class DockerSlaves extends Plugin implements Describable<DockerSlaves> {

    /**
     * Base Build image name. Build commands will run on it.
     */
    private String defaultBuildContainerImageName;

    /**
     * Remoting Container image name. Jenkins Remoting will be launched in it.
     */
    private final String remotingContainerImageName = System.getProperty("com.cloudbees.jenkins.plugins.containerslaves.DockerSlaves.image", "jenkinsci/slave");

    private DockerServerEndpoint dockerHost;

    @Override
    public void configure(StaplerRequest req, JSONObject formData) throws IOException, ServletException, Descriptor.FormException {
        req.bindJSON(this, formData);
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

    public DockerJobContainersProvisioner buildProvisioner(Job job, TaskListener slaveListener) throws IOException, InterruptedException {
        return new DockerJobContainersProvisioner(job, dockerHost, slaveListener, getDefaultBuildContainerImageName(job), remotingContainerImageName);
    }

    private String getDefaultBuildContainerImageName(Job job) {
        // TODO iterate over job.getParent() to find configuration for a default container image at folder level
        return defaultBuildContainerImageName;
    }

    public static DockerSlaves get() {
        return (DockerSlaves) Jenkins.getInstance().getPlugin("docker-slaves");
    }

    @Override
    public Descriptor<DockerSlaves> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(DockerSlaves.class);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerSlaves> {

        @Override
        public String getDisplayName() {
            return "Docker Slaves";
        }
    }
}

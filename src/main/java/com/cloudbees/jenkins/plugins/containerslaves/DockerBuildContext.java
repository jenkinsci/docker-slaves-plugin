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

import hudson.model.BuildBadgeAction;
import hudson.model.Job;

public class DockerBuildContext implements BuildBadgeAction {

    protected final Job job;

    protected final String remotingContainerImageName;

    protected final String buildContainerName;

    String remotingContainerId;

    String buildContainerId;

    public DockerBuildContext(Job job, String remotingContainerImageName, String buildContainerName) {
        this.job = job;
        this.remotingContainerImageName = remotingContainerImageName;
        this.buildContainerName = buildContainerName;
    }

    public Job getJob() {
        return job;
    }

    public String getRemotingContainerImageName() {
        return remotingContainerImageName;
    }

    String getRemotingContainerId() {
        return remotingContainerId;
    }

    String getBuildContainerId() {
        return buildContainerId;
    }

    public void setRemotingContainerId(String remotingContainerId) {
        this.remotingContainerId = remotingContainerId;
    }

    public void setBuildContainerId(String buildContainerId) {
        this.buildContainerId = buildContainerId;
    }

    @Override
    public String getIconFileName() {
        return "/plugin/container-slaves/images/24x24/docker-logo.png";
    }

    @Override
    public String getDisplayName() {
        return "Docker Build Context";
    }

    @Override
    public String getUrlName() {
        return "docker";
    }

    public String getBuildContainerName() {
        return buildContainerName;
    }
}

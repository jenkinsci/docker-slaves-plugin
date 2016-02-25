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

import hudson.model.AbstractBuild;
import hudson.model.BuildBadgeAction;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JobBuildsContainersContext implements BuildBadgeAction {

    protected ContainerInstance remotingContainer;

    protected List<ContainerInstance> buildContainers = new ArrayList<ContainerInstance>();

    protected Map<String, ContainerInstance> sideContainers = new HashMap<String, ContainerInstance>();

    /**
     * Flag to indicate the SCM checkout build phase is running.
     */
    private transient boolean preScm;

    public JobBuildsContainersContext() {
        preScm = true;
    }

    public JobBuildsContainersContext(boolean preScm) {
        this.preScm = preScm;
    }

    protected void onScmChekoutCompleted(Run<?, ?> build, TaskListener listener) {
        preScm = false;
    }

    public ContainerInstance getRemotingContainer() {
        return remotingContainer;
    }

    public List<ContainerInstance> getBuildContainers() {
        return buildContainers;
    }

    public boolean isPreScm() {
        return preScm;
    }

    public void setRemotingContainer(ContainerInstance remotingContainer) {
        this.remotingContainer = remotingContainer;
    }

    public Map<String, ContainerInstance> getSideContainers() {
        return sideContainers;
    }

    @Override
    public String getIconFileName() {
        return "/plugin/docker-slaves/images/24x24/docker-logo.png";
    }

    @Override
    public String getDisplayName() {
        return "Docker Build Context";
    }

    @Override
    public String getUrlName() {
        return "docker";
    }
}

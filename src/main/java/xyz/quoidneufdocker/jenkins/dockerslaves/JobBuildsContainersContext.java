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

package xyz.quoidneufdocker.jenkins.dockerslaves;

import hudson.model.BuildBadgeAction;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.util.HashMap;
import java.util.Map;

public class JobBuildsContainersContext implements BuildBadgeAction {

    protected ContainerInstance remotingContainer;

    protected ContainerInstance buildContainer;

    protected ContainerInstance scmContainer;

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

    public ContainerInstance getBuildContainer() {
        return buildContainer;
    }

    public boolean isPreScm() {
        return preScm;
    }

    public void setRemotingContainer(ContainerInstance remotingContainer) {
        this.remotingContainer = remotingContainer;
    }

    public void setBuildContainer(ContainerInstance buildContainer) {
        this.buildContainer = buildContainer;
    }

    public ContainerInstance getScmContainer() {
        return scmContainer;
    }

    public void setScmContainer(ContainerInstance scmContainer) {
        this.scmContainer = scmContainer;
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

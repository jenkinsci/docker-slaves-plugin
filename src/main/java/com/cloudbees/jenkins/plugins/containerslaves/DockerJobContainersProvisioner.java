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

import hudson.Launcher;
import hudson.Proc;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.CommandLauncher;
import hudson.slaves.SlaveComputer;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;

/**
 * Provision {@link DockerSlave} to provide queued task an executor.
 */
public class DockerJobContainersProvisioner {

    protected final Job job;

    private final JobBuildsContainersContext context;

    private final TaskListener slaveListener;

    private final DockerDriver driver;

    private final Launcher localLauncher;

    public DockerJobContainersProvisioner(Job job, DockerDriver driver, TaskListener slaveListener, String defaultBuildContainerImageName, String remotingContainerImageName) {
        this.job = job;
        this.slaveListener = slaveListener;
        this.driver = driver;
        localLauncher = new Launcher.LocalLauncher(slaveListener);

        String buildContainerImageName = defaultBuildContainerImageName;
        JobBuildsContainersDefinition jobBuildsContainersDefinition = (JobBuildsContainersDefinition) job.getProperty(JobBuildsContainersDefinition.class);

        if (StringUtils.isNotEmpty(jobBuildsContainersDefinition.getBuildHostImage())) {
            buildContainerImageName = jobBuildsContainersDefinition.getBuildHostImage();
        }

        context = new JobBuildsContainersContext(remotingContainerImageName, buildContainerImageName, jobBuildsContainersDefinition.getSideContainers());

        // reuse previous remoting container to retrieve workspace
        Run lastBuild = job.getBuilds().getLastBuild();
        if (lastBuild != null) {
            JobBuildsContainersContext previousContext = (JobBuildsContainersContext) lastBuild.getAction(JobBuildsContainersContext.class);
            if (previousContext.getRemotingContainer() != null) {
                context.getRemotingContainer().setId(previousContext.getRemotingContainer().getId());
            }
        }
    }

    public JobBuildsContainersContext getContext() {
        return context;
    }

    public void prepareRemotingContainer()  throws IOException, InterruptedException {
        // if remoting container already exists, we reuse it
        if (context.getRemotingContainer() != null) {
            if (driver.hasContainer(localLauncher, context.getRemotingContainer())) {
                return;
            }
        }
        driver.createRemotingContainer(localLauncher, context.getRemotingContainer());
    }

    public void launchRemotingContainer(final SlaveComputer computer, TaskListener listener) {
        CommandLauncher launcher = new CommandLauncher("docker start -ia " + context.getRemotingContainer().getId());
        launcher.launch(computer, listener);
    }

    public void prepareBuildCommandLaunch(Launcher.ProcStarter starter, ContainerInstance buildContainer) throws IOException, InterruptedException {
        driver.createBuildContainer(localLauncher, buildContainer, context.getRemotingContainer(), starter);
    }

    public Proc launchBuildCommand(Launcher.ProcStarter starter, ContainerInstance buildContainer) throws IOException, InterruptedException {
        return driver.runContainer(localLauncher, buildContainer.getId()).stdout(starter.stdout()).start();
    }

    public void launchSideContainers(DockerComputer computer, TaskListener listener) throws IOException, InterruptedException {
        for (ContainerInstance instance : context.getSideContainers()) {
            driver.launchSideContainer(localLauncher, instance, context.getRemotingContainer());
        }
    }

    public void clean() throws IOException, InterruptedException {
        for (ContainerInstance instance : context.getSideContainers()) {
            driver.removeContainer(localLauncher, instance);
        }

        for (ContainerInstance instance : context.getBuildContainers()) {
            driver.removeContainer(localLauncher, instance);
        }
    }
}
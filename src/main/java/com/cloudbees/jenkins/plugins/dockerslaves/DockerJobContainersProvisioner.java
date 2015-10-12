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

import hudson.Launcher;
import hudson.Proc;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.CommandLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;

import java.io.IOException;

/**
 * Provision {@link ContainerInstance}s based on ${@link JobBuildsContainersDefinition} to provide a queued task
 * an executor.
 */
public class DockerJobContainersProvisioner {

    private final JobBuildsContainersContext context;

    private final TaskListener slaveListener;

    private final DockerDriver driver;

    private final Launcher localLauncher;

    private final JobBuildsContainersDefinition spec;

    private final String remotingImage;
    private final String scmImage;
    private String buildImage;

    public DockerJobContainersProvisioner(Job job, DockerServerEndpoint dockerHost, TaskListener slaveListener, String remotingImage, String scmImage) throws IOException, InterruptedException {
        this.slaveListener = slaveListener;
        this.driver = new DockerDriver(dockerHost, job);
        localLauncher = new Launcher.LocalLauncher(slaveListener);
        spec = (JobBuildsContainersDefinition) job.getProperty(JobBuildsContainersDefinition.class);

        this.remotingImage = remotingImage;
        this.scmImage = scmImage;
        context = new JobBuildsContainersContext();

        // TODO define a configurable volume strategy to retrieve a (maybe persistent) workspace
        // could rely on docker volume driver
        // in the meantime, we just rely on previous build's remoting container as a data volume container

        // reuse previous remoting container to retrieve workspace
        Run lastBuild = job.getBuilds().getLastBuild();
        if (lastBuild != null) {
            JobBuildsContainersContext previousContext = (JobBuildsContainersContext) lastBuild.getAction(JobBuildsContainersContext.class);
            if (previousContext != null && previousContext.getRemotingContainer() != null) {
                context.setRemotingContainer(previousContext.getRemotingContainer());
            }
        }
    }

    public JobBuildsContainersContext getContext() {
        return context;
    }

    public void prepareRemotingContainer() throws IOException, InterruptedException {
        // if remoting container already exists, we reuse it
        if (context.getRemotingContainer() != null) {
            if (driver.hasContainer(localLauncher, context.getRemotingContainer().getId())) {
                return;
            }
        }
        final ContainerInstance remotingContainer = driver.createRemotingContainer(localLauncher, remotingImage);
        context.setRemotingContainer(remotingContainer);
    }

    public void launchRemotingContainer(final SlaveComputer computer, TaskListener listener) {
        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("start")
                .add("-ia", context.getRemotingContainer().getId());
        driver.prependArgs(args);
        CommandLauncher launcher = new CommandLauncher(args.toString(), driver.dockerEnv.env());
        launcher.launch(computer, listener);
    }

    public BuildContainer newBuildContainer(Launcher.ProcStarter starter, TaskListener listener) throws IOException, InterruptedException {
        if (!context.isPreScm() && spec.getSideContainers().size() > 0 && context.getSideContainers().size() == 0) {
            // In a ideal world we would run side containers when DockerSlave.DockerSlaveSCMListener detect scm checkout completed
            // but then we don't have a ProcStarter reference. So do it first time a command is ran during the build
            // after scm checkout completed. We detect this is the first time as spec > context
            createSideContainers(starter, listener);
        }

        if (context.isPreScm()) {
            return newBuildContainer(starter, scmImage);
        } else {
            if (buildImage == null) buildImage = spec.getBuildHostImage().getImage(driver, starter, listener);
            return newBuildContainer(starter, buildImage);
        }
    }

    private void createSideContainers(Launcher.ProcStarter starter, TaskListener listener) throws IOException, InterruptedException {
        for (SideContainerDefinition definition : spec.getSideContainers()) {
            final String name = definition.getName();
            final String image = definition.getSpec().getImage(driver, starter, listener);
            listener.getLogger().println("Starting " + name + " container");
            ContainerInstance container = new ContainerInstance(image);
            context.getSideContainers().put(name, container);
            driver.launchSideContainer(localLauncher, container, context.getRemotingContainer());
        }
    }

    private BuildContainer newBuildContainer(Launcher.ProcStarter procStarter, String buildImage) {
        final ContainerInstance c = new ContainerInstance(context.isPreScm() ? scmImage : buildImage);
        context.getBuildContainers().add(c);
        return new BuildContainer(c, procStarter);
    }

    public void createBuildContainer(BuildContainer buildContainer) throws IOException, InterruptedException {
        driver.createBuildContainer(localLauncher, buildContainer.instance, context.getRemotingContainer(), buildContainer.procStarter, spec.getCreateOptions());
    }

    public Proc startBuildContainer(BuildContainer buildContainer) throws IOException, InterruptedException {
        return driver.startContainer(localLauncher, buildContainer.instance.getId(), buildContainer.procStarter.stdout());
    }

    public void clean() throws IOException, InterruptedException {
        for (ContainerInstance instance : context.getSideContainers().values()) {
            driver.removeContainer(localLauncher, instance);
        }

        for (ContainerInstance instance : context.getBuildContainers()) {
            driver.removeContainer(localLauncher, instance);
        }

        driver.close();
    }

    public class BuildContainer {
        final ContainerInstance instance;
        final Launcher.ProcStarter procStarter;

        protected BuildContainer(ContainerInstance instance, Launcher.ProcStarter procStarter) {
            this.instance = instance;
            this.procStarter = procStarter;
        }

        public String getId() {
            return instance.getId();
        }

        public String getImageName() {
            return instance.getImageName();
        }
    }
}

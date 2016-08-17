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

import hudson.model.Job;
import it.dockins.dockerslaves.spi.DockerDriver;
import it.dockins.dockerslaves.spec.ContainerSetDefinition;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.TaskListener;
import hudson.slaves.SlaveComputer;
import it.dockins.dockerslaves.spec.SideContainerDefinition;

import java.io.IOException;
import java.util.Collections;

/**
 * Provision {@link ContainerInstance}s based on ${@link ContainerSetDefinition} to provide a queued task
 * an executor.
 */
public class DockerProvisioner {

    protected final JobBuildsContainersContext context;

    protected final TaskListener slaveListener;

    protected final DockerDriver driver;

    protected final Launcher launcher;

    protected final ContainerSetDefinition spec;

    protected final String remotingImage;

    protected final String scmImage;

    public DockerProvisioner(JobBuildsContainersContext context, TaskListener slaveListener, DockerDriver driver, Job job, ContainerSetDefinition spec, String remotingImage, String scmImage) throws IOException, InterruptedException {
        this.context = context;
        this.slaveListener = slaveListener;
        this.driver = driver;
        this.launcher = new Launcher.LocalLauncher(slaveListener);
        this.spec = spec;
        this.remotingImage = remotingImage;
        this.scmImage = scmImage;

        // Sanity check
        driver.serverVersion(launcher);
    }

    public JobBuildsContainersContext getContext() {
        return context;
    }

    public void prepareRemotingContainer(TaskListener listener) throws IOException, InterruptedException {
        // if remoting container already exists, we reuse it
        if (context.getRemotingContainer() != null) {
            if (driver.hasContainer(launcher, context.getRemotingContainer().getId())) {
                return;
            }
        }

        String volume = context.getWorkdirVolume();
        if (!driver.hasVolume(launcher, volume)) {
            volume = driver.createVolume(launcher, "local", Collections.EMPTY_LIST);
            context.setWorkdirVolume(volume);
        }

        final ContainerInstance remotingContainer = driver.createRemotingContainer(launcher, remotingImage, volume);
        context.setRemotingContainer(remotingContainer);
    }

    public void launchRemotingContainer(final SlaveComputer computer, TaskListener listener) {
        driver.launchRemotingContainer(computer, listener, context.getRemotingContainer());
    }

    public ContainerInstance launchBuildContainer(Launcher.ProcStarter starter, TaskListener listener) throws IOException, InterruptedException {
        if (spec.getSideContainers().size() > 0 && context.getSideContainers().size() == 0) {
            // In a ideal world we would run side containers when DockerSlave.DockerSlaveSCMListener detect scm checkout completed
            // but then we don't have a ProcStarter reference. So do it first time a command is ran during the build
            // after scm checkout completed. We detect this is the first time as spec > context
            createSideContainers(starter, listener);
        }

        String buildImage = spec.getBuildHostImage().getImage(driver, starter, listener);
        final ContainerInstance buildContainer = driver.createAndLaunchBuildContainer(launcher, buildImage, context.getRemotingContainer());
        context.setBuildContainer(buildContainer);
        return buildContainer;
    }

    public ContainerInstance launchScmContainer() throws IOException, InterruptedException {
        final ContainerInstance scmContainer = driver.createAndLaunchBuildContainer(launcher, scmImage, context.getRemotingContainer());
        context.setBuildContainer(scmContainer);
        return scmContainer;
    }

    private void createSideContainers(Launcher.ProcStarter starter, TaskListener listener) throws IOException, InterruptedException {
        for (SideContainerDefinition definition : spec.getSideContainers()) {
            final String name = definition.getName();
            final String image = definition.getSpec().getImage(driver, starter, listener);
            listener.getLogger().println("Starting " + name + " container");
            ContainerInstance container = new ContainerInstance(image);
            context.getSideContainers().put(name, container);
            driver.launchSideContainer(launcher, container, context.getRemotingContainer());
        }
    }

    public Proc launchBuildProcess(Launcher.ProcStarter procStarter, TaskListener listener) throws IOException, InterruptedException {
        ContainerInstance targetContainer = null;

        if (context.isPreScm()) {
            targetContainer = context.getScmContainer();
            if (targetContainer == null) {
                targetContainer = launchScmContainer();
            }
        } else {
            targetContainer = context.getBuildContainer();
            if (targetContainer == null) {
                targetContainer = launchBuildContainer(procStarter, listener);
            }
        }

        return driver.execInContainer(launcher, targetContainer.getId(), procStarter);
    }

    public void clean() throws IOException, InterruptedException {
        for (ContainerInstance instance : context.getSideContainers().values()) {
            driver.removeContainer(launcher, instance);
        }

        if (context.getBuildContainer() != null) {
            driver.removeContainer(launcher, context.getBuildContainer());
        }

        if (context.getScmContainer() != null) {
            driver.removeContainer(launcher, context.getScmContainer());
        }

        driver.close();
    }
}
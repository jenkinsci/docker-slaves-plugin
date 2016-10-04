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
import it.dockins.dockerslaves.spec.ContainerDefinition;
import it.dockins.dockerslaves.spi.DockerDriver;
import it.dockins.dockerslaves.spec.ContainerSetDefinition;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.TaskListener;
import hudson.slaves.SlaveComputer;
import it.dockins.dockerslaves.spec.SideContainerDefinition;
import it.dockins.dockerslaves.spi.DockerProvisioner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Provision {@link Container}s based on ${@link ContainerSetDefinition} to provide a queued task
 * an executor.
 */
public class DefaultDockerProvisioner extends DockerProvisioner {

    protected final ContainersContext context;

    protected final DockerDriver driver;

    protected final ContainerSetDefinition spec;

    protected final String remotingImage;

    protected final String scmImage;


    public DefaultDockerProvisioner(ContainersContext context, DockerDriver driver, Job job, ContainerSetDefinition spec, String remotingImage, String scmImage) throws IOException, InterruptedException {
        this.context = context;
        this.driver = driver;
        this.spec = spec;
        this.remotingImage = remotingImage;
        this.scmImage = scmImage;
    }

    @Override
    public ContainersContext getContext() {
        return context;
    }

    @Override
    public Container launchRemotingContainer(final SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        // if remoting container already exists, we reuse it
        final Container existing = context.getRemotingContainer();
        if (existing != null) {
            if (driver.hasContainer(listener, existing.getId())) {
                return existing;
            }
        }

        String volume = context.getWorkdirVolume();
        if (!driver.hasVolume(listener, volume)) {
            volume = driver.createVolume(listener);
            context.setWorkdirVolume(volume);
        }

        final Container remotingContainer = driver.launchRemotingContainer(listener, remotingImage, volume, computer);
        context.setRemotingContainer(remotingContainer);
        return remotingContainer;
    }

    @Override
    public Container launchBuildContainers(Launcher.ProcStarter starter, TaskListener listener) throws IOException, InterruptedException {
        if (spec.getSideContainers().size() > 0 && context.getSideContainers().size() == 0) {
            // In a ideal world we would run side containers when DockerSlave.DockerSlaveSCMListener detect scm checkout completed
            // but then we don't have a ProcStarter reference. So do it first time a command is ran during the build
            // after scm checkout completed. We detect this is the first time as spec > context
            createSideContainers(starter, listener);
        }

        final ContainerDefinition build = spec.getBuildHostImage();
        String buildImage = build.getImage(driver, starter, listener);
        final List<String> mounts = build.getMounts();
        final Container buildContainer = driver.launchBuildContainer(listener, buildImage, context.getRemotingContainer(), mounts);
        context.setBuildContainer(buildContainer);
        return buildContainer;
    }

    @Override
    public Container launchScmContainer(TaskListener listener) throws IOException, InterruptedException {
        final Container scmContainer = driver.launchBuildContainer(listener, scmImage, context.getRemotingContainer(), Collections.EMPTY_LIST);
        context.setBuildContainer(scmContainer);
        return scmContainer;
    }

    private void createSideContainers(Launcher.ProcStarter starter, TaskListener listener) throws IOException, InterruptedException {
        for (SideContainerDefinition definition : spec.getSideContainers()) {
            final String name = definition.getName();
            final ContainerDefinition sidecar = definition.getSpec();
            final String image = sidecar.getImage(driver, starter, listener);
            final List<String> mounts = sidecar.getMounts();
            listener.getLogger().println("Starting " + name + " container");
            Container container = driver.launchSideContainer(listener, image, context.getRemotingContainer(), mounts);
            context.getSideContainers().put(name, container);
        }
    }

    @Override
    public Proc launchBuildProcess(Launcher.ProcStarter procStarter, TaskListener listener) throws IOException, InterruptedException {
        Container targetContainer = null;

        if (context.isPreScm()) {
            targetContainer = context.getScmContainer();
            if (targetContainer == null) {
                targetContainer = launchScmContainer(listener);
            }
        } else {
            targetContainer = context.getBuildContainer();
            if (targetContainer == null) {
                targetContainer = launchBuildContainers(procStarter, listener);
            }
        }
        return driver.execInContainer(listener, targetContainer.getId(), procStarter);
    }

    @Override
    public void clean(TaskListener listener) throws IOException, InterruptedException {
        for (Container instance : context.getSideContainers().values()) {
            driver.removeContainer(listener, instance);
        }

        if (context.getBuildContainer() != null) {
            driver.removeContainer(listener, context.getBuildContainer());
        }

        if (context.getScmContainer() != null) {
            driver.removeContainer(listener, context.getScmContainer());
        }

        driver.close();
    }
}
/*
 * The MIT License
 *
 *  Copyright (c) 2016, CloudBees, Inc.
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

import com.cloudbees.jenkins.plugins.dockerslaves.spec.ContainerSetDefinition;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;

import java.io.IOException;

public abstract class DockerProvisionerFactory {
    protected final DockerServerEndpoint dockerHost;
    protected final String remotingContainerImageName;
    protected final String scmContainerImageName;

    public DockerProvisionerFactory(DockerServerEndpoint dockerHost, String remotingContainerImageName, String scmContainerImageName) {
        this.dockerHost = dockerHost;
        this.remotingContainerImageName = remotingContainerImageName;
        this.scmContainerImageName = scmContainerImageName;
    }

    public abstract DockerProvisioner createProvisioner(TaskListener slaveListener) throws IOException, InterruptedException;

    public static class StandardJob extends DockerProvisionerFactory {
        protected final Job job;

        public StandardJob(DockerServerEndpoint dockerHost, String remotingContainerImageName, String scmContainerImageName, Job job) {
            super(dockerHost, remotingContainerImageName, scmContainerImageName);
            this.job = job;
        }

        @Override
        public DockerProvisioner createProvisioner(TaskListener slaveListener) throws IOException, InterruptedException {
            JobBuildsContainersContext context = new JobBuildsContainersContext();

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

            return new DockerProvisioner(context, slaveListener, new DockerDriver(dockerHost, job), new Launcher.LocalLauncher(slaveListener),
                    (ContainerSetDefinition) job.getProperty(ContainerSetDefinition.class), remotingContainerImageName, scmContainerImageName);
        }
    }

    public static class PipelineJob extends DockerProvisionerFactory {
        protected final Job job;
        protected final ContainerSetDefinition spec;

        public PipelineJob(DockerServerEndpoint dockerHost, String remotingContainerImageName, String scmContainerImageName, Job job, ContainerSetDefinition spec) {
            super(dockerHost, remotingContainerImageName, scmContainerImageName);
            this.job = job;
            this.spec = spec;
        }

        @Override
        public DockerProvisioner createProvisioner(TaskListener slaveListener) throws IOException, InterruptedException {
            return new DockerProvisioner(new JobBuildsContainersContext(false), slaveListener, new DockerDriver(dockerHost, job), new Launcher.LocalLauncher(slaveListener),
                    spec, remotingContainerImageName, scmContainerImageName);
        }
    }
}

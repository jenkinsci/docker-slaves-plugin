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

package it.dockins.dockerslaves;

import it.dockins.dockerslaves.spec.ContainerSetDefinition;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import it.dockins.dockerslaves.spi.DockerHostSource;

import java.io.IOException;

public abstract class DockerProvisionerFactory {

    protected final DockerHostSource dockerHost;
    protected final String remotingContainerImageName;
    protected final String scmContainerImageName;
    protected final Job job;
    protected final ContainerSetDefinition spec;

    public DockerProvisionerFactory(DockerHostSource dockerHost, String remotingContainerImageName, String scmContainerImageName, Job job, ContainerSetDefinition spec) {
        this.dockerHost = dockerHost;
        this.remotingContainerImageName = remotingContainerImageName;
        this.scmContainerImageName = scmContainerImageName;
        this.job = job;
        this.spec = spec;
    }

    protected void prepareWorkspace(Job job, JobBuildsContainersContext context) {

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


    public abstract DockerProvisioner createProvisioner(TaskListener slaveListener) throws IOException, InterruptedException;

    public static class StandardJob extends DockerProvisionerFactory {

        public StandardJob(DockerHostSource dockerHost, String remotingContainerImageName, String scmContainerImageName, Job job) {
            super(dockerHost, remotingContainerImageName, scmContainerImageName, job, (ContainerSetDefinition) job.getProperty(ContainerSetDefinition.class));
        }

        @Override
        public DockerProvisioner createProvisioner(TaskListener slaveListener) throws IOException, InterruptedException {
            JobBuildsContainersContext context = new JobBuildsContainersContext();

            prepareWorkspace(job, context);

            return new DockerProvisioner(context, slaveListener, dockerHost, job,
                    spec, remotingContainerImageName, scmContainerImageName);
        }
    }

    public static class PipelineJob extends DockerProvisionerFactory {

        public PipelineJob(DockerHostSource dockerHost, String remotingContainerImageName, String scmContainerImageName, Job job, ContainerSetDefinition spec) {
            super(dockerHost, remotingContainerImageName, scmContainerImageName, job, spec);
        }

        @Override
        public DockerProvisioner createProvisioner(TaskListener slaveListener) throws IOException, InterruptedException {
            JobBuildsContainersContext context = new JobBuildsContainersContext(false);
            return new DockerProvisioner(context, slaveListener, dockerHost, job,
                    spec, remotingContainerImageName, scmContainerImageName);
        }
    }
}

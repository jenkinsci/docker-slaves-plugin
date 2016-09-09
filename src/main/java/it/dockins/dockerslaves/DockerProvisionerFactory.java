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

import it.dockins.dockerslaves.spi.DockerDriver;
import it.dockins.dockerslaves.spec.ContainerSetDefinition;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.IOException;

public abstract class DockerProvisionerFactory {

    protected final DockerDriver driver;
    protected final Job job;
    protected final ContainerSetDefinition spec;

    public DockerProvisionerFactory(DockerDriver driver, Job job, ContainerSetDefinition spec) {
        this.driver = driver;
        this.job = job;
        this.spec = spec;
    }

    protected void prepareWorkspace(Job job, ContainersContext context) {

        // TODO define a configurable volume strategy to retrieve a (maybe persistent) workspace

        Run lastBuild = job.getLastCompletedBuild();
        if (lastBuild != null) {
            ContainersContext previousContext = (ContainersContext) lastBuild.getAction(ContainersContext.class);
            if (previousContext != null && previousContext.getWorkdirVolume() != null) {
                context.setWorkdirVolume(previousContext.getWorkdirVolume());
            }
        }
    }


    public abstract DockerProvisioner createProvisioner(TaskListener slaveListener) throws IOException, InterruptedException;

    public static class StandardJob extends DockerProvisionerFactory {

        public StandardJob(DockerDriver driver, Job job) {
            super(driver, job, (ContainerSetDefinition) job.getProperty(ContainerSetDefinition.class));
        }

        @Override
        public DockerProvisioner createProvisioner(TaskListener slaveListener) throws IOException, InterruptedException {
            ContainersContext context = new ContainersContext();

            prepareWorkspace(job, context);

            return new DockerProvisioner(context, slaveListener, driver, job, spec);
        }
    }

    public static class PipelineJob extends DockerProvisionerFactory {

        public PipelineJob(DockerDriver driver, Job job, ContainerSetDefinition spec) {
            super(driver, job, spec);
        }

        @Override
        public DockerProvisioner createProvisioner(TaskListener slaveListener) throws IOException, InterruptedException {
            ContainersContext context = new ContainersContext(false);
            return new DockerProvisioner(context, slaveListener, driver, job, spec);
        }
    }
}

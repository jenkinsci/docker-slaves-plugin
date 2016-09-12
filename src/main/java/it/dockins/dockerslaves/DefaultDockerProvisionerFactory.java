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

import hudson.Extension;
import it.dockins.dockerslaves.spi.DockerDriver;
import it.dockins.dockerslaves.spec.ContainerSetDefinition;
import hudson.model.Job;
import hudson.model.Run;
import it.dockins.dockerslaves.spi.DockerDriverFactory;
import it.dockins.dockerslaves.spi.DockerProvisioner;
import it.dockins.dockerslaves.spi.DockerProvisionerFactory;
import it.dockins.dockerslaves.spi.DockerProvisionerFactoryDescriptor;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.IOException;

public class DefaultDockerProvisionerFactory extends DockerProvisionerFactory {


    private final DockerDriverFactory dockerDriverFactory;

    private String scmImage;

    private String remotingImage;

    @DataBoundConstructor
    public DefaultDockerProvisionerFactory(DockerDriverFactory dockerDriverFactory) {
        this.dockerDriverFactory = dockerDriverFactory;
    }

    public DockerDriverFactory getDockerDriverFactory() {
        return dockerDriverFactory;
    }

    public String getScmImage() {
        return StringUtils.isBlank(scmImage) ? "buildpack-deps:scm" : scmImage;
    }

    public String getRemotingImage() {
        return StringUtils.isBlank(remotingImage) ? "jenkinsci/slave" : remotingImage;
    }

    @DataBoundSetter
    public void setScmImage(String scmImage) {
        this.scmImage = scmImage;
    }

    @DataBoundSetter
    public void setRemotingImage(String remotingImage) {
        this.remotingImage = remotingImage;
    }

    protected void prepareWorkspace(Job job, ContainersContext context) {

        // TODO define a configurable volume strategy to retrieve a (maybe persistent) workspace

        Run lastBuild = job.getLastCompletedBuild();
        if (lastBuild != null) {
            ContainersContext previousContext = lastBuild.getAction(ContainersContext.class);
            if (previousContext != null && previousContext.getWorkdirVolume() != null) {
                context.setWorkdirVolume(previousContext.getWorkdirVolume());
            }
        }
    }

    @Override
    public DockerProvisioner createProvisionerForClassicJob(Job job, ContainerSetDefinition spec) throws IOException, InterruptedException {
        final DockerDriver driver = dockerDriverFactory.forJob(job);
        ContainersContext context = new ContainersContext();
        prepareWorkspace(job, context);
        return new DefaultDockerProvisioner(context, driver, job, spec, getRemotingImage(), getScmImage());
    }

    @Override
    public DockerProvisioner createProvisionerForPipeline(Job job, ContainerSetDefinition spec) throws IOException, InterruptedException {
        final DockerDriver driver = dockerDriverFactory.forJob(job);
        ContainersContext context = new ContainersContext(false);
        return new DefaultDockerProvisioner(context, driver, job, spec, getRemotingImage(), getScmImage());
    }

    @Extension
    public static class DescriptorImpl extends DockerProvisionerFactoryDescriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Default";
        }
    }
}

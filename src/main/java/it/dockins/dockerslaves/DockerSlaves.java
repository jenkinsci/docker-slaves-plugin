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

import hudson.Extension;
import hudson.Plugin;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.slaves.Cloud;
import it.dockins.dockerslaves.drivers.PlainDockerAPIDockerDriverFactory;
import it.dockins.dockerslaves.spec.ContainerSetDefinition;
import it.dockins.dockerslaves.spi.DockerProvisioner;
import it.dockins.dockerslaves.spi.DockerProvisionerFactory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * {@link Cloud} implementation designed to launch a set of containers (aka "pod") to establish a Jenkins executor.
 *
 */
public class DockerSlaves extends Plugin implements Describable<DockerSlaves> {

    /**
     * Base Build image name. Build commands will run on it.
     */
    private String defaultBuildContainerImageName;

    private DockerProvisionerFactory dockerProvisionerFactory;

    private int maxContainers = 10;

    public void start() throws IOException {
        load();
    }

    @Override
    public void configure(StaplerRequest req, JSONObject formData) throws IOException, ServletException, Descriptor.FormException {
        req.bindJSON(this, formData);
        save();
    }

    public String getDefaultBuildContainerImageName() {
        return defaultBuildContainerImageName;
    }

    @DataBoundSetter
    public void setDefaultBuildContainerImageName(String defaultBuildContainerImageName) {
        this.defaultBuildContainerImageName = defaultBuildContainerImageName;
    }

    @DataBoundSetter
    public void setDockerProvisionerFactory(DockerProvisionerFactory dockerProvisionerFactory) {
        this.dockerProvisionerFactory = dockerProvisionerFactory;
    }

    public DockerProvisionerFactory getDockerProvisionerFactory() {
        if (dockerProvisionerFactory == null) {
            final DefaultDockerProvisionerFactory factory = new DefaultDockerProvisionerFactory(
                    new PlainDockerAPIDockerDriverFactory(dockerHost));
            factory.setRemotingImage(remotingContainerImageName);
            factory.setScmImage(scmContainerImageName);
            return dockerProvisionerFactory = factory;
        }
        return dockerProvisionerFactory;
    }

    public DockerProvisioner createStandardJobProvisionerFactory(Job job) throws IOException, InterruptedException {
        // TODO iterate on job's ItemGroup and it's parents so end-user can configure this at folder level.

        ContainerSetDefinition spec = (ContainerSetDefinition) job.getProperty(ContainerSetDefinition.class);
        return getDockerProvisionerFactory().createProvisionerForClassicJob(job, spec);
    }

    public int getMaxContainers() {
        return maxContainers;
    }

    @DataBoundSetter
    public void setMaxContainers(int maxContainers) {
        this.maxContainers = maxContainers;
    }

    public DockerProvisioner createProvisionerForPipeline(Job job, ContainerSetDefinition spec) throws IOException, InterruptedException {
        return getDockerProvisionerFactory().createProvisionerForPipeline(job, spec);
    }

    public static DockerSlaves get() {
        return Jenkins.getActiveInstance().getPlugin(DockerSlaves.class);
    }

    @Override
    public Descriptor<DockerSlaves> getDescriptor() {
        return Jenkins.getActiveInstance().getDescriptorOrDie(DockerSlaves.class);
    }


    static {
        Jenkins.XSTREAM.aliasPackage("xyz.quoidneufdocker.jenkins", "it.dockins");
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerSlaves> {

        @Override
        public String getDisplayName() {
            return "Docker Slaves";
        }
    }


    /// --- kept for backward compatibility

    public String scmContainerImageName;

    public String remotingContainerImageName;

    public DockerServerEndpoint dockerHost;
}

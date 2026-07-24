package it.dockins.dockerslaves.spi;

import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Job;
import jenkins.model.Jenkins;

import java.io.IOException;

/**
 * A DockerHostSource is responsible to determine (or provision) the dockerhost to host a build for the specified job.
 * <p>
 * Implementation can use this extension to execute some decision and/or provisioning logic, depending on infrastructure
 * details and constraints.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class DockerHostSource implements ExtensionPoint, Describable<DockerHostSource> {

    @Override
    public Descriptor<DockerHostSource> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    /**
     * Allocate / Determine best Docker host to use to build this Job.
     * @param job
     * @return
     */
    public abstract DockerHostConfig getDockerHost(Job job) throws IOException, InterruptedException;
}

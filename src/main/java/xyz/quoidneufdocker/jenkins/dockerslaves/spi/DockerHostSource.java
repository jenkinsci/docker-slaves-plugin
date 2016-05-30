package xyz.quoidneufdocker.jenkins.dockerslaves.spi;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Job;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;

/**
 * A DockerHostSource is responsible to determine (or provision) the dockerhost to host a build for the specified job.
 * <p>
 * Implementation can use this extension to execute some decision and/or provisioning logic, depending on infrastructure
 * details and constraints.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class DockerHostSource extends AbstractDescribableImpl<DockerHostSource> implements ExtensionPoint{

    /**
     * Allocate / Determine best Docker host to use to build this Job.
     * @param job
     * @return
     */
    public abstract DockerServerEndpoint getDockerHost(Job job);

}

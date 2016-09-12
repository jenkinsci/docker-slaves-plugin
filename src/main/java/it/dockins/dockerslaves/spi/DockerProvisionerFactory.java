package it.dockins.dockerslaves.spi;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Job;
import it.dockins.dockerslaves.DockerProvisioner;
import it.dockins.dockerslaves.spec.ContainerSetDefinition;

import java.io.IOException;

/**
 * This component is responsible to orchestrate the provisioning of a build environment based on configured
 * {@link ContainerSetDefinition} for specified {@link Job}.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class DockerProvisionerFactory extends AbstractDescribableImpl<DockerProvisionerFactory> {

    public abstract DockerProvisioner createProvisionerForClassicJob(Job job, ContainerSetDefinition spec) throws IOException, InterruptedException;

    public abstract DockerProvisioner createProvisionerForPipeline(Job job, ContainerSetDefinition spec) throws IOException, InterruptedException;
}

package it.dockins.dockerslaves.spi;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Job;
import it.dockins.dockerslaves.spec.ContainerDefinitionDescriptor;
import it.dockins.dockerslaves.spec.ContainerSetDefinition;
import it.dockins.dockerslaves.spec.DockerSocketContainerDefinition;
import jenkins.model.Jenkins;

import java.io.IOException;

/**
 * This component is responsible to orchestrate the provisioning of a build environment based on configured
 * {@link ContainerSetDefinition} for specified {@link Job}.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class DockerProvisionerFactory implements Describable<DockerProvisionerFactory> {

    @Override
    public Descriptor<DockerProvisionerFactory> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    public abstract DockerProvisioner createProvisionerForClassicJob(Job job, ContainerSetDefinition spec) throws IOException, InterruptedException;

    public abstract DockerProvisioner createProvisionerForPipeline(Job job, ContainerSetDefinition spec) throws IOException, InterruptedException;

    public boolean canBeUsedAsMainContainer(ContainerDefinitionDescriptor d) {
        return d.clazz != DockerSocketContainerDefinition.class;
    }

    public boolean canBeUsedAsSideContainer(ContainerDefinitionDescriptor d) {
        return true;
    }
}

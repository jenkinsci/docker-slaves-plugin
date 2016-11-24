package it.dockins.dockerslaves.spec;

import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import java.util.List;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ContainerDefinitionDescriptor extends Descriptor<ContainerDefinition> {

    public static List<ContainerDefinitionDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(ContainerDefinition.class);
    }

    public boolean canBeUsedForMainContainer() {
        return true;
    }
}

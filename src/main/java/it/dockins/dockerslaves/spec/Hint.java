package it.dockins.dockerslaves.spec;

import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class Hint implements ExtensionPoint, Describable<Hint> {

    @Override
    public Descriptor<Hint> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

}

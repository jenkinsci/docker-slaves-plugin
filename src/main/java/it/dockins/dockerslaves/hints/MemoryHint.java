package it.dockins.dockerslaves.hints;

import hudson.Extension;
import it.dockins.dockerslaves.spec.Hint;
import it.dockins.dockerslaves.spec.HintDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class MemoryHint extends Hint{

    private final String memory;

    @DataBoundConstructor
    public MemoryHint(String memory) {
        this.memory = memory;
    }

    public String getMemory() {
        return memory;
    }

    @Extension
    public static class DescriptorImpl extends HintDescriptor {

        @Override
        public String getDisplayName() {
            return "Memory";
        }
    }
}

package it.dockins.dockerslaves.hints;

import hudson.Extension;
import it.dockins.dockerslaves.spec.Hint;
import it.dockins.dockerslaves.spec.HintDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author <a href="mailto:alexey.galkin@gmail.com">Alexey Galkin</a>
 */
public class RawOptionsHint extends Hint{

    private final String options;

    @DataBoundConstructor
    public RawOptionsHint(String options) {
        this.options = options;
    }

    public String getOptions() {
        return options;
    }

    @Extension
    public static class DescriptorImpl extends HintDescriptor {

        @Override
        public String getDisplayName() {
            return "Raw Options";
        }
    }
}

package it.dockins.dockerslaves.hints;

import it.dockins.dockerslaves.spec.Hint;

/**
 * This hint has no descriptor as we don't want to see it exposed to end user, this is just a hack being used by
 * ${@link it.dockins.dockerslaves.spec.DockerSocketContainerDefinition}.
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class VolumeHint extends Hint{

    private final String volume;

    public VolumeHint(String volume) {
        this.volume = volume;
    }

    public String getVolume() {
        return volume;
    }

}

package it.dockins.dockerslaves.hints;

import it.dockins.dockerslaves.spec.Hint;

/**
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

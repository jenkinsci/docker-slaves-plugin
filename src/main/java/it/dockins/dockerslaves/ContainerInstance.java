package it.dockins.dockerslaves;

public class ContainerInstance {
    final String imageName;
    String id;

    public ContainerInstance(String imageName) {
        this.imageName = imageName;
    }

    public ContainerInstance(String image, String id) {
        this(image);
        this.id = id;
    }

    public String getImageName() {
        return imageName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}

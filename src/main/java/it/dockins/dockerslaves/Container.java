package it.dockins.dockerslaves;

public class Container {
    final String imageName;
    String id;

    public Container(String imageName) {
        this.imageName = imageName;
    }

    public Container(String image, String id) {
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

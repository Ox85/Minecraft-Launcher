package spicy.launcher.model;

public record DisplayVersion(String id, String type, boolean installed, boolean isFabric) {

    @Override
    public String toString() {
        return id;
    }
}
package pl.vd.brush;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public class BrushSettings {

    private BrushType type = BrushType.SPHERE;
    private Material primaryMaterial = Material.STONE;
    private Material topMaterial = Material.GRASS_BLOCK;
    private int radius = 3;
    private int overlayThickness = 1;
    private int smoothIterations = 2;
    private final List<Material> randomMaterials = new ArrayList<>(List.of(
            Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE
    ));

    public BrushType getType() { return type; }
    public void setType(BrushType type) { this.type = type; }

    public Material getPrimaryMaterial() { return primaryMaterial; }
    public void setPrimaryMaterial(Material primaryMaterial) { this.primaryMaterial = primaryMaterial; }

    public Material getTopMaterial() { return topMaterial; }
    public void setTopMaterial(Material topMaterial) { this.topMaterial = topMaterial; }

    public int getRadius() { return radius; }
    public void setRadius(int radius) { this.radius = Math.max(1, Math.min(radius, 15)); }

    public int getOverlayThickness() { return overlayThickness; }
    public void setOverlayThickness(int overlayThickness) { this.overlayThickness = Math.max(1, overlayThickness); }

    public int getSmoothIterations() { return smoothIterations; }
    public void setSmoothIterations(int smoothIterations) { this.smoothIterations = Math.max(1, Math.min(smoothIterations, 5)); }

    public List<Material> getRandomMaterials() { return randomMaterials; }

    public void setRandomMaterials(List<Material> materials) {
        randomMaterials.clear();
        randomMaterials.addAll(materials);
    }
}

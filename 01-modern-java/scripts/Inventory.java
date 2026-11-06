// Java 25 compact source file (JEP 512): no class declaration, an instance main method, java.base imported
// implicitly, java.lang.IO for console I/O. Run with: java scripts/Inventory.java
void main() {
    var materials = List.of("Bronze", "Iron", "Steel");
    IO.println("Hephaestus inventory: " + materials.size() + " materials");
}

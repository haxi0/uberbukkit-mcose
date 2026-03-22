package net.minecraft.server;

// No import needed if WorldChunkManagerAlpha is in the same package (net.minecraft.server)
// If it was in net.minecraft.server.Alpha, it would be:
// import net.minecraft.server.Alpha.WorldChunkManagerAlpha;

public class WorldProviderNormal extends WorldProvider {

    public WorldProviderNormal() {}

    /**
     * Creates the chunk manager for the dimension.
     * Overridden to use WorldChunkManagerAlpha for ALPHA terrain type.
     */
    @Override
    protected void a() { // This is initWorldChunkManager
        if (this.a != null && this.a.worldData != null) {
            int terrainType = this.a.worldData.getTerrainType();
            MinecraftServer.log.info("[WorldProviderNormal] Initializing WorldChunkManager for world: " + this.a.worldData.name + " (TerrainType ID: " + terrainType + ")");

            if (terrainType == 1 || terrainType == 5 || terrainType == 7) { // 1=ALPHA, 5=ALPHA_SNOW, 7=INFDEV
                boolean snowWorld = (terrainType == 5) || (this.a.worldData != null && this.a.worldData.isSnowWorld());
                this.b = new WorldChunkManagerAlpha(snowWorld ? BiomeBase.TAIGA : BiomeBase.PLAINS, this.a.getSeed(), snowWorld);
            } else if (terrainType == 6) { // 6 for CLASSIC
                // Match Alpha chunk manager for Classic for identical tinting/biome behavior
                this.b = new WorldChunkManagerAlpha(BiomeBase.PLAINS, this.a.getSeed(), false);
            } else if (terrainType == 2) { // 2 for FLAT
                this.b = new WorldChunkManagerFlat();
            } else if (terrainType == 3) { // 3 for SKY
                this.b = new WorldChunkManagerSky(this.a.getSeed());
            } else { // Default
                super.a(); 
            }
        } else {
            MinecraftServer.log.info("[WorldProviderNormal] Initializing default WorldChunkManager (world or worldData is null).");
            super.a();
        }
    }

    /**
     * Returns the chunk provider for this dimension.
     * Overridden to use AlphaChunkProvider for ALPHA terrain type.
     */
    @Override
    public IChunkProvider getChunkProvider() {
        if (this.a != null && this.a.worldData != null) {
            int terrainType = this.a.worldData.getTerrainType();
            MinecraftServer.log.info("[WorldProviderNormal] Getting ChunkProvider for world: " + this.a.worldData.name + " (TerrainType ID: " + terrainType + "), Seed: " + this.a.getSeed());

            if (terrainType == 1 || terrainType == 5) { // 1 for ALPHA, 5 for ALPHA_SNOW
                return new net.minecraft.server.Alpha.AlphaChunkProvider(this.a, this.a.getSeed(), false);
            } else if (terrainType == 7) { // 7 for INFDEV
                return new net.minecraft.server.Infdev.InfdevChunkProvider(this.a, this.a.getSeed());
            } else if (terrainType == 6) { // 6 for CLASSIC
                return new net.minecraft.server.Classic.ChunkProviderClassic(this.a, this.a.getSeed());
            } else if (terrainType == 2) { // 2 for FLAT
                return new ChunkProviderFlat(this.a, this.a.getSeed(), false); // mapFeaturesEnabled = false
            } else if (terrainType == 3) { // 3 for SKY
                return new ChunkProviderSky(this.a, this.a.getSeed());
            } else { // Default
                return super.getChunkProvider();
            }
        } else {
            MinecraftServer.log.info("[WorldProviderNormal] Returning default ChunkProviderGenerate (world or worldData is null), Seed: " + (this.a != null ? this.a.getSeed() : "UNKNOWN_SEED"));
            return super.getChunkProvider(); 
        }
    }
}

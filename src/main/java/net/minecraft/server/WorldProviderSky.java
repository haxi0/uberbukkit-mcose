package net.minecraft.server;

public class WorldProviderSky extends WorldProvider {

    public WorldProviderSky() {}

    @Override
    public void a() {
        this.b = new WorldChunkManagerSky(this.a != null ? this.a.getSeed() : 0L);
        this.dimension = 1;
    }

    @Override
    public IChunkProvider getChunkProvider() {
        return new ChunkProviderSky(this.a, this.a.getSeed());
    }

    @Override
    public float a(long worldTime, float partialTickTime) {
        return 0.0F;
    }

    /**
     * Returns array with sunrise/sunset colors.
     * Server: public float[] a(float f, float f1) - THIS METHOD DOES NOT EXIST IN SERVER WorldProvider BASE
     */
    public float[] calcSunriseSunsetColors(float celestialAngle, float partialTickTime) {
        return null;
    }

    /**
     * Returns the fog color based on celestial angle and partial ticks.
     * Server: public Vec3D b(float f, float f1) - THIS METHOD DOES NOT EXIST IN SERVER WorldProvider BASE
     * Client equivalent was func_4096_a
     */
    public Vec3D getSkyColor(float celestialAngle, float partialTickTime) {
        int baseColor = 8961023;
        float brightness = MathHelper.cos(celestialAngle * (float) Math.PI * 2.0F) * 2.0F + 0.5F;

        if (brightness < 0.0F) brightness = 0.0F;
        if (brightness > 1.0F) brightness = 1.0F;

        float r = (float) (baseColor >> 16 & 255) / 255.0F;
        float g = (float) (baseColor >> 8 & 255) / 255.0F;
        float bVal = (float) (baseColor & 255) / 255.0F;

        r *= brightness;
        g *= brightness;
        bVal *= brightness;

        return Vec3D.a((double) r, (double) g, (double) bVal);
    }

    /**
     * Returns true if the dimension is Hell (Nether), false otherwise.
     * Server: public boolean e() - THIS METHOD DOES NOT EXIST IN SERVER WorldProvider BASE
     * Client equivalent was func_28112_c()
     */
    public boolean e() {
        return false;
    }

    /**
     * Returns the cloud height for this dimension.
     * Server: public float f() - THIS METHOD DOES NOT EXIST IN SERVER WorldProvider BASE
     */
    public float f() {
        return 8.0F;
    }

    @Override
    public boolean d() {
        return true;
    }

    /**
     * Returns the coordinates of the default spawn point for this dimension.
     * This should be overridden by Sky dimensions to point to a safe platform.
     * Ensure your server's WorldServer class calls this for dimension 1.
     */
    public ChunkCoordinates getSpawnPoint() {
        // Setting a higher default Y for sky worlds. 
        // The actual surface finding logic during player spawn should refine this.
        return new ChunkCoordinates(50, 90, 0); // Changed from 0,65,0 to 50,90,0
    }

    @Override
    public boolean canSpawn(int x, int z) {
        // First, check for a solid block with air above it within a typical sky island Y-range.
        // Iterate downwards from a common sky island height to a reasonable minimum.
        for (int y = 120; y >= 60; y--) {
            int blockId = this.a.getTypeId(x, y, z); // 'a' is the WorldServer instance
            if (blockId != 0) {
                Block block = Block.byId[blockId];
                if (block != null && block.material.isSolid()) {
                    // Check if the block directly above is air to ensure it's a surface.
                    if (this.a.getTypeId(x, y + 1, z) == 0) {
                         return true; // Valid spawn surface found in sky island range
                    }
                }
            }
        }

        // Fallback: If no suitable platform found in the primary sky island range.
        // This uses the world's method to find the highest accessible block.
        // 'e(x,z)' likely corresponds to getTopSolidOrLiquidBlock(x,z) or similar, returning y+1 or 0.
        int topSolidYPlus1 = this.a.e(x, z);
        if (topSolidYPlus1 > 0) { // A block was found by the world's method
            int surfaceY = topSolidYPlus1 - 1;
            // Ensure this surface is at a reasonable height for sky worlds (e.g., not at Y=1)
            if (surfaceY >= 50) {
                int surfaceBlockId = this.a.getTypeId(x, surfaceY, z);
                if (surfaceBlockId != 0) {
                    Block block = Block.byId[surfaceBlockId];
                    if (block != null && block.material.isSolid()) {
                        // Check if the block directly above is air to ensure it's a surface.
                        if (this.a.getTypeId(x, surfaceY + 1, z) == 0) {
                            return true; // Valid spawn surface found by fallback, and it's not too low
                        }
                    }
                }
            }
        }
        
        // If no suitable spawn point found by either method
        return false;
    }
}

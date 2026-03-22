package net.minecraft.server;

// import java.lang.reflect.Field; // Temporarily commented out
// import java.util.Properties;    // Temporarily commented out

public class BlockFenceGate extends Block {

	// Static initializer block to add the translation - TEMPORARILY COMMENTED OUT
    /*
    static {
        try {
            // Assuming StringTranslate is LocaleLanguage on the server
            // and translateTable is 'properties'
            LocaleLanguage ll = LocaleLanguage.getInstance(); // This line is causing an error
            Field translateTableField = LocaleLanguage.class.getDeclaredField("properties"); // This line is causing an error
            translateTableField.setAccessible(true); // Make the private field accessible
            Properties translateTable = (Properties) translateTableField.get(ll);
            
            translateTable.put("tile.fenceGate.name", "Fence Gate");

        } catch (NoSuchFieldException e) {
            // System.err.println("BlockFenceGate: Failed to find 'properties' field in LocaleLanguage: " + e.getMessage());
        } catch (IllegalAccessException e) {
            // System.err.println("BlockFenceGate: Failed to access 'properties' field in LocaleLanguage: " + e.getMessage());
        } catch (SecurityException e) {
            // System.err.println("BlockFenceGate: Security exception with LocaleLanguage reflection: " + e.getMessage());
        } catch (Exception e) {
            // System.err.println("BlockFenceGate: Generic exception in static initializer: " + e.getMessage());
            // e.printStackTrace(); // For more detailed debugging if needed
        }
    }
    */

	public BlockFenceGate(int var1, int var2) {
		// Corrected Material and block name setting
		super(var1, Material.WOOD); // Material.wood -> Material.WOOD
		this.textureId = var2; // Assuming var2 is the texture index from the constructor
		this.a("fenceGate");    // setBlockName("fenceGate") -> a("fenceGate")
		// This should make getLocalizedName() use tile.fenceGate.name
		// and getBlockName() (from ItemBlock) use "tile.fenceGate"
	}

	@Override // Assuming this is the intended override based on common practice
	public boolean canPlace(World var1, int var2, int var3, int var4) { // Renamed from canPlaceBlockAt, super call changed
		return super.canPlace(var1, var2, var3, var4);
	}

	@Override // This is the critical change for collision
	public AxisAlignedBB e(World var1, int var2, int var3, int var4) { // Renamed from getCollisionBoundingBoxFromPool to 'e'
		int var5 = var1.getData(var2, var3, var4); // Metadata
		boolean isOpen = isFenceGateOpen(var5);      // True if meta indicates open (e.g., meta 7)

		if (isOpen) { // If gate is open
		    return null; // No collision box
		} else { // If gate is closed
		    // Determine orientation for closed gate collision box
		    int orientation = var5 & 3; // Lower 2 bits determine orientation
		    boolean isZAxis = orientation == 0 || orientation == 2; // Facing Z-axis (North/South)

		    if (isZAxis) {
		        return AxisAlignedBB.b((double)var2, (double)var3, (double)((float)var4 + 0.375F), (double)(var2 + 1), (double)((float)var3 + 1.5F), (double)((float)var4 + 0.625F));
		    } else { // Facing X-axis (East/West)
		        return AxisAlignedBB.b((double)((float)var2 + 0.375F), (double)var3, (double)var4, (double)((float)var2 + 0.625F), (double)((float)var3 + 1.5F), (double)(var4 + 1));
		    }
		}
	}

	@Override
	public boolean a() {
		return false;
	}

	@Override
	public boolean b() {
		return false;
	}

	@Override
	public int e() {
		return 21;
	}

	// Renamed from onBlockPlacedBy to postPlace to match Block.java's method called by ItemBlock
	@Override // Assuming @Override is appropriate, may need to be removed if it causes issues on this server version
	public void postPlace(World var1, int var2, int var3, int var4, EntityLiving var5) {
		float playerYaw = (var5 != null) ? var5.yaw : -1.0f; // Check if player is null
		int var6 = (MathHelper.floor((double)(playerYaw * 4.0F / 360.0F) + 0.5D) & 3) % 4;
		var1.setData(var2, var3, var4, var6);
		var1.notify(var2, var3, var4);
	}

	@Override
	public boolean interact(World var1, int var2, int var3, int var4, EntityHuman var5) {
		int var6_currentMeta = var1.getData(var2, var3, var4);
		boolean var7_isOpenCurrently = isFenceGateOpen(var6_currentMeta);
		int var8_newMeta;

		if (var7_isOpenCurrently) { // If open, close it
			var8_newMeta = var6_currentMeta & ~4; // Clear the open bit (0x4)
		} else { // If closed, open it
			var8_newMeta = var6_currentMeta | 4;  // Set the open bit (0x4)
		}

		var1.setData(var2, var3, var4, var8_newMeta);
		var1.notify(var2, var3, var4);
		var1.b(var2, var3, var4, var2, var3, var4); // Door-like update (parameters for single block)
		var1.a(var5, 1003, var2, var3, var4, 0);   // SFX last
		return true;
	}

	public static boolean isFenceGateOpen(int var0) {
		return (var0 & 4) != 0;
	}

	// Gets the orientation of the fence gate (0-3)
	public static int func_35290_f(int var0) {
		return var0 & 3;
	}

	// Ensure this signature matches Block.java's onNeighborBlockChange
	// public void onNeighborBlockChange(World world, int x, int y, int z, int neighborBlockId) {
	@Override // Keep @Override to help catch signature mismatches
	public void doPhysics(World world, int x, int y, int z, int neighborBlockId) { // Common server name for onNeighborBlockChange
		if (world.isStatic) { // if it's client world, do nothing
			return;
		}

		boolean powered = world.isBlockIndirectlyPowered(x, y, z);
		int meta = world.getData(x, y, z); // getBlockMetadata -> getData
		boolean open = isFenceGateOpen(meta);

		if (powered && !open) {
			int newMeta = meta | 4;
			world.setData(x, y, z, newMeta);
			world.notify(x, y, z);
			world.b(x,y,z,x,y,z); // Door-like update
			world.a(null, 1003, x, y, z, 0); // SFX last
		} else if (!powered && open) {
			int newMeta = meta & ~4;
			world.setData(x, y, z, newMeta);
			world.notify(x, y, z);
			world.b(x,y,z,x,y,z); // Door-like update
			world.a(null, 1003, x, y, z, 0); // SFX last
		}
	}
} 

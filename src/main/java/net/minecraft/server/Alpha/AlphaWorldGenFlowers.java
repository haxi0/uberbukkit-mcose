package net.minecraft.server.Alpha;

import java.util.Random;
import net.minecraft.server.Block;
import net.minecraft.server.BlockFlower;
import net.minecraft.server.World;
import net.minecraft.server.WorldGenerator;

public class AlphaWorldGenFlowers extends WorldGenerator {
	private int plantBlockId;

	public AlphaWorldGenFlowers(int var1) {
		this.plantBlockId = var1;
	}

	public boolean a(World var1, Random var2, int var3, int var4, int var5) {
		for (int var6 = 0; var6 < 64; ++var6) {
			int var7 = var3 + var2.nextInt(8) - var2.nextInt(8);
			int var8 = var4 + var2.nextInt(4) - var2.nextInt(4);
			int var9 = var5 + var2.nextInt(8) - var2.nextInt(8);
			if (var1.getTypeId(var7, var8, var9) == 0 && ((BlockFlower)Block.byId[this.plantBlockId]).f(var1, var7, var8, var9)) {
				var1.setRawTypeId(var7, var8, var9, this.plantBlockId);
			}
		}

		return true;
	}
}

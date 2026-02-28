package net.minecraft.server.Alpha;

import java.util.Random;
import net.minecraft.server.Block;
import net.minecraft.server.World;
import net.minecraft.server.WorldGenerator;

public class AlphaWorldGenLiquids extends WorldGenerator {
	private int liquidBlockId;

	public AlphaWorldGenLiquids(int var1) {
		this.liquidBlockId = var1;
	}

	public boolean a(World var1, Random var2, int var3, int var4, int var5) {
		if (var1.getTypeId(var3, var4 + 1, var5) != Block.STONE.id) {
			return false;
		} else if (var1.getTypeId(var3, var4 - 1, var5) != Block.STONE.id) {
			return false;
		} else if (var1.getTypeId(var3, var4, var5) != 0 && var1.getTypeId(var3, var4, var5) != Block.STONE.id) {
			return false;
		} else {
			int var6 = 0;
			if (var1.getTypeId(var3 - 1, var4, var5) == Block.STONE.id) {
				++var6;
			}

			if (var1.getTypeId(var3 + 1, var4, var5) == Block.STONE.id) {
				++var6;
			}

			if (var1.getTypeId(var3, var4, var5 - 1) == Block.STONE.id) {
				++var6;
			}

			if (var1.getTypeId(var3, var4, var5 + 1) == Block.STONE.id) {
				++var6;
			}

			int var7 = 0;
			if (var1.getTypeId(var3 - 1, var4, var5) == 0) {
				++var7;
			}

			if (var1.getTypeId(var3 + 1, var4, var5) == 0) {
				++var7;
			}

			if (var1.getTypeId(var3, var4, var5 - 1) == 0) {
				++var7;
			}

			if (var1.getTypeId(var3, var4, var5 + 1) == 0) {
				++var7;
			}

			if (var6 == 3 && var7 == 1) {
				var1.setTypeId(var3, var4, var5, this.liquidBlockId);
			}

			return true;
		}
	}
}

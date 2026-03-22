package net.minecraft.server;

import java.util.List;
import net.minecraft.server.EntityMonster;

public class EntitySnowman extends EntitySnowmanBase {
	private static final EntityDataAccessor<Byte> DATA_PUMPKIN_ID = new EntityDataAccessor<Byte>(16, EntityDataSerializers.BYTE);
	protected boolean hasAttacked = false;

	public EntitySnowman(World var1) {
		super(var1);
		this.texture = "/mob/snowman.png";
		this.b(0.4F, 1.8F);
		this.health = 4;
	}

	protected void defineSynchedData() {
		super.defineSynchedData();
		this.getSynchedEntityData().define(DATA_PUMPKIN_ID, Byte.valueOf((byte)1));
	}

	protected void b() {
		super.b();
		this.datawatcher.a(16, Byte.valueOf(this.getPumpkinData()));
	}

	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if(accessor == DATA_PUMPKIN_ID) {
			Byte value = this.getSynchedEntityData().get(DATA_PUMPKIN_ID);
			this.datawatcher.watch(16, value == null ? Byte.valueOf((byte)1) : value);
		}
	}

	public boolean hasPumpkin() {
		return (this.getPumpkinData() & 1) != 0;
	}

	public void setPumpkin(boolean hasPumpkin) {
		byte data = this.getPumpkinData();
		if(hasPumpkin) {
			data = (byte)(data | 1);
		} else {
			data = (byte)(data & -2);
		}

		this.setPumpkinData(data);
	}

	public int getMaxHealth() {
		return 4;
	}

	public void c_() {
		super.c_();
		
		if(this.target == null && !this.C() && this.world.random.nextInt(100) == 0) {
			List var1 = this.world.a(EntityMonster.class, AxisAlignedBB.a(this.locX, this.locY, this.locZ, this.locX + 1.0D, this.locY + 1.0D, this.locZ + 1.0D).b(16.0D, 4.0D, 16.0D));
			if(!var1.isEmpty()) {
				this.setTarget((Entity)var1.get(this.world.random.nextInt(var1.size())));
			}
		}

		int var5_x = MathHelper.floor(this.locX);
		int var3_z = MathHelper.floor(this.locZ);

		WorldChunkManager wcm = this.world.getWorldChunkManager();
		wcm.a((BiomeBase[])null, var5_x, var3_z, 1, 1);
		double currentBlockTemperature = wcm.temperature[0];

		if(currentBlockTemperature > 1.0F) {
	            // Snowmen melt in hot biomes - client code stated: "melt damage not implemented in current codebase"
	            // For now, let's leave it out to match that, can be re-added with a suitable DamageSource if desired.
		}

		for(int var5_loop = 0; var5_loop < 4; ++var5_loop) {
			int var2_loop_x = MathHelper.floor(this.locX + (double)((float)(var5_loop % 2 * 2 - 1) * 0.25F));
			int var3_loop_y = MathHelper.floor(this.locY);
			int var4_loop_z = MathHelper.floor(this.locZ + (double)((float)(var5_loop / 2 % 2 * 2 - 1) * 0.25F));
			
			wcm.a((BiomeBase[])null, var2_loop_x, var4_loop_z, 1, 1);
			double snowPlaceTemperature = wcm.temperature[0];

			if(this.world.getTypeId(var2_loop_x, var3_loop_y, var4_loop_z) == 0 && snowPlaceTemperature < 0.8F && Block.SNOW.canPlace(this.world, var2_loop_x, var3_loop_y, var4_loop_z)) {
				this.world.setTypeId(var2_loop_x, var3_loop_y, var4_loop_z, Block.SNOW.id);
			}
		}
	}

	protected void attackEntity(Entity var1, float var2) {
		
		if(var2 < 10.0F) {
			double var3 = var1.locX - this.locX;
			double var5 = var1.locZ - this.locZ;
			if(this.attackTicks == 0) {
				EntitySnowball var7 = new EntitySnowball(this.world, this);
				double entityEyeHeight = (var1 instanceof EntityLiving) ? ((EntityLiving)var1).t() : 1.62D;
				double var8 = var1.locY + entityEyeHeight - 1.1D - var7.locY;
				float var10 = MathHelper.a(var3 * var3 + var5 * var5) * 0.2F;
				this.world.makeSound(this, "random.bow", 1.0F, 1.0F / (this.random.nextFloat() * 0.4F + 0.8F));
				this.world.addEntity(var7);
				var7.a(var3, var8 + (double)var10, var5, 1.6F, 12.0F);
				this.attackTicks = 10;
			}

			this.yaw = (float)(Math.atan2(var5, var3) * 180.0D / Math.PI) - 90.0F;
			this.hasAttacked = true;
		}
		
	}

	public void b(NBTTagCompound var1) {
		super.b(var1);
		var1.a("Pumpkin", this.hasPumpkin());
	}

	public void a(NBTTagCompound var1) {
		super.a(var1);
		if(var1.hasKey("Pumpkin")) {
			this.setPumpkin(var1.m("Pumpkin"));
		} else {
			this.setPumpkin(true);
		}
	}

	protected int getDropItemId() {
		return Item.SNOW_BALL.id;
	}

	public boolean a(EntityHuman entityhuman) {
		ItemStack heldItem = entityhuman.G();
		if(this.hasPumpkin() && heldItem != null && heldItem.id == Item.SHEARS.id) {
			if(!this.world.isStatic) {
				this.setPumpkin(false);
				EntityItem pumpkinEntity = this.a(new ItemStack(Block.PUMPKIN.id, 1, 0), 1.0F);
				pumpkinEntity.motY += (double)(this.random.nextFloat() * 0.05F);
				pumpkinEntity.motX += (double)((this.random.nextFloat() - this.random.nextFloat()) * 0.1F);
				pumpkinEntity.motZ += (double)((this.random.nextFloat() - this.random.nextFloat()) * 0.1F);
				if(entityhuman.gameMode != 1) {
					heldItem.damage(1, entityhuman);
				}
			}

			return true;
		}

		return super.a(entityhuman);
	}

	protected void dropFewItems(boolean var1, int var2) {
		
		int var3 = this.random.nextInt(16);

		for(int var4 = 0; var4 < var3; ++var4) {
			this.a(Item.SNOW_BALL.id, 1);
		}
		
	}

	private byte getPumpkinData() {
		Byte value = this.getSynchedEntityData() == null ? null : this.getSynchedEntityData().get(DATA_PUMPKIN_ID);
		if(value != null) {
			return value.byteValue();
		}
		return this.datawatcher.a(16);
	}

	private void setPumpkinData(byte data) {
		if(this.getSynchedEntityData() != null) {
			this.getSynchedEntityData().set(DATA_PUMPKIN_ID, Byte.valueOf(data));
		} else {
			this.datawatcher.watch(16, Byte.valueOf(data));
		}
	}
}

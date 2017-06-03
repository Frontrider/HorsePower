package se.gorymoon.horsepower.tileentity;

import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import se.gorymoon.horsepower.recipes.MillRecipes;

import java.util.UUID;

public class TileEntityMill extends TileEntity implements ITickable, ISidedInventory {

    private static final int[] SLOTS_TOP = new int[] {0};
    private static final int[] SLOTS_BOTTOM = new int[] {1};

    private NonNullList<ItemStack> millItemStacks = NonNullList.<ItemStack>withSize(2, ItemStack.EMPTY);

    private static double[][] path = {{-1.5, -1.5}, {0, -1.5}, {1, -1.5}, {1, 0}, {1, 1}, {0, 1}, {-1.5, 1}, {-1.5, 0}};
    private AxisAlignedBB[] searchAreas = new AxisAlignedBB[8];
    private int origin = -1;
    private int target = origin;

    private boolean hasWorker = false;
    private AbstractHorse worker;
    private NBTTagCompound nbtWorker;

    private int currentItemMillTime;
    private int totalItemMillTime;
    private boolean running = true;
    private boolean wasRunning = false;

    public void setWorker(AbstractHorse newWorker) {
        hasWorker = true;
        worker = newWorker;
        worker.setHomePosAndDistance(pos, 3);
        target = getClosestTarget();
    }

    public boolean hasWorker() {
        return worker != null && !worker.isDead && !worker.getLeashed() && worker.getDistanceSq(pos) < 35;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        ItemStackHelper.saveAllItems(compound, millItemStacks);
        compound.setInteger("millTime", currentItemMillTime);
        compound.setInteger("totalMillTime", totalItemMillTime);

        compound.setInteger("target", target);
        compound.setInteger("origin", origin);
        compound.setBoolean("hasWorker", hasWorker);

        if (this.worker != null)
        {
            NBTTagCompound nbtTagCompound = new NBTTagCompound();
            UUID uuid = worker.getUniqueID();
            nbtTagCompound.setUniqueId("UUID", uuid);

            compound.setTag("leash", nbtTagCompound);
        }

        return super.writeToNBT(compound);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        millItemStacks = NonNullList.<ItemStack>withSize(this.getSizeInventory(), ItemStack.EMPTY);
        ItemStackHelper.loadAllItems(compound, millItemStacks);
        currentItemMillTime = compound.getInteger("millTime");
        totalItemMillTime = compound.getInteger("totalMillTime");

        target = compound.getInteger("target");
        origin = compound.getInteger("origin");
        hasWorker = compound.getBoolean("hasWorker");

        if (hasWorker && compound.hasKey("leash", 10)) {
            nbtWorker = compound.getCompoundTag("leash");
        }


    }

    @Override
    public void update() {
        boolean flag = false;

        if (!world.isRemote) {

            if (nbtWorker != null) {
                if (hasWorker) {
                    UUID uuid = nbtWorker.getUniqueId("UUID");
                    int x = pos.getX();
                    int y = pos.getY();
                    int z = pos.getZ();

                    for (AbstractHorse abstractHorse : world.getEntitiesWithinAABB(AbstractHorse.class, new AxisAlignedBB((double)x - 4.0D, (double)y - 4.0D, (double)z - 4.0D, (double)x + 4.0D, (double)y + 4.0D, (double)z + 4.0D))) {
                        if (abstractHorse.getUniqueID().equals(uuid)) {
                            setWorker(abstractHorse);
                            break;
                        }
                    }
                }
                nbtWorker = null;
            }

            if (!running && canMill()) {
                running = true;
            } else if (running && !canMill()){
                running = false;
            }

            if (running != wasRunning) {
                target = getClosestTarget();
                wasRunning = running;
            }

            if (hasWorker()) {
                if (running) {

                    double x = pos.getX() + path[target][0] * 2;
                    double y = pos.getY();
                    double z = pos.getZ() + path[target][1] * 2;

                    if (searchAreas[target] == null)
                        searchAreas[target] = new AxisAlignedBB(x - 0.5D, y - 0.5D, z - 0.5D, x + 0.5D, y + 0.5D, z + 0.5D);

                    if (worker.getEntityBoundingBox().intersectsWith(searchAreas[target])) {
                        int next = target + 1;
                        int previous = target -1;
                        if (next >= path.length)
                            next = 0;
                        if (previous < 0)
                            previous = path.length - 1;

                        if (origin != target && target != previous) {
                            origin = target;
                            currentItemMillTime++;
                            //TODO remove debug message
                            System.out.println("Mill progress: " + currentItemMillTime);

                            if (currentItemMillTime == totalItemMillTime) {
                                currentItemMillTime = 0;

                                totalItemMillTime = MillRecipes.instance().getMillTime(millItemStacks.get(0));
                                millItem();
                                flag = true;
                                //TODO remove debug message
                                System.out.println("Milled!");
                            }
                        }
                        target = next;
                    }

                    if (worker.isEatingHaystack())
                        worker.setEatingHaystack(false);

                    if (target != -1 && worker.getNavigator().noPath()) {
                        x = pos.getX() + path[target][0] * 2;
                        y = pos.getY();
                        z = pos.getZ() + path[target][1] * 2;

                        worker.getNavigator().tryMoveToXYZ(x, y, z, 1.2D);
                    }

                }
            } else if (worker != null) {
                hasWorker = false;
                worker = null;
                InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY() + 1, pos.getZ(), new ItemStack(Items.LEAD));
            }
        }

        if (flag) {
            markDirty();
        }
    }

    private int getClosestTarget() {
        if (hasWorker()) {
            double dist = Double.MAX_VALUE;
            int closest = 0;

            for (int i = 0; i < path.length; i++) {
                double x = pos.getX() + path[i][0] * 2;
                double y = pos.getY();
                double z = pos.getZ() + path[i][1] * 2;

                double tmp = worker.getDistance(x, y, z);
                if (tmp < dist) {
                    dist = tmp;
                    closest = i;
                }
            }

            return closest;
        }
        return 0;
    }

    private void millItem() {
        if (canMill()) {
            ItemStack input = millItemStacks.get(0);
            ItemStack result = MillRecipes.instance().getMillResult(millItemStacks.get(0));
            ItemStack output = millItemStacks.get(1);

            if (output.isEmpty())
            {
                millItemStacks.set(1, result.copy());
            }
            else if (output.getItem() == result.getItem())
            {
                output.grow(result.getCount());
            }

            input.shrink(1);
        }
    }

    private boolean canMill() {
        if (millItemStacks.get(0).isEmpty()) {
            return false;
        } else {
            ItemStack itemstack = MillRecipes.instance().getMillResult(millItemStacks.get(0));

            if (itemstack.isEmpty())
            {
                return false;
            }
            else
            {
                ItemStack output = millItemStacks.get(1);
                if (output.isEmpty()) return true;
                if (!output.isItemEqual(itemstack)) return false;
                int result = output.getCount() + itemstack.getCount();
                return result <= getInventoryStackLimit() && result <= output.getMaxStackSize();
            }
        }
    }

    public static boolean canCombine(ItemStack stack1, ItemStack stack2) {
        return stack1.getItem() == stack2.getItem() && (stack1.getMetadata() == stack2.getMetadata() && (stack1.getCount() <= stack1.getMaxStackSize() && ItemStack.areItemStackTagsEqual(stack1, stack2)));
    }

    @Override
    public int[] getSlotsForFace(EnumFacing side) {
        return side == EnumFacing.DOWN ? SLOTS_BOTTOM : (side == EnumFacing.UP ? SLOTS_TOP : new int[0]);
    }

    @Override
    public boolean canInsertItem(int index, ItemStack itemStackIn, EnumFacing direction) {
        return isItemValidForSlot(index, itemStackIn);
    }

    @Override
    public boolean canExtractItem(int index, ItemStack stack, EnumFacing direction) {
        return direction == EnumFacing.DOWN && index == 1;
    }

    @Override
    public int getSizeInventory() {
        return millItemStacks.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : millItemStacks)
        {
            if (!itemstack.isEmpty())
            {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return millItemStacks.get(index);
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        return ItemStackHelper.getAndSplit(millItemStacks, index, count);
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        return ItemStackHelper.getAndRemove(millItemStacks, index);
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        ItemStack itemstack = millItemStacks.get(index);
        boolean flag = !stack.isEmpty() && stack.isItemEqual(itemstack) && ItemStack.areItemStackTagsEqual(stack, itemstack);
        millItemStacks.set(index, stack);

        if (stack.getCount() > this.getInventoryStackLimit()) {
            stack.setCount(this.getInventoryStackLimit());
        }

        if (index == 0 && !flag) {
            totalItemMillTime = MillRecipes.instance().getMillTime(stack);
            currentItemMillTime = 0;
            markDirty();
        }
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return this.world.getTileEntity(this.pos) == this && player.getDistanceSq((double) this.pos.getX() + 0.5D, (double) this.pos.getY() + 0.5D, (double) this.pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void openInventory(EntityPlayer player) {

    }

    @Override
    public void closeInventory(EntityPlayer player) {

    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return index != 1 && index == 0 && MillRecipes.instance().hasRecipe(stack);
    }

    @Override
    public int getField(int id) {
        switch (id) {
            case 0:
                return this.totalItemMillTime;
            case 1:
                return this.currentItemMillTime;
            default:
                return 0;
        }
    }

    @Override
    public void setField(int id, int value) {
        switch (id) {
            case 0:
                this.totalItemMillTime = value;
                break;
            case 1:
                this.currentItemMillTime = value;
        }
    }

    @Override
    public int getFieldCount() {
        return 2;
    }

    @Override
    public void clear() {
        millItemStacks.clear();
    }

    @Override
    public String getName() {
        return "container.mill";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    private IItemHandler handlerTop = new SidedInvWrapper(this, EnumFacing.UP);
    private IItemHandler handlerBottom = new SidedInvWrapper(this, EnumFacing.DOWN);

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, @javax.annotation.Nullable net.minecraft.util.EnumFacing facing)
    {
        if (facing != null && capability == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
            if (facing == EnumFacing.DOWN)
                return (T) handlerBottom;
            else if (facing == EnumFacing.UP)
                return (T) handlerTop;
        return super.getCapability(capability, facing);
    }
}

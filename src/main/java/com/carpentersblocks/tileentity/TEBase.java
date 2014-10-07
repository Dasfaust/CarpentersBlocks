package com.carpentersblocks.tileentity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import com.carpentersblocks.util.protection.IProtected;
import com.carpentersblocks.util.protection.ProtectedUtil;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public class TEBase extends TileEntity implements IProtected {

    private static final String TAG_ATTR          = "cbAttribute";
    private static final String TAG_ATTR_LIST     = "cbAttrList";
    private static final String TAG_METADATA      = "cbMetadata";
    private static final String TAG_OWNER         = "cbOwner";
    private static final String TAG_CHISEL_DESIGN = "cbChiselDesign";
    private static final String TAG_DESIGN        = "cbDesign";

    public static final byte[] ID_COVER           = {  0,  1,  2,  3,  4,  5,  6 };
    public static final byte[] ID_DYE             = {  7,  8,  9, 10, 11, 12, 13 };
    public static final byte[] ID_OVERLAY         = { 14, 15, 16, 17, 18, 19, 20 };
    public static final byte   ID_ILLUMINATOR     = 21;

    public Map<Byte, ItemStack> cbAttrMap = new HashMap<Byte, ItemStack>();

    /** Chisel design for each side and base block. */
    public String[] cbChiselDesign = { "", "", "", "", "", "", "" };

    /** Holds specific block information like facing, states, etc. */
    public short cbMetadata;

    /** Design name. */
    public String cbDesign = "";

    /** Owner of tile entity. */
    public String cbOwner = "";

    @Override
    public void readFromNBT(NBTTagCompound nbt)
    {
        super.readFromNBT(nbt);

        cbAttrMap.clear();
        if (nbt.hasKey("owner")) {
            MigrationHelper.updateMappingsOnRead(this, nbt);
        } else {
            NBTTagList nbttaglist = nbt.getTagList(TAG_ATTR_LIST, 10);
            for (int idx = 0; idx < nbttaglist.tagCount(); ++idx) {
                NBTTagCompound nbt1 = nbttaglist.getCompoundTagAt(idx);
                ItemStack tempStack = ItemStack.loadItemStackFromNBT(nbt1);
                tempStack.stackSize = 1; // All ItemStacks pre-3.2.7 DEV R3 stored original stack sizes, reduce them here.
                cbAttrMap.put((byte) (nbt1.getByte(TAG_ATTR) & 255), tempStack);
            }

            for (int idx = 0; idx < 7; ++idx) {
                cbChiselDesign[idx] = nbt.getString(TAG_CHISEL_DESIGN + "_" + idx);
            }

            cbMetadata = nbt.getShort(TAG_METADATA);
            cbDesign = nbt.getString(TAG_DESIGN);
            cbOwner = nbt.getString(TAG_OWNER);
        }

        /*
         * Attempt to update owner name to new UUID format.
         * TODO: Remove when player name-changing system is switched on
         */
        if (FMLCommonHandler.instance().getSide().equals(Side.SERVER)) {
            ProtectedUtil.updateOwnerUUID(this);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt)
    {
        super.writeToNBT(nbt);

        NBTTagList itemstack_list = new NBTTagList();
        Iterator iterator = cbAttrMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry entry = (Map.Entry) iterator.next();
            NBTTagCompound nbt1 = new NBTTagCompound();
            byte key = (Byte) entry.getKey();
            nbt1.setByte(TAG_ATTR, key);
            ((ItemStack)entry.getValue()).writeToNBT(nbt1);
            itemstack_list.appendTag(nbt1);
        }
        nbt.setTag(TAG_ATTR_LIST, itemstack_list);

        for (int idx = 0; idx < 7; ++idx) {
            nbt.setString(TAG_CHISEL_DESIGN + "_" + idx, cbChiselDesign[idx]);
        }

        nbt.setShort(TAG_METADATA, cbMetadata);
        nbt.setString(TAG_DESIGN, cbDesign);
        nbt.setString(TAG_OWNER, cbOwner);
    }

    @Override
    /**
     * Overridden in a sign to provide the text.
     */
    public Packet getDescriptionPacket()
    {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    @Override
    /**
     * Called when you receive a TileEntityData packet for the location this
     * TileEntity is currently in. On the client, the NetworkManager will always
     * be the remote server. On the server, it will be whomever is responsible for
     * sending the packet.
     *
     * @param net The NetworkManager the packet originated from
     * @param pkt The data packet
     */
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt)
    {
        readFromNBT(pkt.func_148857_g());

        if (worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            // TODO: Rename to updateAllLightByTypes
            worldObj.func_147451_t(xCoord, yCoord, zCoord);
        }
    }

    /**
     * Called from Chunk.setBlockIDWithMetadata, determines if this tile entity should be re-created when the ID, or Metadata changes.
     * Use with caution as this will leave straggler TileEntities, or create conflicts with other TileEntities if not used properly.
     *
     * @param oldID The old ID of the block
     * @param newID The new ID of the block (May be the same)
     * @param oldMeta The old metadata of the block
     * @param newMeta The new metadata of the block (May be the same)
     * @param world Current world
     * @param x X Position
     * @param y Y Position
     * @param z Z Position
     * @return True to remove the old tile entity, false to keep it in tact {and create a new one if the new values specify to}
     */
    @Override
    public boolean shouldRefresh(Block oldBlock, Block newBlock, int oldMeta, int newMeta, World world, int x, int y, int z)
    {
        /*
         * This is a curious method.
         *
         * Essentially, when doing most block logic server-side, changes
         * to blocks will momentarily "flash" to their default state
         * when rendering client-side.  This is most noticeable when adding
         * or removing covers for the first time.
         *
         * Making the tile entity refresh only when the block is first created
         * is not only reasonable, but fixes this behavior.
         */
        return oldBlock != newBlock;
    }

    /**
     * Sets owner of tile entity.
     */
    @Override
    public void setOwner(UUID uuid)
    {
        cbOwner = uuid.toString();
        markDirty();
    }

    @Override
    public String getOwner()
    {
        return cbOwner;
    }

    @Override
    /**
     * Determines if this TileEntity requires update calls.
     * @return True if you want updateEntity() to be called, false if not
     */
    public boolean canUpdate()
    {
        return false;
    }

}

/*
 * @file AutoSwitchBlock.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2018 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Basic class for blocks representing redstone signal sources, like
 * the vanilla lever or button.
 */
package wile.rsgauges.blocks;

import net.minecraft.world.server.ServerWorld;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.math.*;
import net.minecraft.world.IWorldReader;
import net.minecraft.tileentity.TileEntity;
import wile.rsgauges.blocks.IntervalTimerSwitchBlock.IntervalTimerSwitchTileEntity;
import wile.rsgauges.detail.ModResources;
import wile.rsgauges.detail.SwitchLink;

import javax.annotation.Nullable;
import java.util.Random;


public abstract class AutoSwitchBlock extends SwitchBlock
{
  public AutoSwitchBlock(long config, AbstractBlock.Properties properties, AxisAlignedBB unrotatedBBUnpowered, @Nullable AxisAlignedBB unrotatedBBPowered, @Nullable ModResources.BlockSoundEvent powerOnSound, @Nullable ModResources.BlockSoundEvent powerOffSound)
  { super(config, properties, unrotatedBBUnpowered, unrotatedBBPowered, powerOnSound, powerOffSound); }

  public AutoSwitchBlock(long config, AbstractBlock.Properties properties, AxisAlignedBB unrotatedBBUnpowered, @Nullable AxisAlignedBB unrotatedBBPowered)
  { this(config, properties, unrotatedBBUnpowered, unrotatedBBPowered, null, null); }

  // -------------------------------------------------------------------------------------------------------------------
  // Block overrides
  // -------------------------------------------------------------------------------------------------------------------

  @Override
  public void tick(BlockState state, ServerWorld world, BlockPos pos, Random random)
  {}

  // -------------------------------------------------------------------------------------------------------------------

  @Override
  public SwitchLink.RequestResult switchLinkTrigger(SwitchLink link)
  {
    if((config & SWITCH_CONFIG_LINK_TARGET_SUPPORT)==0) return SwitchLink.RequestResult.REJECTED;
    if((config & SWITCH_CONFIG_TIMER_INTERVAL)==0) return SwitchLink.RequestResult.REJECTED; // only interval timer can be a link target
    BlockState state = link.world.getBlockState(link.target_position);
    if((state == null) || (!(state.getBlock() instanceof AutoSwitchBlock))) return SwitchLink.RequestResult.REJECTED;
    AutoSwitchTileEntity te = getTe(link.world, link.target_position);
    if((te==null) || (!te.verifySwitchLinkTarget(link))) return SwitchLink.RequestResult.REJECTED;
    te.updateSwitchState(state, this, !state.getValue(POWERED), 0);
    return SwitchLink.RequestResult.OK;
  }

  @Override
  public AutoSwitchTileEntity getTe(IWorldReader world, BlockPos pos)
  {
    TileEntity te = world.getBlockEntity(pos);
    if((!(te instanceof AutoSwitchTileEntity))) return null;
    return (AutoSwitchTileEntity)te;
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Tile Entity
  // -------------------------------------------------------------------------------------------------------------------

  /**
   * Tile entity base
   */
  public static abstract class AutoSwitchTileEntity extends SwitchTileEntity
  {
    public AutoSwitchTileEntity(TileEntityType<?> te_type)
    { super(te_type); }

    protected final void updateSwitchState(BlockState state, AutoSwitchBlock block, boolean active, int hold_time)
    { updateSwitchState(state, block, active, hold_time, true); }

    protected final void updateSwitchState(BlockState state, AutoSwitchBlock block, boolean active, int hold_time, boolean link_update)
    {
      if(active) {
        on_timer_reset(hold_time);
        if(!state.getValue(POWERED)) {
          if(this instanceof IntervalTimerSwitchTileEntity) ((IntervalTimerSwitchTileEntity)this).restart();
          level.setBlock(worldPosition, (state.setValue(POWERED, true)), 2|8|16);
          block.power_on_sound.play(level, worldPosition);
          level.updateNeighborsAt(worldPosition, block);
          BlockPos np = worldPosition.relative(state.getValue(FACING).getOpposite());
          Block nb = level.getBlockState(np).getBlock();
          level.updateNeighborsAt(np, nb);
          if(link_update && ((block.config & SwitchBlock.SWITCH_CONFIG_LINK_SOURCE_SUPPORT)!=0)) {
            if(!activateSwitchLinks(on_power(), 15, true)) {
              ModResources.BlockSoundEvents.SWITCHLINK_LINK_PEAL_USE_FAILED.play(level, worldPosition);
            }
          }
        }
      } else if(state.getValue(POWERED)) {
        if((hold_time<=0) || (on_time_remaining() <= 0)) {
          level.setBlock(worldPosition, state.setValue(POWERED, false), 2|8|16);
          block.power_off_sound.play(level, worldPosition);
          level.updateNeighborsAt(worldPosition, block);
          BlockPos np = worldPosition.relative(state.getValue(FACING).getOpposite());
          Block nb = level.getBlockState(np).getBlock();
          level.updateNeighborsAt(np, nb);
          if(link_update && ((block.config & SwitchBlock.SWITCH_CONFIG_LINK_SOURCE_SUPPORT)!=0)) {
            if(!activateSwitchLinks(0, 0, true)) {
              ModResources.BlockSoundEvents.SWITCHLINK_LINK_PEAL_USE_FAILED.play(level, worldPosition);
            }
          }
        }
      }
    }
  }

}

/**
 *
 */
package com.mcfht.realisticfluids.commands;

import com.google.common.eventbus.Subscribe;
import com.mcfht.realisticfluids.FluidManager;
import com.mcfht.realisticfluids.RealisticFluids;

import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/**
 * @author Keybounce
 *
 */
public class CommandDeflood extends CommandBase implements ICommand
{

    /* (non-Javadoc)
     * @see net.minecraft.command.ICommand#getCommandName()
     */
//    
//    @EventHandler
//    public void serverStarting(FMLServerStartingEvent evt)
//    {
//        System.out.println("*** ENABLE FLOW COMMAND ***");
//        evt.registerServerCommand(new CommandEnableFlow());
//    }
//    
    @Override
    public String getCommandName()
    {
        return "deflood";
    }

    /* (non-Javadoc)
     * @see net.minecraft.command.ICommand#getCommandUsage(net.minecraft.command.ICommandSender)
     */
    @Override
    public String getCommandUsage(ICommandSender p_71518_1_)
    {
        // Fixme needs localizaion
        return "deflood";
    }

    /* (non-Javadoc)
     * @see net.minecraft.command.ICommand#processCommand(net.minecraft.command.ICommandSender, java.lang.String[])
     */
    @Override
    public void processCommand(ICommandSender sender, String[] args)
    {

        World world = sender.getEntityWorld();

        if (world.isRemote)
        {
            System.out.println("Not processing on Client side");
        }
        else
        {
            System.out.println("Processing on Server side");
            if(args.length != 0)
            {
                sender.addChatMessage(new ChatComponentText("Usage: deflood. Attempt to remove floods on top of mod fluids."));
                return;
            }
            // Step 1: Save the old vaues for absorb
            int oldAbsorb = RealisticFluids.ABSORB;
            
            // Step 2: Set absorb to MAX_FLUID+1.
            RealisticFluids.ABSORB = RealisticFluids.MAX_FLUID+1;
            // Step 3: Run two processing runs. Intent: More blocks will be marked for update after first run.
            RealisticFluids.tickChunks();
            RealisticFluids.tickChunks();
            // Step 4: Restore Absorb.
            RealisticFluids.ABSORB = oldAbsorb;
        }
    }
}

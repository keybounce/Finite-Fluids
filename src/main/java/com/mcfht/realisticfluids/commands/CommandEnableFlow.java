/**
 *
 */
package com.mcfht.realisticfluids.commands;

import com.mcfht.realisticfluids.FluidManager;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Property;

/**
 * @author Keybounce
 *
 */
public class CommandEnableFlow extends CommandBase implements ICommand
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
        return "enableflow";
    }

    /* (non-Javadoc)
     * @see net.minecraft.command.ICommand#getCommandUsage(net.minecraft.command.ICommandSender)
     */
    @Override
    public String getCommandUsage(ICommandSender p_71518_1_)
    {
        // Fixme needs localizaion
        return "enableflow <true|anything else>";
    }

    /* (non-Javadoc)
     * @see net.minecraft.command.ICommand#processCommand(net.minecraft.command.ICommandSender, java.lang.String[])
     */
    @Override
    public void processCommand(ICommandSender sender, String[] args)
    {

        // I don't want to just set a global.
        // So How do I talk to the fluid manager?
        // And can I make it a per-dimension effect?
        // FluidManager.Delegator has a public array of worlds, and is the code entity that
        // does the scheduling in this version, so that is who I want to talk to.
        //
        // Why not just give it an "on/off" method, and call that?
        //
        // Actually, FluidManager->doTask() is the routine that does the work.
        // That is where to toggle things -- elsewhere will fail to clear out data structures.
        //
        // See also: Should it still do the random ticks?
        // Right now, that's ocean smoothing.
        // It's also what will do rainfall / evaporation / etc.
        // So, probably not -- when turned off, nothing happens.
        //
        // So that's the answer. doTask() will just return without doing anything.
        
        World world = sender.getEntityWorld();

        if (world.isRemote)
        {
            System.out.println("Not processing on Client side");
        }
        else
        {
            System.out.println("Processing on Server side");
            if(args.length != 1)
            {
                sender.addChatMessage(new ChatComponentText("Usage: enableflow <true|false>"));
                return;
            }
            RealisticFluids.FlowEnabled = Boolean.parseBoolean(args[0]);
            sender.addChatMessage(new ChatComponentText(args.toString()));
            sender.addChatMessage(new ChatComponentText("Liquid flow set to" + RealisticFluids.FlowEnabled));
            // Do I want these two? Not sure what I was thinking when I wrote them.
            // FluidManager.delegator.nearChunkSet.clear();
            // FluidManager.delegator.farChunkSet.clear();

            Property p = FluidConfig.config.get(FluidConfig.GENERAL, "FlowEnabled", RealisticFluids.FlowEnabled);
            p.set(RealisticFluids.FlowEnabled);
            FluidConfig.config.save();
        }
    }
}

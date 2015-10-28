/**
 *
 */
package com.mcfht.realisticfluids.commands;

import com.google.common.eventbus.Subscribe;
import com.mcfht.realisticfluids.FluidManager;

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
        return "EnableFlow";
    }

    /* (non-Javadoc)
     * @see net.minecraft.command.ICommand#getCommandUsage(net.minecraft.command.ICommandSender)
     */
    @Override
    public String getCommandUsage(ICommandSender p_71518_1_)
    {
        // Fixme needs localizaion
        return "EnableFlow <true|false>";
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
                sender.addChatMessage(new ChatComponentText("Usage: EnableFlow <true|false>"));
                return;
            }
            FluidManager.FlowEnabled = Boolean.parseBoolean(args[0]);
            sender.addChatMessage(new ChatComponentText(args.toString()));
            sender.addChatMessage(new ChatComponentText("Liquid flow set to" + FluidManager.FlowEnabled));
        }
    }
}

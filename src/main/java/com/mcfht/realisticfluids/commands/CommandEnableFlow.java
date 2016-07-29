/**
 *
 */
package com.mcfht.realisticfluids.commands;

import java.util.ArrayList;
import java.util.List;

import com.mcfht.realisticfluids.FluidConfig;
import com.mcfht.realisticfluids.RealisticFluids;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Property;

/**
 * @author Keybounce
 *
 */
public class CommandEnableFlow extends CommandBase
{
    private List<String> aliases;

    public CommandEnableFlow()
    {
        super();
        aliases = new ArrayList<String>();
        aliases.add("enableflow");
    }

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
        return "rff-enableflow";
    }

    /* (non-Javadoc)
     * @see net.minecraft.command.ICommand#getCommandUsage(net.minecraft.command.ICommandSender)
     */
    @Override
    public String getCommandUsage(ICommandSender p_71518_1_)
    {
        // Fixme needs localizaion
        return "rff-enableflow <true|false>";
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args)
    {
        if (1 == args.length)
        {
            return (List<String>) getListOfStringsMatchingLastWord(args, new String[] {"true", "false"});
        }
      return null;
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

        if (!world.isRemote)
        {
            if(args.length != 1)
            {
                throw new WrongUsageException("Usage: " + getCommandUsage(sender), new Object[0]);
            }
            RealisticFluids.FlowEnabled = parseBoolean(sender, args[0]);
            sender.addChatMessage(new ChatComponentText("Liquid flow set to" + RealisticFluids.FlowEnabled));

            Property p = FluidConfig.config.get(FluidConfig.GENERAL, "FlowEnabled", RealisticFluids.FlowEnabled);
            p.set(RealisticFluids.FlowEnabled);
            FluidConfig.config.save();
        }
    }

    @Override
    public List<String> getCommandAliases()
    {
        return this.aliases;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender)
    {
        return MinecraftServer.getServer().isSinglePlayer() || super.canCommandSenderUseCommand(sender);
    }

    /**
     * Return the required permission level for this command.
     */
    public int getRequiredPermissionLevel()
    {
        return 2;
    }
}

/**
 * 
 */
package com.mcfht.realisticfluids.commands;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/**
 * @author Keybounce
 *
 */
public class CommandEnableFlow implements ICommand {
    private final List aliases;
        

    public CommandEnableFlow()
    {
      this.aliases = new ArrayList();
      this.aliases.add("EnableFlow");
      this.aliases.add("Flow");
    }


	/* (non-Javadoc)
	 * @see net.minecraft.command.ICommand#getCommandName()
	 */
	@Override
	public String getCommandName() {
		return "EnableFlow";
	}

	/* (non-Javadoc)
	 * @see net.minecraft.command.ICommand#getCommandUsage(net.minecraft.command.ICommandSender)
	 */
	@Override
	public String getCommandUsage(ICommandSender icommandsender) {
		return "EnableFlow <True|False>";
	}

	/* (non-Javadoc)
	 * @see net.minecraft.command.ICommand#getCommandAliases()
	 */
	@Override
	public List getCommandAliases() {
		return this.aliases;
	}

	/* (non-Javadoc)
	 * @see net.minecraft.command.ICommand#processCommand(net.minecraft.command.ICommandSender, java.lang.String[])
	 */
	@Override
	public void processCommand(ICommandSender sender, String[] args) {
		// TODO  stub

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
            sender.addChatMessage(new ChatComponentText(args.toString())); 
        }

	}

	/* (non-Javadoc)
	 * @see net.minecraft.command.ICommand#canCommandSenderUseCommand(net.minecraft.command.ICommandSender)
	 */
	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		// TODO  stub
		return true;
	}

	/* (non-Javadoc)
	 * @see net.minecraft.command.ICommand#addTabCompletionOptions(net.minecraft.command.ICommandSender, java.lang.String[])
	 */
	@Override
	public List addTabCompletionOptions(ICommandSender sender,
			String[] astring) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see net.minecraft.command.ICommand#isUsernameIndex(java.lang.String[], int)
	 */
	@Override
	public boolean isUsernameIndex(String[] p_82358_1_, int p_82358_2_) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(Object o) {
		// TODO Auto-generated method stub
		return 0;
	}

}

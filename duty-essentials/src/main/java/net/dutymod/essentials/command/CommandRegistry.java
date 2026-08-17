package net.dutymod.essentials.command;

import net.dutymod.essentials.command.chat.BroadcastCommand;
import net.dutymod.essentials.command.chat.ReplyCommand;
import net.dutymod.essentials.command.inventory.EnderChestCommand;
import net.dutymod.essentials.command.inventory.InvseeCommand;
import net.dutymod.essentials.command.player.*;
import net.dutymod.essentials.command.teleportation.level.RTPCommand;
import net.dutymod.essentials.command.teleportation.level.spawn.SetSpawnCommand;
import net.dutymod.essentials.command.teleportation.level.spawn.SpawnCommand;
import net.dutymod.essentials.command.teleportation.level.warp.DeleteWarpCommand;
import net.dutymod.essentials.command.teleportation.level.warp.SetWarpCommand;
import net.dutymod.essentials.command.teleportation.level.warp.WarpCommand;
import net.dutymod.essentials.command.teleportation.player.back.BackCommand;
import net.dutymod.essentials.command.teleportation.player.home.DeleteHomeCommand;
import net.dutymod.essentials.command.teleportation.player.home.HomeCommand;
import net.dutymod.essentials.command.teleportation.player.home.SetHomeCommand;
import net.dutymod.essentials.command.teleportation.player.tpa.*;
import net.dutymod.essentials.command.time.DayCommand;
import net.dutymod.essentials.command.time.MidnightCommand;
import net.dutymod.essentials.command.time.NightCommand;
import net.dutymod.essentials.command.time.NoonCommand;
import net.dutymod.essentials.command.weather.RainCommand;
import net.dutymod.essentials.command.weather.SunCommand;
import net.dutymod.essentials.command.weather.ThunderCommand;

import java.util.ArrayList;
import java.util.List;

public interface CommandRegistry {

    List<Command> COMMANDS = new ArrayList<>();

    Command SPAWN = register(new SpawnCommand());
    Command SET_SPAWN = register(new SetSpawnCommand());

    Command RTP = register(new RTPCommand());

    Command WARP = register(new WarpCommand());
    Command SET_WARP = register(new SetWarpCommand());
    Command DEL_WARP = register(new DeleteWarpCommand());

    Command HOME = register(new HomeCommand());
    Command SET_HOME = register(new SetHomeCommand());
    Command DEL_HOME = register(new DeleteHomeCommand());

    Command BACK = register(new BackCommand());

    Command TPA = register(new TPACommand());
    Command TPA_HERE = register(new TPAHereCommand());
    Command TPA_ACCEPT = register(new TPAcceptCommand());
    Command TPA_DENY = register(new TPADenyCommand());
    Command TPA_TOGGLE = register(new TPAToggleCommand());

    Command DAY = register(new DayCommand());
    Command NOON = register(new NoonCommand());
    Command NIGHT = register(new NightCommand());
    Command MIDNIGHT = register(new MidnightCommand());

    Command SUN = register(new SunCommand());
    Command RAIN = register(new RainCommand());
    Command THUNDER = register(new ThunderCommand());

    Command NICK = register(new NickCommand());
    Command DEL_NICK = register(new DelNickCommand());

    Command AFK = register(new AFKCommand());
    Command FLY = register(new FlyCommand());
    Command HEAL = register(new HealCommand());
    Command FEED = register(new FeedCommand());

    Command VANISH = register(new VanishCommand());

    Command INVSEE = register(new InvseeCommand());
    Command ENDER_CHEST = register(new EnderChestCommand());

    Command REPLY = register(new ReplyCommand());
    Command BROADCAST = register(new BroadcastCommand());

    Command GOD = register(new GodCommand());
    Command GAMEMODE = register(new GamemodeCommand());


    static Command register(Command command) {
        COMMANDS.add(command);
        return command;
    }
}
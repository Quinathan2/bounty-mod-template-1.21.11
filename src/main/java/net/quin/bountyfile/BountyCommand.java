package net.quin.bountyfile;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.quin.bountyfile.gui.BountyGUI;
import net.quin.bountyfile.gui.BountyScreenHandler;

public class BountyCommand {

    // Register all /bounty commands
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(CommandManager.literal("bounty")
                    // Open GUI: /bounty gui
                    .then(CommandManager.literal("gui")
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                BountyGUI.open(player);
                                return 1;
                            })
                    )

                    // /bounty <player> <amount>
                    .then(CommandManager.argument("target", EntityArgumentType.player())
                            .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                    .executes(ctx -> {
                                        try {
                                            return BCommands.setBounty(ctx);
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                            )
                    )

                    // /bounty list
                    .then(CommandManager.literal("list")
                            .executes(ctx -> BCommands.listBounties(ctx))
                    )

                    // /bounty remove <player> (OP only)
                    .then(CommandManager.literal("remove")
                            .requires(BCommands::isOp)
                            .then(CommandManager.argument("target", EntityArgumentType.player())
                                    .executes(ctx -> {
                                        try {
                                            return BCommands.removeBounty(ctx);
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                            )
                    )

                    // /bounty edit <player> <amount> (OP only)
                    .then(CommandManager.literal("edit")
                            .requires(BCommands::isOp)
                            .then(CommandManager.argument("target", EntityArgumentType.player())
                                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                            .executes(ctx -> {
                                                try {
                                                    return BCommands.editBounty(ctx);
                                                } catch (Exception e) {
                                                    throw new RuntimeException(e);
                                                }
                                            })
                                    )
                            )
                    )

                    // /bounty claim <player> (OP only)
                    .then(CommandManager.literal("claim")
                            .requires(BCommands::isOp)
                            .then(CommandManager.argument("target", EntityArgumentType.player())
                                    .executes(ctx -> {
                                        try {
                                            return BCommands.claimBounty(ctx);
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                            )
                    )

                    // /bounty top
                    .then(CommandManager.literal("top")
                            .executes(ctx -> BCommands.topBounties(ctx))
                    )

                    // /bounty info <player>
                    .then(CommandManager.literal("info")
                            .then(CommandManager.argument("target", EntityArgumentType.player())
                                    .executes(ctx -> {
                                        try {
                                            return BCommands.infoBounty(ctx);
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                            )
                    )

                    // /bounty help
                    .then(CommandManager.literal("help")
                            .executes(ctx -> BCommands.help(ctx))
                    )
            );
        });
    }
}
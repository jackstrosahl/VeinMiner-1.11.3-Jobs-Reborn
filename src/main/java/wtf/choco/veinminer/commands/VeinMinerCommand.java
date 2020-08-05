package wtf.choco.veinminer.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Enums;
import com.google.common.collect.Iterables;

import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import wtf.choco.veinminer.VeinMiner;
import wtf.choco.veinminer.api.ActivationStrategy;
import wtf.choco.veinminer.api.VeinMinerManager;
import wtf.choco.veinminer.data.BlockList;
import wtf.choco.veinminer.data.PlayerPreferences;
import wtf.choco.veinminer.data.block.VeinBlock;
import wtf.choco.veinminer.pattern.VeinMiningPattern;
import wtf.choco.veinminer.tool.ToolCategory;
import wtf.choco.veinminer.tool.ToolTemplateMaterial;
import wtf.choco.veinminer.utils.Chat;
import wtf.choco.veinminer.utils.ConfigWrapper;
import wtf.choco.veinminer.utils.UpdateChecker;
import wtf.choco.veinminer.utils.UpdateChecker.UpdateResult;
import wtf.choco.veinminer.utils.VMConstants;

public final class VeinMinerCommand implements TabExecutor {

    private static final List<String> BLOCK_KEYS = Arrays.stream(Material.values()).filter(Material::isBlock).map(m -> m.getKey().toString()).collect(Collectors.toList());
    private static final List<String> ITEM_KEYS = Arrays.stream(Material.values()).filter(Material::isItem).map(m -> m.getKey().toString()).collect(Collectors.toList());

    private final VeinMiner plugin;

    public VeinMinerCommand(VeinMiner plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
        if (args.length == 0) {
            Chat.PREFIXED.translateSend(sender, "%rInvalid command syntax! %gMissing parameter. %y/veinminer <reload|version|blocklist|toggle|pattern>", ChatColor.RED, ChatColor.GRAY, ChatColor.YELLOW);
            return true;
        }

        // Reload subcommand
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(VMConstants.PERMISSION_RELOAD)) {
                Chat.PREFIXED.translateSend(sender, "%rYou have insufficient permissions to execute this command", ChatColor.RED);
                return true;
            }

            this.plugin.reloadConfig();
            ToolCategory.clearCategories();

            VeinMinerManager manager = plugin.getVeinMinerManager();
            manager.loadToolCategories();
            manager.loadVeinableBlocks();
            manager.loadMaterialAliases();

            Chat.PREFIXED.translateSend(sender, "%gVeinMiner configuration successfully reloaded", ChatColor.GREEN);
        }

        // Version subcommand
        else if (args[0].equalsIgnoreCase("version")) {
            Chat.MESSAGE.translateSend(sender, "%g%b%s--------------------------------------------", ChatColor.GOLD, ChatColor.BOLD, ChatColor.STRIKETHROUGH);
            sender.sendMessage("");
            sender.sendMessage(Chat.translate("%a%bVersion: %g", ChatColor.DARK_AQUA, ChatColor.GOLD, ChatColor.GRAY) + plugin.getDescription().getVersion() + getUpdateSuffix());
            sender.sendMessage(Chat.translate("%a%bDeveloper: %gChoco %y(https://choco.wtf)", ChatColor.DARK_AQUA, ChatColor.GOLD, ChatColor.GRAY, ChatColor.YELLOW));
            sender.sendMessage(Chat.translate("%a%bPlugin Page: %ghttps://www.spigotmc.org/resources/veinminer.12038", ChatColor.DARK_AQUA, ChatColor.GOLD, ChatColor.GRAY));
            sender.sendMessage(Chat.translate("%a%bReport bugs to: %ghttps://github.com/2008Choco/VeinMiner/issues", ChatColor.DARK_AQUA, ChatColor.GOLD, ChatColor.GRAY));
            sender.sendMessage("");
            Chat.MESSAGE.translateSend(sender, "%g%b%s--------------------------------------------", ChatColor.GOLD, ChatColor.BOLD, ChatColor.STRIKETHROUGH);
        }

        // Toggle subcommand
        else if (args[0].equalsIgnoreCase("toggle")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("VeinMiner cannot be toggled from the console...");
                return true;
            }

            Player player = (Player) sender;
            if (!canVeinMine(player)) {
                Chat.PREFIXED.translateSend(player, "%rYou may not toggle a feature to which you do not have access", ChatColor.RED);
                return true;
            }

            if (!player.hasPermission(VMConstants.PERMISSION_TOGGLE)) {
                Chat.PREFIXED.translateSend(player, "%rYou have insufficient permissions to execute this command", ChatColor.RED);
                return true;
            }

            PlayerPreferences playerData = PlayerPreferences.get(player);
            // Toggle a specific tool
            if (args.length >= 2) {
                ToolCategory category = ToolCategory.get(args[1]);
                if (category == null) {
                    Chat.PREFIXED.translateSend(player, "Invalid tool category: %y" + args[1], ChatColor.YELLOW);
                    return true;
                }

                playerData.setVeinMinerEnabled(!playerData.isVeinMinerEnabled(), category);
                player.sendMessage(VeinMiner.CHAT_PREFIX + "VeinMiner successfully toggled "
                    + (playerData.isVeinMinerDisabled(category) ? ChatColor.RED + "off" : ChatColor.GREEN + "on")
                    + ChatColor.GRAY + " for tool " + ChatColor.YELLOW + category.getId().toLowerCase());
            }

            // Toggle all tools
            else {
                playerData.setVeinMinerEnabled(!playerData.isVeinMinerEnabled());
                player.sendMessage(VeinMiner.CHAT_PREFIX + "VeinMiner successfully toggled "
                    + (playerData.isVeinMinerEnabled() ? ChatColor.GREEN + "on" : ChatColor.RED + "off")
                    + ChatColor.GRAY + " for " + ChatColor.YELLOW + "all tools");
            }
        }

        // Mode subcommand
        else if (args[0].equalsIgnoreCase("mode")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("VeinMiner cannot change mode from the console...");
                return true;
            }

            Player player = (Player) sender;
            if (!canVeinMine(player)) {
                Chat.PREFIXED.translateSend(player, "%rYou may not toggle a feature to which you do not have access", ChatColor.RED);
                return true;
            }

            if (!player.hasPermission(VMConstants.PERMISSION_MODE)) {
                Chat.PREFIXED.translateSend(player, "%rYou have insufficient permissions to execute this command", ChatColor.RED);
                return true;
            }

            if (args.length < 2) {
                Chat.PREFIXED.translateSend(sender, "%rInvalid command syntax! %gMissing parameter(s) %y/" + label + " mode <sneak|stand|always>", ChatColor.RED, ChatColor.GRAY, ChatColor.YELLOW);
                return true;
            }

            ActivationStrategy strategy = Enums.getIfPresent(ActivationStrategy.class, args[1].toUpperCase()).orNull();
            if (strategy == null) {
                Chat.PREFIXED.translateSend(sender, "Invalid activation strategy: %y" + args[1], ChatColor.YELLOW);
                return true;
            }

            PlayerPreferences.get(player).setActivationStrategy(strategy);
            Chat.PREFIXED.translateSend(player, "%gActivation mode successfully changed to %y" + strategy.name().toLowerCase().replace("_", " "), ChatColor.GREEN, ChatColor.YELLOW);
        }

        // Blocklist subcommand
        else if (args[0].equalsIgnoreCase("blocklist")) {
            if (args.length < 2) {
                Chat.PREFIXED.translateSend(sender, "%rInvalid command syntax! %gMissing parameter(s) %y/" + label + " blocklist <tool> <add|remove|list>", ChatColor.RED, ChatColor.GRAY, ChatColor.YELLOW);
                return true;
            }

            ToolCategory category = ToolCategory.get(args[1]);

            if (category == null) {
                Chat.PREFIXED.translateSend(sender, "Invalid tool category: %y" + args[1], ChatColor.YELLOW);
                return true;
            }

            if (args.length < 3) {
                Chat.PREFIXED.translateSend(sender, "%rInvalid command syntax! %gMissing parameter(s) %y/" + label + " blocklist " + args[1] + " <add|remove|list>", ChatColor.RED, ChatColor.GRAY, ChatColor.YELLOW);
                return true;
            }

            // /veinminer blocklist <tool> add
            if (args[2].equalsIgnoreCase("add")) {
                if (!sender.hasPermission(VMConstants.PERMISSION_BLOCKLIST_ADD)) {
                    Chat.PREFIXED.translateSend(sender, "%rYou have insufficient permissions to execute this command", ChatColor.RED);
                    return true;
                }

                if (args.length < 4) {
                    Chat.PREFIXED.translateSend(sender, "%rInvalid command syntax! %gMissing parameter(s) %y/" + label + " blocklist " + args[1] + " add <block>[[data]]", ChatColor.RED, ChatColor.GRAY, ChatColor.YELLOW);
                    return true;
                }

                BlockList blocklist = category.getBlocklist();
                List<String> configBlocklist = plugin.getConfig().getStringList("BlockList." + category.getId());

                for (int i = 3; i < args.length; i++) {
                    String blockArg = args[i].toLowerCase();
                    VeinBlock block = VeinBlock.fromString(blockArg);
                    if (block == null) {
                        Chat.PREFIXED.translateSend(sender, "%rUnknown block type (was it an item?) and/or block states. Given %y" + blockArg, ChatColor.RED, ChatColor.YELLOW);
                        continue;
                    }

                    if (blocklist.contains(block)) {
                        Chat.PREFIXED.translateSend(sender, "A block with the ID %y" + blockArg + " %gis already on the %y" + args[1].toLowerCase() + " %gblocklist", ChatColor.YELLOW, ChatColor.GRAY);
                        continue;
                    }

                    blocklist.add(block);
                    configBlocklist.add(block.asDataString());
                    this.plugin.getConfig().set("BlockList." + category.getId(), configBlocklist);

                    Chat.PREFIXED.translateSend(sender, "Block ID %y" + block.asDataString() + " %gsuccessfully added to the blocklist", ChatColor.YELLOW, ChatColor.GRAY);
                }


                this.plugin.saveConfig();
                this.plugin.reloadConfig();
            }

            // /veinminer blocklist <category> remove
            else if (args[2].equalsIgnoreCase("remove")) {
                if (!sender.hasPermission(VMConstants.PERMISSION_BLOCKLIST_REMOVE)) {
                    Chat.PREFIXED.translateSend(sender, "%rYou have insufficient permissions to execute this command", ChatColor.RED);
                    return true;
                }

                if (args.length < 4) {
                    Chat.PREFIXED.translateSend(sender, "%rInvalid command syntax! %gMissing parameter %y/" + label + " blocklist " + args[1] + " remove <block>[[data]]", ChatColor.RED, ChatColor.GRAY, ChatColor.YELLOW);
                    return true;
                }

                BlockList blocklist = category.getBlocklist();
                List<String> configBlocklist = plugin.getConfig().getStringList("BlockList." + category.getId());

                for (int i = 3; i < args.length; i++) {
                    String blockArg = args[i].toLowerCase();
                    VeinBlock block = VeinBlock.fromString(blockArg);
                    if (block == null) {
                        Chat.PREFIXED.translateSend(sender, "%rUnknown block type (was it an item?) and/or block states. Given %y" + blockArg, ChatColor.RED, ChatColor.YELLOW);
                        continue;
                    }

                    if (!blocklist.contains(block)) {
                        Chat.PREFIXED.translateSend(sender, "No block with the ID %y" + blockArg + " %gwas found on the %y" + args[1].toLowerCase() + " %gblocklist", ChatColor.YELLOW, ChatColor.GRAY);
                        continue;
                    }

                    blocklist.remove(block);
                    configBlocklist.remove(block.asDataString());
                    this.plugin.getConfig().set("BlockList." + category.getId(), configBlocklist);

                    Chat.PREFIXED.translateSend(sender, "Block ID %y" + block.asDataString() + " %gsuccessfully removed from the blocklist", ChatColor.YELLOW, ChatColor.GRAY);
                }

                this.plugin.saveConfig();
                this.plugin.reloadConfig();
            }

            // /veinminer blocklist <tool> list
            else if (args[2].equalsIgnoreCase("list")) {
                if (!sender.hasPermission(VMConstants.PERMISSION_BLOCKLIST_LIST + "." + category.getId().toLowerCase())) {
                    Chat.PREFIXED.translateSend(sender, "%rYou have insufficient permissions to execute this command", ChatColor.RED);
                    return true;
                }

                Iterable<VeinBlock> blocklistIterable;
                if (plugin.getConfig().getBoolean("SortBlocklistAlphabetically", true)) {
                    blocklistIterable = new ArrayList<>();
                    Iterables.addAll((List<VeinBlock>) blocklistIterable, category.getBlocklist());
                    Collections.sort((List<VeinBlock>) blocklistIterable);
                } else {
                    blocklistIterable = category.getBlocklist();
                }

                if (Iterables.isEmpty(blocklistIterable)) {
                    Chat.MESSAGE.translateSend(sender, "%yThe " + category.getId() + " category is empty.", ChatColor.YELLOW);
                    return true;
                }

                Chat.MESSAGE.translateSend(sender, "%y%bVeinMiner Blocklist (Category = " + category.getId() + ")", ChatColor.YELLOW, ChatColor.BOLD);
                blocklistIterable.forEach(block -> sender.sendMessage(ChatColor.YELLOW + "  - " + block.asDataString()));
            }

            // Unknown parameter
            else {
                Chat.PREFIXED.translateSend(sender, "%rInvalid command syntax! %gUnknown parameter %a" + args[2] + "%g. %y/" + label + " blocklist " + args[1] + " <add|remove|list>", ChatColor.RED, ChatColor.GRAY, ChatColor.AQUA, ChatColor.YELLOW);
                return true;
            }
        }

        else if (args[0].equalsIgnoreCase("toollist")) {
            if (args.length < 2) {
                Chat.PREFIXED.translateSend(sender, "%rInvalid command syntax! %gMissing parameter(s) %y/" + label + " toollist <tool> <add|remove|list>", ChatColor.RED, ChatColor.GRAY, ChatColor.YELLOW);
                return true;
            }

            ToolCategory category = ToolCategory.get(args[1]);

            if (category == null) {
                Chat.PREFIXED.translateSend(sender, "Invalid tool category: %y" + args[1], ChatColor.YELLOW);
                return true;
            }

            if (category == ToolCategory.HAND) {
                Chat.PREFIXED.translateSend(sender, "%rThe hand category cannot be modified", ChatColor.RED);
                return true;
            }

            if (args.length < 3) {
                Chat.PREFIXED.translateSend(sender, "%rInvalid command syntax! %gMissing parameter(s) %y/" + label + " toollist " + args[1] + " <add|remove|list>", ChatColor.RED, ChatColor.GRAY, ChatColor.YELLOW);
                return true;
            }

            // /veinminer toollist <tool> add
            if (args[2].equalsIgnoreCase("add")) {
                if (!sender.hasPermission(VMConstants.PERMISSION_TOOLLIST_ADD)) {
                    Chat.PREFIXED.translateSend(sender, "%rYou have insufficient permissions to execute this command", ChatColor.RED);
                    return true;
                }

                if (args.length < 4) {
                    Chat.PREFIXED.translateSend(sender, "%rInvalid command syntax! %gMissing parameter(s) %y/" + label + " toollist " + args[1] + " add <item>", ChatColor.RED, ChatColor.GRAY, ChatColor.YELLOW);
                    return true;
                }

                ConfigWrapper categoriesConfigWrapper = plugin.getCategoriesConfig();
                FileConfiguration categoriesConfig = categoriesConfigWrapper.asRawConfig();
                @SuppressWarnings("unchecked")
                List<Object> configToolList = (List<Object>) categoriesConfig.getList(category.getId() + ".Items", new ArrayList<>());
                if (configToolList == null) {
                    Chat.PREFIXED.translateSend(sender, "%rSomething went wrong... is the categories.yml formatted properly?", ChatColor.RED);
                    return true;
                }

                for (int i = 3; i < args.length; i++) {
                    String toolArg = args[i].toLowerCase();
                    Material tool = Material.matchMaterial(toolArg);
                    if (tool == null) {
                        Chat.PREFIXED.translateSend(sender, "%rUnknown item. Given %y" + toolArg, ChatColor.RED, ChatColor.YELLOW);
                        continue;
                    }

                    if (category.containsTool(tool)) {
                        Chat.PREFIXED.translateSend(sender, "An item with the ID %y" + toolArg + " %gis already on the %y" + args[1].toLowerCase() + " %gtool list", ChatColor.YELLOW, ChatColor.GRAY);
                        continue;
                    }

                    configToolList.add(tool.getKey().toString());
                    category.addTool(new ToolTemplateMaterial(category, tool));
                    categoriesConfig.set(category.getId() + ".Items", configToolList);

                    Chat.PREFIXED.translateSend(sender, "Item ID %y" + tool.getKey() + " %gsuccessfully added to the tool list", ChatColor.YELLOW, ChatColor.GRAY);
                }

                categoriesConfigWrapper.save();
                categoriesConfigWrapper.reload();
            }

            // /veinminer toollist <tool> remove
            else if (args[2].equalsIgnoreCase("remove")) {
                if (!sender.hasPermission(VMConstants.PERMISSION_TOOLLIST_REMOVE)) {
                    Chat.PREFIXED.translateSend(sender, "%rYou have insufficient permissions to execute this command", ChatColor.RED);
                    return true;
                }

                if (args.length < 4) {
                    Chat.PREFIXED.translateSend(sender, "%rInvalid command syntax! %gMissing parameter(s) %y/" + label + " toollist " + args[1] + " remove <item>", ChatColor.RED, ChatColor.GRAY, ChatColor.YELLOW);
                    return true;
                }

                ConfigWrapper categoriesConfigWrapper = plugin.getCategoriesConfig();
                FileConfiguration categoriesConfig = categoriesConfigWrapper.asRawConfig();
                @SuppressWarnings("unchecked")
                List<Object> configToolList = (List<Object>) categoriesConfig.getList(category.getId() + ".Items", new ArrayList<>());
                if (configToolList == null) {
                    Chat.PREFIXED.translateSend(sender, "%rSomething went wrong... is the categories.yml formatted properly?", ChatColor.RED);
                    return true;
                }

                for (int i = 3; i < args.length; i++) {
                    String toolArg = args[i].toLowerCase();
                    Material tool = Material.matchMaterial(toolArg);
                    if (tool == null) {
                        Chat.PREFIXED.translateSend(sender, "%rUnknown item. Given %y" + toolArg, ChatColor.RED, ChatColor.YELLOW);
                        continue;
                    }

                    if (!category.containsTool(tool)) {
                        Chat.PREFIXED.translateSend(sender, "An item with the ID %y" + toolArg + " %gis not on the %y" + args[1].toLowerCase() + " %gtool list", ChatColor.YELLOW, ChatColor.GRAY);
                        continue;
                    }

                    configToolList.remove(tool.getKey().toString());
                    category.removeTool(tool);
                    categoriesConfig.set(category.getId() + ".Items", configToolList);

                    Chat.PREFIXED.translateSend(sender, "Item ID %y" + tool.getKey() + " %gsuccessfully removed from the tool list", ChatColor.YELLOW, ChatColor.GRAY);
                }

                categoriesConfigWrapper.save();
                categoriesConfigWrapper.reload();
            }

            // /veinminer toollist <tool> list
            else if (args[2].equalsIgnoreCase("list")) {
                if (!sender.hasPermission(VMConstants.PERMISSION_TOOLLIST_LIST + "." + category.getId().toLowerCase())) {
                    Chat.PREFIXED.translateSend(sender, "%rYou have insufficient permissions to execute this command", ChatColor.RED);
                    return true;
                }

                Chat.PREFIXED.translateSend(sender, "%y%bVeinMiner Blocklist (Category = " + category.getId() + ")", ChatColor.YELLOW, ChatColor.BOLD);
                category.getTools().forEach(tool -> sender.sendMessage(ChatColor.YELLOW + "  - " + tool));
            }
        }

        else if (args[0].equalsIgnoreCase("pattern")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("VeinMiner patterns cannot be changed from the console...");
                return true;
            }

            if (!sender.hasPermission(VMConstants.PERMISSION_PATTERN)) {
                Chat.PREFIXED.translateSend(sender, "%rYou have insufficient permissions to execute this command", ChatColor.RED);
                return true;
            }

            if (args.length < 2) {
                Chat.PREFIXED.translateSend(sender, "%rInvalid command syntax! %gMissing parameter. %y/" + label + " pattern <pattern_id>", ChatColor.RED, ChatColor.GRAY, ChatColor.YELLOW);
                return true;
            }

            Player player = (Player) sender;
            String patternNamespace = args[1].toLowerCase();

            if (!patternNamespace.contains(":")) {
                patternNamespace = plugin.getName().toLowerCase() + ":" + patternNamespace;
            } else if (patternNamespace.startsWith(":") || patternNamespace.split(":").length > 2) {
                Chat.PREFIXED.translateSend(player, "Invalid ID. Pattern IDs should be formatted as %ynamespace:id %g(i.e. %yveinminer:expansive%g)", ChatColor.YELLOW, ChatColor.GRAY);
                return true;
            }

            VeinMiningPattern pattern = plugin.getPatternRegistry().getPattern(patternNamespace);
            if (pattern == null) {
                Chat.PREFIXED.translateSend(player, "A pattern with the ID %y" + patternNamespace + "%g could not be found", ChatColor.YELLOW, ChatColor.GRAY);
                return true;
            }

            this.plugin.setVeinMiningPattern(pattern);
            Chat.PREFIXED.translateSend(player, "Pattern successfully changed to %y" + patternNamespace, ChatColor.YELLOW);
        }

        // Unknown command usage
        else {
            Chat.PREFIXED.translateSend(sender, "%rInvalid command syntax! %gUnknown parameter, %a" + args[0] + "%g. %y/" + label + " <version|reload|blocklist|toollist|toggle|pattern>", ChatColor.RED, ChatColor.GRAY, ChatColor.AQUA, ChatColor.YELLOW);
            return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        List<String> values = new ArrayList<>();
        if (args.length == 1) {
            values.add("version");
            if (sender.hasPermission(VMConstants.PERMISSION_RELOAD)) {
                values.add("reload");
            }
            if (sender.hasPermission(VMConstants.PERMISSION_RELOAD)) {
                values.add("toggle");
            }
            if (sender.hasPermission(VMConstants.PERMISSION_MODE)) {
                values.add("mode");
            }
            if (hasBlocklistPerms(sender)) {
                values.add("blocklist");
            }
            if (hasToolListPerms(sender)) {
                values.add("toollist");
            }
            if (sender.hasPermission(VMConstants.PERMISSION_PATTERN)) {
                values.add("pattern");
            }
        }

        else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("toggle") || args[0].equalsIgnoreCase("blocklist") || args[0].equalsIgnoreCase("toollist")) {
                for (ToolCategory category : ToolCategory.getAll()) {
                    values.add(category.getId().toLowerCase());
                }

                if (args[0].equalsIgnoreCase("toollist")) {
                    values.remove("hand"); // Cannot modify the hand's tool list
                }
            }

            else if (args[0].equalsIgnoreCase("mode")) {
                for (ActivationStrategy activationStrategy : ActivationStrategy.values()) {
                    values.add(activationStrategy.name().toLowerCase());
                }
            }

            else if (args[0].equalsIgnoreCase("pattern")) {
                values = plugin.getPatternRegistry().getPatterns().stream()
                        .map(VeinMiningPattern::getKey).map(NamespacedKey::toString)
                        .collect(Collectors.toList());
            }
        }

        else if (args.length == 3) {
            String listType = args[0].toLowerCase();
            if (listType.equals("blocklist") || listType.equals("toollist")) {
                if (sender.hasPermission(String.format(VMConstants.PERMISSION_DYNAMIC_LIST_ADD, listType))) {
                    values.add("add");
                }
                if (sender.hasPermission(String.format(VMConstants.PERMISSION_DYNAMIC_LIST_REMOVE, listType))) {
                    values.add("remove");
                }
                if (sender.hasPermission(String.format(VMConstants.PERMISSION_DYNAMIC_LIST_LIST, listType) + ".*")) {
                    values.add("list");
                }
            }
        }

        else if (args.length >= 4) {
            List<String> suggestions = Collections.emptyList();

            // Prefix arguments with "minecraft:" if necessary
            String materialArg = args[args.length - 1];
            if (!materialArg.startsWith("minecraft:")) {
                materialArg = (materialArg.startsWith(":") ? "minecraft" : "minecraft:") + materialArg;
            }

            // Impossible suggestions (defaults to previous arguments)
            List<String> impossibleSuggestions = new ArrayList<>();
            for (int i = 3; i < args.length - 1; i++) {
                impossibleSuggestions.add(args[i]);
                impossibleSuggestions.add("minecraft:" + args[i]);
            }

            if (args[2].equalsIgnoreCase("add")) {
                if (args[0].equalsIgnoreCase("blocklist")) {
                    suggestions = StringUtil.copyPartialMatches(materialArg, BLOCK_KEYS, new ArrayList<>());
                    ToolCategory.get(args[1]).getBlocklist().forEach(b -> impossibleSuggestions.add(b.getType().getKey().toString()));
                }
                else if (args[0].equalsIgnoreCase("toollist")) {
                    suggestions = StringUtil.copyPartialMatches(materialArg, ITEM_KEYS, new ArrayList<>());
                }
            }

            else if (args[2].equalsIgnoreCase("remove")) {
                if (args[0].equalsIgnoreCase("blocklist")) {
                    BlockList blocklist = ToolCategory.get(args[1]).getBlocklist();
                    List<String> blocklistSuggestions = new ArrayList<>(blocklist.size());
                    blocklist.forEach(v -> blocklistSuggestions.add(v.asDataString()));

                    suggestions = StringUtil.copyPartialMatches(materialArg, blocklistSuggestions, new ArrayList<>());
                }
                else if (args[0].equalsIgnoreCase("toollist")) {
                    suggestions = StringUtil.copyPartialMatches(materialArg, ITEM_KEYS, new ArrayList<>());
                }
            }

            suggestions.removeAll(impossibleSuggestions);
            return suggestions;
        }

        else {
            return Collections.emptyList();
        }

        return StringUtil.copyPartialMatches(args[args.length - 1], values, new ArrayList<>());
    }

    public static void assignTo(VeinMiner plugin, String commandString) {
        Validate.notEmpty(commandString);
        PluginCommand command = plugin.getCommand(commandString);
        if (command == null) {
            return;
        }

        VeinMinerCommand tabExecutor = new VeinMinerCommand(plugin);
        command.setExecutor(tabExecutor);
        command.setTabCompleter(tabExecutor);
    }

    private boolean hasBlocklistPerms(CommandSender sender) {
        return sender.hasPermission(VMConstants.PERMISSION_BLOCKLIST_ADD)
            || sender.hasPermission(VMConstants.PERMISSION_BLOCKLIST_REMOVE)
            || sender.hasPermission(VMConstants.PERMISSION_BLOCKLIST_LIST + ".*");
    }

    private boolean hasToolListPerms(CommandSender sender) {
        return sender.hasPermission(VMConstants.PERMISSION_TOOLLIST_ADD)
            || sender.hasPermission(VMConstants.PERMISSION_TOOLLIST_REMOVE)
            || sender.hasPermission(VMConstants.PERMISSION_TOOLLIST_LIST + ".*");
    }

    private boolean canVeinMine(Player player) {
        for (ToolCategory category : ToolCategory.getAll()) {
            if (player.hasPermission(String.format(VMConstants.PERMISSION_DYNAMIC_VEINMINE, category.getId().toLowerCase()))) {
                return true;
            }
        }

        return false;
    }

    private String getUpdateSuffix() {
        if (!plugin.getConfig().getBoolean("PerformUpdateChecks")) {
            return "";
        }

        UpdateResult result = UpdateChecker.get().getLastResult();
        return (result != null && result.requiresUpdate()) ? " (" + ChatColor.GREEN + ChatColor.BOLD + "UPDATE AVAILABLE!" + ChatColor.GRAY + ")" : "";
    }

}

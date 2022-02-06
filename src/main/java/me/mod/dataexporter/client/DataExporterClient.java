package me.mod.dataexporter.client;

import com.google.gson.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Environment(EnvType.CLIENT)
public class DataExporterClient implements ClientModInitializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Logger logger = LoggerFactory.getLogger("DataExporter");

    @Override
    public void onInitializeClient() {
        CommandRegistrationCallback.EVENT.register(((dispatcher, dedicated) -> {
            dispatcher.register(
                    Commands.literal("dataexporter")
                            .then(Commands.literal("export")
                                    .executes(context -> this.export()))
            );
        }));
    }

    public int export() {
        logger.info("Running export");

        NonNullList<ItemStack> items = NonNullList.create();
        JsonObject itemRegistryJson = new JsonObject();

        for (Item item : Registry.ITEM) {
            items.add(new ItemStack(item));

            if (item instanceof PotionItem || item instanceof TippedArrowItem) {
                for (Potion potion : Registry.POTION) {
                    items.add(PotionUtils.setPotion(new ItemStack(item), potion));
                }
            } else if (item instanceof FireworkRocketItem) {
                for (int i = 1; i <= 3; i++) {
                    ItemStack stack = new ItemStack(item);
                    var fireworksTag = new CompoundTag();
                    fireworksTag.putByte("Flight", (byte)i);
                    stack.getOrCreateTag().put("Fireworks", fireworksTag);
                    items.add(stack);
                }
            } else if (item instanceof EnchantedBookItem) {
                for (Enchantment enchantment : Registry.ENCHANTMENT) {
                    for (int i = enchantment.getMinLevel(); i <= enchantment.getMaxLevel(); i++) {
                        items.add(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, i)));
                    }
                }
            }

            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("description_id", item.getDescriptionId());
            if (item.getMaxStackSize() != 64) itemJson.addProperty("max_stack_size", item.getMaxStackSize());
            if (item.getMaxDamage() != 0) itemJson.addProperty("max_damage", item.getMaxDamage());
            itemRegistryJson.add(Registry.ITEM.getKey(item).toString(), itemJson);
        }

        JsonArray itemsJson = new JsonArray();

        for (ItemStack item : items) {
            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("id", Registry.ITEM.getKey(item.getItem()).toString());
            if (item.hasTag()) {
                itemJson.addProperty("tag", item.getTag().getAsString());
            }
            itemJson.addProperty("description_id", item.getDescriptionId());
            JsonArray tooltipLines = new JsonArray();
            for (var component : item.getTooltipLines(null, TooltipFlag.Default.NORMAL)) {
                tooltipLines.add(Component.Serializer.toJsonTree(component));
            }
            itemJson.add("tooltip_lines", tooltipLines);
            itemsJson.add(itemJson);
        }


        try {
            var exportPath = new File(Minecraft.getInstance().gameDirectory.getPath(), "data_export");
            exportPath.mkdir();
            Files.writeString(new File(exportPath, "item_registry.json").toPath(), GSON.toJson(itemRegistryJson));
            Files.writeString(new File(exportPath, "items.json").toPath(), GSON.toJson(itemsJson));
        } catch (IOException e) {
            e.printStackTrace();
            return 1;
        }

        logger.info("Export finished");

        return 0;
    }
}

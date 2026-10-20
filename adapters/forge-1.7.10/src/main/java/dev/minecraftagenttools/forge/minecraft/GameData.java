package dev.minecraftagenttools.forge.minecraft;

import static dev.minecraftagenttools.protocol.data.Data.map;

import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.potion.PotionEffect;

final class GameData {
    private GameData() {}

    static Map<String, Object> nbt(NBTTagCompound tag) {
        String text = tag.toString();
        int max = 16384;
        return map(
                "format",
                "minecraft-1.7.10-nbt-text",
                "text",
                text.substring(0, Math.min(max, text.length())),
                "truncated",
                text.length() > max,
                "originalCharacters",
                text.length());
    }

    static Object stack(ItemStack stack) {
        if (stack == null) return null;
        return map(
                "item",
                Item.itemRegistry.getNameForObject(stack.getItem()),
                "count",
                stack.stackSize,
                "metadata",
                stack.getItemDamage(),
                "damageable",
                stack.isItemStackDamageable(),
                "maxDamage",
                stack.getMaxDamage(),
                "displayName",
                stack.getDisplayName(),
                "enchantments",
                stack.getEnchantmentTagList() == null
                        ? "[]"
                        : stack.getEnchantmentTagList().toString(),
                "nbt",
                stack.hasTagCompound() ? nbt(stack.getTagCompound()) : null);
    }

    static List<Object> inventory(IInventory inventory) {
        List<Object> slots = new ArrayList<Object>();
        for (int i = 0; i < Math.min(inventory.getSizeInventory(), 256); i++)
            slots.add(map("slot", i, "stack", stack(inventory.getStackInSlot(i))));
        return slots;
    }

    static Map<String, Object> container(Container container) {
        List<Object> slots = new ArrayList<Object>();
        for (Object obj : container.inventorySlots) {
            if (slots.size() == 256) break;
            Slot slot = (Slot) obj;
            slots.add(map("slot", slot.slotNumber, "stack", stack(slot.getStack())));
        }
        return map(
                "type",
                container.getClass().getSimpleName(),
                "windowId",
                container.windowId,
                "slots",
                slots,
                "totalSlots",
                container.inventorySlots.size(),
                "truncated",
                container.inventorySlots.size() > slots.size());
    }

    static Map<String, Object> inventory(EntityPlayerMP player) {
        return map(
                "selectedHotbarSlot",
                player.inventory.currentItem,
                "slots",
                inventory(player.inventory),
                "cursorStack",
                stack(player.inventory.getItemStack()),
                "enderChest",
                inventory(player.getInventoryEnderChest()),
                "openContainer",
                container(player.openContainer));
    }

    static Map<String, Object> entity(Entity e, boolean detailed) {
        Map<String, Object> data =
                map(
                        "entityId",
                        e.getEntityId(),
                        "uuid",
                        e.getUniqueID().toString(),
                        "type",
                        e instanceof EntityPlayerMP ? "Player" : EntityList.getEntityString(e),
                        "name",
                        e.getCommandSenderName(),
                        "position",
                        Arrays.asList(e.posX, e.posY, e.posZ),
                        "motion",
                        Arrays.asList(e.motionX, e.motionY, e.motionZ),
                        "yaw",
                        e.rotationYaw,
                        "pitch",
                        e.rotationPitch,
                        "onGround",
                        e.onGround,
                        "dead",
                        e.isDead,
                        "burning",
                        e.isBurning(),
                        "inWater",
                        e.isInWater(),
                        "sneaking",
                        e.isSneaking(),
                        "sprinting",
                        e.isSprinting(),
                        "ridingEntityId",
                        e.ridingEntity == null ? null : e.ridingEntity.getEntityId());
        if (e instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) e;
            List<Object> effects = new ArrayList<Object>();
            for (Object o : living.getActivePotionEffects()) {
                PotionEffect effect = (PotionEffect) o;
                effects.add(
                        map(
                                "id",
                                effect.getPotionID(),
                                "amplifier",
                                effect.getAmplifier(),
                                "durationTicks",
                                effect.getDuration()));
            }
            data.putAll(
                    map(
                            "health",
                            living.getHealth(),
                            "maxHealth",
                            living.getMaxHealth(),
                            "absorption",
                            living.getAbsorptionAmount(),
                            "armor",
                            living.getTotalArmorValue(),
                            "effects",
                            effects));
            if (detailed) {
                List<Object> equipment = new ArrayList<Object>();
                for (int i = 0; i <= 4; i++)
                    equipment.add(map("slot", i, "stack", stack(living.getEquipmentInSlot(i))));
                data.put("equipment", equipment);
            }
        }
        if (e instanceof EntityItem) data.put("stack", stack(((EntityItem) e).getEntityItem()));
        if (e instanceof EntityPlayerMP) {
            EntityPlayerMP p = (EntityPlayerMP) e;
            data.putAll(
                    map(
                            "food",
                            p.getFoodStats().getFoodLevel(),
                            "saturation",
                            p.getFoodStats().getSaturationLevel(),
                            "air",
                            p.getAir(),
                            "experienceLevel",
                            p.experienceLevel,
                            "experienceProgress",
                            p.experience,
                            "experienceTotal",
                            p.experienceTotal,
                            "gameMode",
                            p.theItemInWorldManager.getGameType().getName(),
                            "flying",
                            p.capabilities.isFlying,
                            "allowFlying",
                            p.capabilities.allowFlying,
                            "sleeping",
                            p.isPlayerSleeping()));
        }
        if (detailed) {
            NBTTagCompound tag = new NBTTagCompound();
            e.writeToNBT(tag);
            data.put("nbt", nbt(tag));
        }
        return data;
    }
}

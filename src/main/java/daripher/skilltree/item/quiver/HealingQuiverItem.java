package daripher.skilltree.item.quiver;

import java.util.UUID;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import daripher.skilltree.init.PSTAttributes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

public class HealingQuiverItem extends QuiverItem {
	public HealingQuiverItem() {
		super(150);
	}
	
	@Override
	public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
		Multimap<Attribute, AttributeModifier> modifiers = LinkedHashMultimap.create();
		modifiers.put(PSTAttributes.INCOMING_HEALING.get(), new AttributeModifier(uuid, "QuiverBonus", 0.05, Operation.MULTIPLY_BASE));
		return modifiers;
	}
}

package daripher.skilltree.mixin.minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import daripher.skilltree.item.ItemHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeItemStack;

@Mixin(ItemStack.class)
public abstract class MixinItemStack implements IForgeItemStack {
	@Inject(method = "getMaxDamage", at = @At("RETURN"), cancellable = true)
	private void getMaxDamage(CallbackInfoReturnable<Integer> callback) {
		ItemStack stack = (ItemStack) (Object) this;
		if (!ItemHelper.hasBonus(stack, ItemHelper.DURABILITY)) return;
		double durabilityBonus = ItemHelper.getBonus(stack, ItemHelper.DURABILITY);
		callback.setReturnValue((int) (callback.getReturnValue() * (1 + durabilityBonus)));
	}
}

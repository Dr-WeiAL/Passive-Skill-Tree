package daripher.skilltree.mixin.minecraft;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import daripher.skilltree.potion.PotionHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;

@Mixin(PotionUtils.class)
public class MixinPotionUtils {
	@Inject(method = "getMobEffects", at = @At("RETURN"), cancellable = true)
	private static void applySuperiorPotionBonuses(ItemStack stack, CallbackInfoReturnable<List<MobEffectInstance>> callback) {
		if (!PotionHelper.isSuperiorPotion(stack)) return;
		callback.setReturnValue(enhanceEffects(stack, callback.getReturnValue()));
	}

	private static List<MobEffectInstance> enhanceEffects(ItemStack stack, List<MobEffectInstance> effects) {
		var enhancedEffects = new ArrayList<MobEffectInstance>();
		float durationBonus = PotionHelper.getDurationBonus(stack);
		int strengthBonus = PotionHelper.getStrengthBonus(stack);
		effects.forEach(effect -> {
			int duration = (int) (effect.getDuration() * (1 + durationBonus));
			int amplifier = effect.getAmplifier() + strengthBonus;
			enhancedEffects.add(new MobEffectInstance(effect.getEffect(), duration, amplifier));
		});
		return enhancedEffects;
	}
}

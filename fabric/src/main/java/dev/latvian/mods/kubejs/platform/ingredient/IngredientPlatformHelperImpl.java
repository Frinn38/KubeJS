package dev.latvian.mods.kubejs.platform.ingredient;

import com.faux.ingredientextension.api.ingredient.IngredientHelper;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.item.ingredient.IngredientPlatformHelper;
import dev.latvian.mods.kubejs.item.ingredient.IngredientStack;
import net.minecraft.core.Registry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class IngredientPlatformHelperImpl implements IngredientPlatformHelper {
	public static void register() {
		Registry.register(IngredientHelper.INGREDIENT_SERIALIZER_REGISTRY, KubeJS.id("stack"), IngredientStackImpl.SERIALIZER);
		Registry.register(IngredientHelper.INGREDIENT_SERIALIZER_REGISTRY, KubeJS.id("wildcard"), WildcardIngredient.SERIALIZER);
		Registry.register(IngredientHelper.INGREDIENT_SERIALIZER_REGISTRY, KubeJS.id("custom"), CustomIngredient.SERIALIZER);
		Registry.register(IngredientHelper.INGREDIENT_SERIALIZER_REGISTRY, KubeJS.id("custom_predicate"), CustomPredicateIngredient.SERIALIZER);
		Registry.register(IngredientHelper.INGREDIENT_SERIALIZER_REGISTRY, KubeJS.id("tag"), TagIngredient.SERIALIZER);
		Registry.register(IngredientHelper.INGREDIENT_SERIALIZER_REGISTRY, KubeJS.id("mod"), ModIngredient.SERIALIZER);
		Registry.register(IngredientHelper.INGREDIENT_SERIALIZER_REGISTRY, KubeJS.id("regex"), RegExIngredient.SERIALIZER);
		Registry.register(IngredientHelper.INGREDIENT_SERIALIZER_REGISTRY, KubeJS.id("creative_tab"), CreativeTabIngredient.SERIALIZER);
		Registry.register(IngredientHelper.INGREDIENT_SERIALIZER_REGISTRY, KubeJS.id("not"), NotIngredient.SERIALIZER);
		Registry.register(IngredientHelper.INGREDIENT_SERIALIZER_REGISTRY, KubeJS.id("or"), OrIngredient.SERIALIZER);
		Registry.register(IngredientHelper.INGREDIENT_SERIALIZER_REGISTRY, KubeJS.id("and"), AndIngredient.SERIALIZER);
		Registry.register(IngredientHelper.INGREDIENT_SERIALIZER_REGISTRY, KubeJS.id("ignore_nbt"), IgnoreNBTIngredient.SERIALIZER);
		Registry.register(IngredientHelper.INGREDIENT_SERIALIZER_REGISTRY, KubeJS.id("strong_nbt"), StrongNBTIngredient.SERIALIZER);
		Registry.register(IngredientHelper.INGREDIENT_SERIALIZER_REGISTRY, KubeJS.id("weak_nbt"), WeakNBTIngredient.SERIALIZER);
	}

	@Override
	public IngredientStack stack(Ingredient ingredient, int count) {
		return new IngredientStackImpl(ingredient, count);
	}

	@Override
	public Ingredient wildcard() {
		return WildcardIngredient.INSTANCE;
	}

	@Override
	public Ingredient custom(Ingredient parent, Predicate<ItemStack> predicate) {
		return new CustomIngredient(predicate);
	}

	@Override
	public Ingredient custom(Ingredient parent, @Nullable UUID uuid) {
		return new CustomPredicateIngredient(parent, uuid);
	}

	@Override
	public Ingredient tag(String tag) {
		return TagIngredient.ofTag(tag);
	}

	@Override
	public Ingredient mod(String mod) {
		return ModIngredient.ofMod(mod);
	}

	@Override
	public Ingredient regex(Pattern pattern) {
		return new RegExIngredient(pattern);
	}

	@Override
	public Ingredient creativeTab(CreativeModeTab tab) {
		return new CreativeTabIngredient(tab);
	}

	@Override
	public Ingredient not(Ingredient ingredient) {
		return new NotIngredient(ingredient);
	}

	@Override
	public Ingredient or(Ingredient[] ingredients) {
		return ingredients.length == 0 ? Ingredient.EMPTY : new OrIngredient(ingredients);
	}

	@Override
	public Ingredient and(Ingredient[] ingredients) {
		return ingredients.length == 0 ? Ingredient.EMPTY : ingredients.length == 1 ? ingredients[0] : new AndIngredient(ingredients);
	}

	@Override
	public Ingredient ignoreNBT(Item item) {
		return new IgnoreNBTIngredient(item);
	}

	@Override
	public Ingredient strongNBT(ItemStack item) {
		return new StrongNBTIngredient(item);
	}

	@Override
	public Ingredient weakNBT(ItemStack item) {
		return new WeakNBTIngredient(item);
	}
}

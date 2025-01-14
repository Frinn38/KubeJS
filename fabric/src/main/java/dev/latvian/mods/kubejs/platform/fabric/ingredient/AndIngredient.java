package dev.latvian.mods.kubejs.platform.fabric.ingredient;

import com.faux.ingredientextension.api.ingredient.serializer.IIngredientSerializer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.item.ingredient.IngredientJS;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class AndIngredient extends KubeJSIngredient {
	public static final KubeJSIngredientSerializer<AndIngredient> SERIALIZER = new KubeJSIngredientSerializer<>(AndIngredient::new, AndIngredient::new);

	public final Ingredient[] ingredients;

	public AndIngredient(Ingredient[] ingredients) {
		this.ingredients = ingredients;
	}

	public AndIngredient(FriendlyByteBuf buf) {
		this.ingredients = new Ingredient[buf.readVarInt()];

		for (int i = 0; i < ingredients.length; i++) {
			ingredients[i] = IngredientJS.ofNetwork(buf);
		}
	}

	public AndIngredient(JsonObject json) {
		var array = json.get("ingredients").getAsJsonArray();
		this.ingredients = new Ingredient[array.size()];

		for (int i = 0; i < ingredients.length; i++) {
			ingredients[i] = IngredientJS.ofJson(array.get(i));
		}
	}

	@Override
	public IIngredientSerializer<? extends Ingredient> getSerializer() {
		return SERIALIZER;
	}

	@Override
	public boolean test(@Nullable ItemStack stack) {
		if (stack != null) {
			for (var in : ingredients) {
				if (!in.test(stack)) {
					return false;
				}
			}

			return true;
		}

		return false;
	}

	@Override
	public void dissolve() {
		if (this.itemStacks == null) {
			this.itemStacks = Arrays.stream(this.ingredients[0].getItems()).filter((stack) -> {
				for (int i = 1; i < this.ingredients.length; ++i) {
					if (!this.ingredients[i].test(stack)) {
						return false;
					}
				}

				return true;
			}).toArray(ItemStack[]::new);
		}
	}

	@Override
	public boolean isEmpty() {
		return Arrays.stream(ingredients).anyMatch(Ingredient::isEmpty);
	}

	@Override
	public void toJson(JsonObject json) {
		JsonArray array = new JsonArray();

		for (var in : ingredients) {
			array.add(in.toJson());
		}

		json.add("ingredients", array);
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(ingredients.length);

		for (var in : ingredients) {
			in.toNetwork(buf);
		}
	}

	@Override
	public Ingredient kjs$and(Ingredient ingredient) {
		if (ingredient != Ingredient.EMPTY) {
			Ingredient[] in = new Ingredient[ingredients.length + 1];
			System.arraycopy(ingredients, 0, in, 0, ingredients.length);
			in[ingredients.length] = ingredient;
			return new AndIngredient(in);
		}

		return this;
	}
}

package dev.latvian.mods.kubejs.item.ingredient;

import com.google.gson.JsonElement;
import dev.latvian.mods.kubejs.KubeJSRegistries;
import dev.latvian.mods.kubejs.core.IngredientSupplierKJS;
import dev.latvian.mods.kubejs.item.ItemStackJS;
import dev.latvian.mods.kubejs.platform.IngredientPlatformHelper;
import dev.latvian.mods.kubejs.platform.RecipePlatformHelper;
import dev.latvian.mods.kubejs.recipe.RecipeExceptionJS;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.util.ListJS;
import dev.latvian.mods.kubejs.util.MapJS;
import dev.latvian.mods.kubejs.util.UtilsJS;
import dev.latvian.mods.rhino.Wrapper;
import dev.latvian.mods.rhino.mod.util.NBTUtils;
import dev.latvian.mods.rhino.regexp.NativeRegExp;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author LatvianModder
 */
public interface IngredientJS {
	Map<String, Ingredient> PARSE_CACHE = new HashMap<>();

	static Ingredient of(@Nullable Object o) {
		while (o instanceof Wrapper w) {
			o = w.unwrap();
		}

		if (o == null || o == ItemStack.EMPTY || o == Items.AIR || o == Ingredient.EMPTY) {
			return Ingredient.EMPTY;
		} else if (o instanceof IngredientSupplierKJS ingr) {
			return ingr.kjs$asIngredient();
		} else if (o instanceof Pattern || o instanceof NativeRegExp) {
			var reg = UtilsJS.parseRegex(o);

			if (reg != null) {
				return IngredientPlatformHelper.get().regex(reg);
			}

			return Ingredient.EMPTY;
		} else if (o instanceof JsonElement json) {
			return ofJson(json);
		} else if (o instanceof CharSequence) {
			var os = o.toString();
			var s = os;

			var cached = PARSE_CACHE.get(os);

			if (cached != null) {
				return cached;
			}

			var count = 1;
			var spaceIndex = s.indexOf(' ');

			if (spaceIndex >= 2 && s.indexOf('x') == spaceIndex - 1) {
				count = Integer.parseInt(s.substring(0, spaceIndex - 1));
				s = s.substring(spaceIndex + 1);
			}

			if (RecipeJS.itemErrors && count <= 0) {
				throw new RecipeExceptionJS("Invalid count!").error();
			}

			cached = parse(s);
			cached = cached.kjs$withCount(count);
			PARSE_CACHE.put(os, cached);
			return cached;
		}

		List<?> list = ListJS.of(o);

		if (list != null) {
			var inList = new ArrayList<Ingredient>(list.size());

			for (var o1 : list) {
				var ingredient = of(o1);

				if (ingredient != Ingredient.EMPTY) {
					inList.add(ingredient);
				}
			}

			if (inList.isEmpty()) {
				return Ingredient.EMPTY;
			} else if (inList.size() == 1) {
				return inList.get(0);
			} else {
				return IngredientPlatformHelper.get().or(inList.toArray(new Ingredient[0]));
			}
		}

		var map = MapJS.of(o);

		if (map != null) {
			Ingredient in = Ingredient.EMPTY;
			var val = map.containsKey("value");

			if (map.containsKey("type")) {
				if ("forge:nbt".equals(map.get("type"))) {
					in = ItemStackJS.of(map.get("item")).kjs$withNBT(NBTUtils.toTagCompound(map.get("nbt"))).kjs$strongNBT();
				} else {
					var json = MapJS.json(o);

					if (json == null) {
						throw new RecipeExceptionJS("Failed to parse custom ingredient (" + o + " is not a json object").fallback();
					}

					try {
						in = RecipePlatformHelper.get().getCustomIngredient(json);
					} catch (Exception ex) {
						throw new RecipeExceptionJS("Failed to parse custom ingredient (" + json.get("type") + ") from " + json + ": " + ex).fallback();
					}
				}
			} else if (val || map.containsKey("ingredient")) {
				in = of(val ? map.get("value") : map.get("ingredient"));
			} else if (map.containsKey("tag")) {
				in = IngredientPlatformHelper.get().tag(map.get("tag").toString());
			} else if (map.containsKey("item")) {
				in = ItemStackJS.of(map).getItem().kjs$asIngredient();
			}

			return in;
		}

		return ItemStackJS.of(o).kjs$asIngredient();
	}

	static Ingredient parse(String s) {
		if (s.isEmpty() || s.equals("-") || s.equals("air") || s.equals("minecraft:air")) {
			return Ingredient.EMPTY;
		} else if (s.equals("*")) {
			return IngredientPlatformHelper.get().wildcard();
		} else if (s.startsWith("#")) {
			return IngredientPlatformHelper.get().tag(s.substring(1));
		} else if (s.startsWith("@")) {
			return IngredientPlatformHelper.get().mod(s.substring(1));
		} else if (s.startsWith("%")) {
			var group = UtilsJS.findCreativeTab(s.substring(1));

			if (group == null) {
				if (RecipeJS.itemErrors) {
					throw new RecipeExceptionJS("Item group '" + s.substring(1) + "' not found!").error();
				}

				return Ingredient.EMPTY;
			}

			return IngredientPlatformHelper.get().creativeTab(group);
		}

		var reg = UtilsJS.parseRegex(s);

		if (reg != null) {
			return IngredientPlatformHelper.get().regex(reg);
		}

		var item = KubeJSRegistries.items().get(new ResourceLocation(s));

		if (item == null || item == Items.AIR) {
			return Ingredient.EMPTY;
		}

		return item.kjs$asIngredient();
	}

	static Ingredient ofJson(JsonElement json) {
		if (json.isJsonArray()) {
			var inList = new ArrayList<Ingredient>();

			for (var e : json.getAsJsonArray()) {
				var i = ofJson(e);

				if (i != Ingredient.EMPTY) {
					inList.add(i);
				}
			}

			if (inList.isEmpty()) {
				return Ingredient.EMPTY;
			} else if (inList.size() == 1) {
				return inList.get(0);
			} else {
				return IngredientPlatformHelper.get().or(inList.toArray(new Ingredient[0]));
			}
		} else if (json.isJsonPrimitive()) {
			return of(json.getAsString());
		} else if (json.isJsonObject()) {
			var o = json.getAsJsonObject();
			var val = o.has("value");
			var count = o.has("count") ? o.get("count").getAsInt() : 1;

			if (o.has("type")) {
				try {
					return RecipePlatformHelper.get().getCustomIngredient(o).kjs$withCount(count);
				} catch (Exception ex) {
					throw new RecipeExceptionJS("Failed to parse custom ingredient (" + o.get("type") + ") from " + o + ": " + ex);
				}
			} else if (val || o.has("ingredient")) {
				return ofJson(val ? o.get("value") : o.get("ingredient")).kjs$withCount(count);
			} else if (o.has("tag")) {
				return IngredientPlatformHelper.get().tag(o.get("tag").getAsString()).kjs$withCount(count);
			} else if (o.has("item")) {
				return ItemStackJS.of(o.get("item").getAsString()).getItem().kjs$asIngredient().kjs$withCount(count);
			}

			return Ingredient.EMPTY;
		}

		return Ingredient.EMPTY;
	}

	static Ingredient ofNetwork(FriendlyByteBuf buf) {
		return Ingredient.fromNetwork(buf);
	}
}
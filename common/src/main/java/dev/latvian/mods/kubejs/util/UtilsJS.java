package dev.latvian.mods.kubejs.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.StringReader;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.KubeJSRegistries;
import dev.latvian.mods.kubejs.bindings.event.BlockEvents;
import dev.latvian.mods.kubejs.bindings.event.ItemEvents;
import dev.latvian.mods.kubejs.block.BlockModificationEventJS;
import dev.latvian.mods.kubejs.item.ItemModificationEventJS;
import dev.latvian.mods.kubejs.level.BlockContainerJS;
import dev.latvian.mods.kubejs.platform.MiscPlatformHelper;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.NativeJavaObject;
import dev.latvian.mods.rhino.Wrapper;
import dev.latvian.mods.rhino.mod.util.Copyable;
import dev.latvian.mods.rhino.mod.util.NBTUtils;
import dev.latvian.mods.rhino.regexp.NativeRegExp;
import net.minecraft.ResourceLocationException;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.ClampedInt;
import net.minecraft.util.valueproviders.ClampedNormalInt;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.BinomialDistributionGenerator;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * @author LatvianModder
 */
public class UtilsJS {
	public static final Random RANDOM = new Random();
	public static final Pattern REGEX_PATTERN = Pattern.compile("/(.*)/([a-z]*)");
	public static final ResourceLocation AIR_LOCATION = new ResourceLocation("minecraft:air");
	public static final Pattern SNAKE_CASE_SPLIT = Pattern.compile("[_/]");
	public static final Set<String> ALWAYS_LOWER_CASE = new HashSet<>(Arrays.asList("a", "an", "the", "of", "on", "in"));
	public static MinecraftServer staticServer = null;
	public static final ResourceLocation UNKNOWN_ID = new ResourceLocation("unknown", "unknown");
	public static final Predicate<Object> ALWAYS_TRUE = o -> true;

	private static MessageDigest messageDigest;

	private static Collection<BlockState> ALL_STATE_CACHE = null;
	private static final Map<String, EntitySelector> ENTITY_SELECTOR_CACHE = new HashMap<>();
	private static final EntitySelector ALL_ENTITIES_SELECTOR = new EntitySelector(EntitySelector.INFINITE, true, false, e -> true, MinMaxBounds.Doubles.ANY, Function.identity(), null, EntitySelectorParser.ORDER_ARBITRARY, false, null, null, null, true);

	public interface TryIO {
		void run() throws IOException;
	}

	public static void tryIO(TryIO tryIO) {
		try {
			tryIO.run();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T cast(Object o) {
		return (T) o;
	}

	@Nullable
	public static Pattern parseRegex(Object o) {
		if (o instanceof CharSequence || o instanceof NativeRegExp) {
			return regex(o.toString());
		} else if (o instanceof Pattern pattern) {
			return pattern;
		}

		return null;
	}

	@Nullable
	public static Pattern regex(String string) {
		if (string.length() < 3) {
			return null;
		}

		var matcher = REGEX_PATTERN.matcher(string);

		if (matcher.matches()) {
			var flags = 0;
			var f = matcher.group(2);

			for (var i = 0; i < f.length(); i++) {
				switch (f.charAt(i)) {
					case 'd' -> flags |= Pattern.UNIX_LINES;
					case 'i' -> flags |= Pattern.CASE_INSENSITIVE;
					case 'x' -> flags |= Pattern.COMMENTS;
					case 'm' -> flags |= Pattern.MULTILINE;
					case 's' -> flags |= Pattern.DOTALL;
					case 'u' -> flags |= Pattern.UNICODE_CASE;
					case 'U' -> flags |= Pattern.UNICODE_CHARACTER_CLASS;
				}
			}

			return Pattern.compile(matcher.group(1), flags);
		}

		return null;
	}

	public static String toRegexString(Pattern pattern) {
		var sb = new StringBuilder("/");
		sb.append(pattern.pattern());
		sb.append('/');

		var flags = pattern.flags();

		if ((flags & Pattern.UNIX_LINES) != 0) {
			sb.append('d');
		}

		if ((flags & Pattern.CASE_INSENSITIVE) != 0) {
			sb.append('i');
		}

		if ((flags & Pattern.COMMENTS) != 0) {
			sb.append('x');
		}

		if ((flags & Pattern.MULTILINE) != 0) {
			sb.append('m');
		}

		if ((flags & Pattern.DOTALL) != 0) {
			sb.append('s');
		}

		if ((flags & Pattern.UNICODE_CASE) != 0) {
			sb.append('u');
		}

		if ((flags & Pattern.UNICODE_CHARACTER_CLASS) != 0) {
			sb.append('U');
		}

		return sb.toString();
	}

	public static void queueIO(Runnable runnable) {
		try {
			runnable.run();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Nullable
	public static Path getPath(Object o) {
		try {
			if (o instanceof Path) {
				return KubeJS.verifyFilePath((Path) o);
			} else if (o == null || o.toString().isEmpty()) {
				return null;
			}

			return KubeJS.verifyFilePath(KubeJS.getGameDirectory().resolve(o.toString()));
		} catch (Exception ex) {
			return null;
		}
	}

	@Nullable
	public static File getFileFromPath(Object o) {
		try {
			if (o instanceof File) {
				return KubeJS.verifyFilePath(((File) o).toPath()).toFile();
			} else if (o == null || o.toString().isEmpty()) {
				return null;
			}

			return KubeJS.verifyFilePath(KubeJS.getGameDirectory().resolve(o.toString())).toFile();
		} catch (Exception ex) {
			return null;
		}
	}

	@Nullable
	public static Object copy(@Nullable Object o) {
		if (o instanceof Copyable copyable) {
			return copyable.copy();
		} else if (o instanceof JsonElement json) {
			return JsonIO.copy(json);
		} else if (o instanceof Tag tag) {
			return tag.copy();
		}
		return o;
	}

	@Nullable
	public static Object wrap(@Nullable Object o, JSObjectType type) {
		//Primitives and already normalized objects
		if (o == null || o instanceof WrappedJS || o instanceof Tag || o instanceof Number || o instanceof Character || o instanceof String || o instanceof Enum || o.getClass().isPrimitive() && !o.getClass().isArray()) {
			return o;
		} else if (o instanceof CharSequence || o instanceof ResourceLocation) {
			return o.toString();
		} else if (o instanceof Wrapper w) {
			return wrap(w.unwrap(), type);
		}
		// Maps
		else if (o instanceof Map) {
			return o;
		}
		// Lists, Collections, Iterables, GSON Arrays
		else if (o instanceof Iterable<?> itr) {
			if (!type.checkList()) {
				return null;
			}

			var list = new ArrayList<>();

			for (var o1 : itr) {
				list.add(o1);
			}

			return list;
		}
		// Arrays (and primitive arrays are a pain)
		else if (o.getClass().isArray()) {
			if (type.checkList()) {
				return ListJS.ofArray(o);
			} else {
				return null;
			}
		}
		// GSON Primitives
		else if (o instanceof JsonPrimitive json) {
			return JsonIO.toPrimitive(json);
		}
		// GSON Objects
		else if (o instanceof JsonObject json) {
			if (!type.checkMap()) {
				return null;
			}

			var map = new HashMap<String, Object>(json.size());

			for (var entry : json.entrySet()) {
				map.put(entry.getKey(), entry.getValue());
			}

			return map;
		}
		// GSON and NBT Null
		else if (o instanceof JsonNull || o instanceof EndTag) {
			return null;
		}
		// NBT
		else if (o instanceof CompoundTag tag) {
			if (!type.checkMap()) {
				return null;
			}

			var map = new HashMap<String, Tag>(tag.size());

			for (var s : tag.getAllKeys()) {
				map.put(s, tag.get(s));
			}

			return map;
		} else if (o instanceof NumericTag tag) {
			return tag.getAsNumber();
		} else if (o instanceof StringTag tag) {
			return tag.getAsString();
		}

		return o;
	}

	public static int parseInt(@Nullable Object object, int def) {
		if (object == null) {
			return def;
		} else if (object instanceof Number num) {
			return num.intValue();
		}

		try {
			var s = object.toString();

			if (s.isEmpty()) {
				return def;
			}

			return Integer.parseInt(s);
		} catch (Exception ex) {
			return def;
		}
	}

	public static long parseLong(@Nullable Object object, long def) {
		if (object == null) {
			return def;
		} else if (object instanceof Number num) {
			return num.longValue();
		}

		try {
			var s = object.toString();

			if (s.isEmpty()) {
				return def;
			}

			return Long.parseLong(s);
		} catch (Exception ex) {
			return def;
		}
	}

	public static double parseDouble(@Nullable Object object, double def) {
		if (object == null) {
			return def;
		} else if (object instanceof Number num) {
			return num.doubleValue();
		}

		try {
			var s = object.toString();

			if (s.isEmpty()) {
				return def;
			}

			return Double.parseDouble(String.valueOf(object));
		} catch (Exception ex) {
			return def;
		}
	}

	public static String getID(@Nullable String s) {
		if (s == null || s.isEmpty()) {
			return "minecraft:air";
		}

		if (s.indexOf(':') == -1) {
			return "minecraft:" + s;
		}

		return s;
	}

	public static ResourceLocation getMCID(Context cx, @Nullable Object o) {
		if (o == null) {
			return null;
		} else if (o instanceof ResourceLocation id) {
			return id;
		}

		var s = o.toString();
		try {
			return new ResourceLocation(s);
		} catch (ResourceLocationException ex) {
			ConsoleJS.getCurrent(cx).error("Could not create ID from '%s'!".formatted(s), ex);
		}

		return null;
	}

	public static String getNamespace(@Nullable String s) {
		if (s == null || s.isEmpty()) {
			return "minecraft";
		}

		var i = s.indexOf(':');
		return i == -1 ? "minecraft" : s.substring(0, i);
	}

	public static String getPath(@Nullable String s) {
		if (s == null || s.isEmpty()) {
			return "air";
		}

		var i = s.indexOf(':');
		return i == -1 ? s : s.substring(i + 1);
	}

	public static BlockState parseBlockState(String string) {
		if (string.isEmpty()) {
			return Blocks.AIR.defaultBlockState();
		}

		var i = string.indexOf('[');
		var hasProperties = i >= 0 && string.indexOf(']') == string.length() - 1;
		var state = KubeJSRegistries.blocks().get(new ResourceLocation(hasProperties ? string.substring(0, i) : string)).defaultBlockState();

		if (hasProperties) {
			for (var s : string.substring(i + 1, string.length() - 1).split(",")) {
				var s1 = s.split("=", 2);

				if (s1.length == 2 && !s1[0].isEmpty() && !s1[1].isEmpty()) {
					var p = state.getBlock().getStateDefinition().getProperty(s1[0]);

					if (p != null) {
						Optional<?> o = p.getValue(s1[1]);

						if (o.isPresent()) {
							state = state.setValue(p, UtilsJS.cast(o.get()));
						}
					}
				}
			}
		}

		return state;
	}

	public static <T> Predicate<T> onMatchDo(Predicate<T> predicate, Consumer<T> onMatch) {
		return t -> {
			var match = predicate.test(t);
			if (match) {
				onMatch.accept(t);
			}
			return match;
		};
	}

	public static List<ItemStack> rollChestLoot(ResourceLocation id, @Nullable Entity entity) {
		var list = new ArrayList<ItemStack>();

		if (UtilsJS.staticServer != null) {
			var tables = UtilsJS.staticServer.getLootTables();
			var table = tables.get(id);

			LootContext.Builder builder;

			if (entity != null) {
				builder = new LootContext.Builder((ServerLevel) entity.level)
						.withOptionalParameter(LootContextParams.THIS_ENTITY, entity)
						.withParameter(LootContextParams.ORIGIN, entity.position());
			} else {
				builder = new LootContext.Builder(UtilsJS.staticServer.overworld())
						.withOptionalParameter(LootContextParams.THIS_ENTITY, null)
						.withParameter(LootContextParams.ORIGIN, Vec3.ZERO);
			}

			table.getRandomItems(builder.create(LootContextParamSets.CHEST), list::add);
		}

		return list;
	}

	// TODO: We could probably make these generic for RegistryObjectBuilderTypes,
	//  so maybe look into that to allow people to modify builtin fluids, etc. as well.
	public static void postModificationEvents() {
		BlockEvents.MODIFICATION.post(new BlockModificationEventJS());
		ItemEvents.MODIFICATION.post(new ItemModificationEventJS());
	}

	public static Class<?> getRawType(Type type) {
		if (type instanceof Class<?> clz) {
			return clz;
		} else if (type instanceof ParameterizedType paramType) {
			var rawType = paramType.getRawType();

			if (rawType instanceof Class<?> clz) {
				return clz;
			}
		} else if (type instanceof GenericArrayType arrType) {
			var componentType = arrType.getGenericComponentType();
			return Array.newInstance(getRawType(componentType), 0).getClass();
		} else if (type instanceof TypeVariable) {
			return Object.class;
		} else if (type instanceof WildcardType wildcard) {
			return getRawType(wildcard.getUpperBounds()[0]);
		}

		var className = type == null ? "null" : type.getClass().getName();
		throw new IllegalArgumentException("Expected a Class, ParameterizedType, GenericArrayType, TypeVariable or WildcardType, but <" + type + "> is of type " + className);
	}

	public static String convertSnakeCaseToCamelCase(String string) {
		var s = SNAKE_CASE_SPLIT.split(string, 0);

		var sb = new StringBuilder();
		var first = true;

		for (var value : s) {
			if (!value.isEmpty()) {
				if (first) {
					first = false;
					sb.append(value);
				} else {
					sb.append(Character.toUpperCase(value.charAt(0)));
					sb.append(value, 1, value.length());
				}
			}
		}

		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	public static IntProvider intProviderOf(Object o) {
		if (o instanceof Number n) {
			return ConstantInt.of(n.intValue());
		} else if (o instanceof List l && !l.isEmpty()) {
			var min = (Number) l.get(0);
			var max = l.size() >= 2 ? (Number) l.get(1) : min;
			return UniformInt.of(min.intValue(), max.intValue());
		} else if (o instanceof Map) {
			var m = (Map<String, Object>) o;

			var intBounds = parseIntBounds(m);
			if (intBounds != null) {
				return intBounds;
			} else if (m.containsKey("clamped")) {
				var source = intProviderOf(m.get("clamped"));
				var clampTo = parseIntBounds(m);
				if (clampTo != null) {
					return ClampedInt.of(source, clampTo.getMinValue(), clampTo.getMaxValue());
				}
			} else if (m.containsKey("clamped_normal")) {
				var clampTo = parseIntBounds(m);
				var mean = ((Number) m.get("mean")).intValue();
				var deviation = ((Number) m.get("deviation")).intValue();
				if (clampTo != null) {
					return ClampedNormalInt.of(mean, deviation, clampTo.getMinValue(), clampTo.getMaxValue());
				}
			}

			var decoded = IntProvider.CODEC.parse(NbtOps.INSTANCE, NBTUtils.toTagCompound(m)).result();
			if (decoded.isPresent()) {
				return decoded.get();
			}
		}

		return ConstantInt.of(0);
	}

	private static UniformInt parseIntBounds(Map<String, Object> m) {
		if (m.get("bounds") instanceof List bounds) {
			return UniformInt.of(UtilsJS.parseInt(bounds.get(0), 0), UtilsJS.parseInt(bounds.get(1), 0));
		} else if (m.containsKey("min") && m.containsKey("max")) {
			return UniformInt.of(((Number) m.get("min")).intValue(), ((Number) m.get("max")).intValue());
		} else if (m.containsKey("min_inclusive") && m.containsKey("max_inclusive")) {
			return UniformInt.of(((Number) m.get("min_inclusive")).intValue(), ((Number) m.get("max_inclusive")).intValue());
		} else if (m.containsKey("value")) {
			var f = ((Number) m.get("value")).intValue();
			return UniformInt.of(f, f);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static NumberProvider numberProviderOf(Object o) {
		if (o instanceof Number n) {
			var f = n.floatValue();
			return UniformGenerator.between(f, f);
		} else if (o instanceof List l && !l.isEmpty()) {
			var min = (Number) l.get(0);
			var max = l.size() >= 2 ? (Number) l.get(1) : min;
			return UniformGenerator.between(min.floatValue(), max.floatValue());
		} else if (o instanceof Map) {
			var m = (Map<String, Object>) o;
			if (m.containsKey("min") && m.containsKey("max")) {
				return UniformGenerator.between(((Number) m.get("min")).intValue(), ((Number) m.get("max")).floatValue());
			} else if (m.containsKey("n") && m.containsKey("p")) {
				return BinomialDistributionGenerator.binomial(((Number) m.get("n")).intValue(), ((Number) m.get("p")).floatValue());
			} else if (m.containsKey("value")) {
				var f = ((Number) m.get("value")).floatValue();
				return UniformGenerator.between(f, f);
			}
		}

		return ConstantValue.exactly(0);
	}

	public static JsonElement numberProviderJson(NumberProvider gen) {
		return Deserializers.createConditionSerializer().create().toJsonTree(gen);
	}

	public static Vec3 vec3Of(@Nullable Object o) {
		if (o instanceof Vec3 vec) {
			return vec;
		} else if (o instanceof Entity entity) {
			return entity.position();
		} else if (o instanceof List<?> list && list.size() >= 3) {
			return new Vec3(UtilsJS.parseDouble(list.get(0), 0), UtilsJS.parseDouble(list.get(1), 0), UtilsJS.parseDouble(list.get(2), 0));
		} else if (o instanceof BlockPos pos) {
			return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
		} else if (o instanceof BlockContainerJS block) {
			return new Vec3(block.getPos().getX() + 0.5D, block.getPos().getY() + 0.5D, block.getPos().getZ() + 0.5D);
		}

		return Vec3.ZERO;
	}

	public static BlockPos blockPosOf(@Nullable Object o) {
		if (o instanceof BlockPos pos) {
			return pos;
		} else if (o instanceof List<?> list && list.size() >= 3) {
			return new BlockPos(UtilsJS.parseInt(list.get(0), 0), UtilsJS.parseInt(list.get(1), 0), UtilsJS.parseInt(list.get(2), 0));
		} else if (o instanceof BlockContainerJS block) {
			return block.getPos();
		} else if (o instanceof Vec3 vec) {
			return new BlockPos(vec.x, vec.y, vec.z);
		}

		return BlockPos.ZERO;
	}

	public static Collection<BlockState> getAllBlockStates() {
		if (ALL_STATE_CACHE != null) {
			return ALL_STATE_CACHE;
		}

		var states = new HashSet<BlockState>();
		for (var block : KubeJSRegistries.blocks()) {
			states.addAll(block.getStateDefinition().getPossibleStates());
		}

		ALL_STATE_CACHE = Collections.unmodifiableCollection(states);
		return ALL_STATE_CACHE;
	}

	public static String toTitleCase(String s) {
		if (s.isEmpty()) {
			return "";
		} else if (ALWAYS_LOWER_CASE.contains(s)) {
			return s;
		} else if (s.length() == 1) {
			return s.toUpperCase();
		}

		char[] chars = s.toCharArray();
		chars[0] = Character.toUpperCase(chars[0]);
		return new String(chars);
	}

	public static String getMobTypeId(MobType type) {
		if (type == MobType.UNDEAD) {
			return "undead";
		} else if (type == MobType.ARTHROPOD) {
			return "arthropod";
		} else if (type == MobType.ILLAGER) {
			return "illager";
		} else if (type == MobType.WATER) {
			return "water";
		} else {
			return "unknown";
		}
	}

	public static MobCategory mobCategoryByName(String s) {
		return MiscPlatformHelper.get().getMobCategory(s);
	}

	public static String stripIdForEvent(ResourceLocation id) {
		return stripEventName(id.toString());
	}

	public static String getUniqueId(JsonElement json) {
		return getUniqueId(json, Function.identity());
	}

	public static <T> String getUniqueId(T input, Codec<T> codec) {
		return getUniqueId(input, o -> codec.encodeStart(JsonOps.COMPRESSED, o)
				.getOrThrow(false, str -> {
					throw new RuntimeException("Could not encode element to JSON: " + str);
				}));
	}

	private static <T> String getUniqueId(T input, Function<T, JsonElement> toJson) {
		if (messageDigest == null) {
			try {
				messageDigest = MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException nsae) {
				throw new InternalError("MD5 not supported", nsae);
			}
		}

		var json = toJson.apply(input);

		if (messageDigest == null) {
			return new BigInteger(HexFormat.of().formatHex(JsonIO.getJsonHashBytes(json)), 16).toString(36);
		} else {
			messageDigest.reset();
			return new BigInteger(HexFormat.of().formatHex(messageDigest.digest(JsonIO.getJsonHashBytes(json))), 16).toString(36);
		}
	}

	public static String stripEventName(String s) {
		return s.replaceAll("[/:]", ".").replace('-', '_');
	}

	public static EntitySelector entitySelector(@Nullable Object o) {
		if (o == null) {
			return ALL_ENTITIES_SELECTOR;
		} else if (o instanceof EntitySelector s) {
			return s;
		}

		String s = o.toString();

		if (s.isBlank()) {
			return ALL_ENTITIES_SELECTOR;
		}

		var sel = ENTITY_SELECTOR_CACHE.get(s);

		if (sel == null) {
			sel = ALL_ENTITIES_SELECTOR;

			try {
				sel = new EntitySelectorParser(new StringReader(s), true).parse();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		ENTITY_SELECTOR_CACHE.put(s, sel);
		return sel;
	}

	@Nullable
	public static CreativeModeTab findCreativeTab(String id) {
		for (var group : CreativeModeTab.TABS) {
			if (id.equals(group.getRecipeFolderName())) {
				return group;
			}
		}

		return null;
	}

	public static <T> T makeFunctionProxy(ScriptType type, Class<T> targetClass, BaseFunction function) {
		return cast(NativeJavaObject.createInterfaceAdapter(type.manager.get().context, targetClass, function));
	}
}
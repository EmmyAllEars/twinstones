package net.punhalla.twinstones;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-wide list of stone names and where they stand, saved with the overworld so one
 * list covers every dimension. Each name holds at most {@link #MAX_PER_NAME} stones.
 */
public class TwinstoneLinks extends SavedData {
    public static final int MAX_PER_NAME = 2;
    private static final String FILE_ID = "twinstones";

    private final Map<String, List<GlobalPos>> stones = new HashMap<>();

    public static TwinstoneLinks get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(TwinstoneLinks::new, TwinstoneLinks::load), FILE_ID);
    }

    /** Names match ignoring case and surrounding spaces. */
    public static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    public List<GlobalPos> at(String key) {
        return List.copyOf(stones.getOrDefault(key, List.of()));
    }

    public void add(String key, GlobalPos pos) {
        List<GlobalPos> list = stones.computeIfAbsent(key, k -> new ArrayList<>());
        if (!list.contains(pos)) {
            list.add(pos);
            setDirty();
        }
    }

    public void remove(String key, GlobalPos pos) {
        List<GlobalPos> list = stones.get(key);
        if (list != null && list.remove(pos)) {
            if (list.isEmpty()) {
                stones.remove(key);
            }
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag names = new ListTag();
        stones.forEach((key, list) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Name", key);
            ListTag positions = new ListTag();
            for (GlobalPos pos : list) {
                CompoundTag p = new CompoundTag();
                p.putString("Dim", pos.dimension().location().toString());
                p.putLong("Pos", pos.pos().asLong());
                positions.add(p);
            }
            entry.put("Stones", positions);
            names.add(entry);
        });
        tag.put("Names", names);
        return tag;
    }

    private static TwinstoneLinks load(CompoundTag tag, HolderLookup.Provider registries) {
        TwinstoneLinks links = new TwinstoneLinks();
        ListTag names = tag.getList("Names", Tag.TAG_COMPOUND);
        for (int i = 0; i < names.size(); i++) {
            CompoundTag entry = names.getCompound(i);
            List<GlobalPos> list = new ArrayList<>();
            ListTag positions = entry.getList("Stones", Tag.TAG_COMPOUND);
            for (int j = 0; j < positions.size(); j++) {
                CompoundTag p = positions.getCompound(j);
                ResourceLocation dim = ResourceLocation.tryParse(p.getString("Dim"));
                if (dim != null) {
                    list.add(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(p.getLong("Pos"))));
                }
            }
            if (!list.isEmpty()) {
                links.stones.put(entry.getString("Name"), list);
            }
        }
        return links;
    }
}

package quickcarpet.mixin.spawning;

import com.mojang.serialization.Codec;
import net.minecraft.entity.EntityType;
import net.minecraft.util.collection.Pool;
import net.minecraft.world.biome.SpawnSettings;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.EndCityFeature;
import net.minecraft.world.gen.feature.StructureFeature;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EndCityFeature.class)
public abstract class EndCityFeatureMixin extends StructureFeature<DefaultFeatureConfig> {
    private static final Pool<SpawnSettings.SpawnEntry> spawnList = Pool.of(new SpawnSettings.SpawnEntry(EntityType.SHULKER, 10, 4, 4));

    public EndCityFeatureMixin(Codec<DefaultFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public Pool<SpawnSettings.SpawnEntry> getMonsterSpawns() {
        return spawnList;
    }
}

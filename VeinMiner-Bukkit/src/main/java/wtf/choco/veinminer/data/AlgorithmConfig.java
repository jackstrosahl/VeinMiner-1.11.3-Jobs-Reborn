package wtf.choco.veinminer.data;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.container.JobProgression;
import com.google.common.base.Preconditions;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import wtf.choco.veinminer.VeinMiner;
import wtf.choco.veinminer.utils.VMConstants;

/**
 * Represents various configurable options for the vein miner algorithm.
 *
 * @author Parker Hawke - Choco
 */
public class AlgorithmConfig implements Cloneable {

    private boolean repairFriendly = false;
    private boolean includeEdges = true;
    private int maxVeinSize = 64;
    private double cost = 0.0D;
    private Set<@NotNull UUID> disabledWorlds = new HashSet<>();

    // Jobs data
    private boolean jobsEnabled = false;
    private String job = null;
    private double jobBase;
    private double jobPerLevel;


    /**
     * Construct an AlgorithmConfig using values supplied from the given configuration section and
     * using the provided AlgorithmConfig as a default fallback if values are not present in the
     * configuration section.
     *
     * @param section the section from which to read values
     * @param defaultValues the config from which to pull default values
     */
    public AlgorithmConfig(@NotNull ConfigurationSection section, @Nullable AlgorithmConfig defaultValues) {
        Preconditions.checkArgument(section != null, "section cannot be null");

        this.repairFriendly = section.getBoolean(VMConstants.CONFIG_REPAIR_FRIENDLY_VEINMINER, (defaultValues != null) ? defaultValues.repairFriendly : repairFriendly);
        this.includeEdges = section.getBoolean(VMConstants.CONFIG_INCLUDE_EDGES, (defaultValues != null) ? defaultValues.includeEdges : includeEdges);
        this.maxVeinSize = section.getInt(VMConstants.CONFIG_MAX_VEIN_SIZE, (defaultValues != null) ? defaultValues.maxVeinSize : maxVeinSize);
        this.cost = section.getDouble(VMConstants.CONFIG_COST, (defaultValues != null) ? defaultValues.cost : cost);
        ConfigurationSection jobsSection = section.getConfigurationSection(VMConstants.CONFIG_JOBS_SECTION);
        if(jobsSection != null && VeinMiner.getPlugin().isJobsEnabled()) {
            jobsEnabled = true;
            job = jobsSection.getString(VMConstants.CONFIG_JOBS_JOB);
            jobBase = jobsSection.getDouble(VMConstants.CONFIG_JOBS_BASE);
            jobPerLevel = jobsSection.getDouble(VMConstants.CONFIG_JOBS_PER_LEVEL);
        }
        List<String> disabledWorlds = section.getStringList(VMConstants.CONFIG_DISABLED_WORLDS);
        if (disabledWorlds.isEmpty() && defaultValues != null) {
            this.disabledWorlds = new HashSet<>(defaultValues.disabledWorlds);
        }

        disabledWorlds.forEach(worldName -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return;
            }

            this.disabledWorlds.add(world.getUID());
        });
    }

    /**
     * Construct an AlgorithmConfig using values supplied from the given configuration section.
     *
     * @param section the section from which to read values
     */
    public AlgorithmConfig(@NotNull ConfigurationSection section) {
        this(section, null);
    }

    /**
     * Construct an AlgorithmConfig using values supplied from the given algorithm config.
     *
     * @param config the configuration from which to read values
     *
     * @see #clone()
     */
    public AlgorithmConfig(@NotNull AlgorithmConfig config) {
        Preconditions.checkArgument(config != null, "config cannot be null");

        this.repairFriendly = config.repairFriendly;
        this.includeEdges = config.includeEdges;
        this.maxVeinSize = config.maxVeinSize;
        this.cost = config.cost;
        this.disabledWorlds = new HashSet<>(config.disabledWorlds);
        this.jobsEnabled = config.jobsEnabled;
        this.job = config.job;
        this.jobBase = config.jobBase;
        this.jobPerLevel = config.jobPerLevel;
    }

    /**
     * Construct an AlgorithmConfig with hard-coded default values.
     */
    public AlgorithmConfig() { }

    /**
     * Set whether or not vein miner should be repair-friendly. If true, vein miner will
     * cease execution before the tool has lost all its durability allowing the user to
     * repair their tool before it gets broken. If false, vein miner will cease execution
     * after the tool has broken.
     *
     * @param friendly the repair-friendly state
     *
     * @return this instance. Allows for chained method calls
     */
    @NotNull
    public AlgorithmConfig repairFriendly(boolean friendly) {
        this.repairFriendly = friendly;
        return this;
    }

    /**
     * Get whether or not vein miner should be repair-friendly. See
     * {@link #repairFriendly(boolean)}.
     *
     * @return true if repair-friendly, false otherwise
     */
    public boolean isRepairFriendly() {
        return repairFriendly;
    }

    /**
     * Set whether or not vein miner should search for vein mineable blocks along the
     * farthest edges of a block (i.e. north north east, north north west, etc.).
     *
     * @param includeEdges whether to include edges
     *
     * @return this instance. Allows for chained method calls
     */
    @NotNull
    public AlgorithmConfig includeEdges(boolean includeEdges) {
        this.includeEdges = includeEdges;
        return this;
    }

    /**
     * Check whether or not vein miner should include edges. See {@link #includesEdges()}.
     *
     * @return true if includes edges, false otherwise
     */
    public boolean includesEdges() {
        return includeEdges;
    }

    /**
     * Set the maximum vein size.
     *
     * @param maxVeinSize the maximum vein size
     *
     * @return this instance. Allows for chained method calls
     */
    @NotNull
    public AlgorithmConfig maxVeinSize(int maxVeinSize) {
        Preconditions.checkArgument(maxVeinSize > 0, "Max vein size must be > 0");

        this.maxVeinSize = maxVeinSize;
        return this;
    }

    /**
     * Get the maximum vein size.
     *
     * @return the maximum vein size
     */
    public int getMaxVeinSize(Player player) {
        if (!jobsEnabled || player == null)
            return maxVeinSize;
        JobProgression progression = Jobs.getPlayerManager().getJobsPlayer(player).
                                                getJobProgression(Jobs.getJob(job));
        if(progression==null) return 1;
        int level = progression.getLevel();
        int out = (int) (jobBase + level*jobPerLevel);

        return Math.min(out, maxVeinSize);
    }

    public int getMaxVeinSize()
    {
        return getMaxVeinSize(null);
    }

    /**
     * Set the amount of money required to vein mine. Note, this feature requires Vault
     * and an economy plugin, else it is ignored.
     *
     * @param cost the cost
     *
     * @return this instance. Allows for chained method calls
     */
    @NotNull
    public AlgorithmConfig cost(double cost) {
        Preconditions.checkArgument(cost >= 0.0, "Cost must be positive or 0");

        this.cost = cost;
        return this;
    }

    /**
     * Get the economic cost.
     *
     * @return the cost
     */
    public double getCost() {
        return cost;
    }

    /**
     * Add a world in which vein miner should be disabled.
     *
     * @param world the world in which to disable vein miner
     *
     * @return this instance. Allows for chained method calls
     */
    @NotNull
    public AlgorithmConfig disabledWorld(@NotNull World world) {
        Preconditions.checkArgument(world != null, "Cannot disable null world");

        this.disabledWorlds.add(world.getUID());
        return this;
    }

    /**
     * Remove a world in which vein miner is disabled. Vein miner will be usable again.
     *
     * @param world the world in which to enabled vein miner
     *
     * @return this instance. Allows for chained method calls
     */
    @NotNull
    public AlgorithmConfig undisableWorld(@NotNull World world) {
        Preconditions.checkArgument(world != null, "Cannot undisable null world");

        this.disabledWorlds.remove(world.getUID());
        return this;
    }

    /**
     * Check whether or not vein miner is disabled in the specified world.
     *
     * @param world the world to check
     *
     * @return true if disabled, false otherwise
     */
    public boolean isDisabledWorld(@NotNull World world) {
        return world != null && (disabledWorlds.contains(world.getUID()));
    }

    /**
     * Read configured values from a raw {@literal Map<String, Object>}. This is not a
     * recommended means of reading data and exists solely for internal use.
     *
     * @param raw the raw data from which to read configured values
     *
     * @deprecated not set for removal but AVOID AT ALL COSTS. Constructors and builder
     * methods should be the preferred approach to reading configured values.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public void readUnsafe(@NotNull Map<String, Object> raw) {
        Preconditions.checkArgument(raw != null, "cannot pass null raw data map");

        Object repairFriendlyVeinMiner = raw.get(VMConstants.CONFIG_REPAIR_FRIENDLY_VEINMINER);
        Object includeEdges = raw.get(VMConstants.CONFIG_INCLUDE_EDGES);
        Object maxVeinSize = raw.get(VMConstants.CONFIG_MAX_VEIN_SIZE);
        Object cost = raw.get(VMConstants.CONFIG_COST);
        Object disabledWorlds = raw.get(VMConstants.CONFIG_DISABLED_WORLDS);

        if (repairFriendlyVeinMiner instanceof Boolean) {
            this.repairFriendly((boolean) repairFriendlyVeinMiner);
        }
        if (includeEdges instanceof Boolean) {
            this.includeEdges((boolean) includeEdges);
        }
        if (maxVeinSize instanceof Integer) {
            this.maxVeinSize(Math.max((int) maxVeinSize, 1));
        }
        if (cost instanceof Number) {
            this.cost(Math.max((double) cost, 0.0));
        }
        if (disabledWorlds instanceof List) {
            ((List<Object>) disabledWorlds).stream().filter(o -> o instanceof String).map(s -> UUID.fromString((String) s))
                .distinct().map(Bukkit::getWorld).forEach(w -> {
                    if (w == null) {
                        return;
                    }

                    this.disabledWorld(w);
                });
        }
    }

    @Override
    public AlgorithmConfig clone() {
        return new AlgorithmConfig(this);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repairFriendly, includeEdges, maxVeinSize, cost, disabledWorlds);
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }

        if (!(object instanceof AlgorithmConfig)) {
            return false;
        }

        AlgorithmConfig other = (AlgorithmConfig) object;
        return repairFriendly == other.repairFriendly && includeEdges == other.includeEdges
            && maxVeinSize == other.maxVeinSize && cost == other.cost && Objects.equals(disabledWorlds, other.disabledWorlds);
    }

}

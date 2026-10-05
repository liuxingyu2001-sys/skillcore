package com.skillcore.effect;

import com.skillcore.SkillCorePlugin;
import com.skillcore.hook.CraftEngineHook;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 显示物特效 — 用 {@link ItemDisplay} 显示一个物品/模型（支持 CraftEngine {@code craftengine_model}）。
 * <p>
 * 无碰撞箱、无 AI、默认非持久化（区块卸载自动消失）；可设置缩放、朝向与自动移除时间。
 */
public final class DisplayEffect implements SkillEffect {

    private final ItemStack item;
    private final String craftEngineModel;
    private final float scale;
    private final long removeAfterTicks;
    private final boolean faceDirection;

    public DisplayEffect(ItemStack item, String craftEngineModel, float scale,
                         long removeAfterTicks, boolean faceDirection) {
        this.item = item;
        this.craftEngineModel = craftEngineModel;
        this.scale = scale;
        this.removeAfterTicks = removeAfterTicks;
        this.faceDirection = faceDirection;
    }

    @Override
    public void play(EffectContext context) {
        if (!EffectSettings.effectsEnabled() || !context.valid()) return;
        ItemStack stack = resolveItem();
        if (stack == null || stack.getType().isAir()) return;

        float s = scale <= 0 ? 1.0f : scale;
        Quaternionf rotation = faceDirection ? directionToQuaternion(context.direction()) : identity();
        Location spawnAt = context.location().clone();

        ItemDisplay display = spawnAt.getWorld().spawn(spawnAt, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setInvulnerable(true);
            d.setGravity(false);
            d.setPersistent(false);
            d.setShadowRadius(0);
            d.setShadowStrength(0);
            d.addScoreboardTag("skillcore_display");
            d.setTeleportDuration(EffectSettings.teleportDuration());
            d.setInterpolationDuration(EffectSettings.interpolationDuration());
            d.setInterpolationDelay(0);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new Quaternionf(rotation),
                    new Vector3f(s, s, s), identity()));
        });

        if (display != null && removeAfterTicks > 0) {
            SkillCorePlugin plugin = SkillCorePlugin.getInstance();
            if (plugin != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (display.isValid()) display.remove();
                }, removeAfterTicks);
            }
        }
    }

    private ItemStack resolveItem() {
        if (item != null) {
            return item.clone();
        }
        if (craftEngineModel != null && !craftEngineModel.isEmpty()) {
            return CraftEngineHook.buildItem(craftEngineModel);
        }
        return null;
    }

    private static Quaternionf identity() {
        return new Quaternionf(0, 0, 0, 1);
    }

    /** 把物品默认的 Y 轴朝向旋转到 direction。 */
    private static Quaternionf directionToQuaternion(org.bukkit.util.Vector direction) {
        if (direction == null || direction.lengthSquared() < 1.0e-8) {
            return identity();
        }
        Vector3f dir = new Vector3f(
                (float) direction.getX(), (float) direction.getY(), (float) direction.getZ()).normalize();
        Vector3f up = new Vector3f(0, 1, 0);
        float dot = up.dot(dir);
        if (dot < -0.999f) {
            return new Quaternionf().rotationX((float) Math.PI);
        }
        Vector3f cross = new Vector3f(up).cross(dir);
        return new Quaternionf(cross.x, cross.y, cross.z, 1.0f + dot).normalize();
    }

    /** 供外部手动微调 Display 平滑（绕过本类的 Display）。 */
    public static void applySmooth(Display display) {
        if (display == null) return;
        display.setTeleportDuration(EffectSettings.teleportDuration());
        display.setInterpolationDuration(EffectSettings.interpolationDuration());
        display.setInterpolationDelay(0);
    }
}

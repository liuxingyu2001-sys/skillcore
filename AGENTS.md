# AGENTS.md — SkillCore

Minecraft Paper 插件：技能与武器绑定，手持对应武器才能释放技能。SkillCore 是唯一技能来源，没有独立的技能绑定/解锁系统。

- 语言/API：Java 21（`maven.compiler.release=21`），Paper API `26.2.build.129-stable`，`api-version: '1.21'`
- 构建：Maven，产物 `target/SkillCore-${project.version}.jar`（当前 `SkillCore-1.0.0.jar`）
- `groupId=com.skillcore`，`artifactId=skillcore`，主类 `com.skillcore.SkillCorePlugin`
- 代码注释、配置注释均为中文，保持中文风格
- 已在 Paper 26.2 服务端实机验证加载（自动注册 11 个技能类、7 把武器，含风暴战锤）

## 常用命令

```bash
mvn clean package                 # 编译打包（依赖 paper-api + placeholderapi，均 provided）
```

本机没有测试代码。验证方式：编译通过 + 在服务器上 `/sc reload` / 发武器测试。

## 核心架构（只有这一套是生效的）

数据流：`skills/*.yml`（每把武器一个文件） → `ConfigManager.loadWeapons` → `WeaponFactory.parse` 生成 `SkillWeapon`（外观+`WeaponStats`）并 `createSkill` 生成 `WeaponSkill` 实例 → 存入 `WeaponRegistry`。

运行时：
- `WeaponInputListener` 监听左/右键、Shift 组合、双击 Shift、按住/松开 Shift，先用 `WeaponItems.getHeldWeaponId` 校验主手是否为技能武器（PDC key `skillcore_weapon_id`），再交给 `WeaponManager.triggerHeld(player, SkillTrigger)`
- `WeaponManager.triggerHeld`：解析左右技能 → `supportsTrigger` 检查（双击/按住/松开未实现则不触发、不进冷却）→ `cast`
- `WeaponManager.cast`：构造 `WeaponContext` → `skill.canUse` → 冷却检查（`CooldownUtils`，`skillcore.bypass.cooldown` 可绕过）→ `dispatch` 到对应钩子 → 启动冷却（松开 Shift 不启动）
- `WeaponContext` 是写技能的主入口，封装伤害（吃暴击/穿透/百分比，统一走 `SkillDamageUtils`）、吸血、位移、击退、控制、粒子/音效，全部从 `ctx.stats()` 读数值
- `CombatListener` 处理吸血/反伤等战斗事件（`onHit`/`onKill`/`onDamaged` 回调）

关键文件：
- `src/main/java/com/skillcore/SkillCorePlugin.java` — 入口，onEnable 注册事件/自动扫描技能/占位符/假人模块
- `src/main/java/com/skillcore/weapon/` — 武器系统核心
  - `WeaponFactory` — 配置解析 + 内置技能注册 + `autoRegister` 注解扫描
  - `WeaponStats` — 所有数值字段 + getter + `fromConfig`/`copy`/`calculateHit`/`calculateTotal`（只读配置里出现过的键，缺失用内置默认值）
  - `WeaponContext` / `AbstractWeaponSkill` / `WeaponSkill` — 技能编写 API
  - `WeaponItems` / `WeaponLore` — 物品构建、PDC 识别、lore 变量替换
  - `WeaponRegistry` / `WeaponManager` — 注册表、释放流程、生命周期清理
  - `ClassScanner` — 注解扫描（支持 class 目录与 jar）
- `src/main/java/com/skillcore/weapon/skills/` — 内置技能实现（每个类一个 `@WeaponSkillInfo`）；`StormHammerFx` 是风暴战锤专属分阶段特效
- `src/main/java/com/skillcore/config/` — `ConfigManager`（config.yml + skills/ 目录加载与轮询）
- `src/main/java/com/skillcore/dummy/TestDummyManager.java` — 测试假人模块
- `src/main/java/com/skillcore/hook/SkillCorePlaceholders.java` — PlaceholderAPI 占位符（PAPI 未安装时自动跳过）
- `src/main/java/com/skillcore/hook/CraftEngineHook.java` — CraftEngine 模型软依赖（反射调用）
- `src/main/java/com/skillcore/hook/PacketEventsHook.java` — PacketEvents 软依赖（反射发送方块破坏动画数据包，地面碎裂特效）
- `src/main/java/com/skillcore/effect/` — 特效接口与工厂（`SkillEffect` / `Effects` / `DisplayEffect` 等）
- `src/main/java/com/skillcore/utils/ParticleUtils.java` — 粒子绘制工具（点/线/环/球/立方/锥/螺旋/光束/闪电）
- `src/main/java/com/skillcore/utils/TextUtils.java` — MiniMessage 与 `&` 颜色码混合解析（显示名/lore 用）
- `src/main/java/com/skillcore/utils/VulcanHelper.java` — Vulcan 反作弊移动检测 VL 清零（反射软依赖，未装则空操作）
- `src/main/java/com/skillcore/command/SkillCoreCommand.java` — `/sc give|weapons|info|updatelore|cooldown|testdummy|reload`
- `src/main/java/com/skillcore/utils/` — 通用工具（`TargetFilter`/`SkillDamageUtils`/伤害/吸血/位移/瞄准/粒子/音效等）

## 添加一把新技能武器（注解自动注册）

1. 在 `src/main/java/com/skillcore/weapon/skills/` 下新建一个 `AbstractWeaponSkill` 子类，并提供**无参构造**，标注 `@WeaponSkillInfo(id = "MY_TYPE")`（可加 `aliases`）
2. 无需手动注册：`WeaponFactory.autoRegister` 启动时扫描该包自动注册（内置技能另有 `WeaponFactory.registerBuiltins()` 兜底）
3. 在 `src/main/resources/skills/` 下新建一个 `<weaponId>.yml`（文件名即武器 id，也可用文件内 `id:` 覆盖），`right-skill`/`left-skill` 填技能 key；**只写该技能用到的数值键**（`stats:` 段），缺失键用代码内置默认值

新增触发（可选）：覆写 `onDoubleShift` / `onHoldShift` / `onReleaseShift`，输入层已统一派发。

### 硬性约定

- 技能逻辑里**不要硬编码数值**（伤害/冷却/位移/范围/特效等），一律从 `ctx.stats()` 读，方便 `skills/*.yml` 调平衡
- 新增数值字段时同步改 `WeaponStats` 的字段、getter、`fromConfig`、`copy` 四处；**不再自动向每个文件填充默认键**，每个技能文件只写自己用到的键
- 玩家来源技能伤害必须走 `ctx.damage(...)` / `SkillDamageUtils.damage(...)`，不要直接 `target.damage(x, player)`；**吸血自动生效**：`ctx.damage*` 内部按 `stats.lifesteal()` 统一吸血，技能里无需再手动 `healSelfByDamage`
- 所有 AOE / 射线 / 吸附 / 路径伤害必须经过 `TargetFilter`（过滤友军/宠物/NPC/盔甲架/同队/PvP 禁用世界）
- 触发键用 `SkillTrigger` 枚举；识别武器只认 PDC key `skillcore_weapon_id`（`WeaponItems.KEY_WEAPON_ID`），不要用 displayName 判断
- 有持久状态（状态表 / BossBar / `BukkitRunnable`）的技能必须覆写 `WeaponSkill.cleanup()`，并在 `onPlayerQuit` 清理
- 版本号在 `pom.xml` 的 `<version>`；`plugin.yml` 用 `${project.version}` 过滤

## 生命周期

- `WeaponSkill.cleanup()` → `WeaponRegistry.cleanup()` → `SkillCorePlugin.onDisable()` / `reloadAll()` 触发
- `WeaponInputListener` 在 `PlayerQuitEvent` / `PlayerItemHeldEvent` 清理双击/按住状态
- `CombatListener` 在退出/死亡清理吸血/反伤 buff

## 特效接口 / 粒子 / CraftEngine 模型

- 特效接口：`com.skillcore.effect.SkillEffect`（函数式，`play(EffectContext)`），工厂 `Effects`：
  - 形状：`particle/ring/ringFacing/sphere/helix/cone/beam/lineToTarget/explosion`
  - 彩色尘：`dust(Color, size, count[, spread])`
  - 闪电：`bolt`（沿朝向）/ `boltBetween` / `skyStrikes`（天雷）/ `skyStrikesDust`（彩色天雷）
  - 动画形状：`expandingRing`（扩散环）/ `groundCracks`（地面裂纹）/ `orbit`（环绕螺旋）/ `groundShatter`（地面碎裂：方块碎屑粒子 + PacketEvents 方块裂纹动画后恢复）
  - 音效：`sound/soundRadius`
  - 显示物：`display(ItemStack,...)` / `model(craftEngineModel,...)` / `modelFacing(...)`（内部 `DisplayEffect`，ItemDisplay + 平滑插值 + 自动移除）
  - 组合：`parallel/sequence/delayed/at`，或 `fx.andThen(next).delayed(ticks)`
  - 技能里用 `ctx.play(effect)` / `ctx.playAt(effect, loc)`；`WeaponContext.effectContext()` 提供位置/朝向/施法者/目标
- 粒子工具：`ParticleUtils` 已统一走 `EffectSettings`（`config.yml` 的 `effects.*`）开关、密度、降级粒子，并对 null/NaN/无穷坐标防护；新增 `bolt/boltDust/boltPoints` 闪电折线（中点位移法）
- CraftEngine 模型：`craftengine_model`（也兼容 `craftengine-model`）配置键 → `SkillWeapon.craftEngineModel()`；`WeaponItems.create` 优先用 `CraftEngineHook.buildItem(...)` 作底物，失败降级 `material` + `custom-model-data`。`CraftEngineHook` 用反射调用 `CraftEngineItems.byId(id).buildBukkitItem()`，未装 CraftEngine 时全部返回 null/false

## 配置要点

- `config.yml`：`debug`、`shared_config_dir`、`config_poll_interval`、`combat.*`（含 `pvp-blocked-worlds`）、`target-filter.*`、`input.*`（`double-shift-window-ms`/`hold-shift-delay-ms`）、`cooldown.*`、`messages.*`（`&` 颜色码）、`placeholders.*`、`effects.*`（`enabled`/`particles`/`density`/`fallback-particle`/`display.*`）、`logging.*`
- `skills/*.yml`（一个技能一个文件，放在 jar 的 `skills/` 目录；首次运行自动复制到数据目录 `skills/`）：每把武器有 `display-name/description/material/lore/right-skill/left-skill/*-trigger/glow/custom-model-data/unbreakable/enchantments/stats`；`stats:` 只写本技能用到的键
  - 数值既可写在 `stats:` 段，也可平铺在文件顶层（`stats:` 优先）——避免「设了没反应」
  - **技能冷却秒数**在 `stats.cooldown`（`0` = 无冷却）；也可用 `/sc cooldown <weaponId> <秒>` 写回文件并重载
  - `display-name`/`lore` 支持 MiniMessage（`<gradient>/<color>`）与 `&` 颜色码混合，由 `TextUtils.parse` 解析
  - `enchantments:` 为「附魔 key: 等级」映射（如 `wind_burst: 5`），通过 `Registry.ENCHANTMENT` 解析并 `addEnchant(..., true)` 绕过原版等级上限
  - 风暴战锤为无目标技能：不锁定目标也能释放（`canUse` 不要求 target），以自身为落点做 AOE；砸地会向上弹飞范围内敌人（`slam-uppercut-velocity`）并自身反冲（`slam-self-bounce`），模拟原版风爆手感
- `testdummy.yml`：`dummy.name/health/no-ai/silent`；本服数据目录独立，不参与共享
- `lore` 变量由 `WeaponLore` 替换，例如：`{id} {display} {damage} {scaling} {crit}/{crit-chance} {crit-multi} {pen}/{armor-penetration} {attack-speed} {percent-max} {percent-current} {percent-missing} {lifesteal} {reflect} {reflect-flat} {cooldown}/{cd} {dash-speed} {dash-distance} {blink} {knockback} {range} {aim-range} {aoe}/{aoe-radius} {aoe-ratio} {slow-ticks} {stun-ticks} {root-ticks} {heal} {heal-percent} {shield} {dr} {hits} {max-targets}`

## 群组服共享配置

- `config.yml` 的 `shared_config_dir` 指向共享目录后，`config.yml` + `skills/` 目录从该目录读写（首次自动从本服复制）
- `config_poll_interval` 秒轮询文件 `lastModified`，变化则 `reloadAll()`；`/sc reload` 立即重载本服
- `shared_config_dir` 本身只从本服 datafolder 的 `config.yml` 读取（bootstrap）；`testdummy.yml` 始终本服独立

## 独立模块

- PlaceholderAPI：`softdepend`，未安装时自动跳过。占位符 `%skillcore_weapon_id% / _weapon_name% / _has_weapon% / _weapon_cd% / _weapon_cd_left% / _weapon_cd_right%`
- CraftEngine：`softdepend`，未安装时 `craftengine_model` 降级为 `material` + `custom-model-data`
- packetevents：`softdepend`，未安装时地面碎裂特效降级为纯方块碎屑粒子（无方块裂纹数据包）
- 测试假人：`/sc testdummy spawn [health] | list | clear [all]`，僵尸无 AI/静音/防火/不还击，持久化到 `testdummy.yml`

## 遗留代码清理（已完成）

早期「独立技能绑定」设计已被删除：`api/`（`SkillTrigger` 已迁移到 `weapon/` 包）、`factory/`、`manager/`、`model/`、`skill/`、`listener/SkillInputListener.java`、`resources/skills.yml` 均已移除。不要再基于这些包开发。

`SkillTrigger` 枚举现位于 `com.skillcore.weapon.SkillTrigger`。

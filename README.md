# easy-trade

`easy-trade` 是一个面向 Minecraft Java Edition 26.2 的纯客户端 Fabric 模组。

- 作者：`ozozorz`
- Minecraft：`26.2`
- Fabric Loader：`0.19.3`
- Fabric API：`0.156.0+26.2`
- Java：`25`
- Mod Menu：`20.0.1`（可选，但推荐安装）

## 使用方法

1. 正常右键村民并打开交易界面。
2. 选择一个交易项目。
3. 点击界面右上角的 `★` 按钮收藏该项目。
4. 关闭交易界面。
5. 手持该交易所需的任意一种输入物品，右键同一个村民。

模组会在后台建立原版村民菜单，等待服务器发送最新报价，选择收藏项目，执行一次原版快速移动，然后等待服务器同步并关闭菜单。一次原版快速移动会完成该项目当前能够完成的全部次数。

潜行右键始终绕过自动化，正常打开村民交易界面。

## 配置面板

安装 Mod Menu 后：

```text
主菜单或暂停菜单 → 模组 → easy-trade → 配置
```

也可以使用客户端命令：

```text
/easytrade config
```

面板可以调整：

- 是否启用快捷交易；
- 基础操作延迟；
- 随机延迟上限；
- 重复触发冷却；
- 交易菜单等待超时；
- 服务器同步等待超时。

按 `ESC` 会放弃尚未保存的面板改动；点击“保存并完成”才会写入配置文件。

## 客户端命令

```text
/easytrade
/easytrade config
/easytrade delay <100..5000>
/easytrade jitter <0..2000>
/easytrade on
/easytrade off
/easytrade reload
```

## 本地文件

```text
.minecraft/config/easy-trade.json
.minecraft/config/easy-trade-bookmarks.json
```

收藏按照服务器地址、维度和村民 UUID 隔离。交易匹配保存输入、输出及数据组件，同时忽略会动态变化的需求价格和剩余次数。

## 服务器兼容策略

- 不使用自定义服务端数据包；
- 不要求服务器安装 Fabric、Fabric API 或 easy-trade；
- 每个阶段都等待对应的菜单或槽位状态；
- 每次激活只提交一次原版 `QUICK_MOVE`；
- 默认基础延迟为 350ms，并独立增加 0–150ms 随机延迟；
- 完成后默认冷却 900ms，避免按住右键重复触发。

这些措施不能保证符合所有公共服务器的规则。即使数据包属于原版协议，服务器仍可能禁止宏或自动化功能，请遵守目标服务器规定。

## 构建

使用 JDK 25：

```text
./gradlew build
```

成品位于 `build/libs/easy-trade-1.0.0.jar`。

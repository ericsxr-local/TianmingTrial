# TianmingTrial

[English](README.md)

创作者：Eric_SXR

十二个现实日的天命试炼活动，包含进度继承、能力、HUD 和伪装。

## 功能

- 使用 `/tianming` 管理活动与状态。
- 通过 YAML 配置能力和活动规则。
- 照着那个挺火的那个短剧搞的，感觉非常肥美

## 兼容环境

- 插件版本：`1.0.0`（以 `plugin.yml` 为准）。
- 目标服务端：Paper/Purpur 1.20.1，API `1.20`。
- 编译 JDK：17。
- 必需插件：未声明。
- 可选插件：ProtocolLib。

## 编译与安装

1. 使用 JDK 17，在仓库根目录运行 `mvn package`。
2. 将 `target/` 中生成的 JAR 复制到服务器的 `plugins/` 目录。
3. 重启服务器，再按需修改插件生成的配置文件。

## 命令与配置

- 注册命令：`/tianming`。权限和用法见 `src/main/resources/plugin.yml`。
- 主要配置段：`activity`, `disguise`, `hud`, `test`。详见 `src/main/resources/config.yml`。

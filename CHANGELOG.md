# Changelog
This fork restarts release versioning at `1.0`.
Entries under **Upstream History** are preserved from the original project and are not part of this fork's version sequence.

## 1.0

* [X] Forked from `DragonKnightOfBreeze/BBCode` to build Steam BBCode support
* [X] Added `olist` schema support
* [X] Added nested list-container schema support (`list`, `ul`, `ol`, `olist`)
* [X] Updated schema parent rules for list item tags (`li`, `*`) to include `olist`
* [X] Added Steam list schema assertions in `BBCodeSchemaResolvingTest`
* [X] Updated bundled sample BBCode with `olist` and nested list examples
* [X] Improved tag completion for list containers (`list`, `ul`, `ol`, `olist`) to expand into a multiline block with caret placed inside
* [X] Added Enter-key handling so `[list]|[/list]` expands to a three-line block with caret on the middle line
* [X] Fixed completion context inside line-style list items (`[*]...`) so nested `list` suggestions appear within list containers
* [X] Fixed nested list completion expansion to preserve parent indentation in inserted inner list blocks
* [X] Updated completions so that `[list]` and `[olist]` suggestions appear within list containers (for nested lists)

## Upstream History (before fork)

## 2.3

* [X] #4 Minor schema issue with `[img]` tag

***

* [X] #4 Minor schema issue with `[img]` tag

## 2.2

* [X] #2 `[img]` and custom tags do not work
* [X] #3 `[list]` with only one `[*]` is detected as an error
* [X] 其他优化与BUG修复

***

* [X] #2 `[img]` and custom tags do not work
* [X] #3 `[list]` with only one `[*]` is detected as an error
* [X] Other optimizations and BUG fixes

## 2.1

* [X] 参照XML与HTML，补充一些意向操作
* [X] 参照XML与HTML，补充一些unwrap操作
* [X] 实现基于规范文件的代码补全功能

## 2.0

* [X] 修复 #1
* [X] 提供标准规范文件（以xml文件的形式）
* [X] 实现基于规范文件的标签与特性的引用解析、代码导航等功能
* [X] 实现基于规范文件的快速文档功能
* [X] 实现基于规范文件的无法解析或者不合法的标签与特性的代码检查

## 1.8

* 重新上传
* 兼容解析短语标签（如，`[br]`）

## 1.7

* 兼容解析短语标签（如，`[br]`）

## 1.6

* 更换包名
* 更新IDE版本到2022.3

## 1.5

* 更新IDE版本到2020.3

## 1.4

* 更新项目文档

## 1.3

* 更新项目文档

## 1.2

* 更新项目文档

## 1.1

* 更新项目文档

## 1.0

* 完成基础的语言功能

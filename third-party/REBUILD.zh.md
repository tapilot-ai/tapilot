# Android 图像运行库的源码与重链接

Tapilot 0.1.8 的 Android 包采用自行重链接的 Sharp WASM。上游 npm 包的版本号不变，但发布文件的哈希不同；`sharp-rebuild.json` 记录实际使用的文件，构建时逐个校验。

## 已完成的验证

- libvips 的静态库在 GitHub Actions 任务 `41452931004` 中编译完成。该任务的源码打包失败，且旧工作流未传播管道左侧的退出码，因此不能将该任务的绿色状态当作整体验证成功。
- 任务 `41456669649` 从校验过的原始产物取回静态库，并按原构建记录的 SHA-256 补齐全部下载源码。源码包包含依赖归档、补丁和 Rust vendor 目录。
- 任务 `41457297001` 使用上述静态库重链接 Sharp，并通过 PNG、JPEG、WebP 的编码、解码、缩放测试。该工作流启用了 Bash 的管道失败传播。
- 任务 `41457717934` 校验三个输入产物的完整 ZIP 哈希，将源码与重链接材料上传到已有的 **草稿** Release；该操作不代表 APK 已通过验收或公开。

以上任务链接的前缀均为 `https://github.com/tapilot-ai/tapilot/actions/runs/`。

## 重建和替换

1. 从对应 Release 下载 `Tapilot-0.1.8-materials.json` 及源码归档，逐个核对其中的 SHA-256。
2. 在支持 Docker 的 Linux 环境运行 `scripts/distribution/rebuild-vips-wasm.sh <输出目录>`。脚本固定 wasm-vips 提交和 Emscripten 镜像摘要；源码包内保存了本次构建使用的依赖和补丁。外部源如果改变，应使用已保存且哈希匹配的源码，不能默默接受新的内容。
3. 运行 `scripts/relink-sharp-wasm.sh <Sharp 输出目录> <libvips-wasm-rebuilt.tar.gz>`。脚本提供静态库与头文件替换位置，并执行图像处理测试。Sharp 的源码、构建依赖锁文件以及编译后的 libvips 静态库均随 Release 提供。
4. 修改 LGPL 组件后，更新本地重建归档和文件哈希记录，执行 `pnpm runtime:bundle`，然后重新构建 APK。发布版的校验记录只适用于发布时验证的字节，不禁止用户自行修改及重新链接。
5. 自行构建的 APK 使用自己的签名。如果与现有安装的签名不同，Android 不允许直接覆盖；安装前应备份个人配置。

本仓库的重链接脚本和 GPL/LGPL 许可文本随源码提供。不得把原 npm 二进制的版本号相同视为已通过本次源码和重链接验证。真机验收与签名核验仍需单独完成。

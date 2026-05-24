# 游戏性能监控 (GamePerfMonitor)

一款 Android 游戏性能监控应用，通过 Root 权限实时采集硬件数据，对局结束后可查看各指标曲线图。

## 功能

- **实时监控**: 帧率、CPU频率/使用率/温度、GPU频率、电池温度、功耗
- **对局记录**: 自动保存每次监控会话
- **曲线图表**: 7种指标曲线可切换查看

## 自动编译

上传到 GitHub 后，Actions 会自动编译 APK。

1. 进入 **Actions** 标签
2. 等待编译完成（约5-10分钟）
3. 下载 **Artifacts** 中的 `GamePerfMonitor-Debug.zip`
4. 解压得到 `app-debug.apk`，安装到已 Root 的手机

## 注意

- 需要 Root 权限
- 支持 Android 8.0+

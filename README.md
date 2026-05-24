# 游戏性能监控 (GamePerfMonitor)

一款 Android 游戏性能监控应用，通过 Root 权限实时采集硬件数据，对局结束后可查看各指标曲线图。

## 功能特性

- **实时监控**: 帧率、CPU频率/使用率/温度、GPU频率、电池温度、功耗
- **对局记录**: 自动保存每次监控会话
- **曲线图表**: 7种指标曲线可切换查看

## 使用 GitHub Actions 自动编译 APK

### 步骤 1: 创建 GitHub 仓库

1. 登录 [GitHub](https://github.com)
2. 点击右上角 **+** → **New repository**
3. 填写仓库名称（如 `GamePerfMonitor`）
4. 选择 **Public** 或 **Private**
5. 点击 **Create repository**

### 步骤 2: 上传项目

**方式 A: 使用 Git 命令行**

```bash
# 解压项目
unzip GamePerfMonitor-GitHub.zip
cd GamePerfMonitor-GitHub

# 初始化 Git
git init
git add .
git commit -m "Initial commit"

# 推送到 GitHub（替换 YOUR_USERNAME 和 YOUR_REPO）
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git branch -M main
git push -u origin main
```

**方式 B: 使用 GitHub 网页上传**

1. 在仓库页面点击 **uploading an existing file**
2. 将解压后的所有文件拖拽上传
3. 点击 **Commit changes**

### 步骤 3: 触发编译

上传完成后，GitHub Actions 会自动开始编译。

- 点击仓库页面的 **Actions** 标签查看进度
- 编译完成后，点击具体的 workflow run
- 在页面底部的 **Artifacts** 区域下载 APK

### 步骤 4: 安装 APK

1. 下载 `GamePerfMonitor-Debug.zip`
2. 解压得到 `app-debug.apk`
3. 传输到已 Root 的 Android 手机安装

## 本地编译（需要 Android Studio）

1. 安装 [Android Studio](https://developer.android.com/studio)
2. 打开项目文件夹
3. 等待 Gradle 同步完成
4. 点击 **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. APK 位于 `app/build/outputs/apk/debug/`

## 注意事项

- **需要 Root 权限**才能采集硬件数据
- 支持 Android 8.0 及以上版本

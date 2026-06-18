
# 环境准备

本项目使用 `pyproject.toml` 和 `uv.lock` 管理依赖，推荐使用 `uv` 安装和运行。

## 安装 uv

如果本机还没有安装 `uv`，可以执行：

```powershell
python -m pip install --user uv
```

安装后重新打开终端，确认命令可用：

```powershell
uv --version
```

## 安装依赖

进入项目目录后执行：

```powershell
uv sync
```

`uv sync` 会自动创建/更新 `.venv`，并根据 `uv.lock` 安装依赖。

如果不使用 `uv`，也可以手动创建虚拟环境并从 `pyproject.toml` 安装依赖。

Windows PowerShell：

```powershell
python -m venv .venv
.\.venv\Scripts\activate
python -m pip install fastapi frida frida-tools loguru requests rich sqlalchemy uvicorn
```

Linux/macOS：

```bash
python -m venv .venv
source .venv/bin/activate
python -m pip install fastapi frida frida-tools loguru requests rich sqlalchemy uvicorn
```

## 运行应用

```powershell
uv run main.py
```

或在已激活 `.venv` 的终端中运行：

```powershell
python main.py
```

## 配置说明

在模型配置中设置目标 URL 和 hooks URL，然后开启 hooks。


# `webui.py`使用方法

将模块`web`目录拷贝到本目录
将你模块的`config.json`文件拷贝到本目录
将你任意一个配置的`friend.json`文件拷贝到本目录
cd 到本目录
运行`uv run webui.py`
访问`http://127.0.0.1:8000`这样你就可方便的在本地调试你的`WEB-UI`了

![image](https://cdn.jsdelivr.net/gh/Fansirsqi/picx-images-hosting@master/image.lw7lwtiwe.webp)


url添加路径就可以访问自定义的UI了 http://127.0.0.1:8080/semi_index.html

http://127.0.0.1:8080/varlet_index.html

![image](https://cdn.jsdelivr.net/gh/Fansirsqi/picx-images-hosting@master/image.73uff876w2.webp)

![image](https://cdn.jsdelivr.net/gh/Fansirsqi/picx-images-hosting@master/image.4g4z4vfld0.webp)

欢迎更多大佬pr



@echo off
set "gitPath=D:\软件\Git\bin"
set "projectPath=%~dp0"
set "parentPath=%~dp0.."
set "remoteRepo=https://gitee.com/abc62201/ai_ledger.git"

set "path=%path%;%gitPath%"
cd /d "%parentPath%"

rem 1. 检查 .git 是否已经在上级目录下，没有则初始化
if not exist ".git" (
    echo 正在初始化上级目录为Git仓库...
    git init
    git remote add origin %remoteRepo%
    git branch -M master
)

rem 2. 获取远程最新状态
git fetch origin

rem 3. 添加 myapp 文件夹到暂存区
git add "myapp"

rem 4. 提交
git commit -m "自动部署myapp项目代码"

rem 5. 推送到远程
git push -u origin master --force-with-lease

echo.
echo ==========================================
echo  部署完成！请检查远程仓库的 myapp/ 文件夹。
echo ==========================================
echo.
pause
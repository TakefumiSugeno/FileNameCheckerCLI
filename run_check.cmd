@echo off

rem フォルダパスを引数として受け取る
set "folder_path=%~1"

rem ここにプログラム実行のコマンドを記述します。
rem 例: プログラム名.exe "%folder_path%"
Clojure.Main.exe -i .\src\check_filenames.clj -m file-checker.core "%folder_path%"

pause

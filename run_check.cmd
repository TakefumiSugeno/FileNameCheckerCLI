@echo off

rem �t�H���_�p�X�������Ƃ��Ď󂯎��
set "folder_path=%~1"

rem �����Ƀv���O�������s�̃R�}���h���L�q���܂��B
rem ��: �v���O������.exe "%folder_path%"
Clojure.Main.exe -i .\src\check_filenames.clj -m file-checker.core "%folder_path%"

pause

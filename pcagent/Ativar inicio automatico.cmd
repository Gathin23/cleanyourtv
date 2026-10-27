@echo off
rem Coloca a Central de Jogos para abrir junto com o Windows (atalho na pasta Inicializar do seu usuario)
rem e ja abre agora. Para desfazer: apague "Central de Jogos (TV)" em shell:startup.
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$s=(New-Object -ComObject WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Startup')+'\Central de Jogos (TV).lnk');" ^
  "$s.TargetPath='C:\tvh\pcagent\venv\Scripts\pythonw.exe'; $s.Arguments='\"C:\tvh\pcagent\agent.py\"';" ^
  "$s.WorkingDirectory='C:\tvh\pcagent'; $s.Description='Liga a aba Jogos da TV ao PC'; $s.Save()"
start "" "C:\tvh\pcagent\venv\Scripts\pythonw.exe" "C:\tvh\pcagent\agent.py"
echo Pronto: a Central de Jogos vai abrir sempre que o Windows iniciar.
pause

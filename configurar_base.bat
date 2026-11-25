@echo off
setlocal
title AlphaSport - Conectar con la base compartida
cd /d "%~dp0"
rem ======================================================================
rem  Conecta este computador con la base compartida de AlphaSport (Neon).
rem  Pide la cadena de conexion (postgresql://...) que le pasan por privado
rem  y crea db.properties. Ese archivo NUNCA se sube al repositorio.
rem ======================================================================
echo.
echo  Conectar AlphaSport con la base compartida (Neon)
echo  -------------------------------------------------
echo  Pegue la cadena de conexion que le pasaron por privado
echo  (empieza por postgresql://) y presione Enter.
echo  No se vera en pantalla mientras la pega: es normal.
echo.
powershell -NoProfile -ExecutionPolicy Bypass -Command "$s = Read-Host '  Cadena' -AsSecureString; $c = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s)).Trim().Trim([char]39, [char]34); if ($c -notmatch '^postgres(ql)?://[^:/@]+:[^@]+@[^/@]+/') { Write-Host ''; Write-Host '  [ERROR] Eso no parece una cadena de Neon. Debe verse asi: postgresql://usuario:clave@servidor/base?sslmode=require'; exit 1 }; if ((Test-Path 'db.properties') -and -not (Test-Path 'db.properties.local')) { Copy-Item 'db.properties' 'db.properties.local'; Write-Host '  Su db.properties anterior quedo guardado como db.properties.local' }; $t = '# Base compartida (Neon). Este archivo NO se sube al repositorio.' + [char]10 + 'db.url=' + $c.Replace([string][char]92, [string][char]92 + [char]92) + [char]10; [IO.File]::WriteAllText((Join-Path (Get-Location) 'db.properties'), $t); Write-Host ''; Write-Host '  Listo: db.properties creado. Abra AlphaSport (escritorio o web) y entre con su cuenta.'"
echo.
pause

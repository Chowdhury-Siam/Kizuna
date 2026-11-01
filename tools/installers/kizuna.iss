#ifndef AppVersion
  #define AppVersion "1.0.5"
#endif
#ifndef PublishDir
  #define PublishDir "..\..\publish"
#endif
[Setup]
AppId={{51D43AE8-5679-4C99-9839-2A08820A37A2}
AppName=Kizuna
AppVersion={#AppVersion}
AppPublisher=Kizuna contributors
AppPublisherURL=https://github.com/Chowdhury-Siam/Kizuna
DefaultDirName={localappdata}\Programs\Kizuna
DefaultGroupName=Kizuna
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\..\dist
OutputBaseFilename=Kizuna-v{#AppVersion}-Setup
SetupIconFile=..\..\server\DroidOSS.App\Assets\kizuna.ico
UninstallDisplayIcon={app}\Kizuna.exe
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
RestartApplications=no
LicenseFile=..\..\LICENSE
[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"; Flags: unchecked
[Files]
Source: "{#PublishDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
[Icons]
Name: "{group}\Kizuna"; Filename: "{app}\Kizuna.exe"
Name: "{autodesktop}\Kizuna"; Filename: "{app}\Kizuna.exe"; Tasks: desktopicon
[Run]
Filename: "{app}\Kizuna.exe"; Description: "Open Kizuna"; Flags: nowait postinstall skipifsilent; Check: not IsUpdate
Filename: "{app}\Kizuna.exe"; Flags: nowait; Check: IsUpdate
[Code]
function IsUpdate: Boolean;
begin
  Result := ExpandConstant('{param:UPDATE|0}') = '1';
end;

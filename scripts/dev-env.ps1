$ErrorActionPreference = 'Stop'

$env:ANDROID_HOME = 'D:\Code\Android\Sdk'
$env:ANDROID_SDK_ROOT = 'D:\Code\Android\Sdk'
$env:ANDROID_USER_HOME = 'D:\Code\Android\UserHome'
$env:ANDROID_AVD_HOME = 'D:\Code\Android\Avd'
$env:GRADLE_USER_HOME = 'D:\Code\GradleHome'
$env:IDEA_PROPERTIES = 'D:\Code\Android\StudioData\idea.properties'
$env:JAVA_HOME = 'D:\Code\Java\jdk-17.0.20+8'
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

$requiredDirectories = @(
    $env:ANDROID_HOME,
    $env:ANDROID_USER_HOME,
    $env:ANDROID_AVD_HOME,
    $env:GRADLE_USER_HOME,
    'D:\Code\Android\StudioData\config',
    'D:\Code\Android\StudioData\system',
    'D:\Code\Android\StudioData\plugins',
    'D:\Code\Android\StudioData\log'
)

foreach ($directory in $requiredDirectories) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}

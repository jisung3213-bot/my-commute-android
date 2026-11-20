$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 17 before running this script.' }
    & .\gradlew.bat compileDebugUnitTestJavaWithJavac --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
    $cacheRoot = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $junit = Get-ChildItem (Join-Path $cacheRoot 'caches/modules-2/files-2.1/junit/junit/4.13.2') -Recurse -Filter 'junit-4.13.2.jar' | Select-Object -First 1
    $hamcrest = Get-ChildItem (Join-Path $cacheRoot 'caches/modules-2/files-2.1/org.hamcrest/hamcrest-core/1.3') -Recurse -Filter 'hamcrest-core-1.3.jar' | Select-Object -First 1
    $json = Get-ChildItem (Join-Path $cacheRoot 'caches/modules-2/files-2.1/org.json/json/20240303') -Recurse -Filter 'json-20240303.jar' | Select-Object -First 1
    if (-not $junit -or -not $hamcrest -or -not $json) { throw 'Unit test dependencies missing' }
    # Use a direct Unicode-safe launch rather than Gradle 8.2 worker classpath serialization on Windows.
    $testOutput = (Resolve-Path 'app/build/intermediates/javac/debugUnitTest/classes').Path
    $testClasses = @(Get-ChildItem $testOutput -Recurse -File -Filter '*Test.class' | ForEach-Object {
        $_.FullName.Substring($testOutput.Length + 1) -replace '\.class$', '' -replace '[\\/]', '.'
    } | Sort-Object)
    if ($testClasses.Count -eq 0) { throw 'No compiled unit tests found' }
    $classPath = "$testOutput;app/build/intermediates/javac/debug/classes;$($junit.FullName);$($hamcrest.FullName);$($json.FullName)"
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp $classPath org.junit.runner.JUnitCore @testClasses
    if ($LASTEXITCODE -ne 0) { throw 'Unit tests failed' }
} finally { Pop-Location }

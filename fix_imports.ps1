# Run this from the repo root: powershell -ExecutionPolicy Bypass -File fix_imports.ps1

$javaDir = ".\app\src\main\java"
$files = Get-ChildItem -Recurse -Filter "*.java" -Path $javaDir

$replacements = @(
    # Support lib -> AndroidX
    @("android\.support\.v7\.app\.AppCompatActivity",          "androidx.appcompat.app.AppCompatActivity"),
    @("android\.support\.v7\.widget\.RecyclerView",            "androidx.recyclerview.widget.RecyclerView"),
    @("android\.support\.v7\.graphics\.Palette",               "androidx.palette.graphics.Palette"),
    @("android\.support\.v4\.app\.FragmentStatePagerAdapter",  "androidx.fragment.app.FragmentStatePagerAdapter"),
    @("android\.support\.v4\.app\.FragmentManager",            "androidx.fragment.app.FragmentManager"),
    @("android\.support\.v4\.app\.FragmentTransaction",        "androidx.fragment.app.FragmentTransaction"),
    @("android\.support\.v4\.app\.Fragment",                   "androidx.fragment.app.Fragment"),
    @("android\.support\.v4\.view\.ViewPager",                 "androidx.viewpager.widget.ViewPager"),
    @("android\.support\.v4\.view\.PagerAdapter",              "androidx.viewpager.widget.PagerAdapter"),
    @("android\.support\.v4\.content\.ContextCompat",          "androidx.core.content.ContextCompat"),
    @("android\.support\.v4\.graphics\.drawable\.RoundedBitmapDrawableFactory", "androidx.core.graphics.drawable.RoundedBitmapDrawableFactory"),
    @("android\.support\.v4\.graphics\.drawable\.RoundedBitmapDrawable",        "androidx.core.graphics.drawable.RoundedBitmapDrawable"),
    @("android\.support\.v4\.app\.NotificationCompat",         "androidx.core.app.NotificationCompat"),
    @("android\.support\.v4\.media\.MediaDescriptionCompat",   "androidx.media.MediaDescriptionCompat"),
    @("android\.support\.v4\.media\.session\.MediaSessionCompat", "androidx.media.session.MediaSessionCompat"),
    @("android\.support\.annotation\.NonNull",                 "androidx.annotation.NonNull"),
    @("android\.support\.annotation\.Nullable",                "androidx.annotation.Nullable"),
    # Old Glide API - asBitmap() chain order fix (simple swap won't break things, handled manually)
    # Old ExoPlayer references removed in ExoPlayerService rewrite
    # RecyclerView adapter import
    @("import android\.support\.v7\.widget\.RecyclerView;",    "import androidx.recyclerview.widget.RecyclerView;"),
    # ViewPager import in ViewSongActivity
    @("import android\.support\.v4\.view\.ViewPager;",         "import androidx.viewpager.widget.ViewPager;")
)

$totalChanges = 0
foreach ($file in $files) {
    $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8
    $original = $content
    foreach ($pair in $replacements) {
        $content = $content -replace $pair[0], $pair[1]
    }
    if ($content -ne $original) {
        Set-Content -Path $file.FullName -Value $content -Encoding UTF8 -NoNewline
        Write-Host "Updated: $($file.Name)"
        $totalChanges++
    }
}
Write-Host "`nDone. $totalChanges file(s) updated."

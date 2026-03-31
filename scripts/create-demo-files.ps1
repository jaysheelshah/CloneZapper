# Creates a demo folder structure with known duplicates for testing CloneZapper
# Run from anywhere: .\scratch\create-demo-files.ps1
# Output: C:\CloneZapperDemo\

$root = "C:\CloneZapperDemo"

Write-Host "Creating demo files in $root ..."

# ── Exact duplicate pair 1: a report PDF (simulated) ──────────────────────────
$report = "This is a project report.`nAuthor: Jay`nDate: 2024-01-15`nContent: Lorem ipsum dolor sit amet."
New-Item -ItemType Directory -Force "$root\Documents\Work"    | Out-Null
New-Item -ItemType Directory -Force "$root\Downloads"         | Out-Null
Set-Content "$root\Documents\Work\project-report.txt" $report
Set-Content "$root\Downloads\project-report.txt"      $report

# ── Exact duplicate pair 2: a photo (simulated binary-ish content) ────────────
$photoData = "X" * 2000
$photo = "FAKEIMAGEDATA::$photoData"
New-Item -ItemType Directory -Force "$root\Pictures\Holidays" | Out-Null
New-Item -ItemType Directory -Force "$root\Desktop\Temp"      | Out-Null
Set-Content "$root\Pictures\Holidays\beach.jpg"  $photo
Set-Content "$root\Desktop\Temp\beach.jpg"       $photo

# ── Explicit copy (copy-pattern in filename) ───────────────────────────────────
$notes = "Meeting notes from Q1 planning session.`nAction items: 1. Ship it. 2. Test it."
New-Item -ItemType Directory -Force "$root\Documents\Notes" | Out-Null
Set-Content "$root\Documents\Notes\meeting-notes.txt"        $notes
Set-Content "$root\Documents\Notes\meeting-notes - Copy.txt" $notes

# ── Triple duplicate ───────────────────────────────────────────────────────────
$budget = "Budget spreadsheet data`nQ1: 10000`nQ2: 12000`nQ3: 15000`nQ4: 18000"
New-Item -ItemType Directory -Force "$root\Documents\Finance" | Out-Null
New-Item -ItemType Directory -Force "$root\Desktop\Old"       | Out-Null
New-Item -ItemType Directory -Force "$root\Downloads\Archive" | Out-Null
Set-Content "$root\Documents\Finance\budget-2024.txt"  $budget
Set-Content "$root\Desktop\Old\budget-2024.txt"        $budget
Set-Content "$root\Downloads\Archive\budget-2024.txt"  $budget

# ── Unique files (should NOT appear as duplicates) ────────────────────────────
Set-Content "$root\Documents\Work\todo.txt"      "Buy milk`nCall dentist`nFix bug"
Set-Content "$root\Desktop\Temp\readme.txt"      "This is a readme file with unique content."
$sunsetData = "Y" * 2000
Set-Content "$root\Pictures\Holidays\sunset.jpg" "FAKEIMAGEDATA::$sunsetData"

Write-Host ""
Write-Host "Done. Folder structure:"
Get-ChildItem $root -Recurse -File | ForEach-Object { Write-Host "  $_" }
Write-Host ""
Write-Host "Expected duplicate groups:"
Write-Host "  Group 1: project-report.txt (x2)"
Write-Host "  Group 2: beach.jpg (x2)"
Write-Host "  Group 3: meeting-notes.txt + meeting-notes - Copy.txt"
Write-Host "  Group 4: budget-2024.txt (x3)"
Write-Host ""
Write-Host "Now scan C:\CloneZapperDemo in the UI at http://localhost:8080"
